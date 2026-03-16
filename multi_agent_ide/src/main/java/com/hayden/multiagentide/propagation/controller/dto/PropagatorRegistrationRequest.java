package com.hayden.multiagentide.propagation.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record PropagatorRegistrationRequest(
        @NotBlank String name,
        String description,
        @NotBlank String sourcePath,
        @NotBlank String propagatorKind,
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
