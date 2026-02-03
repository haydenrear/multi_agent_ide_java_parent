package com.hayden.multiagentide.agent.decorator;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.MergeResult;
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
import java.util.List;

/**
 * Decorator that performs trunk → child merge when a dispatched agent completes.
 * 
 * Phase 1 of the worktree merge flow:
 * - After child agent (Ticket/Planning/Discovery) completes its work
 * - Merge any changes from trunk into the child's worktree
 * - Attach MergeDescriptor to the result
 * 
 * This ensures the child has the latest trunk changes before its results
 * are aggregated back to trunk.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorktreeMergeResultDecorator implements DispatchedAgentResultDecorator {

    private final GitWorktreeService gitWorktreeService;

    @Override
    public int order() {
        return 1000;  // Run after core decorators, before emit decorators
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorate(T result, DecoratorContext context) {
        if (result == null) {
            return result;
        }

        // Only handle dispatch agent results that have worktree context
        if (!(result instanceof AgentModels.TicketAgentResult) &&
            !(result instanceof AgentModels.PlanningAgentResult) &&
            !(result instanceof AgentModels.DiscoveryAgentResult)) {
            return result;
        }

        WorktreeSandboxContext childContext = resolveChildWorktreeContext(result, context);
        WorktreeSandboxContext trunkContext = resolveTrunkWorktreeContext(context);

        if (childContext == null || trunkContext == null) {
            log.debug("Skipping trunk→child merge: missing worktree context");
            return result;
        }

        MergeDescriptor descriptor = performTrunkToChildMerge(trunkContext, childContext);
        
        return addMergeDescriptor(result, descriptor);
    }

    /**
     * Performs the trunk → child merge operation.
     * Merges submodules first, then main worktree.
     */
    private MergeDescriptor performTrunkToChildMerge(
            WorktreeSandboxContext trunk,
            WorktreeSandboxContext child) {
        
        List<SubmoduleMergeResult> submoduleResults = new ArrayList<>();
        List<String> allConflicts = new ArrayList<>();
        boolean allSuccessful = true;
        String errorMessage = null;

        // Merge submodules first (trunk → child)
        if (child.submoduleWorktrees() != null) {
            for (SubmoduleWorktreeContext childSubmodule : child.submoduleWorktrees()) {
                SubmoduleWorktreeContext trunkSubmodule = findMatchingSubmodule(trunk, childSubmodule.submoduleName());
                if (trunkSubmodule != null) {
                    try {
                        MergeResult result = gitWorktreeService.mergeWorktrees(
                                trunkSubmodule.worktreeId(),
                                childSubmodule.worktreeId()
                        );
                        result = gitWorktreeService.ensureMergeConflictsCaptured(result);
                        submoduleResults.add(new SubmoduleMergeResult(
                                childSubmodule.submoduleName(),
                                normalizePath(trunkSubmodule.worktreePath()),
                                normalizePath(childSubmodule.worktreePath()),
                                result,
                                false  // pointer update not applicable for trunk→child
                        ));
                        if (!result.successful()) {
                            allSuccessful = false;
                            allConflicts.addAll(result.conflicts().stream()
                                    .map(MergeResult.MergeConflict::filePath)
                                    .toList());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to merge submodule {}: {}", childSubmodule.submoduleName(), e.getMessage());
                        allSuccessful = false;
                        errorMessage = "Submodule merge failed: " + e.getMessage();
                    }
                }
            }
        }

        // Merge main worktree (trunk → child)
        MergeResult mainResult = null;
        if (allSuccessful) {
            try {
                mainResult = gitWorktreeService.mergeWorktrees(
                        trunk.mainWorktree().worktreeId(),
                        child.mainWorktree().worktreeId()
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
                .mergeDirection(MergeDirection.TRUNK_TO_CHILD)
                .successful(allSuccessful)
                .conflictFiles(allConflicts)
                .submoduleMergeResults(submoduleResults)
                .mainWorktreeMergeResult(mainResult)
                .errorMessage(allSuccessful ? null : (errorMessage != null ? errorMessage : "Merge conflicts detected"))
                .build();
    }

    /**
     * Resolves the child worktree context from the result or decorator context.
     */
    private WorktreeSandboxContext resolveChildWorktreeContext(AgentModels.AgentResult result, DecoratorContext context) {
        // Try to get from the agentRequest in context (the request that produced this result)
        if (context.agentRequest() instanceof AgentModels.AgentRequest request) {
            WorktreeSandboxContext worktreeContext = request.worktreeContext();
            if (worktreeContext != null) {
                return worktreeContext;
            }
        }
        return null;
    }

    /**
     * Resolves the trunk (parent) worktree context from the last request.
     */
    private WorktreeSandboxContext resolveTrunkWorktreeContext(DecoratorContext context) {
        // The trunk context comes from the dispatch agent's request (the parent)
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

    /**
     * Adds the merge descriptor to the appropriate result type.
     */
    @SuppressWarnings("unchecked")
    private <T extends AgentModels.AgentResult> T addMergeDescriptor(T result, MergeDescriptor descriptor) {
        return switch (result) {
            case AgentModels.TicketAgentResult r -> (T) r.toBuilder().mergeDescriptor(descriptor).build();
            case AgentModels.PlanningAgentResult r -> (T) r.toBuilder().mergeDescriptor(descriptor).build();
            case AgentModels.DiscoveryAgentResult r -> (T) r.toBuilder().mergeDescriptor(descriptor).build();
            default -> result;
        };
    }

    private String normalizePath(Path path) {
        if (path == null) {
            return null;
        }
        return path.toAbsolutePath().normalize().toString();
    }
}
