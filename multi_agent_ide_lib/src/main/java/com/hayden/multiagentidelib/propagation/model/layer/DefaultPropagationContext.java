package com.hayden.multiagentidelib.propagation.model.layer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentidelib.filter.config.FilterConfigProperties;
import com.hayden.multiagentidelib.filter.model.layer.FilterContext;
import com.hayden.multiagentidelib.propagation.model.PropagatorMatchOn;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Autowired;

@Data
public final class DefaultPropagationContext implements FilterContext {

    private final String layerId;
    private final ArtifactKey key;
    private final PropagatorMatchOn actionStage;
    private final String sourceName;
    private final String sourceNodeId;
    private final Object originalPayload;
    private final String serializedPayload;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Autowired
    private FilterConfigProperties filterConfigProperties;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private ObjectMapper objectMapper;

    @Override
    public String layerId() {
        return layerId;
    }

    @Override
    public ArtifactKey key() {
        return key;
    }

    @Override
    public FilterConfigProperties filterConfigProperties() {
        return filterConfigProperties;
    }

    @Override
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public PropagatorMatchOn actionStage() {
        return actionStage;
    }

    public String sourceName() {
        return sourceName;
    }

    public String sourceNodeId() {
        return sourceNodeId;
    }

    public Object originalPayload() {
        return originalPayload;
    }

    public String serializedPayload() {
        return serializedPayload;
    }
}
