package com.hayden.multiagentide.agent;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ProcessOptions;
import com.hayden.multiagentide.service.InterruptService;
import com.hayden.multiagentide.support.AgentTestBase;
import com.hayden.multiagentide.support.TestEventListener;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.utilitymodule.acp.events.EventBus;
import com.hayden.utilitymodule.acp.events.Events;
import com.hayden.multiagentidelib.model.nodes.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.hayden.multiagentidelib.agent.BlackboardHistory.registerAndHideInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for WorkflowAgent that mock the action methods directly.
 *
 * This allows testing complex routing scenarios, interrupts, loops, and edge cases
 * without needing to craft JSON responses from the LLM.
 *
 * We spy on the WorkflowAgent and stub the action methods to return specific routing decisions.
 */
@SpringBootTest
class WorkflowAgentActionMockTest extends AgentTestBase {

    @Autowired
    private AgentPlatform agentPlatform;

    @MockitoSpyBean
    private AgentInterfaces.WorkflowAgent workflowAgent;

    @MockitoSpyBean
    private AgentInterfaces.WorkflowAgent.DiscoveryDispatchSubagent discoveryDispatchSubagent;

    @MockitoSpyBean
    private AgentInterfaces.WorkflowAgent.PlanningDispatchSubagent planningDispatchSubagent;

    @MockitoSpyBean
    private AgentInterfaces.WorkflowAgent.TicketDispatchSubagent ticketDispatchSubagent;

    @MockitoBean
    private WorkflowGraphService workflowGraphService;

    @MockitoBean
    private InterruptService interruptService;

    @MockitoBean
    private EventBus eventBus;

    @Autowired
    private TestEventListener testEventListener;

    @TestConfiguration
    static class TestConfig {
        @Bean
        TestEventListener testEventListener() {
            return new TestEventListener();
        }

    }

    @BeforeEach
    void setUp() {
        testEventListener.clear();
        reset(workflowAgent, discoveryDispatchSubagent, planningDispatchSubagent, ticketDispatchSubagent,
              workflowGraphService, interruptService, eventBus);

        // Setup default mock returns for graph service
        when(workflowGraphService.startOrchestrator(any())).thenReturn(createMockOrchestratorNode());
        when(workflowGraphService.startOrchestratorCollector(any(), any())).thenReturn(createMockCollectorNode());
        when(workflowGraphService.startDiscoveryOrchestrator(any(), any())).thenReturn(createMockDiscoveryOrchestratorNode());
        when(workflowGraphService.startDiscoveryCollector(any(), any())).thenReturn(createMockDiscoveryCollectorNode());
        when(workflowGraphService.startPlanningOrchestrator(any(), any())).thenReturn(createMockPlanningOrchestratorNode());
        when(workflowGraphService.startPlanningCollector(any(), any())).thenReturn(createMockPlanningCollectorNode());
        when(workflowGraphService.startTicketOrchestrator(any(), any())).thenReturn(createMockTicketOrchestratorNode());
        when(workflowGraphService.startTicketCollector(any(), any())).thenReturn(createMockTicketCollectorNode());
        when(workflowGraphService.requireDiscoveryOrchestrator(any())).thenReturn(createMockDiscoveryOrchestratorNode());
        when(workflowGraphService.requirePlanningOrchestrator(any())).thenReturn(createMockPlanningOrchestratorNode());
        when(workflowGraphService.requireTicketOrchestrator(any())).thenReturn(createMockTicketOrchestratorNode());
        when(workflowGraphService.startDiscoveryAgent(any(), any(), any())).thenReturn(createMockDiscoveryNode());
        when(workflowGraphService.startPlanningAgent(any(), any(), any())).thenReturn(createMockPlanningNode());
        when(workflowGraphService.startTicketAgent(any(), any(), anyInt())).thenReturn(createMockTicketNode());
//        when(workflowGraphService.startReviewNode(any())).thenReturn(createMockReviewNode());
//        when(workflowGraphService.startMergeNode(any())).thenReturn(createMockMergeNode());
        // Orchestrator collector completes
        doReturn(AgentModels.OrchestratorCollectorResult.builder()
                        .consolidatedOutput("All done")
                        .collectorDecision(AgentModels.CollectorDecision.builder()
                                .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                .rationale("Complete")
                                .requestedPhase("COMPLETE")
                                .build())
                        .build()).when(workflowAgent).finalCollectorResult(
                Mockito.any(AgentModels.OrchestratorCollectorResult.class),
                any()
        );
    }

    @Nested
    class HappyPathWorkflows {

        @Test
        void fullWorkflow_discoveryToPlanningSingleAgentsToCompletion() {
            // 1. Orchestrator routes to collector
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorRouting.builder()
                        .collectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                                .goal("Implement auth")
                                .phase("DISCOVERY")
                                .build())
                        .build();
            }).when(workflowAgent).coordinateWorkflow(any(), any());

            // 2. Collector routes to Discovery
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .discoveryRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                                .goal("Implement auth")
                                .build())
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.argThat(req -> "DISCOVERY".equals(req.phase())),
                    any()
            );

            // 3. Discovery orchestrator creates single agent request
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryOrchestratorRouting.builder()
                        .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                                .requests(List.of(
                                        AgentModels.DiscoveryAgentRequest.builder()
                                                .goal("Implement auth")
                                                .subdomainFocus("Auth modules")
                                                .build()
                                ))
                                .build())
                        .build();
            }).when(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());

            // 4. Discovery subagent returns output
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                .output("Found AuthService, UserService")
                                .build())
                        .build();
            }).when(discoveryDispatchSubagent).run(any(AgentModels.DiscoveryAgentRequest.class), any());

            // 5. Discovery dispatch routes to collector
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentDispatchRouting.builder()
                        .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                                .goal("Implement auth")
                                .discoveryResults("All findings")
                                .build())
                        .build();
            }).when(workflowAgent).dispatchDiscoveryAgentRequests(any(AgentModels.DiscoveryAgentRequests.class), any());

            // 6. Discovery collector routes to Planning
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryCollectorRouting.builder()
                        .planningRequest(AgentModels.PlanningOrchestratorRequest.builder()
                                .goal("Implement auth")
                                .build())
                        .build();
            }).when(workflowAgent).consolidateDiscoveryFindings(any(AgentModels.DiscoveryCollectorRequest.class), any());

            // 7. Planning orchestrator creates single agent request
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningOrchestratorRouting.builder()
                        .agentRequests(AgentModels.PlanningAgentRequests.builder()
                                .requests(List.of(
                                        AgentModels.PlanningAgentRequest.builder()
                                                .goal("Implement auth")
                                                .build()
                                ))
                                .build())
                        .build();
            }).when(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());

            // 8. Planning subagent returns output
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningAgentRouting.builder()
                        .agentResult(AgentModels.PlanningAgentResult.builder()
                                .output("Plan: Add JWT, Update UserService")
                                .build())
                        .build();
            }).when(planningDispatchSubagent).run(any(AgentModels.PlanningAgentRequest.class), any());

            // 9. Planning dispatch routes to collector
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningAgentDispatchRouting.builder()
                        .planningCollectorRequest(AgentModels.PlanningCollectorRequest.builder()
                                .goal("Implement auth")
                                .planningResults("All plans")
                                .build())
                        .build();
            }).when(workflowAgent).dispatchPlanningAgentRequests(any(AgentModels.PlanningAgentRequests.class), any());

            // 10. Planning collector routes back to orchestrator collector with output
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningCollectorRouting.builder()
                        .collectorResult(AgentModels.PlanningCollectorResult.builder()
                                .consolidatedOutput("Plans consolidated")
                                .collectorDecision(AgentModels.CollectorDecision.builder()
                                        .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                        .rationale("Planning complete")
                                        .requestedPhase("COMPLETE")
                                        .build())
                                .build())
                        .build();
            }).when(workflowAgent).consolidatePlansIntoTickets(any(AgentModels.PlanningCollectorRequest.class), any());

            // Mock ticket orchestration
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketOrchestratorRouting.builder()
                        .collectorRequest(new AgentModels.TicketCollectorRequest("Implement auth", "output"))
                        .build();
            }).when(workflowAgent).orchestrateTicketExecution(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketCollectorRouting.builder()
                        .orchestratorCollectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                                .goal("Implement auth")
                                .phase("COMPLETE")
                                .build())
                        .build();
            }).when(workflowAgent).consolidateTicketResults(any(), any());

            doAnswer(inv -> {
                return inv.getArgument(0);
            }).when(workflowAgent).finalCollectorResult(any(), any());

            // 11. Orchestrator collector returns final output
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .collectorResult(AgentModels.OrchestratorCollectorResult.builder()
                                .consolidatedOutput("Workflow complete")
                                .collectorDecision(AgentModels.CollectorDecision.builder()
                                        .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                        .rationale("All phases done")
                                        .requestedPhase("COMPLETE")
                                        .build())
                                .build())
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.<AgentModels.OrchestratorCollectorRequest>argThat(req -> "COMPLETE".equals(req.phase())),
                    any()
            );

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-full-workflow").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Implement auth", "DISCOVERY"))
            );

            // Assert
            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);

            // Verify action methods called in correct order
            verify(workflowAgent).coordinateWorkflow(any(), any());
            verify(workflowAgent, atLeastOnce()).consolidateWorkflowOutputs(
                    any(AgentModels.OrchestratorCollectorRequest.class),
                    any()
            );
            verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            verify(workflowAgent).dispatchDiscoveryAgentRequests(any(), any());
            verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            verify(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());
            verify(workflowAgent).dispatchPlanningAgentRequests(any(), any());
            verify(workflowAgent).consolidatePlansIntoTickets(any(), any());

            // Verify graph service calls
