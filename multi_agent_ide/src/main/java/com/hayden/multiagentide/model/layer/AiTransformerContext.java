package com.hayden.multiagentide.model.layer;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.filter.FilterFn;
import com.hayden.multiagentide.filter.config.FilterConfigProperties;
import com.hayden.multiagentide.filter.model.layer.FilterContext;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.tool.ToolContext;
import lombok.Builder;
import lombok.experimental.Delegate;

import java.util.Map;
import java.util.Optional;

@Builder(toBuilder = true)
public record AiTransformerContext(
        @Delegate ControllerEndpointTransformationContext transformationContext,
        String templateName,
        PromptContext promptContext,
        Map<String, Object> model,
        ToolContext toolContext,
        Class<AgentModels.AiTransformerResult> responseClass,
        OperationContext context
) implements FilterContext {

    @Override
    public String layerId() {
        return transformationContext.layerId();
    }

    @Override
    public ArtifactKey key() {
        return transformationContext.key();
    }

    @Override
    public FilterConfigProperties filterConfigProperties() {
        return transformationContext.filterConfigProperties();
    }

    @Override
    public void setFilterConfigProperties(FilterConfigProperties filterConfigProperties) {
        transformationContext.setFilterConfigProperties(filterConfigProperties);
    }

    @Override
    public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return transformationContext.objectMapper();
    }

    @Override
    public void setObjectMapper(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        transformationContext.setObjectMapper(objectMapper);
    }

    @Override
    public Optional<FilterFn> fn() {
        return transformationContext.fn();
    }
}
