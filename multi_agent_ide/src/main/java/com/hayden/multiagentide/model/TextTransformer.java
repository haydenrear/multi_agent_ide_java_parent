package com.hayden.multiagentide.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.filter.model.executor.ExecutableTool;
import com.hayden.multiagentide.filter.service.FilterResult;
import com.hayden.multiagentide.model.layer.ControllerEndpointTransformationContext;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TextTransformer(
        String id,
        String name,
        String description,
        String sourcePath,
        ExecutableTool<String, Object, ControllerEndpointTransformationContext> executor,
        FilterEnums.PolicyStatus status,
        int priority,
        boolean replaceEndpointResponse,
        Instant createdAt,
        Instant updatedAt
) implements Transformer<String, TransformationOutput, ControllerEndpointTransformationContext> {

    @Override
    public TransformationOutput apply(String input, ControllerEndpointTransformationContext ctx) {
        FilterResult<Object> result = executor == null ? null : executor.apply(input, ctx);
        Object raw = result == null ? null : result.t();
        return normalize(raw, ctx == null ? null : ctx.objectMapper(), input);
    }

    static TransformationOutput normalize(Object raw, ObjectMapper objectMapper, String fallbackText) {
        if (raw instanceof TransformationOutput output) {
            return output;
        }
        if (raw instanceof AgentModels.AiTransformerResult aiResult) {
            return aiResult.toOutput();
        }
        if (raw instanceof String text) {
            return TransformationOutput.builder()
                    .transformedText(text)
                    .metadata(Map.of())
                    .build();
        }
        if (raw != null && objectMapper != null) {
            try {
                return objectMapper.convertValue(raw, TransformationOutput.class);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return TransformationOutput.builder()
                .transformedText(fallbackText)
                .metadata(Map.of())
                .build();
    }
}
