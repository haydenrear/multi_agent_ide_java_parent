package com.hayden.multiagentidelib.model.merge;

import com.hayden.multiagentidelib.model.MergeResult;
import lombok.Builder;

/**
 * Result of merging a single submodule worktree.
 */
@Builder(toBuilder = true)
public record SubmoduleMergeResult(
        String submoduleName,
        String childWorktreePath,
        String parentWorktreePath,
        MergeResult mergeResult,
        boolean pointerUpdated
) {
    public SubmoduleMergeResult {
        if (submoduleName == null || submoduleName.isBlank()) {
            throw new IllegalArgumentException("submoduleName required");
        }
    }
    
    public boolean successful() {
        return mergeResult != null && mergeResult.successful();
    }
}
