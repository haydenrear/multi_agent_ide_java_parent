package com.hayden.multiagentide.artifacts;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.acp.events.MessageStreamArtifact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for ArtifactEvent emissions and builds/persists the artifact tree.
 *
 * Event-driven artifact persistence:
 * - Subscribes to ARTIFACT_EMITTED events on the EventBus
 * - Captures semantic graph events as EventArtifact nodes when they can be tied to an ArtifactKey
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
    private final Map<String, List<Artifact>> pendingArtifactsByExecution = new ConcurrentHashMap<>();

    @Override
    public String listenerId() {
        return "artifact-event-listener";
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        if (!persistenceEnabled) {
            return;
        }

        switch (event) {
            case Events.ArtifactEvent artifactEvent -> handleArtifactEvent(artifactEvent);
            case Events.NodeStreamDeltaEvent streamDeltaEvent -> handleStreamEvent(streamDeltaEvent);
            case Events.NodeThoughtDeltaEvent thoughtDeltaEvent -> handleStreamEvent(thoughtDeltaEvent);
            case Events.UserMessageChunkEvent userMessageChunkEvent -> handleStreamEvent(userMessageChunkEvent);
            case Events.AddMessageEvent addMessageEvent -> handleStreamEvent(addMessageEvent);

            case Events.NodeAddedEvent nodeAddedEvent -> handleEventArtifact(nodeAddedEvent);
            case Events.ActionStartedEvent actionStartedEvent -> handleEventArtifact(actionStartedEvent);
            case Events.ActionCompletedEvent actionCompletedEvent -> handleEventArtifact(actionCompletedEvent);
            case Events.StopAgentEvent stopAgentEvent -> handleEventArtifact(stopAgentEvent);
            case Events.PauseEvent pauseEvent -> handleEventArtifact(pauseEvent);
            case Events.ResumeEvent resumeEvent -> handleEventArtifact(resumeEvent);
            case Events.ResolveInterruptEvent resolveInterruptEvent -> handleEventArtifact(resolveInterruptEvent);
            case Events.InterruptRequestEvent interruptRequestEvent -> handleEventArtifact(interruptRequestEvent);
            case Events.NodeErrorEvent nodeErrorEvent -> handleEventArtifact(nodeErrorEvent);
            case Events.NodeBranchedEvent nodeBranchedEvent -> handleEventArtifact(nodeBranchedEvent);
            case Events.NodePrunedEvent nodePrunedEvent -> handleEventArtifact(nodePrunedEvent);
            case Events.NodeReviewRequestedEvent nodeReviewRequestedEvent -> handleEventArtifact(nodeReviewRequestedEvent);
            case Events.InterruptStatusEvent interruptStatusEvent -> handleEventArtifact(interruptStatusEvent);
            case Events.GoalStartedEvent goalStartedEvent -> handleEventArtifact(goalStartedEvent);
            case Events.GoalCompletedEvent goalCompletedEvent -> handleEventArtifact(goalCompletedEvent);
            case Events.WorktreeCreatedEvent worktreeCreatedEvent -> handleEventArtifact(worktreeCreatedEvent);
            case Events.WorktreeBranchedEvent worktreeBranchedEvent -> handleEventArtifact(worktreeBranchedEvent);
            case Events.WorktreeMergedEvent worktreeMergedEvent -> handleEventArtifact(worktreeMergedEvent);
            case Events.WorktreeDiscardedEvent worktreeDiscardedEvent -> handleEventArtifact(worktreeDiscardedEvent);
            case Events.NodeUpdatedEvent nodeUpdatedEvent -> handleEventArtifact(nodeUpdatedEvent);
            case Events.NodeDeletedEvent nodeDeletedEvent -> handleEventArtifact(nodeDeletedEvent);
            case Events.ChatSessionCreatedEvent chatSessionCreatedEvent -> handleEventArtifact(chatSessionCreatedEvent);
            case Events.ChatSessionClosedEvent chatSessionClosedEvent -> handleEventArtifact(chatSessionClosedEvent);
            case Events.AiFilterSessionEvent aiFilterSessionEvent -> handleEventArtifact(aiFilterSessionEvent);
            case Events.ToolCallEvent toolCallEvent -> handleEventArtifact(toolCallEvent);
            case Events.GuiRenderEvent guiRenderEvent -> handleEventArtifact(guiRenderEvent);
            case Events.UiDiffAppliedEvent uiDiffAppliedEvent -> handleEventArtifact(uiDiffAppliedEvent);
            case Events.UiDiffRejectedEvent uiDiffRejectedEvent -> handleEventArtifact(uiDiffRejectedEvent);
            case Events.UiDiffRevertedEvent uiDiffRevertedEvent -> handleEventArtifact(uiDiffRevertedEvent);
            case Events.UiFeedbackEvent uiFeedbackEvent -> handleEventArtifact(uiFeedbackEvent);
            case Events.NodeBranchRequestedEvent nodeBranchRequestedEvent -> handleEventArtifact(nodeBranchRequestedEvent);
            case Events.PlanUpdateEvent planUpdateEvent -> handleEventArtifact(planUpdateEvent);
            case Events.PermissionRequestedEvent permissionRequestedEvent -> handleEventArtifact(permissionRequestedEvent);
            case Events.PermissionResolvedEvent permissionResolvedEvent -> handleEventArtifact(permissionResolvedEvent);
            case Events.MergePhaseStartedEvent mergePhaseStartedEvent -> handleEventArtifact(mergePhaseStartedEvent);
            case Events.MergePhaseCompletedEvent mergePhaseCompletedEvent -> handleEventArtifact(mergePhaseCompletedEvent);

            case Events.AddChildNodeEvent ignored -> {
            }
            case Events.NodeStatusChangedEvent ignored -> {
            }
            case Events.CurrentModeUpdateEvent ignored -> {
            }
            case Events.AvailableCommandsUpdateEvent ignored -> {
            }
            case Events.TuiInteractionGraphEvent ignored -> {
            }
            case Events.TuiSystemGraphEvent ignored -> {
            }
        }
    }

    @Override
    public boolean isInterestedIn(Events.GraphEvent event) {
        return switch (event) {
            case Events.ArtifactEvent ignored -> true;
            case Events.NodeStreamDeltaEvent ignored -> true;
            case Events.NodeThoughtDeltaEvent ignored -> true;
            case Events.UserMessageChunkEvent ignored -> true;
            case Events.AddMessageEvent ignored -> true;
            case Events.NodeAddedEvent ignored -> true;
            case Events.ActionStartedEvent ignored -> true;
            case Events.ActionCompletedEvent ignored -> true;
            case Events.StopAgentEvent ignored -> true;
            case Events.PauseEvent ignored -> true;
            case Events.ResumeEvent ignored -> true;
            case Events.ResolveInterruptEvent ignored -> true;
            case Events.InterruptRequestEvent ignored -> true;
            case Events.NodeErrorEvent ignored -> true;
            case Events.NodeBranchedEvent ignored -> true;
            case Events.NodePrunedEvent ignored -> true;
            case Events.NodeReviewRequestedEvent ignored -> true;
            case Events.InterruptStatusEvent ignored -> true;
            case Events.GoalStartedEvent ignored -> true;
            case Events.GoalCompletedEvent ignored -> true;
            case Events.WorktreeCreatedEvent ignored -> true;
            case Events.WorktreeBranchedEvent ignored -> true;
            case Events.WorktreeMergedEvent ignored -> true;
            case Events.WorktreeDiscardedEvent ignored -> true;
            case Events.NodeUpdatedEvent ignored -> true;
            case Events.NodeDeletedEvent ignored -> true;
            case Events.ChatSessionCreatedEvent ignored -> true;
            case Events.ChatSessionClosedEvent ignored -> true;
            case Events.AiFilterSessionEvent ignored -> true;
            case Events.ToolCallEvent ignored -> true;
            case Events.GuiRenderEvent ignored -> true;
            case Events.UiDiffAppliedEvent ignored -> true;
            case Events.UiDiffRejectedEvent ignored -> true;
            case Events.UiDiffRevertedEvent ignored -> true;
            case Events.UiFeedbackEvent ignored -> true;
            case Events.NodeBranchRequestedEvent ignored -> true;
            case Events.PlanUpdateEvent ignored -> true;
            case Events.PermissionRequestedEvent ignored -> true;
            case Events.PermissionResolvedEvent ignored -> true;
            case Events.MergePhaseStartedEvent ignored -> true;
            case Events.MergePhaseCompletedEvent ignored -> true;
            case Events.AddChildNodeEvent ignored -> false;
            case Events.NodeStatusChangedEvent ignored -> false;
            case Events.CurrentModeUpdateEvent ignored -> false;
            case Events.AvailableCommandsUpdateEvent ignored -> false;
            case Events.TuiInteractionGraphEvent ignored -> false;
            case Events.TuiSystemGraphEvent ignored -> false;
        };
    }

    /**
     * Registers an active execution for artifact tracking.
     */
    public void registerExecution(String executionKey, String workflowRunId) {
        activeExecutions.put(workflowRunId, executionKey);
        log.debug("Registered execution: {} -> {}", workflowRunId, executionKey);
    }

    public void registerExecutionArtifact(String executionKey, Artifact workflowArtifact) {
        treeBuilder.addArtifact(executionKey, workflowArtifact);
        flushPendingArtifacts(executionKey);
        log.debug("Registered execution artifact for {}", executionKey);
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

        pendingArtifactsByExecution.remove(executionKey);
        activeExecutions.entrySet().removeIf(entry -> executionKey.equals(entry.getKey()) || executionKey.equals(entry.getValue()));
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

    private void handleArtifactEvent(Events.ArtifactEvent event) {
        try {
            Artifact artifact = event.artifact();
            if (artifact == null) {
                log.warn("Could not convert artifact event to Artifact: {}", event);
                return;
            }

            ArtifactKey artifactKey = event.artifactKey();
            String executionKey = extractExecutionKey(artifactKey);
            boolean added = treeBuilder.addArtifact(executionKey, artifact);
            if (added) {
                flushPendingArtifacts(executionKey);
                log.debug("Added artifact: {}", artifactKey.value());
            }
        } catch (Exception e) {
            log.error("Failed to handle artifact event: {}", event, e);
        }
    }

    /**
     * Handles stream-related events by converting them to MessageStreamArtifact nodes.
     */
    private void handleStreamEvent(Events.GraphEvent event) {
        try {
            MessageStreamArtifact streamArtifact = eventArtifactMapper.mapToStreamArtifact(event);
            String executionKey = extractExecutionKey(streamArtifact.artifactKey());
            addOrQueueArtifact(executionKey, streamArtifact);
        } catch (Exception e) {
            log.error("Failed to handle stream event: {}", event, e);
        }
    }

    /**
     * Handles semantic graph events by capturing them as EventArtifact nodes.
     */
    private void handleEventArtifact(Events.GraphEvent event) {
        try {
            Optional<Artifact.EventArtifact> maybeArtifact = eventArtifactMapper.mapToEventArtifactIfPossible(event);
            if (maybeArtifact.isEmpty()) {
                return;
            }

            Artifact.EventArtifact eventArtifact = maybeArtifact.get();
            String executionKey = extractExecutionKey(eventArtifact.artifactKey());
            addOrQueueArtifact(executionKey, eventArtifact);
        } catch (Exception e) {
            log.error("Failed to handle event artifact for {}", event, e);
        }
    }

    private void addOrQueueArtifact(String executionKey, Artifact artifact) {
        if (executionKey == null || executionKey.isBlank()) {
            log.debug("Skipping artifact with no execution key: {}", artifact.artifactKey());
            return;
        }

        if (treeBuilder.getExecutionTree(executionKey).isPresent()) {
            boolean added = treeBuilder.addArtifact(executionKey, artifact);
            if (added) {
                log.debug("Added derived artifact: {} ({})", artifact.artifactKey().value(), artifact.artifactType());
            }
            return;
        }

        pendingArtifactsByExecution
                .computeIfAbsent(executionKey, ignored -> java.util.Collections.synchronizedList(new ArrayList<>()))
                .add(artifact);
        log.debug("Queued artifact {} until execution root {} is available", artifact.artifactKey().value(), executionKey);
    }

    private void flushPendingArtifacts(String executionKey) {
        List<Artifact> pending = pendingArtifactsByExecution.remove(executionKey);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        for (Artifact artifact : pending) {
            boolean added = treeBuilder.addArtifact(executionKey, artifact);
            if (!added) {
                log.debug("Skipped queued artifact {} for execution {}", artifact.artifactKey().value(), executionKey);
            }
        }
    }

    private String extractExecutionKey(ArtifactKey artifactKey) {
        return artifactKey.isRoot() ? artifactKey.value() : artifactKey.root().value();
    }
}
