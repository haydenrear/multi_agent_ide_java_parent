package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.WorkflowAgentGraphNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt contributor that shows the agent where it is in the workflow graph,
 * what the history of execution has been, and what the available routing options mean.
 * 
 * This contributor uses a static template with placeholders for dynamic content,
 * ensuring the template hash remains stable across executions.
 */
@Component
public class WeAreHerePromptContributor implements PromptContributor {

    private static final String CURRENT_MARKER = ">>> YOU ARE HERE <<<";
    private static final String VISITED_MARKER = "[visited]";
    
    /**
     * Static template with placeholders for dynamic content.
     * Placeholders use Jinja2-style syntax: {{ variable_name }}
     */
    private static final String TEMPLATE = """
        ## Workflow Position
        
        ### Workflow Graph
        
        ```
        {{ node_orchestrator }}
            │ (returns OrchestratorRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If collectorRequest → Orchestrator Collector
            ├─▶ If orchestratorRequest → Discovery Orchestrator
            └─▶ If contextManagerRequest → Context Manager
            ▼
        {{ node_discovery_orchestrator }}
            │ (returns DiscoveryOrchestratorRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If agentRequests → Discovery Agents (dispatch)
            ├─▶ If collectorRequest → Discovery Collector
            └─▶ If contextManagerRequest → Context Manager
            ▼
        {{ node_discovery_agent_dispatch }}
            │ (returns DiscoveryAgentDispatchRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If collectorRequest → Discovery Collector
            └─▶ If contextManagerRequest → Context Manager
            ▼
        {{ node_discovery_agents }}
            │ (each agent returns DiscoveryAgentRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If agentResult → Discovery results
            ├─▶ If planningOrchestratorRequest → Planning Orchestrator
            └─▶ If contextManagerRequest → Context Manager
            ▼
        {{ node_discovery_collector }}
            │ (returns DiscoveryCollectorRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If collectorResult → handleDiscoveryCollectorBranch
            │     ├─▶ discoveryRequest (ROUTE_BACK)
            │     └─▶ planningRequest (ADVANCE_PHASE)
            ├─▶ If orchestratorRequest → Orchestrator
            ├─▶ If discoveryRequest → Discovery Orchestrator
            ├─▶ If planningRequest → Planning Orchestrator
            ├─▶ If ticketRequest → Ticket Orchestrator
            ├─▶ If reviewRequest → Review Agent
            ├─▶ If mergerRequest → Merger Agent
            └─▶ If contextManagerRequest → Context Manager
            ▼
        {{ node_planning_orchestrator }}
            │ (returns PlanningOrchestratorRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If agentRequests → Planning Agents (dispatch)
            ├─▶ If collectorRequest → Planning Collector
            └─▶ If contextManagerRequest → Context Manager
            ▼
        {{ node_planning_agent_dispatch }}
            │ (returns PlanningAgentDispatchRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If planningCollectorRequest → Planning Collector
            └─▶ If contextManagerRequest → Context Manager
            ▼
        {{ node_planning_agents }}
            │ (each agent returns PlanningAgentRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If agentResult → Planning results
            └─▶ If contextManagerRequest → Context Manager
            ▼
        {{ node_planning_collector }}
            │ (returns PlanningCollectorRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If collectorResult → handlePlanningCollectorBranch
            │     ├─▶ planningRequest (ROUTE_BACK)
            │     └─▶ ticketOrchestratorRequest (ADVANCE_PHASE)
            ├─▶ If planningRequest → Planning Orchestrator
            ├─▶ If ticketOrchestratorRequest → Ticket Orchestrator
            ├─▶ If discoveryOrchestratorRequest → Discovery Orchestrator
            ├─▶ If orchestratorCollectorRequest → Orchestrator Collector
            ├─▶ If reviewRequest → Review Agent
            ├─▶ If mergerRequest → Merger Agent
            └─▶ If contextManagerRequest → Context Manager
            ▼
        {{ node_ticket_orchestrator }}
            │ (returns TicketOrchestratorRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If agentRequests → Ticket Agents (dispatch)
            ├─▶ If collectorRequest → Ticket Collector
            └─▶ If contextManagerRequest → Context Manager
            ▼
        {{ node_ticket_agent_dispatch }}
            │ (returns TicketAgentDispatchRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If ticketCollectorRequest → Ticket Collector
            └─▶ If contextManagerRequest → Context Manager
            ▼
        {{ node_ticket_agents }}
            │ (each agent returns TicketAgentRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If agentResult → Ticket results
            └─▶ If contextManagerRequest → Context Manager
            ▼
        {{ node_ticket_collector }}
            │ (returns TicketCollectorRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If collectorResult → handleTicketCollectorBranch
            │     ├─▶ ticketRequest (ROUTE_BACK)
            │     └─▶ orchestratorCollectorRequest (ADVANCE_PHASE)
            ├─▶ If ticketRequest → Ticket Orchestrator
            ├─▶ If orchestratorCollectorRequest → Orchestrator Collector
            ├─▶ If reviewRequest → Review Agent
            ├─▶ If mergerRequest → Merger Agent
            └─▶ If contextManagerRequest → Context Manager
            ▼
        {{ node_orchestrator_collector }}
            │ (returns OrchestratorCollectorRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If collectorResult → handleOrchestratorCollectorBranch
            │     ├─▶ orchestratorRequest (ROUTE_BACK)
            │     └─▶ COMPLETE (ADVANCE_PHASE)
            ├─▶ If orchestratorRequest → Orchestrator
            ├─▶ If discoveryRequest → Discovery Orchestrator
            ├─▶ If planningRequest → Planning Orchestrator
            ├─▶ If ticketRequest → Ticket Orchestrator
            ├─▶ If reviewRequest → Review Agent
            ├─▶ If mergerRequest → Merger Agent
            └─▶ If contextManagerRequest → Context Manager
            ▼
          COMPLETE
        
        ─────────────────────────────────────────────────────────────────────────────
        SIDE NODES (can be reached from collectors and route back to collectors)
        ─────────────────────────────────────────────────────────────────────────────
        
        {{ node_review }}
            │ (returns ReviewRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If reviewResult → Review results
            ├─▶ If orchestratorCollectorRequest → Orchestrator Collector
            ├─▶ If discoveryCollectorRequest → Discovery Collector
            ├─▶ If planningCollectorRequest → Planning Collector
            ├─▶ If ticketCollectorRequest → Ticket Collector
            └─▶ If contextManagerRequest → Context Manager
        
        {{ node_merger }}
            │ (returns MergerRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If mergerResult → Merger results
            ├─▶ If orchestratorCollectorRequest → Orchestrator Collector
            ├─▶ If discoveryCollectorRequest → Discovery Collector
            ├─▶ If planningCollectorRequest → Planning Collector
            ├─▶ If ticketCollectorRequest → Ticket Collector
            └─▶ If contextManagerRequest → Context Manager
        
        {{ node_context_manager }}
            │ (returns ContextManagerResultRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If orchestratorRequest → Orchestrator
            ├─▶ If orchestratorCollectorRequest → Orchestrator Collector
            ├─▶ If discoveryOrchestratorRequest → Discovery Orchestrator
            ├─▶ If discoveryCollectorRequest → Discovery Collector
            ├─▶ If planningOrchestratorRequest → Planning Orchestrator
            ├─▶ If planningCollectorRequest → Planning Collector
            ├─▶ If ticketOrchestratorRequest → Ticket Orchestrator
            ├─▶ If ticketCollectorRequest → Ticket Collector
            ├─▶ If reviewRequest → Review Agent
            ├─▶ If mergerRequest → Merger Agent
            ├─▶ If planningAgentRequest → Planning Agent
            ├─▶ If planningAgentRequests → Planning Agent Dispatch
            ├─▶ If planningAgentResults → Planning Agent Results
            ├─▶ If ticketAgentRequest → Ticket Agent
            ├─▶ If ticketAgentRequests → Ticket Agent Dispatch
            ├─▶ If ticketAgentResults → Ticket Agent Results
            ├─▶ If discoveryAgentRequest → Discovery Agent
            ├─▶ If discoveryAgentRequests → Discovery Agent Dispatch
            ├─▶ If discoveryAgentResults → Discovery Agent Results
            └─▶ If contextOrchestratorRequest → Context Manager (recursive)
        ```
        
        ### Execution History
        
        {{ execution_history }}
        
        ### Available Routing Options
        
        {{ routing_options }}
        """;

