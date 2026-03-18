package com.hayden.multiagentidelib.agent.history.interpreters;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.ContextAlgebra;
import com.hayden.multiagentidelib.agent.history.HistoryInterpreter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects {@link Events.AgentErrorEvent} entries in the blackboard history
 * and emits {@link ContextAlgebra.ErrorOccurred}.
 */
@Slf4j
@Component
public class ErrorInterpreter implements HistoryInterpreter {

    @Override
    public String name() {
        return "error";
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
        if (newEntry == null || newEntry.input() == null) {
            return List.of();
        }
        if (newEntry instanceof ContextAlgebra) {
            return List.of();
        }

        if (!(newEntry.input() instanceof Events.AgentErrorEvent errorEvent)) {
            return List.of();
        }

        return List.of(new ContextAlgebra.ErrorOccurred(
                newEntry.timestamp(),
                newEntry.actionName(),
                newEntry.input(),
                newEntryIndex, newEntryIndex,
                errorEvent.errorMessage(),
                errorEvent.failedOutputTypeName(),
                errorEvent.failedInputTypeName()));
    }
}
