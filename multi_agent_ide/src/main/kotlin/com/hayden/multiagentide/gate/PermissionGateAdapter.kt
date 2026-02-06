package com.hayden.multiagentide.gate

import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.RequestPermissionResponse
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey
import com.hayden.acp_cdc_ai.acp.events.Events
import com.hayden.acp_cdc_ai.permission.IPermissionGate
import com.hayden.multiagentidelib.model.nodes.AskPermissionNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.CompletableFuture

@Component
class PermissionGateAdapter(val permissionGate: PermissionGate) : IPermissionGate by permissionGate {

    fun awaitPermissionSync(
        requestId: String,
        origin: String,
        toolCallId: String
    ): RequestPermissionResponse {
        val now = Instant.now()
        val originKey = try {
            ArtifactKey(origin)
        } catch (ex: IllegalArgumentException) {
            ArtifactKey.createRoot()
        }
        val originId = originKey.value
        val permissions = PermissionOptionKind.entries
            .map {
                PermissionOption(
                    PermissionOptionId(it.name),
                    it.name,
                    it
                )
            }.toList()
        val permissionNode = AskPermissionNode.builder()
            .nodeId(originKey.createChild().value)
            .title("Permission request")
            .goal("Permission requested for tool call $toolCallId")
            .status(Events.NodeStatus.WAITING_INPUT)
            .parentNodeId(originId)
            .childNodeIds(mutableListOf())
            .metadata(
                mutableMapOf(
                    "requestId" to requestId,
                    "toolCallId" to toolCallId,
                    "originNodeId" to originId
                )
            )
            .createdAt(now)
            .lastUpdatedAt(now)
            .toolCallId(toolCallId)
            .optionIds(permissions.map { it.optionId.value })
            .build()

        permissionGate.publishAskPermissionRequest(
            originId,
            permissionNode,
            requestId,
            permissions,
            toolCallId
        )

        return runBlocking {
                permissionGate.awaitResponse(requestId)
            }
    }

    fun awaitResponseSync(requestId: String): RequestPermissionResponse =
        runBlocking {
            awaitResponse(requestId)
        }

    fun awaitResponseToFuture(requestId: String): CompletableFuture<RequestPermissionResponse> =
        CoroutineScope(Dispatchers.Default).future {
            awaitResponse(requestId)
        }

    fun awaitInterruptSync(interruptId: String): IPermissionGate.InterruptResolution =
        runBlocking {
            awaitInterrupt(interruptId)
        }
}
