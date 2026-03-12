package com.hayden.multiagentide.transformation.controller.dto;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record TransformerRegistrationRequest(
        String name,
        String description,
        String sourcePath,
        String transformerKind,
        int priority,
        boolean replaceEndpointResponse,
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
