package com.hayden.multiagentide.agent.decorator;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.decorator.request.WorktreeMergeResultsDecorator;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentide.service.WorktreeAutoCommitService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.MergeResult;
import com.hayden.multiagentidelib.model.merge.MergeAggregation;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.merge.MergeDirection;
import com.hayden.multiagentidelib.model.merge.SubmoduleMergeResult;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorktreeMergeResultsDecoratorTest {

    @Mock
    private GitWorktreeService gitWorktreeService;
    @Mock
    private WorktreeAutoCommitService worktreeAutoCommitService;

    private WorktreeMergeResultsDecorator decorator;

    @BeforeEach
    void setUp() {
        when(worktreeAutoCommitService.autoCommitDirtyWorktrees(any(), any(), any(), any()))
                .thenReturn(AgentModels.CommitAgentResult.builder()
                        .successful(true)
                        .commitMetadata(List.of())
                        .build());
        decorator = new WorktreeMergeResultsDecorator(gitWorktreeService, worktreeAutoCommitService, null);
    }

    @Test
    @DisplayName("decorate stops on first conflict and leaves remaining children pending")
    void decorateStopsOnConflictAndLeavesPending() {
        WorktreeSandboxContext trunk = sandboxContext(
                mainContext("trunk-main", "/tmp/trunk"),
                List.of(submoduleContext("trunk-sub-1", "sub-1", "/tmp/trunk/sub1", "trunk-main"))
        );

        ChildSetup child1 = childSetup("child-1", "child-1-sub", "sub-1");
        ChildSetup child2 = childSetup("child-2", "child-2-sub", "sub-1");

        when(gitWorktreeService.getMainWorktree("child-1")).thenReturn(java.util.Optional.of(child1.mainContext));
        when(gitWorktreeService.getSubmoduleWorktree("child-1-sub")).thenReturn(java.util.Optional.of(child1.submoduleContext));
        when(gitWorktreeService.getMainWorktree("child-2")).thenReturn(java.util.Optional.of(child2.mainContext));
        when(gitWorktreeService.getSubmoduleWorktree("child-2-sub")).thenReturn(java.util.Optional.of(child2.submoduleContext));

        // child1 merge conflicts
        MergeDescriptor conflictDescriptor = MergeDescriptor.builder()
                .mergeDirection(MergeDirection.CHILD_TO_TRUNK)
                .successful(false)
                .conflictFiles(List.of("README.md"))
                .mainWorktreeMergeResult(conflictMerge("child-1", "trunk-main", "README.md"))
                .errorMessage("Merge conflicts detected")
                .build();

        when(gitWorktreeService.mergeChildToTrunk(any(WorktreeSandboxContext.class), eq(trunk)))
                .thenReturn(conflictDescriptor);

        AgentModels.TicketAgentResults resultsRequest = AgentModels.TicketAgentResults.builder()
                .contextId(ArtifactKey.createRoot())
                .ticketAgentResults(List.of(child1.ticketResult, child2.ticketResult))
                .worktreeContext(trunk)
                .build();

        DecoratorContext ctx = new DecoratorContext(null, "agent", "action", "method", null, resultsRequest);

        AgentModels.TicketAgentResults decorated = decorator.decorate(resultsRequest, ctx);
        MergeAggregation aggregation = decorated.mergeAggregation();

        assertThat(aggregation).isNotNull();
        assertThat(aggregation.conflicted()).isNotNull();
        assertThat(aggregation.merged()).isEmpty();
        assertThat(aggregation.pending()).hasSize(1);
        assertThat(aggregation.totalCount()).isEqualTo(2);

        // Only called once for child1 — child2 was never attempted
        verify(gitWorktreeService, times(1)).mergeChildToTrunk(any(WorktreeSandboxContext.class), eq(trunk));
    }

    @Test
    @DisplayName("decorate adds merge aggregation for all results request types")
    void decorateAddsMergeAggregationForAllResultsRequestTypes() {
        WorktreeSandboxContext trunk = sandboxContext(mainContext("trunk-main", "/tmp/trunk"), List.of());
        ChildSetup child = childSetup("child-1", null, null);

        when(gitWorktreeService.getMainWorktree("child-1")).thenReturn(java.util.Optional.of(child.mainContext));

        MergeDescriptor successDescriptor = MergeDescriptor.builder()
                .mergeDirection(MergeDirection.CHILD_TO_TRUNK)
                .successful(true)
                .mainWorktreeMergeResult(successMerge("child-1", "trunk-main"))
                .build();

        when(gitWorktreeService.mergeChildToTrunk(any(WorktreeSandboxContext.class), eq(trunk)))
                .thenReturn(successDescriptor);

        Stream.of(
                AgentModels.TicketAgentResults.builder()
                        .contextId(ArtifactKey.createRoot())
                        .ticketAgentResults(List.of(child.ticketResult))
                        .worktreeContext(trunk)
                        .build(),
                AgentModels.PlanningAgentResults.builder()
                        .contextId(ArtifactKey.createRoot())
                        .planningAgentResults(List.of(child.planningResult))
                        .worktreeContext(trunk)
                        .build(),
                AgentModels.DiscoveryAgentResults.builder()
                        .contextId(ArtifactKey.createRoot())
                        .result(List.of(child.discoveryResult))
                        .worktreeContext(trunk)
                        .build()
        ).forEach(request -> {
            DecoratorContext ctx = new DecoratorContext(null, "agent", "action", "method", null, request);
            AgentModels.ResultsRequest decorated = decorator.decorate(request, ctx);
            MergeAggregation aggregation = decorated.mergeAggregation();

            assertThat(aggregation).isNotNull();
            assertThat(aggregation.pending()).isEmpty();
            assertThat(aggregation.conflicted()).isNull();
            assertThat(aggregation.merged()).hasSize(1);
            assertThat(aggregation.totalCount()).isEqualTo(1);
        });

        verify(gitWorktreeService, times(3)).mergeChildToTrunk(any(WorktreeSandboxContext.class), eq(trunk));
    }

    private ChildSetup childSetup(String childMainId, String childSubId, String submoduleName) {
        MainWorktreeContext mainContext = mainContext(childMainId, "/tmp/" + childMainId);
        SubmoduleWorktreeContext submoduleContext = null;
        if (childSubId != null && submoduleName != null) {
            submoduleContext = submoduleContext(childSubId, submoduleName, "/tmp/" + childSubId, childMainId);
        }

        MergeDescriptor mergeDescriptor = MergeDescriptor.builder()
                .mergeDirection(MergeDirection.TRUNK_TO_CHILD)
                .successful(true)
                .mainWorktreeMergeResult(successMerge("trunk-main", childMainId))
                .submoduleMergeResults(submoduleContext == null
                        ? List.of()
                        : List.of(SubmoduleMergeResult.builder()
                                .submoduleName(submoduleName)
                                .mergeResult(successMerge("trunk-sub-1", childSubId))
                                .pointerUpdated(false)
                                .build()))
                .build();

        ArtifactKey contextId = ArtifactKey.createRoot();
        AgentModels.TicketAgentResult ticketResult = AgentModels.TicketAgentResult.builder()
                .contextId(contextId)
                .output("ticket")
                .mergeDescriptor(mergeDescriptor)
                .build();
        AgentModels.PlanningAgentResult planningResult = AgentModels.PlanningAgentResult.builder()
                .contextId(contextId)
                .output("planning")
                .mergeDescriptor(mergeDescriptor)
                .build();
        AgentModels.DiscoveryAgentResult discoveryResult = AgentModels.DiscoveryAgentResult.builder()
                .contextId(contextId)
                .output("discovery")
                .mergeDescriptor(mergeDescriptor)
                .build();

        return new ChildSetup(mainContext, submoduleContext, ticketResult, planningResult, discoveryResult);
    }

    private MergeResult successMerge(String childId, String parentId) {
        return new MergeResult(
                "merge-" + childId + "-" + parentId,
                childId,
                parentId,
                "/tmp/" + childId,
                "/tmp/" + parentId,
                true,
                "commit",
                List.of(),
                List.of(),
                "ok",
                Instant.now()
        );
    }

    private MergeResult conflictMerge(String childId, String parentId, String conflictFile) {
        return new MergeResult(
                "merge-" + childId + "-" + parentId,
                childId,
                parentId,
                "/tmp/" + childId,
                "/tmp/" + parentId,
                false,
                null,
                List.of(new MergeResult.MergeConflict(conflictFile, "content", "", "", "", null)),
                List.of(),
                "conflict",
                Instant.now()
        );
    }

    private WorktreeSandboxContext sandboxContext(MainWorktreeContext main, List<SubmoduleWorktreeContext> submodules) {
        return new WorktreeSandboxContext(main, submodules);
    }

    private MainWorktreeContext mainContext(String id, String path) {
        return MainWorktreeContext.builder()
                .worktreeId(id)
                .worktreePath(Path.of(path))
                .repositoryUrl("repo")
                .status(WorktreeContext.WorktreeStatus.ACTIVE)
                .metadata(Map.of())
                .build();
    }

    private SubmoduleWorktreeContext submoduleContext(String id, String name, String path, String mainWorktreeId) {
        return new SubmoduleWorktreeContext(
                id,
                Path.of(path),
                "main",
                WorktreeContext.WorktreeStatus.ACTIVE,
                mainWorktreeId,
                null,
                Instant.now(),
                "commit",
                name,
                "repo",
                mainWorktreeId,
                Map.of()
        );
    }

    private record ChildSetup(
            MainWorktreeContext mainContext,
            SubmoduleWorktreeContext submoduleContext,
            AgentModels.TicketAgentResult ticketResult,
            AgentModels.PlanningAgentResult planningResult,
            AgentModels.DiscoveryAgentResult discoveryResult
    ) {}
}
