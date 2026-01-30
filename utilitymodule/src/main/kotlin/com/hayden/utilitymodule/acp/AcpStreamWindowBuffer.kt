package com.hayden.utilitymodule.acp

import com.agentclientprotocol.model.ContentBlock
import com.hayden.utilitymodule.acp.events.EventBus
import com.hayden.utilitymodule.acp.events.Events
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.Generation
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AcpStreamWindowBuffer(private val eventBus: EventBus) {

    enum class StreamWindowType {
        MESSAGE,
        THOUGHT,
        TOOL_CALL,
        PLAN,
        USER_MESSAGE,
        CURRENT_MODE,
        AVAILABLE_COMMANDS
    }

    data class StreamKey(
        val nodeId: String,
        val type: StreamWindowType
    )

    private val streamWindows = ConcurrentHashMap<StreamKey, StreamWindow>()

    fun appendStreamWindow(memoryId: Any?, type: StreamWindowType, content: ContentBlock) {
        val text = extractText(content) ?: return
        val nodeId = memoryId?.toString() ?: "unknown"
        val key = StreamKey(nodeId, type)
        val window = streamWindows.computeIfAbsent(key) { createStreamWindow(nodeId, type) }
        window.appendText(text)
    }

    fun appendStreamWindow(memoryId: Any?, type: StreamWindowType, text: String) {
        val nodeId = memoryId?.toString() ?: "unknown"
        val key = StreamKey(nodeId, type)
        val window = streamWindows.computeIfAbsent(key) { createStreamWindow(nodeId, type) }
        window.appendText(text)
    }

    fun appendEventWindow(memoryId: Any?, type: StreamWindowType, event: Events.GraphEvent) {
        val nodeId = memoryId?.toString() ?: "unknown"
        val key = StreamKey(nodeId, type)
        val window = streamWindows.computeIfAbsent(key) { createStreamWindow(nodeId, type) }
        window.appendEvent(event)
    }

    fun flushWindows(memoryId: Any?): List<Generation> {
        val nodeId = memoryId?.toString() ?: "unknown"
        val keys = streamWindows.keys.filter { it.nodeId == nodeId }
        val generations = mutableListOf<Generation>()
        keys.forEach { flushWindow(it, isFinal = true)?.let(generations::add) }
        return generations
    }

    fun flushOtherWindows(memoryId: Any?, keepType: StreamWindowType?): List<Generation> {
        val nodeId = memoryId?.toString() ?: "unknown"
        val keys = streamWindows.keys.filter { it.nodeId == nodeId && it.type != keepType }
        val generations = mutableListOf<Generation>()
        keys.forEach { flushWindow(it, isFinal = true)?.let(generations::add) }
        return generations
    }

    private fun flushWindow(key: StreamKey, isFinal: Boolean): Generation? {
        val window = streamWindows.remove(key) ?: return null
        return window.flush(isFinal)
    }

    private fun createStreamWindow(nodeId: String, type: StreamWindowType): StreamWindow = when (type) {
        StreamWindowType.MESSAGE -> AgentStreamWindow(nodeId, eventBus)
        StreamWindowType.THOUGHT -> EventStreamWindow(nodeId, type, eventBus)
        StreamWindowType.TOOL_CALL -> EventStreamWindow(nodeId, type, eventBus)
        StreamWindowType.PLAN -> EventStreamWindow(nodeId, type, eventBus)
        StreamWindowType.USER_MESSAGE -> EventStreamWindow(nodeId, type, eventBus)
        StreamWindowType.CURRENT_MODE -> EventStreamWindow(nodeId, type, eventBus)
        StreamWindowType.AVAILABLE_COMMANDS -> EventStreamWindow(nodeId, type, eventBus)
    }

    interface StreamWindow {
        fun appendText(text: String)
        fun appendEvent(event: Events.GraphEvent)
        fun flush(isFinal: Boolean): Generation?
    }

    private class EventStreamWindow(
        private val nodeId: String,
        private val type: StreamWindowType,
        private val eventBus: EventBus
    ) : StreamWindow {
        private val buffer = StringBuilder()
        private var tokenCount = 0
        private val events = mutableListOf<Events.GraphEvent>()

        override fun appendText(text: String) {
            buffer.append(text)
            tokenCount += 1
        }

        override fun appendEvent(event: Events.GraphEvent) {
            events.add(event)
        }

        override fun flush(isFinal: Boolean): Generation? {
            if (buffer.isEmpty() && events.isEmpty()) {
                return null
            }
            if (type == StreamWindowType.THOUGHT && buffer.isNotEmpty()) {
                val content = buffer.toString()
                eventBus.publish(
                    Events.NodeThoughtDeltaEvent(
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        nodeId,
                        content,
                        tokenCount,
                        isFinal
                    )
                )
            }
            if (type == StreamWindowType.USER_MESSAGE && buffer.isNotEmpty()) {
                eventBus.publish(Events.UserMessageChunkEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    nodeId,
                    buffer.toString()
                ))
            }
            if (events.isNotEmpty()) {
                events.forEach { eventBus.publish(it) }
            }
            return null
        }
    }

    private class AgentStreamWindow(
        private val nodeId: String,
        private val eventBus: EventBus
    ) : StreamWindow {
        private val buffer = StringBuilder()
        private var tokenCount = 0

        override fun appendText(text: String) {
            buffer.append(text)
            tokenCount += 1
        }

        override fun appendEvent(event: Events.GraphEvent) {
        }

        override fun flush(isFinal: Boolean): Generation? {
            if (buffer.isEmpty()) {
                return null
            }
            val content = buffer.toString()
            eventBus.publish(
                Events.NodeStreamDeltaEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    nodeId,
                    content,
                    tokenCount,
                    isFinal
                )
            )
            return Generation(AssistantMessage(content))
        }
    }
}
