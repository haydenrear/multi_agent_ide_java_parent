package com.hayden.multiagentide.agent.decorator.request;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentide.service.WorktreeAutoCommitService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.MergeResult;
import com.hayden.multiagentidelib.model.merge.AgentMergeStatus;
import com.hayden.multiagentidelib.model.merge.MergeAggregation;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.merge.MergeDirection;
import com.hayden.multiagentidelib.model.merge.SubmoduleMergeResult;
import com.hayden.multiagentidelib.model.merge.WorktreeCommitMetadata;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Decorator that performs child → trunk merge when aggregating dispatch agent results.
 * 
 * Phase 2 of the worktree merge flow:
 * - After collecting child agent results (Ticket/Planning/Discovery)
 * - Iterate through each child's worktree
 * - Merge child → trunk using centralized service method
 * - Stop on first conflict, populate MergeAggregation
 * 
 * The routing LLM will receive the MergeAggregation via a PromptContributor
 * and decide how to handle any conflicts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorktreeMergeResultsDecorator implements ResultsRequestDecorator {

    private final GitWorktreeService gitWorktreeService;
    private final WorktreeAutoCommitService worktreeAutoCommitService;
    private final EventBus eventBus;

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public <T extends AgentModels.ResultsRequest> T decorate(T resultsRequest, DecoratorContext context) {
        if (resultsRequest == null) {
            return resultsRequest;
        }
        String nodeId = resolveNodeId(resultsRequest);

        WorktreeSandboxContext trunkContext = resolveTrunkWorktreeContext(context);
        if (trunkContext == null) {
            String reason = "Skipping child->trunk merge: no trunk worktree context";
            publishNodeError(nodeId, reason);
            publishIfAvailable(new Events.MergePhaseCompletedEvent(
                    UUID.randomUUID().toString(), Instant.now(), nodeId,
                    "CHILD_TO_TRUNK", false,
                    0,
                    0,
                    List.of(),
                    reason));
            return resultsRequest;
        }

        List<? extends AgentModels.AgentResult> childResults = resultsRequest.childResults();
        if (childResults == null || childResults.isEmpty()) {
            log.debug("Skipping child→trunk merge: no child results");
            return resultsRequest;
        }

        String trunkId = trunkContext.mainWorktree() != null ? trunkContext.mainWorktree().worktreeId() : "unknown";

        publishIfAvailable(new Events.MergePhaseStartedEvent(
                UUID.randomUUID().toString(), Instant.now(), nodeId,
                "CHILD_TO_TRUNK", trunkId, null, childResults.size()));

        MergeAggregation aggregation = performChildToTrunkMerges(childResults, trunkContext, nodeId, context);

        int conflictCount = aggregation.conflicted() != null ? 1 : 0;
        List<String> conflictFiles = aggregation.conflicted() != null && aggregation.conflicted().mergeDescriptor() != null
                ? aggregation.conflicted().mergeDescriptor().conflictFiles()
                : List.of();
        String conflictError = aggregation.conflicted() != null && aggregation.conflicted().mergeDescriptor() != null
                ? aggregation.conflicted().mergeDescriptor().errorMessage()
                : null;

        publishIfAvailable(new Events.MergePhaseCompletedEvent(
                UUID.randomUUID().toString(), Instant.now(), nodeId,
                "CHILD_TO_TRUNK", conflictCount == 0,
                aggregation.merged() != null ? aggregation.merged().size() : 0,
                conflictCount,
                conflictFiles != null ? conflictFiles : List.of(),
                conflictError));

        T decorated = resultsRequest.withMergeAggregation(aggregation);
        return decorated;
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

    private String resolveNodeId(AgentModels.ResultsRequest resultsRequest) {
        if (resultsRequest.contextId() != null && resultsRequest.contextId().value() != null) {
            return resultsRequest.contextId().value();
        }
        return "unknown";
    }

    private MergeAggregation performChildToTrunkMerges(
            List<? extends AgentModels.AgentResult> childResults,
            WorktreeSandboxContext trunkContext,
            String nodeId,
            DecoratorContext decoratorContext) {

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
                publishNodeError(nodeId, "Child " + status.agentResultId() + " has no worktree context, marking as no-op merge");
                continue;
            }

            AgentModels.AgentResult sourceResult = resultsById.get(status.agentResultId());
            AgentModels.CommitAgentResult autoCommitResult = worktreeAutoCommitService.autoCommitDirtyWorktrees(
                    sourceResult,
                    childContext,
                    decoratorContext,
                    extractGoalHint(decoratorContext)
            );
            if (!autoCommitResult.successful()) {
                pendingIterator.remove();
                conflicted = status.withMergeDescriptor(
                        MergeDescriptor.conflict(
                                MergeDirection.CHILD_TO_TRUNK,
                                List.of(),
                                null,
                                List.of(),
                                "Auto-commit failed before child->trunk merge: " + autoCommitResult.errorMessage()
                        ).withCommitMetadata(autoCommitResult.commitMetadata())
                );
                publishNodeError(nodeId, "Auto-commit failed for child " + status.agentResultId() + ": " + autoCommitResult.errorMessage());
                break;
            }

            MergeDescriptor descriptor = gitWorktreeService.mergeChildToTrunk(childContext, trunkContext);
            descriptor = descriptor.withCommitMetadata(mergeCommitMetadata(descriptor.commitMetadata(), autoCommitResult.commitMetadata()));
            AgentMergeStatus updatedStatus = status.withMergeDescriptor(descriptor);

            if (!descriptor.successful()) {
                pendingIterator.remove();
                conflicted = updatedStatus;
                publishNodeError(nodeId, "Merge conflict detected for child " + status.agentResultId() + ", stopping merge iteration");
                break;
            }

            pendingIterator.remove();
            merged.add(updatedStatus);
            log.debug("Successfully merged child {} to trunk", status.agentResultId());
        }

        return MergeAggregation.builder()
                .merged(merged)
                .pending(new ArrayList<>(pending))
                .conflicted(conflicted)
                .build();
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

    private AgentMergeStatus createPendingStatus(AgentModels.AgentResult result) {
        String agentResultId = (result.contextId() != null && result.contextId().value() != null) ? result.contextId().value() : "unknown";
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
                .map(main -> new WorktreeSandboxContext(
                        main,
                        resolveChildSubmoduleWorktrees(descriptor)
                ))
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
            gitWorktreeService.getSubmoduleWorktree(childSubmoduleId)
                    .ifPresent(submodules::add);
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

    private WorktreeSandboxContext resolveTrunkWorktreeContext(DecoratorContext context) {
        if (context.agentRequest() instanceof AgentModels.ResultsRequest resultsRequest) {
            return resultsRequest.worktreeContext();
        }
        if (context.lastRequest() instanceof AgentModels.AgentRequest lastRequest) {
            return lastRequest.worktreeContext();
        }
        return null;
    }
}
