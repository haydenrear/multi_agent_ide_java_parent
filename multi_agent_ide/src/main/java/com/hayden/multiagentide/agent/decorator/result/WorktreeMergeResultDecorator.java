package com.hayden.multiagentide.agent.decorator.result;

import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
public class WorktreeMergeResultDecorator implements DispatchedAgentResultDecorator, ResultDecorator {

    private final GitWorktreeService gitWorktreeService;
    private final EventBus eventBus;

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
            log.debug("Skipping trunk→child merge: missing worktree context (child={}, trunk={})",
                    childContext != null, trunkContext != null);
            return result;
        }

        String nodeId = result.contextId() != null ? result.contextId().value() : "unknown";
        String trunkId = trunkContext.mainWorktree() != null ? trunkContext.mainWorktree().worktreeId() : "unknown";
        String childId = childContext.mainWorktree() != null ? childContext.mainWorktree().worktreeId() : "unknown";

        publishIfAvailable(new Events.MergePhaseStartedEvent(
                UUID.randomUUID().toString(), Instant.now(), nodeId,
                "TRUNK_TO_CHILD", trunkId, childId, 1));

        MergeDescriptor descriptor = gitWorktreeService.mergeTrunkToChild(trunkContext, childContext);

        publishIfAvailable(new Events.MergePhaseCompletedEvent(
                UUID.randomUUID().toString(), Instant.now(), nodeId,
                "TRUNK_TO_CHILD", descriptor.successful(),
                descriptor.successful() ? 1 : 0,
                descriptor.conflictFiles() != null ? descriptor.conflictFiles().size() : 0,
                descriptor.conflictFiles() != null ? descriptor.conflictFiles() : List.of(),
                descriptor.errorMessage()));

        return addMergeDescriptor(result, descriptor);
    }

    private void publishIfAvailable(Events.GraphEvent event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
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

    private <T extends AgentModels.AgentResult> T addMergeDescriptor(T result, MergeDescriptor descriptor) {
        return switch (result) {
            case AgentModels.TicketAgentResult r -> (T) r.toBuilder().mergeDescriptor(descriptor).build();
            case AgentModels.PlanningAgentResult r -> (T) r.toBuilder().mergeDescriptor(descriptor).build();
            case AgentModels.DiscoveryAgentResult r -> (T) r.toBuilder().mergeDescriptor(descriptor).build();
            default -> result;
        };
    }
}
