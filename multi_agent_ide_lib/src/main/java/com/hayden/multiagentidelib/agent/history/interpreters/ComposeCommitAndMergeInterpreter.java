package com.hayden.multiagentidelib.agent.history.interpreters;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.ContextAlgebra;
import com.hayden.multiagentidelib.agent.history.HistoryInterpreter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects commit+merge sequences:
 * <ul>
 *   <li>{@link ContextAlgebra.AgentCommitAndMerge} when a {@link AgentModels.CommitAgentResult} appears,
 *       looking forward for any {@link AgentModels.MergeConflictResult} for the same agent.</li>
 *   <li>{@link ContextAlgebra.DispatchBatchCommitAndMerge} when a collector request arrives
 *       and all preceding agents have individual AgentCommitAndMerge entries.</li>
 * </ul>
 */
@Slf4j
@Component
public class ComposeCommitAndMergeInterpreter implements HistoryInterpreter {

    @Override
    public String name() {
        return "compose-commit-and-merge";
    }

    @Override
    public int priority() {
        return 110;
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

        // Detect individual AgentCommitAndMerge on CommitAgentResult
        if (newEntry.input() instanceof AgentModels.CommitAgentResult commitResult) {
            return detectAgentCommitAndMerge(entries, newEntryIndex, commitResult);
        }

        // Detect DispatchBatchCommitAndMerge on collector request arrival
        if (isCollectorRequest(newEntry.inputType())) {
            return detectBatchCommitAndMerge(entries, newEntryIndex);
        }

        return List.of();
    }

    private List<ContextAlgebra> detectAgentCommitAndMerge(
            List<BlackboardHistory.Entry> entries, int commitIndex,
            AgentModels.CommitAgentResult commitResult) {

        // Look backward for the CommitAgentRequest to find agent identity
        String agentIdentity = null;
        int startIndex = commitIndex;
        for (int i = commitIndex - 1; i >= 0; i--) {
            BlackboardHistory.Entry candidate = entries.get(i);
            if (candidate instanceof ContextAlgebra) continue;
            if (candidate != null && candidate.input() instanceof AgentModels.CommitAgentRequest car) {
                agentIdentity = car.contextId() != null ? car.contextId().value() : null;
                startIndex = i;
                break;
            }
            // Stop if we hit something unrelated
            if (candidate instanceof BlackboardHistory.DefaultEntry
                    && !(candidate.input() instanceof AgentModels.MergeConflictRequest)
                    && !(candidate.input() instanceof AgentModels.MergeConflictResult)) {
                break;
            }
        }

        // Look forward for a MergeConflictResult (may not exist yet)
        AgentModels.MergeConflictResult mergeResult = null;
        for (int i = commitIndex + 1; i < entries.size(); i++) {
            BlackboardHistory.Entry candidate = entries.get(i);
            if (candidate instanceof ContextAlgebra) continue;
            if (candidate != null && candidate.input() instanceof AgentModels.MergeConflictResult mcr) {
                mergeResult = mcr;
                break;
            }
            // Stop on unrelated entries
            if (candidate instanceof BlackboardHistory.DefaultEntry
                    && !(candidate.input() instanceof AgentModels.MergeConflictRequest)) {
                break;
            }
        }

        return List.of(new ContextAlgebra.AgentCommitAndMerge(
                entries.get(commitIndex).timestamp(),
                entries.get(commitIndex).actionName(),
                entries.get(commitIndex).input(),
                startIndex, commitIndex,
                commitResult,
                mergeResult,
                agentIdentity));
    }

    private List<ContextAlgebra> detectBatchCommitAndMerge(
            List<BlackboardHistory.Entry> entries, int collectorIndex) {

        // Collect all AgentCommitAndMerge entries between the dispatch and this collector request
        List<ContextAlgebra.AgentCommitAndMerge> agentMerges = new ArrayList<>();
        Class<?> dispatchRequestType = null;
        int startIndex = collectorIndex;

        for (int i = collectorIndex - 1; i >= 0; i--) {
            BlackboardHistory.Entry candidate = entries.get(i);
            if (candidate instanceof ContextAlgebra.AgentCommitAndMerge acm) {
                agentMerges.add(acm);
                startIndex = i;
                continue;
            }
            if (candidate instanceof ContextAlgebra) continue;
            // Find the dispatch request type
            if (candidate != null && isDispatchRequest(candidate.inputType())) {
                dispatchRequestType = candidate.inputType();
                startIndex = i;
                break;
            }
        }

        if (agentMerges.isEmpty()) {
            return List.of();
        }

        return List.of(new ContextAlgebra.DispatchBatchCommitAndMerge(
                entries.get(collectorIndex).timestamp(),
                entries.get(collectorIndex).actionName(),
                entries.get(collectorIndex).input(),
                startIndex, collectorIndex,
                dispatchRequestType != null ? dispatchRequestType : Object.class,
                agentMerges));
    }

    private static boolean isCollectorRequest(Class<?> type) {
        if (type == null) return false;
        return type == AgentModels.DiscoveryCollectorRequest.class
                || type == AgentModels.PlanningCollectorRequest.class
                || type == AgentModels.TicketCollectorRequest.class
                || type == AgentModels.OrchestratorCollectorRequest.class;
    }

    private static boolean isDispatchRequest(Class<?> type) {
        if (type == null) return false;
        return type == AgentModels.DiscoveryAgentRequests.class
                || type == AgentModels.PlanningAgentRequests.class
                || type == AgentModels.TicketAgentRequests.class;
    }
}