//            verify(workflowGraphService).startOrchestrator(any());
//            verify(workflowGraphService).startDiscoveryOrchestrator(any(), any());
//            verify(workflowGraphService).startDiscoveryAgent(any(), any(), any());
//            verify(workflowGraphService).startDiscoveryCollector(any(), any());
//            verify(workflowGraphService).startPlanningOrchestrator(any(), any());
//            verify(workflowGraphService).startPlanningAgent(any(), any(), any());
//            verify(workflowGraphService).startPlanningCollector(any(), any());
        }

        @Test
        void skipDiscovery_startAtPlanning() {
            // 1. Orchestrator routes to collector with PLANNING phase
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorRouting.builder()
                        .collectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                                .goal("Quick task")
                                .phase("PLANNING")
                                .build())
                        .build();
            }).when(workflowAgent).coordinateWorkflow(any(), any());

            // 2. Collector routes directly to Planning (skips Discovery)
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .planningRequest(AgentModels.PlanningOrchestratorRequest.builder()
                                .goal("Quick task")
                                .build())
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.argThat(req -> "PLANNING".equals(req.phase())),
                    any()
            );

            // 3. Planning orchestrator creates request
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningOrchestratorRouting.builder()
                        .agentRequests(AgentModels.PlanningAgentRequests.builder()
                                .requests(List.of(
                                        AgentModels.PlanningAgentRequest.builder()
                                                .goal("Quick task")
                                                .build()
                                ))
                                .build())
                        .build();
            }).when(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());

            // 4. Planning subagent returns output
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningAgentRouting.builder()
                        .agentResult(AgentModels.PlanningAgentResult.builder()
                                .output("Simple plan")
                                .build())
                        .build();
            }).when(planningDispatchSubagent).run(any(AgentModels.PlanningAgentRequest.class), any());

            // 5. Planning dispatch routes to collector
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningAgentDispatchRouting.builder()
                        .planningCollectorRequest(AgentModels.PlanningCollectorRequest.builder()
                                .goal("Quick task")
                                .planningResults("plan")
                                .build())
                        .build();
            }).when(workflowAgent).dispatchPlanningAgentRequests(any(AgentModels.PlanningAgentRequests.class), any());

            // 6. Planning collector completes
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningCollectorRouting.builder()
                        .collectorResult(AgentModels.PlanningCollectorResult.builder()
                                .consolidatedOutput("Done")
                                .collectorDecision(AgentModels.CollectorDecision.builder()
                                        .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                        .rationale("Complete")
                                        .requestedPhase("COMPLETE")
                                        .build())
                                .build())
                        .build();
            }).when(workflowAgent).consolidatePlansIntoTickets(any(AgentModels.PlanningCollectorRequest.class), any());

            // Mock ticket orchestration
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketOrchestratorRouting.builder()
                        .collectorRequest(new AgentModels.TicketCollectorRequest("Quick task", "output"))
                        .build();
            }).when(workflowAgent).orchestrateTicketExecution(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketCollectorRouting.builder()
                        .orchestratorCollectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                                .goal("Quick task")
                                .phase("COMPLETE")
                                .build())
                        .build();
            }).when(workflowAgent).consolidateTicketResults(any(), any());

            doAnswer(inv -> {
                return inv.getArgument(0);
            }).when(workflowAgent).finalCollectorResult(any(), any());

            // 7. Orchestrator collector completes
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .collectorResult(AgentModels.OrchestratorCollectorResult.builder()
                                .consolidatedOutput("Complete")
                                .collectorDecision(AgentModels.CollectorDecision.builder()
                                        .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                        .rationale("Done")
                                        .requestedPhase("COMPLETE")
                                        .build())
                                .build())
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.<AgentModels.OrchestratorCollectorRequest>argThat(req -> "COMPLETE".equals(req.phase())),
                    any()
            );

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-skip-discovery").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Quick task", "PLANNING"))
            );

            // Assert
            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);

            // Verify Discovery was skipped
            verify(workflowAgent, never()).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());

            // Verify Planning was executed
            verify(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());
//            verify(discoveryDispatchSubagent, never()).run(any(AgentModels.DiscoveryAgentRequest.class), any());
//            verify(workflowGraphService, never()).startDiscoveryOrchestrator(any(), any());
//            verify(workflowGraphService).startPlanningOrchestrator(any(), any());
        }

