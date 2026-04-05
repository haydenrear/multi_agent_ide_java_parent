package com.hayden.multiagentide.integration;

import com.embabel.agent.api.annotation.support.ActionQosProvider;
import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.ActionQos;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.WorkflowGraphService;
import com.hayden.multiagentide.artifacts.ArtifactEventListener;
import com.hayden.multiagentide.artifacts.ExecutionScopeService;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import com.hayden.multiagentide.filter.repository.FilterDecisionRecordRepository;
import com.hayden.multiagentide.filter.repository.LayerRepository;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationRepository;
import com.hayden.multiagentide.filter.service.LayerHierarchyBootstrap;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.orchestration.ComputationGraphOrchestrator;
import com.hayden.multiagentide.propagation.repository.*;
import com.hayden.multiagentide.propagation.service.AutoAiPropagatorBootstrap;
import com.hayden.multiagentide.propagation.service.PropagatorRegistrationService;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.multiagentide.service.*;
import com.hayden.multiagentide.support.AgentTestBase;
import com.hayden.multiagentide.support.QueuedChatModel;
import com.hayden.multiagentide.support.TestEventListener;
import com.hayden.multiagentide.transformation.repository.TransformationRecordRepository;
import com.hayden.multiagentide.transformation.repository.TransformerRegistrationRepository;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.CompactionException;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.model.nodes.OrchestratorNode;
import com.hayden.multiagentide.model.worktree.MainWorktreeContext;
import com.hayden.multiagentide.model.worktree.WorktreeContext;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.hayden.multiagentide.support.TestTraceWriter;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;

/**
 * Integration tests that mock at the ChatModel (AcpChatModel) layer
 * instead of at the LlmRunner layer.
 *
 * This exercises the full pipeline:
 *   AgentExecutor → DefaultLlmRunner → embabel framework → ChatModel.call()
 *
 * By queuing responses and errors on QueuedChatModel, we validate that:
 * - Exceptions from ChatModel propagate correctly through the framework retry
 * - ActionRetryListenerImpl classifies errors onto BlackboardHistory
 * - The workflow recovers on retry
 */
@Slf4j
@SpringBootTest
@ActiveProfiles({"test", "testdocker"})
class WorkflowAgentAcpChatModelTest extends AgentTestBase {

    @Autowired
    private AgentPlatform agentPlatform;

    @Autowired
    private QueuedChatModel queuedChatModel;

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
    private FilterDecisionRecordRepository filterDecisionRecordRepository;

    @Autowired
    private PolicyRegistrationRepository policyRegistrationRepository;

    @Autowired
    private LayerRepository layerRepository;

    @Autowired
    private PropagationRecordRepository propagationRecordRepository;

    @Autowired
    private PropagationItemRepository propagationItemRepository;

    @Autowired
    private PropagatorRegistrationRepository propagatorRegistrationRepository;

    @Autowired
    private AutoAiPropagatorBootstrap autoAiPropagatorBootstrap;

    @Autowired
    private LayerHierarchyBootstrap layerHierarchyBootstrap;

    @Autowired
    private TransformationRecordRepository transformationRecordRepository;

    @Autowired
    private TransformerRegistrationRepository transformerRegistrationRepository;

    @MockitoSpyBean
    private EventBus eventBus;

    @MockitoSpyBean
    private ArtifactEventListener artifactEventListener;

    @MockitoSpyBean
    private ExecutionScopeService executionScopeService;

    @MockitoSpyBean
    private WorkflowGraphService workflowGraphService;

    @MockitoSpyBean
    private ComputationGraphOrchestrator computationGraphOrchestrator;

    @MockitoSpyBean
    private AgentCommunicationService agentCommunicationService;

    @Autowired
    private AgentExecutor agentExecutor;

    @TempDir
    Path path;

    @Autowired
    private PropagatorRegistrationService propagatorRegistrationService;

    private static final Path TEST_WORK_DIR = Path.of("test_work/chatmodel");

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        ChatModel chatModel() {
            return new QueuedChatModel();
        }

        @Bean
        QueuedChatModel queuedChatModel(ChatModel chatModel) {
            return (QueuedChatModel) chatModel;
        }

        @Bean
        TestEventListener testEventListener() {
            return new TestEventListener();
        }

