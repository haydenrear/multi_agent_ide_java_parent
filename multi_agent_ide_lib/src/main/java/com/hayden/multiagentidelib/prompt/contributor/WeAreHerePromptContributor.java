package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.WorkflowAgentGraphNode;

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

    private static Map<Class<?>, String> initGuidanceTemplates() {
        Map<Class<?>, String> templates = new HashMap<>();
        
        templates.put(AgentModels.OrchestratorRequest.class, """
            **Guidance:** You must return an `OrchestratorRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `collectorRequest` - Route to Orchestrator Collector (final consolidation)
            - `orchestratorRequest` - Route to Discovery Orchestrator
            - `contextManagerRequest` - Route to Context Manager for context reconstruction

            **Happy path:** For a new workflow, set `orchestratorRequest` to start discovery.
            Only set `collectorRequest` when ALL workflow phases are complete.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.DiscoveryOrchestratorRequest.class, """
            **Guidance:** You must return a `DiscoveryOrchestratorRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `agentRequests` - Delegate to multiple discovery agents (contains list of DiscoveryAgentRequest)
            - `collectorRequest` - Route to Discovery Collector to consolidate results
            - `contextManagerRequest` - Route to Context Manager for context reconstruction

            **Happy path:** Set `agentRequests` to dispatch discovery work, then later set
            `collectorRequest` when agents have gathered sufficient information.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.DiscoveryCollectorRequest.class, """
            **Guidance:** You must return a `DiscoveryCollectorRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `collectorResult` - Collector result with CollectorDecision (triggers handleDiscoveryCollectorBranch)
            - `orchestratorRequest` - Route to main Orchestrator
            - `discoveryRequest` - Route back to Discovery Orchestrator
            - `planningRequest` - Route to Planning Orchestrator
            - `ticketRequest` - Route to Ticket Orchestrator
            - `reviewRequest` - Route to Review Agent
            - `mergerRequest` - Route to Merger Agent
            - `contextManagerRequest` - Route to Context Manager for context reconstruction

            **Branching behavior when `collectorResult` is set:**
            - Framework calls `handleDiscoveryCollectorBranch` which interprets the decision
            - ADVANCE_PHASE → handler sets `planningRequest`
            - ROUTE_BACK → handler sets `discoveryRequest`

            **Most common:** Use `collectorResult` for standard flow control.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.PlanningOrchestratorRequest.class, """
            **Guidance:** You must return a `PlanningOrchestratorRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `agentRequests` - Delegate to multiple planning agents (contains list of PlanningAgentRequest)
            - `collectorRequest` - Route to Planning Collector to consolidate results
            - `contextManagerRequest` - Route to Context Manager for context reconstruction

            **Happy path:** Set `agentRequests` to dispatch planning work, then later set
            `collectorRequest` when planning is complete.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.PlanningCollectorRequest.class, """
            **Guidance:** You must return a `PlanningCollectorRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `collectorResult` - Collector result with CollectorDecision (triggers handlePlanningCollectorBranch)
            - `planningRequest` - Route back to Planning Orchestrator
            - `ticketOrchestratorRequest` - Route to Ticket Orchestrator
            - `discoveryOrchestratorRequest` - Route to Discovery Orchestrator
            - `orchestratorCollectorRequest` - Route to Orchestrator Collector
            - `reviewRequest` - Route to Review Agent
            - `mergerRequest` - Route to Merger Agent
            - `contextManagerRequest` - Route to Context Manager for context reconstruction

            **Branching behavior when `collectorResult` is set:**
            - Framework calls `handlePlanningCollectorBranch` which interprets the decision
            - ADVANCE_PHASE → handler sets `ticketOrchestratorRequest`
            - ROUTE_BACK → handler sets `planningRequest`

            **Most common:** Use `collectorResult` for standard flow control.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.TicketOrchestratorRequest.class, """
            **Guidance:** You must return a `TicketOrchestratorRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `agentRequests` - Delegate to multiple ticket agents (contains list of TicketAgentRequest)
            - `collectorRequest` - Route to Ticket Collector to consolidate results
            - `contextManagerRequest` - Route to Context Manager for context reconstruction

            **Happy path:** Set `agentRequests` to dispatch ticket execution work, then later set
            `collectorRequest` when implementation is complete.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.TicketCollectorRequest.class, """
            **Guidance:** You must return a `TicketCollectorRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `collectorResult` - Collector result with CollectorDecision (triggers handleTicketCollectorBranch)
            - `ticketRequest` - Route back to Ticket Orchestrator
            - `orchestratorCollectorRequest` - Route to Orchestrator Collector
            - `reviewRequest` - Route to Review Agent
            - `mergerRequest` - Route to Merger Agent
            - `contextManagerRequest` - Route to Context Manager for context reconstruction

            **Branching behavior when `collectorResult` is set:**
            - Framework calls `handleTicketCollectorBranch` which interprets the decision
            - ADVANCE_PHASE → handler sets `orchestratorCollectorRequest`
            - ROUTE_BACK → handler sets `ticketRequest`

            **Most common:** Use `collectorResult` for standard flow control.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.OrchestratorCollectorRequest.class, """
            **Guidance:** You must return an `OrchestratorCollectorRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `collectorResult` - Collector result with CollectorDecision (triggers handleOrchestratorCollectorBranch)
            - `orchestratorRequest` - Route back to Orchestrator
            - `discoveryRequest` - Route to Discovery Orchestrator
            - `planningRequest` - Route to Planning Orchestrator
            - `ticketRequest` - Route to Ticket Orchestrator
            - `reviewRequest` - Route to Review Agent
            - `mergerRequest` - Route to Merger Agent
            - `contextManagerRequest` - Route to Context Manager for context reconstruction

            **Branching behavior when `collectorResult` is set:**
            - Framework calls `handleOrchestratorCollectorBranch` which interprets the decision
            - ADVANCE_PHASE → workflow complete (requestedPhase="COMPLETE")
            - ROUTE_BACK → restart at specific phase

            **Most common:** Use `collectorResult` with ADVANCE_PHASE for workflow completion.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.DiscoveryAgentRequest.class, """
            **Guidance:** You must return a `DiscoveryAgentRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `agentResult` - Return discovery findings (contains DiscoveryReport)
            - `planningOrchestratorRequest` - Bypass to Planning Orchestrator (rare)
            - `contextManagerRequest` - Route to Context Manager for context reconstruction

            **Happy path:** Set `agentResult` with your discovery findings.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.PlanningAgentRequest.class, """
            **Guidance:** You must return a `PlanningAgentRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `agentResult` - Return planning results (contains tickets)
            - `contextManagerRequest` - Route to Context Manager for context reconstruction

            **Happy path:** Set `agentResult` with your planning tickets.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.TicketAgentRequest.class, """
            **Guidance:** You must return a `TicketAgentRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `agentResult` - Return ticket execution results (files modified, tests, commits)
            - `contextManagerRequest` - Route to Context Manager for context reconstruction

            **Happy path:** Set `agentResult` with your implementation results.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.DiscoveryAgentResults.class, """
            **Guidance:** You must return a `DiscoveryAgentDispatchRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `collectorRequest` - Route to Discovery Collector
            - `contextManagerRequest` - Route to Context Manager for context reconstruction

            **Happy path:** Set `collectorRequest` to consolidate discovery results.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.PlanningAgentResults.class, """
            **Guidance:** You must return a `PlanningAgentDispatchRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `planningCollectorRequest` - Route to Planning Collector
            - `contextManagerRequest` - Route to Context Manager for context reconstruction

            **Happy path:** Set `planningCollectorRequest` to consolidate planning results.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.TicketAgentResults.class, """
            **Guidance:** You must return a `TicketAgentDispatchRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `ticketCollectorRequest` - Route to Ticket Collector
            - `contextManagerRequest` - Route to Context Manager for context reconstruction

            **Happy path:** Set `ticketCollectorRequest` to consolidate ticket results.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.ReviewRequest.class, """
            **Guidance:** You must return a `ReviewRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `reviewResult` - Return review findings (contains ReviewAgentResult)
            - `orchestratorCollectorRequest` - Route to Orchestrator Collector
            - `discoveryCollectorRequest` - Route to Discovery Collector
            - `planningCollectorRequest` - Route to Planning Collector
            - `ticketCollectorRequest` - Route to Ticket Collector
            - `contextManagerRequest` - Route to Context Manager for context reconstruction

            **Return routes:** The request includes `returnTo*` fields indicating which collector 
            invoked you. Route back to the appropriate collector after completing your review.

            **Happy path:** Set `reviewResult` with your review findings, then route back to 
            the originating collector using the corresponding `*CollectorRequest` field.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.MergerRequest.class, """
            **Guidance:** You must return a `MergerRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `mergerResult` - Return merge validation results (contains MergerAgentResult)
            - `orchestratorCollectorRequest` - Route to Orchestrator Collector
            - `discoveryCollectorRequest` - Route to Discovery Collector
            - `planningCollectorRequest` - Route to Planning Collector
            - `ticketCollectorRequest` - Route to Ticket Collector
            - `contextManagerRequest` - Route to Context Manager for context reconstruction

            **Return routes:** The request includes `returnTo*` fields indicating which collector 
            invoked you. Route back to the appropriate collector after completing your merge validation.

            **Happy path:** Set `mergerResult` with your merge findings, then route back to 
            the originating collector using the corresponding `*CollectorRequest` field.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.ContextManagerRoutingRequest.class, """
            **Guidance:** You must return a `ContextManagerRequest` assembled by the routing action.

            **Routing options:**
            - `reason` - Explain why context reconstruction is needed
            - `type` - Choose the reconstruction type (INTROSPECT_AGENT_CONTEXT or PROCEED)

            **Happy path:** Provide a concise reason and pick the most appropriate type.
            """);
        
        templates.put(AgentModels.ContextManagerRequest.class, """
            **Guidance:** You must return a `ContextManagerResultRouting` with exactly ONE field set.

            **Routing options:**
            - `interruptRequest` - Pause or stop execution per interrupt request
            - `orchestratorRequest` - Route to Orchestrator
            - `orchestratorCollectorRequest` - Route to Orchestrator Collector
            - `discoveryOrchestratorRequest` - Route to Discovery Orchestrator
            - `discoveryCollectorRequest` - Route to Discovery Collector
            - `planningOrchestratorRequest` - Route to Planning Orchestrator
            - `planningCollectorRequest` - Route to Planning Collector
            - `ticketOrchestratorRequest` - Route to Ticket Orchestrator
            - `ticketCollectorRequest` - Route to Ticket Collector
            - `reviewRequest` - Route to Review Agent
            - `mergerRequest` - Route to Merger Agent
            - `planningAgentRequest` - Route to Planning Agent
            - `planningAgentRequests` - Route to Planning Agent dispatch
            - `planningAgentResults` - Route to Planning Agent results
            - `ticketAgentRequest` - Route to Ticket Agent
            - `ticketAgentRequests` - Route to Ticket Agent dispatch
            - `ticketAgentResults` - Route to Ticket Agent results
            - `discoveryAgentRequest` - Route to Discovery Agent
            - `discoveryAgentRequests` - Route to Discovery Agent dispatch
            - `discoveryAgentResults` - Route to Discovery Agent results
            - `contextOrchestratorRequest` - Route to Context Manager (recursive)

            **Available return routes in this request (may be null):**
            - `returnToOrchestrator`
            - `returnToOrchestratorCollector`
            - `returnToDiscoveryOrchestrator`
            - `returnToDiscoveryCollector`
            - `returnToPlanningOrchestrator`
            - `returnToPlanningCollector`
            - `returnToTicketOrchestrator`
            - `returnToTicketCollector`
            - `returnToReview`
            - `returnToMerger`
            - `returnToPlanningAgent`
            - `returnToPlanningAgentRequests`
            - `returnToPlanningAgentResults`
            - `returnToTicketAgent`
            - `returnToTicketAgentRequests`
            - `returnToTicketAgentResults`
            - `returnToDiscoveryAgent`
            - `returnToDiscoveryAgentRequests`
            - `returnToDiscoveryAgentResults`
            - `returnToContextOrchestrator`

            **Constraint:** Exactly one `returnTo*` field should be non-null in any ContextManagerRequest.

            **Happy path:** Route to the agent that can most directly act on the reconstructed context.
            """ + INTERRUPT_GUIDANCE);
        
        // Dispatch routings (aggregate results from parallel agents)
        templates.put(AgentModels.DiscoveryAgentRequests.class, """
            **Guidance:** You are in discovery agent dispatch. Individual discovery agents have been 
            invoked and their results will be aggregated.

            This is an intermediate state - the framework will collect results and route to 
            `DiscoveryAgentDispatchRouting` which typically routes to `Discovery Collector`.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.PlanningAgentRequests.class, """
            **Guidance:** You are in planning agent dispatch. Individual planning agents have been 
            invoked and their results will be aggregated.

            This is an intermediate state - the framework will collect results and route to 
            `PlanningAgentDispatchRouting` which typically routes to `Planning Collector`.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
        templates.put(AgentModels.TicketAgentRequests.class, """
            **Guidance:** You are in ticket agent dispatch. Individual ticket agents have been 
            invoked and their results will be aggregated.

            This is an intermediate state - the framework will collect results and route to 
            `TicketAgentDispatchRouting` which typically routes to `Ticket Collector`.
            """ + INTERRUPT_GUIDANCE + CONTEXT_MANAGER_GUIDANCE);
        
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
        args.put("node_orchestrator", nodeDisplay("Orchestrator", 
                AgentModels.OrchestratorRequest.class, currentRequestType, visitedTypes));
        args.put("node_discovery_orchestrator", nodeDisplay("Discovery Orchestrator", 
                AgentModels.DiscoveryOrchestratorRequest.class, currentRequestType, visitedTypes));
        args.put("node_discovery_agent_dispatch", nodeDisplay("Discovery Agent Dispatch", 
                AgentModels.DiscoveryAgentRequests.class, currentRequestType, visitedTypes));
        args.put("node_discovery_agents", nodeDisplay("Discovery Agents", 
                AgentModels.DiscoveryAgentRequest.class, currentRequestType, visitedTypes));
        args.put("node_discovery_collector", nodeDisplay("Discovery Collector", 
                AgentModels.DiscoveryCollectorRequest.class, currentRequestType, visitedTypes));
        args.put("node_planning_orchestrator", nodeDisplay("Planning Orchestrator", 
                AgentModels.PlanningOrchestratorRequest.class, currentRequestType, visitedTypes));
        args.put("node_planning_agent_dispatch", nodeDisplay("Planning Agent Dispatch", 
                AgentModels.PlanningAgentRequests.class, currentRequestType, visitedTypes));
        args.put("node_planning_agents", nodeDisplay("Planning Agents", 
                AgentModels.PlanningAgentRequest.class, currentRequestType, visitedTypes));
        args.put("node_planning_collector", nodeDisplay("Planning Collector", 
                AgentModels.PlanningCollectorRequest.class, currentRequestType, visitedTypes));
        args.put("node_ticket_orchestrator", nodeDisplay("Ticket Orchestrator", 
                AgentModels.TicketOrchestratorRequest.class, currentRequestType, visitedTypes));
        args.put("node_ticket_agent_dispatch", nodeDisplay("Ticket Agent Dispatch", 
                AgentModels.TicketAgentRequests.class, currentRequestType, visitedTypes));
        args.put("node_ticket_agents", nodeDisplay("Ticket Agents", 
                AgentModels.TicketAgentRequest.class, currentRequestType, visitedTypes));
        args.put("node_ticket_collector", nodeDisplay("Ticket Collector", 
                AgentModels.TicketCollectorRequest.class, currentRequestType, visitedTypes));
        args.put("node_orchestrator_collector", nodeDisplay("Orchestrator Collector", 
                AgentModels.OrchestratorCollectorRequest.class, currentRequestType, visitedTypes));

        // Side nodes (can be reached from collectors)
        args.put("node_review", nodeDisplay("Review Agent", 
                AgentModels.ReviewRequest.class, currentRequestType, visitedTypes));
        args.put("node_merger", nodeDisplay("Merger Agent", 
                AgentModels.MergerRequest.class, currentRequestType, visitedTypes));
        args.put("node_context_manager", nodeDisplay("Context Manager", 
                AgentModels.ContextManagerRequest.class, currentRequestType, visitedTypes));

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
     * Build a description of available routing options for the current position.
     */
    private String buildRoutingOptions(PromptContext context) {
        StringBuilder sb = new StringBuilder();

        if (context.currentRequest() == null) {
            sb.append("_No request context available._\n");
            return sb.toString();
        }

        Class<?> requestType = context.currentRequest().getClass();
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
     * Provide contextual guidance based on the current request type.
     */
    private String getContextualGuidance(Class<?> requestType) {
        return GUIDANCE_TEMPLATES.getOrDefault(requestType, "");
    }
}
