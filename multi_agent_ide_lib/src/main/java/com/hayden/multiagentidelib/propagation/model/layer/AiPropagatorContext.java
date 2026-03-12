package com.hayden.multiagentidelib.propagation.model.layer;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.filter.FilterFn;
import com.hayden.multiagentidelib.filter.config.FilterConfigProperties;
import com.hayden.multiagentidelib.filter.model.layer.FilterContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.tool.ToolContext;
import lombok.Builder;
import lombok.experimental.Delegate;

import java.util.Map;
import java.util.Optional;

@Builder(toBuilder = true)
public record AiPropagatorContext(
        @Delegate DefaultPropagationContext propagationContext,
        String templateName,
        PromptContext promptContext,
        Map<String, Object> model,
        ToolContext toolContext,
        Class<AgentModels.AiPropagatorResult> responseClass,
        OperationContext context
) implements FilterContext {

    @Override
    public String layerId() {
        return propagationContext.layerId();
    }

    @Override
    public ArtifactKey key() {
        return propagationContext.key();
    }

    @Override
    public FilterConfigProperties filterConfigProperties() {
        return propagationContext.filterConfigProperties();
    }

    @Override
    public void setFilterConfigProperties(FilterConfigProperties filterConfigProperties) {
        propagationContext.setFilterConfigProperties(filterConfigProperties);
    }

    @Override
    public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return propagationContext.objectMapper();
    }

    @Override
    public void setObjectMapper(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        propagationContext.setObjectMapper(objectMapper);
    }

    @Override
    public Optional<FilterFn> fn() {
        return propagationContext.fn();
    }
}
