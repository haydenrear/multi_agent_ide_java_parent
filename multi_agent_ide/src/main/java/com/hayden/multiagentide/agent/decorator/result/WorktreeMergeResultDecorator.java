package com.hayden.multiagentide.agent.decorator.result;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentide.service.WorktreeAutoCommitService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.merge.MergeDirection;
import com.hayden.multiagentidelib.model.merge.WorktreeCommitMetadata;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Performs trunk -> child merges for dispatched agent results and stores merge metadata.
 *
 * The child worktree always comes from the result/request context.
 * The trunk worktree is resolved from child.mainWorktree.parentWorktreeId.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorktreeMergeResultDecorator implements DispatchedAgentResultDecorator, ResultDecorator {

    private final GitWorktreeService gitWorktreeService;
    private final WorktreeAutoCommitService worktreeAutoCommitService;
    private final EventBus eventBus;

    @Override
    public int order() {
        return 1000;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends AgentModels.Routing> T decorate(T routing, DecoratorContext context) {
        if (routing == null) {
            return null;
        }

        return switch (routing) {
            case AgentModels.DiscoveryAgentRouting r ->
                    (T) r.toBuilder().agentResult(decorate(r.agentResult(), context)).build();
            case AgentModels.PlanningAgentRouting r ->
                    (T) r.toBuilder().agentResult(decorate(r.agentResult(), context)).build();
            case AgentModels.TicketAgentRouting r ->
                    (T) r.toBuilder().agentResult(decorate(r.agentResult(), context)).build();
            default -> routing;
        };
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorate(T result, DecoratorContext context) {
        if (result == null) {
            return null;
        }

        T withContext = withWorktreeContext(result, context);
        if (!isDispatchResult(withContext)) {
            return withContext;
        }
        String nodeId = resolveNodeId(withContext);

        WorktreeSandboxContext childContext = withContext.worktreeContext();
        if (childContext == null || childContext.mainWorktree() == null) {
            String reason = "Skipping trunk->child merge: missing child worktree context";
            publishNodeError(nodeId, reason);
            publishSkippedMergePhase(nodeId, "unknown", "unknown", reason);
            return addMergeDescriptor(withContext, MergeDescriptor.noOp(MergeDirection.TRUNK_TO_CHILD));
        }

        AgentModels.CommitAgentResult autoCommitResult = worktreeAutoCommitService.autoCommitDirtyWorktrees(
                withContext,
                childContext,
                context,
                extractGoalHint(context)
        );
        if (!autoCommitResult.successful()) {
            String reason = "Auto-commit failed before trunk->child merge: " + autoCommitResult.errorMessage();
            publishNodeError(nodeId, reason);
            publishSkippedMergePhase(nodeId, "unknown", childContext.mainWorktree().worktreeId(), reason);
            MergeDescriptor failed = MergeDescriptor.conflict(
                    MergeDirection.TRUNK_TO_CHILD,
                    List.of(),
                    null,
                    List.of(),
                    reason
            ).withCommitMetadata(autoCommitResult.commitMetadata());
            return addMergeDescriptor(withContext, failed);
        }

        WorktreeSandboxContext trunkContext = resolveTrunkWorktreeContext(childContext, context);
        if (trunkContext == null || trunkContext.mainWorktree() == null) {
            String childId = childContext.mainWorktree().worktreeId() != null ? childContext.mainWorktree().worktreeId() : "unknown";
            String reason = "Skipping trunk->child merge: missing trunk worktree context for child " + childId;
            publishNodeError(nodeId, reason);
            publishSkippedMergePhase(nodeId, "unknown", childId, reason);
            return addMergeDescriptor(
                    withContext,
                    MergeDescriptor.noOp(MergeDirection.TRUNK_TO_CHILD).withCommitMetadata(autoCommitResult.commitMetadata())
            );
        }

        String childId = childContext.mainWorktree().worktreeId();
        String trunkId = trunkContext.mainWorktree().worktreeId();
        if (childId != null && childId.equals(trunkId)) {
            String reason = "Skipping trunk->child merge: child and trunk worktree are the same (" + childId + ")";
            publishNodeError(nodeId, reason);
            publishSkippedMergePhase(nodeId, trunkId, childId, reason);
            return addMergeDescriptor(
                    withContext,
                    MergeDescriptor.noOp(MergeDirection.TRUNK_TO_CHILD).withCommitMetadata(autoCommitResult.commitMetadata())
            );
        }

        publishIfAvailable(new Events.MergePhaseStartedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                "TRUNK_TO_CHILD",
                trunkId != null ? trunkId : "unknown",
                childId != null ? childId : "unknown",
                1));

        MergeDescriptor descriptor = gitWorktreeService.mergeTrunkToChild(trunkContext, childContext);
        descriptor = descriptor.withCommitMetadata(mergeCommitMetadata(
                descriptor.commitMetadata(),
                autoCommitResult.commitMetadata()
        ));

        publishIfAvailable(new Events.MergePhaseCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                "TRUNK_TO_CHILD",
                descriptor.successful(),
                descriptor.successful() ? 1 : 0,
                descriptor.conflictFiles() != null ? descriptor.conflictFiles().size() : 0,
                descriptor.conflictFiles() != null ? descriptor.conflictFiles() : List.of(),
                descriptor.errorMessage()));

        return addMergeDescriptor(withContext, descriptor);
    }

    private List<WorktreeCommitMetadata> mergeCommitMetadata(
            List<WorktreeCommitMetadata> existing,
            List<WorktreeCommitMetadata> autoCommits
    ) {
        List<WorktreeCommitMetadata> merged = new ArrayList<>();
        if (existing != null) {
            merged.addAll(existing);
        }
        if (autoCommits != null) {
            merged.addAll(autoCommits);
        }
        return merged;
    }

    private String extractGoalHint(DecoratorContext context) {
        if (context == null) {
            return "";
        }
        if (context.agentRequest() instanceof AgentModels.AgentRequest request) {
            return request.goalExtraction();
        }
        return "";
    }

    private void publishIfAvailable(Events.GraphEvent event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    private void publishNodeError(String nodeId, String reason) {
        log.error(reason);
        if (eventBus == null) {
            return;
        }
        eventBus.publish(Events.NodeErrorEvent.err(reason, safeKey(nodeId)));
    }

    private void publishSkippedMergePhase(String nodeId, String trunkId, String childId, String reason) {
        publishIfAvailable(new Events.MergePhaseStartedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                "TRUNK_TO_CHILD",
                trunkId != null ? trunkId : "unknown",
                childId != null ? childId : "unknown",
                1
        ));
        publishIfAvailable(new Events.MergePhaseCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                "TRUNK_TO_CHILD",
                false,
                0,
                0,
                List.of(),
                reason
        ));
    }

    private boolean isDispatchResult(AgentModels.AgentResult result) {
        return result instanceof AgentModels.TicketAgentResult
                || result instanceof AgentModels.PlanningAgentResult
                || result instanceof AgentModels.DiscoveryAgentResult;
    }

    private WorktreeSandboxContext resolveTrunkWorktreeContext(WorktreeSandboxContext childContext, DecoratorContext context) {
        if (context != null
                && context.lastRequest() instanceof AgentModels.AgentRequest lastRequest
                && lastRequest.worktreeContext() != null) {
            return lastRequest.worktreeContext();
        }

        if (childContext == null || childContext.mainWorktree() == null) {
            return null;
        }

        String parentWorktreeId = childContext.mainWorktree().parentWorktreeId();
        if (parentWorktreeId == null || parentWorktreeId.isBlank()) {
            return null;
        }

        return gitWorktreeService.getMainWorktree(parentWorktreeId)
                .map(main -> new WorktreeSandboxContext(main, gitWorktreeService.getSubmoduleWorktrees(parentWorktreeId)))
                .orElse(null);
    }

    private String resolveNodeId(AgentModels.AgentResult result) {
        if (result != null && result.contextId() != null && result.contextId().value() != null) {
            return result.contextId().value();
        }
        return "unknown";
    }

    private ArtifactKey safeKey(String nodeId) {
        if (nodeId == null || nodeId.isBlank() || "unknown".equals(nodeId)) {
            return ArtifactKey.createRoot();
        }
        try {
            return new ArtifactKey(nodeId);
        } catch (IllegalArgumentException e) {
            log.error("Could not create artifact key for nodeId {}", nodeId, e);
            return ArtifactKey.createRoot();
        }
    }

    private <T extends AgentModels.AgentResult> T withWorktreeContext(T result, DecoratorContext context) {
        if (result.worktreeContext() != null) {
            return result;
        }

        WorktreeSandboxContext resolved = null;
        if (context.agentRequest() instanceof AgentModels.AgentRequest request && request.worktreeContext() != null) {
            resolved = request.worktreeContext();
        } else if (context.lastRequest() instanceof AgentModels.AgentRequest request && request.worktreeContext() != null) {
            resolved = request.worktreeContext();
        }

        if (resolved == null) {
            return result;
        }

        return switch (result) {
            case AgentModels.TicketAgentResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.PlanningAgentResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.DiscoveryAgentResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            default -> result;
        };
    }

    @SuppressWarnings("unchecked")
    private <T extends AgentModels.AgentResult> T addMergeDescriptor(T result, MergeDescriptor descriptor) {
        return switch (result) {
            case AgentModels.TicketAgentResult r -> (T) r.toBuilder().mergeDescriptor(descriptor).build();
            case AgentModels.PlanningAgentResult r -> (T) r.toBuilder().mergeDescriptor(descriptor).build();
            case AgentModels.DiscoveryAgentResult r -> (T) r.toBuilder().mergeDescriptor(descriptor).build();
            default -> result;
        };
    }
}
