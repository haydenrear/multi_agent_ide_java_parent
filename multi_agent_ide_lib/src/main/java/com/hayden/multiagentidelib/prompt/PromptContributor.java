package com.hayden.multiagentidelib.prompt;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactHashing;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Templated;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface PromptContributor {

    String name();

    boolean include(PromptContext promptContext);

    String contribute(PromptContext context);

    String template();

    default Map<String, Object> args() {
        return Map.of();
    }

    int priority();

    default String templateStaticId() {
        return name();
    }

    default String templateText() {
        return template();
    }

    @JsonIgnore
    default Optional<String> contentHash() {
        String text = templateText();
        return text == null ? Optional.empty() : Optional.of(ArtifactHashing.hashText(text));
    }

    default String artifactType() {
        return "PromptContributionTemplate";
    }

    default Map<String, String> metadata() {
        return Map.of();
    }

    default List<Artifact> children() {
        return List.of();
    }

    default boolean isApplicable(PromptContext agentType) {
        if (agentType == null) {
            return false;
        }
        return include(agentType);
    }
}
