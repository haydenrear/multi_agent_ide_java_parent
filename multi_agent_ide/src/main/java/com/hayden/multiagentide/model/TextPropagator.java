package com.hayden.multiagentide.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.filter.model.executor.ExecutableTool;
import com.hayden.multiagentide.filter.service.FilterResult;
import com.hayden.multiagentide.model.layer.DefaultPropagationContext;
import lombok.Builder;
import lombok.With;

import java.time.Instant;
import java.util.Map;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@With
public record TextPropagator(
        String id,
        String name,
        String description,
        String sourcePath,
        ExecutableTool<String, Object, DefaultPropagationContext> executor,
        FilterEnums.PolicyStatus status,
        int priority,
        Instant createdAt,
        Instant updatedAt
) implements Propagator<String, PropagationOutput, DefaultPropagationContext> {

    @Override
    public PropagationOutput apply(String input, DefaultPropagationContext ctx) {
        FilterResult<Object> result = executor == null ? null : executor.apply(input, ctx);
        Object raw = result == null ? null : result.t();
        return normalize(raw, ctx == null ? null : ctx.objectMapper(), input);
    }

    static PropagationOutput normalize(Object raw, ObjectMapper objectMapper, String fallbackText) {
        if (raw instanceof PropagationOutput output) {
            return output;
        }
        if (raw instanceof AgentModels.AiPropagatorResult aiResult) {
            return aiResult.toOutput();
        }
        if (raw instanceof String text) {
            return PropagationOutput.builder()
                    .propagatedText(text)
                    .summaryText(text)
                    .metadata(Map.of())
                    .build();
        }
        if (raw != null && objectMapper != null) {
            try {
                return objectMapper.convertValue(raw, PropagationOutput.class);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return PropagationOutput.builder()
                .propagatedText(fallbackText)
                .summaryText(fallbackText)
                .metadata(Map.of())
                .build();
    }
}
