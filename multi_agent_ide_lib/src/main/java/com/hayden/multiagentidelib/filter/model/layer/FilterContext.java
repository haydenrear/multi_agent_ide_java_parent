package com.hayden.multiagentidelib.filter.model.layer;

import com.embabel.agent.api.common.OperationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.filter.FilterFn;
import com.hayden.multiagentidelib.filter.config.FilterConfigProperties;
import com.hayden.multiagentidelib.filter.model.AiPathFilter;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.tool.ToolContext;
import lombok.Builder;
import lombok.experimental.Delegate;

import java.util.Map;
import java.util.Optional;

/**
 * Runtime context supplied to filter execution.
 * Extends LayerCtx so filters have access to the layer context they're executing in.
 */
public sealed interface FilterContext extends LayerCtx
        permits PromptContributorContext, GraphEventObjectContext, FilterContext.PathFilterContext {

    sealed interface PathFilterContext extends FilterContext permits DefaultPathFilterContext, AiFilterContext {
        FilterContext filterContext();
    }

    @Builder
    record AiFilterContext(
            @Delegate FilterContext filterContext,
            String templateName,
            PromptContext promptContext,
            Map<String, Object> model,
            ToolContext toolContext,
            Class<AgentModels.AiFilterResult> responseClass,
            OperationContext context) implements PathFilterContext {
    }

    String layerId();

    ArtifactKey key();

    FilterConfigProperties filterConfigProperties();

    void setFilterConfigProperties(FilterConfigProperties filterConfigProperties);

    ObjectMapper objectMapper();

    void setObjectMapper(ObjectMapper objectMapper);

    default Optional<FilterFn> fn()  {
        return Optional.empty();
    }

}
