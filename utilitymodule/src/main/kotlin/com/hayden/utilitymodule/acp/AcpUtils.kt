package com.hayden.utilitymodule.acp

import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallContent
import com.agentclientprotocol.model.ToolCallStatus
import com.hayden.utilitymodule.acp.events.Events
import org.springframework.ai.chat.model.Generation
import java.time.Instant
import java.util.UUID

fun extractText(content: ContentBlock): String? = when (content) {
    is ContentBlock.Text -> content.text
    is ContentBlock.ResourceLink -> content.title ?: content.name
    is ContentBlock.Resource -> content.resource.toString()
    is ContentBlock.Audio -> "[audio:${content.mimeType}]"
    is ContentBlock.Image -> "[image:${content.mimeType}]"
}

fun parseGenerationsFromAcpEvent(event: Event, sessionContext: AcpSessionManager.AcpSessionContext, memoryId: Any?): List<Generation> =
    if (event is Event.SessionUpdateEvent) {
        when (val update = event.update) {
            is SessionUpdate.AgentMessageChunk -> {
                val flushed = sessionContext.flushOtherWindows(memoryId, AcpStreamWindowBuffer.StreamWindowType.MESSAGE)
                sessionContext.appendStreamWindow(memoryId, AcpStreamWindowBuffer.StreamWindowType.MESSAGE, update.content)
                flushed
            }
            is SessionUpdate.UserMessageChunk -> {
                val flushed = sessionContext.flushOtherWindows(memoryId, AcpStreamWindowBuffer.StreamWindowType.USER_MESSAGE)
                sessionContext.appendStreamWindow(memoryId, AcpStreamWindowBuffer.StreamWindowType.USER_MESSAGE, update.content)
                flushed
            }
            is SessionUpdate.AgentThoughtChunk -> {
                val flushed = sessionContext.flushOtherWindows(memoryId, AcpStreamWindowBuffer.StreamWindowType.THOUGHT)
                sessionContext.appendStreamWindow(memoryId, AcpStreamWindowBuffer.StreamWindowType.THOUGHT, update.content)
                flushed
            }
            is SessionUpdate.AvailableCommandsUpdate -> {
                val flushed = sessionContext.flushOtherWindows(memoryId, AcpStreamWindowBuffer.StreamWindowType.AVAILABLE_COMMANDS)
                sessionContext.appendEventWindow(
                    memoryId, AcpStreamWindowBuffer.StreamWindowType.AVAILABLE_COMMANDS,
                    buildAvailableCommandsUpdateEvent(memoryId, update)
                )
                flushed
            }
            is SessionUpdate.CurrentModeUpdate -> {
                val flushed = sessionContext.flushOtherWindows(memoryId, AcpStreamWindowBuffer.StreamWindowType.CURRENT_MODE)
                sessionContext.appendEventWindow(
                    memoryId,
                    AcpStreamWindowBuffer.StreamWindowType.CURRENT_MODE,
                    buildCurrentModeUpdateEvent(memoryId, update)
                )
                flushed
            }
            is SessionUpdate.PlanUpdate -> {
                val flushed = sessionContext.flushOtherWindows(memoryId, AcpStreamWindowBuffer.StreamWindowType.PLAN)
                sessionContext.appendEventWindow(
                    memoryId,
                    AcpStreamWindowBuffer.StreamWindowType.PLAN,
                    buildPlanUpdateEvent(memoryId, update)
                )
                flushed
            }
            is SessionUpdate.ToolCall -> {
                val flushed = sessionContext.flushOtherWindows(memoryId, AcpStreamWindowBuffer.StreamWindowType.TOOL_CALL)
                sessionContext.appendEventWindow(memoryId, AcpStreamWindowBuffer.StreamWindowType.TOOL_CALL, buildToolCallEvent(memoryId, update, "START"))
                flushed
            }
            is SessionUpdate.ToolCallUpdate -> {
                val flushed = sessionContext.flushOtherWindows(memoryId, AcpStreamWindowBuffer.StreamWindowType.TOOL_CALL)
                sessionContext.appendEventWindow(memoryId, AcpStreamWindowBuffer.StreamWindowType.TOOL_CALL, buildToolCallUpdateEvent(memoryId, update))
                flushed
            }
        }
    } else {
        emptyList()
    }

