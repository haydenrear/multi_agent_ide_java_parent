package com.hayden.multiagentide.gate

import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.RequestPermissionResponse
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey
import com.hayden.acp_cdc_ai.acp.events.Events
import com.hayden.acp_cdc_ai.permission.IPermissionGate
import com.hayden.multiagentide.model.nodes.AskPermissionNode
import com.hayden.multiagentide.model.nodes.GraphNodeBuilderHelper
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
        val permissionNode = GraphNodeBuilderHelper.buildAskPermissionNode(
            originKey.createChild().value,
            "Permission request",
            "Permission requested for tool call $toolCallId",
            Events.NodeStatus.WAITING_INPUT,
            originId,
            mutableListOf(),
            mutableMapOf(
                "requestId" to requestId,
                "toolCallId" to toolCallId,
                "originNodeId" to originId
            ),
            now,
            now,
            toolCallId,
            permissions.map { it.optionId.value }
        )

        permissionGate.publishAskPermissionRequest(
            originId,
            permissionNode,
            requestId,
            permissions,
            toolCallId
        )

        return runBlocking {
                permissionGate.awaitResponse(requestId)
            }.requestPermissionResponse
    }

    fun awaitResponseSync(requestId: String): IPermissionGate.PermissionResolvedResponse =
        runBlocking {
            awaitResponse(requestId)
        }

    fun awaitResponseToFuture(requestId: String): CompletableFuture<IPermissionGate.PermissionResolvedResponse> =
        CoroutineScope(Dispatchers.Default).future {
            awaitResponse(requestId)
        }

    fun awaitInterruptSync(interruptId: String): IPermissionGate.InterruptResolution =
        runBlocking {
            awaitInterrupt(interruptId)
        }
}
