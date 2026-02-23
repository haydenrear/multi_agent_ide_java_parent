package com.hayden.multiagentide.agent;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.model.nodes.*;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class GraphNodeFactory {

    public String newNodeId() {
        return ArtifactKey.createRoot().value();
    }

    private String resolveNodeId(String nodeId) {
        return (nodeId == null || nodeId.isBlank()) ? newNodeId() : nodeId;
    }

    public CollectorNode orchestratorCollectorNode(OrchestratorNode orchestrator, String goal, ArtifactKey artifactKey) {
        Instant now = Instant.now();
        var submoduleWorktrees = orchestrator.submoduleWorktrees();
        var submoduleNames = submoduleWorktrees == null
                ? new ArrayList<String>()
                : submoduleWorktrees.stream()
                .map(SubmoduleWorktreeContext::submoduleName)
                .toList();
        var submoduleWorktreeIds = submoduleWorktrees == null
                ? new ArrayList<String>()
                : submoduleWorktrees.stream()
                .map(SubmoduleWorktreeContext::worktreeId)
                .toList();
        return new CollectorNode(
                artifactKey != null ? artifactKey.value() : newNodeId(),
                "Workflow Collector",
                goal,
                Events.NodeStatus.READY,
                orchestrator.nodeId(),
                new ArrayList<>(),
                new ConcurrentHashMap<>(),
                now,
                now,
                orchestrator.worktreeContext().repositoryUrl(),
                orchestrator.worktreeContext().baseBranch(),
                orchestrator.hasSubmodules(),
                submoduleNames,
                orchestrator.mainWorktreeId(),
                submoduleWorktreeIds,
                ""
        );
    }

    public DiscoveryOrchestratorNode discoveryOrchestratorNode(String parentId, String goal, ArtifactKey artifactKey) {
        Instant now = Instant.now();
        return new DiscoveryOrchestratorNode(
                artifactKey != null ? artifactKey.value() : newNodeId(),
                "Discovery Orchestrator",
                goal,
                Events.NodeStatus.READY,
                parentId,
                new ArrayList<>(),
                new ConcurrentHashMap<>(),
                now,
                now,
                "",
                0,
                0
        );
    }

    public DiscoveryNode discoveryNode(String parentId, String goal, String title, AgentModels.DiscoveryAgentRequest enrichedRequest, ArtifactKey artifactKey) {
        Instant now = Instant.now();
        return new DiscoveryNode(
                artifactKey != null ? artifactKey.value() : newNodeId(),
                title,
                goal,
                Events.NodeStatus.READY,
                parentId,
                new ArrayList<>(),
                new ConcurrentHashMap<>(),
                now,
                now,
                "",
                0,
                0
        );
    }

    public DiscoveryCollectorNode discoveryCollectorNode(String parentId, String goal, ArtifactKey artifactKey) {
        Instant now = Instant.now();
        return new DiscoveryCollectorNode(
                artifactKey != null ? artifactKey.value() : newNodeId(),
                "Discovery Collector",
                goal,
                Events.NodeStatus.READY,
                parentId,
                new ArrayList<>(),
                new ConcurrentHashMap<>(),
                now,
                now,
                "",
                0,
                0
        );
    }

    public DiscoveryDispatchAgentNode discoveryDispatchAgentNode(
            String parentId,
            AgentModels.DiscoveryAgentRequests input
    ) {
        Instant now = Instant.now();
        int requestCount = input != null && input.requests() != null ? input.requests().size() : 0;
        return new DiscoveryDispatchAgentNode(
                input != null && input.contextId() != null ? input.contextId().value() : newNodeId(),
                "Discovery Dispatch",
                input != null ? input.goal() : "",
                Events.NodeStatus.READY,
                parentId,
                new ArrayList<>(),
                new ConcurrentHashMap<>(),
                now,
                now,
                requestCount,
                input != null ? input.delegationRationale() : "",
                input,
                null,
                WorkflowContext.initial()
        );
    }

    public PlanningOrchestratorNode planningOrchestratorNode(
            String parentId,
            String goal,
            Map<String, String> metadata,
            ArtifactKey artifactKey
    ) {
        Instant now = Instant.now();
        return new PlanningOrchestratorNode(
                artifactKey != null ? artifactKey.value() : newNodeId(),
                "Planning Orchestrator",
                goal,
                Events.NodeStatus.READY,
                parentId,
                new ArrayList<>(),
                new ConcurrentHashMap<>(metadata),
                now,
                now,
                new ArrayList<>(),
                "",
                0,
                0
        );
    }

    public PlanningNode planningNode(
            String parentId,
            String goal,
            String title,
            Map<String, String> metadata,
            ArtifactKey artifactKey
    ) {
        Instant now = Instant.now();
        return new PlanningNode(
                artifactKey != null ? artifactKey.value() : newNodeId(),
                title,
                goal,
                Events.NodeStatus.READY,
                parentId,
                new ArrayList<>(),
                new ConcurrentHashMap<>(metadata),
                now,
                now,
                new ArrayList<>(),
                "",
                0,
                0
        );
    }

    public PlanningCollectorNode planningCollectorNode(String parentId, String goal, ArtifactKey artifactKey) {
        Instant now = Instant.now();
        return new PlanningCollectorNode(
                artifactKey != null ? artifactKey.value() : newNodeId(),
                "Planning Collector",
                goal,
                Events.NodeStatus.READY,
                parentId,
                new ArrayList<>(),
                new ConcurrentHashMap<>(),
                now,
                now,
                new ArrayList<>(),
                "",
                0,
                0
        );
    }

    public PlanningDispatchAgentNode planningDispatchAgentNode(
            String parentId,
            AgentModels.PlanningAgentRequests input
    ) {
        Instant now = Instant.now();
        int requestCount = input != null && input.requests() != null ? input.requests().size() : 0;
        return new PlanningDispatchAgentNode(
                input != null && input.contextId() != null ? input.contextId().value() : newNodeId(),
                "Planning Dispatch",
                input != null ? input.goal() : "",
                Events.NodeStatus.READY,
                parentId,
                new ArrayList<>(),
                new ConcurrentHashMap<>(),
                now,
                now,
                requestCount,
                input != null ? input.delegationRationale() : "",
                input,
                null,
                WorkflowContext.initial()
        );
    }

    public TicketOrchestratorNode ticketOrchestratorNode(
            String parentId,
            String goal,
            Map<String, String> metadata,
            HasWorktree.WorkTree worktree,
            ArtifactKey artifactKey
    ) {
        Instant now = Instant.now();
        return new TicketOrchestratorNode(
                artifactKey != null ? artifactKey.value() : newNodeId(),
                "Ticket Orchestrator",
                goal,
                Events.NodeStatus.READY,
                parentId,
                new ArrayList<>(),
                new ConcurrentHashMap<>(metadata),
                now,
                now,
                worktree,
                0,
                0,
                "ticket-orchestrator",
                "",
                true,
                0
        );
    }

    public TicketNode ticketNode(
            String parentId,
            String title,
            String ticketDetails,
            Map<String, String> metadata,
            HasWorktree.WorkTree worktree,
            ArtifactKey artifactKey
    ) {
        Instant now = Instant.now();
        return new TicketNode(
                artifactKey != null ? artifactKey.value() : newNodeId(),
                title,
                ticketDetails,
                Events.NodeStatus.READY,
                parentId,
                new ArrayList<>(),
                new ConcurrentHashMap<>(metadata),
                now,
                now,
                worktree,
                0,
                0,
                "ticket-agent",
                "",
                true,
                0
        );
    }

    public TicketCollectorNode ticketCollectorNode(String parentId, String goal, ArtifactKey artifactKey) {
        Instant now = Instant.now();
        return new TicketCollectorNode(
                artifactKey != null ? artifactKey.value() : newNodeId(),
                "Ticket Collector",
                goal,
                Events.NodeStatus.READY,
                parentId,
                new ArrayList<>(),
                new ConcurrentHashMap<>(),
                now,
                now,
                "",
                0,
                0
        );
    }

    public TicketDispatchAgentNode ticketDispatchAgentNode(
            String parentId,
            AgentModels.TicketAgentRequests input
    ) {
        Instant now = Instant.now();
        int requestCount = input != null && input.requests() != null ? input.requests().size() : 0;
        return new TicketDispatchAgentNode(
                input != null && input.contextId() != null ? input.contextId().value() : newNodeId(),
                "Ticket Dispatch",
                input != null ? input.goal() : "",
                Events.NodeStatus.READY,
                parentId,
                new ArrayList<>(),
                new ConcurrentHashMap<>(),
                now,
                now,
                requestCount,
                input != null ? input.delegationRationale() : "",
                input,
                null,
                WorkflowContext.initial()
        );
    }

    public ReviewNode reviewNode(String parentId, String goal, String reviewContent, String reviewerType, ArtifactKey artifactKey) {
        Instant now = Instant.now();
        return new ReviewNode(
                artifactKey != null ? artifactKey.value() : newNodeId(),
                "Review",
                goal,
                Events.NodeStatus.READY,
                parentId,
                new ArrayList<>(),
                new ConcurrentHashMap<>(),
                now,
                now,
                parentId,
                reviewContent,
                false,
                false,
                "",
                reviewerType,
                null
        );
    }

    public MergeNode mergeNode(String parentId, String goal, String summary, Map<String, String> metadata, ArtifactKey artifactKey) {
        Instant now = Instant.now();
        return new MergeNode(
                artifactKey != null ? artifactKey.value() : newNodeId(),
                "Merge",
                goal,
                Events.NodeStatus.READY,
                parentId,
                new ArrayList<>(),
                new ConcurrentHashMap<>(metadata),
                now,
                now,
                summary,
                0,
                0
        );
    }
}
