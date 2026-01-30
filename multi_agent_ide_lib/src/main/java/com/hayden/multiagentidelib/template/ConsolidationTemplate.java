package com.hayden.multiagentidelib.template;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.ContextId;

import java.util.List;
import java.util.Map;

public interface ConsolidationTemplate {

    String schemaVersion();

    ContextId resultId();

    List<InputReference> inputs();

    String mergeStrategy();

    List<ConflictResolution> conflictResolutions();

    Map<String, Double> aggregatedMetrics();

    String consolidatedOutput();

    AgentModels.CollectorDecision decision();

    List<ContextId> upstreamContextChain();

    Map<String, String> metadata();

    default List<Curation> curations() {
        return List.of();
    }

    record InputReference(
            ContextId inputContextId,
            String inputType,
            String inputSummary
    ) {
    }

    record ConflictResolution(
            String conflictDescription,
            String resolutionApproach,
            List<ContextId> conflictingInputs
    ) {
    }

    interface Curation {
        ContextId contextId();

        ContextId sourceResultId();

        String selectionRationale();
    }

    record ConsolidationSummary(
            List<InputReference> inputs,
            String mergeStrategy,
            List<ConflictResolution> conflictResolutions,
            Map<String, Double> aggregatedMetrics,
            String consolidatedOutput,
            AgentModels.CollectorDecision decision,
            List<ContextId> upstreamContextChain,
            Map<String, String> metadata
    ) {
    }
}
