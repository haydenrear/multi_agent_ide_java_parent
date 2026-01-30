package com.hayden.multiagentidelib.model.nodes;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.utilitymodule.acp.events.Events;
import lombok.Builder;

import java.time.Instant;
import java.util.*;

/**
 * Node representing a unit of work tied to main and submodule worktrees.
 * Can be Branchable, Editable, Reviewable, Prunable.
 */
@Builder(toBuilder = true)
public record TicketOrchestratorNode(
        String nodeId,
        String title,
        String goal,
        Events.NodeStatus status,
        String parentNodeId,
        List<String> childNodeIds,
        Map<String, String> metadata,
        Instant createdAt,
        Instant lastUpdatedAt,
        WorkTree worktree,
        int completedSubtasks,
        int totalSubtasks,
        String agentType,  // Type of agent handling this work
        String workOutput,
        boolean mergeRequired,
        int streamingTokenCount,
        AgentModels.TicketOrchestratorResult ticketOrchestratorResult,
        InterruptContext interruptibleContext
) implements GraphNode, Viewable<String>, Orchestrator, HasWorktree, Interruptible {

    public TicketOrchestratorNode(String nodeId, String title, String goal, Events.NodeStatus status, String parentNodeId, List<String> childNodeIds, Map<String, String> metadata,
                                  Instant createdAt, Instant lastUpdatedAt, WorkTree worktree, int completedSubtasks, int totalSubtasks, String agentType, String workOutput, boolean mergeRequired, int streamingTokenCount) {
        this(nodeId, title, goal, status, parentNodeId, childNodeIds, metadata, createdAt, lastUpdatedAt,
                worktree, completedSubtasks, totalSubtasks, agentType,
                workOutput, mergeRequired, streamingTokenCount, null, null);
    }


    public TicketOrchestratorNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (goal == null || goal.isEmpty()) throw new IllegalArgumentException("goal required");
        if (worktree == null) throw new IllegalArgumentException("mainWorktreeId required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
    }

    @Override
    public Events.NodeType nodeType() {
        return Events.NodeType.WORK;
    }

    @Override
    public String getView() {
        return workOutput;
    }

    /**
     * Create an updated version with new status.
     */
    public TicketOrchestratorNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Update work output and streaming progress.
     */
    public TicketOrchestratorNode withOutput(String output, int tokens) {
        return toBuilder()
                .workOutput(output)
                .streamingTokenCount(tokens)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Update progress.
     */
    public TicketOrchestratorNode withProgress(int completed, int total) {
        return toBuilder()
                .completedSubtasks(completed)
                .totalSubtasks(total)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Mark that merge is required.
     */
    public TicketOrchestratorNode requireMerge() {
        return toBuilder()
                .mergeRequired(true)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Add a child node ID.
     */
    public TicketOrchestratorNode addChildNode(String childNodeId) {
        List<String> newChildren = new ArrayList<>(childNodeIds);
        newChildren.add(childNodeId);
        return toBuilder()
                .childNodeIds(newChildren)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public TicketOrchestratorNode withTicketOrchestratorResult(AgentModels.TicketOrchestratorResult result) {
        return toBuilder()
                .ticketOrchestratorResult(result)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public TicketOrchestratorNode withInterruptibleContext(InterruptContext context) {
        return toBuilder()
                .interruptibleContext(context)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public String mainWorktreeId() {
        return worktree.worktreeId();
    }

    public List<WorkTree> submoduleWorktreeIds() {
        return this.worktree.submoduleWorktrees();
    }
}