//        @Test
        void skipToTickets_directlyFromOrchestrator() {
            // 1. Orchestrator routes to collector with TICKETS phase
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorRouting.builder()
                        .collectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                                .goal("Direct to tickets")
                                .phase("TICKETS")
                                .build())
                        .build();
            }).when(workflowAgent).coordinateWorkflow(any(), any());

            // 2. Collector routes directly to Tickets
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .ticketRequest(new AgentModels.TicketOrchestratorRequest("Direct to tickets"))
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.<AgentModels.OrchestratorCollectorRequest>argThat(req -> "TICKETS".equals(req.phase())),
                    any()
            );

            // 3. Ticket orchestrator creates requests
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketOrchestratorRouting.builder()
                        .agentRequests(AgentModels.TicketAgentRequests.builder()
                                .requests(List.of(new AgentModels.TicketAgentRequest("Direct to tickets", "")))
                                .build())
                        .build();
            }).when(workflowAgent).orchestrateTicketExecution(any(AgentModels.TicketOrchestratorRequest.class), any());

            // 4. Ticket subagent returns output
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketAgentRouting.builder()
                        .agentResult(AgentModels.TicketAgentResult.builder()
                                .output("Ticket completed")
                                .build())
                        .build();
            }).when(ticketDispatchSubagent).run(any(AgentModels.TicketAgentRequest.class), any());

            // 5. Ticket dispatch routes to collector
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketAgentDispatchRouting.builder()
                        .ticketCollectorRequest(AgentModels.TicketCollectorRequest.builder()
                                .goal("Direct to tickets")
                                .ticketResults("outputs")
                                .build())
                        .build();
            }).when(workflowAgent).dispatchTicketAgentRequests(any(AgentModels.TicketAgentRequests.class), any());

            // 6. Ticket collector completes
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketCollectorRouting.builder()
                        .collectorResult(AgentModels.TicketCollectorResult.builder()
                                .consolidatedOutput("Tickets done")
                                .collectorDecision(AgentModels.CollectorDecision.builder()
                                        .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                        .rationale("Complete")
                                        .requestedPhase("COMPLETE")
                                        .build())
                                .build())
                        .build();
            }).when(workflowAgent).consolidateTicketResults(any(AgentModels.TicketCollectorRequest.class), any());

            // Mock ticket orchestration
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketOrchestratorRouting.builder()
                        .collectorRequest(new AgentModels.TicketCollectorRequest("Direct to tickets", "output"))
                        .build();
            }).when(workflowAgent).orchestrateTicketExecution(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketCollectorRouting.builder()
                        .orchestratorCollectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                                .goal("Direct to tickets")
                                .phase("COMPLETE")
                                .build())
                        .build();
            }).when(workflowAgent).consolidateTicketResults(any(), any());

            doAnswer(inv -> {
                return inv.getArgument(0);
            }).when(workflowAgent).finalCollectorResult(any(), any());

            // 7. Orchestrator collector completes
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .collectorResult(AgentModels.OrchestratorCollectorResult.builder()
                                .consolidatedOutput("Complete")
                                .collectorDecision(AgentModels.CollectorDecision.builder()
                                        .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                        .rationale("Done")
                                        .requestedPhase("COMPLETE")
                                        .build())
                                .build())
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.<AgentModels.OrchestratorCollectorRequest>argThat(req -> "COMPLETE".equals(req.phase())),
                    any()
            );

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-direct-tickets").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Direct to tickets", "TICKETS"))
            );

            // Assert
            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);

            // Verify Discovery and Planning were skipped
            verify(workflowGraphService, never()).startDiscoveryOrchestrator(any(), any());
            verify(workflowGraphService, never()).startPlanningOrchestrator(any(), any());

            // Verify Tickets was executed
            verify(workflowAgent).orchestrateTicketExecution(any(AgentModels.TicketOrchestratorRequest.class), any());
            verify(workflowGraphService).startTicketOrchestrator(any(), any());
        }
    }

    @Nested
    class MultipleAgentScenarios {

//        @Test
        void multipleDiscoveryAgents_allComplete() {
            // 1. Orchestrator routes to collector
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorRouting.builder()
                        .collectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                                .goal("Complex discovery")
                                .phase("DISCOVERY")
                                .build())
                        .build();
            }).when(workflowAgent).coordinateWorkflow(any(), any());

            // 2. Collector routes to Discovery
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .discoveryRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                                .goal("Complex discovery")
                                .build())
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.argThat(req -> "DISCOVERY".equals(req.phase())),
                    any()
            );

            // 3. Discovery orchestrator creates MULTIPLE agent requests
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryOrchestratorRouting.builder()
                        .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                                .requests(List.of(
                                        AgentModels.DiscoveryAgentRequest.builder()
                                                .goal("Complex discovery")
                                                .subdomainFocus("Frontend")
                                                .build(),
                                        AgentModels.DiscoveryAgentRequest.builder()
                                                .goal("Complex discovery")
                                                .subdomainFocus("Backend")
                                                .build(),
                                        AgentModels.DiscoveryAgentRequest.builder()
                                                .goal("Complex discovery")
                                                .subdomainFocus("Database")
                                                .build()
                                ))
                                .build())
                        .build();
            }).when(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());

            // 4. Each discovery subagent returns outputs
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                .output("Frontend findings")
                                .build())
                        .build();
            }).when(discoveryDispatchSubagent).run(
                    argThat((AgentModels.DiscoveryAgentRequest req) -> req.subdomainFocus().equals("Frontend")),
                    any()
            );

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                .output("Backend findings")
                                .build())
                        .build();
            }).when(discoveryDispatchSubagent).run(
                    argThat((AgentModels.DiscoveryAgentRequest req) -> req.subdomainFocus().equals("Backend")),
                    any()
            );

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                .output("Database findings")
                                .build())
                        .build();
            }).when(discoveryDispatchSubagent).run(
                    argThat((AgentModels.DiscoveryAgentRequest req) -> req.subdomainFocus().equals("Database")),
                    any()
            );

            // 5. Discovery dispatch routes to collector
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentDispatchRouting.builder()
                        .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                                .goal("Complex discovery")
                                .discoveryResults("All findings")
                                .build())
                        .build();
            }).when(workflowAgent).dispatchDiscoveryAgentRequests(any(AgentModels.DiscoveryAgentRequests.class), any());

            // 6. Discovery collector completes
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryCollectorRouting.builder()
                        .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                                .consolidatedOutput("Discovery complete")
                                .collectorDecision(AgentModels.CollectorDecision.builder()
                                        .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                        .rationale("Done")
                                        .requestedPhase("COMPLETE")
                                        .build())
                                .build())
                        .build();
            }).when(workflowAgent).consolidateDiscoveryFindings(any(AgentModels.DiscoveryCollectorRequest.class), any());

            // Mock ticket orchestration
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketOrchestratorRouting.builder()
                        .collectorRequest(new AgentModels.TicketCollectorRequest("Complex discovery", "output"))
                        .build();
            }).when(workflowAgent).orchestrateTicketExecution(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketCollectorRouting.builder()
                        .orchestratorCollectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                                .goal("Complex discovery")
                                .phase("COMPLETE")
                                .build())
                        .build();
            }).when(workflowAgent).consolidateTicketResults(any(), any());

            doAnswer(inv -> {
                return inv.getArgument(0);
            }).when(workflowAgent).finalCollectorResult(any(), any());

            // 7. Orchestrator collector completes
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .collectorResult(AgentModels.OrchestratorCollectorResult.builder()
                                .consolidatedOutput("Complete")
                                .collectorDecision(AgentModels.CollectorDecision.builder()
                                        .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                        .rationale("Done")
                                        .requestedPhase("COMPLETE")
                                        .build())
                                .build())
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.<AgentModels.OrchestratorCollectorRequest>argThat(req -> "COMPLETE".equals(req.phase())),
                    any()
            );

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-multiple-discovery").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Complex discovery", "DISCOVERY"))
            );

            // Assert
            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);

            // Verify all 3 discovery agents were called
            verify(discoveryDispatchSubagent, times(3)).run(any(AgentModels.DiscoveryAgentRequest.class), any());
            verify(workflowGraphService, times(3)).startDiscoveryAgent(any(), any(), any());
        }

//        @Test
        void multiplePlanningAgents_consolidatedIntoTickets() {
            // Setup: Start at planning with multiple agents
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorRouting.builder()
                        .collectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                                .goal("Multi-plan")
                                .phase("PLANNING")
                                .build())
                        .build();
            }).when(workflowAgent).coordinateWorkflow(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .planningRequest(AgentModels.PlanningOrchestratorRequest.builder()
                                .goal("Multi-plan")
                                .build())
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.<AgentModels.OrchestratorCollectorRequest>argThat(req -> "PLANNING".equals(req.phase())),
                    any()
            );

            // Create 4 planning agents
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningOrchestratorRouting.builder()
                        .agentRequests(AgentModels.PlanningAgentRequests.builder()
                                .requests(List.of(
                                        AgentModels.PlanningAgentRequest.builder().goal("Multi-plan").build(),
                                        AgentModels.PlanningAgentRequest.builder().goal("Multi-plan").build(),
                                        AgentModels.PlanningAgentRequest.builder().goal("Multi-plan").build(),
                                        AgentModels.PlanningAgentRequest.builder().goal("Multi-plan").build()
                                ))
                                .build())
                        .build();
            }).when(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());

            // All planning agents return outputs
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningAgentRouting.builder()
                        .agentResult(AgentModels.PlanningAgentResult.builder()
                                .output("Plan part")
                                .build())
                        .build();
            }).when(planningDispatchSubagent).run(any(AgentModels.PlanningAgentRequest.class), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningAgentDispatchRouting.builder()
                        .planningCollectorRequest(AgentModels.PlanningCollectorRequest.builder()
                                .goal("Multi-plan")
                                .planningResults("All plans")
                                .build())
                        .build();
            }).when(workflowAgent).dispatchPlanningAgentRequests(any(AgentModels.PlanningAgentRequests.class), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningCollectorRouting.builder()
                        .collectorResult(AgentModels.PlanningCollectorResult.builder()
                                .consolidatedOutput("Plans ready")
                                .collectorDecision(AgentModels.CollectorDecision.builder()
                                        .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                        .rationale("Done")
                                        .requestedPhase("COMPLETE")
                                        .build())
                                .build())
                        .build();
            }).when(workflowAgent).consolidatePlansIntoTickets(any(AgentModels.PlanningCollectorRequest.class), any());

            // Mock ticket orchestration
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketOrchestratorRouting.builder()
                        .collectorRequest(new AgentModels.TicketCollectorRequest("Multi-plan", "output"))
                        .build();
            }).when(workflowAgent).orchestrateTicketExecution(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketCollectorRouting.builder()
                        .orchestratorCollectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                                .goal("Multi-plan")
                                .phase("COMPLETE")
                                .build())
                        .build();
            }).when(workflowAgent).consolidateTicketResults(any(), any());

            doAnswer(inv -> {
                return inv.getArgument(0);
            }).when(workflowAgent).finalCollectorResult(any(), any());

            doReturn(AgentModels.OrchestratorCollectorRouting.builder()
                    .collectorResult(AgentModels.OrchestratorCollectorResult.builder()
                            .consolidatedOutput("Complete")
                            .collectorDecision(AgentModels.CollectorDecision.builder()
                                    .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                    .rationale("Done")
                                    .requestedPhase("COMPLETE")
                                    .build())
                            .build())
                    .build()
            ).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.<AgentModels.OrchestratorCollectorRequest>argThat(req -> "COMPLETE".equals(req.phase())),
                    any()
            );

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-multiple-planning").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Multi-plan", "PLANNING"))
            );

            // Assert
            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);
            verify(planningDispatchSubagent, times(4)).run(any(AgentModels.PlanningAgentRequest.class), any());
            verify(workflowGraphService, times(4)).startPlanningAgent(any(), any(), any());
        }
    }

    @Nested
    class InterruptScenarios {

        @Test
        void orchestratorPause_workflowStops() {
            // Orchestrator immediately pauses
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorRouting.builder()
                        .interruptRequest(AgentModels.OrchestratorInterruptRequest.builder()
                                .type(Events.InterruptType.PAUSE)
                                .reason("User requested pause")
                                .build())
                        .build();
            }).when(workflowAgent).coordinateWorkflow(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorRouting.builder()
                        .collectorRequest(AgentModels.OrchestratorCollectorRequest.builder().build())
                        .build();
            }).when(workflowAgent).handleOrchestratorInterrupt(any(), any());

            mockOutOrchestratorCollector();

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-pause").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Paused task", "DISCOVERY"))
            );

            // Assert - workflow should be interrupted
