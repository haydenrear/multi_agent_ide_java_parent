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
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey
import com.hayden.acp_cdc_ai.acp.events.EventBus
import com.hayden.acp_cdc_ai.acp.events.Events
import com.hayden.acp_cdc_ai.permission.IPermissionGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

@Service
class PermissionGate(
    private val graphRepository: GraphRepository,
    private val orchestrator: ComputationGraphOrchestrator
) : IPermissionGate {

    @Autowired
    @Lazy
    private lateinit var eventBus: EventBus

    val log = LoggerFactory.getLogger(PermissionGate::class.java)


    private val pendingRequests = ConcurrentHashMap<String, IPermissionGate.PendingPermissionRequest>()

    private val pendingInterrupts = ConcurrentHashMap<String, IPermissionGate.PendingInterruptRequest>()

    override fun getInterruptPending(requestId: Predicate<IPermissionGate.PendingInterruptRequest>): IPermissionGate.PendingInterruptRequest? {
        return pendingInterrupts.entries
            .filter { requestId.test(it.value) }
            .map { it.value }
            .firstOrNull()
    }

    override fun isInterruptPending(requestId: Predicate<IPermissionGate.PendingInterruptRequest>): Boolean {
        return pendingInterrupts.entries
            .any { requestId.test(it.value) }
    }

    override fun pendingPermissionRequests(): List<IPermissionGate.PendingPermissionRequest> {
        return pendingRequests.values.toList()
    }

    override fun pendingInterruptRequests(): List<IPermissionGate.PendingInterruptRequest> {
        return pendingInterrupts.values.toList()
    }

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
        val permissionNode = AskPermissionNode.builder()
            .nodeId(ArtifactKey(originNodeId).createChild().value)
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

        return publishAskPermissionRequest(
            originNodeId,
            permissionNode,
            requestId,
            permissions,
            toolCall.toolCallId.value,
            meta
        )
    }

    fun publishAskPermissionRequest(
        originNodeId: String,
        permissionNode: AskPermissionNode?,
        requestId: String,
        permissions: List<PermissionOption>,
        toolCallId: String,
        meta: JsonElement? = null
    ): IPermissionGate.PendingPermissionRequest {
        return pendingRequests.computeIfAbsent(requestId, { it ->
            if (permissionNode != null) {
                try {
                    orchestrator.addChildNodeAndEmitEvent(originNodeId, permissionNode)
                } catch (ex: Exception) {
                    log.error("Could not attach permission node to parent, emitting add event as fallback", ex)
                    orchestrator.emitNodeAddedEvent(permissionNode, originNodeId)
                }
            }

            val deferred = CompletableDeferred<RequestPermissionResponse>()
            val pending = IPermissionGate.PendingPermissionRequest(
                requestId = requestId,
                originNodeId = originNodeId,
                toolCallId = toolCallId,
                permissions = permissions,
                deferred = deferred,
                meta = meta,
                nodeId = permissionNode?.nodeId
            )
            eventBus.publish(
                Events.PermissionRequestedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    permissionNode?.nodeId,
                    originNodeId,
                    requestId,
                    toolCallId,
                    permissions
                )
            )

            pending
        })

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
                emitNodeUpdatedIf(updated)
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

    override fun publishInterrupt(
        interruptId: String,
        originNodeId: String,
        type: Events.InterruptType,
        reason: String?
    ): IPermissionGate.PendingInterruptRequest {
        val existing = pendingInterrupts[interruptId]

        if (existing != null) {
            return existing
        }

        return pendingInterrupts.computeIfAbsent(interruptId, { _ ->
            val pending = IPermissionGate.PendingInterruptRequest(
                interruptId = interruptId,
                originNodeId = originNodeId,
                type = type,
                reason = reason,
                deferred = CompletableDeferred()
            )
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
                    emitNodeUpdatedIf(updated)
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
                    emitNodeUpdatedIf(updated)
                    orchestrator.emitStatusChangeEvent(
                        reviewNode.nodeId(),
                        reviewNode.status(),
                        Events.NodeStatus.RUNNING,
                        "Agent review started"
                    )
                }
            }

            pending
        })

    }

    override suspend fun awaitInterrupt(interruptId: String): IPermissionGate.InterruptResolution {
        val pending = pendingInterrupts[interruptId] ?: return invalidInterrupt(interruptId)
        return pending.deferred.await()
    }

    fun invalidInterrupt(interruptId: String): IPermissionGate.InterruptResolution {
        return IPermissionGate.InterruptResolution(
            interruptId = interruptId,
            originNodeId = interruptId,
            resolutionType = IPermissionGate.ResolutionType.CANCELLED,
            resolutionNotes = null
        )
    }

    fun awaitInterruptBlocking(interruptId: String): IPermissionGate.InterruptResolution {
        return runBlocking {
            awaitInterrupt(interruptId)
        }
    }

    override fun resolveInterrupt(
        interruptId: String,
        resolutionType: IPermissionGate.ResolutionType?,
        resolutionNotes: String?,
        reviewResult: IPermissionGate.InterruptResult?
    ): Boolean {
        val rr: AgentModels.ReviewAgentResult? = if (reviewResult != null)
            AgentModels.ReviewAgentResult(
                reviewResult.contextId,
                reviewResult.assessmentStatus?.name,
                reviewResult.feedback,
                reviewResult.suggestions ?: emptyList(),
                reviewResult.contentLinks ?: emptyList(),
                reviewResult.output
            )
        else null

        return resolveInterrupt(interruptId, resolutionType, resolutionNotes, rr)
    }

    fun resolveInterrupt(
        interruptId: String,
        resolutionType: IPermissionGate.ResolutionType?,
        resolutionNotes: String?,
        reviewResult: AgentModels.ReviewAgentResult? = null
    ): Boolean {
        val pending = pendingInterrupts.remove(interruptId) ?: return false

        val resolution = IPermissionGate.InterruptResolution(
            interruptId = interruptId,
            originNodeId = pending.originNodeId,
            resolutionType = resolutionType,
            resolutionNotes = resolutionNotes
        )

        val interruptNode = graphRepository.findById(interruptId).orElse(null)
        when (interruptNode) {
            is ReviewNode -> {
                val updatedContext = interruptNode.interruptContext()
                    ?.withStatus(InterruptContext.InterruptStatus.RESOLVED)
                    ?.withResultPayload(resolutionNotes)
                val updated = interruptNode
                    .toBuilder()
                    .approved(resolution.approved())
                    .agentFeedback(resolutionNotes ?: "")
                    .reviewCompletedAt(Instant.now())
                    .reviewResult(reviewResult)
                    .interruptContext(updatedContext ?: interruptNode.interruptContext())
                    .status(Events.NodeStatus.COMPLETED)
                    .lastUpdatedAt(Instant.now())
                    .build()
                emitNodeUpdatedIf(updated)
                orchestrator.emitStatusChangeEvent(
                    interruptNode.nodeId(),
                    interruptNode.status(),
                    Events.NodeStatus.COMPLETED,
                    "Review resolved"
                )
                orchestrator.emitInterruptResolved(
                    ArtifactKey(interruptNode.nodeId()).createChild().value,
                    interruptNode.nodeId(),
                    interruptNode.interruptContext.type,
                    resolution.resolutionNotes
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
                emitNodeUpdatedIf(updated)
                orchestrator.emitStatusChangeEvent(
                    interruptNode.nodeId(),
                    interruptNode.status(),
                    Events.NodeStatus.COMPLETED,
                    "Interrupt resolved"
                )
                orchestrator.emitInterruptResolved(
                    ArtifactKey(interruptNode.nodeId()).createChild().value,
                    interruptNode.nodeId(),
                    interruptNode.interruptContext.type,
                    resolution.resolutionNotes
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
                emitNodeUpdatedIf(updatedOrigin)
                if (originNode.status() == Events.NodeStatus.WAITING_INPUT ||
                    originNode.status() == Events.NodeStatus.WAITING_REVIEW
                ) {
                    val resumed = updatedOrigin.withStatus(Events.NodeStatus.RUNNING)
                    emitNodeUpdatedIf(resumed)
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

        pending.deferred.complete(resolution)
        return true
    }

    private fun emitNodeUpdatedIf(node: GraphNode?) {
        if (node == null) {
            return
        }
        orchestrator.emitNodeUpdatedEvent(node)
    }

    private fun updateInterruptContext(node: GraphNode, context: InterruptContext): GraphNode {
        return when (node) {
            is com.hayden.multiagentidelib.model.nodes.DiscoveryNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()

            is com.hayden.multiagentidelib.model.nodes.DiscoveryCollectorNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()

            is com.hayden.multiagentidelib.model.nodes.DiscoveryDispatchAgentNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()

            is com.hayden.multiagentidelib.model.nodes.DiscoveryOrchestratorNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()

            is com.hayden.multiagentidelib.model.nodes.PlanningNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()

            is com.hayden.multiagentidelib.model.nodes.PlanningCollectorNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()

            is com.hayden.multiagentidelib.model.nodes.PlanningDispatchAgentNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()

            is com.hayden.multiagentidelib.model.nodes.PlanningOrchestratorNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()

            is com.hayden.multiagentidelib.model.nodes.TicketNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()

            is com.hayden.multiagentidelib.model.nodes.TicketCollectorNode ->
                node.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build()

            is com.hayden.multiagentidelib.model.nodes.TicketDispatchAgentNode ->
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
