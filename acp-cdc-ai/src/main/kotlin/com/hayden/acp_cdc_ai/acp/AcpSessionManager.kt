package com.hayden.acp_cdc_ai.acp

import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.Transport
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey
import com.hayden.acp_cdc_ai.acp.events.EventBus
import com.hayden.acp_cdc_ai.acp.events.Events
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class AcpSessionManager {

    val sessionContexts = ConcurrentHashMap<Any, AcpSessionContext>()

    @Lazy
    @Autowired
    lateinit var eventBus: EventBus

    inner class AcpSessionContext(
        val scope: CoroutineScope,
        val transport: Transport,
        val protocol: Protocol,
        val client: Client,
        val session: ClientSession,
        val streamWindows: AcpStreamWindowBuffer = AcpStreamWindowBuffer(eventBus),
        val messageParent: ArtifactKey,
        val chatModelKey: ArtifactKey,
    ) {

        init {
            eventBus.publish(Events.ChatSessionCreatedEvent(UUID.randomUUID().toString(), Instant.now(), messageParent.value, chatModelKey))
        }

        suspend fun prompt(content: List<ContentBlock>, _meta: JsonElement? = null): Flow<Event> = session.prompt(content, _meta)

        fun appendStreamWindow(
            memoryId: Any?,
            type: AcpStreamWindowBuffer.StreamWindowType,
            content: ContentBlock
        ) = streamWindows.appendStreamWindow(memoryId, type, content, messageParent, chatModelKey)

        fun appendStreamWindow(
            memoryId: Any?,
            type: AcpStreamWindowBuffer.StreamWindowType,
            content: String
        ) = streamWindows.appendStreamWindow(memoryId, type, content, messageParent, chatModelKey)

        fun appendEventWindow(
            memoryId: Any?,
            type: AcpStreamWindowBuffer.StreamWindowType,
            event: Events.GraphEvent
        ) = streamWindows.appendEventWindow(memoryId, type, event, messageParent, chatModelKey)

        fun flushWindows(memoryId: Any?) = streamWindows.flushWindows(memoryId)

        fun flushOtherWindows(
            memoryId: Any?,
            keepType: AcpStreamWindowBuffer.StreamWindowType?
        ) = streamWindows.flushOtherWindows(memoryId, keepType)

    }

}