//            verify(workflowGraphService).handleOrchestratorInterrupt(any(),
//                    argThat(req -> req.type() == Events.InterruptType.PAUSE));
            verify(workflowAgent).coordinateWorkflow(any(), any());
            verify(workflowAgent).handleOrchestratorInterrupt(any(), any());
        }

//        @Test
        void orchestratorStop_workflowAborts() {
            // Orchestrator stops the workflow
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorRouting.builder()
                        .interruptRequest(AgentModels.OrchestratorInterruptRequest.builder()
                                .type(Events.InterruptType.STOP)
                                .reason("Critical error detected")
                                .build())
                        .build();
            }).when(workflowAgent).coordinateWorkflow(any(), any());

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-stop").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Stopped task", "DISCOVERY"))
            );

            // Assert
            verify(workflowGraphService).handleOrchestratorInterrupt(any(),
                    argThat(req -> req.type() == Events.InterruptType.STOP));
            verify(workflowAgent, never()).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
        }

//        @Test
        void collectorPause_afterDiscovery() {
            // Normal orchestrator
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorRouting.builder()
                        .collectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                                .goal("Task")
                                .phase("DISCOVERY")
                                .build())
                        .build();
            }).when(workflowAgent).coordinateWorkflow(any(), any());

            // Collector routes to discovery
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .discoveryRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                                .goal("Task")
                                .build())
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(any(AgentModels.OrchestratorCollectorRequest.class), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryOrchestratorRouting.builder()
                        .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                                .requests(List.of(
                                        AgentModels.DiscoveryAgentRequest.builder()
                                                .goal("Task")
                                                .subdomainFocus("subdomain")
                                                .build()
                                ))
                                .build())
                        .build();
            }).when(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                .output("Found stuff")
                                .build())
                        .build();
            }).when(discoveryDispatchSubagent).run(any(AgentModels.DiscoveryAgentRequest.class), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentDispatchRouting.builder()
                        .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                                .goal("Task")
                                .discoveryResults("findings")
                                .build())
                        .build();
            }).when(workflowAgent).dispatchDiscoveryAgentRequests(any(AgentModels.DiscoveryAgentRequests.class), any());

            // Discovery collector PAUSES instead of continuing
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryCollectorRouting.builder()
                        .interruptRequest(AgentModels.DiscoveryCollectorInterruptRequest.builder()
                                .type(Events.InterruptType.PAUSE)
                                .reason("Need human review")
                                .build())
                        .build();
            }).when(workflowAgent).consolidateDiscoveryFindings(any(AgentModels.DiscoveryCollectorRequest.class), any());

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-collector-pause").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Task", "DISCOVERY"))
            );

            // Assert
            verify(workflowAgent).consolidateDiscoveryFindings(any(AgentModels.DiscoveryCollectorRequest.class), any());
            // Should NOT proceed to planning
            verify(workflowAgent, never()).decomposePlanAndCreateWorkItems(any(), any());
        }

//        @Test
        void discoveryAgentPause_oneOfMany() {
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorRouting.builder()
                        .collectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                                .goal("Multi-agent pause")
                                .phase("DISCOVERY")
                                .build())
                        .build();
            }).when(workflowAgent).coordinateWorkflow(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .discoveryRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                                .goal("Multi-agent pause")
                                .build())
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(any(AgentModels.OrchestratorCollectorRequest.class), any());

            // Create 3 discovery agents
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryOrchestratorRouting.builder()
                        .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                                .requests(List.of(
                                        AgentModels.DiscoveryAgentRequest.builder()
                                                .goal("Multi-agent pause")
                                                .subdomainFocus("Agent1")
                                                .build(),
                                        AgentModels.DiscoveryAgentRequest.builder()
                                                .goal("Multi-agent pause")
                                                .subdomainFocus("Agent2")
                                                .build(),
                                        AgentModels.DiscoveryAgentRequest.builder()
                                                .goal("Multi-agent pause")
                                                .subdomainFocus("Agent3")
                                                .build()
                                ))
                                .build())
                        .build();
            }).when(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());

            // Agent 1 succeeds
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                .output("Agent 1 output")
                                .build())
                        .build();
            }).when(discoveryDispatchSubagent).run(
                    argThat((AgentModels.DiscoveryAgentRequest req) -> req.subdomainFocus().equals("Agent1")),
                    any()
            );

            // Agent 2 PAUSES
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentRouting.builder()
                        .interruptRequest(AgentModels.DiscoveryAgentInterruptRequest.builder()
                                .type(Events.InterruptType.PAUSE)
                                .reason("Agent 2 needs input")
                                .build())
                        .build();
            }).when(discoveryDispatchSubagent).run(
                    argThat((AgentModels.DiscoveryAgentRequest req) -> req.subdomainFocus().equals("Agent2")),
                    any()
            );

            // Agent 3 succeeds
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                .output("Agent 3 output")
                                .build())
                        .build();
            }).when(discoveryDispatchSubagent).run(
                    argThat((AgentModels.DiscoveryAgentRequest req) -> req.subdomainFocus().equals("Agent3")),
                    any()
            );

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-agent-pause").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Multi-agent pause", "DISCOVERY"))
            );

            // Assert - one agent paused
            // Note: interrupt handling is done by the graph service, not directly verified on action calls
            verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
        }

//        @Test
        void planningOrchestratorHumanReview_needsApproval() {
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorRouting.builder()
                        .collectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                                .goal("Review needed")
                                .phase("PLANNING")
                                .build())
                        .build();
            }).when(workflowAgent).coordinateWorkflow(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .planningRequest(AgentModels.PlanningOrchestratorRequest.builder()
                                .goal("Review needed")
                                .build())
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(any(AgentModels.OrchestratorCollectorRequest.class), any());

            // Planning orchestrator requests HUMAN_REVIEW
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningOrchestratorRouting.builder()
                        .interruptRequest(AgentModels.PlanningOrchestratorInterruptRequest.builder()
                                .type(Events.InterruptType.HUMAN_REVIEW)
                                .reason("Plan needs approval before proceeding")
                                .build())
                        .build();
            }).when(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-human-review").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Review needed", "PLANNING"))
            );

            // Assert
            // Note: Planning orchestrator interrupt is handled by WorkflowAgent.handlePlanningInterrupt
            verify(planningDispatchSubagent, never()).run(any(AgentModels.PlanningAgentRequest.class), any());
        }

