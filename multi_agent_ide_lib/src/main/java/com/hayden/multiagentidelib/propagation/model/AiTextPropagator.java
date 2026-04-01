package com.hayden.multiagentidelib.propagation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.filter.model.executor.ExecutableTool;
import com.hayden.multiagentidelib.filter.service.FilterResult;
import com.hayden.multiagentidelib.propagation.model.layer.AiPropagatorContext;
import lombok.Builder;
import lombok.With;

import java.time.Instant;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@With
public record AiTextPropagator(
        String id,
        String name,
        String description,
        String sourcePath,
        ExecutableTool<AgentModels.AiPropagatorRequest, AgentModels.AiPropagatorResult, AiPropagatorContext> executor,
        FilterEnums.PolicyStatus status,
        int priority,
        Instant createdAt,
        Instant updatedAt
) implements Propagator<AgentModels.AiPropagatorRequest, AgentModels.AiPropagatorResult, AiPropagatorContext> {

    @Override
    public AgentModels.AiPropagatorResult apply(AgentModels.AiPropagatorRequest input, AiPropagatorContext ctx) {
        FilterResult<AgentModels.AiPropagatorResult> result = executor == null ? null : executor.apply(input, ctx);
        if (result == null || result.t() == null) {
            return AgentModels.AiPropagatorResult.builder()
                    .successful(false)
                    .propagatedText(input == null ? null : input.input())
                    .summaryText(input == null ? null : input.input())
                    .errorMessage("Propagator returned no result")
                    .build();
        }
        return result.t();
    }
}
