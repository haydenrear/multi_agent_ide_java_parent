package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentModels.InterruptRequest;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Centralized registry of agent node type metadata: display names, routing field names, and
 * request classes. All prompt contributors and factories that need to display or route to
 * agent types should reference this interface rather than maintaining their own mappings.
 *
 * <p>The {@code fieldName} corresponds to the JSON property name on routing records
 * (e.g., {@link AgentModels.ContextManagerResultRouting}). It is {@code null} for types
 * that do not appear as routable fields (e.g., abstract interfaces, interrupt base type).</p>
 */
public interface NodeMappings {

    record NodeMapping(
            Class<?> requestType,
            String displayName,
            @Nullable String fieldName
    ) {}

    List<NodeMapping> ALL_MAPPINGS = initAllMappings();

    /** Display name lookup by request type. */
    Map<Class<?>, String> DISPLAY_NAMES = initDisplayNames();

    /** Field name lookup by request type (only types with a routable field). */
    Map<Class<?>, String> FIELD_NAMES = initFieldNames();

    static String displayName(Class<?> type) {
        return DISPLAY_NAMES.getOrDefault(type, type.getSimpleName());
    }

    static @Nullable String fieldName(Class<?> type) {
        return FIELD_NAMES.get(type);
    }

    static @Nullable NodeMapping findByType(Class<?> type) {
        for (NodeMapping m : ALL_MAPPINGS) {
            if (m.requestType().equals(type)) {
                return m;
            }
        }
        return null;
    }

    // -----------------------------------------------------------
    // Initialization
    // -----------------------------------------------------------

    private static List<NodeMapping> initAllMappings() {
        List<NodeMapping> m = new ArrayList<>();

        // Orchestrator level
        m.add(new NodeMapping(AgentModels.OrchestratorRequest.class, "Orchestrator", "orchestratorRequest"));
        m.add(new NodeMapping(AgentModels.OrchestratorCollectorRequest.class, "Orchestrator Collector", "orchestratorCollectorRequest"));

        // Discovery phase
        m.add(new NodeMapping(AgentModels.DiscoveryOrchestratorRequest.class, "Discovery Orchestrator", "discoveryOrchestratorRequest"));
        m.add(new NodeMapping(AgentModels.DiscoveryAgentRequests.class, "Discovery Agent Dispatch", "discoveryAgentRequests"));
        m.add(new NodeMapping(AgentModels.DiscoveryAgentRequest.class, "Discovery Agents", "discoveryAgentRequest"));
        m.add(new NodeMapping(AgentModels.DiscoveryCollectorRequest.class, "Discovery Collector", "discoveryCollectorRequest"));
        m.add(new NodeMapping(AgentModels.DiscoveryAgentResults.class, "Discovery Agent Results Dispatch", "discoveryAgentResults"));

        // Planning phase
        m.add(new NodeMapping(AgentModels.PlanningOrchestratorRequest.class, "Planning Orchestrator", "planningOrchestratorRequest"));
        m.add(new NodeMapping(AgentModels.PlanningAgentRequests.class, "Planning Agent Dispatch", "planningAgentRequests"));
        m.add(new NodeMapping(AgentModels.PlanningAgentRequest.class, "Planning Agents", "planningAgentRequest"));
        m.add(new NodeMapping(AgentModels.PlanningCollectorRequest.class, "Planning Collector", "planningCollectorRequest"));
        m.add(new NodeMapping(AgentModels.PlanningAgentResults.class, "Planning Agent Results Dispatch", "planningAgentResults"));

        // Ticket phase
        m.add(new NodeMapping(AgentModels.TicketOrchestratorRequest.class, "Ticket Orchestrator", "ticketOrchestratorRequest"));
        m.add(new NodeMapping(AgentModels.TicketAgentRequests.class, "Ticket Agent Dispatch", "ticketAgentRequests"));
        m.add(new NodeMapping(AgentModels.TicketAgentRequest.class, "Ticket Agents", "ticketAgentRequest"));
        m.add(new NodeMapping(AgentModels.TicketCollectorRequest.class, "Ticket Collector", "ticketCollectorRequest"));
        m.add(new NodeMapping(AgentModels.TicketAgentResults.class, "Ticket Agent Results Dispatch", "ticketAgentResults"));

        // Review, Merger, Context Manager
        m.add(new NodeMapping(AgentModels.ReviewRequest.class, "Review Agent", "reviewRequest"));
        m.add(new NodeMapping(AgentModels.MergerRequest.class, "Merger Agent", "mergerRequest"));
        m.add(new NodeMapping(AgentModels.ContextManagerRequest.class, "Context Manager", null));
        m.add(new NodeMapping(AgentModels.ContextManagerRoutingRequest.class, "Context Manager Routing Request", "contextOrchestratorRequest"));
        m.add(new NodeMapping(AgentModels.ResultsRequest.class, "Results Request", null));

        // Interrupt types
        m.add(new NodeMapping(InterruptRequest.class, "Interrupt Request", null));
        m.add(new NodeMapping(InterruptRequest.OrchestratorInterruptRequest.class, "Orchestrator Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.OrchestratorCollectorInterruptRequest.class, "Orchestrator Collector Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.DiscoveryOrchestratorInterruptRequest.class, "Discovery Orchestrator Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.DiscoveryAgentInterruptRequest.class, "Discovery Agent Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.DiscoveryCollectorInterruptRequest.class, "Discovery Collector Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.DiscoveryAgentDispatchInterruptRequest.class, "Discovery Agent Dispatch Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.PlanningOrchestratorInterruptRequest.class, "Planning Orchestrator Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.PlanningAgentInterruptRequest.class, "Planning Agent Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.PlanningCollectorInterruptRequest.class, "Planning Collector Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.PlanningAgentDispatchInterruptRequest.class, "Planning Agent Dispatch Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.TicketOrchestratorInterruptRequest.class, "Ticket Orchestrator Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.TicketAgentInterruptRequest.class, "Ticket Agent Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.TicketCollectorInterruptRequest.class, "Ticket Collector Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.TicketAgentDispatchInterruptRequest.class, "Ticket Agent Dispatch Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.ReviewInterruptRequest.class, "Review Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.MergerInterruptRequest.class, "Merger Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.ContextManagerInterruptRequest.class, "Context Manager Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.QuestionAnswerInterruptRequest.class, "Question Answer Interrupt", null));

        return Collections.unmodifiableList(m);
    }

    private static Map<Class<?>, String> initDisplayNames() {
        Map<Class<?>, String> map = new HashMap<>();
        for (NodeMapping m : ALL_MAPPINGS) {
            map.put(m.requestType(), m.displayName());
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<Class<?>, String> initFieldNames() {
        Map<Class<?>, String> map = new HashMap<>();
        for (NodeMapping m : ALL_MAPPINGS) {
            if (m.fieldName() != null) {
                map.put(m.requestType(), m.fieldName());
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
