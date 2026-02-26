package com.hayden.multiagentide.service;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.decorator.prompt.PromptContextDecorator;
import com.hayden.multiagentide.agent.decorator.prompt.ToolContextDecorator;
import com.hayden.multiagentide.agent.decorator.request.RequestDecorator;
import com.hayden.multiagentide.agent.decorator.result.ResultDecorator;
import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.model.merge.AgentMergeStatus;
import com.hayden.multiagentidelib.model.merge.MergeAggregation;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorktreeMergeConflictService {

    private static final String TEMPLATE = "workflow/worktree_merge_conflict_agent";
    private static final String AGENT_NAME = "worktree-merge-conflict";
    private static final String ACTION_NAME = "merge-conflict-agent";
    private static final String METHOD_NAME = "runMergeConflictAgent";

    private final LlmRunner llmRunner;
    @Autowired
    @Lazy
    private EventBus eventBus;

    @Autowired
    @Lazy
    private List<PromptContextDecorator> promptContextDecorators;

    @Autowired
    @Lazy
    private List<ToolContextDecorator> toolContextDecorators;

    @Autowired @Lazy
    private List<ResultDecorator> resultDecorators;
    @Autowired @Lazy
    private List<RequestDecorator> requestDecorators;

    public AgentModels.MergeConflictResult runForResult(
            AgentModels.AgentResult sourceResult,
            MergeDescriptor descriptor,
            WorktreeSandboxContext trunkContext,
            WorktreeSandboxContext worktreeContext,
            DecoratorContext decoratorContext,
            String goalHint
    ) {
        AgentModels.AgentRequest sourceRequest = resolveSourceRequest(decoratorContext);
        return run(sourceRequest, sourceResult, descriptor, trunkContext, worktreeContext, decoratorContext, goalHint, null);
    }

    public AgentModels.MergeConflictResult runForRequest(
            AgentModels.AgentRequest sourceRequest,
            MergeDescriptor descriptor,
            WorktreeSandboxContext trunkContext,
            WorktreeSandboxContext worktreeContext,
            DecoratorContext decoratorContext,
            String goalHint
    ) {
        return run(sourceRequest, null, descriptor, trunkContext, worktreeContext, decoratorContext, goalHint, null);
    }

    public AgentModels.MergeConflictResult runForResultsAggregation(
            AgentModels.ResultsRequest resultsRequest,
            MergeAggregation aggregation,
            DecoratorContext decoratorContext,
            String goalHint
    ) {
        MergeDescriptor descriptor = toAggregationDescriptor(aggregation);
        WorktreeSandboxContext worktreeContext = resultsRequest != null ? resultsRequest.worktreeContext() : null;
        return run(resultsRequest, null, descriptor, worktreeContext, worktreeContext, decoratorContext, goalHint, resultsRequest);
    }

    private AgentModels.MergeConflictResult run(
            AgentModels.AgentRequest sourceRequest,
            AgentModels.AgentResult sourceResult,
            MergeDescriptor descriptor,
            WorktreeSandboxContext trunkContext,
            WorktreeSandboxContext worktreeContext,
            DecoratorContext decoratorContext,
            String goalHint,
            AgentModels.ResultsRequest parentResultsRequest
    ) {
        if (descriptor == null || worktreeContext == null || worktreeContext.mainWorktree() == null) {
            return AgentModels.MergeConflictResult.builder()
                    .successful(true)
                    .output("No merge conflict work required.")
                    .resolvedConflictFiles(List.of())
                    .notes(List.of())
                    .build();
        }
        OperationContext operationContext = decoratorContext != null ? decoratorContext.operationContext() : null;
        if (operationContext == null) {
            return AgentModels.MergeConflictResult.builder()
                    .successful(true)
                    .output("Merge conflict agent skipped.")
                    .errorMessage(null)
                    .resolvedConflictFiles(List.of())
                    .notes(List.of())
                    .build();
        }

        AgentModels.AgentRequest routedFromRequest = parentResultsRequest != null ? parentResultsRequest : sourceRequest;
        ArtifactKey contextId = resolveContextId(routedFromRequest, sourceResult);
        AgentModels.MergeConflictRequest request = AgentModels.MergeConflictRequest.builder()
                .contextId(contextId)
                .worktreeContext(worktreeContext)
                .routedFromRequest(routedFromRequest)
                .goal(goalHint)
                .sourceAgentType(resolveSourceAgentType(sourceResult, sourceRequest))
                .sourceRequestType(routedFromRequest != null ? routedFromRequest.getClass().getSimpleName() : null)
                .mergeDirection(descriptor.mergeDirection() != null ? descriptor.mergeDirection().name() : "UNKNOWN")
                .conflictFiles(descriptor.conflictFiles() != null ? descriptor.conflictFiles() : List.of())
                .mergeError(descriptor.errorMessage())
                .build();

        Map<String, Object> model = new HashMap<>();
        model.put("sourceWorktree", safeWorktreePath(worktreeContext));
        model.put("targetWorktree", safeWorktreePath(trunkContext));
        model.put("targetBranch", safeTargetDerivedBranch(trunkContext));
        model.put("metadataTrailer", metadataTrailer(
                request.sourceAgentType(),
                trunkContext != null && trunkContext.mainWorktree() != null ? trunkContext.mainWorktree().worktreeId() : null
        ));

        request = decorateCommitRequest(
                request,
                decoratorContext,
                sourceRequest,
                routedFromRequest
        );

        PromptContext promptContext = new PromptContext(
                request.sourceAgentType() != null ? request.sourceAgentType() : AgentType.MERGE_CONFLICT_AGENT,
                request.contextId(),
                List.of(),
                null,
                null,
                sourceRequest,
                request,
                Map.of(),
                TEMPLATE,
                model,
                "DEFAULT",
                operationContext
        );

        PromptContext decoratedPromptContext = AgentInterfaces.decoratePromptContext(
                promptContext,
                operationContext,
                promptContextDecorators,
                AGENT_NAME,
                ACTION_NAME,
                METHOD_NAME,
                sourceRequest,
                request
        );

        ToolContext toolContext = AgentInterfaces.decorateToolContext(
                ToolContext.empty(),
                request,
                sourceRequest,
                operationContext,
                toolContextDecorators,
                AGENT_NAME,
                ACTION_NAME,
                METHOD_NAME
        );

        try {
            AgentModels.MergeConflictResult conflictResult = llmRunner.runWithTemplate(
                    TEMPLATE,
                    decoratedPromptContext,
                    model,
                    toolContext,
                    AgentModels.MergeConflictResult.class,
                    operationContext
            );
            if (conflictResult == null) {
                String reason = "Merge conflict agent returned empty result.";
                publishNodeError(reason, request.contextId());
                return AgentModels.MergeConflictResult.builder()
                        .contextId(request.contextId())
                        .successful(false)
                        .output(reason)
                        .errorMessage("No response from model.")
                        .resolvedConflictFiles(List.of())
                        .notes(List.of())
                        .worktreeContext(worktreeContext)
                        .build();
            }

            return AgentInterfaces.decorateResult(
                    conflictResult,
                    operationContext,
                    resultDecorators,
                    AGENT_NAME,
                    ACTION_NAME,
                    METHOD_NAME,
                    request
            );
        } catch (Exception e) {
            String reason = "Merge conflict agent execution failed: " + e.getMessage();
            log.error(reason, e);
            publishNodeError(reason, request.contextId());
            return AgentModels.MergeConflictResult.builder()
                    .contextId(request.contextId())
                    .successful(false)
                    .output("Merge conflict agent execution failed.")
                    .errorMessage(e.getMessage())
                    .resolvedConflictFiles(List.of())
                    .notes(List.of())
                    .worktreeContext(worktreeContext)
                    .build();
        }
    }

    private AgentModels.MergeConflictRequest decorateCommitRequest(
            AgentModels.MergeConflictRequest request,
            DecoratorContext decoratorContext,
            AgentModels.AgentRequest sourceRequest,
            AgentModels.AgentRequest lastRequest
    ) {
        if (decoratorContext == null || decoratorContext.operationContext() == null) {
            return request;
        }

        AgentModels.AgentRequest parentRequest = sourceRequest != null ? sourceRequest : lastRequest;
        AgentModels.MergeConflictRequest decorated = AgentInterfaces.decorateRequest(
                request,
                decoratorContext.operationContext(),
                requestDecorators,
                AGENT_NAME,
                ACTION_NAME,
                METHOD_NAME,
                parentRequest
        );
        return decorated;
    }

    private AgentModels.AgentRequest resolveSourceRequest(DecoratorContext decoratorContext) {
        if (decoratorContext == null) {
            return null;
        }
        if (decoratorContext.agentRequest() instanceof AgentModels.AgentRequest request
                && !(request instanceof AgentModels.CommitAgentRequest)
                && !(request instanceof AgentModels.MergeConflictRequest)) {
            return request;
        }
        if (decoratorContext.lastRequest() instanceof AgentModels.AgentRequest request
                && !(request instanceof AgentModels.CommitAgentRequest)
                && !(request instanceof AgentModels.MergeConflictRequest)) {
            return request;
        }
        return null;
    }

    private AgentType resolveSourceAgentType(AgentModels.AgentResult result, AgentModels.AgentRequest sourceRequest) {
        if (sourceRequest != null) {
            return switch (sourceRequest) {
                case AgentModels.TicketAgentRequest ignored -> AgentType.TICKET_AGENT;
                case AgentModels.PlanningAgentRequest ignored -> AgentType.PLANNING_AGENT;
                case AgentModels.DiscoveryAgentRequest ignored -> AgentType.DISCOVERY_AGENT;
                case AgentModels.OrchestratorCollectorRequest ignored -> AgentType.ORCHESTRATOR_COLLECTOR;
                default -> AgentType.ALL;
            };
        }
        return switch (result) {
            case AgentModels.TicketAgentResult ignored -> AgentType.TICKET_AGENT;
            case AgentModels.PlanningAgentResult ignored -> AgentType.PLANNING_AGENT;
            case AgentModels.DiscoveryAgentResult ignored -> AgentType.DISCOVERY_AGENT;
            default -> AgentType.ALL;
        };
    }

    private MergeDescriptor toAggregationDescriptor(MergeAggregation aggregation) {
        if (aggregation == null) {
            return MergeDescriptor.noOp(com.hayden.multiagentidelib.model.merge.MergeDirection.CHILD_TO_TRUNK);
        }
        if (aggregation.conflicted() != null && aggregation.conflicted().mergeDescriptor() != null) {
            return aggregation.conflicted().mergeDescriptor();
        }
        List<String> conflictFiles = new ArrayList<>();
        List<AgentMergeStatus> merged = aggregation.merged() != null ? aggregation.merged() : List.of();
        for (AgentMergeStatus status : merged) {
            if (status == null || status.mergeDescriptor() == null || status.mergeDescriptor().conflictFiles() == null) {
                continue;
            }
            conflictFiles.addAll(status.mergeDescriptor().conflictFiles());
        }
        return MergeDescriptor.builder()
                .mergeDirection(com.hayden.multiagentidelib.model.merge.MergeDirection.CHILD_TO_TRUNK)
                .successful(true)
                .conflictFiles(conflictFiles)
                .build();
    }

    private com.hayden.acp_cdc_ai.acp.events.ArtifactKey resolveContextId(
            AgentModels.AgentRequest sourceRequest,
            AgentModels.AgentResult sourceResult
    ) {
        if (sourceRequest != null && sourceRequest.contextId() != null) {
            return sourceRequest.contextId().createChild();
        }
        if (sourceResult != null && sourceResult.contextId() != null) {
            return sourceResult.contextId().createChild();
        }
        return com.hayden.acp_cdc_ai.acp.events.ArtifactKey.createRoot().createChild();
    }

    private void publishNodeError(String reason, ArtifactKey key) {
        if (eventBus == null) {
            return;
        }
        ArtifactKey safeKey = key != null ? key : ArtifactKey.createRoot();
        eventBus.publish(Events.NodeErrorEvent.err(reason, safeKey));
    }

    private String safeWorktreePath(WorktreeSandboxContext context) {
        if (context == null || context.mainWorktree() == null || context.mainWorktree().worktreePath() == null) {
            return "";
        }
        return context.mainWorktree().worktreePath().toAbsolutePath().normalize().toString();
    }

    private String safeTargetDerivedBranch(WorktreeSandboxContext trunkContext) {
        if (trunkContext == null || trunkContext.mainWorktree() == null) {
            return "";
        }
        String branch = trunkContext.mainWorktree().derivedBranch();
        return branch != null ? branch : "";
    }

    private String metadataTrailer(AgentType sourceAgentType, String worktreeId) {
        return "Agent-Merge-Trace: " + mergeTrace(sourceAgentType, worktreeId) + "\n"
                + "Agent-Type: " + (sourceAgentType != null ? sourceAgentType.wireValue() : AgentType.ALL.wireValue()) + "\n"
                + "Worktree-Id: " + (worktreeId != null && !worktreeId.isBlank() ? worktreeId : "unknown");
    }

    private String mergeTrace(AgentType sourceAgentType, String worktreeId) {
        String prefix = sourceAgentType != null && sourceAgentType != AgentType.ALL
                ? sourceAgentType.wireValue()
                : "agent";
        return prefix + "-" + shortFragment(worktreeId, 6);
    }

    private String shortFragment(String value, int max) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String compact = value.replaceAll("[^a-zA-Z0-9]", "");
        if (compact.isBlank()) {
            compact = value;
        }
        return compact.length() <= max ? compact : compact.substring(0, max);
    }
}
