package com.hayden.multiagentide.service;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.MergeResult;
import com.hayden.multiagentidelib.model.merge.AgentMergeStatus;
import com.hayden.multiagentidelib.model.merge.MergeAggregation;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.merge.MergeDirection;
import com.hayden.multiagentidelib.model.merge.MergeErrorType;
import com.hayden.multiagentidelib.model.merge.SubmoduleMergeResult;
import com.hayden.multiagentidelib.model.merge.WorktreeCommitMetadata;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitMergeService {

    private final GitWorktreeService gitWorktreeService;
    private final WorktreeAutoCommitService worktreeAutoCommitService;
    private final WorktreeMergeConflictService worktreeMergeConflictService;
    private final EventBus eventBus;

    public MergeDescriptor mergeTrunkToChildWithAutoCommit(
            AgentModels.AgentResult sourceResult,
            WorktreeSandboxContext childContext,
            WorktreeSandboxContext trunkContext,
            DecoratorContext decoratorContext,
            String goalHint
    ) {
        if (childContext == null || childContext.mainWorktree() == null) {
            return MergeDescriptor.noOp(MergeDirection.TRUNK_TO_CHILD);
        }

        AgentModels.CommitAgentResult autoCommitResult = worktreeAutoCommitService.autoCommitDirtyWorktrees(
                sourceResult,
                childContext,
                decoratorContext,
                goalHint
        );
        if (!autoCommitResult.successful()) {
            String message = "Auto-commit failed before trunk->child merge: " + autoCommitResult.errorMessage();
            publishNodeError(decoratorContext, sourceResult, null, message);
            return MergeDescriptor.conflict(
                    MergeDirection.TRUNK_TO_CHILD,
                    List.of(),
                    null,
                    List.of(),
                    message,
                    MergeErrorType.AUTO_COMMIT_FAILED
            ).withCommitMetadata(autoCommitResult.commitMetadata());
        }

        if (trunkContext == null || trunkContext.mainWorktree() == null) {
            log.error("Trunk context was null - skipping merge.");
            return MergeDescriptor.noOp(MergeDirection.TRUNK_TO_CHILD)
                    .withCommitMetadata(autoCommitResult.commitMetadata());
        }

        String childId = childContext.mainWorktree().worktreeId();
        String trunkId = trunkContext.mainWorktree().worktreeId();
        if (childId != null && childId.equals(trunkId)) {
            log.error("Child ID didn't match - skipping merge.");
            return MergeDescriptor.noOp(MergeDirection.TRUNK_TO_CHILD)
                    .withCommitMetadata(autoCommitResult.commitMetadata());
        }

        MergeDescriptor descriptor = gitWorktreeService.mergeTrunkToChild(trunkContext, childContext);

        descriptor = descriptor.withCommitMetadata(mergeCommitMetadata(
                descriptor.commitMetadata(),
                autoCommitResult.commitMetadata()
        ));
        AgentModels.MergeConflictResult conflictResult = worktreeMergeConflictService.runForResult(
                sourceResult,
                descriptor,
                trunkContext,
                childContext,
                decoratorContext,
                goalHint
        );
        try {
            MergeDescriptor refreshed = gitWorktreeService.mergeTrunkToChild(trunkContext, childContext);
            refreshed = refreshed.withCommitMetadata(mergeCommitMetadata(
                    refreshed.commitMetadata(),
                    autoCommitResult.commitMetadata()
            ));
            return applyConflictAgentOutcome(refreshed, conflictResult);
        } catch (Exception e) {
            String message = "Error when finished merging after resolving conflicts with LLM: " + e.getMessage();
            log.error(message, e);
            publishNodeError(decoratorContext, sourceResult, null, message);
            return descriptor.toBuilder()
                    .successful(false)
                    .errorType(MergeErrorType.MERGE_EXECUTION_FAILED)
                    .errorMessage(message)
                    .build();
        }
    }

    public MergeAggregation mergeChildResultsToTrunkWithAutoCommit(
            List<? extends AgentModels.AgentResult> childResults,
            WorktreeSandboxContext trunkContext,
            DecoratorContext decoratorContext,
            String goalHint
    ) {
        if (childResults == null || childResults.isEmpty()) {
            return MergeAggregation.builder()
                    .merged(List.of())
                    .pending(List.of())
                    .conflicted(null)
                    .build();
        }

        List<AgentMergeStatus> merged = new ArrayList<>();
        List<AgentMergeStatus> pending = new ArrayList<>();
        AgentMergeStatus conflicted = null;
        Map<String, AgentModels.AgentResult> resultsById = new HashMap<>();

        for (AgentModels.AgentResult result : childResults) {
            AgentMergeStatus status = createPendingStatus(result);
            pending.add(status);
            resultsById.put(status.agentResultId(), result);
        }

        Iterator<AgentMergeStatus> pendingIterator = pending.iterator();
        while (pendingIterator.hasNext()) {
            AgentMergeStatus status = pendingIterator.next();
            WorktreeSandboxContext childContext = status.worktreeContext();

            if (childContext == null) {
                pendingIterator.remove();
                merged.add(status.withMergeDescriptor(MergeDescriptor.noOp(MergeDirection.CHILD_TO_TRUNK)));
                continue;
            }

            AgentModels.AgentResult sourceResult = resultsById.get(status.agentResultId());
            AgentModels.CommitAgentResult autoCommitResult = worktreeAutoCommitService.autoCommitDirtyWorktrees(
                    sourceResult,
                    childContext,
                    decoratorContext,
                    goalHint
            );
            if (!autoCommitResult.successful()) {
                pendingIterator.remove();
                String message = "Auto-commit failed before child->trunk merge: " + autoCommitResult.errorMessage();
                publishNodeError(decoratorContext, sourceResult, null, message);
                conflicted = status.withMergeDescriptor(
                        MergeDescriptor.conflict(
                                MergeDirection.CHILD_TO_TRUNK,
                                List.of(),
                                null,
                                List.of(),
                                message,
                                MergeErrorType.AUTO_COMMIT_FAILED
                        ).withCommitMetadata(autoCommitResult.commitMetadata())
                );
                break;
            }
            try {
                MergeDescriptor mergedDescriptor = gitWorktreeService.mergeChildToTrunk(childContext, trunkContext);
                mergedDescriptor = mergedDescriptor.withCommitMetadata(mergeCommitMetadata(
                        mergedDescriptor.commitMetadata(),
                        autoCommitResult.commitMetadata()
                ));
                AgentModels.MergeConflictResult conflictResult = worktreeMergeConflictService.runForResult(
                        sourceResult,
                        mergedDescriptor,
                        trunkContext,
                        childContext,
                        decoratorContext,
                        goalHint
                );
                MergeDescriptor refreshedDescriptor = gitWorktreeService.mergeChildToTrunk(childContext, trunkContext);
                refreshedDescriptor = refreshedDescriptor.withCommitMetadata(mergeCommitMetadata(
                        refreshedDescriptor.commitMetadata(),
                        autoCommitResult.commitMetadata()
                ));
                MergeDescriptor resolvedDescriptor = applyConflictAgentOutcome(refreshedDescriptor, conflictResult);
                pendingIterator.remove();
                AgentMergeStatus resolvedStatus = status.withMergeDescriptor(resolvedDescriptor);
                if (!resolvedDescriptor.successful()) {
                    publishNodeError(
                            decoratorContext,
                            sourceResult,
                            null,
                            "Merge failed for child result " + status.agentResultId() + ": " + resolvedDescriptor.errorMessage()
                    );
                    conflicted = resolvedStatus;
                    break;
                }
                merged.add(resolvedStatus);
            } catch (Exception e) {
                pendingIterator.remove();
                String message = "Merge execution failed for child result " + status.agentResultId() + ": " + e.getMessage();
                publishNodeError(decoratorContext, sourceResult, null, message);
                conflicted = status.withMergeDescriptor(MergeDescriptor.conflict(
                        MergeDirection.CHILD_TO_TRUNK,
                        List.of(),
                        null,
                        List.of(),
                        message,
                        MergeErrorType.MERGE_EXECUTION_FAILED
                ).withCommitMetadata(autoCommitResult.commitMetadata()));
                break;
            }
        }

        MergeAggregation aggregation = MergeAggregation.builder()
                .merged(merged)
                .pending(new ArrayList<>(pending))
                .conflicted(conflicted)
                .build();
        return aggregation;
    }

    public MergeDescriptor finalMergeToSourceWithAutoCommit(
            AgentModels.AgentRequest sourceRequest,
            WorktreeSandboxContext trunkContext,
            WorktreeSandboxContext mergeContext,
            DecoratorContext decoratorContext,
            String goalHint,
            String mainWorktreeId
    ) {
        AgentModels.CommitAgentResult autoCommitResult = worktreeAutoCommitService.autoCommitDirtyWorktreesForRequest(
                sourceRequest,
                mergeContext,
                decoratorContext,
                goalHint
        );
        if (!autoCommitResult.successful()) {
            String message = "Final merge to source blocked: auto-commit failed: " + autoCommitResult.errorMessage();
            publishNodeError(decoratorContext, null, sourceRequest, message);
        }

        MergeDescriptor descriptor = gitWorktreeService.finalMergeToSourceDescriptor(mainWorktreeId);

        descriptor = descriptor.withCommitMetadata(mergeCommitMetadata(
                descriptor.commitMetadata(),
                autoCommitResult.commitMetadata()
        ));

        AgentModels.MergeConflictResult conflictResult = worktreeMergeConflictService.runForRequest(
                sourceRequest,
                descriptor,
                trunkContext,
                mergeContext,
                decoratorContext,
                goalHint
        );
        try {
            MergeDescriptor refreshed = gitWorktreeService.finalMergeToSourceDescriptor(mainWorktreeId);
            refreshed = refreshed.withCommitMetadata(mergeCommitMetadata(
                    refreshed.commitMetadata(),
                    autoCommitResult.commitMetadata()
            ));
            MergeDescriptor resolved = applyConflictAgentOutcome(refreshed, conflictResult);
            if (!resolved.successful()) {
                publishNodeError(decoratorContext, null, sourceRequest, "Final merge to source failed: " + resolved.errorMessage());
            }
            return resolved;
        } catch (Exception e) {
            String message = "Final merge redetection failed after conflict resolution: " + e.getMessage();
            publishNodeError(decoratorContext, null, sourceRequest, message);
            return descriptor.toBuilder()
                    .successful(false)
                    .errorType(MergeErrorType.MERGE_EXECUTION_FAILED)
                    .errorMessage(message)
                    .build();
        }
    }

    public MergeAggregation runFinalAggregationConflictPass(
            AgentModels.ResultsRequest resultsRequest,
            MergeAggregation aggregation,
            WorktreeSandboxContext trunkContext,
            DecoratorContext decoratorContext,
            String goalHint
    ) {
        if (resultsRequest == null || aggregation == null || trunkContext == null) {
            return aggregation;
        }
        AgentModels.MergeConflictResult result = worktreeMergeConflictService.runForResultsAggregation(
                resultsRequest,
                aggregation,
                decoratorContext,
                goalHint
        );
        if (result != null && !result.successful()) {
            publishNodeError(decoratorContext, null, resultsRequest, "Final merge aggregation conflict pass failed: " + result.errorMessage());
        }
        return redetectAggregation(aggregation, trunkContext);
    }

    private AgentMergeStatus createPendingStatus(AgentModels.AgentResult result) {
        String agentResultId = (result.contextId() != null && result.contextId().value() != null)
                ? result.contextId().value()
                : "unknown";
        WorktreeSandboxContext worktreeContext = result != null ? result.worktreeContext() : null;
        if (worktreeContext == null) {
            worktreeContext = resolveChildWorktreeFromMergeDescriptor(result);
        }

        return AgentMergeStatus.builder()
                .agentResultId(agentResultId)
                .worktreeContext(worktreeContext)
                .mergeDescriptor(null)
                .build();
    }

    private WorktreeSandboxContext resolveChildWorktreeFromMergeDescriptor(AgentModels.AgentResult result) {
        MergeDescriptor descriptor = switch (result) {
            case AgentModels.TicketAgentResult r -> r.mergeDescriptor();
            case AgentModels.PlanningAgentResult r -> r.mergeDescriptor();
            case AgentModels.DiscoveryAgentResult r -> r.mergeDescriptor();
            default -> null;
        };

        if (descriptor == null) {
            return null;
        }

        String childMainWorktreeId = resolveChildWorktreeId(descriptor, descriptor.mainWorktreeMergeResult());
        if (childMainWorktreeId == null || childMainWorktreeId.isBlank()) {
            return null;
        }

        return gitWorktreeService.getMainWorktree(childMainWorktreeId)
                .map(main -> new WorktreeSandboxContext(main, resolveChildSubmoduleWorktrees(descriptor)))
                .orElse(null);
    }

    private List<SubmoduleWorktreeContext> resolveChildSubmoduleWorktrees(MergeDescriptor descriptor) {
        if (descriptor == null || descriptor.submoduleMergeResults() == null) {
            return List.of();
        }
        List<SubmoduleWorktreeContext> submodules = new ArrayList<>();
        for (SubmoduleMergeResult submoduleResult : descriptor.submoduleMergeResults()) {
            if (submoduleResult == null || submoduleResult.mergeResult() == null) {
                continue;
            }
            String childSubmoduleId = resolveChildWorktreeId(descriptor, submoduleResult.mergeResult());
            if (childSubmoduleId == null || childSubmoduleId.isBlank()) {
                continue;
            }
            gitWorktreeService.getSubmoduleWorktree(childSubmoduleId).ifPresent(submodules::add);
        }
        return submodules;
    }

    private String resolveChildWorktreeId(MergeDescriptor descriptor, MergeResult mergeResult) {
        if (descriptor == null || mergeResult == null) {
            return null;
        }
        return descriptor.mergeDirection() == MergeDirection.TRUNK_TO_CHILD
                ? mergeResult.parentWorktreeId()
                : mergeResult.childWorktreeId();
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

    private MergeAggregation redetectAggregation(MergeAggregation aggregation, WorktreeSandboxContext trunkContext) {
        List<AgentMergeStatus> refreshedMerged = refreshStatuses(aggregation.merged(), trunkContext);
        List<AgentMergeStatus> refreshedPending = aggregation.pending() == null
                ? List.of()
                : new ArrayList<>(aggregation.pending());
        AgentMergeStatus refreshedConflicted = refreshStatus(aggregation.conflicted(), trunkContext);
        return MergeAggregation.builder()
                .merged(refreshedMerged)
                .pending(refreshedPending)
                .conflicted(refreshedConflicted)
                .build();
    }

    private List<AgentMergeStatus> refreshStatuses(List<AgentMergeStatus> statuses, WorktreeSandboxContext trunkContext) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        List<AgentMergeStatus> refreshed = new ArrayList<>();
        for (AgentMergeStatus status : statuses) {
            refreshed.add(refreshStatus(status, trunkContext));
        }
        return refreshed;
    }

    private AgentMergeStatus refreshStatus(AgentMergeStatus status, WorktreeSandboxContext trunkContext) {
        if (status == null || status.worktreeContext() == null || trunkContext == null) {
            return status;
        }
        MergeDescriptor refreshedDescriptor = gitWorktreeService.mergeChildToTrunk(status.worktreeContext(), trunkContext);
        if (status.mergeDescriptor() != null) {
            refreshedDescriptor = refreshedDescriptor.withCommitMetadata(mergeCommitMetadata(
                    refreshedDescriptor.commitMetadata(),
                    status.mergeDescriptor().commitMetadata()
            ));
        }
        return status.withMergeDescriptor(refreshedDescriptor);
    }

    private MergeDescriptor applyConflictAgentOutcome(
            MergeDescriptor descriptor,
            AgentModels.MergeConflictResult conflictResult
    ) {
        if (descriptor == null || conflictResult == null || conflictResult.successful()) {
            return descriptor;
        }
        String message = conflictResult.errorMessage() != null && !conflictResult.errorMessage().isBlank()
                ? conflictResult.errorMessage()
                : "merge conflict agent reported failure";
        String existingError = descriptor.errorMessage();
        String combinedError = (existingError == null || existingError.isBlank())
                ? message
                : existingError + " | " + message;
        return descriptor.toBuilder()
                .successful(false)
                .errorType(MergeErrorType.CONFLICT_AGENT_FAILED)
                .errorMessage(combinedError)
                .build();
    }

    private void publishNodeError(
            DecoratorContext decoratorContext,
            AgentModels.AgentResult sourceResult,
            AgentModels.AgentRequest sourceRequest,
            String reason
    ) {
        log.error(reason);
        if (eventBus == null) {
            return;
        }
        eventBus.publish(Events.NodeErrorEvent.err(reason, resolveArtifactKey(decoratorContext, sourceResult, sourceRequest)));
    }

    private ArtifactKey resolveArtifactKey(
            DecoratorContext decoratorContext,
            AgentModels.AgentResult sourceResult,
            AgentModels.AgentRequest sourceRequest
    ) {
        if (sourceRequest != null && sourceRequest.contextId() != null) {
            return sourceRequest.contextId();
        }
        if (sourceResult != null && sourceResult.contextId() != null) {
            return sourceResult.contextId();
        }
        if (decoratorContext != null) {
            try {
                return new ArtifactKey(decoratorContext.operationContext().getAgentProcess().getId());
            } catch (IllegalArgumentException ignored) {
                log.error("Could not find artifact key!");
                // Fall through to root key when process id is not a valid ArtifactKey value.
            }
        }
        return ArtifactKey.createRoot();
    }
}
