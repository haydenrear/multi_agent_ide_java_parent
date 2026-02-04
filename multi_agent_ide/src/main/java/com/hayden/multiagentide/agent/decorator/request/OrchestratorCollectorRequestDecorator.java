package com.hayden.multiagentide.agent.decorator.request;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.WorkflowGraphService;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.merge.MergeDirection;
import com.hayden.multiagentidelib.model.nodes.OrchestratorNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    private final GitWorktreeService gitWorktreeService;

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

            log.info("Executing final merge to source for worktree: {}", mainWorktreeId);
            MergeDescriptor descriptor = gitWorktreeService.finalMergeToSourceDescriptor(mainWorktreeId);

            if (descriptor.successful()) {
                log.info("Final merge to source successful.");
            } else {
                log.error("Final merge to source failed: {}. Conflicts: {}",
                        descriptor.errorMessage(), descriptor.conflictFiles());
            }

            return (T) collectorRequest.toBuilder()
                    .mergeDescriptor(descriptor)
                    .build();
        } catch (Exception e) {
            log.error("Error during final merge to source", e);
            return (T) collectorRequest.toBuilder()
                    .mergeDescriptor(MergeDescriptor.builder()
                            .mergeDirection(MergeDirection.WORKTREE_TO_SOURCE)
                            .successful(false)
                            .errorMessage("Final merge to source failed: " + e.getMessage())
                            .build())
                    .build();
        }
    }
}
