package com.hayden.multiagentide.artifacts;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for ArtifactEvent emissions and builds/persists the artifact tree.
 * 
 * Event-driven artifact persistence:
 * - Subscribes to ARTIFACT_EMITTED events on the EventBus
 * - Delegates to ArtifactTreeBuilder for trie-based storage with hash deduplication
 * - Persists on execution completion via finished()
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArtifactEventListener implements EventListener {
    
    private final ArtifactTreeBuilder treeBuilder;

    @Value("${artifacts.persistence.enabled:true}")
    private boolean persistenceEnabled;
    
    // Track active executions
    private final Map<String, String> activeExecutions = new ConcurrentHashMap<>();

    @Override
    public String listenerId() {
        return "artifact-event-listener";
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        if (!persistenceEnabled) {
            return;
        }

        switch(event) {
            case Events.ArtifactEvent artifactEvent -> handleArtifactEvent(artifactEvent);
            case Events.NodeStreamDeltaEvent delta -> {}
            case Events.NodeThoughtDeltaEvent delta -> {}
            case Events.UserMessageChunkEvent delta -> {}
            case Events.PlanUpdateEvent delta -> {}
            default -> log.warn("Found event not subscribed to: {}", event);
        }
    }
    
    @Override
    public boolean isInterestedIn(Events.GraphEvent event) {
        return event instanceof Events.ArtifactEvent 
                || event instanceof Events.GoalCompletedEvent
                || event instanceof Events.NodeStreamDeltaEvent
                || event instanceof Events.NodeThoughtDeltaEvent
                || event instanceof Events.UserMessageChunkEvent
                || event instanceof Events.PlanUpdateEvent;
    }
    
    /**
     * Registers an active execution for artifact tracking.
     */
    public void registerExecution(String executionKey, String workflowRunId) {
        activeExecutions.put(workflowRunId, executionKey);
        log.debug("Registered execution: {} -> {}", workflowRunId, executionKey);
    }
    
    /**
     * Finishes an execution and returns the built artifact tree.
     * This persists all artifacts and returns the root with children populated.
     */
    public Optional<Artifact> finishPersistRemove(String executionKey) {
        Optional<Artifact> finished;
        if (persistenceEnabled) {
            finished = treeBuilder.persistRemoveExecution(executionKey);
        } else {
            finished = treeBuilder.buildRemoveArtifactTree(executionKey);
        }

        this.activeExecutions.remove(executionKey);
        return finished;
    }
    
    /**
     * Manually triggers persistence for an execution without finishing it.
     */
    public void flushExecution(String executionKey) {
        if (persistenceEnabled) {
            treeBuilder.persistExecutionTree(executionKey);
        }
    }
    
    // ========== Private Handlers ==========
    
    private void handleArtifactEvent(Events.ArtifactEvent event) {
        try {
            // Determine execution key from artifact key (root segment)
            ArtifactKey artifactKey = event.artifactKey();
            String executionKey = extractExecutionKey(artifactKey);

            // Get the artifact from the event
            Artifact artifact = event.artifact();
            if (artifact == null) {
                log.warn("Could not convert artifact event to Artifact: {}", event);
                return;
            }
            
            // Add to tree builder - deduplication is handled internally via trie structure
            // The tree builder will:
            // 1. Navigate to the correct position using hierarchical key
            // 2. Check siblings for matching content hash
            // 3. Add only if no duplicate key or hash exists
            // 4. Add the child to the parent artifact's children list
            boolean added = treeBuilder.addArtifact(executionKey, artifact);
            if (added) {
                log.debug("Added artifact: {} (type: {})", artifactKey.value(), event.artifactType());
            }
            
        } catch (Exception e) {
            log.error("Failed to handle artifact event: {}", event, e);
        }
    }
    
    private void handleExecutionComplete(Events.GoalCompletedEvent event) {
        String workflowRunId = event.orchestratorNodeId();
        String executionKey = findExecutionKey(workflowRunId);
        if (executionKey == null) {
            log.warn("No active execution found for completion: {}", workflowRunId);
            return;
        }

        // Finish and persist the execution
        log.info("Execution completed, finishing artifacts for: {}", executionKey);
        Optional<Artifact> result = treeBuilder.persistExecutionTree(executionKey);
        
        if (result.isPresent()) {
            log.info("Persisted artifact tree for execution: {} with {} children", 
                    executionKey, result.get().children().size());
        }
        
        // Remove from active executions
        activeExecutions.remove(workflowRunId);
    }

    private String findExecutionKey(String workflowRunIdOrExecutionKey) {
        if (workflowRunIdOrExecutionKey == null || workflowRunIdOrExecutionKey.isBlank()) {
            return null;
        }
        String executionKey = activeExecutions.get(workflowRunIdOrExecutionKey);
        if (executionKey != null) {
            return executionKey;
        }
        // Check if it's the execution key itself
        for (Map.Entry<String, String> entry : activeExecutions.entrySet()) {
            if (workflowRunIdOrExecutionKey.equals(entry.getValue())) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    private String extractExecutionKey(ArtifactKey artifactKey) {
        // The execution key is the root segment
        String value = artifactKey.value();
        int firstSlash = value.indexOf('/');
        if (firstSlash > 0) {
            return value.substring(0, firstSlash);
        }
        return value;
    }
}
