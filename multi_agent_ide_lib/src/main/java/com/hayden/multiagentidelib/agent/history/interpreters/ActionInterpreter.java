package com.hayden.multiagentidelib.agent.history.interpreters;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.ContextAlgebra;
import com.hayden.multiagentidelib.agent.history.HistoryInterpreter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects a complete agent action: a request entry, the associated message entry
 * (which already accumulates all stream deltas/thoughts/tool calls), and the
 * result/routing entry. Uses contextId hierarchy to match.
 *
 * <p>Runs at high priority so higher-level interpreters can see AgentAction entries.</p>
 */
@Slf4j
@Component
public class ActionInterpreter implements HistoryInterpreter {

    @Override
    public String name() {
        return "action";
    }

    @Override
    public int priority() {
        return 20;
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

        // Only trigger on result/routing entries
        if (!(newEntry.input() instanceof AgentModels.AgentResult)
                && !isRoutingType(newEntry.inputType())) {
            return List.of();
        }

        // Walk backward to find the matching request entry using contextId hierarchy
        ArtifactKey resultContextId = newEntry.contextId();

        BlackboardHistory.Entry requestEntry = null;
        int requestIndex = -1;
        BlackboardHistory.MessageEntry messageEntry = null;
        Class<?> agentRequestType = null;

        for (int i = newEntryIndex - 1; i >= 0; i--) {
            BlackboardHistory.Entry candidate = entries.get(i);
            if (candidate instanceof ContextAlgebra) {
                continue;
            }
            if (candidate instanceof BlackboardHistory.MessageEntry me) {
                // The MessageEntry for this action shares the same contextId hierarchy
                if (messageEntry == null && resultContextId != null && me.contextId() != null
                        && (me.contextId().isDescendantOf(resultContextId)
                            || me.contextId().equals(resultContextId)
                            || contextIdsRelated(me.contextId(), resultContextId))) {
                    messageEntry = me;
                }
                continue;
            }
            if (candidate instanceof BlackboardHistory.DefaultEntry de) {
                if (de.input() instanceof AgentModels.AgentRequest) {
                    ArtifactKey candidateCtxId = de.contextId();
                    if (contextIdsRelated(candidateCtxId, resultContextId)) {
                        requestEntry = de;
                        requestIndex = i;
                        agentRequestType = de.inputType();
                        break;
                    }
                }
                // If we hit a different default entry, stop searching
                if (de.input() instanceof AgentModels.AgentRequest
                        || de.input() instanceof AgentModels.AgentResult) {
                    break;
                }
            }
        }

        if (requestEntry == null) {
            return List.of();
        }

        return List.of(new ContextAlgebra.AgentAction(
                newEntry.timestamp(),
                newEntry.actionName(),
                newEntry.input(),
                requestIndex, newEntryIndex,
                requestEntry,
                messageEntry,
                newEntry,
                agentRequestType));
    }

    private static boolean contextIdsRelated(ArtifactKey a, ArtifactKey b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        return a.isDescendantOf(b) || b.isDescendantOf(a)
                // Same parent = sibling contextIds (request and result often share parent)
                || a.parent().isPresent() && a.parent().equals(b.parent());
    }

    private static boolean isRoutingType(Class<?> type) {
        if (type == null) return false;
        String name = type.getSimpleName();
        return name.endsWith("Routing");
    }
}
