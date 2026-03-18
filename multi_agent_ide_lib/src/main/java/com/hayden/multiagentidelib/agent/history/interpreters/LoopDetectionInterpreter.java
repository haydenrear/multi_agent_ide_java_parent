package com.hayden.multiagentidelib.agent.history.interpreters;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.ContextAlgebra;
import com.hayden.multiagentidelib.agent.DegenerateLoopPolicy;
import com.hayden.multiagentidelib.agent.history.HistoryInterpreter;
import com.hayden.multiagentidelib.events.DegenerateLoopException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Detects degenerate loops by delegating to all registered {@link DegenerateLoopPolicy}
 * implementations. Emits a {@link ContextAlgebra.LoopDetected} for each policy that detects a loop.
 */
@Slf4j
@Component
public class LoopDetectionInterpreter implements HistoryInterpreter {

    private final List<DegenerateLoopPolicy> loopPolicies;

    public LoopDetectionInterpreter(List<DegenerateLoopPolicy> loopPolicies) {
        this.loopPolicies = loopPolicies != null ? loopPolicies : List.of();
    }

    @Override
    public String name() {
        return "loop-detection";
    }

    @Override
    public int priority() {
        return 150;
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

        Object input = newEntry.input();
        if (!(input instanceof Artifact.AgentModel agentModel)) {
            return List.of();
        }

        String actionName = newEntry.actionName();

        // Create a temporary history wrapping the current entries
        BlackboardHistory tempHistory = new BlackboardHistory(
                new BlackboardHistory.History(entries), null, null);

        List<ContextAlgebra> detected = new ArrayList<>();

        for (DegenerateLoopPolicy policy : loopPolicies) {
            try {
                Optional<DegenerateLoopException> result = policy.detectLoop(
                        tempHistory, actionName, agentModel);
                result.ifPresent(ex -> detected.add(new ContextAlgebra.LoopDetected(
                        newEntry.timestamp(),
                        newEntry.actionName(),
                        newEntry.input(),
                        0, newEntryIndex,
                        List.of(ex.getMessage()),
                        ex.getRepetitionCount())));
            } catch (DegenerateLoopException e) {
                detected.add(new ContextAlgebra.LoopDetected(
                        newEntry.timestamp(),
                        newEntry.actionName(),
                        newEntry.input(),
                        0, newEntryIndex,
                        List.of(e.getMessage()),
                        e.getRepetitionCount()));
            } catch (Exception e) {
                log.error("Loop detection policy {} threw: {}", policy.getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        return detected;
    }
}
