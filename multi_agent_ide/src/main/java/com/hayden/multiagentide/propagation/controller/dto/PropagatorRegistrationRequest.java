package com.hayden.multiagentide.propagation.controller.dto;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record PropagatorRegistrationRequest(
        String name,
        String description,
        String sourcePath,
        String propagatorKind,
        int priority,
        String propagationMode,
        boolean isInheritable,
        boolean isPropagatedToParent,
        List<LayerBindingRequest> layerBindings,
        Object executor,
        boolean activate
) {
    @Builder(toBuilder = true)
    public record LayerBindingRequest(
            String layerId,
            String layerType,
            String layerKey,
            boolean enabled,
            boolean includeDescendants,
            boolean isInheritable,
            boolean isPropagatedToParent,
            String matcherKey,
            String matcherType,
            String matcherText,
            String matchOn
    ) {
    }
}
