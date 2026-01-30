package com.hayden.multiagentidelib.model.nodes;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.Builder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root node in the computation graph that orchestrates the overall goal.
 * Can be Branchable, and Viewable.
 */
@Builder(toBuilder = true)
public record OrchestratorNode(
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
        AgentModels.OrchestratorAgentResult orchestratorResult,
        InterruptContext interruptibleContext
) implements GraphNode, Viewable<String>, Orchestrator, Interruptible {

    public OrchestratorNode(String nodeId, String title, String goal, Events.NodeStatus status, String parentNodeId, List<String> childNodeIds, Map<String, String> metadata, Instant createdAt, Instant lastUpdatedAt, String repositoryUrl, String baseBranch, boolean hasSubmodules, List<String> submoduleNames, String mainWorktreeId, List<String> submoduleWorktreeIds, String orchestratorOutput) {
        this(nodeId, title, goal, status, parentNodeId, childNodeIds, metadata, createdAt, lastUpdatedAt,
                repositoryUrl, baseBranch, hasSubmodules, submoduleNames, mainWorktreeId, submoduleWorktreeIds, orchestratorOutput,
                new ArrayList<>(), null, null);
    }

    public OrchestratorNode(String nodeId, String title, String goal, Events.NodeStatus status, String parentNodeId, List<String> childNodeIds, Map<String, String> metadata, Instant createdAt, Instant lastUpdatedAt, String repositoryUrl, String baseBranch, boolean hasSubmodules, List<String> submoduleNames, String mainWorktreeId, List<String> submoduleWorktreeIds, String orchestratorOutput, List<SubmoduleNode> submodules) {
        this(nodeId, title, goal, status, parentNodeId, childNodeIds, metadata, createdAt, lastUpdatedAt,
                repositoryUrl, baseBranch, hasSubmodules, submoduleNames, mainWorktreeId, submoduleWorktreeIds, orchestratorOutput,
                submodules, null, null);
    }

    public OrchestratorNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (repositoryUrl == null || repositoryUrl.isEmpty()) throw new IllegalArgumentException("repositoryUrl required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
        if (submoduleNames == null) submoduleNames = new ArrayList<>();
        if (submoduleWorktreeIds == null) submoduleWorktreeIds = new ArrayList<>();
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
    public OrchestratorNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Add a child node ID.
     */
    public OrchestratorNode addChildNode(String childNodeId) {
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
    public OrchestratorNode withOutput(String output) {
        return toBuilder()
                .orchestratorOutput(output)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public OrchestratorNode withResult(AgentModels.OrchestratorAgentResult result) {
        return toBuilder()
                .orchestratorResult(result)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public OrchestratorNode withInterruptibleContext(InterruptContext context) {
        return toBuilder()
                .interruptibleContext(context)
                .lastUpdatedAt(Instant.now())
                .build();
    }
}
