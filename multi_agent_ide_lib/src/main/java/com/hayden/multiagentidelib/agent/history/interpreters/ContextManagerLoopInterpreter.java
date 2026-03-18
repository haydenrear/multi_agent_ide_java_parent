package com.hayden.multiagentidelib.agent.history.interpreters;

import com.hayden.acp_cdc_ai.acp.events.HasContextId;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.ContextAlgebra;
import com.hayden.multiagentidelib.agent.history.HistoryInterpreter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects context manager round-trips. Includes the routing request, all interrupt/review/
 * merge/commit requests until we find either another agent request (CM routed to a different
 * agent) or the agent result for the agent that routed to it.
 */
@Slf4j
@Component
public class ContextManagerLoopInterpreter implements HistoryInterpreter {

    @Override
    public String name() {
        return "context-manager-loop";
    }

    @Override
    public int priority() {
        return 90;
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

        // Only trigger when we see a non-CM, non-interrupt, non-utility workflow request or result
        // This signals the CM loop has ended
        if (newEntry.input() instanceof AgentModels.ContextManagerRequest
                || newEntry.input() instanceof AgentModels.ContextManagerRoutingRequest
                || newEntry.input() instanceof AgentModels.InterruptRequest
                || newEntry.input() instanceof AgentModels.CommitAgentRequest
                || newEntry.input() instanceof AgentModels.CommitAgentResult
                || newEntry.input() instanceof AgentModels.MergeConflictRequest
                || newEntry.input() instanceof AgentModels.MergeConflictResult) {
            return List.of();
        }
        if (!(newEntry.input() instanceof AgentModels.AgentRequest)
                && !(newEntry.input() instanceof AgentModels.AgentResult)) {
            return List.of();
        }

        // Walk backward to find the ContextManagerRequest
        AgentModels.ContextManagerRequest cmRequest = null;
        int cmIndex = -1;
        HasContextId routingRequest = null;
        List<BlackboardHistory.Entry> containedEntries = new ArrayList<>();

        for (int i = newEntryIndex - 1; i >= 0; i--) {
            BlackboardHistory.Entry candidate = entries.get(i);
            if (candidate instanceof ContextAlgebra) continue;
            if (candidate == null || candidate.input() == null) continue;

            // Collect CM-scoped entries (interrupts, reviews, merges, commits, CM routing)
            if (candidate.input() instanceof AgentModels.InterruptRequest
                    || candidate.input() instanceof AgentModels.CommitAgentRequest
                    || candidate.input() instanceof AgentModels.CommitAgentResult
                    || candidate.input() instanceof AgentModels.MergeConflictRequest
                    || candidate.input() instanceof AgentModels.MergeConflictResult
                    || candidate.input() instanceof AgentModels.ContextManagerRoutingRequest) {
                containedEntries.addFirst(candidate);
                continue;
            }

            if (candidate.input() instanceof AgentModels.ContextManagerRequest cm) {
                cmRequest = cm;
                cmIndex = i;
                containedEntries.addFirst(candidate);
                break;
            }

            // Hit a non-CM, non-utility entry — no open CM loop
            break;
        }

        if (cmRequest == null) {
            return List.of();
        }

        // Find the routing request (the entry that triggered the CM route)
        for (int i = cmIndex - 1; i >= 0; i--) {
            BlackboardHistory.Entry candidate = entries.get(i);
            if (candidate instanceof ContextAlgebra) continue;
            if (candidate != null && candidate.input() instanceof AgentModels.ContextManagerRoutingRequest cmr) {
                routingRequest = cmr;
                break;
            }
            if (candidate != null && candidate.input() instanceof AgentModels.AgentRequest
                    && !(candidate.input() instanceof AgentModels.ContextManagerRequest)) {
                routingRequest = candidate.input();
                break;
            }
            break;
        }

        return List.of(new ContextAlgebra.ContextManagerLoop(
                newEntry.timestamp(),
                newEntry.actionName(),
                newEntry.input(),
                cmIndex,
                newEntryIndex,
                routingRequest,
                cmRequest,
                containedEntries,
                newEntry.input()));
    }

}
