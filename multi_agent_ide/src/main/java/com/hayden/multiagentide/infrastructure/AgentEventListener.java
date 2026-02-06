package com.hayden.multiagentide.infrastructure;

import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.model.nodes.*;
import com.hayden.multiagentide.orchestration.ComputationGraphOrchestrator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Event-driven listener that orchestrates multi-agent workflows.
 * Listens for node completion events and triggers the next phase of the workflow.
 *
 * Workflow Orchestration Flow:
 * OrchestratorNode (READY) → DiscoveryOrchestratorNode → DiscoveryNode(s) → DiscoveryMergerNode
 *   → PlanningOrchestratorNode → PlanningNode(s) → PlanningMergerNode
 *   → TicketOrchestratorNode → EditorNode(s) → ReviewNode → MergeNode(s)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentEventListener implements EventListener {

    private final ComputationGraphOrchestrator orchestrator;

    @Lazy
    @Autowired
    private AgentRunner agentRunner;

    @Override
    public String listenerId() {
        return "WorkflowOrchestratorListener";
    }

    /**
     * Dispatches an agent for execution with its parent and children context.
     */

    @Override
    public void onEvent(Events.GraphEvent event) {
        switch (event) {
            case Events.AddMessageEvent nodeAddedEvent -> {
                doAgentRunner(nodeAddedEvent);
            }
            case Events.NodeAddedEvent nodeAddedEvent -> {
                handleNodeAdded(nodeAddedEvent);
            }
            case Events.NodeStatusChangedEvent statusChangedEvent -> {
                handleNodeStatusChanged(statusChangedEvent);
            }
            case Events.GoalCompletedEvent goalCompletedEvent -> {
                log.info("Workflow goal completed: {}", goalCompletedEvent.eventId());
            }
            case Events.NodeBranchedEvent nodeBranchedEvent -> {
                log.debug("Node branched: {}", nodeBranchedEvent.eventId());
            }
            case Events.NodeDeletedEvent nodeDeletedEvent -> {
                log.debug("Node deleted: {}", nodeDeletedEvent.eventId());
            }
            case Events.NodePrunedEvent nodePrunedEvent -> {
                log.debug("Node pruned: {}", nodePrunedEvent.eventId());
            }
            case Events.NodeReviewRequestedEvent nodeReviewRequestedEvent -> {
                doAgentRunner(nodeReviewRequestedEvent);
            }
            case Events.NodeStreamDeltaEvent nodeStreamDeltaEvent -> {
                log.debug("Node stream delta: {}", nodeStreamDeltaEvent.eventId());
            }
            case Events.NodeUpdatedEvent nodeUpdatedEvent -> {
                log.debug("Node updated: {}", nodeUpdatedEvent.eventId());
            }
            case Events.InterruptStatusEvent interruptStatusEvent -> {
                log.debug("Interrupt status event: {}", interruptStatusEvent.eventId());
            }
            case Events.WorktreeBranchedEvent worktreeBranchedEvent -> {
                log.debug("Worktree branched: {}", worktreeBranchedEvent.eventId());
            }
            case Events.WorktreeCreatedEvent worktreeCreatedEvent -> {
                log.debug("Worktree created: {}", worktreeCreatedEvent.eventId());
            }
            case Events.WorktreeDiscardedEvent worktreeDiscardedEvent -> {
                log.debug("Worktree discarded: {}", worktreeDiscardedEvent.eventId());
            }
            case Events.WorktreeMergedEvent worktreeMergedEvent -> {
                log.debug("Worktree merged: {}", worktreeMergedEvent.eventId());
            }
            case Events.StopAgentEvent stopAgentEvent -> {
                doAgentRunner(stopAgentEvent);
            }
            case Events.PauseEvent pauseEvent -> {
                doAgentRunner(pauseEvent);
            }
            case Events.GuiRenderEvent guiRenderEvent -> {
            }
            case Events.NodeBranchRequestedEvent nodeBranchRequestedEvent -> {
            }
            case Events.NodeThoughtDeltaEvent nodeThoughtDeltaEvent -> {
            }
            case Events.ResumeEvent resumeEvent -> {
            }
            case Events.ToolCallEvent toolCallEvent -> {
            }
            case Events.UiDiffAppliedEvent uiDiffAppliedEvent -> {
            }
            case Events.UiDiffRejectedEvent uiDiffRejectedEvent -> {
            }
            case Events.UiDiffRevertedEvent uiDiffRevertedEvent -> {
            }
            case Events.UiFeedbackEvent uiFeedbackEvent -> {
            }
            case Events.AvailableCommandsUpdateEvent availableCommandsUpdateEvent -> {
            }
            case Events.CurrentModeUpdateEvent currentModeUpdateEvent -> {
            }
            case Events.PlanUpdateEvent planUpdateEvent -> {
            }
            case Events.UserMessageChunkEvent userMessageChunkEvent -> {
            }
            case Events.PermissionRequestedEvent permissionRequestedEvent -> {
            }
            case Events.PermissionResolvedEvent permissionResolvedEvent -> {
            }
            case Events.ActionCompletedEvent actionCompletedEvent -> {
            }
            case Events.ActionStartedEvent actionStartedEvent -> {
            }
            case Events.ArtifactEvent artifactEvent -> {
            }
            case Events.NodeErrorEvent nodeErrorEvent -> {
            }
            case Events.ResolveInterruptEvent resolveInterruptEvent -> {
            }
            case Events.ChatSessionCreatedEvent chatSessionCreatedEvent -> {
            }
        }
    }

    /**
     * Handles NodeAddedEvent: Dispatches agent for execution if node is in READY status.
     * This triggers the initial execution of nodes that have been registered and are ready to run.
     *
     * Only nodes with READY status are dispatched. Other statuses (FAILED, WAITING_INPUT, etc.)
     * require different handling and are ignored here.
     */
    private void handleNodeAdded(Events.NodeAddedEvent event) {
        doAgentRunner(event);
    }

    private void handleNodeStatusChanged(Events.NodeStatusChangedEvent event) {
        String nodeId = event.nodeId();
        Optional<GraphNode> nodeOpt = orchestrator.getNode(nodeId);
        if (nodeOpt.isEmpty()) {
            log.warn("Node status changed but node not found: {}", nodeId);
            return;
        }

        GraphNode node = nodeOpt.get();
        if (event.newStatus() == Events.NodeStatus.COMPLETED && node instanceof Collector) {
            log.info(
                    "Collector completed: {} ({})",
                    node.title(),
                    nodeId
            );
        }
        log.info("Node completed: {} ({}), triggering next phase", node.title(), nodeId);

        try {
            agentRunner.runOnAgent(new AgentRunner.AgentDispatchArgs(node,
                    orchestrator.getNode(node.parentNodeId()).orElse(null),
                    orchestrator.getChildNodes(node.nodeId()), event));
        } catch (Exception e) {
            log.error("Failed to execute agent for node: {} ({}) during dispatch",
                    node.title(), nodeId, e);
        }
    }

    private void doAgentRunner(Events.AgentEvent event) {
        String nodeId = event.nodeId();
        Optional<GraphNode> nodeOpt = orchestrator.getNode(nodeId);

        if (nodeOpt.isEmpty()) {
            log.warn("Node added event received but node not found: {}", nodeId);
            return;
        }

        GraphNode node = nodeOpt.get();

        log.info("Node added and ready for execution: {} ({})", node.title(), nodeId);

        Optional<GraphNode> parentOpt = Optional.empty();
        if (node.parentNodeId() != null) {
            parentOpt = orchestrator.getNode(node.parentNodeId());
        }

        var children = orchestrator.getChildNodes(nodeId);
        var dispatch = new AgentRunner.AgentDispatchArgs(node, parentOpt.orElse(null), children, event);

        try {
            agentRunner.runOnAgent(dispatch);
        } catch (Exception e) {
            log.error("Failed to execute agent for node: {} ({}) during dispatch",
                    node.title(), nodeId, e);
        }
    }

    @Override
    public boolean isInterestedIn(Events.GraphEvent eventType) {
        return true;
    }
}