//        @Test
        void ticketAgentStop_criticalError() {
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorRouting.builder()
                        .collectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                                .goal("Ticket error")
                                .phase("TICKETS")
                                .build())
                        .build();
            }).when(workflowAgent).coordinateWorkflow(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .ticketRequest(new AgentModels.TicketOrchestratorRequest("Direct to tickets"))
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(any(AgentModels.OrchestratorCollectorRequest.class), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketOrchestratorRouting.builder()
                        .agentRequests(AgentModels.TicketAgentRequests.builder()
                                .requests(List.of(new AgentModels.TicketAgentRequest("Ticket error", "")))
                                .build())
                        .build();
            }).when(workflowAgent).orchestrateTicketExecution(any(), any());

            // Ticket agent encounters critical error and STOPS
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketAgentRouting.builder()
                        .interruptRequest(AgentModels.TicketAgentInterruptRequest.builder()
                                .type(Events.InterruptType.STOP)
                                .reason("Build failed, cannot continue")
                                .build())
                        .build();
            }).when(ticketDispatchSubagent).run(any(AgentModels.TicketAgentRequest.class), any());

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-ticket-stop").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Ticket error", "TICKETS"))
            );

            // Assert
            verify(workflowAgent).orchestrateTicketExecution(any(AgentModels.TicketOrchestratorRequest.class), any());
        }
    }

    @Nested
    class LoopingScenarios {

        @Test
        void discoveryCollector_loopsBackForMoreInvestigation() {
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorRouting.builder()
                        .collectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                                .goal("Needs more discovery")
                                .phase("DISCOVERY")
                                .build())
                        .build();
            }).when(workflowAgent).coordinateWorkflow(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .discoveryRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                                .goal("Needs more discovery")
                                .build())
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(any(AgentModels.OrchestratorCollectorRequest.class), any());

            // Use counter to distinguish between first and second discovery iteration
            AtomicInteger discoveryOrchestratorCount = new AtomicInteger(0);
            doAnswer(inv -> {
                registerAndHide(inv);
                if (discoveryOrchestratorCount.getAndIncrement() == 0) {
                    // First discovery iteration
                    return AgentModels.DiscoveryOrchestratorRouting.builder()
                            .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                                    .requests(List.of(
                                            AgentModels.DiscoveryAgentRequest.builder()
                                                    .goal("Needs more discovery")
                                                    .subdomainFocus("Initial")
                                                    .build()
                                    ))
                                    .build())
                            .build();
                } else {
                    // Second discovery iteration with different subdomain
                    return AgentModels.DiscoveryOrchestratorRouting.builder()
                            .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                                    .requests(List.of(
                                            AgentModels.DiscoveryAgentRequest.builder()
                                                    .goal("Needs more discovery")
                                                    .subdomainFocus("Deeper")
                                                    .build()
                                    ))
                                    .build())
                            .build();
                }
            }).when(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                .output("Initial findings - incomplete")
                                .build())
                        .build();
            }).when(discoveryDispatchSubagent).run(
                    argThat((AgentModels.DiscoveryAgentRequest req) -> "Initial".equals(req.subdomainFocus())),
                    any()
            );

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                .output("Deeper findings - complete")
                                .build())
                        .build();
            }).when(discoveryDispatchSubagent).run(
                    argThat((AgentModels.DiscoveryAgentRequest req) -> "Deeper".equals(req.subdomainFocus())),
                    any()
            );

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentDispatchRouting.builder()
                        .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                                .goal("Needs more discovery")
                                .discoveryResults("initial")
                                .build())
                        .build();
            }).when(workflowAgent).dispatchDiscoveryAgentRequests(any(AgentModels.DiscoveryAgentRequests.class), any());

            // Discovery collector: first call loops back, second call advances to planning
            AtomicInteger discoveryCollectorCount = new AtomicInteger(0);
            doAnswer(inv -> {
                registerAndHide(inv);
                if (discoveryCollectorCount.getAndIncrement() == 0) {
                    // First call: loop back to discovery orchestrator for MORE discovery
                    return AgentModels.DiscoveryCollectorRouting.builder()
                            .discoveryRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                                    .goal("Needs more discovery")
                                    .build())
                            .build();
                } else {
                    // Second call: advance to planning
                    return AgentModels.DiscoveryCollectorRouting.builder()
                            .planningRequest(AgentModels.PlanningOrchestratorRequest.builder()
                                    .goal("Needs more discovery")
                                    .build())
                            .build();
                }
            }).when(workflowAgent).consolidateDiscoveryFindings(any(AgentModels.DiscoveryCollectorRequest.class), any());

            // Rest of workflow
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningOrchestratorRouting.builder()
                        .agentRequests(AgentModels.PlanningAgentRequests.builder()
                                .requests(List.of(
                                        AgentModels.PlanningAgentRequest.builder()
                                                .goal("Needs more discovery")
                                                .build()
                                ))
                                .build())
                        .build();
            }).when(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningAgentRouting.builder()
                        .agentResult(AgentModels.PlanningAgentResult.builder()
                                .output("Plan")
                                .build())
                        .build();
            }).when(planningDispatchSubagent).run(any(AgentModels.PlanningAgentRequest.class), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningAgentDispatchRouting.builder()
                        .planningCollectorRequest(AgentModels.PlanningCollectorRequest.builder()
                                .goal("Needs more discovery")
                                .planningResults("plan")
                                .build())
                        .build();
            }).when(workflowAgent).dispatchPlanningAgentRequests(any(AgentModels.PlanningAgentRequests.class), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningCollectorRouting.builder()
                        .collectorResult(AgentModels.PlanningCollectorResult.builder()
                                .consolidatedOutput("Done")
                                .collectorDecision(AgentModels.CollectorDecision.builder()
                                        .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                        .rationale("Complete")
                                        .requestedPhase("COMPLETE")
                                        .build())
                                .build())
                        .build();
            }).when(workflowAgent).consolidatePlansIntoTickets(any(AgentModels.PlanningCollectorRequest.class), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .collectorResult(AgentModels.OrchestratorCollectorResult.builder()
                                .consolidatedOutput("Complete")
                                .collectorDecision(AgentModels.CollectorDecision.builder()
                                        .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                        .rationale("Done")
                                        .requestedPhase("COMPLETE")
                                        .build())
                                .build())
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.argThat(req -> "COMPLETE".equals(req.phase())),
                    any()
            );

            mockOutTicketLoop();

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-discovery-loop").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Needs more discovery", "DISCOVERY"))
            );

            // Assert
            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);

            // Verify TWO discovery iterations
            verify(workflowAgent, times(2)).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            verify(workflowAgent, times(2)).consolidateDiscoveryFindings(any(AgentModels.DiscoveryCollectorRequest.class), any());

            // But planning only once
            verify(workflowAgent, times(1)).decomposePlanAndCreateWorkItems(any(), any());

