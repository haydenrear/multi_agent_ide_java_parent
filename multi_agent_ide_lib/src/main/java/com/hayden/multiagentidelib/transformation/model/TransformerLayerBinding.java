package com.hayden.multiagentidelib.transformation.model;

import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
public record TransformerLayerBinding(
        String layerId,
        boolean enabled,
        boolean includeDescendants,
        boolean isInheritable,
        boolean isPropagatedToParent,
        FilterEnums.MatcherKey matcherKey,
        FilterEnums.MatcherType matcherType,
        String matcherText,
        TransformerMatchOn matchOn,
        String updatedBy,
        Instant updatedAt
) {
}
