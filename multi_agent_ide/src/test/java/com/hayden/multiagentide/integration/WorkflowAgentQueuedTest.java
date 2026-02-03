package com.hayden.multiagentide.integration;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ProcessOptions;
import com.hayden.commitdiffcontext.git.res.Git;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.WorkflowGraphService;
import com.hayden.multiagentide.artifacts.ArtifactEventListener;
import com.hayden.multiagentide.artifacts.ArtifactTreeBuilder;
import com.hayden.multiagentide.artifacts.ExecutionScopeService;
import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.orchestration.ComputationGraphOrchestrator;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentide.service.LlmRunner;
import com.hayden.multiagentide.service.WorktreeService;
import com.hayden.multiagentide.support.AgentTestBase;
import com.hayden.multiagentide.support.QueuedLlmRunner;
import com.hayden.multiagentide.support.TestEventListener;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.nodes.*;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.*;

/**
 * Integration tests for WorkflowAgent using queue-based LLM mocking.
 *
 * This approach:
 * 1. Mocks the LlmRunner with a queue of pre-defined responses
 * 2. Lets the real agent code execute (no need to mock individual actions)
 * 3. Verifies the workflow executes correctly with those responses
 *
 * Benefits over action-level mocking:
 * - Simpler test setup (just queue responses)
 * - Tests execute closer to production code
 * - No need to manually call registerAndHideInput
 * - More resilient to refactoring
 *
 * **Important Note**: all of these tests take about 5 min to complete, i.e. not tuned currently
 */
@Slf4j
@SpringBootTest
@Profile("test")
class WorkflowAgentQueuedTest extends AgentTestBase {

    @Autowired
    private AgentPlatform agentPlatform;

    @Autowired
    private QueuedLlmRunner queuedLlmRunner;

    @Autowired
    private GraphRepository graphRepository;

    @Autowired
    private WorktreeRepository worktreeRepository;


    @MockitoBean
    private GitWorktreeService worktreeService;

    @Autowired
    private TestEventListener testEventListener;

    @Autowired
    private PermissionGate permissionGate;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private ArtifactTreeBuilder artifactTreeBuilder;

    @MockitoSpyBean
    private EventBus eventBus;

    @MockitoSpyBean
    private ArtifactEventListener artifactEventListener;

    @MockitoSpyBean
    private ExecutionScopeService executionScopeService;

    @MockitoSpyBean
    private AgentInterfaces.WorkflowAgent workflowAgent;

    @MockitoSpyBean
    private AgentInterfaces.WorkflowAgent.DiscoveryDispatchSubagent discoveryDispatchSubagent;

    @MockitoSpyBean
    private AgentInterfaces.WorkflowAgent.PlanningDispatchSubagent planningDispatchSubagent;

    @MockitoSpyBean
    private AgentInterfaces.WorkflowAgent.TicketDispatchSubagent ticketDispatchSubagent;

    @MockitoSpyBean
    private WorkflowGraphService workflowGraphService;

    @MockitoSpyBean
    private ComputationGraphOrchestrator computationGraphOrchestrator;

    @TempDir
    Path path;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        LlmRunner llmRunner() {
            return new QueuedLlmRunner();
        }

        @Bean
        QueuedLlmRunner queuedLlmRunner(LlmRunner llmRunner) {
            return (QueuedLlmRunner) llmRunner;
        }

