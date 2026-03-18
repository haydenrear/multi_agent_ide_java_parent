package com.hayden.multiagentidelib.agent.history.interpreters;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.ContextAlgebra;
import com.hayden.multiagentidelib.agent.history.HistoryInterpreter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects retry sequences: an agent request followed by error(s) and then
 * the same agent request type again. The pattern is:
 * AgentRequest(type A) → AgentErrorEvent(s) → AgentRequest(type A)
 */
@Slf4j
@Component
public class RetrySequenceInterpreter implements HistoryInterpreter {

    @Override
    public String name() {
        return "retry-sequence";
    }

    @Override
    public int priority() {
        return 120;
    }

    @Override
    public List<ContextAlgebra> interpret(List<BlackboardHistory.Entry> entries, int newEntryIndex) {
        if (newEntryIndex < 2 || newEntryIndex >= entries.size()) {
            return List.of();
        }
        BlackboardHistory.Entry newEntry = entries.get(newEntryIndex);
        if (newEntry == null || newEntry.input() == null) {
            return List.of();
        }
        if (newEntry instanceof ContextAlgebra) {
            return List.of();
        }
        if (!(newEntry.input() instanceof AgentModels.AgentRequest)) {
            return List.of();
        }

        Class<?> currentRequestType = newEntry.inputType();

        // Walk backward: collect error entries, then look for the same request type
        List<BlackboardHistory.Entry> errorEntries = new ArrayList<>();
        int retryCount = 1;
        int startIndex = newEntryIndex;

        for (int i = newEntryIndex - 1; i >= 0; i--) {
            BlackboardHistory.Entry candidate = entries.get(i);
            if (candidate instanceof ContextAlgebra) continue;
            if (candidate == null) continue;

            // Is this an error event entry?
            if (candidate.input() instanceof Events.AgentErrorEvent
                    || candidate.input() instanceof Events.NodeErrorEvent) {
                errorEntries.add(candidate);
                continue;
            }

            // Is this the same request type? (another retry attempt)
            if (candidate.inputType() != null
                    && candidate.inputType().equals(currentRequestType)
                    && candidate.input() instanceof AgentModels.AgentRequest) {
                retryCount++;
                startIndex = i;
                // Keep going — there may be more error→request pairs
                continue;
            }

            // Hit something else — stop
            break;
        }

        if (errorEntries.isEmpty() || retryCount <= 1) {
            return List.of();
        }

        return List.of(new ContextAlgebra.RetrySequence(
                newEntry.timestamp(),
                newEntry.actionName(),
                newEntry.input(),
                startIndex, newEntryIndex,
                currentRequestType,
                retryCount,
                errorEntries));
    }
}
