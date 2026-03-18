package com.hayden.multiagentidelib.agent.history.interpreters;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.ContextAlgebra;
import com.hayden.multiagentidelib.agent.history.HistoryInterpreter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detects complete sub-graphs for workflow stages. When a collector result closes
 * a stage, walks backward to the stage's orchestrator request and collects all
 * entries in the span — including agents, dispatches, interrupts, reviews, merges,
 * commits, context manager loops, and errors.
 *
 * <p>Uses object references rather than indices.</p>
 * <p>Runs at low priority (high number) so it can see other algebra entries.</p>
 */
@Slf4j
@Component
public class AgentSubGraphInterpreter implements HistoryInterpreter {

    private static final Map<Class<?>, ContextAlgebra.SubGraphType> COLLECTOR_RESULT_TO_STAGE = Map.of(
            AgentModels.DiscoveryCollectorResult.class, ContextAlgebra.SubGraphType.DISCOVERY,
            AgentModels.PlanningCollectorResult.class, ContextAlgebra.SubGraphType.PLANNING,
            AgentModels.TicketCollectorResult.class, ContextAlgebra.SubGraphType.TICKET,
            AgentModels.OrchestratorCollectorResult.class, ContextAlgebra.SubGraphType.ORCHESTRATOR
    );

    private static final Map<ContextAlgebra.SubGraphType, Class<?>> STAGE_ORCHESTRATOR_REQUEST = Map.of(
            ContextAlgebra.SubGraphType.DISCOVERY, AgentModels.DiscoveryOrchestratorRequest.class,
            ContextAlgebra.SubGraphType.PLANNING, AgentModels.PlanningOrchestratorRequest.class,
            ContextAlgebra.SubGraphType.TICKET, AgentModels.TicketOrchestratorRequest.class,
            ContextAlgebra.SubGraphType.ORCHESTRATOR, AgentModels.OrchestratorRequest.class
    );

    @Override
    public String name() {
        return "agent-sub-graph";
    }

    @Override
    public int priority() {
        return 200;
    }

    @Override
    public List<ContextAlgebra> interpret(List<BlackboardHistory.Entry> entries, int newEntryIndex) {
        if (newEntryIndex < 1 || newEntryIndex >= entries.size()) {
            return List.of();
        }
        BlackboardHistory.Entry newEntry = entries.get(newEntryIndex);
        if (newEntry == null || newEntry.input() == null) {
            return List.of();
        }

        ContextAlgebra.SubGraphType stageType = COLLECTOR_RESULT_TO_STAGE.get(newEntry.inputType());
        if (stageType == null) {
            return List.of();
        }

        Class<?> orchestratorRequestType = STAGE_ORCHESTRATOR_REQUEST.get(stageType);
        if (orchestratorRequestType == null) {
            return List.of();
        }

        int startIndex = -1;
        List<BlackboardHistory.Entry> subGraphEntries = new ArrayList<>();

        // Walk backward, collecting ALL entries (including algebra, interrupts, commits, merges, etc.)
        for (int i = newEntryIndex; i >= 0; i--) {
            BlackboardHistory.Entry candidate = entries.get(i);
            subGraphEntries.addFirst(candidate);

            if (!(candidate instanceof ContextAlgebra)
                    && candidate != null && candidate.inputType() != null
                    && orchestratorRequestType.isAssignableFrom(candidate.inputType())) {
                startIndex = i;
                break;
            }
        }

        if (startIndex < 0) {
            return List.of();
        }

        return List.of(new ContextAlgebra.AgentSubGraph(
                newEntry.timestamp(),
                newEntry.actionName(),
                newEntry.input(),
                startIndex, newEntryIndex,
                stageType,
                subGraphEntries));
    }
}
