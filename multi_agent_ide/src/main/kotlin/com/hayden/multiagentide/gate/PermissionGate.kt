package com.hayden.multiagentide.gate

import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionUpdate
import com.hayden.multiagentide.orchestration.ComputationGraphOrchestrator
import com.hayden.multiagentide.repository.GraphRepository
import com.hayden.multiagentidelib.agent.AgentModels
import com.hayden.multiagentidelib.model.nodes.AskPermissionNode
import com.hayden.multiagentidelib.model.nodes.GraphNode
import com.hayden.multiagentidelib.model.nodes.InterruptContext
import com.hayden.multiagentidelib.model.nodes.InterruptNode
import com.hayden.multiagentidelib.model.nodes.Interruptible
import com.hayden.multiagentidelib.model.nodes.ReviewNode
import com.hayden.utilitymodule.acp.events.EventBus
import com.hayden.utilitymodule.acp.events.Events
import com.hayden.utilitymodule.permission.IPermissionGate
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class PermissionGate(
    private val graphRepository: GraphRepository,
    private val orchestrator: ComputationGraphOrchestrator,
    private val eventBus: EventBus
) : IPermissionGate {

    val log = LoggerFactory.getLogger(PermissionGate::class.java)

    data class PendingInterruptRequest(
        val interruptId: String,
        val originNodeId: String,
        val type: Events.InterruptType,
        val reason: String?,
        val deferred: CompletableDeferred<InterruptResolution>
    )

    data class InterruptResolution(
        val interruptId: String,
        val originNodeId: String,
        val resolutionType: String?,
        val resolutionNotes: String?
    )

    private val pendingRequests = ConcurrentHashMap<String, IPermissionGate.PendingPermissionRequest>()
    private val pendingInterrupts = ConcurrentHashMap<String, PendingInterruptRequest>()

    override fun publishRequest(
        requestId: String,
        originNodeId: String,
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        meta: JsonElement?
    ): IPermissionGate.PendingPermissionRequest {
        val existing = pendingRequests[requestId]
        if (existing != null) {
            return existing
        }

        val now = Instant.now()
        val permissionNodeId = UUID.randomUUID().toString()
        val permissionNode = AskPermissionNode.builder()
            .nodeId(permissionNodeId)
            .title("Permission: " + (toolCall.title ?: "request"))
            .goal("Permission requested for tool call " + toolCall.toolCallId.value)
            .status(Events.NodeStatus.WAITING_INPUT)
            .parentNodeId(originNodeId)
            .childNodeIds(mutableListOf())
            .metadata(
                mutableMapOf(
                    "requestId" to requestId,
                    "toolCallId" to toolCall.toolCallId.value,
                    "originNodeId" to originNodeId
                )
            )
            .createdAt(now)
            .lastUpdatedAt(now)
            .toolCallId(toolCall.toolCallId.value)
            .optionIds(permissions.map { it.optionId.toString() })
            .build()

        try {
            orchestrator.addChildNodeAndEmitEvent(originNodeId, permissionNode)
        } catch (ex: Exception) {
            log.error("Could not add child node and emit event", ex)
            graphRepository.save(permissionNode)
        }

        val deferred = CompletableDeferred<RequestPermissionResponse>()
        val pending = IPermissionGate.PendingPermissionRequest(
            requestId = requestId,
            originNodeId = originNodeId,
            toolCallId = toolCall.toolCallId.value,
            permissions = permissions,
            deferred = deferred,
            meta = meta,
            nodeId = permissionNodeId
        )

        pendingRequests[requestId] = pending

        eventBus.publish(
            Events.PermissionRequestedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                permissionNodeId,
                originNodeId,
                requestId,
                toolCall.toolCallId.value,
                permissions
            )
        )

        return pending
    }

    override suspend fun awaitResponse(requestId: String): RequestPermissionResponse {
        val pending = pendingRequests[requestId] ?: return RequestPermissionResponse(
            RequestPermissionOutcome.Cancelled,
            null
        )
        return pending.deferred.await()
    }

    override fun resolveSelected(requestId: String, optionId: String?): Boolean {
        val pending = pendingRequests.remove(requestId) ?: return false
        val selected = resolveSelectedOption(pending.permissions, optionId)
        val outcome = if (selected == null) {
            RequestPermissionOutcome.Cancelled
        } else {
            RequestPermissionOutcome.Selected(selected.optionId)
        }
        completePending(pending, outcome, selected?.optionId?.toString())
        return true
    }

    override fun resolveCancelled(requestId: String): Boolean {
        val pending = pendingRequests.remove(requestId) ?: return false
        completePending(pending, RequestPermissionOutcome.Cancelled, null)
        return true
    }

    override fun resolveSelectedOption(
        permissions: List<PermissionOption>,
        optionId: String?
    ): PermissionOption? {
        if (permissions.isEmpty()) {
            return null
        }
        if (optionId.isNullOrBlank()) {
            return permissions.first()
        }
        return permissions.firstOrNull { it.optionId.toString() == optionId }
            ?: permissions.firstOrNull()
    }

    override fun completePending(
        pending: IPermissionGate.PendingPermissionRequest,
        outcome: RequestPermissionOutcome,
        selectedOptionId: String?
    ) {
        val response = RequestPermissionResponse(outcome, pending.meta)
        pending.deferred.complete(response)

        val nodeId = pending.nodeId
        if (nodeId != null) {
            val node = graphRepository.findById(nodeId).orElse(null)
            if (node is AskPermissionNode) {
                val updated = node.withStatus(Events.NodeStatus.COMPLETED)
                graphRepository.save(updated)
                orchestrator.emitStatusChangeEvent(
                    nodeId,
                    node.status(),
                    Events.NodeStatus.COMPLETED,
                    "Permission resolved"
                )
            }
        }

        eventBus.publish(
            Events.PermissionResolvedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                pending.nodeId,
                pending.originNodeId,
                pending.requestId,
                pending.toolCallId,
                outcome.toString(),
                selectedOptionId
            )
        )
    }

    fun publishInterrupt(
        interruptId: String,
        originNodeId: String,
        type: Events.InterruptType,
        reason: String?
    ): PendingInterruptRequest {
        val existing = pendingInterrupts[interruptId]
        if (existing != null) {
            return existing
        }

        val pending = PendingInterruptRequest(
            interruptId = interruptId,
            originNodeId = originNodeId,
            type = type,
            reason = reason,
            deferred = CompletableDeferred()
        )
        pendingInterrupts[interruptId] = pending

        if (type == Events.InterruptType.HUMAN_REVIEW) {
            val node = graphRepository.findById(interruptId).orElse(null)
            val reviewNode = node as? ReviewNode
            val reviewContent = reason ?: reviewNode?.reviewContent ?: ""
            if (reviewNode != null && reviewNode.status() != Events.NodeStatus.WAITING_INPUT) {
                val updated = reviewNode
                    .toBuilder()
                    .status(Events.NodeStatus.WAITING_INPUT)
                    .lastUpdatedAt(Instant.now())
                    .build()
                graphRepository.save(updated)
                orchestrator.emitStatusChangeEvent(
                    reviewNode.nodeId(),
                    reviewNode.status(),
                    Events.NodeStatus.WAITING_INPUT,
                    "Human review requested"
                )
            }
            orchestrator.emitReviewRequestedEvent(
                originNodeId,
                interruptId,
                Events.ReviewType.HUMAN,
                reviewContent
            )
        }
        if (type == Events.InterruptType.AGENT_REVIEW) {
            val node = graphRepository.findById(interruptId).orElse(null)
            val reviewNode = node as? ReviewNode
            if (reviewNode != null && reviewNode.status() == Events.NodeStatus.READY) {
                val updated = reviewNode
                    .toBuilder()
                    .status(Events.NodeStatus.RUNNING)
                    .lastUpdatedAt(Instant.now())
                    .build()
                graphRepository.save(updated)
                orchestrator.emitStatusChangeEvent(
                    reviewNode.nodeId(),
                    reviewNode.status(),
                    Events.NodeStatus.RUNNING,
                    "Agent review started"
                )
            }
        }

        return pending
    }

    suspend fun awaitInterrupt(interruptId: String): InterruptResolution {
        val pending = pendingInterrupts[interruptId] ?: return InterruptResolution(
            interruptId = interruptId,
            originNodeId = interruptId,
            resolutionType = "cancelled",
            resolutionNotes = null
        )
        return pending.deferred.await()
    }

    fun awaitInterruptBlocking(interruptId: String): InterruptResolution {
        return runBlocking {
            awaitInterrupt(interruptId)
        }
    }

    fun resolveInterrupt(
        interruptId: String,
        resolutionType: String?,
        resolutionNotes: String?,
        reviewResult: AgentModels.ReviewAgentResult? = null
    ): Boolean {
        val pending = pendingInterrupts.remove(interruptId) ?: return false
        val resolution = InterruptResolution(
            interruptId = interruptId,
            originNodeId = pending.originNodeId,
            resolutionType = resolutionType,
            resolutionNotes = resolutionNotes
        )
        pending.deferred.complete(resolution)

        val interruptNode = graphRepository.findById(interruptId).orElse(null)
        when (interruptNode) {
            is ReviewNode -> {
                val approved = resolutionType?.equals("approved", ignoreCase = true) == true
                    || resolutionType?.equals("accept", ignoreCase = true) == true
                val updatedContext = interruptNode.interruptContext()
                    ?.withStatus(InterruptContext.InterruptStatus.RESOLVED)
                    ?.withResultPayload(resolutionNotes)
                val updated = interruptNode
                    .toBuilder()
                    .approved(approved)
                    .agentFeedback(resolutionNotes ?: "")
                    .reviewCompletedAt(Instant.now())
                    .reviewResult(reviewResult)
                    .interruptContext(updatedContext ?: interruptNode.interruptContext())
                    .status(Events.NodeStatus.COMPLETED)
                    .lastUpdatedAt(Instant.now())
                    .build()
                graphRepository.save(updated)
                orchestrator.emitStatusChangeEvent(
                    interruptNode.nodeId(),
                    interruptNode.status(),
                    Events.NodeStatus.COMPLETED,
                    "Review resolved"
                )
            }
            is InterruptNode -> {
                val updatedContext = interruptNode.interruptContext()
                    ?.withStatus(InterruptContext.InterruptStatus.RESOLVED)
                    ?.withResultPayload(resolutionNotes)
                val updated = interruptNode
                    .toBuilder()
                    .interruptContext(updatedContext ?: interruptNode.interruptContext())
                    .status(Events.NodeStatus.COMPLETED)
                    .lastUpdatedAt(Instant.now())
                    .build()
                graphRepository.save(updated)
                orchestrator.emitStatusChangeEvent(
                    interruptNode.nodeId(),
                    interruptNode.status(),
                    Events.NodeStatus.COMPLETED,
                    "Interrupt resolved"
                )
            }
            else -> {
            }
        }

        val originNode = graphRepository.findById(pending.originNodeId).orElse(null)
        if (originNode is Interruptible) {
            val context = originNode.interruptibleContext()
            if (context != null) {
                val updatedContext = context
                    .withStatus(InterruptContext.InterruptStatus.RESOLVED)
                    .withResultPayload(resolutionNotes)
                val updatedOrigin = updateInterruptContext(originNode, updatedContext)
                graphRepository.save(updatedOrigin)
                if (originNode.status() == Events.NodeStatus.WAITING_INPUT ||
                    originNode.status() == Events.NodeStatus.WAITING_REVIEW
                ) {
                    val resumed = updatedOrigin.withStatus(Events.NodeStatus.RUNNING)
                    graphRepository.save(resumed)
                    orchestrator.emitStatusChangeEvent(
                        originNode.nodeId(),
                        originNode.status(),
                        Events.NodeStatus.RUNNING,
                        "Interrupt resolved"
                    )
                }
                orchestrator.emitInterruptStatusEvent(
                    originNode.nodeId(),
                    pending.type.name,
                    InterruptContext.InterruptStatus.RESOLVED.name,
                    context.originNodeId(),
                    context.resumeNodeId()
                )
            }
        }
        return true
    }

    private fun updateInterruptContext(node: GraphNode, context: InterruptContext): GraphNode {
        return when (node) {
            is com.hayden.multiagentidelib.model.nodes.DiscoveryNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()
            is com.hayden.multiagentidelib.model.nodes.DiscoveryCollectorNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()
            is com.hayden.multiagentidelib.model.nodes.DiscoveryOrchestratorNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()
            is com.hayden.multiagentidelib.model.nodes.PlanningNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()
            is com.hayden.multiagentidelib.model.nodes.PlanningCollectorNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()
            is com.hayden.multiagentidelib.model.nodes.PlanningOrchestratorNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()
            is com.hayden.multiagentidelib.model.nodes.TicketNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()
            is com.hayden.multiagentidelib.model.nodes.TicketCollectorNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()
            is com.hayden.multiagentidelib.model.nodes.TicketOrchestratorNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()
            is ReviewNode ->
                node.toBuilder().interruptContext(context).lastUpdatedAt(Instant.now()).build()
            is com.hayden.multiagentidelib.model.nodes.MergeNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()
            is com.hayden.multiagentidelib.model.nodes.OrchestratorNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()
            is com.hayden.multiagentidelib.model.nodes.CollectorNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()
            is com.hayden.multiagentidelib.model.nodes.SummaryNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()
            is InterruptNode ->
                node.toBuilder().interruptContext(context).lastUpdatedAt(Instant.now()).build()
            is com.hayden.multiagentidelib.model.nodes.AskPermissionNode ->
                node.toBuilder().lastUpdatedAt(Instant.now()).build()
        }
    }
}