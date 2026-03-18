package com.hayden.multiagentidelib.agent.history.interpreters;

import com.hayden.acp_cdc_ai.acp.events.HasContextId;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.ContextAlgebra;
import com.hayden.multiagentidelib.agent.history.HistoryInterpreter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects standard workflow transitions in the entry stream.
 *
 * <p>Orchestrator-level transitions: Orchestrator→DiscoveryOrch, DiscoveryCollector→PlanningOrch, etc.
 * Orchestrator-to-dispatch: DiscoveryOrch→DiscoveryAgentDispatch, etc.
 * Dispatch/Agent transitions (per sub-graph):
 *   - Dispatch→Agent (first agent request after dispatch)
 *   - Agent→Agent (consecutive agent request after agent result)
 *   - Agent→Dispatch (last agent result back to dispatch)
 *   - Dispatch→Collector (dispatch routes to collector)
 * </p>
 */
@Slf4j
@Component
public class AgentToAgentInterpreter implements HistoryInterpreter {

    @FunctionalInterface
    private interface TransitionFactory {
        ContextAlgebra create(Instant timestamp, String actionName,
                              HasContextId input,
                              int startIndex, int endIndex,
                              HasContextId fromEntry, HasContextId toEntry);
    }

    private record TransitionEdge(Class<?> fromType, TransitionFactory factory) {}

    /**
     * Map from "to" request type → (expected "from" type, factory).
     * Orchestrator-level and orchestrator-to-dispatch transitions.
     */
    private static final Map<Class<?>, TransitionEdge> ORCHESTRATOR_TRANSITIONS = Map.ofEntries(
            // Orchestrator-level
            Map.entry(AgentModels.DiscoveryOrchestratorRequest.class,
                    new TransitionEdge(AgentModels.OrchestratorRequest.class,
                            ContextAlgebra.OrchestratorToDiscoveryOrchestrator::new)),
            Map.entry(AgentModels.PlanningOrchestratorRequest.class,
                    new TransitionEdge(AgentModels.DiscoveryCollectorRequest.class,
                            ContextAlgebra.DiscoveryCollectorToPlanningOrchestrator::new)),
            Map.entry(AgentModels.TicketOrchestratorRequest.class,
                    new TransitionEdge(AgentModels.PlanningCollectorRequest.class,
                            ContextAlgebra.PlanningCollectorToTicketOrchestrator::new)),
            Map.entry(AgentModels.OrchestratorCollectorRequest.class,
                    new TransitionEdge(AgentModels.TicketCollectorRequest.class,
                            ContextAlgebra.TicketCollectorToOrchestratorCollector::new)),
            // Orchestrator-to-dispatch
            Map.entry(AgentModels.DiscoveryAgentRequests.class,
                    new TransitionEdge(AgentModels.DiscoveryOrchestratorRequest.class,
                            ContextAlgebra.DiscoveryOrchestratorToDiscoveryAgentDispatch::new)),
            Map.entry(AgentModels.PlanningAgentRequests.class,
                    new TransitionEdge(AgentModels.PlanningOrchestratorRequest.class,
                            ContextAlgebra.PlanningOrchestratorToPlanningAgentDispatch::new)),
            Map.entry(AgentModels.TicketAgentRequests.class,
                    new TransitionEdge(AgentModels.TicketOrchestratorRequest.class,
                            ContextAlgebra.TicketOrchestratorToTicketAgentDispatch::new))
    );

    // Dispatch → first Agent
    private static final Map<Class<?>, DispatchAgentConfig> DISPATCH_AGENT_CONFIGS = Map.of(
            AgentModels.DiscoveryAgentRequest.class, new DispatchAgentConfig(
                    AgentModels.DiscoveryAgentRequests.class, AgentModels.DiscoveryAgentResult.class,
                    AgentModels.DiscoveryAgentResults.class, AgentModels.DiscoveryCollectorRequest.class),
            AgentModels.PlanningAgentRequest.class, new DispatchAgentConfig(
                    AgentModels.PlanningAgentRequests.class, AgentModels.PlanningAgentResult.class,
                    AgentModels.PlanningAgentResults.class, AgentModels.PlanningCollectorRequest.class),
            AgentModels.TicketAgentRequest.class, new DispatchAgentConfig(
                    AgentModels.TicketAgentRequests.class, AgentModels.TicketAgentResult.class,
                    AgentModels.TicketAgentResults.class, AgentModels.TicketCollectorRequest.class)
    );

