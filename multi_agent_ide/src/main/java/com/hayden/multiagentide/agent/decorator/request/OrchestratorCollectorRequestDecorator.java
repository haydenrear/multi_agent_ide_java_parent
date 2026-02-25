package com.hayden.multiagentide.agent.decorator.request;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.WorkflowGraphService;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentide.service.WorktreeAutoCommitService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.merge.MergeDirection;
import com.hayden.multiagentidelib.model.merge.WorktreeCommitMetadata;
import com.hayden.multiagentidelib.model.nodes.OrchestratorNode;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
    private final WorktreeAutoCommitService worktreeAutoCommitService;

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
            WorktreeSandboxContext mergeContext = resolveMergeContext(collectorRequest, mainWorktreeId);
            AgentModels.AgentRequest sourceRequest = resolveCommitSourceRequest(collectorRequest, context);

            AgentModels.CommitAgentResult autoCommitResult = worktreeAutoCommitService.autoCommitDirtyWorktreesForRequest(
                    sourceRequest,
                    mergeContext,
                    context,
                    collectorRequest.goal()
            );
            if (!autoCommitResult.successful()) {
                String reason = "Final merge to source blocked: auto-commit failed: " + autoCommitResult.errorMessage();
                log.error(reason);
                return (T) collectorRequest.toBuilder()
                        .mergeDescriptor(MergeDescriptor.conflict(
                                MergeDirection.WORKTREE_TO_SOURCE,
                                List.of(),
                                null,
                                List.of(),
                                reason
                        ).withCommitMetadata(autoCommitResult.commitMetadata()))
                        .build();
            }

            log.info("Executing final merge to source for worktree: {}", mainWorktreeId);
            MergeDescriptor descriptor = gitWorktreeService.finalMergeToSourceDescriptor(mainWorktreeId);
            descriptor = descriptor.withCommitMetadata(mergeCommitMetadata(
                    descriptor.commitMetadata(),
                    autoCommitResult.commitMetadata()
            ));

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

    private WorktreeSandboxContext resolveMergeContext(AgentModels.OrchestratorCollectorRequest collectorRequest, String mainWorktreeId) {
        if (collectorRequest != null && collectorRequest.worktreeContext() != null) {
            return collectorRequest.worktreeContext();
        }
        return gitWorktreeService.getMainWorktree(mainWorktreeId)
                .map(main -> new WorktreeSandboxContext(main, gitWorktreeService.getSubmoduleWorktrees(mainWorktreeId)))
                .orElse(null);
    }

    private AgentModels.AgentRequest resolveCommitSourceRequest(
            AgentModels.OrchestratorCollectorRequest collectorRequest,
            DecoratorContext context
    ) {
        if (context != null
                && context.lastRequest() instanceof AgentModels.AgentRequest request
                && !(request instanceof AgentModels.CommitAgentRequest)) {
            return request;
        }
        return collectorRequest;
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
}
