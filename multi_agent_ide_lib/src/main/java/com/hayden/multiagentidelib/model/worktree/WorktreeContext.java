package com.hayden.multiagentidelib.model.worktree;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Context for a git worktree - can represent either main repo or submodule.
 * Sealed interface to distinguish main worktree from submodule worktrees.
 */
public sealed interface WorktreeContext 
        permits MainWorktreeContext, SubmoduleWorktreeContext {

    /**
     * Unique identifier for this worktree.
     */
    String worktreeId();

    /**
     * Path to the worktree root.
     */
    Path worktreePath();

    /**
     * The git branch this worktree is on.
     */
    String baseBranch();

    /**
     * Current status of the worktree.
     */
    WorktreeStatus status();

    /**
     * ID of parent worktree, or null if root.
     */
    String parentWorktreeId();

    /**
     * Associated node ID.
     */
    String associatedNodeId();

    /**
     * Timestamp when worktree was created.
     */
    Instant createdAt();

    /**
     * Last commit hash in this worktree.
     */
    String lastCommitHash();

    /**
     * Worktree status.
     */
    enum WorktreeStatus {
        ACTIVE,      // Being worked on
        MERGED,      // Merged to parent
        DISCARDED    // Removed/pruned
    }
}
