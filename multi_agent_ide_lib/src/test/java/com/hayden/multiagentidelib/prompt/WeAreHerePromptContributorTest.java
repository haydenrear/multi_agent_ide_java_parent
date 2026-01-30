package com.hayden.multiagentidelib.prompt;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.ContextId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WeAreHerePromptContributor")
class WeAreHerePromptContributorTest {

    private WeAreHerePromptContributor contributor;

    @BeforeEach
    void setUp() {
        contributor = new WeAreHerePromptContributor();
    }

    @Test
    @DisplayName("should have correct name")
    void shouldHaveCorrectName() {
        assertThat(contributor.name()).isEqualTo("workflow-position");
    }

    @Test
    @DisplayName("should have high priority")
    void shouldHaveHighPriority() {
        assertThat(contributor.priority()).isEqualTo(90);
    }

    @Test
    @DisplayName("should apply to all workflow agent types")
    void shouldApplyToWorkflowAgents() {
        var applicableAgents = contributor.applicableAgents();
        
        assertThat(applicableAgents).contains(
                AgentType.ORCHESTRATOR,
                AgentType.ORCHESTRATOR_COLLECTOR,
                AgentType.DISCOVERY_ORCHESTRATOR,
                AgentType.DISCOVERY_AGENT_DISPATCH,
                AgentType.DISCOVERY_COLLECTOR,
                AgentType.PLANNING_ORCHESTRATOR,
                AgentType.PLANNING_AGENT_DISPATCH,
                AgentType.PLANNING_COLLECTOR,
                AgentType.TICKET_ORCHESTRATOR,
                AgentType.TICKET_AGENT_DISPATCH,
                AgentType.TICKET_COLLECTOR,
                AgentType.REVIEW_AGENT,
                AgentType.MERGER_AGENT
        );
    }

    @Nested
    @DisplayName("Orchestrator Request")
    class OrchestratorRequestTests {
        