    // Agent result types mapped to their configs
    private static final Map<Class<?>, DispatchAgentConfig> AGENT_RESULT_CONFIGS = Map.of(
            AgentModels.DiscoveryAgentResult.class, DISPATCH_AGENT_CONFIGS.get(AgentModels.DiscoveryAgentRequest.class),
            AgentModels.PlanningAgentResult.class, DISPATCH_AGENT_CONFIGS.get(AgentModels.PlanningAgentRequest.class),
            AgentModels.TicketAgentResult.class, DISPATCH_AGENT_CONFIGS.get(AgentModels.TicketAgentRequest.class)
    );

    // Dispatch results type → collector request type
    private static final Map<Class<?>, Class<?>> DISPATCH_RESULT_TO_COLLECTOR = Map.of(
            AgentModels.DiscoveryAgentResults.class, AgentModels.DiscoveryCollectorRequest.class,
            AgentModels.PlanningAgentResults.class, AgentModels.PlanningCollectorRequest.class,
            AgentModels.TicketAgentResults.class, AgentModels.TicketCollectorRequest.class
    );

    private record DispatchAgentConfig(
            Class<?> dispatchRequestType,
            Class<?> agentResultType,
            Class<?> dispatchResultType,
            Class<?> collectorRequestType
    ) {}

