package com.hayden.multiagentide.agent;

import com.hayden.utilitymodule.acp.events.Events;
import com.hayden.multiagentidelib.model.nodes.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class GraphNodeFactory {

    public String newNodeId() {
        return UUID.randomUUID().toString();
    }

    public CollectorNode orchestratorCollectorNode(OrchestratorNode orchestrator, String goal) {
        Instant now = Instant.now();
        return new CollectorNode(
                newNodeId(),
                "Workflow Collector",
                goal,
                Events.NodeStatus.READY,
                orchestrator.nodeId(),
                new ArrayList<>(),
                new ConcurrentHashMap<>(),
                now,
                now,
                orchestrator.repositoryUrl(),
                orchestrator.baseBranch(),
                orchestrator.hasSubmodules(),
                orchestrator.submoduleNames(),
                orchestrator.mainWorktreeId(),
                orchestrator.submoduleWorktreeIds(),
                ""
        );
    }

    public DiscoveryOrchestratorNode discoveryOrchestratorNode(String parentId, String goal) {
        Instant now = Instant.now();
        return new DiscoveryOrchestratorNode(
                newNodeId(),
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

    public DiscoveryNode discoveryNode(String parentId, String goal, String title) {
        Instant now = Instant.now();
        return new DiscoveryNode(
                newNodeId(),
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

    public DiscoveryCollectorNode discoveryCollectorNode(String parentId, String goal) {
        Instant now = Instant.now();
        return new DiscoveryCollectorNode(
                newNodeId(),
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

    public PlanningOrchestratorNode planningOrchestratorNode(
            String parentId,
            String goal,
            Map<String, String> metadata
    ) {
        Instant now = Instant.now();
        return new PlanningOrchestratorNode(
                newNodeId(),
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
            Map<String, String> metadata
    ) {
        Instant now = Instant.now();
        return new PlanningNode(
                newNodeId(),
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

    public PlanningCollectorNode planningCollectorNode(String parentId, String goal) {
        Instant now = Instant.now();
        return new PlanningCollectorNode(
                newNodeId(),
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

    public TicketOrchestratorNode ticketOrchestratorNode(
            String parentId,
            String goal,
            Map<String, String> metadata,
            HasWorktree.WorkTree worktree
    ) {
        Instant now = Instant.now();
        return new TicketOrchestratorNode(
                newNodeId(),
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
            HasWorktree.WorkTree worktree
    ) {
        Instant now = Instant.now();
        return new TicketNode(
                newNodeId(),
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

    public TicketCollectorNode ticketCollectorNode(String parentId, String goal) {
        Instant now = Instant.now();
        return new TicketCollectorNode(
                newNodeId(),
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

    public ReviewNode reviewNode(String parentId, String goal, String reviewContent, String reviewerType) {
        Instant now = Instant.now();
        return new ReviewNode(
                newNodeId(),
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

    public MergeNode mergeNode(String parentId, String goal, String summary, Map<String, String> metadata) {
        Instant now = Instant.now();
        return new MergeNode(
                newNodeId(),
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
