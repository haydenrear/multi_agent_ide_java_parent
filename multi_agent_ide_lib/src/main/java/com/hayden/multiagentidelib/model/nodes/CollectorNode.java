package com.hayden.multiagentidelib.model.nodes;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.Builder;

import java.time.Instant;
import java.util.*;

/**
 * Root node in the computation graph that orchestrates the overall goal.
 * Can be Branchable, and Viewable.
 */
@Builder(toBuilder = true)
public record CollectorNode(
        String nodeId,
        String title,
        String goal,
        Events.NodeStatus status,
        String parentNodeId,
        List<String> childNodeIds,
        Map<String, String> metadata,
        Instant createdAt,
        Instant lastUpdatedAt,
        String repositoryUrl,
        String baseBranch,
        boolean hasSubmodules,
        List<String> submoduleNames,
        String mainWorktreeId,
        List<String> submoduleWorktreeIds,
        String orchestratorOutput,
        List<SubmoduleNode> submodules,
        List<CollectedNodeStatus> collectedNodes,
        AgentModels.CollectorDecision collectorDecision,
        AgentModels.OrchestratorCollectorResult collectorResult,
        InterruptContext interruptibleContext
) implements GraphNode, Viewable<String>, Collector, Interruptible {

    public CollectorNode(String nodeId, String title, String goal, Events.NodeStatus status, String parentNodeId, List<String> childNodeIds, Map<String, String> metadata, Instant createdAt, Instant lastUpdatedAt, String repositoryUrl, String baseBranch, boolean hasSubmodules, List<String> submoduleNames, String mainWorktreeId, List<String> submoduleWorktreeIds, String orchestratorOutput) {
        this(nodeId, title, goal, status, parentNodeId, childNodeIds, metadata, createdAt, lastUpdatedAt,
                repositoryUrl, baseBranch, hasSubmodules, submoduleNames, mainWorktreeId, submoduleWorktreeIds, orchestratorOutput,
                new ArrayList<>(), new ArrayList<>(), null, null, null);
    }

    public CollectorNode(String nodeId, String title, String goal, Events.NodeStatus status, String parentNodeId, List<String> childNodeIds, Map<String, String> metadata, Instant createdAt, Instant lastUpdatedAt, String repositoryUrl, String baseBranch, boolean hasSubmodules, List<String> submoduleNames, String mainWorktreeId, List<String> submoduleWorktreeIds, String orchestratorOutput, List<SubmoduleNode> submodules) {
        this(nodeId, title, goal, status, parentNodeId, childNodeIds, metadata, createdAt, lastUpdatedAt,
                repositoryUrl, baseBranch, hasSubmodules, submoduleNames, mainWorktreeId, submoduleWorktreeIds, orchestratorOutput,
                submodules, new ArrayList<>(), null, null, null);
    }

    public CollectorNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (repositoryUrl == null || repositoryUrl.isEmpty()) throw new IllegalArgumentException("repositoryUrl required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
        if (submoduleNames == null) submoduleNames = new ArrayList<>();
        if (submoduleWorktreeIds == null) submoduleWorktreeIds = new ArrayList<>();
        if (collectedNodes == null) collectedNodes = new ArrayList<>();
    }

    @Override
    public Events.NodeType nodeType() {
        return Events.NodeType.ORCHESTRATOR;
    }

    @Override
    public String getView() {
        return orchestratorOutput;
    }

    /**
     * Create an updated version with new status.
     */
    public CollectorNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Add a child node ID.
     */
    public CollectorNode addChildNode(String childNodeId) {
        List<String> newChildren = new ArrayList<>(childNodeIds);
        newChildren.add(childNodeId);
        return toBuilder()
                .childNodeIds(newChildren)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Update orchestrator output.
     */
    public CollectorNode withOutput(String output) {
        return toBuilder()
                .orchestratorOutput(output)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public CollectorNode withResult(AgentModels.OrchestratorCollectorResult result) {
        return toBuilder()
                .collectorResult(result)
                .collectorDecision(result != null ? result.collectorDecision() : collectorDecision)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public CollectorNode withCollectedNodes(List<CollectedNodeStatus> nodes) {
        return toBuilder()
                .collectedNodes(nodes)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public CollectorNode withInterruptibleContext(InterruptContext context) {
        return toBuilder()
                .interruptibleContext(context)
                .lastUpdatedAt(Instant.now())
                .build();
    }
}
