package com.hayden.multiagentide.artifacts;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.acp.events.MessageStreamArtifact;
import com.hayden.utilitymodule.stream.StreamUtil;
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
    private final EventArtifactMapper eventArtifactMapper;

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
            case Events.NodeStreamDeltaEvent delta -> handleStreamEvent(delta);
            case Events.NodeThoughtDeltaEvent delta -> handleStreamEvent(delta);
            case Events.UserMessageChunkEvent delta -> handleStreamEvent(delta);
            case Events.PlanUpdateEvent delta -> handlePlanUpdateEvent(delta);
            case Events.ChatSessionCreatedEvent delta -> handleChatSessionCreatedEvent(delta);
            default -> log.warn("Found event not subscribed to: {}", event);
        }
    }

    private void handleChatSessionCreatedEvent(Events.ChatSessionCreatedEvent delta) {
        var m = eventArtifactMapper.mapToEventArtifact(delta);
        treeBuilder.addArtifact(m);
    }

    @Override
    public boolean isInterestedIn(Events.GraphEvent event) {
        log.info("Found event {}.", event.getClass().getSimpleName());
        return event instanceof Events.ArtifactEvent 
                || event instanceof Events.GoalCompletedEvent
                || event instanceof Events.NodeStreamDeltaEvent
                || event instanceof Events.NodeThoughtDeltaEvent
                || event instanceof Events.UserMessageChunkEvent
                || event instanceof Events.PlanUpdateEvent
                || event instanceof Events.ChatSessionCreatedEvent;
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
            boolean added = treeBuilder.addArtifact(artifact);
            if (added) {
                log.debug("Added artifact: {}:\n{}", artifactKey.value(), event.artifact());
            }
            
        } catch (Exception e) {
            log.error("Failed to handle artifact event: {}", event, e);
        }
    }
    
    /**
     * Handles stream delta events (NodeStreamDelta, NodeThoughtDelta, UserMessageChunk).
     * Converts them to MessageStreamArtifact and adds to the tree.
     */
    private void handleStreamEvent(Events.GraphEvent event) {
        try {
            String nodeId = event.nodeId();

            // Map to MessageStreamArtifact
            MessageStreamArtifact streamArtifact = eventArtifactMapper.mapToStreamArtifact(event);
            
            boolean added = treeBuilder.addArtifact(streamArtifact);
            if (added) {
                log.debug("Added stream artifact: {}:\n{}",
                        streamArtifact.artifactKey().value(), streamArtifact);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle stream event: {}", event, e);
        }
    }
    
    /**
     * Handles plan update events.
     * Converts them to EventArtifact and adds to the tree.
     */
    private void handlePlanUpdateEvent(Events.PlanUpdateEvent event) {
        try {
            // Map to EventArtifact (generic event capture)
            Artifact.EventArtifact eventArtifact = eventArtifactMapper.mapToEventArtifact(event);
            
            boolean added = treeBuilder.addArtifact(eventArtifact);
            if (added) {
                log.debug("Added plan update artifact: {} (type: {})", 
                        eventArtifact.artifactKey().value(), eventArtifact.eventType());
            }
            
        } catch (Exception e) {
            log.error("Failed to handle plan update event: {}", event, e);
        }
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
        return artifactKey.isRoot() ? artifactKey.value() : artifactKey.root().value();
    }
}
