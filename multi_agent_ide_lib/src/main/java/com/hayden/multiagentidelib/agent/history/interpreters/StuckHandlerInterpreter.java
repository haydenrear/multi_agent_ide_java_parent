package com.hayden.multiagentidelib.agent.history.interpreters;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.ContextAlgebra;
import com.hayden.multiagentidelib.agent.history.HistoryInterpreter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects stuck handler invocations by looking for {@link AgentModels.ContextManagerRequest}
 * entries with reason "stuck-handler". Walks backward to find the last workflow request
 * that was active when the stuck handler fired.
 */
@Slf4j
@Component
public class StuckHandlerInterpreter implements HistoryInterpreter {

    private static final String STUCK_HANDLER_REASON = "stuck-handler";

    @Override
    public String name() {
        return "stuck-handler";
    }

    @Override
    public int priority() {
        return 55;
    }

    @Override
    public List<ContextAlgebra> interpret(List<BlackboardHistory.Entry> entries, int newEntryIndex) {
        if (newEntryIndex < 0 || newEntryIndex >= entries.size()) {
            return List.of();
        }
        BlackboardHistory.Entry newEntry = entries.get(newEntryIndex);
        if (newEntry == null || newEntry.input() == null) {
            return List.of();
        }
        if (newEntry instanceof ContextAlgebra) {
            return List.of();
        }

        if (!(newEntry.input() instanceof AgentModels.ContextManagerRequest cmRequest)) {
            return List.of();
        }

        if (!STUCK_HANDLER_REASON.equals(cmRequest.reason())) {
            return List.of();
        }

        // Walk backward to find the last non-CM, non-interrupt workflow request
        AgentModels.AgentRequest lastWorkflowRequest = null;
        String loopSummary = null;
        int startIndex = newEntryIndex;

        for (int i = newEntryIndex - 1; i >= 0; i--) {
            BlackboardHistory.Entry candidate = entries.get(i);
            if (candidate instanceof ContextAlgebra) continue;
            if (candidate == null || candidate.input() == null) continue;

            if (BlackboardHistory.isNonUtilityWorkflowRequest(candidate.input())
                    && candidate.input() instanceof AgentModels.AgentRequest ar) {
                lastWorkflowRequest = ar;
                startIndex = i;
                break;
            }
        }

        return List.of(new ContextAlgebra.StuckHandlerInvoked(
                newEntry.timestamp(),
                newEntry.actionName(),
                newEntry.input(),
                startIndex, newEntryIndex,
                cmRequest,
                lastWorkflowRequest,
                loopSummary));
    }
}
