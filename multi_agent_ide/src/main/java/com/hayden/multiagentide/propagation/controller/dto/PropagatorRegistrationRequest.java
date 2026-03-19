package com.hayden.multiagentide.propagation.controller.dto;

import com.hayden.multiagentidelib.propagation.model.executor.AiPropagatorTool;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.List;

@Schema(description = "Request body for registering a new propagator. The executor field (with registrarPrompt) is the most important part — it contains the AI instructions.")
@Builder(toBuilder = true)
public record PropagatorRegistrationRequest(
        @Schema(description = "Unique name for this propagator registration.", requiredMode = Schema.RequiredMode.REQUIRED, example = "ai-prop-myAction-action-request")
        @NotBlank String name,
        @Schema(description = "Human-readable description of what this propagator does.")
        String description,
        @Schema(description = "The action/layer path this propagator is associated with.", requiredMode = Schema.RequiredMode.REQUIRED, example = "workflow-agent/coordinateWorkflow")
        @NotBlank String sourcePath,
        @Schema(description = "The kind of propagator. Use AI_TEXT for AI-powered propagators.", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"AI_TEXT", "PASS_THROUGH", "SCRIPT"}, example = "AI_TEXT")
        @NotBlank String propagatorKind,
        @Schema(description = "Execution priority. Lower values run first.", example = "100")
        int priority,
        @Schema(description = "Propagation mode. Null uses the default.")
        String propagationMode,
        @Schema(description = "Whether child nodes inherit this propagator.")
        boolean isInheritable,
        @Schema(description = "Whether this propagator's output propagates to the parent node.")
        boolean isPropagatedToParent,
        @Schema(description = "Layer bindings that control which layers and stages trigger this propagator.")
        List<LayerBindingRequest> layerBindings,
        @Schema(description = "AI propagator executor configuration. Contains registrarPrompt (the AI instructions), sessionMode, and configVersion. Only AI_PROPAGATOR executors are supported via this API.")
        AiPropagatorTool executor,
        @Schema(description = "Whether to activate the propagator immediately after registration.")
        boolean activate
) {
    @Schema(description = "Binds a propagator to a specific layer and stage.")
    @Builder(toBuilder = true)
    public record LayerBindingRequest(
            @Schema(description = "The layer ID to bind to. May contain slashes.", requiredMode = Schema.RequiredMode.REQUIRED, example = "workflow-agent/coordinateWorkflow")
            String layerId,
            @Schema(description = "Layer type filter. Null matches all types.")
            String layerType,
            @Schema(description = "Layer key filter. Null matches all keys.")
            String layerKey,
            @Schema(description = "Whether this binding is active.")
            boolean enabled,
            @Schema(description = "Whether to also match descendant layers.")
            boolean includeDescendants,
            @Schema(description = "Whether child nodes inherit this binding.")
            boolean isInheritable,
            @Schema(description = "Whether this binding propagates to parent.")
            boolean isPropagatedToParent,
            @Schema(description = "Optional matcher key for advanced filtering.")
            String matcherKey,
            @Schema(description = "Optional matcher type for advanced filtering.")
            String matcherType,
            @Schema(description = "Optional matcher text for advanced filtering.")
            String matcherText,
            @Schema(description = "The stage to match on: ACTION_REQUEST or ACTION_RESPONSE.", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"ACTION_REQUEST", "ACTION_RESPONSE"}, example = "ACTION_REQUEST")
            String matchOn
    ) {
    }
}
