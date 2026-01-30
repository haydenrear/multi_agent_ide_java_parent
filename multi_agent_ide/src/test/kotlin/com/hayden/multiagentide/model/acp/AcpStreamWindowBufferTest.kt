package com.hayden.multiagentide.model.acp

import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.PlanEntry
import com.agentclientprotocol.model.PlanEntryPriority
import com.agentclientprotocol.model.PlanEntryStatus
import com.agentclientprotocol.model.SessionModeId
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolCallStatus
import com.agentclientprotocol.model.ToolKind
import com.agentclientprotocol.model.AvailableCommand
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.Transport
import com.hayden.acp_cdc_ai.acp.AcpSessionManager
import com.hayden.acp_cdc_ai.acp.AcpStreamWindowBuffer
import com.hayden.acp_cdc_ai.acp.parseGenerationsFromAcpEvent
import com.hayden.acp_cdc_ai.acp.events.EventBus
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey
import com.hayden.acp_cdc_ai.acp.events.EventListener
import com.hayden.acp_cdc_ai.acp.events.Events
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class AcpStreamWindowBufferTest {

    @Test
    fun `flushes message window into generation when switching types`() {
        val bus = RecordingEventBus()
        val buffer = AcpStreamWindowBuffer(bus)

        buffer.appendStreamWindow("node-1", AcpStreamWindowBuffer.StreamWindowType.MESSAGE, ContentBlock.Text("Hello"),ArtifactKey.createRoot())
        val flushed = buffer.flushOtherWindows("node-1", AcpStreamWindowBuffer.StreamWindowType.THOUGHT)

        assertEquals(1, flushed.size)
        assertEquals("Hello", flushed.first().output.text)
        assertTrue(bus.events.any { it is Events.NodeStreamDeltaEvent })
    }

    @Test
    fun `parseGenerationsFromAcpEvent aggregates agent messages and emits only on flush`() {
        val bus = RecordingEventBus()
        val sessionContext = createSessionContext(bus)

        val first = parseGenerationsFromAcpEvent(
            Event.SessionUpdateEvent(SessionUpdate.AgentMessageChunk(ContentBlock.Text("Hel"))),
            sessionContext,
            "node-1"
        )
        val second = parseGenerationsFromAcpEvent(
            Event.SessionUpdateEvent(SessionUpdate.AgentMessageChunk(ContentBlock.Text("lo"))),
            sessionContext,
            "node-1"
        )
        val flushed = parseGenerationsFromAcpEvent(
            Event.SessionUpdateEvent(SessionUpdate.UserMessageChunk(ContentBlock.Text("hi"))),
            sessionContext,
            "node-1"
        )

        assertTrue(first.isEmpty())
        assertTrue(second.isEmpty())
        assertEquals(1, flushed.size)
        assertEquals("Hello", flushed.first().output.text)

        sessionContext.flushWindows("node-1")
        val userEvents = bus.events.filterIsInstance<Events.UserMessageChunkEvent>()
        assertEquals(1, userEvents.size)
        assertEquals("hi", userEvents.first().content())
    }

    @Test
    fun `parseGenerationsFromAcpEvent aggregates thoughts and flushes on switch`() {
        val bus = RecordingEventBus()
        val sessionContext = createSessionContext(bus)

        parseGenerationsFromAcpEvent(
            Event.SessionUpdateEvent(SessionUpdate.AgentThoughtChunk(ContentBlock.Text("think"))),
            sessionContext,
            "node-1"
        )
        parseGenerationsFromAcpEvent(
            Event.SessionUpdateEvent(SessionUpdate.AgentThoughtChunk(ContentBlock.Text("ing"))),
            sessionContext,
            "node-1"
        )
        val flushed = parseGenerationsFromAcpEvent(
            Event.SessionUpdateEvent(SessionUpdate.AgentMessageChunk(ContentBlock.Text("Hello"))),
            sessionContext,
            "node-1"
        )

        assertTrue(flushed.isEmpty())
        val thoughtEvents = bus.events.filterIsInstance<Events.NodeThoughtDeltaEvent>()
        assertEquals(1, thoughtEvents.size)
        assertEquals("thinking", thoughtEvents.first().deltaContent())

        val finalFlush = sessionContext.flushWindows("node-1")
        assertEquals(1, finalFlush.size)
        assertEquals("Hello", finalFlush.first().output.text)
    }

    @Test
    fun `aggregates agent message chunks into single generation`() {
        val bus = RecordingEventBus()
        val buffer = AcpStreamWindowBuffer(bus)

        buffer.appendStreamWindow("node-1", AcpStreamWindowBuffer.StreamWindowType.MESSAGE, "Hel",ArtifactKey.createRoot())
        buffer.appendStreamWindow("node-1", AcpStreamWindowBuffer.StreamWindowType.MESSAGE, "lo",ArtifactKey.createRoot())
        val flushed = buffer.flushWindows("node-1")

        assertEquals(1, flushed.size)
        assertEquals("Hello", flushed.first().output.text)
        assertTrue(bus.events.any { it is Events.NodeStreamDeltaEvent })
    }

    @Test
    fun `aggregates thought chunks and emits on flush`() {
        val bus = RecordingEventBus()
        val buffer = AcpStreamWindowBuffer(bus)

        buffer.appendStreamWindow("node-1", AcpStreamWindowBuffer.StreamWindowType.THOUGHT, "think",ArtifactKey.createRoot())
        buffer.appendStreamWindow("node-1", AcpStreamWindowBuffer.StreamWindowType.THOUGHT, "ing",ArtifactKey.createRoot())
        buffer.flushWindows("node-1")

        val thoughtEvents = bus.events.filterIsInstance<Events.NodeThoughtDeltaEvent>()
        assertEquals(1, thoughtEvents.size)
        assertEquals("thinking", thoughtEvents.first().deltaContent())
    }

    @Test
    fun `publishes tool call events on flush`() {
        val bus = RecordingEventBus()
        val buffer = AcpStreamWindowBuffer(bus)

        val toolEvent = Events.ToolCallEvent(
            "evt-1",
            Instant.now(),
            "node-1",
            "tool-1",
            "search",
            "SEARCH",
            "PENDING",
            "START",
            emptyList(),
            emptyList(),
            null,
            null
        )

        buffer.appendEventWindow("node-1", AcpStreamWindowBuffer.StreamWindowType.TOOL_CALL, toolEvent,ArtifactKey.createRoot())
        buffer.flushWindows("node-1")

        assertTrue(bus.events.contains(toolEvent))
    }

    @Test
    fun `publishes thought and user message chunks on flush`() {
        val bus = RecordingEventBus()
        val buffer = AcpStreamWindowBuffer(bus)

        buffer.appendStreamWindow("node-1", AcpStreamWindowBuffer.StreamWindowType.THOUGHT, "Thinking...",ArtifactKey.createRoot())
        buffer.appendStreamWindow("node-1", AcpStreamWindowBuffer.StreamWindowType.USER_MESSAGE, "User says hi",ArtifactKey.createRoot())
        buffer.flushWindows("node-1")

        assertTrue(bus.events.any { it is Events.NodeThoughtDeltaEvent })
        assertTrue(bus.events.any { it is Events.UserMessageChunkEvent })
    }

    @Test
    fun `parseGenerationsFromAcpEvent buffers tool call events until flush`() {
        val bus = RecordingEventBus()
        val sessionContext = createSessionContext(bus)

        val toolCall = SessionUpdate.ToolCall(
            ToolCallId("tool-1"),
            "search",
            ToolKind.SEARCH,
            ToolCallStatus.PENDING
        )
        val toolCallUpdate = SessionUpdate.ToolCallUpdate(
            ToolCallId("tool-1"),
            "search",
            ToolKind.SEARCH,
            ToolCallStatus.COMPLETED
        )

        val flushed = parseGenerationsFromAcpEvent(
            Event.SessionUpdateEvent(toolCall),
            sessionContext,
            "node-1"
        )
        parseGenerationsFromAcpEvent(
            Event.SessionUpdateEvent(toolCallUpdate),
            sessionContext,
            "node-1"
        )

        assertTrue(flushed.isEmpty())
        assertTrue(bus.events.none { it is Events.ToolCallEvent })

        sessionContext.flushWindows("node-1")
        val toolEvents = bus.events.filterIsInstance<Events.ToolCallEvent>()
        assertEquals(2, toolEvents.size)
    }

    @Test
    fun `parseGenerationsFromAcpEvent buffers plan mode and commands until flush`() {
        val bus = RecordingEventBus()
        val sessionContext = createSessionContext(bus)

        val plan = SessionUpdate.PlanUpdate(
            listOf(PlanEntry("step", PlanEntryPriority.HIGH, PlanEntryStatus.PENDING))
        )
        val mode = SessionUpdate.CurrentModeUpdate(SessionModeId("analysis"))
        val commands = SessionUpdate.AvailableCommandsUpdate(
            listOf(AvailableCommand("cmd", "desc", null))
        )

        parseGenerationsFromAcpEvent(Event.SessionUpdateEvent(plan), sessionContext, "node-1")
        assertTrue(bus.events.isEmpty())
        parseGenerationsFromAcpEvent(Event.SessionUpdateEvent(mode), sessionContext, "node-1")
        assertTrue(bus.events.any { it is Events.PlanUpdateEvent })
        parseGenerationsFromAcpEvent(Event.SessionUpdateEvent(commands), sessionContext, "node-1")
        assertTrue(bus.events.any { it is Events.CurrentModeUpdateEvent })
        sessionContext.flushWindows("node-1")

        assertTrue(bus.events.any { it is Events.AvailableCommandsUpdateEvent })
    }

    @Test
    fun `publishes non-message updates on flush`() {
        val bus = RecordingEventBus()
        val buffer = AcpStreamWindowBuffer(bus)

        val planEvent = Events.PlanUpdateEvent(
            "plan-1",
            Instant.now(),
            "node-1",
            listOf(mapOf("content" to "step", "priority" to "HIGH", "status" to "PENDING"))
        )
        val modeEvent = Events.CurrentModeUpdateEvent(
            "mode-1",
            Instant.now(),
            "node-1",
            "analysis"
        )

        buffer.appendEventWindow("node-1", AcpStreamWindowBuffer.StreamWindowType.PLAN, planEvent,ArtifactKey.createRoot())
        buffer.appendEventWindow("node-1", AcpStreamWindowBuffer.StreamWindowType.CURRENT_MODE, modeEvent,ArtifactKey.createRoot())
        buffer.flushWindows("node-1")

        assertTrue(bus.events.contains(planEvent))
        assertTrue(bus.events.contains(modeEvent))
    }

    @Test
    fun `flushOtherWindows keeps active window and flushes others`() {
        val bus = RecordingEventBus()
        val buffer = AcpStreamWindowBuffer(bus)

        buffer.appendStreamWindow("node-1", AcpStreamWindowBuffer.StreamWindowType.MESSAGE, "hi",ArtifactKey.createRoot())
        buffer.appendStreamWindow("node-1", AcpStreamWindowBuffer.StreamWindowType.THOUGHT, "think",ArtifactKey.createRoot())
        val flushed = buffer.flushOtherWindows("node-1", AcpStreamWindowBuffer.StreamWindowType.MESSAGE)

        assertEquals(0, flushed.size)
        assertTrue(bus.events.any { it is Events.NodeThoughtDeltaEvent })

        val finalFlush = buffer.flushWindows("node-1")
        assertEquals(1, finalFlush.size)
        assertEquals("hi", finalFlush.first().output.text)
    }

    private fun createSessionContext(bus: RecordingEventBus): AcpSessionManager.AcpSessionContext {
        val manager = AcpSessionManager(bus)
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val transport = mock(Transport::class.java)
        val protocol = mock(Protocol::class.java)
        val client = mock(Client::class.java)
        val session = mock(ClientSession::class.java)
        return manager.AcpSessionContext(scope, transport, protocol, client, session, messageParent = ArtifactKey.createRoot())
    }

    private class RecordingEventBus : EventBus {
        val events = CopyOnWriteArrayList<Events.GraphEvent>()

        override fun subscribe(listener: EventListener) {
        }

        override fun unsubscribe(listener: EventListener) {
        }

        override fun publish(event: Events.GraphEvent) {
            events.add(event)
        }

        override fun getSubscribers(): List<EventListener> = emptyList()

        override fun clear() {
            events.clear()
        }

        override fun hasSubscribers(): Boolean = false
    }
}
