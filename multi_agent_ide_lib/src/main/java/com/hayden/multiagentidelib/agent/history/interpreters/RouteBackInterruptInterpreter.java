package com.hayden.multiagentidelib.agent.history.interpreters;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.ContextAlgebra;
import com.hayden.multiagentidelib.agent.history.HistoryInterpreter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects interrupt entries: when a new entry is an {@link AgentModels.InterruptRequest},
 * looks backward to find the originating agent and emits {@link ContextAlgebra.AgentToInterrupt}.
 *
 * Also detects when an interrupt cycle completes (route-back to originating agent)
 * and emits {@link ContextAlgebra.InterruptCycleCompleted}.
 */
@Slf4j
@Component
public class RouteBackInterruptInterpreter implements HistoryInterpreter {

    @Override
    public String name() {
        return "route-back-interrupt";
    }

    @Override
    public int priority() {
        return 60;
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

        // Detect AgentToInterrupt
        if (newEntry.input() instanceof AgentModels.InterruptRequest interruptRequest) {
            return detectAgentToInterrupt(entries, newEntryIndex, interruptRequest);
        }

        // Detect InterruptCycleCompleted: a non-utility workflow request after an interrupt sequence
        if (BlackboardHistory.isNonUtilityWorkflowRequest(newEntry.input())) {
            return detectInterruptCycleCompleted(entries, newEntryIndex);
        }

        return List.of();
    }

    private List<ContextAlgebra> detectAgentToInterrupt(
            List<BlackboardHistory.Entry> entries, int newEntryIndex,
            AgentModels.InterruptRequest interruptRequest) {
        for (int i = newEntryIndex - 1; i >= 0; i--) {
            BlackboardHistory.Entry candidate = entries.get(i);
            if (candidate instanceof ContextAlgebra) continue;
            if (candidate != null && BlackboardHistory.isNonUtilityWorkflowRequest(candidate.input())) {
                return List.of(new ContextAlgebra.AgentToInterrupt(
                        entries.get(newEntryIndex).timestamp(),
                        entries.get(newEntryIndex).actionName(),
                        entries.get(newEntryIndex).input(),
                        i, newEntryIndex,
                        candidate.inputType(),
                        candidate.input(),
                        interruptRequest));
            }
        }
        return List.of();
    }

    private List<ContextAlgebra> detectInterruptCycleCompleted(
            List<BlackboardHistory.Entry> entries, int newEntryIndex) {
        BlackboardHistory.Entry returnEntry = entries.get(newEntryIndex);

        ContextAlgebra.AgentToInterrupt interruptEntry = null;
        int interruptIndex = -1;
        for (int i = newEntryIndex - 1; i >= 0; i--) {
            BlackboardHistory.Entry candidate = entries.get(i);
            if (candidate instanceof ContextAlgebra.AgentToInterrupt ati) {
                interruptEntry = ati;
                interruptIndex = i;
                break;
            }
            // If we hit a non-utility workflow request before finding an AgentToInterrupt,
            // there's no open interrupt cycle
            if (candidate instanceof BlackboardHistory.DefaultEntry
                    && BlackboardHistory.isNonUtilityWorkflowRequest(candidate.input())) {
                return List.of();
            }
        }

        if (interruptEntry == null) {
            return List.of();
        }

        return List.of(new ContextAlgebra.InterruptCycleCompleted(
                returnEntry.timestamp(),
                returnEntry.actionName(),
                returnEntry.input(),
                interruptIndex, newEntryIndex,
                interruptEntry.originatingAgentType(),
                interruptEntry.interruptRequest(),
                returnEntry.inputType()));
    }
}
