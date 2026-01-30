package com.hayden.utilitymodule.acp

import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.Transport
import com.hayden.utilitymodule.acp.events.EventBus
import com.hayden.utilitymodule.acp.events.Events
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class AcpSessionManager(private val eventBus: EventBus) {

    val sessionContexts = ConcurrentHashMap<Any, AcpSessionContext>()

    inner class AcpSessionContext(
        val scope: CoroutineScope,
        val transport: Transport,
        val protocol: Protocol,
        val client: Client,
        val session: ClientSession,
        val streamWindows: AcpStreamWindowBuffer = AcpStreamWindowBuffer(eventBus)
    ) {

        suspend fun prompt(content: List<ContentBlock>, _meta: JsonElement? = null): Flow<Event> = session.prompt(content, _meta)

        fun appendStreamWindow(
            memoryId: Any?,
            type: AcpStreamWindowBuffer.StreamWindowType,
            content: ContentBlock
        ) = streamWindows.appendStreamWindow(memoryId, type, content)

        fun appendStreamWindow(
            memoryId: Any?,
            type: AcpStreamWindowBuffer.StreamWindowType,
            content: String
        ) = streamWindows.appendStreamWindow(memoryId, type, content)

        fun appendEventWindow(
            memoryId: Any?,
            type: AcpStreamWindowBuffer.StreamWindowType,
            event: Events.GraphEvent
        ) = streamWindows.appendEventWindow(memoryId, type, event)

        fun flushWindows(memoryId: Any?) = streamWindows.flushWindows(memoryId)

        fun flushOtherWindows(
            memoryId: Any?,
            keepType: AcpStreamWindowBuffer.StreamWindowType?
        ) = streamWindows.flushOtherWindows(memoryId, keepType)

    }

}
