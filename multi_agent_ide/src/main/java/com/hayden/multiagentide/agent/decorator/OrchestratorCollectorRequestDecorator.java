package com.hayden.multiagentide.agent.decorator;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.WorkflowGraphService;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.MergeResult;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.merge.MergeDirection;
import com.hayden.multiagentidelib.model.merge.SubmoduleMergeResult;
import com.hayden.multiagentidelib.model.nodes.OrchestratorNode;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
            MergeResult mergeResult = gitWorktreeService.finalMergeToSource(mainWorktreeId);
            mergeResult = gitWorktreeService.ensureMergeConflictsCaptured(
                    mergeResult,
                    orchestratorNode.worktreeContext()
            );

            MergeDescriptor descriptor = buildMergeDescriptor(mergeResult, orchestratorNode);

            if (descriptor.successful()) {
                log.info("Final merge to source successful. Commit: {}", mergeResult.mergeCommitHash());
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

    private MergeDescriptor buildMergeDescriptor(MergeResult mergeResult, OrchestratorNode orchestratorNode) {
        List<SubmoduleMergeResult> submoduleMergeResults = new ArrayList<>();
        MainWorktreeContext mainWorktree = orchestratorNode.worktreeContext();

        if (mainWorktree != null && mainWorktree.submoduleWorktrees() != null) {
            for (SubmoduleWorktreeContext sub : mainWorktree.submoduleWorktrees()) {
                submoduleMergeResults.add(new SubmoduleMergeResult(
                        sub.submoduleName(),
                        normalizePath(sub.worktreePath()),
                        null,
                        null,
                        false
                ));
            }
        }

        List<String> conflictFiles = mergeResult.conflicts() != null
                ? mergeResult.conflicts().stream()
                    .map(MergeResult.MergeConflict::filePath)
                    .filter(Objects::nonNull)
                    .toList()
                : List.of();

        return MergeDescriptor.builder()
                .mergeDirection(MergeDirection.WORKTREE_TO_SOURCE)
                .successful(mergeResult.successful())
                .conflictFiles(conflictFiles)
                .submoduleMergeResults(submoduleMergeResults)
                .mainWorktreeMergeResult(mergeResult)
                .errorMessage(mergeResult.successful() ? null : mergeResult.mergeMessage())
                .build();
    }

    private String normalizePath(Path path) {
        if (path == null) {
            return null;
        }
        return path.toAbsolutePath().normalize().toString();
    }
}
