package com.hayden.multiagentide.agent.decorator.request;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.service.GitMergeService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.merge.MergeAggregation;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
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

    private final GitMergeService gitMergeService;
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

        MergeAggregation aggregation = gitMergeService.mergeChildResultsToTrunkWithAutoCommit(
                childResults,
                trunkContext,
                context,
                extractGoalHint(context)
        );

        aggregation = gitMergeService.runFinalAggregationConflictPass(
                resultsRequest,
                aggregation,
                trunkContext,
                context,
                extractGoalHint(context)
        );

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

    private String extractGoalHint(DecoratorContext context) {
        if (context == null) {
            return "";
        }
        if (context.agentRequest() instanceof AgentModels.AgentRequest request) {
            return request.goalExtraction();
        }
        return "";
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
