package com.hayden.multiagentidelib.prompt;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.BlackboardHistory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prompt contributor that shows the agent where it is in the workflow graph,
 * what the history of execution has been, and what the available routing options mean.
 */
public class WeAreHerePromptContributor implements PromptContributor {

    private static final String CURRENT_MARKER = ">>> YOU ARE HERE <<<";
    private static final String VISITED_MARKER = "[visited]";

    @Override
    public String name() {
        return "workflow-position";
    }

    @Override
    public Set<AgentType> applicableAgents() {
        // Apply to all workflow agents that make routing decisions
        return Set.of(
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

    @Override
    public String contribute(PromptContext context) {
        StringBuilder sb = new StringBuilder();

        sb.append("## Workflow Position\n\n");

        // Build the graph visualization
        sb.append(buildWorkflowGraph(context));

        // Show execution history
        sb.append("\n");
        sb.append(buildExecutionHistory(context));

        // Show routing options for current position
        sb.append("\n");
        sb.append(buildRoutingOptions(context));

        return sb.toString();
    }

    @Override
    public int priority() {
        return 90; // High priority - show near the top of the prompt
    }

    /**
     * Build an ASCII representation of the workflow graph with "You are here" marker
     */
    private String buildWorkflowGraph(PromptContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Workflow Graph\n\n");
        sb.append("```\n");

        Class<?> currentRequestType = context.request() != null ? context.request().getClass() : null;
        List<Class<?>> visitedTypes = getVisitedTypes(context);

        // Main orchestrator level
        sb.append(nodeDisplay("Orchestrator", AgentModels.OrchestratorRequest.class, currentRequestType, visitedTypes));
        sb.append("\n    │ (returns OrchestratorRouting)\n");
        sb.append("    ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)\n");
        sb.append("    ├─▶ If collectorRequest → Orchestrator Collector\n");
        sb.append("    └─▶ If orchestratorRequest → Discovery Orchestrator\n");
        sb.append("    ▼\n");

        // Discovery phase
        sb.append(nodeDisplay("Discovery Orchestrator", AgentModels.DiscoveryOrchestratorRequest.class, currentRequestType, visitedTypes));
        sb.append("\n    │ (returns DiscoveryOrchestratorRouting)\n");
        sb.append("    ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)\n");
        sb.append("    ├─▶ If agentRequests → Discovery Agents (dispatch)\n");
        sb.append("    └─▶ If collectorRequest → Discovery Collector\n");
        sb.append("    ▼\n");

        sb.append(nodeDisplay("Discovery Agents", AgentModels.DiscoveryAgentRequests.class, currentRequestType, visitedTypes));
        sb.append("\n    │ (each agent returns DiscoveryAgentRouting)\n");
        sb.append("    ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)\n");
        sb.append("    ├─▶ If agentResult → Discovery results\n");
        sb.append("    └─▶ If planningOrchestratorRequest → Planning Orchestrator\n");
        sb.append("    ▼\n");

        sb.append(nodeDisplay("Discovery Collector", AgentModels.DiscoveryCollectorRequest.class, currentRequestType, visitedTypes));
        sb.append("\n    │ (returns DiscoveryCollectorRouting)\n");
        sb.append("    ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)\n");
        sb.append("    ├─▶ If collectorResult → handleDiscoveryCollectorBranch\n");
        sb.append("    │     ├─▶ discoveryRequest (ROUTE_BACK)\n");
        sb.append("    │     └─▶ planningRequest (ADVANCE_PHASE)\n");
        sb.append("    ├─▶ If discoveryRequest → Discovery Orchestrator\n");
        sb.append("    ├─▶ If planningRequest → Planning Orchestrator\n");
        sb.append("    ├─▶ If orchestratorRequest → Orchestrator\n");
        sb.append("    └─▶ If reviewRequest/mergerRequest → Review/Merger\n");
        sb.append("    ▼\n");

        // Planning phase
        sb.append(nodeDisplay("Planning Orchestrator", AgentModels.PlanningOrchestratorRequest.class, currentRequestType, visitedTypes));
        sb.append("\n    │ (returns PlanningOrchestratorRouting)\n");
        sb.append("    ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)\n");
        sb.append("    ├─▶ If agentRequests → Planning Agents (dispatch)\n");
        sb.append("    └─▶ If collectorRequest → Planning Collector\n");
        sb.append("    ▼\n");

        sb.append(nodeDisplay("Planning Agents", AgentModels.PlanningAgentRequests.class, currentRequestType, visitedTypes));
        sb.append("\n    │ (each agent returns PlanningAgentRouting)\n");
        sb.append("    ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)\n");
        sb.append("    └─▶ If agentResult → Planning results\n");
        sb.append("    ▼\n");

        sb.append(nodeDisplay("Planning Collector", AgentModels.PlanningCollectorRequest.class, currentRequestType, visitedTypes));
        sb.append("\n    │ (returns PlanningCollectorRouting)\n");
        sb.append("    ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)\n");
        sb.append("    ├─▶ If collectorResult → handlePlanningCollectorBranch\n");
        sb.append("    │     ├─▶ planningRequest (ROUTE_BACK)\n");
        sb.append("    │     └─▶ ticketOrchestratorRequest (ADVANCE_PHASE)\n");
        sb.append("    ├─▶ If planningRequest → Planning Orchestrator\n");
        sb.append("    ├─▶ If ticketOrchestratorRequest → Ticket Orchestrator\n");
        sb.append("    ├─▶ If discoveryOrchestratorRequest → Discovery Orchestrator\n");
        sb.append("    ├─▶ If orchestratorCollectorRequest → Orchestrator Collector\n");
        sb.append("    └─▶ If reviewRequest/mergerRequest → Review/Merger\n");
        sb.append("    ▼\n");

        // Ticket phase
        sb.append(nodeDisplay("Ticket Orchestrator", AgentModels.TicketOrchestratorRequest.class, currentRequestType, visitedTypes));
        sb.append("\n    │ (returns TicketOrchestratorRouting)\n");
        sb.append("    ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)\n");
        sb.append("    ├─▶ If agentRequests → Ticket Agents (dispatch)\n");
        sb.append("    └─▶ If collectorRequest → Ticket Collector\n");
        sb.append("    ▼\n");

        sb.append(nodeDisplay("Ticket Agents", AgentModels.TicketAgentRequests.class, currentRequestType, visitedTypes));
        sb.append("\n    │ (each agent returns TicketAgentRouting)\n");
        sb.append("    ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)\n");
        sb.append("    └─▶ If agentResult → Ticket results\n");
        sb.append("    ▼\n");

        sb.append(nodeDisplay("Ticket Collector", AgentModels.TicketCollectorRequest.class, currentRequestType, visitedTypes));
        sb.append("\n    │ (returns TicketCollectorRouting)\n");
        sb.append("    ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)\n");
        sb.append("    ├─▶ If collectorResult → handleTicketCollectorBranch\n");
        sb.append("    │     ├─▶ ticketRequest (ROUTE_BACK)\n");
        sb.append("    │     └─▶ orchestratorCollectorRequest (ADVANCE_PHASE)\n");
        sb.append("    ├─▶ If ticketRequest → Ticket Orchestrator\n");
        sb.append("    ├─▶ If orchestratorCollectorRequest → Orchestrator Collector\n");
        sb.append("    └─▶ If reviewRequest/mergerRequest → Review/Merger\n");
        sb.append("    ▼\n");

        // Final collector
        sb.append(nodeDisplay("Orchestrator Collector", AgentModels.OrchestratorCollectorRequest.class, currentRequestType, visitedTypes));
        sb.append("\n    │ (returns OrchestratorCollectorRouting)\n");
        sb.append("    ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)\n");
        sb.append("    ├─▶ If collectorResult → handleOrchestratorCollectorBranch\n");
        sb.append("    │     ├─▶ orchestratorRequest (ROUTE_BACK)\n");
        sb.append("    │     └─▶ COMPLETE (ADVANCE_PHASE)\n");
        sb.append("    ├─▶ If orchestratorRequest → Orchestrator\n");
        sb.append("    ├─▶ If discoveryRequest → Discovery Orchestrator\n");
        sb.append("    ├─▶ If planningRequest → Planning Orchestrator\n");
        sb.append("    ├─▶ If ticketRequest → Ticket Orchestrator\n");
        sb.append("    └─▶ If reviewRequest/mergerRequest → Review/Merger\n");
        sb.append("    ▼\n");
        sb.append("  COMPLETE\n");

        sb.append("```\n");

        return sb.toString();
    }

    /**
     * Format a node for display, marking current position and visited nodes
     */
    private String nodeDisplay(String name, Class<?> nodeType, Class<?> currentType, List<Class<?>> visited) {
        StringBuilder sb = new StringBuilder();

        boolean isCurrent = nodeType.equals(currentType);
        boolean isVisited = visited.contains(nodeType);

        if (isCurrent) {
            sb.append(">>> ");
        } else {
            sb.append("    ");
        }

        sb.append("[").append(name).append("]");

        if (isCurrent) {
            sb.append(" <<< YOU ARE HERE");
        } else if (isVisited) {
            sb.append(" ").append(VISITED_MARKER);
        }

        return sb.toString();
    }

    /**
     * Get the list of request types that have been visited based on blackboard history
     */
    private List<Class<?>> getVisitedTypes(PromptContext context) {
        List<Class<?>> visited = new ArrayList<>();

        if (context.blackboardHistory() == null) {
            return visited;
        }

        for (BlackboardHistory.Entry entry : context.blackboardHistory().entries()) {
            if (entry.inputType() != null && !visited.contains(entry.inputType())) {
                visited.add(entry.inputType());
            }
        }

        return visited;
    }

    /**
     * Build a summary of the execution history
     */
    private String buildExecutionHistory(PromptContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Execution History\n\n");

        if (context.blackboardHistory() == null || context.blackboardHistory().entries().isEmpty()) {
            sb.append("_No prior actions in this workflow run._\n");
            return sb.toString();
        }

        List<BlackboardHistory.Entry> entries = context.blackboardHistory().entries();

        sb.append("| # | Action | Input Type |\n");
        sb.append("|---|--------|------------|\n");

        int index = 1;
        for (BlackboardHistory.Entry entry : entries) {
            String typeName = entry.inputType() != null
                    ? WorkflowAgentGraphNode.getDisplayName(entry.inputType())
                    : "unknown";
            sb.append("| ").append(index++).append(" | ")
              .append(entry.actionName()).append(" | ")
              .append(typeName).append(" |\n");
        }

        // Add loop detection warning
        Map<Class<?>, Class<?>> requestToRouting = WorkflowAgentGraphNode.getRequestToRoutingMap();
        for (Class<?> requestType : requestToRouting.keySet()) {
            long count = context.blackboardHistory().countType(requestType);
            if (count >= 2) {
                sb.append("\n**Warning:** ")
                  .append(WorkflowAgentGraphNode.getDisplayName(requestType))
                  .append(" has been visited ").append(count).append(" times. ")
                  .append("Consider whether the workflow is making progress or looping.\n");
            }
        }

        return sb.toString();
    }

    /**
     * Build a description of available routing options for the current position
     */
    private String buildRoutingOptions(PromptContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Available Routing Options\n\n");

        if (context.request() == null) {
            sb.append("_No request context available._\n");
            return sb.toString();
        }

        Class<?> requestType = context.request().getClass();
        Map<Class<?>, Class<?>> requestToRouting = WorkflowAgentGraphNode.getRequestToRoutingMap();

        Class<?> routingType = requestToRouting.get(requestType);
        if (routingType == null) {
            sb.append("_No routing options defined for this request type._\n");
            return sb.toString();
        }

        sb.append("From **").append(WorkflowAgentGraphNode.getDisplayName(requestType))
          .append("**, you can route to:\n\n");

        List<WorkflowAgentGraphNode.RoutingBranch> branches =
                WorkflowAgentGraphNode.buildBranchesFromRouting(routingType);

        for (WorkflowAgentGraphNode.RoutingBranch branch : branches) {
            sb.append("- **").append(branch.fieldName()).append("** → ")
              .append(branch.description()).append("\n");
        }

        // Add guidance based on request type
        sb.append("\n");
        sb.append(getContextualGuidance(requestType));

        return sb.toString();
    }

    /**
     * Provide contextual guidance based on the current request type
     */
    private String getContextualGuidance(Class<?> requestType) {
        if (requestType.equals(AgentModels.OrchestratorRequest.class)) {
            return """
                **Guidance:** You must return an `OrchestratorRouting` with exactly ONE field set.

                **Routing options:**
                - `interruptRequest` - Pause or stop execution per interrupt request
                - `collectorRequest` - Route to Orchestrator Collector (final consolidation)
                - `orchestratorRequest` - Route to Discovery Orchestrator

                **Happy path:** For a new workflow, set `orchestratorRequest` to start discovery.
                Only set `collectorRequest` when ALL workflow phases are complete.
                """ + interruptGuidance();
        }

        if (requestType.equals(AgentModels.DiscoveryOrchestratorRequest.class)) {
            return """
                **Guidance:** You must return a `DiscoveryOrchestratorRouting` with exactly ONE field set.

                **Routing options:**
                - `interruptRequest` - Pause or stop execution per interrupt request
                - `agentRequests` - Delegate to multiple discovery agents (contains list of DiscoveryAgentRequest)
                - `collectorRequest` - Route to Discovery Collector to consolidate results

                **Happy path:** Set `agentRequests` to dispatch discovery work, then later set
                `collectorRequest` when agents have gathered sufficient information.
                """ + interruptGuidance();
        }

        if (requestType.equals(AgentModels.DiscoveryCollectorRequest.class)) {
            return """
                **Guidance:** You must return a `DiscoveryCollectorRouting` with exactly ONE field set.

                **Two routing options:**

                1. **Direct routing** - Set one of these fields directly:
                   - `discoveryRequest` - Route back to Discovery Orchestrator
                   - `planningRequest` - Route to Planning Orchestrator
                   - `orchestratorRequest` - Route to main Orchestrator
                   - `reviewRequest` / `mergerRequest` - Route to review/merge

                2. **Branching routing** - Set `collectorResult` with a `DiscoveryCollectorResult`:
                   - The `collectorResult` contains a `CollectorDecision` with `decisionType`
                   - Framework calls `handleDiscoveryCollectorBranch` which interprets the decision
                   - ADVANCE_PHASE → handler sets `planningRequest`
                   - ROUTE_BACK → handler sets `discoveryRequest`

                **Most common:** Use option 2 (set `collectorResult`) for standard flow control.
                """ + interruptGuidance();
        }

        if (requestType.equals(AgentModels.PlanningOrchestratorRequest.class)) {
            return """
                **Guidance:** You must return a `PlanningOrchestratorRouting` with exactly ONE field set.

                **Routing options:**
                - `interruptRequest` - Pause or stop execution per interrupt request
                - `agentRequests` - Delegate to multiple planning agents (contains list of PlanningAgentRequest)
                - `collectorRequest` - Route to Planning Collector to consolidate results

                **Happy path:** Set `agentRequests` to dispatch planning work, then later set
                `collectorRequest` when planning is complete.
                """ + interruptGuidance();
        }

        if (requestType.equals(AgentModels.PlanningCollectorRequest.class)) {
            return """
                **Guidance:** You must return a `PlanningCollectorRouting` with exactly ONE field set.

                **Two routing options:**

                1. **Direct routing** - Set one of these fields directly:
                   - `planningRequest` - Route back to Planning Orchestrator
                   - `ticketOrchestratorRequest` - Route to Ticket Orchestrator
                   - `discoveryOrchestratorRequest` - Route to Discovery Orchestrator
                   - `orchestratorCollectorRequest` - Route to Orchestrator Collector
                   - `reviewRequest` / `mergerRequest` - Route to review/merge

                2. **Branching routing** - Set `collectorResult` with a `PlanningCollectorResult`:
                   - The `collectorResult` contains a `CollectorDecision` with `decisionType`
                   - Framework calls `handlePlanningCollectorBranch` which interprets the decision
                   - ADVANCE_PHASE → handler sets `ticketOrchestratorRequest`
                   - ROUTE_BACK → handler sets `planningRequest`

                **Most common:** Use option 2 (set `collectorResult`) for standard flow control.
                """ + interruptGuidance();
        }

        if (requestType.equals(AgentModels.TicketOrchestratorRequest.class)) {
            return """
                **Guidance:** You must return a `TicketOrchestratorRouting` with exactly ONE field set.

                **Routing options:**
                - `interruptRequest` - Pause or stop execution per interrupt request
                - `agentRequests` - Delegate to multiple ticket agents (contains list of TicketAgentRequest)
                - `collectorRequest` - Route to Ticket Collector to consolidate results

                **Happy path:** Set `agentRequests` to dispatch ticket execution work, then later set
                `collectorRequest` when implementation is complete.
                """ + interruptGuidance();
        }

        if (requestType.equals(AgentModels.TicketCollectorRequest.class)) {
            return """
                **Guidance:** You must return a `TicketCollectorRouting` with exactly ONE field set.

                **Two routing options:**

                1. **Direct routing** - Set one of these fields directly:
                   - `ticketRequest` - Route back to Ticket Orchestrator
                   - `orchestratorCollectorRequest` - Route to Orchestrator Collector
                   - `reviewRequest` / `mergerRequest` - Route to review/merge

                2. **Branching routing** - Set `collectorResult` with a `TicketCollectorResult`:
                   - The `collectorResult` contains a `CollectorDecision` with `decisionType`
                   - Framework calls `handleTicketCollectorBranch` which interprets the decision
                   - ADVANCE_PHASE → handler sets `orchestratorCollectorRequest`
                   - ROUTE_BACK → handler sets `ticketRequest`

                **Most common:** Use option 2 (set `collectorResult`) for standard flow control.
                """ + interruptGuidance();
        }

        if (requestType.equals(AgentModels.OrchestratorCollectorRequest.class)) {
            return """
                **Guidance:** You must return an `OrchestratorCollectorRouting` with exactly ONE field set.

                **Two routing options:**

                1. **Direct routing** - Set one of these fields directly:
                   - `orchestratorRequest` - Route back to Orchestrator
                   - `discoveryRequest` - Route to Discovery Orchestrator
                   - `planningRequest` - Route to Planning Orchestrator
                   - `ticketRequest` - Route to Ticket Orchestrator
                   - `reviewRequest` / `mergerRequest` - Route to review/merge

                2. **Branching routing** - Set `collectorResult` with an `OrchestratorCollectorResult`:
                   - The `collectorResult` contains a `CollectorDecision` with `decisionType`
                   - Framework calls `handleOrchestratorCollectorBranch` which interprets the decision
                   - ADVANCE_PHASE → workflow complete (requestedPhase="COMPLETE")
                   - ROUTE_BACK → restart at specific phase

                **Most common:** Use option 2 with ADVANCE_PHASE for workflow completion.
                """ + interruptGuidance();
        }

        // Discovery Agent
        if (requestType.equals(AgentModels.DiscoveryAgentRequest.class)) {
            return """
                **Guidance:** You must return a `DiscoveryAgentRouting` with exactly ONE field set.

                **Routing options:**
                - `interruptRequest` - Pause or stop execution per interrupt request
                - `agentResult` - Return discovery findings (contains DiscoveryReport)
                - `planningOrchestratorRequest` - Bypass to Planning Orchestrator (rare)

                **Happy path:** Set `agentResult` with your discovery findings.
                """ + interruptGuidance();
        }

        // Planning Agent
        if (requestType.equals(AgentModels.PlanningAgentRequest.class)) {
            return """
                **Guidance:** You must return a `PlanningAgentRouting` with exactly ONE field set.

                **Routing options:**
                - `interruptRequest` - Pause or stop execution per interrupt request
                - `agentResult` - Return planning results (contains tickets)

                **Happy path:** Set `agentResult` with your planning tickets.
                """ + interruptGuidance();
        }

        // Ticket Agent
        if (requestType.equals(AgentModels.TicketAgentRequest.class)) {
            return """
                **Guidance:** You must return a `TicketAgentRouting` with exactly ONE field set.

                **Routing options:**
                - `interruptRequest` - Pause or stop execution per interrupt request
                - `agentResult` - Return ticket execution results (files modified, tests, commits)

                **Happy path:** Set `agentResult` with your implementation results.
                """ + interruptGuidance();
        }

        if (requestType.equals(AgentModels.DiscoveryAgentResults.class)) {
            return """
                **Guidance:** You must return a `DiscoveryAgentDispatchRouting` with exactly ONE field set.

                **Routing options:**
                - `interruptRequest` - Pause or stop execution per interrupt request
                - `collectorRequest` - Route to Discovery Collector

                **Happy path:** Set `collectorRequest` to consolidate discovery results.
                """ + interruptGuidance();
        }

        if (requestType.equals(AgentModels.PlanningAgentResults.class)) {
            return """
                **Guidance:** You must return a `PlanningAgentDispatchRouting` with exactly ONE field set.

                **Routing options:**
                - `interruptRequest` - Pause or stop execution per interrupt request
                - `planningCollectorRequest` - Route to Planning Collector

                **Happy path:** Set `planningCollectorRequest` to consolidate planning results.
                """ + interruptGuidance();
        }

        if (requestType.equals(AgentModels.TicketAgentResults.class)) {
            return """
                **Guidance:** You must return a `TicketAgentDispatchRouting` with exactly ONE field set.

                **Routing options:**
                - `interruptRequest` - Pause or stop execution per interrupt request
                - `ticketCollectorRequest` - Route to Ticket Collector

                **Happy path:** Set `ticketCollectorRequest` to consolidate ticket results.
                """ + interruptGuidance();
        }

        return "";
    }

    private String interruptGuidance() {
        return """

                **Interrupt guidance:** If uncertain, emit an `interruptRequest`. You may emit interrupts multiple times if more context is needed.
                Include `reason`, `contextForDecision`, `choices`, `confirmationItems`, and the agent-specific interrupt context fields.
                """;
    }
}