//            verify(workflowGraphService, times(2)).startDiscoveryOrchestrator(any(), any());
        }

        @Test
        void planningCollector_loopsBackToDiscovery_needsMoreContext() {
            // Orchestrator routes to collector with PLANNING phase
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorRouting.builder()
                        .orchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                                .goal("Incomplete context")
                                .build())
                        .build();
            })
                    .when(workflowAgent).coordinateWorkflow(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .discoveryRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                                .goal("Incomplete context")
                                .build())
                        .build();
            }).when(workflowAgent).handleOrchestratorCollectorBranch(
                    Mockito.argThat(req -> "PLANNING".equals(req.collectorDecision().requestedPhase())),
                    any()
            );

            // Planning phase
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningOrchestratorRouting.builder()
                        .agentRequests(AgentModels.PlanningAgentRequests.builder()
                                .requests(List.of(
                                        AgentModels.PlanningAgentRequest.builder()
                                                .goal("Incomplete context")
                                                .build()
                                ))
                                .build())
                        .build();
            }).when(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningAgentRouting.builder()
                        .agentResult(AgentModels.PlanningAgentResult.builder()
                                .output("Plan missing context")
                                .build())
                        .build();
            }).when(planningDispatchSubagent).run(any(AgentModels.PlanningAgentRequest.class), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningAgentDispatchRouting.builder()
                        .planningCollectorRequest(AgentModels.PlanningCollectorRequest.builder()
                                .goal("Incomplete context")
                                .planningResults("plan")
                                .build())
                        .build();
            }).when(workflowAgent).dispatchPlanningAgentRequests(any(AgentModels.PlanningAgentRequests.class), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryCollectorRouting.builder()
                        .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                                .consolidatedOutput("Incomplete context")
                                .collectorDecision(AgentModels.CollectorDecision.builder()
                                        .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                        .rationale("")
                                        .requestedPhase("PLANNING")
                                        .build())
                                .build())
                        .build();
            }).when(workflowAgent).consolidateDiscoveryFindings(
                    any(),
                    any()
            );

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryCollectorRouting.builder()
                        .planningRequest(AgentModels.PlanningOrchestratorRequest.builder()
                                .goal("Incomplete context")
                                .build())
                        .build();
            }).when(workflowAgent).handleDiscoveryCollectorBranch(
                    Mockito.argThat(req -> "PLANNING".equals(req.collectorDecision().requestedPhase())),
                    any()
            );

            // Planning collector decides to loop back to DISCOVERY for more context (first call with "plan")
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningCollectorRouting.builder()
                        .discoveryOrchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                                .goal("Incomplete context")
                                .build())
                        .build();
            }).when(workflowAgent).consolidatePlansIntoTickets(
                    Mockito.argThat(req -> req.planningResults().contains("plan") && !req.planningResults().contains("complete-plan")),
                    any()
            );

            AtomicInteger j = new AtomicInteger(0);
            doAnswer(inv -> {
                registerAndHide(inv);
                if (j.getAndIncrement() >= 1)  {
                    return AgentModels.DiscoveryOrchestratorRouting.builder()
                            .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                                    .requests(List.of(
                                            AgentModels.DiscoveryAgentRequest.builder()
                                                    .goal("Another thing")
                                                    .subdomainFocus("Additional")
                                                    .build()
                                    ))
                                    .build())
                            .build();

                } else {
                    return AgentModels.DiscoveryOrchestratorRouting.builder()
                            .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                                    .requests(List.of(
                                            AgentModels.DiscoveryAgentRequest.builder()
                                                    .goal("Incomplete context")
                                                    .subdomainFocus("Additional")
                                                    .build()
                                    ))
                                    .build())
                            .build();
                }
            }).when(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                .output("Additional context found")
                                .build())
                        .build();
            }).when(discoveryDispatchSubagent).run(any(AgentModels.DiscoveryAgentRequest.class), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentDispatchRouting.builder()
                        .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                                .goal("Incomplete context")
                                .discoveryResults("additional")
                                .build())
                        .build();
            }).when(workflowAgent).dispatchDiscoveryAgentRequests(any(AgentModels.DiscoveryAgentRequests.class), any());

            // Discovery collector completes and advances to planning (has the context now)
            AtomicInteger i = new AtomicInteger(0);

            // Planning orchestrator (second time, after getting more context from discovery)
            doAnswer(inv -> {
                registerAndHide(inv);
               return AgentModels.PlanningOrchestratorRouting.builder()
                        .agentRequests(AgentModels.PlanningAgentRequests.builder()
                                .requests(List.of(AgentModels.PlanningAgentRequest.builder()
                                        .goal("Incomplete context")
                                        .build()))
                                .build())
                        .build();
            }).when(workflowAgent).decomposePlanAndCreateWorkItems(
                    Mockito.argThat(req -> "Incomplete context".equals(req.goal())),
                    any()
            );

            // Planning agent (second attempt with more context)
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningAgentRouting.builder()
                        .agentResult(AgentModels.PlanningAgentResult.builder()
                                .output("Plan with full context")
                                .build())
                        .build();
            }).when(planningDispatchSubagent).run(
                    argThat((AgentModels.PlanningAgentRequest req) -> "Incomplete context".equals(req.goal())),
                    any()
            );


            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningAgentDispatchRouting.builder()
                        .planningCollectorRequest(AgentModels.PlanningCollectorRequest.builder()
                                .goal("Incomplete context")
                                .planningResults("complete-plan")
                                .build())
                        .build();
            }).when(workflowAgent).dispatchPlanningAgentRequests(
                    argThat(requests -> requests.requests().stream()
                            .anyMatch(r -> "Incomplete context".equals(r.goal()))),
                    any()
            );

            doAnswer(inv -> {
                registerAndHide(inv);
                if (i.getAndIncrement() >= 1) {
                    return AgentModels.PlanningCollectorRouting.builder()
                            .collectorResult(AgentModels.PlanningCollectorResult.builder()
                                    .consolidatedOutput("Planning complete with context")
                                    .collectorDecision(AgentModels.CollectorDecision.builder()
                                            .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                            .rationale("Complete")
                                            .requestedPhase("COMPLETE")
                                            .build())
                                    .build())
                            .build();
                }

                return AgentModels.PlanningCollectorRouting.builder()
                        .discoveryOrchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                                .goal("Again!")
                                .build())
                        .build();

            }).when(workflowAgent).consolidatePlansIntoTickets(
                    Mockito.argThat(req -> req.planningResults().contains("complete-plan")),
                    any()
            );

            // Orchestrator collector completes
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                                .collectorResult(new AgentModels.OrchestratorCollectorResult(
                                        "Complete",
                                        new AgentModels.CollectorDecision(
                                                Events.CollectorDecisionType.ADVANCE_PHASE,
                                                "Done",
                                                "COMPLETE"
                                        )
                                ))
                                .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(Mockito.argThat(req -> "COMPLETE".equals(req.phase())), any());

            mockOutTicketLoop();

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningCollectorRouting.builder()
                        .ticketOrchestratorRequest(new AgentModels.TicketOrchestratorRequest(""))
                        .build();
            }).when(workflowAgent).handlePlanningCollectorBranch(
                    any(),
                    any()
            );

            doAnswer(inv -> {
                return inv.getArgument(0);
            }).when(workflowAgent).finalCollectorResult(
                    any(),
                    any()
            );

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-planning-to-discovery").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Incomplete context", "PLANNING")));

            // Assert
            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);

            // Started at planning, then looped back to discovery, then back to planning
            verify(workflowAgent, times(2)).decomposePlanAndCreateWorkItems(any(), any());
            verify(workflowAgent, times(2)).consolidatePlansIntoTickets(any(), any());
            verify(workflowAgent, times(2)).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
        }

