package com.hayden.multiagentidelib.transformation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.filter.model.executor.ExecutableTool;
import com.hayden.multiagentidelib.filter.service.FilterResult;
import com.hayden.multiagentidelib.transformation.model.layer.AiTransformerContext;
import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiTextTransformer(
        String id,
        String name,
        String description,
        String sourcePath,
        ExecutableTool<AgentModels.AiTransformerRequest, AgentModels.AiTransformerResult, AiTransformerContext> executor,
        FilterEnums.PolicyStatus status,
        int priority,
        boolean replaceEndpointResponse,
        Instant createdAt,
        Instant updatedAt
) implements Transformer<AgentModels.AiTransformerRequest, AgentModels.AiTransformerResult, AiTransformerContext> {

    @Override
    public AgentModels.AiTransformerResult apply(AgentModels.AiTransformerRequest input, AiTransformerContext ctx) {
        FilterResult<AgentModels.AiTransformerResult> result = executor == null ? null : executor.apply(input, ctx);
        if (result == null || result.t() == null) {
            return AgentModels.AiTransformerResult.builder()
                    .successful(false)
                    .transformedText(input == null ? null : input.input())
                    .errorMessage("Transformer returned no result")
                    .build();
        }
        return result.t();
    }
}
