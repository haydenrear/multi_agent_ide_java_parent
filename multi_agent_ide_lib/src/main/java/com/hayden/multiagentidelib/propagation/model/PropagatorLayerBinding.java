package com.hayden.multiagentidelib.propagation.model;

import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
public record PropagatorLayerBinding(
        String layerId,
        boolean enabled,
        boolean includeDescendants,
        boolean isInheritable,
        boolean isPropagatedToParent,
        FilterEnums.MatcherKey matcherKey,
        FilterEnums.MatcherType matcherType,
        String matcherText,
        PropagatorMatchOn matchOn,
        String updatedBy,
        Instant updatedAt
) {
}
