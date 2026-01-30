package com.hayden.multiagentidelib.model.nodes;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.utilitymodule.acp.events.Events;
import lombok.Builder;

import java.time.Instant;
import java.util.*;

/**
 * Node that summarizes completed work.
 * Can be Summarizable, Viewable.
 */
@Builder(toBuilder = true)
public record DiscoveryCollectorNode(
        String nodeId,
        String title,
        String goal,
        Events.NodeStatus status,
        String parentNodeId,
        List<String> childNodeIds,
        Map<String, String> metadata,
        Instant createdAt,
        Instant lastUpdatedAt,
        String summaryContent,
        int totalTasksCompleted,
        int totalTasksFailed,
        List<CollectedNodeStatus> collectedNodes,
        AgentModels.CollectorDecision collectorDecision,
        AgentModels.DiscoveryCollectorResult discoveryCollectorResult,
        InterruptContext interruptibleContext
) implements GraphNode, Viewable<String>, Collector, Interruptible {

    public DiscoveryCollectorNode(String nodeId, String title, String goal, Events.NodeStatus status, String parentNodeId, List<String> childNodeIds, Map<String, String> metadata, Instant createdAt, Instant lastUpdatedAt, String summaryContent, int totalTasksCompleted, int totalTasksFailed) {
        this(nodeId, title, goal, status, parentNodeId, childNodeIds, metadata, createdAt, lastUpdatedAt,
                summaryContent, totalTasksCompleted, totalTasksFailed, new ArrayList<>(), null, null, null);
    }

    public DiscoveryCollectorNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
        if (collectedNodes == null) collectedNodes = new ArrayList<>();
    }

    @Override
    public Events.NodeType nodeType() {
        return Events.NodeType.SUMMARY;
    }

    @Override
    public String getView() {
        return summaryContent;
    }

    /**
     * Create an updated version with new status.
     */
    public DiscoveryCollectorNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Update summary content.
     */
    public DiscoveryCollectorNode withContent(String content) {
        return toBuilder()
                .summaryContent(content)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public DiscoveryCollectorNode withResult(AgentModels.DiscoveryCollectorResult result) {
        return toBuilder()
                .discoveryCollectorResult(result)
                .collectorDecision(result != null ? result.collectorDecision() : collectorDecision)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public DiscoveryCollectorNode withCollectedNodes(List<CollectedNodeStatus> nodes) {
        return toBuilder()
                .collectedNodes(nodes)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public DiscoveryCollectorNode withInterruptibleContext(InterruptContext context) {
        return toBuilder()
                .interruptibleContext(context)
                .lastUpdatedAt(Instant.now())
                .build();
    }


}
