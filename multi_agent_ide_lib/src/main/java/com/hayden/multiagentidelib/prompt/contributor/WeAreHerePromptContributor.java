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
     * Non-workflow request/result types to exclude from execution history.
     * These are internal routing and tooling requests that clutter the prompt.
     */
    private static final Set<Class<?>> NON_WORKFLOW_TYPES = Set.of(
            AgentModels.CommitAgentRequest.class,
            AgentModels.AiFilterRequest.class,
            AgentModels.AiPropagatorRequest.class,
            AgentModels.AiTransformerRequest.class,
            AgentModels.MergeConflictRequest.class,
            AgentModels.AgentToAgentRequest.class,
            AgentModels.AgentToControllerRequest.class,
            AgentModels.ControllerToAgentRequest.class,
            AgentModels.AiPropagatorResult.class,
            AgentModels.AiFilterResult.class,
            AgentModels.AiTransformerResult.class,
            AgentModels.CommitAgentResult.class,
            AgentModels.MergeConflictResult.class,
            AgentModels.AgentCallResult.class,
            AgentModels.ControllerCallResult.class,
            AgentModels.ControllerResponseResult.class
    );

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
            ├─▶ If collectorRequest → Discovery Collector (primary forward path; consolidate all discovery agent outputs)
            └─▶ If contextManagerRequest → Context Manager
            ▼
        {{ node_discovery_agents }}
            │ (each agent returns DiscoveryAgentRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If agentResult → Discovery results (include enough findings/context for collector to decide ADVANCE_PHASE vs ROUTE_BACK)
            └─▶ If contextManagerRequest → Context Manager
            ▼
        {{ node_discovery_collector }}
            │ (returns DiscoveryCollectorRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If collectorResult → apply discovery collector decision
            │     ├─▶ ROUTE_BACK → request interrupt clarification first, then Discovery Orchestrator if confirmed
            │     └─▶ ADVANCE_PHASE → Planning Orchestrator
            ├─▶ If orchestratorRequest → Orchestrator
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
            ├─▶ If planningCollectorRequest → Planning Collector (primary forward path; consolidate all planning agent outputs)
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
            ├─▶ If collectorResult → apply planning collector decision
            │     ├─▶ ROUTE_BACK → request interrupt clarification first, then Planning Orchestrator if confirmed
            │     └─▶ ADVANCE_PHASE → Ticket Orchestrator
            ├─▶ If orchestratorRequest → Orchestrator
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
            ├─▶ If ticketCollectorRequest → Ticket Collector (primary forward path; consolidate all ticket agent outputs)
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
            ├─▶ If collectorResult → apply ticket collector decision
            │     ├─▶ ROUTE_BACK → request interrupt clarification first, then Ticket Orchestrator if confirmed
            │     └─▶ ADVANCE_PHASE → Orchestrator Collector (final)
            ├─▶ If orchestratorRequest → Orchestrator
            ├─▶ If mergerRequest → Merger Agent
            └─▶ If contextManagerRequest → Context Manager
            ▼
        {{ node_orchestrator_collector }}
            │ (returns OrchestratorCollectorRouting)
            ├─▶ If interruptRequest → Interrupt (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP)
            ├─▶ If collectorResult → apply final collector decision
            │     ├─▶ ROUTE_BACK → request interrupt clarification first, then Orchestrator if confirmed
            │     └─▶ COMPLETE goal and COMPLETE process (ADVANCE_PHASE)
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
            - Collector decision is interpreted by the discovery collector branch flow
            - ADVANCE_PHASE advances to Planning Orchestrator
            - ROUTE_BACK: you must first set interruptRequest for review; after review feedback, set collectorResult with the reviewer's decision
            - STOP stops execution

            **Route-Back Review:** To ROUTE_BACK, first return a structured JSON response with only `interruptRequest` populated (leave collectorResult null).
            After review, if approved: return `collectorResult` with ROUTE_BACK so discovery runs again.
            After review, if rejected: return `collectorResult` with ADVANCE_PHASE and requestedPhase = "PLANNING",
            filling in unifiedCodeMap, recommendations, and querySpecificFindings with the best available data.

            **Most common:** Use `collectorResult` for standard flow control.""");
        templates.put(AgentModels.PlanningCollectorRequest.class, """
            **Branching behavior when `collectorResult` is set:**
            - Collector decision is interpreted by the planning collector branch flow
            - ADVANCE_PHASE advances to Ticket Orchestrator
            - ROUTE_BACK: you must first set interruptRequest for review; after review feedback, set collectorResult with the reviewer's decision
            - STOP stops execution

            **Route-Back Review:** To ROUTE_BACK, first return a structured JSON response with only `interruptRequest` populated (leave collectorResult null).
            After review, if approved: return `collectorResult` with ROUTE_BACK so planning runs again.
            After review, if rejected: return `collectorResult` with ADVANCE_PHASE and requestedPhase = "TICKETS",
            filling in finalizedTickets and dependencyGraph with the best available plan.

            **Most common:** Use `collectorResult` for standard flow control.""");

        templates.put(AgentModels.PlanningOrchestratorRequest.class, """
            **Happy path:** Set `agentRequests` to dispatch planning work, then later set
            `collectorRequest` when planning is complete.""");


        templates.put(AgentModels.TicketOrchestratorRequest.class, """
            **Happy path:** Set `agentRequests` to dispatch ticket execution work, then later set
            `collectorRequest` when implementation is complete.""");
        templates.put(AgentModels.TicketCollectorRequest.class, """
            **Branching behavior when `collectorResult` is set:**
            - Collector decision is interpreted by the ticket collector branch flow
            - ADVANCE_PHASE advances to Orchestrator Collector (final)
            - ROUTE_BACK: you must first set interruptRequest for review; after review feedback, set collectorResult with the reviewer's decision
            - STOP stops execution

            **Route-Back Review:** To ROUTE_BACK, first return a structured JSON response with only `interruptRequest` populated (leave collectorResult null).
            After review, if approved: return `collectorResult` with ROUTE_BACK so ticket execution runs again.
            After review, if rejected: return `collectorResult` with ADVANCE_PHASE and requestedPhase = "COMPLETE",
            filling in completionStatus and followUps with what was accomplished and what remains.

            **Most common:** Use `collectorResult` for standard flow control.""");

        templates.put(AgentModels.OrchestratorCollectorRequest.class, """
            **Branching behavior when `collectorResult` is set:**
            - Collector decision is interpreted by the final collector branch flow
            - ADVANCE_PHASE → workflow complete (requestedPhase="COMPLETE")
            - ROUTE_BACK: you must first set interruptRequest for review; after review feedback, set collectorResult with the reviewer's decision
            - At this stage, validate ticket completion against the goal and prefer completion when done

            **Route-Back Review:** To ROUTE_BACK, first return a structured JSON response with only `interruptRequest` populated (leave collectorResult null).
            After review, if approved: return `collectorResult` with ROUTE_BACK. Widen the goal in consolidatedOutput
            to cover both the original goal and unresolved gaps. Populate discoveryCollectorResult,
            planningCollectorResult, and ticketCollectorResult so context is preserved.
            After review, if rejected: return `collectorResult` with ADVANCE_PHASE and requestedPhase = "COMPLETE",
            summarizing accomplishments and noting limitations in consolidatedOutput.

            **Most common:** Use `collectorResult` with ADVANCE_PHASE for workflow completion.""");

        templates.put(AgentModels.DiscoveryAgentRequest.class, """
            **Happy path:** Set `agentResult` with your discovery findings and clear next-step signal
            in the report content (what is complete, what is missing, and what should happen next).
            Discovery agents do not route directly to planning; dispatch+collector handle phase advancement.""");

        templates.put(AgentModels.PlanningAgentRequest.class, """
            **Happy path:** Set `agentResult` with planning tickets and an explicit completion signal
            (ready for ticket phase vs needs more planning context). Phase advancement is handled by collector routing.""");

        templates.put(AgentModels.TicketAgentRequest.class, """
            **Happy path:** Set `agentResult` with implementation results plus merge/readiness context
            so the collector can choose advance vs route-back without ambiguity.""");

        templates.put(AgentModels.DiscoveryAgentResults.class, """
            **Happy path:** Set `collectorRequest` to consolidate discovery results.""");

        templates.put(AgentModels.PlanningAgentResults.class, """
            **Happy path:** Set `planningCollectorRequest` to consolidate planning results.""");

        templates.put(AgentModels.TicketAgentResults.class, """
            **Happy path:** Set `ticketCollectorRequest` to consolidate ticket results.""");

        templates.put(AgentModels.ContextManagerRoutingRequest.class, """
            **Happy path:** Provide a concise `reason` and pick the most appropriate `type`
            (INTROSPECT_AGENT_CONTEXT or PROCEED).""");

        templates.put(AgentModels.ContextManagerRequest.class, """
            **Constraint:** Exactly one `returnTo*` field should be non-null in any ContextManagerRequest.

            **Happy path:** Route to the agent that can most directly act on the reconstructed context.""");

        templates.put(AgentModels.DiscoveryAgentRequests.class, """
            This is an intermediate dispatch state.
            **Default forward progress:** set `collectorRequest` in `DiscoveryAgentDispatchRouting`.
            That request should consolidate all discovery agent outputs into a single discovery collector input
            so the collector can choose ADVANCE_PHASE vs ROUTE_BACK.""");

        templates.put(AgentModels.PlanningAgentRequests.class, """
            This is an intermediate dispatch state.
            **Default forward progress:** set `planningCollectorRequest` in `PlanningAgentDispatchRouting`.
            That request should consolidate all planning agent outputs into one planning collector input
            so the collector can choose ADVANCE_PHASE vs ROUTE_BACK.""");

        templates.put(AgentModels.TicketAgentRequests.class, """
            This is an intermediate dispatch state.
            **Default forward progress:** set `ticketCollectorRequest` in `TicketAgentDispatchRouting`.
            That request should consolidate all ticket agent outputs into one ticket collector input
            so the collector can choose ADVANCE_PHASE vs ROUTE_BACK.""");

        return Collections.unmodifiableMap(templates);
    }

    private static final String INTERRUPT_GUIDANCE = """
        **Interrupt guidance:** If uncertain, emit an `interruptRequest`. You may emit interrupts multiple times if more context is needed.
        Include `reason`, `contextForDecision`, `choices`, `confirmationItems`, and the agent-specific interrupt context fields.
        """;

    private static final String CONTEXT_MANAGER_GUIDANCE = """
        **Context guidance:** Use `contextManagerRequest` only when you need specific missing context from another agent chat/history that is required to continue.
        For normal routing decisions, route via `orchestratorRequest` instead of context manager.
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
        if (BlackboardHistory.isNonWorkflowRequest(context.currentRequest())) {
            return "";
        }
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
            // Skip non-workflow entries to reduce prompt clutter
            if (entry instanceof BlackboardHistory.DefaultEntry defaultEntry
                    && defaultEntry.inputType() != null
                    && NON_WORKFLOW_TYPES.contains(defaultEntry.inputType())) {
                continue;
            }

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
        Map<Class<?>, Class<? extends AgentModels.AgentRouting>> requestToRouting = NodeMappings.REQUEST_TO_ROUTING;
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
        Map<Class<?>, Class<? extends AgentModels.AgentRouting>> requestToRouting = NodeMappings.REQUEST_TO_ROUTING;

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
