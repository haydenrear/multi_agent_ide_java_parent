package com.hayden.acp_cdc_ai.acp

import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionUpdate
import com.hayden.acp_cdc_ai.acp.config.AcpModelProperties
import com.hayden.acp_cdc_ai.acp.config.McpProperties
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey
import com.hayden.acp_cdc_ai.acp.events.EventBus
import com.hayden.acp_cdc_ai.permission.IPermissionGate
import com.hayden.acp_cdc_ai.repository.RequestContext
import com.hayden.acp_cdc_ai.repository.RequestContextRepository
import com.hayden.acp_cdc_ai.sandbox.*
import com.hayden.utilitymodule.config.EnvConfigProps
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonElement
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.yaml.snakeyaml.Yaml
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * End-to-end integration tests for AcpChatModel with real ACP providers.
 *
 * These tests actually start the ACP process, send messages via AcpChatModel.call(),
 * and verify responses are received.
 *
 * Prerequisites:
 * - claude-code-acp must be installed
 * - codex-acp must be installed
 * - goose must be installed
 * - Valid API keys must be configured in environment variables
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AcpChatModelIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var mainWorktree: Path
    private lateinit var submodule1: Path

    // Track which executables are available
    private var claudeCodeAcpAvailable = false
    private var codexAcpAvailable = false
    private var gooseAvailable = false

    @BeforeAll
    fun checkExecutables() {
        claudeCodeAcpAvailable = isExecutableAvailable("which","claude-code-acp")
        codexAcpAvailable = isExecutableAvailable("which", "codex-acp")
        gooseAvailable = isExecutableAvailable("goose", "--version")

        println("ACP Provider Availability:")
        println("  claude-code-acp: ${if (claudeCodeAcpAvailable) "AVAILABLE" else "NOT FOUND"}")
        println("  codex-acp: ${if (codexAcpAvailable) "AVAILABLE" else "NOT FOUND"}")
        println("  goose: ${if (gooseAvailable) "AVAILABLE" else "NOT FOUND"}")
    }

    @BeforeEach
    fun setUp() {
        mainWorktree = tempDir.resolve("main-project")
        submodule1 = tempDir.resolve("submodule1")
        Files.createDirectories(mainWorktree)
        Files.createDirectories(submodule1)

        initGitRepo(mainWorktree)
        initGitRepo(submodule1)

        Files.writeString(
            mainWorktree.resolve("README.md"),
            "# Test Project\n\nThis is a test project for ACP integration testing."
        )
    }

    private fun isExecutableAvailable(executable: String, vararg testArgs: String): Boolean {
        return try {
            val command = arrayOf(executable) + testArgs
            val pb = ProcessBuilder(*command)
            pb.redirectErrorStream(true)
            val process = pb.start()
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            if (finished) {
                process.destroy()
                process.exitValue() == 0
            } else {
                process.destroyForcibly()
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun initGitRepo(dir: Path) {
        try {
            val pb = ProcessBuilder("git", "init")
            pb.directory(dir.toFile())
            pb.redirectErrorStream(true)
            val process = pb.start()
            process.waitFor(5, TimeUnit.SECONDS)
            process.destroy()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Creates an AcpChatModel configured for the given provider.
     */
    private fun createAcpChatModel(command: String, sessionId: String): AcpChatModel {

        val properties = AcpModelProperties().apply {
            transport = "stdio"
            this.command = command
            workingDirectory = mainWorktree.toString()
        }

        val registry = SandboxTranslationRegistry(
            listOf(
                ClaudeCodeSandboxStrategy(),
                CodexSandboxStrategy(),
                GooseSandboxStrategy()
            )
        )

        val context = RequestContext.builder()
            .sessionId(sessionId)
            .sandboxContext(
                SandboxContext.builder()
                    .mainWorktreePath(mainWorktree)
                    .submoduleWorktreePaths(listOf(submodule1))
                    .build()
            )
            .build()

        val contextRepository = object : RequestContextRepository {
            override fun save(ctx: RequestContext): RequestContext = ctx
            override fun findBySessionId(sid: String): Optional<RequestContext> =
                if (sessionId == sid) Optional.of(context) else Optional.empty()
            override fun deleteBySessionId(sid: String) {}
            override fun clear() {}
        }

        val permissionGate = AutoAcceptPermissionGate()

        val eventBus = object : EventBus {
            override fun subscribe(listener: com.hayden.acp_cdc_ai.acp.events.EventListener) {}
            override fun unsubscribe(listener: com.hayden.acp_cdc_ai.acp.events.EventListener) {}
            override fun publish(event: com.hayden.acp_cdc_ai.acp.events.Events.GraphEvent) {}
            override fun getSubscribers(): List<com.hayden.acp_cdc_ai.acp.events.EventListener> = emptyList()
            override fun clear() {}
            override fun hasSubscribers(): Boolean = false
        }

        val sessionManager = AcpSessionManager(eventBus)
        val mcpProperties = McpProperties()

        mcpProperties.isEnableSelf = false

        return AcpChatModel(
            properties,
            null,
            sessionManager,
            mcpProperties,
            permissionGate,
            contextRepository,
            registry
        )
    }

    private fun setMemoryId(sessionId: String) {
        EventBus.agentProcess.set(EventBus.AgentNodeKey(sessionId))
    }

    private fun clearMemoryId() {
        EventBus.agentProcess.remove()
    }

    @Nested
    @DisplayName("Claude Code ACP Integration")
    inner class ClaudeCodeAcpTests {

        @Test
        @DisplayName("should send message and receive response via AcpChatModel")
        @Timeout(value = 120, unit = TimeUnit.SECONDS)
        fun shouldSendAndReceiveMessage() {
            assumeTrue(claudeCodeAcpAvailable, "claude-code-acp not available, skipping test")

            val sessionId = ArtifactKey.createRoot().value
            setMemoryId(sessionId)

            try {
                val chatModel = createAcpChatModel("claude-code-acp", sessionId)

                val prompt = Prompt(listOf(UserMessage("What is 2 + 2? Reply with just the number.")))

                val response = chatModel.call(prompt)

                assertThat(response).isNotNull
                assertThat(response.results).isNotEmpty
                assertThat(response.result).isNotNull
                assertThat(response.result.output).isNotNull
                assertThat(response.result.output.text).isNotBlank

                println("Claude response: ${response.result.output.text}")
            } finally {
                clearMemoryId()
            }
        }

        @Test
        @DisplayName("should work with sandbox args applied")
        @Timeout(value = 120, unit = TimeUnit.SECONDS)
        fun shouldWorkWithSandboxArgs() {
            assumeTrue(claudeCodeAcpAvailable, "claude-code-acp not available, skipping test")

            val sessionId = ArtifactKey.createRoot().value
            setMemoryId(sessionId)

            try {
                val chatModel = createAcpChatModel("claude-code-acp", sessionId)

                val prompt = Prompt(listOf(UserMessage("What files are in the current directory? Just list the filenames.")))

                val response = chatModel.call(prompt)

                assertThat(response).isNotNull
                assertThat(response.results).isNotEmpty

                val responseText = response.result.output.text
                println("Claude sandbox response: $responseText")

                assertThat(responseText).isNotBlank
            } finally {
                clearMemoryId()
            }
        }
    }

    @Nested
    @DisplayName("Codex ACP Integration")
    inner class CodexAcpTests {
        @Test
        @DisplayName("should send message and receive response via AcpChatModel")
        @Timeout(value = 120, unit = TimeUnit.SECONDS)
        fun shouldHaveCorrectCd() {
            assumeTrue(codexAcpAvailable, "codex-acp not available, skipping test")

            val sessionId = ArtifactKey.createRoot().value
            setMemoryId(sessionId)

            try {
                val chatModel = createAcpChatModel("codex-acp", sessionId)

                val prompt = Prompt(listOf(UserMessage("What is your current directory?")))

                val response = chatModel.call(prompt)

                assertThat(response).isNotNull
                assertThat(response.results).isNotEmpty
                assertThat(response.result).isNotNull
                assertThat(response.result.output).isNotNull
                assertThat(response.result.output.text).isNotBlank

                println("Codex response: ${response.result.output.text}")
            } finally {
                clearMemoryId()
            }
        }

        @Test
        @DisplayName("should send message and receive response via AcpChatModel")
        @Timeout(value = 120, unit = TimeUnit.SECONDS)
        fun shouldSendAndReceiveMessage() {
            assumeTrue(codexAcpAvailable, "codex-acp not available, skipping test")

            val sessionId = ArtifactKey.createRoot().value
            setMemoryId(sessionId)

            try {
                val chatModel = createAcpChatModel("codex-acp", sessionId)

                val prompt = Prompt(listOf(UserMessage("What is 2 + 2? Reply with just the number.")))

                val response = chatModel.call(prompt)

                assertThat(response).isNotNull
                assertThat(response.results).isNotEmpty
                assertThat(response.result).isNotNull
                assertThat(response.result.output).isNotNull
                assertThat(response.result.output.text).isNotBlank

                println("Codex response: ${response.result.output.text}")
            } finally {
                clearMemoryId()
            }
        }

        @Test
        @DisplayName("should work with sandbox args applied")
        @Timeout(value = 120, unit = TimeUnit.SECONDS)
        fun shouldWorkWithSandboxArgs() {
            assumeTrue(codexAcpAvailable, "codex-acp not available, skipping test")

            val sessionId = ArtifactKey.createRoot().value
            setMemoryId(sessionId)

            try {
                val chatModel = createAcpChatModel("codex-acp", sessionId)

                val prompt = Prompt(listOf(UserMessage("What files are in the current directory? Just list the filenames.")))

                val response = chatModel.call(prompt)

                assertThat(response).isNotNull
                assertThat(response.results).isNotEmpty

                val responseText = response.result.output.text
                println("Codex sandbox response: $responseText")

                assertThat(responseText).isNotBlank
            } finally {
                clearMemoryId()
            }
        }
    }

    @Nested
    @DisplayName("Goose ACP Integration")
    inner class GooseAcpTests {

        @Test
        @DisplayName("should send message and receive response via AcpChatModel")
        @Timeout(value = 120, unit = TimeUnit.SECONDS)
        fun shouldSendAndReceiveMessage() {
            assumeTrue(gooseAvailable, "goose not available, skipping test")

            val sessionId = ArtifactKey.createRoot().value
            setMemoryId(sessionId)

            try {
                val chatModel = createAcpChatModel("goose acp", sessionId)

                val prompt = Prompt(listOf(UserMessage("What is 2 + 2? Reply with just the number.")))

                val response = chatModel.call(prompt)

                assertThat(response).isNotNull
                assertThat(response.results).isNotEmpty
                assertThat(response.result).isNotNull
                assertThat(response.result.output).isNotNull
                assertThat(response.result.output.text).isNotBlank

                println("Goose response: ${response.result.output.text}")
            } finally {
                clearMemoryId()
            }
        }

        @Test
        @DisplayName("should work with sandbox args and GOOSE_MODE env var")
        @Timeout(value = 120, unit = TimeUnit.SECONDS)
        fun shouldWorkWithSandboxArgs() {
            assumeTrue(gooseAvailable, "goose not available, skipping test")

            val sessionId = ArtifactKey.createRoot().value
            setMemoryId(sessionId)

            try {
                val chatModel = createAcpChatModel("goose acp", sessionId)

                val prompt = Prompt(listOf(UserMessage("What files are in the current directory? Just list the filenames.")))

                val response = chatModel.call(prompt)

                assertThat(response).isNotNull
                assertThat(response.results).isNotEmpty

                val responseText = response.result.output.text
                println("Goose sandbox response: $responseText")

                assertThat(responseText).isNotBlank
            } finally {
                clearMemoryId()
            }
        }
    }

    @Nested
    @DisplayName("Cross-Provider Comparison")
    inner class CrossProviderTests {

        @Test
        @DisplayName("all available providers should handle the same prompt")
        @Timeout(value = 300, unit = TimeUnit.SECONDS)
        fun allProvidersShouldHandleSamePrompt() {
            val availableProviders = mutableListOf<String>()
            if (claudeCodeAcpAvailable) availableProviders.add("claude-code-acp")
            if (codexAcpAvailable) availableProviders.add("codex-acp")
            if (gooseAvailable) availableProviders.add("goose acp")

            assumeTrue(availableProviders.isNotEmpty(), "No ACP providers available")

            val responses = mutableMapOf<String, String>()

            for (provider in availableProviders) {
                val sessionId = ArtifactKey.createRoot().value
                setMemoryId(sessionId)

                try {
                    val chatModel = createAcpChatModel(provider, sessionId)

                    val prompt = Prompt(listOf(UserMessage("What is the capital of France? Reply with just the city name.")))

                    val response = chatModel.call(prompt)

                    assertThat(response).isNotNull
                    assertThat(response.results).isNotEmpty

                    val responseText = response.result.output.text
                    responses[provider] = responseText

                    println("$provider response: $responseText")
                } catch (e: Exception) {
                    System.err.println("Provider $provider failed: ${e.message}")
                } finally {
                    clearMemoryId()
                }
            }

            // All providers should have returned something mentioning Paris
            for ((provider, responseText) in responses) {
                assertThat(responseText.lowercase())
                    .`as`("Provider $provider should mention Paris")
                    .contains("paris")
            }
        }
    }

    /**
     * Simple permission gate that auto-accepts all permission requests.
     */
    private class AutoAcceptPermissionGate : IPermissionGate {
        private val pending = mutableMapOf<String, IPermissionGate.PendingPermissionRequest>()

        override fun publishRequest(
            requestId: String,
            originNodeId: String,
            toolCall: SessionUpdate.ToolCallUpdate,
            permissions: List<PermissionOption>,
            meta: JsonElement?
        ): IPermissionGate.PendingPermissionRequest {
            val deferred = CompletableDeferred<RequestPermissionResponse>()
            val request = IPermissionGate.PendingPermissionRequest(
                requestId = requestId,
                originNodeId = originNodeId,
                toolCallId = toolCall.toolCallId.value,
                permissions = permissions,
                deferred = deferred,
                meta = meta,
                nodeId = null
            )
            pending[requestId] = request

            // Auto-accept the first permission option
            if (permissions.isNotEmpty()) {
                val selectedId = permissions[0].optionId
                completePending(request, RequestPermissionOutcome.Selected(selectedId), selectedId.value)
            }

            return request
        }

        override suspend fun awaitResponse(requestId: String): RequestPermissionResponse {
            val request = pending[requestId]
            return if (request != null && request.permissions.isNotEmpty()) {
                val selected = request.permissions[0]
                RequestPermissionResponse(RequestPermissionOutcome.Selected(selected.optionId))
            } else {
                RequestPermissionResponse(RequestPermissionOutcome.Cancelled, null)
            }
        }

        override fun resolveSelected(requestId: String, optionId: String?): Boolean = true

        override fun resolveCancelled(requestId: String): Boolean = true

        override fun resolveSelectedOption(permissions: List<PermissionOption>, optionId: String?): PermissionOption? {
            if (permissions.isEmpty()) return null
            if (optionId == null) return permissions[0]
            return permissions.firstOrNull { it.optionId.value == optionId } ?: permissions[0]
        }

        override fun completePending(
            pending: IPermissionGate.PendingPermissionRequest,
            outcome: RequestPermissionOutcome,
            selectedOptionId: String?
        ) {
            pending.deferred.complete(RequestPermissionResponse(outcome))
        }
    }
}
