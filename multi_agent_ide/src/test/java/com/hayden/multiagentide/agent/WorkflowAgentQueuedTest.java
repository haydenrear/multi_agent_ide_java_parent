package com.hayden.multiagentide.agent;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ProcessOptions;
import com.hayden.multiagentide.orchestration.ComputationGraphOrchestrator;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.multiagentide.service.LlmRunner;
import com.hayden.multiagentide.service.WorktreeService;
import com.hayden.multiagentide.support.AgentTestBase;
import com.hayden.multiagentide.support.QueuedLlmRunner;
import com.hayden.multiagentide.support.TestEventListener;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.utilitymodule.acp.events.EventBus;
import com.hayden.utilitymodule.acp.events.Events;
import com.hayden.multiagentidelib.model.nodes.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
 */
@SpringBootTest
class WorkflowAgentQueuedTest extends AgentTestBase {

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

    @Autowired
    private QueuedLlmRunner queuedLlmRunner;

    @MockitoSpyBean
    private WorkflowGraphService workflowGraphService;

    @MockitoSpyBean
    private ComputationGraphOrchestrator computationGraphOrchestrator;

    @Autowired
    private GraphRepository graphRepository;

    @Autowired
    private WorktreeRepository worktreeRepository;

    @MockitoBean
    private WorktreeService worktreeService;

    @MockitoBean
    private EventBus eventBus;

