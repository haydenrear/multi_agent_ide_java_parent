package com.hayden.multiagentidelib.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;

import java.util.List;

public interface AgentContext extends Artifact.AgentModel, AgentPretty {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    ArtifactKey contextId();

    @Override
    @JsonIgnore
    default ArtifactKey key() {
        return contextId();
    }

    @Override
    @JsonIgnore
    default List<Artifact.AgentModel> children() {
        return List.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    default <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> c) {
        return (T) this;
    }

}