        @Test
        @DisplayName("should show orchestrator position in graph")
        void shouldShowOrchestratorPosition() {
            var request = new AgentModels.OrchestratorRequest("Test goal", "DISCOVERY");
            var context = buildContext(AgentType.ORCHESTRATOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains(">>> [Orchestrator] <<< YOU ARE HERE");
            assertThat(output).contains("Workflow Position");
            assertThat(output).contains("Execution History");
            assertThat(output).contains("Available Routing Options");
        }

        @Test
        @DisplayName("should show routing options for orchestrator")
        void shouldShowRoutingOptions() {
            var request = new AgentModels.OrchestratorRequest("Test goal", "DISCOVERY");
            var context = buildContext(AgentType.ORCHESTRATOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains("interruptRequest");
            assertThat(output).contains("collectorRequest");
            assertThat(output).contains("orchestratorRequest");
            assertThat(output).contains("For a new workflow, route to `orchestratorRequest` (Discovery Orchestrator)");
        }

        @Test
        @DisplayName("should show empty history for first request")
        void shouldShowEmptyHistory() {
            var request = new AgentModels.OrchestratorRequest("Test goal", "DISCOVERY");
            var context = buildContext(AgentType.ORCHESTRATOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains("No prior actions in this workflow run");
        }
    }

    @Nested
    @DisplayName("Discovery Orchestrator Request")
    class DiscoveryOrchestratorRequestTests {
        
        @Test
        @DisplayName("should show discovery orchestrator position")
        void shouldShowDiscoveryOrchestratorPosition() {
            var request = new AgentModels.DiscoveryOrchestratorRequest("Test goal");
            var history = new BlackboardHistory.History()
                    .withEntry("coordinateWorkflow", new AgentModels.OrchestratorRequest("Test goal", "DISCOVERY"));
            var context = buildContext(AgentType.DISCOVERY_ORCHESTRATOR, request, history);
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains(">>> [Discovery Orchestrator] <<< YOU ARE HERE");
            assertThat(output).contains("[Orchestrator] [visited]");
        }

        @Test
        @DisplayName("should show routing options for discovery orchestrator")
        void shouldShowRoutingOptions() {
            var request = new AgentModels.DiscoveryOrchestratorRequest("Test goal");
            var context = buildContext(AgentType.DISCOVERY_ORCHESTRATOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains("interruptRequest");
            assertThat(output).contains("agentRequests");
            assertThat(output).contains("collectorRequest");
            assertThat(output).contains("Dispatch discovery agents to explore the codebase");
        }

        @Test
        @DisplayName("should show execution history")
        void shouldShowExecutionHistory() {
            var request = new AgentModels.DiscoveryOrchestratorRequest("Test goal");
            var history = new BlackboardHistory.History()
                    .withEntry("coordinateWorkflow", new AgentModels.OrchestratorRequest("Test goal", "DISCOVERY"));
            var context = buildContext(AgentType.DISCOVERY_ORCHESTRATOR, request, history);
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains("| # | Action | Input Type |");
            assertThat(output).contains("| 1 | coordinateWorkflow | Orchestrator |");
        }
    }

    @Nested
    @DisplayName("Discovery Collector Request")
    class DiscoveryCollectorRequestTests {
        
        @Test
        @DisplayName("should show discovery collector position")
        void shouldShowDiscoveryCollectorPosition() {
            var request = new AgentModels.DiscoveryCollectorRequest("Test goal", "Discovery results here");
            var context = buildContext(AgentType.DISCOVERY_COLLECTOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains(">>> [Discovery Collector] <<< YOU ARE HERE");
        }

        @Test
        @DisplayName("should show routing options with branching guidance")
        void shouldShowRoutingOptions() {
            var request = new AgentModels.DiscoveryCollectorRequest("Test goal", "Discovery results");
            var context = buildContext(AgentType.DISCOVERY_COLLECTOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains("ADVANCE_PHASE");
            assertThat(output).contains("ROUTE_BACK");
            assertThat(output).contains("Planning Orchestrator");
            assertThat(output).contains("Discovery Orchestrator");
            assertThat(output).contains("branching handler will route based on your collector decision");
        }
    }

    @Nested
    @DisplayName("Planning Orchestrator Request")
    class PlanningOrchestratorRequestTests {
        
        @Test
        @DisplayName("should show planning orchestrator position")
        void shouldShowPlanningOrchestratorPosition() {
            var request = new AgentModels.PlanningOrchestratorRequest("Test goal");
            var context = buildContext(AgentType.PLANNING_ORCHESTRATOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains(">>> [Planning Orchestrator] <<< YOU ARE HERE");
        }

        @Test
        @DisplayName("should show routing options for planning orchestrator")
        void shouldShowRoutingOptions() {
            var request = new AgentModels.PlanningOrchestratorRequest("Test goal");
            var context = buildContext(AgentType.PLANNING_ORCHESTRATOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains("agentRequests");
            assertThat(output).contains("collectorRequest");
            assertThat(output).contains("Break down work into planning agents based on discovery results");
        }
    }

    @Nested
    @DisplayName("Planning Collector Request")
    class PlanningCollectorRequestTests {
        
        @Test
        @DisplayName("should show planning collector position")
        void shouldShowPlanningCollectorPosition() {
            var request = new AgentModels.PlanningCollectorRequest("Test goal", "Planning results");
            var context = buildContext(AgentType.PLANNING_COLLECTOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains(">>> [Planning Collector] <<< YOU ARE HERE");
        }

        @Test
        @DisplayName("should show routing options with branching guidance")
        void shouldShowRoutingOptions() {
            var request = new AgentModels.PlanningCollectorRequest("Test goal", "Planning results");
            var context = buildContext(AgentType.PLANNING_COLLECTOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains("ADVANCE_PHASE");
            assertThat(output).contains("ROUTE_BACK");
            assertThat(output).contains("Ticket Orchestrator");
            assertThat(output).contains("Planning Orchestrator");
            assertThat(output).contains("branching handler will route based on your collector decision");
        }
    }

    @Nested
    @DisplayName("Ticket Orchestrator Request")
    class TicketOrchestratorRequestTests {
        
        @Test
        @DisplayName("should show ticket orchestrator position")
        void shouldShowTicketOrchestratorPosition() {
            var request = new AgentModels.TicketOrchestratorRequest("Test goal");
            var context = buildContext(AgentType.TICKET_ORCHESTRATOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains(">>> [Ticket Orchestrator] <<< YOU ARE HERE");
        }

        @Test
        @DisplayName("should show routing options for ticket orchestrator")
        void shouldShowRoutingOptions() {
            var request = new AgentModels.TicketOrchestratorRequest("Test goal");
            var context = buildContext(AgentType.TICKET_ORCHESTRATOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains("agentRequests");
            assertThat(output).contains("collectorRequest");
            assertThat(output).contains("Dispatch ticket agents to implement the planned work");
        }
    }

    @Nested
    @DisplayName("Ticket Collector Request")
    class TicketCollectorRequestTests {
        
        @Test
        @DisplayName("should show ticket collector position")
        void shouldShowTicketCollectorPosition() {
            var request = new AgentModels.TicketCollectorRequest("Test goal", "Ticket results");
            var context = buildContext(AgentType.TICKET_COLLECTOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains(">>> [Ticket Collector] <<< YOU ARE HERE");
        }

        @Test
        @DisplayName("should show routing options with branching guidance")
        void shouldShowRoutingOptions() {
            var request = new AgentModels.TicketCollectorRequest("Test goal", "Ticket results");
            var context = buildContext(AgentType.TICKET_COLLECTOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains("ADVANCE_PHASE");
            assertThat(output).contains("ROUTE_BACK");
            assertThat(output).contains("Orchestrator Collector");
            assertThat(output).contains("Ticket Orchestrator");
            assertThat(output).contains("branching handler will route based on your collector decision");
        }
    }

    @Nested
    @DisplayName("Orchestrator Collector Request")
    class OrchestratorCollectorRequestTests {
        
        @Test
        @DisplayName("should show orchestrator collector position")
        void shouldShowOrchestratorCollectorPosition() {
            var request = new AgentModels.OrchestratorCollectorRequest("Test goal", "COMPLETE");
            var context = buildContext(AgentType.ORCHESTRATOR_COLLECTOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains(">>> [Orchestrator Collector] <<< YOU ARE HERE");
        }

        @Test
        @DisplayName("should show final consolidation guidance")
        void shouldShowFinalGuidance() {
            var request = new AgentModels.OrchestratorCollectorRequest("Test goal", "COMPLETE");
            var context = buildContext(AgentType.ORCHESTRATOR_COLLECTOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains("final consolidation step");
            assertThat(output).contains("ADVANCE_PHASE");
            assertThat(output).contains("ROUTE_BACK");
            assertThat(output).contains("requestedPhase=\"COMPLETE\"");
        }
    }

    @Nested
    @DisplayName("Review Request")
    class ReviewRequestTests {
        
        @Test
        @DisplayName("should show review agent position")
        void shouldShowReviewPosition() {
            var request = new AgentModels.ReviewRequest(
                    "Content to review",
                    "Review criteria",
                    null, null, null, null
            );
            var context = buildContext(AgentType.REVIEW_AGENT, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains("Available Routing Options");
            assertThat(output).contains("reviewResult");
        }
    }

    @Nested
    @DisplayName("Merger Request")
    class MergerRequestTests {
        
        @Test
        @DisplayName("should show merger agent position")
        void shouldShowMergerPosition() {
            var request = new AgentModels.MergerRequest(
                    "Merge context",
                    "Merge summary",
                    "Conflict files",
                    null, null, null, null
            );
            var context = buildContext(AgentType.MERGER_AGENT, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains("Available Routing Options");
            assertThat(output).contains("mergerResult");
        }
    }

    @Nested
    @DisplayName("Loop Detection")
    class LoopDetectionTests {
        
        @Test
        @DisplayName("should warn about potential loops")
        void shouldWarnAboutLoops() {
            var request = new AgentModels.DiscoveryOrchestratorRequest("Test goal");
            var history = new BlackboardHistory.History()
                    .withEntry("kickOffAgents", new AgentModels.DiscoveryOrchestratorRequest("Test goal"))
                    .withEntry("kickOffAgents", new AgentModels.DiscoveryOrchestratorRequest("Test goal"));
            var context = buildContext(AgentType.DISCOVERY_ORCHESTRATOR, request, history);
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains("Warning:");
            assertThat(output).contains("has been visited 2 times");
            assertThat(output).contains("Consider whether the workflow is making progress or looping");
        }

        @Test
        @DisplayName("should not warn for single visits")
        void shouldNotWarnForSingleVisits() {
            var request = new AgentModels.DiscoveryOrchestratorRequest("Test goal");
            var history = new BlackboardHistory.History()
                    .withEntry("kickOffAgents", new AgentModels.DiscoveryOrchestratorRequest("Test goal"));
            var context = buildContext(AgentType.DISCOVERY_ORCHESTRATOR, request, history);
            
            String output = contributor.contribute(context);
            
            assertThat(output).doesNotContain("Warning:");
        }
    }

    @Nested
    @DisplayName("Execution History Display")
    class ExecutionHistoryTests {
        
        @Test
        @DisplayName("should display complete execution path")
        void shouldDisplayCompletePath() {
            var request = new AgentModels.PlanningOrchestratorRequest("Test goal");
            var history = new BlackboardHistory.History()
                    .withEntry("coordinateWorkflow", new AgentModels.OrchestratorRequest("Test goal", "DISCOVERY"))
                    .withEntry("kickOffDiscovery", new AgentModels.DiscoveryOrchestratorRequest("Test goal"))
                    .withEntry("consolidateDiscovery", new AgentModels.DiscoveryCollectorRequest("Test goal", "results"));
            var context = buildContext(AgentType.PLANNING_ORCHESTRATOR, request, history);
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains("| 1 | coordinateWorkflow | Orchestrator |");
            assertThat(output).contains("| 2 | kickOffDiscovery | Discovery Orchestrator |");
            assertThat(output).contains("| 3 | consolidateDiscovery | Discovery Collector |");
        }

        @Test
        @DisplayName("should mark visited nodes in graph")
        void shouldMarkVisitedNodes() {
            var request = new AgentModels.PlanningOrchestratorRequest("Test goal");
            var history = new BlackboardHistory.History()
                    .withEntry("coordinateWorkflow", new AgentModels.OrchestratorRequest("Test goal", "DISCOVERY"))
                    .withEntry("kickOffDiscovery", new AgentModels.DiscoveryOrchestratorRequest("Test goal"));
            var context = buildContext(AgentType.PLANNING_ORCHESTRATOR, request, history);
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains("[Orchestrator] [visited]");
            assertThat(output).contains("[Discovery Orchestrator] [visited]");
            assertThat(output).contains(">>> [Planning Orchestrator] <<< YOU ARE HERE");
        }
    }

    @Nested
    @DisplayName("Workflow Graph Visualization")
    class WorkflowGraphTests {
        
        @Test
        @DisplayName("should show complete workflow graph")
        void shouldShowCompleteGraph() {
            var request = new AgentModels.OrchestratorRequest("Test goal", "DISCOVERY");
            var context = buildContext(AgentType.ORCHESTRATOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            // Check all major nodes are present
            assertThat(output).contains("[Orchestrator]");
            assertThat(output).contains("[Discovery Orchestrator]");
            assertThat(output).contains("[Discovery Agents]");
            assertThat(output).contains("[Discovery Collector]");
            assertThat(output).contains("[Planning Orchestrator]");
            assertThat(output).contains("[Planning Agents]");
            assertThat(output).contains("[Planning Collector]");
            assertThat(output).contains("[Ticket Orchestrator]");
            assertThat(output).contains("[Ticket Agents]");
            assertThat(output).contains("[Ticket Collector]");
            assertThat(output).contains("[Orchestrator Collector]");
        }

        @Test
        @DisplayName("should show collector result types")
        void shouldShowCollectorResults() {
            var request = new AgentModels.OrchestratorRequest("Test goal", "DISCOVERY");
            var context = buildContext(AgentType.ORCHESTRATOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            assertThat(output).contains("[DiscoveryCollectorResult]");
            assertThat(output).contains("[PlanningCollectorResult]");
            assertThat(output).contains("[TicketCollectorResult]");
        }

        @Test
        @DisplayName("should show branching options from collectors")
        void shouldShowBranchingOptions() {
            var request = new AgentModels.OrchestratorRequest("Test goal", "DISCOVERY");
            var context = buildContext(AgentType.ORCHESTRATOR, request, new BlackboardHistory.History());
            
            String output = contributor.contribute(context);
            
            // Discovery Collector branches
            assertThat(output).contains("Planning Orchestrator (ADVANCE)");
            assertThat(output).contains("Discovery Orchestrator (LOOP)");
            assertThat(output).contains("Orchestrator Collector (STOP)");
            
            // Planning Collector branches
            assertThat(output).contains("Ticket Orchestrator (ADVANCE)");
            assertThat(output).contains("Planning Orchestrator (LOOP)");
            assertThat(output).contains("Discovery Orchestrator (BACKTRACK)");
            
            // Ticket Collector branches
            assertThat(output).contains("Orchestrator Collector (ADVANCE)");
            assertThat(output).contains("Ticket Orchestrator (LOOP)");
            assertThat(output).contains("Planning Orchestrator (BACKTRACK)");
        }
    }

    // Helper method to build context
    private PromptContext buildContext(AgentType agentType, AgentModels.AgentRequest request, BlackboardHistory.History history) {
        return PromptContext.builder()
                .agentType(agentType)
                .currentContextId(new ContextId("test-context", agentType , 0, Instant.now()))
                .request(request)
                .blackboardHistory(history)
                .upstreamContexts(List.of())
                .metadata(Map.of())
                .build();
    }
}