    @Autowired
    private TestEventListener testEventListener;

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
    }

    @Nested
    class InterruptScenarios {

        @Test
        void orchestratorPause_workflowStops() {
            seedOrchestrator("test-pause");
            queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                    .interruptRequest(AgentModels.OrchestratorInterruptRequest.builder()
                            .type(Events.InterruptType.PAUSE)
                            .reason("User requested pause")
                            .build())
                    .build());

            enqueueHappyPath("Paused task");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-pause").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Paused task", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();
            verify(workflowGraphService).handleOrchestratorInterrupt(any(),
                    argThat(req -> req.type() == Events.InterruptType.PAUSE));
            verify(workflowGraphService).startDiscoveryOrchestrator(any(), any());
            verify(computationGraphOrchestrator, atLeastOnce()).emitStatusChangeEvent(any(), any(), any(), any());

            var ordered = inOrder(
                    workflowAgent,
                    discoveryDispatchSubagent,
                    planningDispatchSubagent,
                    ticketDispatchSubagent
            );
            ordered.verify(workflowAgent).coordinateWorkflow(any(), any());
            ordered.verify(workflowAgent).handleOrchestratorInterrupt(any(), any());
            ordered.verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            ordered.verify(workflowAgent).dispatchDiscoveryAgentRequests(any(), any());
            ordered.verify(discoveryDispatchSubagent).run(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).handleDiscoveryCollectorBranch(any(), any());
            ordered.verify(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());
            ordered.verify(workflowAgent).dispatchPlanningAgentRequests(any(), any());
            ordered.verify(planningDispatchSubagent).run(any(AgentModels.PlanningAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidatePlansIntoTickets(any(), any());
            ordered.verify(workflowAgent).handlePlanningCollectorBranch(any(), any());
            ordered.verify(workflowAgent).orchestrateTicketExecution(any(), any());
            ordered.verify(workflowAgent).dispatchTicketAgentRequests(any(), any());
            ordered.verify(ticketDispatchSubagent).run(any(AgentModels.TicketAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateTicketResults(any(), any());
            ordered.verify(workflowAgent).handleTicketCollectorBranch(any(), any());
            ordered.verify(workflowAgent).consolidateWorkflowOutputs(any(), any());
        }

        @Test
        void collectorPause_afterDiscovery() {
            seedOrchestrator("test-collector-pause");
            queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                    .orchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                            .goal("Task")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Task")
                                            .subdomainFocus("subdomain")
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
                            .goal("Task")
                            .discoveryResults("findings")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                    .interruptRequest(AgentModels.DiscoveryCollectorInterruptRequest.builder()
                            .type(Events.InterruptType.PAUSE)
                            .reason("Need human review")
                            .build())
                    .build());

            agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-collector-pause").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Task", "DISCOVERY"))
            );

            queuedLlmRunner.assertAllConsumed();
            verify(workflowGraphService, never()).startPlanningOrchestrator(any(), any());
            verify(computationGraphOrchestrator, atLeastOnce()).emitStatusChangeEvent(any(), any(), any(), any());

            var ordered = inOrder(workflowAgent, discoveryDispatchSubagent);
            ordered.verify(workflowAgent).coordinateWorkflow(any(), any());
            ordered.verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            ordered.verify(workflowAgent).dispatchDiscoveryAgentRequests(any(), any());
            ordered.verify(discoveryDispatchSubagent).run(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
        }
    }

    @Nested
    class HappyPathWorkflows {

        @Test
        void fullWorkflow_discoveryToPlanningSingleAgentsToCompletion() {
            seedOrchestrator("test-full-workflow");
            enqueueHappyPath("Implement auth");

            // Act - Run the workflow with real agent code
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-full-workflow").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Implement auth", "DISCOVERY"))
            );

            // Assert
            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);
            
            // Verify all queued responses were consumed
            queuedLlmRunner.assertAllConsumed();
            
            // Verify graph service calls happened
            verify(workflowGraphService).startOrchestrator(any());
            verify(workflowGraphService).startDiscoveryOrchestrator(any(), any());
            verify(workflowGraphService).startDiscoveryAgent(any(), any(), any());
            verify(workflowGraphService).startPlanningOrchestrator(any(), any());
            verify(workflowGraphService).startPlanningAgent(any(), any(), any());
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
            ordered.verify(discoveryDispatchSubagent).run(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).handleDiscoveryCollectorBranch(any(), any());
            ordered.verify(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());
            ordered.verify(workflowAgent).dispatchPlanningAgentRequests(any(), any());
            ordered.verify(planningDispatchSubagent).run(any(AgentModels.PlanningAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidatePlansIntoTickets(any(), any());
            ordered.verify(workflowAgent).handlePlanningCollectorBranch(any(), any());
            ordered.verify(workflowAgent).orchestrateTicketExecution(any(), any());
            ordered.verify(workflowAgent).dispatchTicketAgentRequests(any(), any());
            ordered.verify(ticketDispatchSubagent).run(any(AgentModels.TicketAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateTicketResults(any(), any());
            ordered.verify(workflowAgent).handleTicketCollectorBranch(any(), any());
            ordered.verify(workflowAgent).consolidateWorkflowOutputs(any(), any());
        }

        @Test
        void skipDiscovery_startAtPlanning() {
            seedOrchestrator("test-skip-discovery");
            // Queue responses for workflow starting at PLANNING phase (short-circuit discovery agents)
            
            // 1. Orchestrator routes to discovery orchestrator
            queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                    .orchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                            .goal("Quick task")
                            .build())
                    .build());
            
            // 2. Discovery orchestrator routes directly to collector (no agents)
            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Quick task")
                            .discoveryResults("")
                            .build())
                    .build());

            // 3. Discovery collector advances via collector result
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
            
            // 4. Planning orchestrator creates request
            queuedLlmRunner.enqueue(AgentModels.PlanningOrchestratorRouting.builder()
                    .agentRequests(AgentModels.PlanningAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.PlanningAgentRequest.builder()
                                            .goal("Quick task")
                                            .build()
                            ))
                            .build())
                    .build());
            
            // 5. Planning agent returns output
            queuedLlmRunner.enqueue(AgentModels.PlanningAgentRouting.builder()
                    .agentResult(AgentModels.PlanningAgentResult.builder()
                            .output("Simple plan")
                            .build())
                    .build());
            
            // 6. Planning dispatch routes to collector
            queuedLlmRunner.enqueue(AgentModels.PlanningAgentDispatchRouting.builder()
                    .planningCollectorRequest(AgentModels.PlanningCollectorRequest.builder()
                            .goal("Quick task")
                            .planningResults("plan")
                            .build())
                    .build());
            
            // 7. Planning collector advances via collector result
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
            
            // 8. Ticket orchestrator creates agent requests
            queuedLlmRunner.enqueue(AgentModels.TicketOrchestratorRouting.builder()
                    .agentRequests(AgentModels.TicketAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.TicketAgentRequest.builder()
                                            .ticketDetails("Quick task")
                                            .ticketDetailsFilePath("ticket-1.md")
                                            .build()
                            ))
                            .build())
                    .build());
            
            // 9. Ticket agent returns output
            queuedLlmRunner.enqueue(AgentModels.TicketAgentRouting.builder()
                    .agentResult(AgentModels.TicketAgentResult.builder()
                            .output("Ticket done")
                            .build())
                    .build());
            
            // 10. Ticket dispatch routes to collector
            queuedLlmRunner.enqueue(AgentModels.TicketAgentDispatchRouting.builder()
                    .ticketCollectorRequest(AgentModels.TicketCollectorRequest.builder()
                            .goal("Quick task")
                            .ticketResults("ticket-results")
                            .build())
                    .build());

            // 11. Ticket collector advances via collector result
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

            // 12. Final orchestrator collector completes
            queuedLlmRunner.enqueue(AgentModels.OrchestratorCollectorRouting.builder()
                    .collectorResult(AgentModels.OrchestratorCollectorResult.builder()
                            .consolidatedOutput("Complete")
                            .collectorDecision(AgentModels.CollectorDecision.builder()
                                    .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                    .rationale("Done")
                                    .requestedPhase("COMPLETE")
                                    .build())
                            .build())
                    .build());

            // Act
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-skip-discovery").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Quick task", "PLANNING"))
            );

            // Assert
            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();
            
            // Verify discovery orchestrator ran without agents
            verify(workflowGraphService).startDiscoveryOrchestrator(any(), any());
            verify(workflowGraphService, never()).startDiscoveryAgent(any(), any(), any());
            
            // Verify Planning was executed
            verify(workflowGraphService).startPlanningOrchestrator(any(), any());
            verify(computationGraphOrchestrator, atLeastOnce()).emitStatusChangeEvent(any(), any(), any(), any());

            var ordered = inOrder(
                    workflowAgent,
                    planningDispatchSubagent,
                    ticketDispatchSubagent
            );
            ordered.verify(workflowAgent).coordinateWorkflow(any(), any());
            ordered.verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).handleDiscoveryCollectorBranch(any(), any());
            ordered.verify(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());
            ordered.verify(workflowAgent).dispatchPlanningAgentRequests(any(), any());
            ordered.verify(planningDispatchSubagent).run(any(AgentModels.PlanningAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidatePlansIntoTickets(any(), any());
            ordered.verify(workflowAgent).handlePlanningCollectorBranch(any(), any());
            ordered.verify(workflowAgent).orchestrateTicketExecution(any(), any());
            ordered.verify(workflowAgent).dispatchTicketAgentRequests(any(), any());
            ordered.verify(ticketDispatchSubagent).run(any(AgentModels.TicketAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateTicketResults(any(), any());
            ordered.verify(workflowAgent).handleTicketCollectorBranch(any(), any());
            ordered.verify(workflowAgent).consolidateWorkflowOutputs(any(), any());
        }
    }

    @Nested
    class LoopingScenarios {

        @Test
        void discoveryCollector_loopsBackForMoreInvestigation() {
            seedOrchestrator("test-discovery-loop");

            queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                    .orchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                            .goal("Needs more discovery")
                            .build())
                    .build());

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
                    ProcessOptions.DEFAULT.withContextId("test-discovery-loop").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Needs more discovery", "DISCOVERY"))
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
            ordered.verify(discoveryDispatchSubagent).run(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).handleDiscoveryCollectorBranch(any(), any());
            ordered.verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            ordered.verify(workflowAgent).dispatchDiscoveryAgentRequests(any(), any());
            ordered.verify(discoveryDispatchSubagent).run(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).handleDiscoveryCollectorBranch(any(), any());
            ordered.verify(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());
            ordered.verify(workflowAgent).dispatchPlanningAgentRequests(any(), any());
            ordered.verify(planningDispatchSubagent).run(any(AgentModels.PlanningAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidatePlansIntoTickets(any(), any());
            ordered.verify(workflowAgent).handlePlanningCollectorBranch(any(), any());
            ordered.verify(workflowAgent).orchestrateTicketExecution(any(), any());
            ordered.verify(workflowAgent).dispatchTicketAgentRequests(any(), any());
            ordered.verify(ticketDispatchSubagent).run(any(AgentModels.TicketAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateTicketResults(any(), any());
            ordered.verify(workflowAgent).handleTicketCollectorBranch(any(), any());
            ordered.verify(workflowAgent).consolidateWorkflowOutputs(any(), any());
        }

        @Test
        void planningCollector_loopsBackToDiscovery_needsMoreContext() {
            seedOrchestrator("test-planning-to-discovery");

            queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                    .orchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                            .goal("Incomplete context")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Incomplete context")
                            .discoveryResults("")
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

            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Incomplete context")
                                            .subdomainFocus("Additional")
                                            .build()
                            ))
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentRouting.builder()
                    .agentResult(AgentModels.DiscoveryAgentResult.builder()
                            .output("Additional context found")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Incomplete context")
                            .discoveryResults("additional")
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

            enqueuePlanningToCompletion("Incomplete context");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-planning-to-discovery").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Incomplete context", "PLANNING"))
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
            ordered.verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).handleDiscoveryCollectorBranch(any(), any());
            ordered.verify(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());
            ordered.verify(workflowAgent).dispatchPlanningAgentRequests(any(), any());
            ordered.verify(planningDispatchSubagent).run(any(AgentModels.PlanningAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidatePlansIntoTickets(any(), any());
            ordered.verify(workflowAgent).handlePlanningCollectorBranch(any(), any());
            ordered.verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            ordered.verify(workflowAgent).dispatchDiscoveryAgentRequests(any(), any());
            ordered.verify(discoveryDispatchSubagent).run(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).handleDiscoveryCollectorBranch(any(), any());
            ordered.verify(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());
            ordered.verify(workflowAgent).dispatchPlanningAgentRequests(any(), any());
            ordered.verify(planningDispatchSubagent).run(any(AgentModels.PlanningAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidatePlansIntoTickets(any(), any());
            ordered.verify(workflowAgent).handlePlanningCollectorBranch(any(), any());
            ordered.verify(workflowAgent).orchestrateTicketExecution(any(), any());
            ordered.verify(workflowAgent).dispatchTicketAgentRequests(any(), any());
            ordered.verify(ticketDispatchSubagent).run(any(AgentModels.TicketAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateTicketResults(any(), any());
            ordered.verify(workflowAgent).handleTicketCollectorBranch(any(), any());
            ordered.verify(workflowAgent).consolidateWorkflowOutputs(any(), any());
        }

        @Test
        void orchestratorCollector_loopsBackMultipleTimes() {
            seedOrchestrator("test-multi-loop");

            queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                    .orchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                            .goal("Multi-loop")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Multi-loop")
                                            .subdomainFocus("First")
                                            .build()
                            ))
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentRouting.builder()
                    .agentResult(AgentModels.DiscoveryAgentResult.builder()
                            .output("First discovery")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Multi-loop")
                            .discoveryResults("first")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                    .orchestratorRequest(AgentModels.OrchestratorRequest.builder()
                            .goal("Multi-loop")
                            .phase("PLANNING")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                    .collectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                            .goal("Multi-loop")
                            .phase("PLANNING")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.OrchestratorCollectorRouting.builder()
                    .planningRequest(AgentModels.PlanningOrchestratorRequest.builder()
                            .goal("Multi-loop")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.PlanningOrchestratorRouting.builder()
                    .agentRequests(AgentModels.PlanningAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.PlanningAgentRequest.builder()
                                            .goal("Multi-loop")
                                            .build()
                            ))
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.PlanningAgentRouting.builder()
                    .agentResult(AgentModels.PlanningAgentResult.builder()
                            .output("Plan")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.PlanningAgentDispatchRouting.builder()
                    .planningCollectorRequest(AgentModels.PlanningCollectorRequest.builder()
                            .goal("Multi-loop")
                            .planningResults("plan")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.PlanningCollectorRouting.builder()
                    .reviewRequest(AgentModels.ReviewRequest.builder()
                            .content("Multi-loop")
                            .criteria("Review criteria")
                            .returnToOrchestratorCollector(new AgentModels.OrchestratorCollectorRequest("Multi-loop", "TICKETS"))
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.ReviewRouting.builder()
                    .reviewResult(AgentModels.ReviewAgentResult.builder()
                            .output("approved")
                            .build())
                    .orchestratorCollectorRequest(new AgentModels.OrchestratorCollectorRequest("Multi-loop", "TICKETS"))
                    .build());

            queuedLlmRunner.enqueue(AgentModels.OrchestratorCollectorRouting.builder()
                    .ticketRequest(AgentModels.TicketOrchestratorRequest.builder()
                            .goal("Multi-loop")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.TicketOrchestratorRouting.builder()
                    .agentRequests(AgentModels.TicketAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.TicketAgentRequest.builder()
                                            .ticketDetails("Multi-loop")
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
                            .goal("Multi-loop")
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

            queuedLlmRunner.enqueue(AgentModels.OrchestratorCollectorRouting.builder()
                    .collectorResult(AgentModels.OrchestratorCollectorResult.builder()
                            .consolidatedOutput("Complete")
                            .collectorDecision(AgentModels.CollectorDecision.builder()
                                    .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                    .rationale("Done")
                                    .requestedPhase("COMPLETE")
                                    .build())
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.OrchestratorCollectorRouting.builder()
                    .collectorResult(AgentModels.OrchestratorCollectorResult.builder()
                            .consolidatedOutput("Finally complete")
                            .collectorDecision(AgentModels.CollectorDecision.builder()
                                    .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                    .rationale("Done")
                                    .requestedPhase("COMPLETE")
                                    .build())
                            .build())
                    .build());

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId("test-multi-loop").withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest("Multi-loop", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();
            verify(workflowAgent, atLeast(2)).consolidateWorkflowOutputs(any(), any());
            verify(workflowAgent).performReview(any(), any());
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
            ordered.verify(discoveryDispatchSubagent).run(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).coordinateWorkflow(any(), any());
            ordered.verify(workflowAgent).consolidateWorkflowOutputs(any(), any());
            ordered.verify(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());
            ordered.verify(workflowAgent).dispatchPlanningAgentRequests(any(), any());
            ordered.verify(planningDispatchSubagent).run(any(AgentModels.PlanningAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidatePlansIntoTickets(any(), any());
            ordered.verify(workflowAgent).handlePlanningCollectorBranch(any(), any());
            ordered.verify(workflowAgent).performReview(any(), any());
            ordered.verify(workflowAgent).consolidateWorkflowOutputs(any(), any());
            ordered.verify(workflowAgent).orchestrateTicketExecution(any(), any());
            ordered.verify(workflowAgent).dispatchTicketAgentRequests(any(), any());
            ordered.verify(ticketDispatchSubagent).run(any(AgentModels.TicketAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateTicketResults(any(), any());
            ordered.verify(workflowAgent).handleTicketCollectorBranch(any(), any());
            ordered.verify(workflowAgent).consolidateWorkflowOutputs(any(), any());
        }
    }

    private com.embabel.agent.core.Agent findWorkflowAgent() {
        return agentPlatform.agents().stream()
                .filter(a -> a.getName().equals(AgentInterfaces.WORKFLOW_AGENT_NAME))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("WorkflowAgent not found"));
    }

    private void enqueueHappyPath(String goal) {
        queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                .orchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                        .goal(goal)
                        .build())
                .build());

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
                        .output("Discovery output")
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                        .goal(goal)
                        .discoveryResults("discovery-results")
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
        enqueuePlanningToCompletion(goal);
    }

    private void enqueuePlanningToCompletion(String goal) {
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

    private void seedOrchestrator(String contextId) {
        graphRepository.save(createMockOrchestratorNode(contextId));
    }

    private OrchestratorNode createMockOrchestratorNode(String nodeId) {
        return OrchestratorNode.builder()
                .nodeId(nodeId)
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
}
