package com.hayden.multiagentide.agent.decorator;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.MergeResult;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class WorktreeMergeResultDecoratorTest {

    @Mock
    private GitWorktreeService gitWorktreeService;

    private WorktreeMergeResultDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new WorktreeMergeResultDecorator(gitWorktreeService);
        lenient().when(gitWorktreeService.ensureMergeConflictsCaptured(
                org.mockito.ArgumentMatchers.any(MergeResult.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(gitWorktreeService.detectMergeConflicts(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(List.of());
        lenient().when(gitWorktreeService.parentContainsChildHead(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(true);
    }

    @Test
    @DisplayName("decorate merges submodules before main and adds merge descriptor for ticket results")
    void decorateTicketAgentResultMergesSubmodulesThenMain() {
        WorktreeSandboxContext trunk = sandboxContext(
                mainContext("trunk-main", "/tmp/trunk"),
                List.of(
                        submoduleContext("trunk-sub-1", "sub-1", "/tmp/trunk/sub1", "trunk-main"),
                        submoduleContext("trunk-sub-2", "sub-2", "/tmp/trunk/sub2", "trunk-main")
                )
        );
        WorktreeSandboxContext child = sandboxContext(
                mainContext("child-main", "/tmp/child"),
                List.of(
                        submoduleContext("child-sub-1", "sub-1", "/tmp/child/sub1", "child-main"),
                        submoduleContext("child-sub-2", "sub-2", "/tmp/child/sub2", "child-main")
                )
        );

        when(gitWorktreeService.mergeWorktrees("trunk-sub-1", "child-sub-1"))
                .thenReturn(successMerge("trunk-sub-1", "child-sub-1"));
        when(gitWorktreeService.mergeWorktrees("trunk-sub-2", "child-sub-2"))
                .thenReturn(successMerge("trunk-sub-2", "child-sub-2"));
        when(gitWorktreeService.mergeWorktrees("trunk-main", "child-main"))
                .thenReturn(successMerge("trunk-main", "child-main"));

        ArtifactKey contextId = ArtifactKey.createRoot();
        AgentModels.TicketAgentResult result = AgentModels.TicketAgentResult.builder()
                .contextId(contextId)
                .output("done")
                .build();
        AgentModels.TicketAgentRequest childRequest = AgentModels.TicketAgentRequest.builder()
                .contextId(contextId)
                .worktreeContext(child)
                .build();
        AgentModels.TicketOrchestratorRequest trunkRequest = AgentModels.TicketOrchestratorRequest.builder()
                .contextId(contextId)
                .worktreeContext(trunk)
                .build();

        DecoratorContext ctx = new DecoratorContext(null, "agent", "action", "method", trunkRequest, childRequest);

        AgentModels.TicketAgentResult decorated = decorator.decorate(result, ctx);
        MergeDescriptor descriptor = decorated.mergeDescriptor();

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.mergeDirection()).isEqualTo(MergeDirection.TRUNK_TO_CHILD);
        assertThat(descriptor.successful()).isTrue();
        assertThat(descriptor.conflictFiles()).isEmpty();
        assertThat(descriptor.submoduleMergeResults()).hasSize(2);
        assertThat(descriptor.submoduleMergeResults())
                .allMatch(SubmoduleMergeResult::successful)
                .allMatch(r -> !r.pointerUpdated());
        assertThat(descriptor.mainWorktreeMergeResult()).isNotNull();

        InOrder order = inOrder(gitWorktreeService);
        order.verify(gitWorktreeService).mergeWorktrees("trunk-sub-1", "child-sub-1");
        order.verify(gitWorktreeService).mergeWorktrees("trunk-sub-2", "child-sub-2");
        order.verify(gitWorktreeService).mergeWorktrees("trunk-main", "child-main");
    }

    @Test
    @DisplayName("decorate adds merge descriptor for planning results")
    void decoratePlanningAgentResultAddsMergeDescriptor() {
        WorktreeSandboxContext trunk = sandboxContext(mainContext("trunk-main", "/tmp/trunk"), List.of());
        WorktreeSandboxContext child = sandboxContext(mainContext("child-main", "/tmp/child"), List.of());

        when(gitWorktreeService.mergeWorktrees("trunk-main", "child-main"))
                .thenReturn(successMerge("trunk-main", "child-main"));

        ArtifactKey contextId = ArtifactKey.createRoot();
        AgentModels.PlanningAgentResult result = AgentModels.PlanningAgentResult.builder()
                .contextId(contextId)
                .output("ok")
                .build();
        AgentModels.PlanningAgentRequest childRequest = AgentModels.PlanningAgentRequest.builder()
                .contextId(contextId)
                .worktreeContext(child)
                .build();
        AgentModels.PlanningOrchestratorRequest trunkRequest = AgentModels.PlanningOrchestratorRequest.builder()
                .contextId(contextId)
                .worktreeContext(trunk)
                .build();

        DecoratorContext ctx = new DecoratorContext(null, "agent", "action", "method", trunkRequest, childRequest);

        AgentModels.PlanningAgentResult decorated = decorator.decorate(result, ctx);
        assertThat(decorated.mergeDescriptor()).isNotNull();
        assertThat(decorated.mergeDescriptor().mergeDirection()).isEqualTo(MergeDirection.TRUNK_TO_CHILD);
        assertThat(decorated.mergeDescriptor().successful()).isTrue();
    }

    @Test
    @DisplayName("decorate adds merge descriptor for discovery results")
    void decorateDiscoveryAgentResultAddsMergeDescriptor() {
        WorktreeSandboxContext trunk = sandboxContext(mainContext("trunk-main", "/tmp/trunk"), List.of());
        WorktreeSandboxContext child = sandboxContext(mainContext("child-main", "/tmp/child"), List.of());

        when(gitWorktreeService.mergeWorktrees("trunk-main", "child-main"))
                .thenReturn(successMerge("trunk-main", "child-main"));

        ArtifactKey contextId = ArtifactKey.createRoot();
        AgentModels.DiscoveryAgentResult result = AgentModels.DiscoveryAgentResult.builder()
                .contextId(contextId)
                .output("ok")
                .build();
        AgentModels.DiscoveryAgentRequest childRequest = AgentModels.DiscoveryAgentRequest.builder()
                .contextId(contextId)
                .worktreeContext(child)
                .build();
        AgentModels.DiscoveryOrchestratorRequest trunkRequest = AgentModels.DiscoveryOrchestratorRequest.builder()
                .contextId(contextId)
                .worktreeContext(trunk)
                .build();

        DecoratorContext ctx = new DecoratorContext(null, "agent", "action", "method", trunkRequest, childRequest);

        AgentModels.DiscoveryAgentResult decorated = decorator.decorate(result, ctx);
        assertThat(decorated.mergeDescriptor()).isNotNull();
        assertThat(decorated.mergeDescriptor().mergeDirection()).isEqualTo(MergeDirection.TRUNK_TO_CHILD);
        assertThat(decorated.mergeDescriptor().successful()).isTrue();
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
}
