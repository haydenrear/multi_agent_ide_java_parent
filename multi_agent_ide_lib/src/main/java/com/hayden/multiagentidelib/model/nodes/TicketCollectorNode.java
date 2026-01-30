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
 * Node that consolidates ticket execution results.
 */
@Builder(toBuilder = true)
public record TicketCollectorNode(
        String nodeId,
        String title,
        String goal,
        Events.NodeStatus status,
        String parentNodeId,
        List<String> childNodeIds,
        Map<String, String> metadata,
        Instant createdAt,
        Instant lastUpdatedAt,
        String ticketSummary,
        int totalTicketsCompleted,
        int totalTicketsFailed,
        List<CollectedNodeStatus> collectedNodes,
        AgentModels.CollectorDecision collectorDecision,
        AgentModels.TicketCollectorResult ticketCollectorResult,
        InterruptContext interruptibleContext
) implements GraphNode, Viewable<String>, Collector, Interruptible {

    public TicketCollectorNode(String nodeId, String title, String goal, Events.NodeStatus status, String parentNodeId, List<String> childNodeIds, Map<String, String> metadata, Instant createdAt, Instant lastUpdatedAt, String ticketSummary, int totalTicketsCompleted, int totalTicketsFailed) {
        this(nodeId, title, goal, status, parentNodeId, childNodeIds, metadata, createdAt, lastUpdatedAt,
                ticketSummary, totalTicketsCompleted, totalTicketsFailed, new ArrayList<>(), null, null, null);
    }

    public TicketCollectorNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
        if (collectedNodes == null) collectedNodes = new ArrayList<>();
    }

    @Override
    public Events.NodeType nodeType() {
        return Events.NodeType.WORK;
    }

    @Override
    public String getView() {
        return ticketSummary;
    }

    public TicketCollectorNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public TicketCollectorNode withSummary(String summary) {
        return toBuilder()
                .ticketSummary(summary)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public TicketCollectorNode withCollectedNodes(List<CollectedNodeStatus> nodes) {
        return toBuilder()
                .collectedNodes(nodes)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public TicketCollectorNode withResult(AgentModels.TicketCollectorResult result) {
        return toBuilder()
                .ticketCollectorResult(result)
                .collectorDecision(result != null ? result.collectorDecision() : collectorDecision)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public TicketCollectorNode withInterruptibleContext(InterruptContext context) {
        return toBuilder()
                .interruptibleContext(context)
                .lastUpdatedAt(Instant.now())
                .build();
    }
}
