package com.hayden.multiagentidelib.agent.history;

import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.ContextAlgebra;

import java.util.List;

/**
 * Interprets raw {@link BlackboardHistory.Entry} sequences and produces
 * {@link ContextAlgebra} composite entries. Implementations are Spring
 * {@code @Component} beans, auto-wired into {@link CompositeHistoryInterpreter}.
 *
 * <p>Interpreters are invoked inside the synchronized {@code addEntry()} call,
 * so they must NOT call back into {@link BlackboardHistory}. They receive the
 * raw entry list directly.</p>
 */
public interface HistoryInterpreter {

    /** Unique name for this interpreter. */
    String name();

    /**
     * Called when a new entry is added at the given index.
     *
     * @param entries       the full entry list (read-only view recommended)
     * @param newEntryIndex the index of the just-added entry
     * @return any new {@link ContextAlgebra} entries detected, or empty list
     */
    List<ContextAlgebra> interpret(List<BlackboardHistory.Entry> entries, int newEntryIndex);

    /** Priority for ordering interpreters. Lower runs first. */
    default int priority() {
        return 100;
    }
}
