package com.hayden.multiagentide.agent.decorator.request;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.WorkflowGraphService;
import com.hayden.multiagentide.service.GitMergeService;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.merge.MergeDirection;
import com.hayden.multiagentidelib.model.nodes.OrchestratorNode;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Request decorator that merges the orchestrator's derived branches back into the
 * original base branches in the source repository when the OrchestratorCollectorRequest
 * is being prepared. Attaches a MergeDescriptor to the request with the result.
 *
 * Runs at order 9999 to execute after most other request decorators.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrchestratorCollectorRequestDecorator implements RequestDecorator {

    private final WorkflowGraphService graphService;
    private final GitMergeService gitMergeService;

    @Override
    public int order() {
        return 9_999;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        if (request == null || context == null) {
            return request;
        }

        if (!(request instanceof AgentModels.OrchestratorCollectorRequest collectorRequest)) {
            return request;
        }

        try {
            OrchestratorNode orchestratorNode = graphService.requireOrchestrator(
                    context.operationContext()
            );

            String mainWorktreeId = orchestratorNode.mainWorktreeId();

            if (!Objects.equals(orchestratorNode.worktreeContext().worktreePath(), collectorRequest.worktreeContext().mainWorktree().worktreePath())) {
                log.error("Worktree paths were not in expected, {}, {}",
                        orchestratorNode.worktreeContext().worktreePath(),
                        collectorRequest.worktreeContext().mainWorktree().worktreePath());
            }

            var wt = getSandboxContext(context, AgentModels.TicketCollectorRequest.class, AgentModels.TicketCollectorResult.class)
                    .or(() -> getSandboxContext(context, AgentModels.DiscoveryCollectorRequest.class, AgentModels.DiscoveryCollectorResult.class))
                    .or(() -> getSandboxContext(context, AgentModels.PlanningCollectorRequest.class, AgentModels.PlanningCollectorResult.class));

            if (wt.isEmpty()) {
                log.error("Worktree could not be found for ticket collector result.");
                return request;
            }

            var sourceRequest = collectorRequest.toBuilder()
                    .worktreeContext(wt.get())
                    .build();

            MergeDescriptor descriptor = gitMergeService.finalMergeToSourceWithAutoCommit(
                    sourceRequest,
                    collectorRequest.worktreeContext(),
                    wt.get(),
                    context,
                    collectorRequest.goal(),
                    mainWorktreeId
            );

            log.info("Executing final merge to source for worktree: {}", mainWorktreeId);

            if (descriptor.successful()) {
                log.info("Final merge to source successful.");
            } else {
                log.error("Final merge to source failed: {}. Conflicts: {}",
                        descriptor.errorMessage(), descriptor.conflictFiles());
            }

            return (T) sourceRequest.toBuilder()
                    .mergeDescriptor(descriptor)
                    .worktreeContext(wt.get())
                    .build();
        } catch (Exception e) {
            log.error("Error during final merge to source", e);
            return (T) collectorRequest.toBuilder()
                    .mergeDescriptor(
                            MergeDescriptor.builder()
                                    .mergeDirection(MergeDirection.WORKTREE_TO_SOURCE)
                                    .successful(false)
                                    .errorMessage("Final merge to source failed: " + e.getMessage())
                                    .build())
                    .build();
        }
    }

    private static @NonNull Optional<AgentModels.AgentRequest> getRequest(DecoratorContext context,
                                                                                 Class<? extends AgentModels.AgentRequest> req) {
        return Optional.ofNullable(BlackboardHistory.getLastFromHistory(context.operationContext(), req));
    }

    private static @NonNull Optional<WorktreeSandboxContext> getSandboxContext(DecoratorContext context,
                                                                               Class<? extends AgentModels.AgentRequest> req,
                                                                               Class<? extends AgentModels.AgentResult> res) {
        return Optional.ofNullable(BlackboardHistory.getLastFromHistory(context.operationContext(), req))
                .flatMap(tcr -> Optional.ofNullable(tcr.worktreeContext()))
                .or(() -> Optional.ofNullable(BlackboardHistory.getLastFromHistory(context.operationContext(), res))
                        .flatMap(tcr -> Optional.ofNullable(tcr.worktreeContext())));
    }

}
