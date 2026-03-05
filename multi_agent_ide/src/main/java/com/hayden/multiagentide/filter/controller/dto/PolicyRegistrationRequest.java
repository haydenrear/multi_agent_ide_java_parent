package com.hayden.multiagentide.filter.controller.dto;

import com.hayden.multiagentidelib.filter.model.executor.ExecutableTool;
import lombok.Builder;

import java.util.List;

/**
 * Request body for policy registration. The filter type is implicit from the endpoint.
 * No filter block or inputType/outputType fields.
 */
@Builder(toBuilder = true)
public record PolicyRegistrationRequest(
        String name,
        String description,
        String sourcePath,
        int priority,
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
