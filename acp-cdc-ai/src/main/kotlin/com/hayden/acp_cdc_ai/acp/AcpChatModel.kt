package com.hayden.acp_cdc_ai.acp

import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.*
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.Transport
import com.fasterxml.jackson.databind.ObjectMapper
import com.hayden.acp_cdc_ai.acp.config.AcpModelProperties
import com.hayden.acp_cdc_ai.acp.config.McpProperties
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey
import com.hayden.acp_cdc_ai.acp.events.Events
import com.hayden.acp_cdc_ai.permission.IPermissionGate
import com.hayden.acp_cdc_ai.repository.RequestContextRepository
import com.hayden.acp_cdc_ai.sandbox.SandboxTranslation
import com.hayden.acp_cdc_ai.sandbox.SandboxTranslationRegistry
import com.hayden.utilitymodule.nullable.mapNullable
import com.hayden.utilitymodule.nullable.or
import io.modelcontextprotocol.server.IdeMcpAsyncServer.TOOL_ALLOWLIST_HEADER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonElement
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.*
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.model.StreamingChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.mcp.AsyncMcpToolCallback
import org.springframework.ai.mcp.SyncMcpToolCallback
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * ACP-backed ChatModel implementation using the agentclientprotocol SDK.
 */

@Component
class AcpChatModel(
    private val properties: AcpModelProperties,
    private val chatMemoryContext: ChatMemoryContext?,
    private val sessionManager: AcpSessionManager,
    private val mcpProperties: McpProperties,
    private val permissionGate: IPermissionGate,
    private val requestContextRepository: RequestContextRepository,
    private val sandboxTranslationRegistry: SandboxTranslationRegistry,
    private val objectMapper: ObjectMapper = ObjectMapper()
) : ChatModel, StreamingChatModel {

    private val log: Logger = LoggerFactory.getLogger(AcpChatModel::class.java)

    companion object AcpChatModel {

        const val MCP_SESSION_HEADER: String = "X-AG-UI-SESSION"

        fun MCP_SESSION_HEADER(): String {
            return MCP_SESSION_HEADER
        }

    }

    override fun call(prompt: Prompt): ChatResponse {
        log.info("Received request - {}.", prompt)
        val cr = doChat(prompt)
        return cr
    }

    override fun stream(prompt: Prompt): Flux<ChatResponse> {
        log.info("Received request - {}.", prompt)
        return performStream(prompt, resolveMemoryId(prompt))
    }

    fun performStream(messages: Prompt, memoryId: Any?): Flux<ChatResponse> {
        return flux {
            Flux.just(
                toChatResponse(
                    streamChat(messages, memoryId)
                        .toList(mutableListOf()),
                )
            )
        }
    }

    fun doChat(chatRequest: Prompt?): ChatResponse {
        val request = requireNotNull(chatRequest) { "chatRequest must not be null" }
        val memoryId = resolveMemoryId(chatRequest)
        val hasSession = sessionExists(memoryId)
        val sessionContext = getOrCreateSession(memoryId, chatRequest)
        val messages = resolveToSendMessages(chatRequest, hasSession)
        return invokeChat(
            Prompt.builder().messages(messages).chatOptions(chatRequest.options).build(),
            sessionContext,
            memoryId
        )
    }

    fun resolveToSendMessages(messages: Prompt, hasSession: Boolean): List<Message> {
        val memoryId = resolveMemoryId(messages)
//        return if (hasSession) {
//            messages.instructions
//        } else {
        return resolveMessages(messages, memoryId)
//        }
    }

    suspend fun streamChat(prompt: Prompt, memoryId: Any?): Flow<Generation> {
        val hasSession = sessionExists(memoryId)
        val session = getOrCreateSession(memoryId, prompt)

        val messages = resolveToSendMessages(prompt, hasSession)

        val content = listOf(
            ContentBlock.Text(
                formatPromptMessages(
                    Prompt.builder().messages(messages).chatOptions(prompt.options).build()
                )
            )
        )

        return session.prompt(content)
            .transform { event ->
                parseGenerationsFromAcpEvent(event, session, memoryId).forEach { emit(it) }
            }
            .onCompletion {
                session.flushWindows(memoryId).forEach { emit(it) }
            }
    }

    fun invokeChat(
        messages: Prompt,
        sessionContext: AcpSessionManager.AcpSessionContext,
        memoryId: Any?
    ): ChatResponse = runBlocking {
        val session = getOrCreateSession(memoryId, messages)
        val generations = mutableListOf<Generation>()
        val content = listOf(ContentBlock.Text(formatPromptMessages(messages)))

        session.prompt(content)
            .transform { event ->
                parseGenerationsFromAcpEvent(event, sessionContext, memoryId).forEach { emit(it) }
            }
            .collect { generations.add(it) }

        generations.addAll(session.flushWindows(memoryId))

        val unparsedToolCall = detectUnparsedToolCallInLastMessage(generations)
        if (unparsedToolCall != null) {
            val err = "ACP returned unparsed tool call as final structured output: $unparsedToolCall"
            log.warn(err)
            sessionManager.eventBus.publish(Events.NodeErrorEvent.err(err, sessionContext.chatModelKey))

            val retryGenerations = mutableListOf<Generation>()
            val retryContent = listOf(
                ContentBlock.Text(
                    "The last thing you sent was parsed as a tool call: $unparsedToolCall. " +
                            "Do not return a tool call as final structured output. " +
                            "Please continue with either a different tool call that can actually execute, " +
                            "or return the required structured response for this step."
                )
            )
            session.prompt(retryContent)
                .transform { event ->
                    parseGenerationsFromAcpEvent(event, sessionContext, memoryId).forEach { emit(it) }
                }
                .collect { retryGenerations.add(it) }

            retryGenerations.addAll(session.flushWindows(memoryId))
            return@runBlocking toChatResponse(retryGenerations)
        }

        toChatResponse(generations)
    }

    private fun toChatResponse(generations: List<Generation>): ChatResponse = ChatResponse.builder()
        .generations(generations.toMutableList())
        .build()

    fun createProcessStdioTransport(
        coroutineScope: CoroutineScope,
        command: Array<String>,
        extraEnv: Map<String, String>,
        dir: Path
    ): Transport {
        val mergedEnv = mutableMapOf<String, String>()
        this.properties.envCopy()
            ?.forEach { (envKey, envValue) -> mergedEnv[envKey] = envValue }
        extraEnv.forEach { (envKey, envValue) -> mergedEnv[envKey] = envValue }
        val effectivePath = sanitizePath(
            mergedEnv["PATH"]
                ?: System.getenv("PATH")
        )
        if (!effectivePath.isNullOrBlank()) {
            mergedEnv["PATH"] = effectivePath
        }

        val resolvedCommand = resolveExecutableCommand(command, effectivePath)
        val errLogName = run {
            val first = resolvedCommand.firstOrNull().orEmpty()
            val pathFirst = if (first.isBlank()) "acp-command" else first
            val fileName = Path.of(pathFirst).fileName?.toString()
            if (fileName.isNullOrBlank()) "acp-command" else fileName
        }

        val pb = ProcessBuilder(*resolvedCommand)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(File("%s-errs.log".format(errLogName)))
            .directory(dir.toFile())

        pb.environment()["CLAUDECODE"] = "0"
        mergedEnv.forEach { (envKey, envValue) -> pb.environment()[envKey] = envValue }
        if (!effectivePath.isNullOrBlank()) {
            pb.environment()["PATH"] = effectivePath
        }

        val process = try {
            pb.start()
        } catch (ex: IOException) {
            val renderedCommand = resolvedCommand.joinToString(" ")
            val renderedPath = pb.environment()["PATH"].orEmpty()
            throw IOException(
                "Failed to start ACP command '$renderedCommand' in '$dir' (PATH='$renderedPath'): ${ex.message}",
                ex
            )
        }

        val stdin = process.outputStream.asSink().buffered()
        val stdout = process.inputStream.asSource().buffered()
        return AcpSerializerTransport(
            parentScope = coroutineScope,
            ioDispatcher = Dispatchers.IO,
            input = stdout,
            output = stdin
        )
    }

    private fun sanitizePath(pathValue: String?): String? {
        if (pathValue.isNullOrBlank()) {
            return pathValue
        }

        val cleaned = pathValue.split(File.pathSeparator)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { File(it).isDirectory }
            .distinct()
            .toMutableList()

        val home = System.getProperty("user.home")
        val preferred = listOf(
            "$home/.asdf/bin",
            "$home/.asdf/shims",
            "$home/.local/bin"
        )
        preferred.asReversed()
            .filter { File(it).isDirectory }
            .forEach { preferredDir ->
                if (!cleaned.contains(preferredDir)) {
                    cleaned.add(0, preferredDir)
                }
            }

        return cleaned.joinToString(File.pathSeparator)
    }

    private fun resolveExecutableCommand(command: Array<String>, pathValue: String?): Array<String> {
        if (command.isEmpty()) {
            return command
        }

        val executable = command.first()
        val hasPathSeparator = executable.contains("/") || executable.contains("\\")
        if (hasPathSeparator) {
            return command
        }

        val resolved = findExecutableOnPath(executable, pathValue)
        if (resolved == null) {
            return command
        }

        val args = command.drop(1).toTypedArray()
        return arrayOf(resolved, *args)
    }

    private fun findExecutableOnPath(executable: String, pathValue: String?): String? {
        if (pathValue.isNullOrBlank()) {
            return null
        }

        val pathEntries = pathValue.split(File.pathSeparator)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (entry in pathEntries) {
            val candidate = File(entry, executable)
            if (!candidate.isFile) {
                continue
            }
            if (!candidate.canExecute()) {
                continue
            }
            return candidate.absolutePath
        }

        return null
    }

    private fun sessionExists(memoryId: Any?): Boolean {
        return memoryId != null && sessionManager.sessionContexts.containsKey(memoryId)
    }

    private fun resolveMessages(chatRequest: Prompt, memoryId: Any?): List<Message> {
        if (memoryId == null) {
            return chatRequest.instructions
        }
        val history = chatMemoryContext?.getMessages(memoryId).orEmpty()
        return if (history.isNotEmpty()) history else chatRequest.instructions
    }

    private fun resolveMemoryId(chatRequest: Prompt): Any? {
        val chatModel = chatRequest.options?.model ?: return ArtifactKey.createRoot()

        if (chatModel.contains("___")) {
            val splitted = chatModel.split("___")
            return splitted[0]
        }

        try {
            return chatModel
        } catch (e: IllegalArgumentException) {
            log.error("Error attempting to cast artifact key to root: {}", e.message, e)
            return ArtifactKey.createRoot()
        }
    }

    private fun getOrCreateSession(memoryId: Any?, chatRequest: Prompt?): AcpSessionManager.AcpSessionContext {
        val m = memoryId ?: ArtifactKey.createRoot().value
        return sessionManager.sessionContexts.computeIfAbsent(m) {
            runBlocking { createSessionContext(it, chatRequest) }
        }
    }

    private suspend fun createSessionContext(
        memoryId: Any?,
        chatRequest: Prompt?
    ): AcpSessionManager.AcpSessionContext {
        log.info("Creating session context for $memoryId")

        if (!properties.transport.equals("stdio", ignoreCase = true)) {
            throw IllegalStateException("Only stdio transport is supported for ACP integration")
        }

        val command = properties.command?.trim()?.split(Regex("\\s+"))?.toTypedArray()

        if (command == null || command.size == 0) {
            throw IllegalStateException("ACP command is not configured")
        }

        val sandboxTranslation = resolveSandboxTranslation(memoryId, properties.args)
        val process = command + sandboxTranslation.args.toTypedArray()
        val workingDirectory = properties.workingDirectory

        val joinedEnv = sandboxTranslation.env.toMutableMap()
//        joinedEnv["user.dir"] = sandboxTranslation.workingDirectory
//        joinedEnv["PATH"] = "" TODO: the path should probably include all docker env, java home, etc...
        joinedEnv.putAll(properties.envCopy())

        // Use sandbox translation working directory if available, otherwise fall back to properties or system default
        var cwd = workingDirectoryOrNull(sandboxTranslation)
            .or {
                if (workingDirectory == null || workingDirectory.isBlank())
                    null
                else
                    workingDirectory
            }
            .or { System.getProperty("user.dir") }!!

        if (cwd.isBlank())
            cwd = System.getProperty("user.dir")

        return try {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val transport = createProcessStdioTransport(scope, process, joinedEnv, Path.of(cwd))
            val protocol = Protocol(scope, transport)
            val client = Client(protocol)

            val agentInfo = protocol.start()

            properties.authMethod?.let {
                val authenticationResult = client.authenticate(AuthMethodId(it))
                log.info("Authenticated with ACP {}", authenticationResult)
            }

            val initialized = client.initialize(
                ClientInfo(
                    capabilities = ClientCapabilities(
                        fs = FileSystemCapability(
                            readTextFile = true,
                            writeTextFile = true
                        ),
                        terminal = true
                    )
                )
            )

            log.info("Agent info: ${initialized.implementation.toString()}")

            val toolAllowlist = mutableSetOf<String>()
            val mcpSyncServers: MutableSet<McpServer> = mutableSetOf()

            if (chatRequest?.options is ToolCallingChatOptions) {
                val options = chatRequest.options as ToolCallingChatOptions
                toolAllowlist.addAll(options.toolNames)

                options.toolCallbacks.map { it.toolDefinition.name() }
                    .map {
                        if (it.contains(".")) {
                            val splitted = it.split(".")
                            splitted.subList(1, splitted.size).joinToString(".")
                        } else
                            it
                    }
                    .forEach { toolAllowlist.add(it) }

                options.toolCallbacks.map { it.toolDefinition }
                    .mapNotNull {
                        this.mcpProperties.retrieve(it)
                            .orElse(null)
                    }
                    .distinct()
                    .forEach { mcpSyncServers.add(it) }

                options.toolCallbacks
                    .mapNotNull {
                        when (it) {
                            is SyncMcpToolCallback -> Pair(it.toolDefinition, it.toolMetadata)
                            is AsyncMcpToolCallback -> Pair(it.toolDefinition, it.toolMetadata)
                            else -> null
                        }
                    }
                    .mapNotNull {
                        this.mcpProperties.retrieve(it.first)
                            .orElse(null)
                    }
                    .distinct()
                    .forEach { mcpSyncServers.add(it) }
            }

            val toolHeaders = mutableListOf(
                HttpHeader(TOOL_ALLOWLIST_HEADER, toolAllowlist.joinToString(","))
            )
            if (memoryId != null) {
                toolHeaders.add(HttpHeader(MCP_SESSION_HEADER, memoryId.toString()))
            }

            // Only add the local MCP server if it's available

            if (this.mcpProperties.didEnableSelf()) {
                mcpSyncServers.add(McpServer.Http("agent-tools", "http://localhost:8080/mcp", toolHeaders))
            }


            val sessionParams = SessionCreationParameters(cwd, mcpSyncServers.toList())

            val chatKey = memoryId
                .mapNullable { ArtifactKey(it.toString()) }
                .or { ArtifactKey.createRoot() }
                ?: ArtifactKey.createRoot()

            val messageParent = chatKey.createChild()

            val session = client.newSession(sessionParams)
            { _, _ -> AcpSessionOperations(permissionGate, chatKey.value) }

            sessionManager.AcpSessionContext(
                scope, transport, protocol, client, session,
                messageParent = messageParent, chatModelKey = chatKey
            )

        } catch (ex: Exception) {
            throw IllegalStateException("Failed to initialize ACP session", ex)
        }
    }

    private fun workingDirectoryOrNull(sandboxTranslation: SandboxTranslation): String? {
        return if (sandboxTranslation.workingDirectory() == null)
            null
        else if (sandboxTranslation.workingDirectory().isBlank())
            null
        else
            sandboxTranslation.workingDirectory
    }

    fun parseArgs(args: String?): List<String> {
        if (args.isNullOrBlank()) {
            return emptyList()
        }
        val tokenizer = StringTokenizer(args)
        val tokens = mutableListOf<String>()
        while (tokenizer.hasMoreTokens()) {
            tokens.add(tokenizer.nextToken())
        }
        return tokens.filter { it.isNotEmpty() }
    }

    fun resolveSandboxTranslation(memoryId: Any?, args: String?): SandboxTranslation {
        val sessionId = memoryId?.toString() ?: return SandboxTranslation.empty()
        val context =
            requestContextRepository.findBySessionId(sessionId).orElse(null) ?: return SandboxTranslation.empty()
        val providerKey = resolveProviderKey()
        val direct = sandboxTranslationRegistry.find(providerKey).orElse(null)
        if (direct != null) {
            return direct.translate(context, parseArgs(args))
        }
        val fallbackKey = providerKey.substringBefore("-")
        val fallback = sandboxTranslationRegistry.find(fallbackKey).orElse(null)
        return fallback?.translate(context, parseArgs(args)) ?: SandboxTranslation.empty()
    }

    fun resolveProviderKey(): String {
        val commandValue = properties.command?.trim().orEmpty()
        if (commandValue.isBlank()) {
            return ""
        }
        val executableAcp = commandValue
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.lowercase() ?: ""

        return if (executableAcp.isEmpty())
            executableAcp
        else
            Path.of(executableAcp).fileName.toString()
    }

    private fun detectUnparsedToolCallInLastMessage(generations: List<Generation>): String? {
        val lastMessage = generations
            .asReversed()
            .mapNotNull { generation -> runCatching { generation.output.text }.getOrNull() }
            .firstOrNull { it.isNotBlank() }
            ?: return null

        return extractToolCallNameFromLastStructuredPayload(lastMessage)
    }

    internal fun extractToolCallNameFromLastStructuredPayload(lastMessage: String): String? {
        if (lastMessage.isBlank()) {
            return null
        }

        val trailingJson = extractTrailingJsonObject(lastMessage)
        if (trailingJson != null) {
            val tree = runCatching { objectMapper.readTree(trailingJson) }.getOrNull()
            if (tree != null && tree.isObject) {
                val toolName = tree.get("tool_name")?.asText()
                    ?: tree.get("name")?.asText()
                if (!toolName.isNullOrBlank()) {
                    return toolName
                }
            }
        }

        val trailingToolCallXml = Regex("""<tool_call>\s*([A-Za-z0-9_./-]+)""")
            .find(lastMessage.trimEnd())
            ?.groupValues
            ?.getOrNull(1)
        return if (trailingToolCallXml.isNullOrBlank()) null else trailingToolCallXml
    }

    private fun extractTrailingJsonObject(text: String): String? {
        val trimmed = text.trimEnd()
        if (!trimmed.endsWith("}")) {
            return null
        }

        var depth = 0
        var inString = false
        var escaping = false
        var start = -1

        for (i in trimmed.lastIndex downTo 0) {
            val c = trimmed[i]
            if (inString) {
                if (escaping) {
                    escaping = false
                } else if (c == '\\') {
                    escaping = true
                } else if (c == '"') {
                    inString = false
                }
                continue
            }

            when (c) {
                '"' -> inString = true
                '}' -> depth++
                '{' -> {
                    depth--
                    if (depth == 0) {
                        start = i
                        break
                    }
                }
            }
        }

        if (start < 0) {
            return null
        }
        return trimmed.substring(start)
    }

    private fun formatPromptMessages(messages: Prompt): String {
        if (messages.instructions.isEmpty()) {
            return ""
        }
        val builder = StringBuilder()
        fun formatMessageRole(role: String, message: Message): String = "$role ${message.text}"

        messages.instructions.forEach { message ->
            val role = resolveRole(message)
            if (builder.isNotEmpty()) {
                builder.append('\n')
            }
            when (message) {
                is UserMessage -> builder.append(formatMessageRole(role, message))
                is AssistantMessage -> builder.append(formatMessageRole(role, message))
                is SystemMessage -> builder.append(formatMessageRole(role, message))
                is ToolResponseMessage -> {}
            }
        }

        return builder.toString()
    }

    private fun resolveRole(message: Message): String = when (message) {
        is UserMessage -> MessageType.USER.name
        is SystemMessage -> MessageType.SYSTEM.name
        is AssistantMessage -> MessageType.ASSISTANT.name
        is ToolResponseMessage -> MessageType.TOOL.name
        else -> "user"
    }

    private class AcpSessionOperations(
        private val permissionGate: IPermissionGate,
        private val originNodeId: String
    ) : ClientSessionOperations {

        private val activeTerminals = ConcurrentHashMap<String, Process>()

        override suspend fun requestPermissions(
            toolCall: SessionUpdate.ToolCallUpdate,
            permissions: List<PermissionOption>,
            _meta: JsonElement?
        ): RequestPermissionResponse {
            val requestId = toolCall.toolCallId.value
            permissionGate.publishRequest(requestId, originNodeId, toolCall, permissions, _meta)
            return permissionGate.awaitResponse(requestId)
        }

        override suspend fun fsReadTextFile(
            path: String,
            line: UInt?,
            limit: UInt?,
            _meta: JsonElement?
        ): ReadTextFileResponse {

            if (StringUtils.isBlank(path) || !Paths.get(path).toFile().exists()) {
                return ReadTextFileResponse("Path did not exist.")
            }

            val p = Paths.get(path)

            if (line == null && limit == null && p.toFile().exists()) {
                return ReadTextFileResponse(p.readText())
            }

            val lines = p.readLines()
            val startIndex = line?.toInt()?.coerceAtLeast(1)?.minus(1) ?: 0
            val endExclusive = limit
                ?.toInt()
                ?.let { (startIndex + it).coerceAtMost(lines.size) }
                ?: lines.size
            val sliced = if (startIndex >= lines.size) emptyList() else lines.subList(startIndex, endExclusive)
            val content = sliced.joinToString("\n")

            return ReadTextFileResponse(content)
        }

        override suspend fun fsWriteTextFile(
            path: String,
            content: String,
            _meta: JsonElement?
        ): WriteTextFileResponse {
            Paths.get(path).writeText(content)
            return WriteTextFileResponse()
        }

        override suspend fun notify(notification: SessionUpdate, _meta: JsonElement?) {
        }

        override suspend fun terminalCreate(
            command: String,
            args: List<String>,
            cwd: String?,
            env: List<EnvVariable>,
            outputByteLimit: ULong?,
            _meta: JsonElement?,
        ): CreateTerminalResponse {
            val processBuilder = ProcessBuilder(listOf(command) + args)
            if (cwd != null) {
                processBuilder.directory(File(cwd))
            }
            env.forEach { processBuilder.environment()[it.name] = it.value }

            val process = processBuilder.start()
            val terminalId = UUID.randomUUID().toString()
            activeTerminals[terminalId] = process

            return CreateTerminalResponse(terminalId)
        }

        override suspend fun terminalOutput(
            terminalId: String,
            _meta: JsonElement?,
        ): TerminalOutputResponse {
            val process = activeTerminals[terminalId] ?: error("Terminal not found: $terminalId")
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val output = if (stderr.isNotEmpty()) "$stdout\nSTDERR:\n$stderr" else stdout

            return TerminalOutputResponse(output, truncated = false)
        }

        override suspend fun terminalRelease(
            terminalId: String,
            _meta: JsonElement?,
        ): ReleaseTerminalResponse {
            activeTerminals.remove(terminalId)
            return ReleaseTerminalResponse()
        }

        override suspend fun terminalWaitForExit(
            terminalId: String,
            _meta: JsonElement?,
        ): WaitForTerminalExitResponse {
            val process = activeTerminals[terminalId] ?: error("Terminal not found: $terminalId")
            val exitCode = process.waitFor()
            return WaitForTerminalExitResponse(exitCode.toUInt())
        }

        override suspend fun terminalKill(
            terminalId: String,
            _meta: JsonElement?,
        ): KillTerminalCommandResponse {
            val process = activeTerminals[terminalId]
            process?.destroy()
            return KillTerminalCommandResponse()
        }
    }
}