    // Guidance templates for each request type - static text blocks
    private static final Map<Class<?>, String> GUIDANCE_TEMPLATES = initGuidanceTemplates();

    /**
     * @deprecated Use {@link NodeMappings#DISPLAY_NAMES} directly instead.
     */
    @Deprecated
    public static final Map<Class<?>, String> NODE_DISPLAY_NAMES = NodeMappings.DISPLAY_NAMES;

    private static Map<Class<?>, String> initGuidanceTemplates() {
        Map<Class<?>, String> templates = new HashMap<>();

        templates.put(AgentModels.OrchestratorRequest.class, """
            **Happy path:** For a new workflow, set `discoveryOrchestratorRequest` to start discovery.
            Only set `collectorRequest` when ALL workflow phases are complete.""");

        templates.put(AgentModels.DiscoveryOrchestratorRequest.class, """
            **Happy path:** Set `agentRequests` to dispatch discovery work, then later set
            `collectorRequest` when agents have gathered sufficient information.""");

        templates.put(AgentModels.DiscoveryCollectorRequest.class, """
            **Branching behavior when `collectorResult` is set:**
            - Framework calls `handleDiscoveryCollectorBranch` which interprets the decision
            - ADVANCE_PHASE → handler sets `planningRequest`
            - ROUTE_BACK → handler sets `discoveryRequest`

            **Most common:** Use `collectorResult` for standard flow control.""");

        templates.put(AgentModels.PlanningCollectorRequest.class, """
            **Branching behavior when `collectorResult` is set:**
            - Framework calls `handlePlanningCollectorBranch` which interprets the decision
            - ADVANCE_PHASE → handler sets `ticketOrchestratorRequest`
            - ROUTE_BACK → handler sets `planningRequest`

            **Most common:** Use `collectorResult` for standard flow control.""");

        templates.put(AgentModels.PlanningOrchestratorRequest.class, """
            **Happy path:** Set `agentRequests` to dispatch planning work, then later set
            `collectorRequest` when planning is complete.""");


        templates.put(AgentModels.TicketOrchestratorRequest.class, """
            **Happy path:** Set `agentRequests` to dispatch ticket execution work, then later set
            `collectorRequest` when implementation is complete.""");

        templates.put(AgentModels.TicketCollectorRequest.class, """
            **Branching behavior when `collectorResult` is set:**
            - Framework calls `handleTicketCollectorBranch` which interprets the decision
            - ADVANCE_PHASE → handler sets `orchestratorCollectorRequest`
            - ROUTE_BACK → handler sets `ticketRequest`

            **Most common:** Use `collectorResult` for standard flow control.""");

        templates.put(AgentModels.OrchestratorCollectorRequest.class, """
            **Branching behavior when `collectorResult` is set:**
            - Framework calls `handleOrchestratorCollectorBranch` which interprets the decision
            - ADVANCE_PHASE → workflow complete (requestedPhase="COMPLETE")
            - ROUTE_BACK → restart at specific phase

            **Most common:** Use `collectorResult` with ADVANCE_PHASE for workflow completion.""");

        templates.put(AgentModels.DiscoveryAgentRequest.class, """
            **Happy path:** Set `agentResult` with your discovery findings.""");

        templates.put(AgentModels.PlanningAgentRequest.class, """
            **Happy path:** Set `agentResult` with your planning tickets.""");

        templates.put(AgentModels.TicketAgentRequest.class, """
            **Happy path:** Set `agentResult` with your implementation results.""");

        templates.put(AgentModels.DiscoveryAgentResults.class, """
            **Happy path:** Set `collectorRequest` to consolidate discovery results.""");

        templates.put(AgentModels.PlanningAgentResults.class, """
            **Happy path:** Set `planningCollectorRequest` to consolidate planning results.""");

        templates.put(AgentModels.TicketAgentResults.class, """
            **Happy path:** Set `ticketCollectorRequest` to consolidate ticket results.""");

        templates.put(AgentModels.ReviewRequest.class, """
            **Return routes:** The request includes `returnTo*` fields indicating which collector
            invoked you. Route back to the appropriate collector after completing your review.

            **Happy path:** Set `reviewResult` with your review findings, then route back to
            the originating collector using the corresponding `*CollectorRequest` field.""");

        templates.put(AgentModels.MergerRequest.class, """
            **Return routes:** The request includes `returnTo*` fields indicating which collector
            invoked you. Route back to the appropriate collector after completing your merge validation.

            **Happy path:** Set `mergerResult` with your merge findings, then route back to
            the originating collector using the corresponding `*CollectorRequest` field.""");

        templates.put(AgentModels.ContextManagerRoutingRequest.class, """
            **Happy path:** Provide a concise `reason` and pick the most appropriate `type`
            (INTROSPECT_AGENT_CONTEXT or PROCEED).""");

        templates.put(AgentModels.ContextManagerRequest.class, """
            **Constraint:** Exactly one `returnTo*` field should be non-null in any ContextManagerRequest.

            **Happy path:** Route to the agent that can most directly act on the reconstructed context.""");

        templates.put(AgentModels.DiscoveryAgentRequests.class, """
            This is an intermediate dispatch state - the framework will collect results and route to
            `DiscoveryAgentDispatchRouting` which typically routes to `Discovery Collector`.""");

        templates.put(AgentModels.PlanningAgentRequests.class, """
            This is an intermediate dispatch state - the framework will collect results and route to
            `PlanningAgentDispatchRouting` which typically routes to `Planning Collector`.""");

        templates.put(AgentModels.TicketAgentRequests.class, """
            This is an intermediate dispatch state - the framework will collect results and route to
            `TicketAgentDispatchRouting` which typically routes to `Ticket Collector`.""");

        return Collections.unmodifiableMap(templates);
    }

