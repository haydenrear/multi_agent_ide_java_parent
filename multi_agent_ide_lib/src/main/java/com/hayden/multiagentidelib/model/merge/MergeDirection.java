package com.hayden.multiagentidelib.model.merge;

/**
 * Direction of a worktree merge operation.
 */
public enum MergeDirection {
    /**
     * Merging from parent/trunk worktree into child worktree (pull latest changes).
     */
    TRUNK_TO_CHILD,
    
    /**
     * Merging from child worktree back to parent/trunk (push changes).
     */
    CHILD_TO_TRUNK,

    /**
     * Merging from worktree derived branches back into the original source repository branches.
     */
    WORKTREE_TO_SOURCE
}