        @Bean
        TestEventListener testEventListener() {
            return new TestEventListener();
        }
    }

    @BeforeEach
    void setUp() {
        testEventListener.clear();
        queuedLlmRunner.clear();
        graphRepository.clear();
        worktreeRepository.clear();
        reset(
                workflowAgent,
                discoveryDispatchSubagent,
                planningDispatchSubagent,
                ticketDispatchSubagent,
                workflowGraphService,
                computationGraphOrchestrator,
                worktreeService,
                eventBus
        );
        doThrow(new RuntimeException("worktree disabled"))
                .when(worktreeService)
                .branchWorktree(any(), any(), any());
        doThrow(new RuntimeException("worktree disabled"))
                .when(worktreeService)
                .branchSubmoduleWorktree(any(), any(), any());

        Mockito.when(worktreeService.attachWorktreesToDiscoveryRequests(any(AgentModels.DiscoveryAgentRequests.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(worktreeService.attachWorktreesToPlanningRequests(any(AgentModels.PlanningAgentRequests.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(worktreeService.attachWorktreesToTicketRequests(any(AgentModels.TicketAgentRequests.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(worktreeService.mergeChildToTrunk(any(WorktreeSandboxContext.class), any(WorktreeSandboxContext.class)))
                        .thenAnswer(inv -> MergeDescriptor.builder().build());
        Mockito.when(worktreeService.mergeTrunkToChild(any(WorktreeSandboxContext.class), any(WorktreeSandboxContext.class)))
                .thenAnswer(inv -> MergeDescriptor.builder().build());
        Mockito.when(worktreeService.finalMergeToSourceDescriptor(anyString()))
                .thenAnswer(inv -> MergeDescriptor.builder().build());

        artifactRepository.deleteAll();
        artifactRepository.flush();
    }

    @Nested
    class InterruptScenarios {

        @SneakyThrows
        @Test
        void orchestratorPause_workflowStops() {
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                    .interruptRequest(AgentModels.InterruptRequest.OrchestratorInterruptRequest.builder()
                            .type(Events.InterruptType.PAUSE)
                            .reason("User requested pause")
                            .build())
                    .build());

            CompletableFuture.runAsync(() -> {
                agentPlatform.runAgentFrom(
                        findWorkflowAgent(),
                        ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                        Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Paused task", "DISCOVERY"))
                );
            });

            await().atMost(Duration.ofSeconds(300))
                    .until(() -> permissionGate.isInterruptPending(t -> t.getType() == Events.InterruptType.PAUSE && Objects.equals(t.getReason(), "User requested pause")));

            var pk = agentPlatform.getAgentProcess(contextId).kill();

            var output = permissionGate.getInterruptPending(t -> t.getType() == Events.InterruptType.PAUSE && Objects.equals(t.getReason(), "User requested pause"));

            assertThat(output).isNotNull();
            verify(workflowGraphService).handleOrchestratorInterrupt(any(),
                    argThat(req -> req.type() == Events.InterruptType.PAUSE));

            var ordered = inOrder(
                    workflowAgent
            );
            ordered.verify(workflowAgent).coordinateWorkflow(any(), any());
            ordered.verify(workflowAgent).handleOrchestratorInterrupt(any(), any());

            verify(computationGraphOrchestrator, atLeastOnce()).emitStatusChangeEvent(any(), any(), any(), any());
            queuedLlmRunner.assertAllConsumed();

            permissionGate.resolveInterrupt(output.getInterruptId(), "", "", null);
        }

        @SneakyThrows
        @Test
        void orchestratorPause_resolveInterruptContinues() {
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                    .interruptRequest(AgentModels.InterruptRequest.OrchestratorInterruptRequest.builder()
                            .type(Events.InterruptType.PAUSE)
                            .reason("User requested pause will continue")
                            .build())
                    .build());

            initialOrchestratorToDiscovery("Do that thing.");
            enqueueDiscoveryToEnd("Do that thing.");

            var res = CompletableFuture.supplyAsync(() -> agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Paused task", "DISCOVERY"))
            ));

            await().atMost(Duration.ofSeconds(300))
                    .until(() -> permissionGate.isInterruptPending(
                            t -> t.getType() == Events.InterruptType.PAUSE && Objects.equals(t.getReason(), "User requested pause will continue")));

            verify(workflowGraphService).handleOrchestratorInterrupt(any(),
                    argThat(req -> req.type() == Events.InterruptType.PAUSE));

            var output = permissionGate.getInterruptPending(t -> t.getType() == Events.InterruptType.PAUSE && Objects.equals(t.getReason(), "User requested pause will continue"));

            assertThat(output).isNotNull();

            permissionGate.resolveInterrupt(
                    output.getInterruptId(),
                    "",
                    "",
                    null);

            await().atMost(Duration.ofSeconds(300))
                    .until(res::isDone);

            var result = res.get();

            assertThat(result).isNotNull();
            assertThat(output).isNotNull();

            var ordered = inOrder(
                    workflowAgent,
                    discoveryDispatchSubagent,
                    planningDispatchSubagent,
                    ticketDispatchSubagent
            );
            ordered.verify(workflowAgent).coordinateWorkflow(any(), any());
            ordered.verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            ordered.verify(workflowAgent).dispatchDiscoveryAgentRequests(any(), any());
            ordered.verify(discoveryDispatchSubagent).runDiscoveryAgent(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).handleDiscoveryCollectorBranch(any(), any());
            ordered.verify(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());
            ordered.verify(workflowAgent).dispatchPlanningAgentRequests(any(), any());
            ordered.verify(planningDispatchSubagent).runPlanningAgent(any(AgentModels.PlanningAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidatePlansIntoTickets(any(), any());
            ordered.verify(workflowAgent).handlePlanningCollectorBranch(any(), any());
            ordered.verify(workflowAgent).orchestrateTicketExecution(any(), any());
            ordered.verify(workflowAgent).dispatchTicketAgentRequests(any(), any());
            ordered.verify(ticketDispatchSubagent).runTicketAgent(any(AgentModels.TicketAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateTicketResults(any(), any());
            ordered.verify(workflowAgent).handleTicketCollectorBranch(any(), any());
            ordered.verify(workflowAgent).consolidateWorkflowOutputs(any(), any());

            verify(computationGraphOrchestrator, atLeastOnce()).emitStatusChangeEvent(any(), any(), any(), any());
            queuedLlmRunner.assertAllConsumed();
        }
    }

    @Nested
    class HappyPathWorkflows {

        @Test
        void fullWorkflow_discoveryToPlanningSingleAgentsToCompletion() {
            var contextId = seedOrchestrator().value();
            enqueueHappyPath("Implement auth");

            // Act - Run the workflow with real agent code
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Implement auth", "DISCOVERY"))
            );

            // Assert
            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);

            // Verify all queued responses were consumed
            queuedLlmRunner.assertAllConsumed();

            // Verify graph service calls happened
            verify(workflowGraphService).startOrchestrator(any());
            verify(workflowGraphService).startDiscoveryOrchestrator(any(), any());
            verify(workflowGraphService).startDiscoveryAgent(any(), any(), any(), any());
            verify(workflowGraphService).startPlanningOrchestrator(any(), any());
            verify(workflowGraphService).startPlanningAgent(any(), any(), any(), any());
            verify(workflowGraphService).startTicketOrchestrator(any(), any());
            verify(workflowGraphService).startTicketAgent(any(), any(), anyInt());
            verify(computationGraphOrchestrator, atLeastOnce()).addChildNodeAndEmitEvent(any(), any());
            verify(computationGraphOrchestrator, atLeastOnce()).emitStatusChangeEvent(any(), any(), any(), any());

            var ordered = inOrder(
                    workflowAgent,
                    discoveryDispatchSubagent,
                    planningDispatchSubagent,
                    ticketDispatchSubagent
            );
            ordered.verify(workflowAgent).coordinateWorkflow(any(), any());
            ordered.verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            ordered.verify(workflowAgent).dispatchDiscoveryAgentRequests(any(), any());
            ordered.verify(discoveryDispatchSubagent).runDiscoveryAgent(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).handleDiscoveryCollectorBranch(any(), any());
            ordered.verify(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());
            ordered.verify(workflowAgent).dispatchPlanningAgentRequests(any(), any());
            ordered.verify(planningDispatchSubagent).runPlanningAgent(any(AgentModels.PlanningAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidatePlansIntoTickets(any(), any());
            ordered.verify(workflowAgent).handlePlanningCollectorBranch(any(), any());
            ordered.verify(workflowAgent).orchestrateTicketExecution(any(), any());
            ordered.verify(workflowAgent).dispatchTicketAgentRequests(any(), any());
            ordered.verify(ticketDispatchSubagent).runTicketAgent(any(AgentModels.TicketAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateTicketResults(any(), any());
            ordered.verify(workflowAgent).handleTicketCollectorBranch(any(), any());
            ordered.verify(workflowAgent).consolidateWorkflowOutputs(any(), any());
        }

//        @Test
        void fullWorkflow_persistsArtifactTree() {
            var contextId =  seedOrchestrator().value();
            enqueueHappyPath("Implement auth");

            Instant startedAt = Instant.now();

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Implement auth", "DISCOVERY"))
            );

            Instant finishedAt = Instant.now();

            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // Verify we emitted artifacts and opened execution scope
            verify(artifactEventListener, atLeastOnce()).onEvent(isA(Events.ArtifactEvent.class));
            verify(executionScopeService, atLeastOnce()).startExecution(anyString(), any(ArtifactKey.class));

            String executionKey = findExecutionKeyForContext(contextId, startedAt, finishedAt);

            List<ArtifactEntity> persisted = artifactRepository.findByExecutionKeyOrderByArtifactKey(executionKey);
            assertThat(persisted).isNotEmpty();

            Set<String> keys = persisted.stream().map(ArtifactEntity::getArtifactKey).collect(java.util.stream.Collectors.toSet());

            ArtifactEntity root = persisted.stream()
                    .filter(entity -> entity.getParentKey() == null && "Execution".equals(entity.getArtifactType()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Execution root not persisted"));

            assertThat(root.getArtifactKey()).isEqualTo(executionKey);
            assertThat(artifactRepository.existsByArtifactKey(executionKey)).isTrue();

            // Validate tree structure: all children reference existing parents
            persisted.stream()
                    .filter(entity -> entity.getParentKey() != null)
                    .forEach(entity -> assertThat(keys).contains(entity.getParentKey()));

            // Validate expected artifacts exist and are nested under rendered prompt
            List<ArtifactEntity> renderedPrompts = persisted.stream()
                    .filter(entity -> "RenderedPrompt".equals(entity.getArtifactType()))
                    .toList();
            assertThat(renderedPrompts).isNotEmpty();

            Set<String> renderedPromptKeys = renderedPrompts.stream()
                    .map(ArtifactEntity::getArtifactKey)
                    .collect(java.util.stream.Collectors.toSet());

            List<ArtifactEntity> templateArtifacts = persisted.stream()
                    .filter(entity -> "PromptTemplateVersion".equals(entity.getArtifactType()))
                    .toList();
            assertThat(templateArtifacts).isNotEmpty();

            templateArtifacts.forEach(template ->
                    assertThat(renderedPromptKeys).contains(template.getParentKey()));

            // Clean up test artifacts to keep DB tidy
            artifactRepository.deleteByExecutionKey(executionKey);
        }

        private String findExecutionKeyForContext(String contextId, Instant startedAt, Instant finishedAt) {
            List<String> executionKeys = artifactRepository.findExecutionKeysBetween(
                    startedAt.minusSeconds(2),
                    finishedAt.plusSeconds(5)
            );

            return executionKeys.stream()
                    .filter(key -> artifactTreeBuilder.loadExecution(key)
                            .filter(artifact -> artifact instanceof Artifact.ExecutionArtifact exec
                                    && contextId.equals(exec.workflowRunId()))
                            .isPresent())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No execution persisted for workflow run id: " + contextId));
        }

        @Test
        void skipDiscovery_startAtPlanning() {
            log.error("Haven't implemented!");
        }

        @Test
        void planningOrchestrator_toContextManager_toTicketOrchestrator() {
            log.error("Haven't implemented!");
        }

    }

    @Nested
    class LoopingScenarios {

        @Test
        void discoveryOrchestrator_toContextManager_backToDiscoveryOrchestrator() {
            log.error("Haven't implemented");
        }

        @Test
        void discoveryCollector_loopsBackForMoreInvestigation() {
            var contextId = seedOrchestrator().value();

            initialOrchestratorToDiscovery("Needs more discovery");

            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Needs more discovery")
                                            .subdomainFocus("Initial")
                                            .build()
                            ))
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentRouting.builder()
                    .agentResult(AgentModels.DiscoveryAgentResult.builder()
                            .output("Initial findings - incomplete")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Needs more discovery")
                            .discoveryResults("initial")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                    .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                            .consolidatedOutput("Need more discovery")
                            .collectorDecision(AgentModels.CollectorDecision.builder()
                                    .decisionType(Events.CollectorDecisionType.ROUTE_BACK)
                                    .rationale("Loop back to discovery")
                                    .requestedPhase("DISCOVERY")
                                    .build())
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Needs more discovery")
                                            .subdomainFocus("Deeper")
                                            .build()
                            ))
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentRouting.builder()
                    .agentResult(AgentModels.DiscoveryAgentResult.builder()
                            .output("Deeper findings - complete")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Needs more discovery")
                            .discoveryResults("deeper")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                    .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                            .consolidatedOutput("Discovery complete")
                            .collectorDecision(AgentModels.CollectorDecision.builder()
                                    .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                    .rationale("Advance to planning")
                                    .requestedPhase("PLANNING")
                                    .build())
                            .build())
                    .build());

            enqueuePlanningToCompletion("Needs more discovery");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Needs more discovery", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();
            verify(workflowAgent, times(2)).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            verify(workflowAgent, times(2)).consolidateDiscoveryFindings(any(), any());
            verify(computationGraphOrchestrator, atLeastOnce()).emitStatusChangeEvent(any(), any(), any(), any());

            var ordered = inOrder(
                    workflowAgent,
                    discoveryDispatchSubagent,
                    planningDispatchSubagent,
                    ticketDispatchSubagent
            );
            ordered.verify(workflowAgent).coordinateWorkflow(any(), any());
            ordered.verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            ordered.verify(workflowAgent).dispatchDiscoveryAgentRequests(any(), any());
            ordered.verify(discoveryDispatchSubagent).runDiscoveryAgent(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).handleDiscoveryCollectorBranch(any(), any());
            ordered.verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            ordered.verify(workflowAgent).dispatchDiscoveryAgentRequests(any(), any());
            ordered.verify(discoveryDispatchSubagent).runDiscoveryAgent(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).handleDiscoveryCollectorBranch(any(), any());
            ordered.verify(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());
            ordered.verify(workflowAgent).dispatchPlanningAgentRequests(any(), any());
            ordered.verify(planningDispatchSubagent).runPlanningAgent(any(AgentModels.PlanningAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidatePlansIntoTickets(any(), any());
            ordered.verify(workflowAgent).handlePlanningCollectorBranch(any(), any());
            ordered.verify(workflowAgent).orchestrateTicketExecution(any(), any());
            ordered.verify(workflowAgent).dispatchTicketAgentRequests(any(), any());
            ordered.verify(ticketDispatchSubagent).runTicketAgent(any(AgentModels.TicketAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateTicketResults(any(), any());
            ordered.verify(workflowAgent).handleTicketCollectorBranch(any(), any());
            ordered.verify(workflowAgent).consolidateWorkflowOutputs(any(), any());
        }

        @Test
        void planningCollector_loopsBackToDiscovery_needsMoreContext() {
            var contextId = seedOrchestrator().value();

            initialOrchestratorToDiscovery("Incomplete context");

            discoveryOnly("Do a goal");

            queuedLlmRunner.enqueue(AgentModels.PlanningOrchestratorRouting.builder()
                    .agentRequests(AgentModels.PlanningAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.PlanningAgentRequest.builder()
                                            .goal("Incomplete context")
                                            .build()
                            ))
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.PlanningAgentRouting.builder()
                    .agentResult(AgentModels.PlanningAgentResult.builder()
                            .output("Plan missing context")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.PlanningAgentDispatchRouting.builder()
                    .planningCollectorRequest(AgentModels.PlanningCollectorRequest.builder()
                            .goal("Incomplete context")
                            .planningResults("plan")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.PlanningCollectorRouting.builder()
                    .discoveryOrchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                            .goal("Incomplete context")
                            .build())
                    .build());


            enqueueDiscoveryToEnd("This goal.");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Incomplete context", "PLANNING"))
            );

            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);

            queuedLlmRunner.assertAllConsumed();

            verify(workflowAgent, times(2)).decomposePlanAndCreateWorkItems(any(), any());
            verify(workflowAgent, times(2)).consolidatePlansIntoTickets(any(), any());
            verify(workflowAgent, times(2)).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            verify(computationGraphOrchestrator, atLeastOnce()).emitStatusChangeEvent(any(), any(), any(), any());

            var ordered = inOrder(
                    workflowAgent,
                    discoveryDispatchSubagent,
                    planningDispatchSubagent,
                    ticketDispatchSubagent
            );

            ordered.verify(workflowAgent).coordinateWorkflow(any(), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).handleDiscoveryCollectorBranch(any(), any());
            ordered.verify(workflowAgent).dispatchPlanningAgentRequests(any(), any());
            ordered.verify(planningDispatchSubagent).runPlanningAgent(any(AgentModels.PlanningAgentRequest.class), any());
            ordered.verify(workflowAgent).dispatchDiscoveryAgentRequests(any(), any());
            ordered.verify(discoveryDispatchSubagent).runDiscoveryAgent(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).handlePlanningCollectorBranch(any(), any());
            ordered.verify(workflowAgent).orchestrateTicketExecution(any(), any());
            ordered.verify(workflowAgent).dispatchTicketAgentRequests(any(), any());
            ordered.verify(ticketDispatchSubagent).runTicketAgent(any(AgentModels.TicketAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateTicketResults(any(), any());
            ordered.verify(workflowAgent).handleTicketCollectorBranch(any(), any());
            ordered.verify(workflowAgent).consolidateWorkflowOutputs(any(), any());
        }

//        @Test
        void orchestratorCollector_loopsBackMultipleTimes() {
        }
    }

    private com.embabel.agent.core.Agent findWorkflowAgent() {
        return agentPlatform.agents().stream()
                .filter(a -> a.getName().equals(AgentInterfaces.WORKFLOW_AGENT_NAME)
                        || a.getName().contains(AgentInterfaces.WorkflowAgent.class.getSimpleName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("WorkflowAgent not found"));
    }

    private void enqueueHappyPath(String goal) {
        initialOrchestratorToDiscovery(goal);
        enqueueDiscoveryToEnd(goal);
    }

    private void enqueueDiscoveryToEnd(String goal) {
        discoveryOnly(goal);
        enqueuePlanningToCompletion(goal);
    }

    private void initialOrchestratorToDiscovery(String goal) {
        queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                .orchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                        .goal(goal)
                        .build())
                .build());
    }

    private void discoveryOnly(String goal) {
        discoveryOnly(goal, AgentModels.DiscoveryCollectorRouting.builder()
                .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                        .consolidatedOutput("Discovery complete")
                        .collectorDecision(AgentModels.CollectorDecision.builder()
                                .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                .rationale("Advance to planning")
                                .requestedPhase("PLANNING")
                                .build())
                        .build())
                .build());
    }

    private void discoveryOnly(String goal, AgentModels.DiscoveryCollectorRouting build) {
        queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                        .requests(List.of(
                                AgentModels.DiscoveryAgentRequest.builder()
                                        .goal(goal)
                                        .subdomainFocus("Primary")
                                        .build()
                        ))
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentRouting.builder()
                .agentResult(AgentModels.DiscoveryAgentResult.builder()
                        .output("Found stuff")
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                        .goal(goal)
                        .discoveryResults("discovery-results")
                        .build())
                .build());

        queuedLlmRunner.enqueue(build);
    }

    private void enqueuePlanningToCompletion(String goal) {
        planningOnly(goal);

        ticketsOnly(goal);

        finalOrchestratorCollector();
    }

    private void finalOrchestratorCollector() {
        queuedLlmRunner.enqueue(AgentModels.OrchestratorCollectorRouting.builder()
                .collectorResult(AgentModels.OrchestratorCollectorResult.builder()
                        .consolidatedOutput("Workflow complete")
                        .collectorDecision(AgentModels.CollectorDecision.builder()
                                .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                .rationale("All phases done")
                                .requestedPhase("COMPLETE")
                                .build())
                        .build())
                .build());
    }

    private void ticketsOnly(String goal) {
        queuedLlmRunner.enqueue(AgentModels.TicketOrchestratorRouting.builder()
                .agentRequests(AgentModels.TicketAgentRequests.builder()
                        .requests(List.of(
                                AgentModels.TicketAgentRequest.builder()
                                        .ticketDetails(goal)
                                        .ticketDetailsFilePath("ticket-1.md")
                                        .build()
                        ))
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.TicketAgentRouting.builder()
                .agentResult(AgentModels.TicketAgentResult.builder()
                        .output("Ticket output")
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.TicketAgentDispatchRouting.builder()
                .ticketCollectorRequest(AgentModels.TicketCollectorRequest.builder()
                        .goal(goal)
                        .ticketResults("ticket-results")
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.TicketCollectorRouting.builder()
                .collectorResult(AgentModels.TicketCollectorResult.builder()
                        .consolidatedOutput("Tickets complete")
                        .collectorDecision(AgentModels.CollectorDecision.builder()
                                .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                .rationale("Advance to orchestrator collector")
                                .requestedPhase("COMPLETE")
                                .build())
                        .build())
                .build());
    }

    private void planningOnly(String goal) {
        queuedLlmRunner.enqueue(AgentModels.PlanningOrchestratorRouting.builder()
                .agentRequests(AgentModels.PlanningAgentRequests.builder()
                        .requests(List.of(
                                AgentModels.PlanningAgentRequest.builder()
                                        .goal(goal)
                                        .build()
                        ))
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.PlanningAgentRouting.builder()
                .agentResult(AgentModels.PlanningAgentResult.builder()
                        .output("Plan output")
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.PlanningAgentDispatchRouting.builder()
                .planningCollectorRequest(AgentModels.PlanningCollectorRequest.builder()
                        .goal(goal)
                        .planningResults("planning-results")
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.PlanningCollectorRouting.builder()
                .collectorResult(AgentModels.PlanningCollectorResult.builder()
                        .consolidatedOutput("Planning complete")
                        .collectorDecision(AgentModels.CollectorDecision.builder()
                                .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                .rationale("Advance to tickets")
                                .requestedPhase("TICKETS")
                                .build())
                        .build())
                .build());
    }

    private ArtifactKey seedOrchestrator() {
        var c = ArtifactKey.createRoot();
        MainWorktreeContext mainWorktree = MainWorktreeContext.builder()
                .worktreeId("wt")
                .repositoryUrl("git@github.com:haydenrear/multi_agent_ide_java_parent.git")
                .parentWorktreeId("wt")
                .worktreePath(path)
                .status(WorktreeContext.WorktreeStatus.ACTIVE)
                .submoduleWorktrees(new ArrayList<>())
                .build();
        graphRepository.save(createMockOrchestratorNode(c.value(), mainWorktree));
        worktreeRepository.save(mainWorktree);
        return c;
    }

    private OrchestratorNode createMockOrchestratorNode(String nodeId, MainWorktreeContext mainWorktree) {
        return OrchestratorNode.builder()
                .nodeId(nodeId)
                .title("Orch")
                .goal("goal")
                .status(Events.NodeStatus.RUNNING)
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .worktreeContext(mainWorktree)
                .build();
    }

}