    private static final String INTERRUPT_GUIDANCE = """

        **Interrupt guidance:** If uncertain, emit an `interruptRequest`. You may emit interrupts multiple times if more context is needed.
        Include `reason`, `contextForDecision`, `choices`, `confirmationItems`, and the agent-specific interrupt context fields.
        """;

    private static final String CONTEXT_MANAGER_GUIDANCE = """

        **Context guidance:** To request context reconstruction, set `contextManagerRequest`
        with a `ContextManagerRoutingRequest` (provide `reason` and `type`).
        """;

    @Override
    public String name() {
        return "workflow-position";
    }

    @Override
    public boolean include(PromptContext promptContext) {
        return true;
    }

    @Override
    public String template() {
        return TEMPLATE;
    }

    @Override
    public Map<String, Object> args() {
        // Base args are empty - actual args come from contribute() at runtime
        return Map.of();
    }

    @Override
    public String contribute(PromptContext context) {
        Map<String, Object> runtimeArgs = buildRuntimeArgs(context);
        return render(TEMPLATE, runtimeArgs);
    }

    @Override
    public int priority() {
        return 90;
    }

    /**
     * Build runtime arguments for template rendering based on context.
     */
    private Map<String, Object> buildRuntimeArgs(PromptContext context) {
        Map<String, Object> args = new HashMap<>();
        
        Class<?> currentRequestType = context.currentRequest() != null 
                ? context.currentRequest().getClass() 
                : null;
        List<Class<?>> visitedTypes = getVisitedTypes(context);

        // Main workflow nodes
        args.put("node_orchestrator", nodeDisplayFor(AgentModels.OrchestratorRequest.class, currentRequestType, visitedTypes));
        args.put("node_discovery_orchestrator", nodeDisplayFor(AgentModels.DiscoveryOrchestratorRequest.class, currentRequestType, visitedTypes));
        args.put("node_discovery_agent_dispatch", nodeDisplayFor(AgentModels.DiscoveryAgentRequests.class, currentRequestType, visitedTypes));
        args.put("node_discovery_agents", nodeDisplayFor(AgentModels.DiscoveryAgentRequest.class, currentRequestType, visitedTypes));
        args.put("node_discovery_collector", nodeDisplayFor(AgentModels.DiscoveryCollectorRequest.class, currentRequestType, visitedTypes));
        args.put("node_planning_orchestrator", nodeDisplayFor(AgentModels.PlanningOrchestratorRequest.class, currentRequestType, visitedTypes));
        args.put("node_planning_agent_dispatch", nodeDisplayFor(AgentModels.PlanningAgentRequests.class, currentRequestType, visitedTypes));
        args.put("node_planning_agents", nodeDisplayFor(AgentModels.PlanningAgentRequest.class, currentRequestType, visitedTypes));
        args.put("node_planning_collector", nodeDisplayFor(AgentModels.PlanningCollectorRequest.class, currentRequestType, visitedTypes));
        args.put("node_ticket_orchestrator", nodeDisplayFor(AgentModels.TicketOrchestratorRequest.class, currentRequestType, visitedTypes));
        args.put("node_ticket_agent_dispatch", nodeDisplayFor(AgentModels.TicketAgentRequests.class, currentRequestType, visitedTypes));
        args.put("node_ticket_agents", nodeDisplayFor(AgentModels.TicketAgentRequest.class, currentRequestType, visitedTypes));
        args.put("node_ticket_collector", nodeDisplayFor(AgentModels.TicketCollectorRequest.class, currentRequestType, visitedTypes));
        args.put("node_orchestrator_collector", nodeDisplayFor(AgentModels.OrchestratorCollectorRequest.class, currentRequestType, visitedTypes));

        // Side nodes (can be reached from collectors)
        args.put("node_review", nodeDisplayFor(AgentModels.ReviewRequest.class, currentRequestType, visitedTypes));
        args.put("node_merger", nodeDisplayFor(AgentModels.MergerRequest.class, currentRequestType, visitedTypes));
        args.put("node_context_manager", nodeDisplayFor(AgentModels.ContextManagerRequest.class, currentRequestType, visitedTypes));

        // Execution history
        args.put("execution_history", buildExecutionHistory(context));

        // Routing options
        args.put("routing_options", buildRoutingOptions(context));

        return args;
    }

