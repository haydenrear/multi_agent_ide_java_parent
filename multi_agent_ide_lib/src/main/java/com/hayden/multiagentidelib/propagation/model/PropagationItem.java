package com.hayden.multiagentidelib.propagation.model;

import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
public record PropagationItem(
        String itemId,
        String registrationId,
        String layerId,
        String sourceNodeId,
        String sourceName,
        String summaryText,
        String propagatedText,
        PropagationMode mode,
        PropagationItemStatus status,
        PropagationResolutionType resolutionType,
        String resolutionNotes,
        String correlationKey,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt
) {
}
