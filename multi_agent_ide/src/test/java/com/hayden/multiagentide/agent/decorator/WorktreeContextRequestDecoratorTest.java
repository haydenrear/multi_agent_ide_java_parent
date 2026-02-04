package com.hayden.multiagentide.agent.decorator;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.decorator.request.WorktreeContextRequestDecorator;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.multiagentide.service.WorktreeService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.nodes.HasWorktree;
import com.hayden.multiagentidelib.model.nodes.OrchestratorNode;
import com.hayden.multiagentidelib.model.nodes.TicketNode;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

@ExtendWith(MockitoExtension.class)
class WorktreeContextRequestDecoratorTest {

    @Mock
    private GraphRepository graphRepository;

    @Mock
    private WorktreeRepository worktreeRepository;

    @Mock
    private WorktreeService worktreeService;

    private WorktreeContextRequestDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new WorktreeContextRequestDecorator(graphRepository, worktreeRepository, worktreeService);
    }

    @TestFactory
    @DisplayName("decorate propagates worktree context for all request types")
    Stream<DynamicTest> decoratePropagatesWorktreeContextForAllRequestTypes() {
        WorktreeSandboxContext sandbox = sandboxContext("main-1", "/tmp/main-1");
        AgentModels.OrchestratorRequest lastRequest = AgentModels.OrchestratorRequest.builder()
                .contextId(ArtifactKey.createRoot())
                .worktreeContext(sandbox)
                .build();

        return simpleRequests().map(request -> DynamicTest.dynamicTest(
                request.getClass().getSimpleName(),
                () -> {
                    DecoratorContext ctx = new DecoratorContext(null, "agent", "action", "method", lastRequest, request);
                    AgentModels.AgentRequest decorated = decorator.decorate(request, ctx);
                    assertThat(decorated.worktreeContext()).isSameAs(sandbox);
                }
        ));
    }

    @Test
    @DisplayName("decorate delegates to worktree service for discovery/planning/ticket agent requests")
    void decorateDelegatesToWorktreeServiceForAgentRequests() {
        WorktreeSandboxContext sandbox = sandboxContext("main-1", "/tmp/main-1");
        AgentModels.OrchestratorRequest lastRequest = AgentModels.OrchestratorRequest.builder()
                .contextId(ArtifactKey.createRoot())
                .worktreeContext(sandbox)
                .build();

        AgentModels.DiscoveryAgentRequests discovery = AgentModels.DiscoveryAgentRequests.builder()
                .contextId(ArtifactKey.createRoot())
                .build();
        AgentModels.PlanningAgentRequests planning = AgentModels.PlanningAgentRequests.builder()
                .contextId(ArtifactKey.createRoot())
                .build();
        AgentModels.TicketAgentRequests ticket = AgentModels.TicketAgentRequests.builder()
                .contextId(ArtifactKey.createRoot())
                .build();

        when(worktreeService.attachWorktreesToDiscoveryRequests(any(AgentModels.DiscoveryAgentRequests.class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(worktreeService.attachWorktreesToPlanningRequests(any(AgentModels.PlanningAgentRequests.class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(worktreeService.attachWorktreesToTicketRequests(any(AgentModels.TicketAgentRequests.class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DecoratorContext ctx = new DecoratorContext(null, "agent", "action", "method", lastRequest, discovery);
        AgentModels.DiscoveryAgentRequests decoratedDiscovery =
                decorator.decorate(discovery, ctx);
        assertThat(decoratedDiscovery.worktreeContext()).isSameAs(sandbox);

        ctx = new DecoratorContext(null, "agent", "action", "method", lastRequest, planning);
        AgentModels.PlanningAgentRequests decoratedPlanning =
                decorator.decorate(planning, ctx);
        assertThat(decoratedPlanning.worktreeContext()).isSameAs(sandbox);

        ctx = new DecoratorContext(null, "agent", "action", "method", lastRequest, ticket);
        AgentModels.TicketAgentRequests decoratedTicket =
                decorator.decorate(ticket, ctx);
        assertThat(decoratedTicket.worktreeContext()).isSameAs(sandbox);

        ArgumentCaptor<AgentModels.DiscoveryAgentRequests> discoveryCaptor = ArgumentCaptor.forClass(AgentModels.DiscoveryAgentRequests.class);
        verify(worktreeService).attachWorktreesToDiscoveryRequests(discoveryCaptor.capture(), anyString());
        assertThat(discoveryCaptor.getValue().worktreeContext()).isSameAs(sandbox);

        ArgumentCaptor<AgentModels.PlanningAgentRequests> planningCaptor = ArgumentCaptor.forClass(AgentModels.PlanningAgentRequests.class);
        verify(worktreeService).attachWorktreesToPlanningRequests(planningCaptor.capture(), anyString());
        assertThat(planningCaptor.getValue().worktreeContext()).isSameAs(sandbox);

        ArgumentCaptor<AgentModels.TicketAgentRequests> ticketCaptor = ArgumentCaptor.forClass(AgentModels.TicketAgentRequests.class);
        verify(worktreeService).attachWorktreesToTicketRequests(ticketCaptor.capture(), anyString());
        assertThat(ticketCaptor.getValue().worktreeContext()).isSameAs(sandbox);
    }

    @Test
    @DisplayName("decorate resolves sandbox context from orchestrator node when no parent context exists")
    void decorateResolvesFromOrchestratorNode() {
        OperationContext operationContext = mock(OperationContext.class, RETURNS_DEEP_STUBS);
        when(operationContext.getProcessContext().getAgentProcess().getId()).thenReturn("node-1");

        MainWorktreeContext main = mainContext("main-1", "/tmp/main-1");
        SubmoduleWorktreeContext sub = submoduleContext("sub-1", "sub-1", "/tmp/main-1/sub1", "main-1");

        MainWorktreeContext orchestratorWorktree = main.toBuilder()
                .submoduleWorktrees(List.of(sub))
                .build();

        OrchestratorNode node = OrchestratorNode.builder()
                .nodeId("node-1")
                .worktreeContext(orchestratorWorktree)
                .build();

        when(graphRepository.findById("node-1")).thenReturn(Optional.of(node));
        when(worktreeRepository.findById("main-1")).thenReturn(Optional.of(main));

        AgentModels.OrchestratorRequest request = AgentModels.OrchestratorRequest.builder()
                .contextId(ArtifactKey.createRoot())
                .build();

        DecoratorContext ctx = new DecoratorContext(operationContext, "agent", "action", "method", null, request);

        AgentModels.OrchestratorRequest decorated = decorator.decorate(request, ctx);
        assertThat(decorated.worktreeContext()).isNotNull();
        assertThat(decorated.worktreeContext().mainWorktree()).isEqualTo(main);
        assertThat(decorated.worktreeContext().submoduleWorktrees()).containsExactly(sub);
    }

    @Test
    @DisplayName("decorate resolves sandbox context from HasWorktree nodes")
    void decorateResolvesFromHasWorktreeNode() {
        OperationContext operationContext = mock(OperationContext.class, RETURNS_DEEP_STUBS);
        when(operationContext.getProcessContext().getAgentProcess().getId()).thenReturn("node-2");

        MainWorktreeContext main = mainContext("main-2", "/tmp/main-2");
        SubmoduleWorktreeContext sub = submoduleContext("sub-2", "sub-2", "/tmp/main-2/sub2", "main-2");

        HasWorktree.WorkTree worktree = new HasWorktree.WorkTree(
                "main-2",
                null,
                List.of(new HasWorktree.WorkTree("sub-2", "main-2", List.of()))
        );
        TicketNode node = TicketNode.builder()
                .nodeId("node-2")
                .goal("goal")
                .worktree(worktree)
                .build();

        when(graphRepository.findById("node-2")).thenReturn(Optional.of(node));
        when(worktreeRepository.findById("main-2")).thenReturn(Optional.of(main));
        when(worktreeRepository.findById("sub-2")).thenReturn(Optional.of(sub));

        AgentModels.TicketAgentRequest request = AgentModels.TicketAgentRequest.builder()
                .contextId(ArtifactKey.createRoot())
                .build();

        DecoratorContext ctx = new DecoratorContext(operationContext, "agent", "action", "method", null, request);

        AgentModels.TicketAgentRequest decorated = decorator.decorate(request, ctx);
        assertThat(decorated.worktreeContext()).isNotNull();
        assertThat(decorated.worktreeContext().mainWorktree()).isEqualTo(main);
        assertThat(decorated.worktreeContext().submoduleWorktrees()).containsExactly(sub);
    }

    private Stream<AgentModels.AgentRequest> simpleRequests() {
        ArtifactKey key = ArtifactKey.createRoot();
        return Stream.of(
                AgentModels.OrchestratorRequest.builder().contextId(key).build(),
                AgentModels.OrchestratorCollectorRequest.builder().contextId(key).build(),
                AgentModels.DiscoveryOrchestratorRequest.builder().contextId(key).build(),
                AgentModels.DiscoveryAgentRequest.builder().contextId(key).build(),
                AgentModels.DiscoveryCollectorRequest.builder().contextId(key).build(),
                AgentModels.DiscoveryAgentResults.builder().contextId(key).build(),
                AgentModels.PlanningOrchestratorRequest.builder().contextId(key).build(),
                AgentModels.PlanningAgentRequest.builder().contextId(key).build(),
                AgentModels.PlanningCollectorRequest.builder().contextId(key).build(),
                AgentModels.PlanningAgentResults.builder().contextId(key).build(),
                AgentModels.TicketOrchestratorRequest.builder().contextId(key).build(),
                AgentModels.TicketAgentRequest.builder().contextId(key).build(),
                AgentModels.TicketCollectorRequest.builder().contextId(key).build(),
                AgentModels.TicketAgentResults.builder().contextId(key).build(),
                AgentModels.ReviewRequest.builder().contextId(key).build(),
                AgentModels.MergerRequest.builder().contextId(key).build(),
                AgentModels.ContextManagerRequest.builder().contextId(key).build(),
                AgentModels.ContextManagerRoutingRequest.builder().contextId(key).build(),
                AgentModels.InterruptRequest.OrchestratorInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.DiscoveryAgentDispatchInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.PlanningAgentInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.PlanningCollectorInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.TicketAgentInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.TicketCollectorInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.TicketAgentDispatchInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.ReviewInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.MergerInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.ContextManagerInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build(),
                AgentModels.InterruptRequest.QuestionAnswerInterruptRequest.builder().contextId(key)
                        .type(Events.InterruptType.HUMAN_REVIEW).build()
        );
    }

    private WorktreeSandboxContext sandboxContext(String id, String path) {
        return new WorktreeSandboxContext(mainContext(id, path), List.of());
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
