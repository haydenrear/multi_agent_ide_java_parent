package com.hayden.multiagentidelib.model.worktree;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Worktree for a git submodule within a main worktree.
 */
@Builder
public record SubmoduleWorktreeContext(
        String worktreeId,
        Path worktreePath,
        String baseBranch,
        WorktreeContext.WorktreeStatus status,
        String parentWorktreeId,
        String associatedNodeId,
        Instant createdAt,
        String lastCommitHash,
        
        // Submodule-specific
        String submoduleName,
        @JsonIgnore
        String submoduleUrl,
        String mainWorktreeId,  // Reference to parent main worktree
        Map<String, String> metadata
) implements WorktreeContext {

    public SubmoduleWorktreeContext {
        if (worktreeId == null || worktreeId.isEmpty()) throw new IllegalArgumentException("worktreeId required");
        if (submoduleName == null || submoduleName.isEmpty()) throw new IllegalArgumentException("submoduleName required");
        if (worktreePath == null) throw new IllegalArgumentException("worktreePath required");
        if (metadata == null) metadata = new HashMap<>();
    }

    public String derivedBranch() {
        return baseBranch;
    }

    /**
     * Create updated version with new status.
     */
    public SubmoduleWorktreeContext withStatus(WorktreeContext.WorktreeStatus newStatus) {
        return new SubmoduleWorktreeContext(
                worktreeId, worktreePath, baseBranch, newStatus, parentWorktreeId,
                associatedNodeId, createdAt, lastCommitHash,
                submoduleName, submoduleUrl, mainWorktreeId, metadata
        );
    }

    /**
     * Update last commit hash.
     */
    public SubmoduleWorktreeContext withLastCommit(String commitHash) {
        return new SubmoduleWorktreeContext(
                worktreeId, worktreePath, baseBranch, status, parentWorktreeId,
                associatedNodeId, createdAt, commitHash,
                submoduleName, submoduleUrl, mainWorktreeId, metadata
        );
    }
}