    /**
     * Render template by substituting placeholders with values.
     */
    private String render(String template, Map<String, Object> args) {
        String rendered = template;
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String placeholder = "\\{\\{\\s*" + Pattern.quote(entry.getKey()) + "\\s*\\}\\}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            rendered = rendered.replaceAll(placeholder, Matcher.quoteReplacement(value));
        }
        return rendered;
    }

    /**
     * Format a node for display, marking current position and visited nodes.
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

    private String nodeName(Class<?> nodeType) {
        return NODE_DISPLAY_NAMES.getOrDefault(nodeType, WorkflowAgentGraphNode.getDisplayName(nodeType));
    }

    private String nodeDisplayFor(Class<?> nodeType, Class<?> currentType, List<Class<?>> visited) {
        return nodeDisplay(nodeName(nodeType), nodeType, currentType, visited);
    }

    /**
     * Get the list of request types that have been visited based on blackboard history.
     */
    private List<Class<?>> getVisitedTypes(PromptContext context) {
        List<Class<?>> visited = new ArrayList<>();

        if (context.blackboardHistory() == null) {
            return visited;
        }

        for (BlackboardHistory.Entry entry : context.blackboardHistory().copyOfEntries()) {
            switch (entry) {
                case BlackboardHistory.DefaultEntry defaultEntry -> {
                    if (defaultEntry.inputType() != null && !visited.contains(defaultEntry.inputType())) {
                        visited.add(defaultEntry.inputType());
                    }
                }
                case BlackboardHistory.MessageEntry ignored -> {
                }
            }
        }

        return visited;
    }

    /**
     * Build a summary of the execution history.
     */
    private String buildExecutionHistory(PromptContext context) {
        StringBuilder sb = new StringBuilder();

        if (context.blackboardHistory() == null || context.blackboardHistory().copyOfEntries().isEmpty()) {
            sb.append("_No prior actions in this workflow run._\n");
            return sb.toString();
        }

        List<BlackboardHistory.Entry> entries = context.blackboardHistory().copyOfEntries();

        sb.append("| # | Action | Input Type |\n");
        sb.append("|---|--------|------------|\n");

        int index = 1;
        for (BlackboardHistory.Entry entry : entries) {
            String actionName;
            String typeName;
            switch (entry) {
                case BlackboardHistory.DefaultEntry defaultEntry -> {
                    actionName = defaultEntry.actionName();
                    typeName = defaultEntry.inputType() != null
                            ? WorkflowAgentGraphNode.getDisplayName(defaultEntry.inputType())
                            : "unknown";
                }
                case BlackboardHistory.MessageEntry messageEntry -> {
                    actionName = messageEntry.actionName();
                    typeName = "MessageEvent";
                }
            }
            sb.append("| ").append(index++).append(" | ")
                    .append(actionName).append(" | ")
                    .append(typeName).append(" |\n");
        }

        // Add loop detection warning
        Map<Class<?>, Class<? extends AgentModels.Routing>> requestToRouting = NodeMappings.REQUEST_TO_ROUTING;
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
     * Build a description of available routing options for the current position.
     */
    private String buildRoutingOptions(PromptContext context) {
        StringBuilder sb = new StringBuilder();

        if (context.currentRequest() == null) {
            sb.append("_No request context available._\n");
            return sb.toString();
        }

        Class<?> requestType = context.currentRequest().getClass();
        Map<Class<?>, Class<? extends AgentModels.Routing>> requestToRouting = NodeMappings.REQUEST_TO_ROUTING;

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

        // Append shared guidance unconditionally
        sb.append(INTERRUPT_GUIDANCE);
        sb.append(CONTEXT_MANAGER_GUIDANCE);

        return sb.toString();
    }

    /**
     * Provide contextual guidance based on the current request type.
     */
    private String getContextualGuidance(Class<?> requestType) {
        return GUIDANCE_TEMPLATES.getOrDefault(requestType, "");
    }
}
