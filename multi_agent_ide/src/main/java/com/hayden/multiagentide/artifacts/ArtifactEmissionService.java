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

    private final EventArtifactMapper eventArtifactMapper;

    private final ObjectMapper objectMapper;
    
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

            ArtifactKey artifactKey = parentKey.createChild();

            var artifact = model.toArtifact(context);

            executionScopeService.emitArtifact(artifact, parentKey);
            log.debug("Emitted AgentRequestArtifact: {} under {}", artifactKey, parentKey);

        } catch (Exception e) {
            log.warn("Failed to emit AgentRequestArtifact for {}: {}",
                    model.getClass().getSimpleName(), e.getMessage());
        }
    }



    /**
     * Emits an ExecutionConfigArtifact with the configuration state.
     * 
     * This should be called at execution start to capture all configuration
     * needed for reproducibility.
     * 
     * @param workflowRunId The workflow run ID
     * @param repositorySnapshotId Git commit SHA or similar
     * @param modelRefs Model configurations
     * @param toolPolicy Tool availability and limits
     * @param routingPolicy Routing and loop parameters
     */
    public void emitExecutionConfig(
            String workflowRunId,
            String repositorySnapshotId,
            Map<String, Object> modelRefs,
            Map<String, Object> toolPolicy,
            Map<String, Object> routingPolicy
    ) {
        try {
            ArtifactKey artifactKey = executionScopeService.createChildKey(
                    workflowRunId, 
                    ExecutionScopeService.GROUP_EXECUTION_CONFIG
            );
            
            // Build a combined config map for hashing
            Map<String, Object> configMap = Map.of(
                    "repositorySnapshotId", repositorySnapshotId != null ? repositorySnapshotId : "",
                    "modelRefs", modelRefs != null ? modelRefs : Map.of(),
                    "toolPolicy", toolPolicy != null ? toolPolicy : Map.of(),
                    "routingPolicy", routingPolicy != null ? routingPolicy : Map.of()
            );
            String hash = ArtifactHashing.hashJson(configMap);
            
            Artifact.ExecutionConfigArtifact artifact = Artifact.ExecutionConfigArtifact.builder()
                    .artifactKey(artifactKey)
                    .repositorySnapshotId(repositorySnapshotId)
                    .modelRefs(modelRefs != null ? modelRefs : Map.of())
                    .toolPolicy(toolPolicy != null ? toolPolicy : Map.of())
                    .routingPolicy(routingPolicy != null ? routingPolicy : Map.of())
                    .hash(hash)
                    .metadata(Map.of())
                    .children(new ArrayList<>())
                    .build();
            
            executionScopeService.emitArtifactToGroup(
                    workflowRunId,
                    ExecutionScopeService.GROUP_EXECUTION_CONFIG,
                    artifact
            );
            log.debug("Emitted ExecutionConfigArtifact: {}", artifactKey);
            
        } catch (Exception e) {
            log.warn("Failed to emit ExecutionConfigArtifact for {}: {}", workflowRunId, e.getMessage());
        }
    }

    /**
     * Emits a GraphEvent as an EventArtifact.
     * 
     * @param parentKey The parent artifact key
     * @param event The GraphEvent to capture
     */
    public void emitGraphEvent(
            ArtifactKey parentKey,
            Events.GraphEvent event
    ) {
        if (parentKey == null) {
            log.warn("Cannot emit graph event without parent key");
            return;
        }
        
        if (event == null) {
            log.debug("Skipping null graph event emission");
            return;
        }
        
        // Skip ArtifactEvents to avoid infinite recursion
        if (!eventArtifactMapper.shouldCapture(event)) {
            return;
        }
        
        try {
            Artifact artifact;
            if (eventArtifactMapper.isStreamEvent(event)) {
                artifact = eventArtifactMapper.mapToStreamArtifact(event, parentKey);
            } else {
                artifact = eventArtifactMapper.mapToEventArtifact(event, parentKey);
            }
            
            executionScopeService.emitArtifact(artifact, parentKey);
            log.debug("Emitted EventArtifact: {} type={}", artifact.artifactKey(), event.eventType());
            
        } catch (Exception e) {
            log.warn("Failed to emit EventArtifact for {}: {}", event.eventType(), e.getMessage());
        }
    }
    
    /**
     * Emits a ToolCallArtifact for a tool invocation.
     * 
     * @param parentKey The parent artifact key (agent request's contextId)
     * @param toolCallEvent The tool call event
     */
    public void emitToolCall(
            ArtifactKey parentKey,
            Events.ToolCallEvent toolCallEvent
    ) {
        if (parentKey == null) {
            log.warn("Cannot emit tool call without parent key");
            return;
        }
        
        if (toolCallEvent == null) {
            log.debug("Skipping null tool call emission");
            return;
        }
        
        try {
            Artifact.ToolCallArtifact artifact = eventArtifactMapper.mapToolCallEvent(
                    toolCallEvent, parentKey);
            executionScopeService.emitArtifact(artifact, parentKey);
            log.debug("Emitted ToolCallArtifact: {} tool={}", 
                    artifact.artifactKey(), toolCallEvent.title());
            
        } catch (Exception e) {
            log.warn("Failed to emit ToolCallArtifact for {}: {}", 
                    toolCallEvent.title(), e.getMessage());
        }
    }
    
    // ========== Private Helpers ==========
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> toPayload(Object obj) {
        if (obj == null) {
            return Map.of();
        }
        try {
            String json = objectMapper.writeValueAsString(obj);
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Failed to convert object to payload: {}", e.getMessage());
            return Map.of("error", "Failed to serialize: " + e.getMessage());
        }
    }
    
    private String determineInteractionType(AgentModels.AgentRequest request) {
        if (request instanceof AgentModels.InterruptRequest) {
            return "INTERRUPT_REQUEST";
        }
        if (request instanceof AgentModels.ContextManagerRequest) {
            return "CONTEXT_MANAGER_REQUEST";
        }
        if (request instanceof AgentModels.ContextManagerRoutingRequest) {
            return "CONTEXT_MANAGER_ROUTING";
        }
        if (request instanceof AgentModels.ReviewRequest) {
            return "REVIEW_REQUEST";
        }
        if (request instanceof AgentModels.MergerRequest) {
            return "MERGER_REQUEST";
        }
        // Check for collector requests
        if (request.getClass().getSimpleName().contains("Collector")) {
            return "COLLECTOR_REQUEST";
        }
        // Check for orchestrator requests
        if (request.getClass().getSimpleName().contains("Orchestrator")) {
            return "ORCHESTRATOR_REQUEST";
        }
        // Check for dispatch requests (plural agent requests)
        if (request.getClass().getSimpleName().endsWith("Requests")) {
            return "DISPATCH_REQUEST";
        }
        return "AGENT_REQUEST";
    }
}
