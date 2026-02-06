package com.hayden.multiagentide.artifacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.ArtifactHashing;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

/**
 * Service for emitting artifacts during agent execution.
 * 
 * CRITICAL: Artifacts are emitted as children of the current agent's ArtifactKey (contextId),
 * NOT as children of the workflow run ID. This preserves the hierarchical tree structure
 * that mirrors the agent execution tree.
 * 
 * Provides methods to emit:
 * - AgentRequestArtifact
 * - AgentResultArtifact
 * - InterruptRequestArtifact
 * - InterruptResolutionArtifact
 * - CollectorDecisionArtifact
 * - ExecutionConfigArtifact
 * - OutcomeEvidenceArtifact
 * - RenderedPromptArtifact
 * - PromptArgsArtifact
 * - GraphEvent as EventArtifact
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArtifactEmissionService {
    
    private final ExecutionScopeService executionScopeService;

    /**
     * Emits an AgentRequestArtifact for a model being processed.
     *
     * The artifact is created as a child of the model's own contextId (ArtifactKey),
     * preserving the hierarchical structure.
     *
     * @param model The agent model to emit
     */
    public void emitAgentModel(
            Artifact.AgentModel model,
            Artifact.HashContext context
    ) {
        if (model == null) {
            log.debug("Skipping null agent model emission");
            return;
        }

        try {
            // The parent key is the model's own contextId - artifacts for this
            // agent's execution are children of this model
            ArtifactKey parentKey = model.key();
            if (parentKey == null) {
                log.warn("AgentRequest has no contextId, cannot emit artifact: {}",
                        model.getClass().getSimpleName());
                return;
            }

            var artifact = model.toArtifact(context);
            var artifactKey = artifact.artifactKey();
            executionScopeService.emitArtifact(artifact, parentKey);
            log.debug("Emitted AgentRequestArtifact: {} under {}", artifactKey, parentKey);

        } catch (Exception e) {
            log.warn("Failed to emit AgentRequestArtifact for {}: {}",
                    model.getClass().getSimpleName(), e.getMessage());
        }
    }

}
