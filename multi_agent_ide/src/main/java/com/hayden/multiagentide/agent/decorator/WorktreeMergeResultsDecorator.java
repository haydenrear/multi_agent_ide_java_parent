package com.hayden.multiagentide.agent.decorator;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.MergeResult;
import com.hayden.multiagentidelib.model.merge.AgentMergeStatus;
import com.hayden.multiagentidelib.model.merge.MergeAggregation;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.merge.MergeDirection;
import com.hayden.multiagentidelib.model.merge.SubmoduleMergeResult;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Decorator that performs child → trunk merge when aggregating dispatch agent results.
 * 
 * Phase 2 of the worktree merge flow:
 * - After collecting child agent results (Ticket/Planning/Discovery)
 * - Iterate through each child's worktree
 * - Merge submodules first (child → trunk), update pointers
 * - Merge main worktree (child → trunk)
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

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public <T extends AgentModels.ResultsRequest> T decorate(T resultsRequest, DecoratorContext context) {
        if (resultsRequest == null) {
            return resultsRequest;
        }

        WorktreeSandboxContext trunkContext = resolveTrunkWorktreeContext(context);
        if (trunkContext == null) {
            log.debug("Skipping child→trunk merge: no trunk worktree context");
            return resultsRequest;
        }

        List<? extends AgentModels.AgentResult> childResults = resultsRequest.childResults();
        if (childResults == null || childResults.isEmpty()) {
            log.debug("Skipping child→trunk merge: no child results");
            return resultsRequest;
        }

        MergeAggregation aggregation = performChildToTrunkMerges(childResults, trunkContext);

        T decorated = resultsRequest.withMergeAggregation(aggregation);
        return decorated;
    }

    /**
     * Performs child → trunk merges for all child results.
     * Stops on first conflict.
     */
    private MergeAggregation performChildToTrunkMerges(
            List<? extends AgentModels.AgentResult> childResults,
            WorktreeSandboxContext trunkContext) {
        
        List<AgentMergeStatus> merged = new ArrayList<>();
        List<AgentMergeStatus> pending = new ArrayList<>();
        AgentMergeStatus conflicted = null;

        // Initialize all as pending
        for (AgentModels.AgentResult result : childResults) {
            pending.add(createPendingStatus(result));
        }

        Iterator<AgentMergeStatus> pendingIterator = pending.iterator();
        while (pendingIterator.hasNext()) {
            AgentMergeStatus status = pendingIterator.next();
            WorktreeSandboxContext childContext = status.worktreeContext();

            if (childContext == null) {
                // No worktree, treat as merged (no-op)
                pendingIterator.remove();
                merged.add(status);
                log.debug("Child {} has no worktree context, treating as merged", status.agentResultId());
                continue;
            }

            MergeDescriptor descriptor = performSingleChildToTrunkMerge(childContext, trunkContext);
            AgentMergeStatus updatedStatus = status.withMergeDescriptor(descriptor);

            if (!descriptor.successful()) {
                pendingIterator.remove();
                conflicted = updatedStatus;
                log.info("Merge conflict detected for child {}, stopping merge iteration", status.agentResultId());
                break;  // Stop on first conflict
            }

            pendingIterator.remove();
            merged.add(updatedStatus);
            log.debug("Successfully merged child {} to trunk", status.agentResultId());
        }

        return MergeAggregation.builder()
                .merged(merged)
                .pending(new ArrayList<>(pending))  // Remaining pending items
                .conflicted(conflicted)
                .build();
    }

    /**
     * Performs a single child → trunk merge operation.
     * Merges submodules first, then main worktree.
     */
    private MergeDescriptor performSingleChildToTrunkMerge(
            WorktreeSandboxContext child,
            WorktreeSandboxContext trunk) {
        
        List<SubmoduleMergeResult> submoduleResults = new ArrayList<>();
        List<String> allConflicts = new ArrayList<>();
        boolean allSuccessful = true;
        String errorMessage = null;

        // Merge submodules first (child → trunk)
        if (child.submoduleWorktrees() != null) {
            for (SubmoduleWorktreeContext childSubmodule : child.submoduleWorktrees()) {
                SubmoduleWorktreeContext trunkSubmodule = findMatchingSubmodule(trunk, childSubmodule.submoduleName());
                if (trunkSubmodule != null) {
                    try {
                        MergeResult result = gitWorktreeService.mergeWorktrees(
                                childSubmodule.worktreeId(),
                                trunkSubmodule.worktreeId()
                        );
                        result = gitWorktreeService.ensureMergeConflictsCaptured(result);

                        boolean pointerUpdated = false;
                        if (result.successful()) {
                            // Update submodule pointer in trunk's main worktree
                            try {
                                gitWorktreeService.updateSubmodulePointer(
                                        trunk.mainWorktree().worktreeId(),
                                        childSubmodule.submoduleName()
                                );
                                pointerUpdated = true;
                            } catch (Exception e) {
                                log.warn("Failed to update submodule pointer for {}: {}", 
                                        childSubmodule.submoduleName(), e.getMessage());
                                // Pointer update failed, treat as partial success
                            }
                        }

                        submoduleResults.add(new SubmoduleMergeResult(
                                childSubmodule.submoduleName(),
                                normalizePath(childSubmodule.worktreePath()),
                                normalizePath(trunkSubmodule.worktreePath()),
                                result,
                                pointerUpdated
                        ));

                        if (!result.successful()) {
                            allSuccessful = false;
                            allConflicts.addAll(result.conflicts().stream()
                                    .map(MergeResult.MergeConflict::filePath)
                                    .toList());
                            break;  // Stop on first submodule conflict
                        }
                    } catch (Exception e) {
                        log.warn("Failed to merge submodule {}: {}", childSubmodule.submoduleName(), e.getMessage());
                        allSuccessful = false;
                        errorMessage = "Submodule merge failed: " + e.getMessage();
                        break;
                    }
                }
            }
        }

        // Merge main worktree only if all submodules succeeded
        MergeResult mainResult = null;
        if (allSuccessful) {
            try {
                mainResult = gitWorktreeService.mergeWorktrees(
                        child.mainWorktree().worktreeId(),
                        trunk.mainWorktree().worktreeId()
                );
                mainResult = gitWorktreeService.ensureMergeConflictsCaptured(mainResult);
                if (!mainResult.successful()) {
                    allSuccessful = false;
                    allConflicts.addAll(mainResult.conflicts().stream()
                            .map(MergeResult.MergeConflict::filePath)
                            .toList());
                }
            } catch (Exception e) {
                log.warn("Failed to merge main worktree: {}", e.getMessage());
                allSuccessful = false;
                errorMessage = "Main worktree merge failed: " + e.getMessage();
            }
        }

        return MergeDescriptor.builder()
                .mergeDirection(MergeDirection.CHILD_TO_TRUNK)
                .successful(allSuccessful)
                .conflictFiles(allConflicts)
                .submoduleMergeResults(submoduleResults)
                .mainWorktreeMergeResult(mainResult)
                .errorMessage(allSuccessful ? null : (errorMessage != null ? errorMessage : "Merge conflicts detected"))
                .build();
    }

    /**
     * Creates a pending AgentMergeStatus from an agent result.
     */
    private AgentMergeStatus createPendingStatus(AgentModels.AgentResult result) {
        String agentResultId = result.contextId() != null ? result.contextId().value() : "unknown";
        WorktreeSandboxContext worktreeContext = resolveChildWorktreeFromResult(result);
        
        return AgentMergeStatus.builder()
                .agentResultId(agentResultId)
                .worktreeContext(worktreeContext)
                .mergeDescriptor(null)  // Will be populated during merge
                .build();
    }

    /**
     * Resolves the child worktree context from the result's merge descriptor
     * or from the associated request context.
     */
    private WorktreeSandboxContext resolveChildWorktreeFromResult(AgentModels.AgentResult result) {
        // Check if we already have worktree context from the result's merge descriptor
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

    /**
     * Resolves the trunk (parent) worktree context from the results request.
     */
    private WorktreeSandboxContext resolveTrunkWorktreeContext(DecoratorContext context) {
        // The trunk context comes from the dispatch agent's request
        if (context.agentRequest() instanceof AgentModels.ResultsRequest resultsRequest) {
            return resultsRequest.worktreeContext();
        }
        if (context.lastRequest() instanceof AgentModels.AgentRequest lastRequest) {
            return lastRequest.worktreeContext();
        }
        return null;
    }

    /**
     * Finds a matching submodule in the trunk context by name.
     */
    private SubmoduleWorktreeContext findMatchingSubmodule(WorktreeSandboxContext trunk, String submoduleName) {
        if (trunk.submoduleWorktrees() == null || submoduleName == null) {
            return null;
        }
        return trunk.submoduleWorktrees().stream()
                .filter(sub -> submoduleName.equals(sub.submoduleName()))
                .findFirst()
                .orElse(null);
    }

    private String normalizePath(Path path) {
        if (path == null) {
            return null;
        }
        return path.toAbsolutePath().normalize().toString();
    }
}
