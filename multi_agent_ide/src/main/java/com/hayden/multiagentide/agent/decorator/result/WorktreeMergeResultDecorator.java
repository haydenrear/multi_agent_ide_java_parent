package com.hayden.multiagentide.agent.decorator.result;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

        MergeDescriptor descriptor = gitWorktreeService.mergeTrunkToChild(trunkContext, childContext);
        
        return addMergeDescriptor(result, descriptor);
    }

    private WorktreeSandboxContext resolveChildWorktreeContext(AgentModels.AgentResult result, DecoratorContext context) {
        if (context.agentRequest() instanceof AgentModels.AgentRequest request) {
            WorktreeSandboxContext worktreeContext = request.worktreeContext();
            if (worktreeContext != null) {
                return worktreeContext;
            }
        }
        return null;
    }

    private WorktreeSandboxContext resolveTrunkWorktreeContext(DecoratorContext context) {
        if (context.lastRequest() instanceof AgentModels.AgentRequest lastRequest) {
            return lastRequest.worktreeContext();
        }
        return null;
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
