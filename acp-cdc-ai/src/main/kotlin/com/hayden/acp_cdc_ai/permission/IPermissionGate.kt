package com.hayden.acp_cdc_ai.permission

import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonElement

interface IPermissionGate {
    data class PendingPermissionRequest(
        val requestId: String,
        val originNodeId: String,
        val toolCallId: String,
        val permissions: List<PermissionOption>,
        val deferred: CompletableDeferred<RequestPermissionResponse>,
        val meta: JsonElement?,
        val nodeId: String?
    )

    fun publishRequest(
        requestId: String,
        originNodeId: String,
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        meta: JsonElement?
    ): PendingPermissionRequest

    suspend fun awaitResponse(requestId: String): RequestPermissionResponse
    fun resolveSelected(requestId: String, optionId: String?): Boolean
    fun resolveCancelled(requestId: String): Boolean
    fun resolveSelectedOption(
        permissions: List<PermissionOption>,
        optionId: String?
    ): PermissionOption?

    fun completePending(
        pending: PendingPermissionRequest,
        outcome: RequestPermissionOutcome,
        selectedOptionId: String?
    )
}