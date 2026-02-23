package com.hayden.multiagentidelib.model.nodes;

import com.hayden.acp_cdc_ai.acp.events.EventNode;
import com.hayden.acp_cdc_ai.acp.events.Events;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Sealed interface representing a node in the computation graph.
 * Nodes can implement capability mixins (Branchable, Editable, etc.) for optional behaviors.
 * This is a data-oriented interface that composes capabilities.
 */
public sealed interface GraphNode extends EventNode
        permits AskPermissionNode, CollectorNode, DiscoveryCollectorNode, DiscoveryDispatchAgentNode, DiscoveryNode, DiscoveryOrchestratorNode, InterruptNode, MergeNode, OrchestratorNode, PlanningCollectorNode, PlanningDispatchAgentNode, PlanningNode, PlanningOrchestratorNode, ReviewNode, SummaryNode, TicketCollectorNode, TicketDispatchAgentNode, TicketNode, TicketOrchestratorNode {

    GraphNode withStatus(Events.NodeStatus nodeStatus);

    /**
     * Unique identifier for this node.
     */
    String nodeId();

    /**
     * Human-readable title/name.
     */
    String title();

    /**
     * Goal or description of work for this node.
     */
    String goal();

    /**
     * Current status of this node.
     */
    Events.NodeStatus status();

    /**
     * ID of parent node, or null if root.
     */
    String parentNodeId();

    /**
     * Child node IDs.
     */
    List<String> childNodeIds();

    /**
     * Metadata and annotations.
     */
    Map<String, String> metadata();

    /**
     * Timestamp when node was created.
     */
    Instant createdAt();

    /**
     * Timestamp when node status last changed.
     */
    Instant lastUpdatedAt();

    /**
     * Type/kind of this node for pattern matching.
     */
    Events.NodeType nodeType();

    /**
     * Check if this node implements a specific capability.
     */
    default boolean hasCapability(Class<?> capabilityClass) {
        return this.getClass().isAssignableFrom(capabilityClass)
                || capabilityClass.isAssignableFrom(this.getClass());
    }

}
