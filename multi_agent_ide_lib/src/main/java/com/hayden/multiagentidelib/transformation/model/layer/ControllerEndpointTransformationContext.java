package com.hayden.multiagentidelib.transformation.model.layer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentidelib.filter.config.FilterConfigProperties;
import com.hayden.multiagentidelib.filter.model.layer.FilterContext;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Autowired;

@Data
public final class ControllerEndpointTransformationContext implements FilterContext {

    private final String layerId;
    private final ArtifactKey key;
    private final String controllerId;
    private final String endpointId;
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

    public String controllerId() {
        return controllerId;
    }

    public String endpointId() {
        return endpointId;
    }

    public Object originalPayload() {
        return originalPayload;
    }

    public String serializedPayload() {
        return serializedPayload;
    }
}
