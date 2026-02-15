package com.hayden.multiagentide.model;

import java.time.Instant;
import java.util.Map;

public record RunTimelineEvent(
        String timelineEventId,
        String runId,
        long sequence,
        String eventId,
        String eventType,
        String nodeId,
        String scopeNodeId,
        Map<String, Object> payload,
        Instant occurredAt
) {
}