    @Override
    public String name() {
        return "agent-to-agent";
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public List<ContextAlgebra> interpret(List<BlackboardHistory.Entry> entries, int newEntryIndex) {
        if (newEntryIndex < 0 || newEntryIndex >= entries.size()) {
            return List.of();
        }
        BlackboardHistory.Entry newEntry = entries.get(newEntryIndex);
        if (newEntry == null || newEntry.inputType() == null) {
            return List.of();
        }

        List<ContextAlgebra> results = new ArrayList<>();

        // Check orchestrator-level transitions
        TransitionEdge edge = ORCHESTRATOR_TRANSITIONS.get(newEntry.inputType());
        if (edge != null) {
            for (int i = newEntryIndex - 1; i >= 0; i--) {
                BlackboardHistory.Entry candidate = entries.get(i);
                if (candidate instanceof ContextAlgebra) continue;
                if (candidate != null && candidate.inputType() != null
                        && edge.fromType.isAssignableFrom(candidate.inputType())) {
                    results.add(edge.factory.create(
                            newEntry.timestamp(), newEntry.actionName(), newEntry.input(),
                            i, newEntryIndex, candidate.input(), newEntry.input()));
                    break;
                }
            }
            return results;
        }

        // Check dispatch→agent (first agent request after dispatch)
        DispatchAgentConfig agentConfig = DISPATCH_AGENT_CONFIGS.get(newEntry.inputType());
        if (agentConfig != null) {
            return detectDispatchToAgent(entries, newEntryIndex, newEntry, agentConfig);
        }

        // Check agent result → could be Agent→Agent or Agent→Dispatch
        DispatchAgentConfig resultConfig = AGENT_RESULT_CONFIGS.get(newEntry.inputType());
        if (resultConfig != null) {
            // This is an agent result - nothing to emit yet. The next entry will trigger.
            return List.of();
        }

        // Check dispatch result → collector (Dispatch→Collector)
        if (DISPATCH_RESULT_TO_COLLECTOR.containsKey(newEntry.inputType())) {
            return detectDispatchToCollector(entries, newEntryIndex, newEntry);
        }

        // Check collector request — detect DispatchToCollector
        for (var config : DISPATCH_AGENT_CONFIGS.values()) {
            if (config.collectorRequestType.equals(newEntry.inputType())) {
                return detectCollectorRequestAfterDispatch(entries, newEntryIndex, newEntry, config);
            }
        }

        return results;
    }

    private List<ContextAlgebra> detectDispatchToAgent(
            List<BlackboardHistory.Entry> entries, int newIdx,
            BlackboardHistory.Entry newEntry, DispatchAgentConfig config) {

        // Look backward: is there a previous agent result of the same type? → Agent→Agent
        // Or is it the dispatch request? → Dispatch→Agent
        for (int i = newIdx - 1; i >= 0; i--) {
            BlackboardHistory.Entry candidate = entries.get(i);
            if (candidate instanceof ContextAlgebra) continue;
            if (candidate == null || candidate.inputType() == null) continue;

            if (config.agentResultType.isAssignableFrom(candidate.inputType())) {
                // Agent result → new agent request = Agent→Agent
                return List.of(createAgentToAgent(newEntry, candidate, i, newIdx));
            }
            if (config.dispatchRequestType.isAssignableFrom(candidate.inputType())) {
                // Dispatch request → first agent = Dispatch→Agent
                return List.of(createDispatchToAgent(newEntry, candidate, i, newIdx));
            }
            // Skip utility entries (commit, merge, interrupt, CM)
            if (isUtilityEntry(candidate)) continue;
            break;
        }
        return List.of();
    }

    private List<ContextAlgebra> detectDispatchToCollector(
            List<BlackboardHistory.Entry> entries, int newIdx,
            BlackboardHistory.Entry newEntry) {
        // Dispatch result → next should be collector request
        // For now, just record Agent→Dispatch when we see the dispatch result
        for (int i = newIdx - 1; i >= 0; i--) {
            BlackboardHistory.Entry candidate = entries.get(i);
            if (candidate instanceof ContextAlgebra) continue;
            if (candidate == null || candidate.inputType() == null) continue;

            // Find the last agent result
            for (var config : DISPATCH_AGENT_CONFIGS.values()) {
                if (config.agentResultType.isAssignableFrom(candidate.inputType())) {
                    return List.of(createAgentToDispatch(newEntry, candidate, i, newIdx));
                }
            }
            if (isUtilityEntry(candidate)) continue;
            break;
        }
        return List.of();
    }

    private List<ContextAlgebra> detectCollectorRequestAfterDispatch(
            List<BlackboardHistory.Entry> entries, int newIdx,
            BlackboardHistory.Entry newEntry, DispatchAgentConfig config) {
        // Look backward for the dispatch result
        for (int i = newIdx - 1; i >= 0; i--) {
            BlackboardHistory.Entry candidate = entries.get(i);
            if (candidate instanceof ContextAlgebra) continue;
            if (candidate == null || candidate.inputType() == null) continue;

            if (config.dispatchResultType.isAssignableFrom(candidate.inputType())) {
                return List.of(createDispatchToCollector(newEntry, candidate, i, newIdx));
            }
            if (isUtilityEntry(candidate)) continue;
            break;
        }
        return List.of();
    }

    private ContextAlgebra createDispatchToAgent(BlackboardHistory.Entry agentEntry, BlackboardHistory.Entry dispatchEntry, int fromIdx, int toIdx) {
        Class<?> agentType = agentEntry.inputType();
        if (AgentModels.DiscoveryAgentRequest.class.isAssignableFrom(agentType)) {
            return new ContextAlgebra.DiscoveryAgentDispatchToDiscoveryAgent(
                    agentEntry.timestamp(), agentEntry.actionName(), agentEntry.input(),
                    fromIdx, toIdx, dispatchEntry.input(), agentEntry.input());
        } else if (AgentModels.PlanningAgentRequest.class.isAssignableFrom(agentType)) {
            return new ContextAlgebra.PlanningAgentDispatchToPlanningAgent(
                    agentEntry.timestamp(), agentEntry.actionName(), agentEntry.input(),
                    fromIdx, toIdx, dispatchEntry.input(), agentEntry.input());
        } else {
            return new ContextAlgebra.TicketAgentDispatchToTicketAgent(
                    agentEntry.timestamp(), agentEntry.actionName(), agentEntry.input(),
                    fromIdx, toIdx, dispatchEntry.input(), agentEntry.input());
        }
    }

    private ContextAlgebra createAgentToAgent(BlackboardHistory.Entry newAgentEntry, BlackboardHistory.Entry prevResultEntry, int fromIdx, int toIdx) {
        Class<?> agentType = newAgentEntry.inputType();
        if (AgentModels.DiscoveryAgentRequest.class.isAssignableFrom(agentType)) {
            return new ContextAlgebra.DiscoveryAgentToDiscoveryAgent(
                    newAgentEntry.timestamp(), newAgentEntry.actionName(), newAgentEntry.input(),
                    fromIdx, toIdx, prevResultEntry.input(), newAgentEntry.input());
        } else if (AgentModels.PlanningAgentRequest.class.isAssignableFrom(agentType)) {
            return new ContextAlgebra.PlanningAgentToPlanningAgent(
                    newAgentEntry.timestamp(), newAgentEntry.actionName(), newAgentEntry.input(),
                    fromIdx, toIdx, prevResultEntry.input(), newAgentEntry.input());
        } else {
            return new ContextAlgebra.TicketAgentToTicketAgent(
                    newAgentEntry.timestamp(), newAgentEntry.actionName(), newAgentEntry.input(),
                    fromIdx, toIdx, prevResultEntry.input(), newAgentEntry.input());
        }
    }

    private ContextAlgebra createAgentToDispatch(BlackboardHistory.Entry dispatchResultEntry, BlackboardHistory.Entry lastAgentResultEntry, int fromIdx, int toIdx) {
        Class<?> dispatchType = dispatchResultEntry.inputType();
        if (AgentModels.DiscoveryAgentResults.class.isAssignableFrom(dispatchType)) {
            return new ContextAlgebra.DiscoveryAgentToDiscoveryAgentDispatch(
                    dispatchResultEntry.timestamp(), dispatchResultEntry.actionName(), dispatchResultEntry.input(),
                    fromIdx, toIdx, lastAgentResultEntry.input(), dispatchResultEntry.input());
        } else if (AgentModels.PlanningAgentResults.class.isAssignableFrom(dispatchType)) {
            return new ContextAlgebra.PlanningAgentToPlanningAgentDispatch(
                    dispatchResultEntry.timestamp(), dispatchResultEntry.actionName(), dispatchResultEntry.input(),
                    fromIdx, toIdx, lastAgentResultEntry.input(), dispatchResultEntry.input());
        } else {
            return new ContextAlgebra.TicketAgentToTicketAgentDispatch(
                    dispatchResultEntry.timestamp(), dispatchResultEntry.actionName(), dispatchResultEntry.input(),
                    fromIdx, toIdx, lastAgentResultEntry.input(), dispatchResultEntry.input());
        }
    }

    private ContextAlgebra createDispatchToCollector(BlackboardHistory.Entry collectorEntry, BlackboardHistory.Entry dispatchResultEntry, int fromIdx, int toIdx) {
        Class<?> collectorType = collectorEntry.inputType();
        if (AgentModels.DiscoveryCollectorRequest.class.isAssignableFrom(collectorType)) {
            return new ContextAlgebra.DiscoveryAgentDispatchToDiscoveryCollector(
                    collectorEntry.timestamp(), collectorEntry.actionName(), collectorEntry.input(),
                    fromIdx, toIdx, dispatchResultEntry.input(), collectorEntry.input());
        } else if (AgentModels.PlanningCollectorRequest.class.isAssignableFrom(collectorType)) {
            return new ContextAlgebra.PlanningAgentDispatchToPlanningCollector(
                    collectorEntry.timestamp(), collectorEntry.actionName(), collectorEntry.input(),
                    fromIdx, toIdx, dispatchResultEntry.input(), collectorEntry.input());
        } else {
            return new ContextAlgebra.TicketAgentDispatchToTicketCollector(
                    collectorEntry.timestamp(), collectorEntry.actionName(), collectorEntry.input(),
                    fromIdx, toIdx, dispatchResultEntry.input(), collectorEntry.input());
        }
    }

    private static boolean isUtilityEntry(BlackboardHistory.Entry entry) {
        if (entry == null || entry.input() == null) return false;
        return entry.input() instanceof AgentModels.CommitAgentRequest
                || entry.input() instanceof AgentModels.CommitAgentResult
                || entry.input() instanceof AgentModels.MergeConflictRequest
                || entry.input() instanceof AgentModels.MergeConflictResult
                || entry.input() instanceof AgentModels.InterruptRequest
                || entry.input() instanceof AgentModels.ContextManagerRequest
                || entry.input() instanceof AgentModels.ContextManagerRoutingRequest;
    }
}
