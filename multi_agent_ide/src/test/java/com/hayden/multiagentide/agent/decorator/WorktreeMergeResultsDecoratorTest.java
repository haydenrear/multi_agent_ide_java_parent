package com.hayden.multiagentide.agent.decorator;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.decorator.request.WorktreeMergeResultsDecorator;
import com.hayden.multiagentide.service.GitMergeService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.MergeResult;
import com.hayden.multiagentidelib.model.merge.AgentMergeStatus;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorktreeMergeResultsDecoratorTest {

    @Mock
    private GitMergeService gitMergeService;

    private WorktreeMergeResultsDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new WorktreeMergeResultsDecorator(gitMergeService, null);
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

        MergeDescriptor conflictDescriptor = MergeDescriptor.builder()
                .mergeDirection(MergeDirection.CHILD_TO_TRUNK)
                .successful(false)
                .conflictFiles(List.of("README.md"))
                .mainWorktreeMergeResult(conflictMerge("child-1", "trunk-main", "README.md"))
                .errorMessage("Merge conflicts detected")
                .build();

        MergeAggregation conflictAggregation = MergeAggregation.builder()
                .merged(List.of())
                .pending(List.of(
                        AgentMergeStatus.builder()
                                .agentResultId(child2.ticketResult.contextId().value())
                                .worktreeContext(child2.ticketResult.worktreeContext())
                                .build()))
                .conflicted(AgentMergeStatus.builder()
                        .agentResultId(child1.ticketResult.contextId().value())
                        .worktreeContext(child1.ticketResult.worktreeContext())
                        .mergeDescriptor(conflictDescriptor)
                        .build())
                .build();

        when(gitMergeService.mergeChildResultsToTrunkWithAutoCommit(any(), any(), any(), any()))
                .thenReturn(conflictAggregation);
        Mockito.when(gitMergeService.runFinalAggregationConflictPass(any(), any(), any(), any(), any()))
                .thenReturn(conflictAggregation);

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

        verify(gitMergeService, times(1)).mergeChildResultsToTrunkWithAutoCommit(any(), any(), any(), any());
    }

    @Test
    @DisplayName("decorate adds merge aggregation for all results request types")
    void decorateAddsMergeAggregationForAllResultsRequestTypes() {
        WorktreeSandboxContext trunk = sandboxContext(mainContext("trunk-main", "/tmp/trunk"), List.of());
        ChildSetup child = childSetup("child-1", null, null);

        MergeDescriptor successDescriptor = MergeDescriptor.success(
                MergeDirection.CHILD_TO_TRUNK,
                successMerge("child-1", "trunk-main"),
                List.of()
        );
        MergeAggregation successAggregation = MergeAggregation.builder()
                .merged(List.of(
                        AgentMergeStatus.builder()
                                .agentResultId(child.ticketResult.contextId().value())
                                .worktreeContext(child.ticketResult.worktreeContext())
                                .mergeDescriptor(successDescriptor)
                                .build()))
                .pending(List.of())
                .conflicted(null)
                .build();

        when(gitMergeService.mergeChildResultsToTrunkWithAutoCommit(any(), any(), any(), any()))
                .thenReturn(successAggregation);
        Mockito.when(gitMergeService.runFinalAggregationConflictPass(any(), any(), any(), any(), any()))
                .thenReturn(successAggregation);

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

        verify(gitMergeService, times(3)).mergeChildResultsToTrunkWithAutoCommit(any(), any(), any(), any());
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