        @Bean @Primary
        ActionQosProvider manager() {
            return new ActionQosProvider() {
                @Override
                public @NonNull ActionQos provideActionQos(@NonNull Method method, @NonNull Object instance) {
                    return new ActionQos(2, 50, 2, 60, false);
                }
            };
        }
    }

    @BeforeEach
    void setUp() {
        testEventListener.clear();
        queuedChatModel.clear();
        graphRepository.clear();
        worktreeRepository.clear();
        filterDecisionRecordRepository.deleteAll();
        policyRegistrationRepository.deleteAll();
        layerRepository.deleteAll();
        propagationRecordRepository.deleteAll();
        propagationItemRepository.deleteAll();
        propagatorRegistrationRepository.deleteAll();
        transformationRecordRepository.deleteAll();
        transformerRegistrationRepository.deleteAll();
        layerHierarchyBootstrap.seedLayersIfAbsent();
        autoAiPropagatorBootstrap.seedAutoAiPropagators();
        reset(
                workflowGraphService,
                computationGraphOrchestrator,
                worktreeService,
                eventBus,
                agentCommunicationService
        );
        Mockito.doThrow(new RuntimeException("worktree disabled"))
                .when(worktreeService)
                .branchWorktree(any(), any(), any());

        Mockito.when(worktreeService.attachWorktreesToDiscoveryRequests(any(AgentModels.DiscoveryAgentRequests.class), anyString()))
                .thenAnswer(inv -> {
                    AgentModels.DiscoveryAgentRequests d = inv.getArgument(0);
                    return d.toBuilder()
                            .requests(d.requests().stream().map(dar -> dar.withWorktreeContext(d.worktreeContext())).toList())
                            .build();
                });
        Mockito.when(worktreeService.attachWorktreesToPlanningRequests(any(AgentModels.PlanningAgentRequests.class), anyString()))
                .thenAnswer(inv -> {
                    AgentModels.PlanningAgentRequests d = inv.getArgument(0);
                    return d.toBuilder()
                            .requests(d.requests().stream().map(dar -> dar.withWorktreeContext(d.worktreeContext())).toList())
                            .build();
                });
        Mockito.when(worktreeService.attachWorktreesToTicketRequests(any(AgentModels.TicketAgentRequests.class), anyString()))
                .thenAnswer(inv -> {
                    AgentModels.TicketAgentRequests d = inv.getArgument(0);
                    return d.toBuilder()
                            .requests(d.requests().stream().map(dar -> dar.withWorktreeContext(d.worktreeContext())).toList())
                            .build();
                });

        artifactRepository.deleteAll();
        artifactRepository.flush();
    }

    private void setLogFile(String testName) {
        Path file = TEST_WORK_DIR.resolve(testName + ".md");
        Path graphFile = TEST_WORK_DIR.resolve(testName + ".graph.md");
        Path eventFile = TEST_WORK_DIR.resolve(testName + ".events.md");
        try {
            java.nio.file.Files.createDirectories(TEST_WORK_DIR);
            java.nio.file.Files.deleteIfExists(file);
            java.nio.file.Files.deleteIfExists(graphFile);
            java.nio.file.Files.deleteIfExists(eventFile);
        } catch (Exception e) {
            // ignore cleanup failures
        }
        queuedChatModel.setLogFile(file);
        queuedChatModel.setTestClassName(WorkflowAgentAcpChatModelTest.class.getSimpleName());
        queuedChatModel.setTestMethodName(testName);

        var traceWriter = new TestTraceWriter();
        traceWriter.setGraphLogFile(graphFile);
        traceWriter.setEventLogFile(eventFile);
        traceWriter.setTestClassName(WorkflowAgentAcpChatModelTest.class.getSimpleName());
        traceWriter.setTestMethodName(testName);
        queuedChatModel.setTraceWriter(traceWriter);
        queuedChatModel.setGraphRepository(graphRepository);
        testEventListener.setTraceWriter(traceWriter);
    }

    @Nested
    class ChatModelRetryScenarios {

        /**
         * CompactionException thrown from ChatModel propagates through
         * DefaultLlmRunner → embabel framework retry → ActionRetryListenerImpl
         * classifies as CompactionError. Retry succeeds with the next queued response.
         */
        @Test
        void retry_compactionFromChatModel_recoversOnRetry() {
            setLogFile("retry_compactionFromChatModel_recoversOnRetry");
            var contextId = seedOrchestrator().value();

            // First ChatModel.call(): throw CompactionException
            queuedChatModel.enqueueError(
                    new CompactionException("Session is compacting", contextId));

            // Retry + rest of happy path
            enqueueHappyPath("Implement auth");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Implement auth", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);

            // Verify retry happened: more starts than completes
            var startEvents = testEventListener.eventsOfType(Events.AgentExecutorStartEvent.class);
            var completeEvents = testEventListener.eventsOfType(Events.AgentExecutorCompleteEvent.class);
            assertThat(startEvents.size())
                    .as("One extra start from the failed attempt")
                    .isGreaterThan(completeEvents.size());
        }

        /**
         * ParseError thrown from ChatModel (RuntimeException with "parse" in message)
         * propagates through the full pipeline. Retry succeeds.
         */
        @Test
        void retry_parseErrorFromChatModel_recoversOnRetry() {
            setLogFile("retry_parseErrorFromChatModel_recoversOnRetry");
            var contextId = seedOrchestrator().value();

            // First ChatModel.call(): throw parse error
            queuedChatModel.enqueueError(
                    new RuntimeException("Failed to parse JSON response from LLM"));

            // Retry + rest of happy path
            enqueueHappyPath("Implement auth");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Implement auth", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────

    private com.embabel.agent.core.Agent findWorkflowAgent() {
        return agentPlatform.agents().stream()
                .filter(a -> a.getName().equals(AgentInterfaces.WORKFLOW_AGENT_NAME)
                        || a.getName().contains(AgentInterfaces.WorkflowAgent.class.getSimpleName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("WorkflowAgent not found"));
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

    private void enqueueHappyPath(String goal) {
        initialOrchestratorToDiscovery(goal);
        enqueueDiscoveryToEnd(goal);
    }

    private void initialOrchestratorToDiscovery(String goal) {
        queuedChatModel.enqueue(AgentModels.OrchestratorRouting.builder()
                .discoveryOrchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                        .goal(goal)
                        .build())
                .build());
    }

    private void enqueueDiscoveryToEnd(String goal) {
        discoveryOnly(goal);
        enqueuePlanningToCompletion(goal);
    }

    private void discoveryOnly(String goal) {
        queuedChatModel.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                        .requests(List.of(
                                AgentModels.DiscoveryAgentRequest.builder()
                                        .goal(goal)
                                        .subdomainFocus("Primary")
                                        .build()
                        ))
                        .build())
                .build());

        queuedChatModel.enqueue(AgentModels.DiscoveryAgentRouting.builder()
                .agentResult(AgentModels.DiscoveryAgentResult.builder()
                        .output("Found stuff")
                        .build())
                .build());

        queuedChatModel.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                        .goal(goal)
                        .discoveryResults("discovery-results")
                        .build())
                .build());

        queuedChatModel.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                        .consolidatedOutput("Discovery complete")
                        .build())
                .build());
    }

    private void enqueuePlanningToCompletion(String goal) {
        planningOnly(goal);
        ticketsOnly(goal);
        finalOrchestratorCollector();
    }

    private void planningOnly(String goal) {
        queuedChatModel.enqueue(AgentModels.PlanningOrchestratorRouting.builder()
                .agentRequests(AgentModels.PlanningAgentRequests.builder()
                        .requests(List.of(
                                AgentModels.PlanningAgentRequest.builder()
                                        .goal(goal)
                                        .build()
                        ))
                        .build())
                .build());

        queuedChatModel.enqueue(AgentModels.PlanningAgentRouting.builder()
                .agentResult(AgentModels.PlanningAgentResult.builder()
                        .output("Plan output")
                        .build())
                .build());

        queuedChatModel.enqueue(AgentModels.PlanningAgentDispatchRouting.builder()
                .planningCollectorRequest(AgentModels.PlanningCollectorRequest.builder()
                        .goal(goal)
                        .planningResults("planning-results")
                        .build())
                .build());

        queuedChatModel.enqueue(AgentModels.PlanningCollectorRouting.builder()
                .collectorResult(AgentModels.PlanningCollectorResult.builder()
                        .consolidatedOutput("Planning complete")
                        .build())
                .build());
    }

    private void ticketsOnly(String goal) {
        queuedChatModel.enqueue(AgentModels.TicketOrchestratorRouting.builder()
                .agentRequests(AgentModels.TicketAgentRequests.builder()
                        .requests(List.of(
                                AgentModels.TicketAgentRequest.builder()
                                        .ticketDetails(goal)
                                        .ticketDetailsFilePath("ticket-1.md")
                                        .build()
                        ))
                        .build())
                .build());

        queuedChatModel.enqueue(AgentModels.TicketAgentRouting.builder()
                .agentResult(AgentModels.TicketAgentResult.builder()
                        .output("Ticket output")
                        .build())
                .build());

        queuedChatModel.enqueue(AgentModels.TicketAgentDispatchRouting.builder()
                .ticketCollectorRequest(AgentModels.TicketCollectorRequest.builder()
                        .goal(goal)
                        .ticketResults("ticket-results")
                        .build())
                .build());

        queuedChatModel.enqueue(AgentModels.TicketCollectorRouting.builder()
                .collectorResult(AgentModels.TicketCollectorResult.builder()
                        .consolidatedOutput("Tickets complete")
                        .build())
                .build());
    }

    private void finalOrchestratorCollector() {
        queuedChatModel.enqueue(AgentModels.OrchestratorCollectorRouting.builder()
                .collectorResult(AgentModels.OrchestratorCollectorResult.builder()
                        .consolidatedOutput("Workflow complete")
                        .build())
                .build());
    }
}
