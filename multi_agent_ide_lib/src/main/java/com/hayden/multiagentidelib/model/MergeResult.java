package com.hayden.multiagentidelib.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of merging a child worktree into parent.
 * Tracks merge status, conflicts, and submodule pointer updates.
 */
public record MergeResult(
        String mergeId,
        String childWorktreeId,
        String parentWorktreeId,
        boolean successful,
        String mergeCommitHash,
        List<MergeConflict> conflicts,
        List<SubmodulePointerUpdate> submoduleUpdates,
        String mergeMessage,
        Instant mergedAt
) {

    public MergeResult {
        if (mergeId == null || mergeId.isEmpty()) throw new IllegalArgumentException("mergeId required");
        if (childWorktreeId == null || childWorktreeId.isEmpty()) throw new IllegalArgumentException("childWorktreeId required");
        if (parentWorktreeId == null || parentWorktreeId.isEmpty()) throw new IllegalArgumentException("parentWorktreeId required");
        if (conflicts == null) conflicts = new ArrayList<>();
        if (submoduleUpdates == null) submoduleUpdates = new ArrayList<>();
    }

    /**
     * Information about a merge conflict.
     */
    public record MergeConflict(
            String filePath,
            String conflictType,  // "content", "delete/modify", "add/add", etc.
            String baseContent,
            String oursContent,
            String theirsContent
    ) {}

    /**
     * Update to a submodule pointer during merge.
     */
    public record SubmodulePointerUpdate(
            String submoduleName,
            String oldCommitHash,
            String newCommitHash,
            boolean requiresResolution
    ) {}
}