private fun buildToolCallEvent(memoryId: Any?, update: SessionUpdate.ToolCall, phase: String): Events.ToolCallEvent {
    val nodeId = memoryId?.toString() ?: "unknown"
    return Events.ToolCallEvent(
        UUID.randomUUID().toString(),
        Instant.now(),
        nodeId,
        update.toolCallId.value,
        update.title,
        update.kind?.name,
        update.status?.name,
        phase,
        update.content.map { toolCallContentToMap(it) },
        update.locations.map { location -> mapOf("path" to location.path, "line" to location.line) },
        update.rawInput?.toString(),
        update.rawOutput?.toString()
    )
}

private fun buildToolCallUpdateEvent(memoryId: Any?, update: SessionUpdate.ToolCallUpdate): Events.ToolCallEvent {
    val phase = when (update.status) {
        ToolCallStatus.COMPLETED -> "RESULT"
        ToolCallStatus.FAILED -> "RESULT"
        ToolCallStatus.IN_PROGRESS -> "UPDATE"
        ToolCallStatus.PENDING -> "ARGS"
        null -> "UPDATE"
    }
    val nodeId = memoryId?.toString() ?: "unknown"
    return Events.ToolCallEvent(
        UUID.randomUUID().toString(),
        Instant.now(),
        nodeId,
        update.toolCallId.value,
        update.title ?: "tool_call",
        update.kind?.name,
        update.status?.name,
        phase,
        update.content?.map { toolCallContentToMap(it) } ?: emptyList(),
        update.locations?.map { location -> mapOf("path" to location.path, "line" to location.line) } ?: emptyList(),
        update.rawInput?.toString(),
        update.rawOutput?.toString()
    )
}

private fun buildPlanUpdateEvent(memoryId: Any?, update: SessionUpdate.PlanUpdate): Events.PlanUpdateEvent {
    val nodeId = memoryId?.toString() ?: "unknown"
    val entries = update.entries.map { entry ->
        mapOf(
            "content" to entry.content,
            "priority" to entry.priority.name,
            "status" to entry.status.name
        )
    }
    return Events.PlanUpdateEvent(
        UUID.randomUUID().toString(),
        Instant.now(),
        nodeId,
        entries
    )
}


private fun buildCurrentModeUpdateEvent(
    memoryId: Any?,
    update: SessionUpdate.CurrentModeUpdate
): Events.CurrentModeUpdateEvent {
    val nodeId = memoryId?.toString() ?: "unknown"
    return Events.CurrentModeUpdateEvent(
        UUID.randomUUID().toString(),
        Instant.now(),
        nodeId,
        update.currentModeId.value
    )
}

private fun buildAvailableCommandsUpdateEvent(
    memoryId: Any?,
    update: SessionUpdate.AvailableCommandsUpdate
): Events.AvailableCommandsUpdateEvent {
    val nodeId = memoryId?.toString() ?: "unknown"
    val commands = update.availableCommands.map { command ->
        mapOf(
            "name" to command.name,
            "description" to command.description,
            "input" to command.input?.toString()
        )
    }
    return Events.AvailableCommandsUpdateEvent(
        UUID.randomUUID().toString(),
        Instant.now(),
        nodeId,
        commands
    )
}

private fun toolCallContentToMap(content: ToolCallContent): Map<String, Any?> = when (content) {
    is ToolCallContent.Content -> mapOf(
        "type" to "content",
        "content" to (extractText(content.content) ?: content.content.toString())
    )
    is ToolCallContent.Diff -> mapOf(
        "type" to "diff",
        "path" to content.path,
        "newText" to content.newText,
        "oldText" to content.oldText
    )
    is ToolCallContent.Terminal -> mapOf(
        "type" to "terminal",
        "terminalId" to content.terminalId
    )
}