//        @Test
        void orchestratorCollector_loopsBackMultipleTimes() {
            // Orchestrator starts workflow
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorRouting.builder()
                        .collectorRequest(new AgentModels.OrchestratorCollectorRequest("Multi-loop", "DISCOVERY"))
                        .build();
            }).when(workflowAgent).coordinateWorkflow(any(), any());

            // First: route to discovery
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .discoveryRequest(new AgentModels.DiscoveryOrchestratorRequest("Multi-loop"))
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.<AgentModels.OrchestratorCollectorRequest>argThat(req -> "DISCOVERY".equals(req.phase())),
                    any()
            );

            // Discovery completes
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryOrchestratorRouting.builder()
                        .agentRequests(new AgentModels.DiscoveryAgentRequests(List.of(
                                new AgentModels.DiscoveryAgentRequest("Multi-loop", "First")
                        )))
                        .build();
            }).when(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(new AgentModels.DiscoveryAgentResult("First discovery"))
                        .build();
            }).when(discoveryDispatchSubagent).run(any(AgentModels.DiscoveryAgentRequest.class), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryAgentDispatchRouting.builder()
                        .collectorRequest(new AgentModels.DiscoveryCollectorRequest("Multi-loop", "first"))
                        .build();
            }).when(workflowAgent).dispatchDiscoveryAgentRequests(any(AgentModels.DiscoveryAgentRequests.class), any());

            // Discovery collector loops back to ORCHESTRATOR COLLECTOR (not to planning)
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.DiscoveryCollectorRouting.builder()
                        .orchestratorRequest(new AgentModels.OrchestratorRequest("Multi-loop", "PLANNING")) // Back to orchestrator!
                        .build();
            }).when(workflowAgent).consolidateDiscoveryFindings(any(AgentModels.DiscoveryCollectorRequest.class), any());

            // Second: orchestrator collector routes to planning
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .planningRequest(new AgentModels.PlanningOrchestratorRequest("Multi-loop"))
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.<AgentModels.OrchestratorCollectorRequest>argThat(req -> "PLANNING".equals(req.phase())),
                    any()
            );

            // Planning completes
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningOrchestratorRouting.builder()
                        .agentRequests(new AgentModels.PlanningAgentRequests(List.of(
                                new AgentModels.PlanningAgentRequest("Multi-loop")
                        )))
                        .build();
            }).when(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningAgentRouting.builder()
                        .agentResult(new AgentModels.PlanningAgentResult("Plan"))
                        .build();
            }).when(planningDispatchSubagent).run(any(AgentModels.PlanningAgentRequest.class), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningAgentDispatchRouting.builder()
                        .planningCollectorRequest(new AgentModels.PlanningCollectorRequest("Multi-loop", "plan"))
                        .build();
            }).when(workflowAgent).dispatchPlanningAgentRequests(any(AgentModels.PlanningAgentRequests.class), any());

            // Planning collector ALSO loops back to orchestrator collector
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.PlanningCollectorRouting.builder()
                        .reviewRequest(new AgentModels.ReviewRequest(
                                "Multi-loop",
                                "Review criteria",
                                new AgentModels.OrchestratorCollectorRequest("Multi-loop", "TICKETS"),
                                null,
                                null,
                                null
                        ))
                        .build();
            }).when(workflowAgent).consolidatePlansIntoTickets(any(AgentModels.PlanningCollectorRequest.class), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.ReviewRouting.builder()
                        .reviewResult(new AgentModels.ReviewAgentResult("approved"))
                        .orchestratorCollectorRequest(new AgentModels.OrchestratorCollectorRequest("Multi-loop", "TICKETS"))
                        .build();
            }).when(workflowAgent).performReview(any(), any());

            // Mock ticket orchestration
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketOrchestratorRouting.builder()
                        .collectorRequest(new AgentModels.TicketCollectorRequest("Multi-loop", "output"))
                        .build();
            }).when(workflowAgent).orchestrateTicketExecution(any(), any());

            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.TicketCollectorRouting.builder()
                        .orchestratorCollectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                                .goal("Multi-loop")
                                .phase("COMPLETE")
                                .build())
                        .build();
            }).when(workflowAgent).consolidateTicketResults(any(), any());

            doAnswer(inv -> {
                return inv.getArgument(0);
            }).when(workflowAgent).finalCollectorResult(any(), any());

            // Third: orchestrator collector finally completes
            doAnswer(inv -> {
                registerAndHide(inv);
                return AgentModels.OrchestratorCollectorRouting.builder()
                        .collectorResult(new AgentModels.OrchestratorCollectorResult(
                                "Finally complete",
                                new AgentModels.CollectorDecision(
                                        Events.CollectorDecisionType.ADVANCE_PHASE,
                                        "Done",
                                        "COMPLETE"
                                )
                        ))
                        .build();
            }).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.<AgentModels.OrchestratorCollectorRequest>argThat(req -> "TICKETS".equals(req.phase())),
                    any()
            );

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-multi-loop").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Multi-loop", "DISCOVERY"))
            );

            // Assert
            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);

            // consolidateWorkflowOutputs called 3 times (DISCOVERY, PLANNING, TICKETS phases)
            verify(workflowAgent, atLeast(3)).consolidateWorkflowOutputs(any(AgentModels.OrchestratorCollectorRequest.class), any());
            verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            verify(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());
        }
    }

    private void mockOutTicketLoop() {
        doAnswer(inv -> {
            registerAndHide(inv);
            return AgentModels.TicketCollectorRouting.builder()
                    .orchestratorCollectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                            .goal("orchestrator")
                            .phase("TICKET")
                            .build())
                    .build();
        }).when(workflowAgent).handleTicketCollectorBranch(
                any(),
                any()
        );

        doAnswer(inv -> {
            registerAndHide(inv);
            return AgentModels.TicketOrchestratorRouting.builder()
                    .collectorRequest(new AgentModels.TicketCollectorRequest("goal", "output"))
                    .build();

        }).when(workflowAgent).orchestrateTicketExecution(
                any(),
                any()
        );

        doAnswer(inv -> {
            registerAndHide(inv);
            return AgentModels.TicketCollectorRouting.builder()
                    .collectorResult(AgentModels.TicketCollectorResult.builder()
                            .consolidatedOutput("Incomplete context")
                            .collectorDecision(AgentModels.CollectorDecision.builder()
                                    .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                    .rationale("")
                                    .requestedPhase("DISCOVERY")
                                    .build())
                            .build())
                    .build();
        }).when(workflowAgent).consolidateTicketResults(
                any(),
                any()
        );


        doAnswer(inv -> {
            registerAndHide(inv);
            return AgentModels.TicketCollectorRouting.builder()
                    .orchestratorCollectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                            .goal("orchestrator collector goal")
                            .phase("COMPLETE")
                            .build())
                    .build();
        }).when(workflowAgent).consolidateTicketResults(
                any(),
                any()
        );
    }

    private void mockOutOrchestratorCollector() {

        doAnswer(inv -> {
            registerAndHide(inv);
            return AgentModels.OrchestratorCollectorRouting.builder()
                    .collectorResult(AgentModels.OrchestratorCollectorResult.builder()
                            .consolidatedOutput("Incomplete context")
                            .collectorDecision(AgentModels.CollectorDecision.builder()
                                    .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                    .rationale("")
                                    .requestedPhase("ORCHESTRATOR")
                                    .build())
                            .build())
                    .build();
        }).when(workflowAgent).consolidateWorkflowOutputs(
                any(),
                any());

        doAnswer(inv -> {
            return inv.getArgument(0);
        }).when(workflowAgent).finalCollectorResult(any(), any());

        doAnswer(inv -> {
            registerAndHide(inv);
            return AgentModels.OrchestratorCollectorRouting.builder()
                    .collectorResult(AgentModels.OrchestratorCollectorResult.builder()
                            .consolidatedOutput("Complete")
                            .collectorDecision(AgentModels.CollectorDecision.builder()
                                    .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                    .rationale("Done")
                                    .requestedPhase("COMPLETE")
                                    .build())
                            .build())
                    .build();
        }).when(workflowAgent).consolidateWorkflowOutputs(
                Mockito.argThat(req -> "COMPLETE".equals(req.phase())),
                any()
        );
    }

    private static void registerAndHide(InvocationOnMock inv) {
        Arrays.stream(inv.getArguments())
                .flatMap(obj -> obj instanceof OperationContext c ? Stream.of(c) : Stream.empty())
                .findFirst()
                .ifPresent(op -> Arrays.stream(inv.getArguments())
                        .flatMap(obj -> obj instanceof OperationContext ? Stream.empty() : Stream.of(obj))
                        .forEach(n -> BlackboardHistory.registerAndHideInput(op, inv.getMethod().getName(), n)));

    }

    @Nested
    class ReviewAndMergeWorkflows {

        // TODO: Re-enable these tests when Review and Merge actions are fully implemented in the refactored agent
        // The current AgentInterfaces.WorkflowAgent doesn't have complete Review/Merge dispatch implementation yet

        // @Test
        void ticketsToReview_thenMerge_thenComplete_DISABLED() {
            // Start at REVIEW phase
            doReturn(AgentModels.OrchestratorRouting.builder()
                    .collectorRequest(new AgentModels.OrchestratorCollectorRequest("Review flow", "REVIEW"))
                    .build()
            ).when(workflowAgent).coordinateWorkflow(any(), any());

            // Orchestrator collector routes to Review
            doReturn(AgentModels.OrchestratorCollectorRouting.builder()
                    .reviewRequest(new AgentModels.ReviewRequest(
                            "Review flow",
                            "Review criteria",
                            new AgentModels.OrchestratorCollectorRequest("Review flow", "MERGE"),
                            null,
                            null,
                            null
                    ))
                    .build()
            ).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.<AgentModels.OrchestratorCollectorRequest>argThat(req -> "REVIEW".equals(req.phase())),
                    any()
            );

            // Review action returns output routing to Merger
            doReturn(AgentModels.ReviewRouting.builder()
                    .reviewResult(new AgentModels.ReviewAgentResult("Review passed"))
                    .orchestratorCollectorRequest(new AgentModels.OrchestratorCollectorRequest("Review flow", "MERGE"))
                    .build()
            ).when(workflowAgent).performReview(any(), any());

            // Merger completes
            doReturn(AgentModels.MergerRouting.builder()
                    .mergerResult(new AgentModels.MergerAgentResult("Merged successfully"))
                    .orchestratorCollectorRequest(new AgentModels.OrchestratorCollectorRequest("Review flow", "COMPLETE"))
                    .build()
            ).when(workflowAgent).performMerge(any(), any());

            // Orchestrator collector completes
            doReturn(AgentModels.OrchestratorCollectorRouting.builder()
                    .collectorResult(new AgentModels.OrchestratorCollectorResult(
                            "All done",
                            new AgentModels.CollectorDecision(
                                    Events.CollectorDecisionType.ADVANCE_PHASE,
                                    "Complete",
                                    "COMPLETE"
                            )
                    ))
                    .build()
            ).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.<AgentModels.OrchestratorCollectorRequest>argThat(req -> "COMPLETE".equals(req.phase())),
                    any()
            );

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-review-merge").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Review flow", "REVIEW"))
            );

            // Assert
            // TODO: Re-enable when review/merge actions are fully implemented
            // assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);
        }

        // @Test
        void reviewFails_loopsBackToTickets_DISABLED() {
            doReturn(AgentModels.OrchestratorRouting.builder()
                    .collectorRequest(new AgentModels.OrchestratorCollectorRequest("Review fail", "REVIEW"))
                    .build()
            ).when(workflowAgent).coordinateWorkflow(any(), any());

            doReturn(AgentModels.OrchestratorCollectorRouting.builder()
                    .reviewRequest(new AgentModels.ReviewRequest(
                            "Review fail",
                            "Review criteria",
                            new AgentModels.OrchestratorCollectorRequest("Review fail", "MERGE"),
                            null,
                            null,
                            null
                    ))
                    .build()
            ).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.<AgentModels.OrchestratorCollectorRequest>argThat(req -> "REVIEW".equals(req.phase())),
                    any()
            );

            // Review FAILS - routes back to orchestrator collector to retry tickets
            doReturn(AgentModels.ReviewRouting.builder()
                    .reviewResult(new AgentModels.ReviewAgentResult("Review failed - tests broken"))
                    .orchestratorCollectorRequest(new AgentModels.OrchestratorCollectorRequest("Review fail", "TICKETS")) // Back to tickets!
                    .build()
            ).when(workflowAgent).performReview(any(), any());

            // Now back to tickets phase
            doReturn(AgentModels.OrchestratorCollectorRouting.builder()
                    .ticketRequest(new AgentModels.TicketOrchestratorRequest("Direct to tickets"))
                    .build()
            ).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.<AgentModels.OrchestratorCollectorRequest>argThat(req -> "TICKETS".equals(req.phase())),
                    any()
            );

            // Tickets execute
            doReturn(AgentModels.TicketOrchestratorRouting.builder()
                    .agentRequests(AgentModels.TicketAgentRequests.builder()
                            .requests(List.of(new AgentModels.TicketAgentRequest("Review fail", "")))
                            .build())
                    .build()
            ).when(workflowAgent).orchestrateTicketExecution(any(), any());

            doReturn(AgentModels.TicketAgentRouting.builder()
                    .agentResult(new AgentModels.TicketAgentResult("Fixed tests"))
                    .build()
            ).when(ticketDispatchSubagent).run(any(AgentModels.TicketAgentRequest.class), any());

            doReturn(AgentModels.TicketAgentDispatchRouting.builder()
                    .ticketCollectorRequest(new AgentModels.TicketCollectorRequest("Review fail", "fixed"))
                    .build()
            ).when(workflowAgent).dispatchTicketAgentRequests(any(AgentModels.TicketAgentRequests.class), any());

            // Ticket collector now advances to REVIEW again
            doReturn(AgentModels.TicketCollectorRouting.builder()
                    .reviewRequest(new AgentModels.ReviewRequest(
                            "Review fail",
                            "Review criteria",
                            new AgentModels.OrchestratorCollectorRequest("Review fail", "REVIEW"),
                            null,
                            null,
                            null
                    ))
                    .build()
            ).when(workflowAgent).consolidateTicketResults(any(AgentModels.TicketCollectorRequest.class), any());

            // Second review attempt
            doReturn(AgentModels.OrchestratorCollectorRouting.builder()
                    .reviewRequest(new AgentModels.ReviewRequest(
                            "Review fail",
                            "Review criteria",
                            new AgentModels.OrchestratorCollectorRequest("Review fail", "MERGE"),
                            null,
                            null,
                            null
                    ))
                    .build()
            ).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.<AgentModels.OrchestratorCollectorRequest>argThat(req -> "REVIEW".equals(req.phase())),
                    any()
            );

            // Second review passes
            doReturn(AgentModels.ReviewRouting.builder()
                    .reviewResult(new AgentModels.ReviewAgentResult("Review passed"))
                    .orchestratorCollectorRequest(new AgentModels.OrchestratorCollectorRequest("Review fail", "MERGE"))
                    .build()
            ).when(workflowAgent).performReview(
                    Mockito.<AgentModels.ReviewRequest>argThat(req -> "Review fail".equals(req.content())),
                    any()
            );

            doReturn(AgentModels.MergerRouting.builder()
                    .mergerResult(new AgentModels.MergerAgentResult("Merged"))
                    .orchestratorCollectorRequest(new AgentModels.OrchestratorCollectorRequest("Review fail", "COMPLETE"))
                    .build()
            ).when(workflowAgent).performMerge(any(), any());

            doReturn(AgentModels.OrchestratorCollectorRouting.builder()
                    .collectorResult(new AgentModels.OrchestratorCollectorResult(
                            "Done",
                            new AgentModels.CollectorDecision(
                                    Events.CollectorDecisionType.ADVANCE_PHASE,
                                    "Complete",
                                    "COMPLETE"
                            )
                    ))
                    .build()
            ).when(workflowAgent).consolidateWorkflowOutputs(
                    Mockito.<AgentModels.OrchestratorCollectorRequest>argThat(req -> "COMPLETE".equals(req.phase())),
                    any()
            );

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-review-loop").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Review fail", "REVIEW"))
            );

            // Assert
            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);

            // Review called twice (failed once, passed second time)
            verify(workflowAgent, times(2)).performReview(any(), any());
            // Tickets executed in the loop
            verify(workflowAgent).orchestrateTicketExecution(any(), any());
        }
    }

    // Helper methods
    private com.embabel.agent.core.Agent findWorkflowAgent() {
        return agentPlatform.agents().stream()
                .filter(a -> a.getName().equals(AgentInterfaces.WORKFLOW_AGENT_NAME))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("WorkflowAgent not found"));
    }

    // Mock node creation helpers
    private OrchestratorNode createMockOrchestratorNode() {
        return OrchestratorNode.builder()
                .nodeId("orch-1")
                .title("Orch")
                .goal("goal")
                .status(Events.NodeStatus.RUNNING)
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .repositoryUrl("repo-url")
                .baseBranch("main")
                .mainWorktreeId("wt")
                .submoduleWorktreeIds(new ArrayList<>())
                .build();
    }

    private CollectorNode createMockCollectorNode() {
        return CollectorNode.builder()
                .nodeId("coll-1")
                .title("Collector")
                .goal("goal")
                .status(Events.NodeStatus.RUNNING)
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .repositoryUrl("repo-url")
                .baseBranch("main")
                .mainWorktreeId("wt")
                .submoduleWorktreeIds(new ArrayList<>())
                .build();
    }

    private DiscoveryOrchestratorNode createMockDiscoveryOrchestratorNode() {
        return DiscoveryOrchestratorNode.builder()
                .nodeId("disc-orch")
                .title("DiscOrch")
                .goal("goal")
                .status(Events.NodeStatus.RUNNING)
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .build();
    }

    private DiscoveryCollectorNode createMockDiscoveryCollectorNode() {
        return DiscoveryCollectorNode.builder()
                .nodeId("disc-coll")
                .title("DiscColl")
                .goal("goal")
                .status(Events.NodeStatus.RUNNING)
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .build();
    }

    private DiscoveryNode createMockDiscoveryNode() {
        return DiscoveryNode.builder()
                .nodeId("disc")
                .title("Disc")
                .goal("goal")
                .status(Events.NodeStatus.RUNNING)
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .build();
    }

    private PlanningOrchestratorNode createMockPlanningOrchestratorNode() {
        return PlanningOrchestratorNode.builder()
                .nodeId("plan-orch")
                .title("PlanOrch")
                .goal("goal")
                .status(Events.NodeStatus.RUNNING)
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .build();
    }

    private PlanningCollectorNode createMockPlanningCollectorNode() {
        return PlanningCollectorNode.builder()
                .nodeId("plan-coll")
                .title("PlanColl")
                .goal("goal")
                .status(Events.NodeStatus.RUNNING)
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .build();
    }

    private PlanningNode createMockPlanningNode() {
        return PlanningNode.builder()
                .nodeId("plan")
                .title("Plan")
                .goal("goal")
                .status(Events.NodeStatus.RUNNING)
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .build();
    }

    private TicketOrchestratorNode createMockTicketOrchestratorNode() {
        return TicketOrchestratorNode.builder()
                .nodeId("tick-orch")
                .title("TickOrch")
                .goal("goal")
                .status(Events.NodeStatus.RUNNING)
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .worktree(new HasWorktree.WorkTree("wt", null, new ArrayList<>()))
                .build();
    }

    private TicketCollectorNode createMockTicketCollectorNode() {
        return TicketCollectorNode.builder()
                .nodeId("tick-coll")
                .title("TickColl")
                .goal("goal")
                .status(Events.NodeStatus.RUNNING)
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .build();
    }

    private TicketNode createMockTicketNode() {
        return TicketNode.builder()
                .nodeId("tick")
                .title("Tick")
                .goal("goal")
                .status(Events.NodeStatus.RUNNING)
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .worktree(new HasWorktree.WorkTree("wt", null, new ArrayList<>()))
                .build();
    }

//    private ReviewNode createMockReviewNode() {
//        return ReviewNode.builder()
//                .nodeId("review")
//                .title("Review")
//                .goal("goal")
//                .status(GraphNode.NodeStatus.RUNNING)
//                .metadata(new HashMap<>())
//                .createdAt(Instant.now())
//                .lastUpdatedAt(Instant.now())
//                .reviewedNodeId("reviewed-1")
//                .reviewContent("Review content")
//                .approved(false)
//                .humanFeedbackRequested(false)
//                .reviewedBy("human")
//                .reviewedAt(Instant.now())
//                .build();
//    }
//
//    private MergeNode createMockMergeNode() {
//        return MergeNode.builder()
//                .nodeId("merge")
//                .title("Merge")
//                .goal("goal")
//                .status(GraphNode.NodeStatus.RUNNING)
//                .inputs(new ArrayList<>())
//                .metadata(new HashMap<>())
//                .createdAt(Instant.now())
//                .updatedAt(Instant.now())
//                .mergeContext("")
//                .maxAgents(0)
//                .currentAgents(0)
//                .build();
//    }
}
