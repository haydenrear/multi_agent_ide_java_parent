package com.hayden.multiagentidelib.agent.history;

import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.ContextAlgebra;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates all {@link HistoryInterpreter} beans. When a new entry is added to
 * blackboard history, this component runs each interpreter in priority order and
 * collects the resulting {@link ContextAlgebra} entries.
 *
 * <p>Stateless singleton — safe for concurrent use from different
 * {@link BlackboardHistory} instances.</p>
 */
@Slf4j
@Component
public class CompositeHistoryInterpreter {

    private final List<HistoryInterpreter> interpreters;

    public CompositeHistoryInterpreter(List<HistoryInterpreter> interpreters) {
        this.interpreters = interpreters.stream()
                .sorted(Comparator.comparingInt(HistoryInterpreter::priority))
                .toList();
        log.info("Initialized CompositeHistoryInterpreter with {} interpreters: {}",
                this.interpreters.size(),
                this.interpreters.stream().map(HistoryInterpreter::name).toList());
    }

    /**
     * Run all interpreters against the entry list after a new entry was added.
     *
     * @param entries       the full entry list (may be mutated by caller to append results)
     * @param newEntryIndex index of the just-added raw entry
     * @return all {@link ContextAlgebra} entries produced by the interpreters
     */
    public List<ContextAlgebra> interpretNewEntry(List<BlackboardHistory.Entry> entries, int newEntryIndex) {
        List<ContextAlgebra> results = new ArrayList<>();
        for (HistoryInterpreter interpreter : interpreters) {
            try {
                List<ContextAlgebra> produced = interpreter.interpret(entries, newEntryIndex);
                if (produced != null && !produced.isEmpty()) {
                    results.addAll(produced);
                }
            } catch (Exception e) {
                log.error("Interpreter {} threw exception on entry index {}: {}",
                        interpreter.name(), newEntryIndex, e.getMessage(), e);
            }
        }
        return results;
    }
}
