package com.hayden.multiagentidelib.model.merge;

import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated merge state for routing LLM after child→trunk merges.
 * Attached to ResultsRequest types (TicketAgentResults, PlanningAgentResults, DiscoveryAgentResults).
 */
@Builder(toBuilder = true)
public record MergeAggregation(
        List<AgentMergeStatus> merged,
        List<AgentMergeStatus> pending,
        AgentMergeStatus conflicted
) {
    public MergeAggregation {
        if (merged == null) {
            merged = new ArrayList<>();
        }
        if (pending == null) {
            pending = new ArrayList<>();
        }
    }
    
    /**
     * Returns true if all agents were successfully merged (no conflicts, nothing pending).
     */
    public boolean allSuccessful() {
        return conflicted == null && pending.isEmpty();
    }
    
    /**
     * Returns true if there is a merge conflict.
     */
    public boolean hasConflict() {
        return conflicted != null;
    }
    
    /**
     * Returns the total number of agents (merged + pending + conflicted).
     */
    public int totalCount() {
        return merged.size() + pending.size() + (conflicted != null ? 1 : 0);
    }
    
    /**
     * Creates an empty aggregation (no agents to merge).
     */
    public static MergeAggregation empty() {
        return MergeAggregation.builder()
                .merged(List.of())
                .pending(List.of())
                .build();
    }
    
    /**
     * Creates an all-successful aggregation.
     */
    public static MergeAggregation allMerged(List<AgentMergeStatus> merged) {
        return MergeAggregation.builder()
                .merged(merged != null ? merged : List.of())
                .pending(List.of())
                .build();
    }
}
