package com.hayden.multiagentidelib.model.merge;

import com.hayden.multiagentidelib.agent.SkipPropertyFilter;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import lombok.Builder;
import lombok.With;

/**
 * Tracks merge status for a single agent in the MergeAggregation.
 */
@Builder(toBuilder = true)
@With
public record AgentMergeStatus(
        String agentResultId,
        @SkipPropertyFilter
        WorktreeSandboxContext worktreeContext,
        MergeDescriptor mergeDescriptor
) {
    public AgentMergeStatus {
        if (agentResultId == null || agentResultId.isBlank()) {
            throw new IllegalArgumentException("agentResultId required");
        }
    }
    
    /**
     * Creates a pending status (before merge attempt).
     */
    public static AgentMergeStatus pending(String agentResultId, WorktreeSandboxContext worktreeContext) {
        return AgentMergeStatus.builder()
                .agentResultId(agentResultId)
                .worktreeContext(worktreeContext)
                .build();
    }
    
    /**
     * Returns true if this agent has been successfully merged.
     */
    public boolean isMerged() {
        return mergeDescriptor != null && mergeDescriptor.successful();
    }
    
    /**
     * Returns true if this agent has a merge conflict.
     */
    public boolean hasConflict() {
        return mergeDescriptor != null && !mergeDescriptor.successful();
    }
}
