package com.hayden.multiagentidelib.agent.history.interpreters;

import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.ContextAlgebra;
import com.hayden.multiagentidelib.agent.history.HistoryInterpreter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects when an interrupt cycle completes but the return agent differs from
 * the originating agent, indicating a human-initiated reroute.
 */
@Slf4j
@Component
public class HumanInterruptInterpreter implements HistoryInterpreter {

    @Override
    public String name() {
        return "human-interrupt";
    }

    @Override
    public int priority() {
        return 70; // runs after RouteBackInterruptInterpreter
    }

    @Override
    public List<ContextAlgebra> interpret(List<BlackboardHistory.Entry> entries, int newEntryIndex) {
        // Look for InterruptCycleCompleted entries just emitted
        // (they would have been appended after newEntryIndex by the RouteBackInterruptInterpreter)
        for (int i = newEntryIndex; i < entries.size(); i++) {
            BlackboardHistory.Entry entry = entries.get(i);
            if (entry instanceof ContextAlgebra.InterruptCycleCompleted icc) {
                if (icc.originatingAgentType() != null && icc.returnAgentType() != null
                        && !icc.originatingAgentType().equals(icc.returnAgentType())) {
                    return List.of(new ContextAlgebra.HumanInterruptReroute(
                            icc.timestamp(),
                            icc.actionName(),
                            icc.input(),
                            icc.sourceStartIndex(), icc.sourceEndIndex(),
                            icc.originatingAgentType(),
                            icc.interruptRequest(),
                            icc.returnAgentType()));
                }
            }
        }
        return List.of();
    }
}
