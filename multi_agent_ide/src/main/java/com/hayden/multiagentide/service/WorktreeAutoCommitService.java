package com.hayden.multiagentide.service;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.decorator.prompt.PromptContextDecorator;
import com.hayden.multiagentide.agent.decorator.prompt.ToolContextDecorator;
import com.hayden.multiagentide.agent.decorator.result.FinalResultDecorator;
import com.hayden.multiagentide.agent.decorator.request.RequestDecorator;
import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.model.merge.WorktreeCommitMetadata;
import com.hayden.multiagentidelib.model.worktree.WorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes commit actions in-agent before merge decorators run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorktreeAutoCommitService {

    private static final String TEMPLATE_WORKTREE_COMMIT_AGENT = "workflow/worktree_commit_agent";
    private static final String AGENT_NAME = "worktree-auto-commit";
    private static final String ACTION_NAME = "commit-agent";
    private static final String METHOD_NAME = "runCommitAgent";
    private static final String CLEAN_REQUIREMENT = "Before returning, git status must be clean for the target worktree (no staged, unstaged, untracked, or conflicted files).";

    private final GitWorktreeService gitWorktreeService;
    private final LlmRunner llmRunner;

    @Autowired @Lazy
    private EventBus eventBus;
    @Autowired @Lazy
    private List<RequestDecorator> requestDecorators;
    @Autowired @Lazy
    private List<PromptContextDecorator> promptContextDecorators;
    @Autowired @Lazy
    private List<ToolContextDecorator> toolContextDecorators;
    @Autowired @Lazy
    private List<FinalResultDecorator> finalResultDecorators;

    public AgentModels.CommitAgentResult autoCommitDirtyWorktrees(
            AgentModels.AgentResult sourceResult,
            WorktreeSandboxContext worktreeContext,
            DecoratorContext decoratorContext,
            String goalHint
    ) {
        return autoCommitDirtyWorktreesInternal(sourceResult, null, worktreeContext, decoratorContext, goalHint);
    }

    public AgentModels.CommitAgentResult autoCommitDirtyWorktreesForRequest(
            AgentModels.AgentRequest sourceRequest,
            WorktreeSandboxContext worktreeContext,
            DecoratorContext decoratorContext,
            String goalHint
    ) {
        return autoCommitDirtyWorktreesInternal(null, sourceRequest, worktreeContext, decoratorContext, goalHint);
    }

    private AgentModels.CommitAgentResult autoCommitDirtyWorktreesInternal(
            AgentModels.AgentResult sourceResult,
            AgentModels.AgentRequest explicitSourceRequest,
            WorktreeSandboxContext worktreeContext,
            DecoratorContext decoratorContext,
            String goalHint
    ) {
        if (worktreeContext == null || worktreeContext.mainWorktree() == null) {
            return commitSuccess(sourceResult, explicitSourceRequest, worktreeContext, List.of(), List.of(), "No worktree context to auto-commit.");
        }

        AgentModels.AgentRequest sourceRequest = explicitSourceRequest != null ? explicitSourceRequest : resolveSourceRequest(decoratorContext);
        AgentType sourceAgentType = resolveSourceAgentType(sourceResult, sourceRequest);
        AgentModels.AgentRequest lastRequest = resolveLastRequest(decoratorContext);
        List<WorktreeCommitMetadata> committed = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        for (WorktreeContext worktree : allWorktrees(worktreeContext)) {
            if (worktree == null || worktree.worktreeId() == null || worktree.worktreeId().isBlank()) {
                continue;
            }

            String worktreeId = worktree.worktreeId();
            try {
                if (!gitWorktreeService.hasUncommittedChanges(worktreeId, worktree.derivedBranch())) {
                    continue;
                }

                List<String> changedFiles = gitWorktreeService.changedFiles(worktreeId);
                AgentModels.CommitAgentRequest request = buildCommitRequest(
                        sourceResult,
                        worktreeContext,
                        goalHint,
                        sourceAgentType,
                        sourceRequest,
                        summarizeSource(sourceResult, sourceRequest)
                );

                AgentModels.CommitAgentRequest decoratedRequest = decorateCommitRequest(
                        request,
                        decoratorContext,
                        sourceRequest,
                        lastRequest
                );

                AgentModels.CommitAgentResult commitResult = executeCommitAgent(
                        decoratedRequest,
                        sourceRequest,
                        lastRequest,
                        decoratorContext,
                        sourceAgentType,
                        sourceResult,
                        worktree,
                        changedFiles
                );

                if (commitResult.commitMetadata() != null && !commitResult.commitMetadata().isEmpty()) {
                    committed.addAll(commitResult.commitMetadata());
                }
                if (commitResult.notes() != null && !commitResult.notes().isEmpty()) {
                    notes.addAll(commitResult.notes());
                }

                if (!commitResult.successful()) {
                    String message = commitResult.errorMessage() != null && !commitResult.errorMessage().isBlank()
                            ? commitResult.errorMessage()
                            : "Commit agent returned unsuccessful response for worktree " + worktreeId;
                    return commitFailure(sourceResult, sourceRequest, worktreeContext, committed, notes, message);
                }

                if (gitWorktreeService.hasUncommittedChanges(worktreeId, worktree.derivedBranch())) {
                    return commitFailure(
                            sourceResult,
                            sourceRequest,
                            worktreeContext,
                            committed,
                            notes,
                            "Commit agent returned while worktree is still dirty for " + worktreeId
                                    + ". " + CLEAN_REQUIREMENT
                    );
                }
            } catch (Exception e) {
                log.error("Commit agent failed for worktree {}", worktreeId, e);
                return commitFailure(
                        sourceResult,
                        sourceRequest,
                        worktreeContext,
                        committed,
                        notes,
                        "Commit agent failed for worktree " + worktreeId + ": " + e.getMessage()
                );
            }
        }

        return commitSuccess(sourceResult, sourceRequest, worktreeContext, committed, notes, "Auto-commit completed.");
    }

    private AgentModels.CommitAgentResult executeCommitAgent(
            AgentModels.CommitAgentRequest request,
            AgentModels.AgentRequest sourceRequest,
            AgentModels.AgentRequest lastRequest,
            DecoratorContext decoratorContext,
            AgentType sourceAgentType,
            AgentModels.AgentResult sourceResult,
            WorktreeContext targetWorktree,
            List<String> changedFiles
    ) {
        OperationContext operationContext = decoratorContext != null ? decoratorContext.operationContext() : null;
        if (operationContext == null) {
            return fallbackCommit(request, sourceResult, targetWorktree, changedFiles, sourceAgentType, "Missing operation context for commit agent call.");
        }

        try {
            Map<String, Object> model = modelFor(request, sourceResult, targetWorktree, changedFiles);
            AgentModels.AgentRequest previousRequest = sourceRequest != null ? sourceRequest : lastRequest;

            PromptContext promptContext = new PromptContext(
                    sourceAgentType,
                    request.contextId(),
                    List.of(),
                    null,
                    BlackboardHistory.getEntireBlackboardHistory(operationContext),
                    previousRequest,
                    request,
                    Map.of(),
                    TEMPLATE_WORKTREE_COMMIT_AGENT,
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
                    previousRequest,
                    request
            );

            ToolContext toolContext = AgentInterfaces.decorateToolContext(
                    ToolContext.empty(),
                    sourceRequest != null ? sourceRequest : request,
                    previousRequest,
                    operationContext,
                    toolContextDecorators,
                    AGENT_NAME,
                    ACTION_NAME,
                    METHOD_NAME
            );

            AgentModels.CommitAgentResult raw = llmRunner.runWithTemplate(
                    TEMPLATE_WORKTREE_COMMIT_AGENT,
                    decoratedPromptContext,
                    model,
                    toolContext,
                    AgentModels.CommitAgentResult.class,
                    operationContext
            );

            AgentModels.CommitAgentResult normalized = normalizeCommitResult(raw, request, sourceResult);

            return AgentInterfaces.decorateFinalResult(
                    normalized,
                    request,
                    previousRequest,
                    operationContext,
                    finalResultDecorators,
                    AGENT_NAME,
                    ACTION_NAME,
                    METHOD_NAME
            );
        } catch (Exception e) {
            log.warn("Commit agent LLM/tool execution failed for worktree {}. Falling back to direct commit.", targetWorktree.worktreeId(), e);
            return fallbackCommit(request, sourceResult, targetWorktree, changedFiles, sourceAgentType, e.getMessage());
        }
    }

    private AgentModels.CommitAgentRequest decorateCommitRequest(
            AgentModels.CommitAgentRequest request,
            DecoratorContext decoratorContext,
            AgentModels.AgentRequest sourceRequest,
            AgentModels.AgentRequest lastRequest
    ) {
        if (decoratorContext == null || decoratorContext.operationContext() == null) {
            return request;
        }

        AgentModels.AgentRequest parentRequest = sourceRequest != null ? sourceRequest : lastRequest;
        AgentModels.CommitAgentRequest decorated = AgentInterfaces.decorateRequest(
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

    private AgentModels.CommitAgentRequest buildCommitRequest(
            AgentModels.AgentResult sourceResult,
            WorktreeSandboxContext worktreeContext,
            String goalHint,
            AgentType sourceAgentType,
            AgentModels.AgentRequest sourceRequest,
            String resultSummary
    ) {
        String goal = goalHint == null || goalHint.isBlank()
                ? "Commit pending worktree changes before merge."
                : goalHint.trim();

        String sourceRequestType = sourceRequest != null
                ? sourceRequest.getClass().getSimpleName()
                : (sourceResult != null ? sourceResult.getClass().getSimpleName() : "UnknownRequest");

        String commitInstructions = "Use your toolset to inspect git status and commit pending changes in this worktree. "
                + "Split into multiple focused commits when appropriate. "
                + "Each commit message must include metadata trailers shown below. "
                + CLEAN_REQUIREMENT;

        return AgentModels.CommitAgentRequest.builder()
                .worktreeContext(worktreeContext)
                .routedFromRequest(sourceRequest)
                .goal(goal)
                .sourceAgentType(sourceAgentType)
                .sourceRequestType(sourceRequestType)
                .commitInstructions(commitInstructions)
                .sourceResultSummary(resultSummary)
                .build();
    }

    private AgentModels.CommitAgentResult fallbackCommit(
            AgentModels.CommitAgentRequest request,
            AgentModels.AgentResult sourceResult,
            WorktreeContext targetWorktree,
            List<String> changedFiles,
            AgentType sourceAgentType,
            String reason
    ) {
        String worktreeId = targetWorktree.worktreeId();
        String message = fallbackCommitMessage(changedFiles)
                + "\n\n"
                + metadataTrailer(sourceAgentType, worktreeId);

        String commitHash = gitWorktreeService.commitChanges(worktreeId, message);
        WorktreeCommitMetadata metadata = WorktreeCommitMetadata.builder()
                .worktreeId(worktreeId)
                .worktreePath(targetWorktree.worktreePath() != null ? targetWorktree.worktreePath().toString() : null)
                .commitHash(commitHash)
                .commitMessage(message.lines().findFirst().orElse(message))
                .summary("Fallback commit after commit-agent execution failure")
                .changedFiles(condenseChangedFiles(changedFiles))
                .committedAt(Instant.now())
                .build();

        return AgentModels.CommitAgentResult.builder()
                .contextId(request != null ? request.contextId() : null)
                .successful(true)
                .output("Fallback commit created for worktree " + worktreeId)
                .errorMessage(null)
                .commitMetadata(List.of(metadata))
                .notes(List.of("Fallback path used: " + reason))
                .worktreeContext(request != null ? request.worktreeContext() : null)
                .build();
    }

    private AgentModels.CommitAgentResult normalizeCommitResult(
            AgentModels.CommitAgentResult raw,
            AgentModels.CommitAgentRequest request,
            AgentModels.AgentResult sourceResult
    ) {
        if (raw == null) {
            return AgentModels.CommitAgentResult.builder()
                    .contextId(request.contextId())
                    .successful(false)
                    .output("Commit agent returned no response.")
                    .errorMessage("Empty commit agent response")
                    .commitMetadata(List.of())
                    .notes(List.of())
                    .worktreeContext(request.worktreeContext())
                    .build();
        }

        return raw.toBuilder()
                .contextId(raw.contextId() != null ? raw.contextId() : request.contextId())
                .worktreeContext(raw.worktreeContext() != null ? raw.worktreeContext() : request.worktreeContext())
                .commitMetadata(sanitizeMetadata(raw.commitMetadata()))
                .notes(raw.notes() != null ? raw.notes() : List.of())
                .output(raw.output() != null && !raw.output().isBlank() ? raw.output() : summarizeResult(sourceResult))
                .build();
    }

    private Map<String, Object> modelFor(
            AgentModels.CommitAgentRequest request,
            AgentModels.AgentResult sourceResult,
            WorktreeContext targetWorktree,
            List<String> changedFiles
    ) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("goal", nullSafe(request.goal()));
        model.put("sourceAgentType", request.sourceAgentType() != null ? request.sourceAgentType().name() : AgentType.ALL.name());
        model.put("sourceRequestType", nullSafe(request.sourceRequestType()));
        model.put("worktreeId", targetWorktree != null ? nullSafe(targetWorktree.worktreeId()) : "");
        model.put("worktreePath", targetWorktree != null && targetWorktree.worktreePath() != null ? targetWorktree.worktreePath().toString() : "");
        model.put("changedFilesCount", changedFiles != null ? changedFiles.size() : 0);
        model.put("commitInstructions", nullSafe(request.commitInstructions()));
        model.put("metadataTrailer", metadataTrailer(request.sourceAgentType(), targetWorktree != null ? targetWorktree.worktreeId() : ""));
        model.put("cleanRequirement", CLEAN_REQUIREMENT);
        model.put("resultSummary", summarizeResult(sourceResult));
        return model;
    }

    private AgentModels.CommitAgentResult commitSuccess(
            AgentModels.AgentResult sourceResult,
            AgentModels.AgentRequest sourceRequest,
            WorktreeSandboxContext worktreeContext,
            List<WorktreeCommitMetadata> metadata,
            List<String> notes,
            String output
    ) {
        return AgentModels.CommitAgentResult.builder()
                .contextId(resolveSourceContextId(sourceResult, sourceRequest))
                .successful(true)
                .output(output)
                .errorMessage(null)
                .commitMetadata(metadata != null ? metadata : List.of())
                .notes(notes != null ? notes : List.of())
                .worktreeContext(worktreeContext)
                .build();
    }

    private AgentModels.CommitAgentResult commitFailure(
            AgentModels.AgentResult sourceResult,
            AgentModels.AgentRequest sourceRequest,
            WorktreeSandboxContext worktreeContext,
            List<WorktreeCommitMetadata> metadata,
            List<String> notes,
            String errorMessage
    ) {
        publishNodeError(sourceResult, sourceRequest, errorMessage);
        return AgentModels.CommitAgentResult.builder()
                .contextId(resolveSourceContextId(sourceResult, sourceRequest))
                .successful(false)
                .output("Auto-commit failed.")
                .errorMessage(errorMessage)
                .commitMetadata(metadata != null ? metadata : List.of())
                .notes(notes != null ? notes : List.of())
                .worktreeContext(worktreeContext)
                .build();
    }

    private ArtifactKey resolveSourceContextId(AgentModels.AgentResult sourceResult, AgentModels.AgentRequest sourceRequest) {
        if (sourceResult != null && sourceResult.contextId() != null) {
            return sourceResult.contextId();
        }
        if (sourceRequest != null && sourceRequest.contextId() != null) {
            return sourceRequest.contextId();
        }
        return null;
    }

    private void publishNodeError(AgentModels.AgentResult sourceResult, AgentModels.AgentRequest sourceRequest, String reason) {
        log.error(reason);
        if (eventBus == null) {
            return;
        }
        ArtifactKey key = resolveSourceContextId(sourceResult, sourceRequest);
        if (key == null) {
            key = ArtifactKey.createRoot();
        }
        eventBus.publish(Events.NodeErrorEvent.err(reason, key));
    }

    private List<WorktreeContext> allWorktrees(WorktreeSandboxContext context) {
        List<WorktreeContext> all = new ArrayList<>();
        all.add(context.mainWorktree());
        if (context.submoduleWorktrees() != null) {
            all.addAll(context.submoduleWorktrees());
        }
        return all;
    }

    private AgentType resolveSourceAgentType(AgentModels.AgentResult result, AgentModels.AgentRequest sourceRequest) {
        if (sourceRequest != null) {
            AgentType fromRequest = switch (sourceRequest) {
                case AgentModels.TicketAgentRequest ignored -> AgentType.TICKET_AGENT;
                case AgentModels.PlanningAgentRequest ignored -> AgentType.PLANNING_AGENT;
                case AgentModels.DiscoveryAgentRequest ignored -> AgentType.DISCOVERY_AGENT;
                default -> null;
            };
            if (fromRequest != null) {
                return fromRequest;
            }
        }
        return switch (result) {
            case AgentModels.TicketAgentResult ignored -> AgentType.TICKET_AGENT;
            case AgentModels.PlanningAgentResult ignored -> AgentType.PLANNING_AGENT;
            case AgentModels.DiscoveryAgentResult ignored -> AgentType.DISCOVERY_AGENT;
            case null, default -> AgentType.ALL;
        };
    }

    private AgentModels.AgentRequest resolveSourceRequest(DecoratorContext decoratorContext) {
        if (decoratorContext == null) {
            return null;
        }
        if (decoratorContext.agentRequest() instanceof AgentModels.AgentRequest request
                && !(request instanceof AgentModels.CommitAgentRequest)) {
            return request;
        }
        if (decoratorContext.lastRequest() instanceof AgentModels.AgentRequest request
                && !(request instanceof AgentModels.CommitAgentRequest)) {
            return request;
        }

        BlackboardHistory history = decoratorContext.operationContext() != null
                ? BlackboardHistory.getEntireBlackboardHistory(decoratorContext.operationContext())
                : null;
        return BlackboardHistory.findLastWorkflowRequest(history);
    }

    private AgentModels.AgentRequest resolveLastRequest(DecoratorContext decoratorContext) {
        if (decoratorContext == null) {
            return null;
        }
        if (decoratorContext.lastRequest() instanceof AgentModels.AgentRequest request
                && !(request instanceof AgentModels.CommitAgentRequest)) {
            return request;
        }

        BlackboardHistory history = decoratorContext.operationContext() != null
                ? BlackboardHistory.getEntireBlackboardHistory(decoratorContext.operationContext())
                : null;
        return BlackboardHistory.findLastWorkflowRequest(history);
    }

    private String summarizeResult(AgentModels.AgentResult result) {
        if (result == null) {
            return "";
        }
        String pretty = result.prettyPrint();
        if (pretty == null || pretty.isBlank()) {
            return "";
        }
        String normalized = pretty.trim().replace('\n', ' ');
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private String summarizeSource(AgentModels.AgentResult sourceResult, AgentModels.AgentRequest sourceRequest) {
        String fromResult = summarizeResult(sourceResult);
        if (!fromResult.isBlank()) {
            return fromResult;
        }
        if (sourceRequest == null) {
            return "";
        }
        String pretty = sourceRequest.prettyPrint();
        if (pretty == null || pretty.isBlank()) {
            return "";
        }
        String normalized = pretty.trim().replace('\n', ' ');
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private String metadataTrailer(AgentType sourceAgentType, String worktreeId) {
        return "Agent-Commit-Trace: " + commitTrace(sourceAgentType, worktreeId) + "\n"
                + "Agent-Type: " + (sourceAgentType != null ? sourceAgentType.wireValue() : AgentType.ALL.wireValue()) + "\n"
                + "Worktree-Id: " + (worktreeId != null && !worktreeId.isBlank() ? worktreeId : "unknown");
    }

    private String commitTrace(AgentType sourceAgentType, String worktreeId) {
        String prefix = sourceAgentType != null && sourceAgentType != AgentType.ALL
                ? sourceAgentType.wireValue()
                : "agent";
        String shortId = shortFragment(worktreeId, 6);
        return prefix + "-" + shortId;
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

    private String fallbackCommitMessage(List<String> changedFiles) {
        String summary;
        if (changedFiles == null || changedFiles.isEmpty()) {
            summary = "Commit pending worktree changes";
        } else if (changedFiles.size() == 1) {
            summary = "Update " + changedFiles.getFirst();
        } else {
            summary = "Update " + changedFiles.getFirst() + " and related files";
        }
        return normalizeCommitSubject(summary);
    }

    private List<WorktreeCommitMetadata> sanitizeMetadata(List<WorktreeCommitMetadata> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return List.of();
        }
        List<WorktreeCommitMetadata> sanitized = new ArrayList<>();
        for (WorktreeCommitMetadata item : metadata) {
            if (item == null) {
                continue;
            }
            sanitized.add(item.withChangedFiles(condenseChangedFiles(item.changedFiles())));
        }
        return sanitized;
    }

    private List<String> condenseChangedFiles(List<String> changedFiles) {
        if (changedFiles == null || changedFiles.isEmpty()) {
            return List.of();
        }
        if (changedFiles.size() <= 20) {
            return List.copyOf(changedFiles);
        }
        return List.copyOf(changedFiles.subList(0, 20));
    }

    private String normalizeCommitSubject(String value) {
        String subject = value == null ? "Commit pending worktree changes" : value.replace('\n', ' ').trim();
        if (subject.isBlank()) {
            subject = "Commit pending worktree changes";
        }
        if (subject.length() > 72) {
            return subject.substring(0, 72).trim();
        }
        return subject;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
