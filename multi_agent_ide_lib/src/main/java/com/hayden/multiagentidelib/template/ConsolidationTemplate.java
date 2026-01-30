package com.hayden.multiagentidelib.template;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;

import java.util.List;
import java.util.Map;

public interface ConsolidationTemplate {

    ArtifactKey contextId();

    String consolidatedOutput();

    AgentModels.CollectorDecision decision();

    Map<String, String> metadata();

    default List<Curation> curations() {
        return List.of();
    }

    interface Curation {
    }

    record ConsolidationSummary(
            String consolidatedOutput,
            Map<String, String> metadata
    ) {
    }
}
