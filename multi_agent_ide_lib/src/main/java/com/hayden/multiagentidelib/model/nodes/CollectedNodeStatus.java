package com.hayden.multiagentidelib.model.nodes;

import com.hayden.utilitymodule.acp.events.Events;

/**
 * Snapshot of a collected node's identity and status at collection time.
 */
public record CollectedNodeStatus(
        String nodeId,
        String title,
        Events.NodeType nodeType,
        Events.NodeStatus status
) {
}
