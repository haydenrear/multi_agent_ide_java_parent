package com.hayden.multiagentidelib.model.worktree;

import lombok.Builder;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Worktree for the main repository.
 */
@Builder(toBuilder = true)
public record MainWorktreeContext(
        String worktreeId,
        Path worktreePath,
        String baseBranch,
        WorktreeContext.WorktreeStatus status,
        String parentWorktreeId,
        String associatedNodeId,
        Instant createdAt,
        String lastCommitHash,
        
        // Main worktree-specific
        String repositoryUrl,
        boolean hasSubmodules,
        List<String> submoduleWorktreeIds,
        Map<String, String> metadata
) implements WorktreeContext {

    public MainWorktreeContext {
        if (worktreeId == null || worktreeId.isEmpty()) throw new IllegalArgumentException("worktreeId required");
        if (repositoryUrl == null || repositoryUrl.isEmpty()) throw new IllegalArgumentException("repositoryUrl required");
        if (worktreePath == null) throw new IllegalArgumentException("worktreePath required");
        if (submoduleWorktreeIds == null) submoduleWorktreeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
    }

    /**
     * Create updated version with new status.
     */
    public MainWorktreeContext withStatus(WorktreeContext.WorktreeStatus newStatus) {
        return new MainWorktreeContext(
                worktreeId, worktreePath, baseBranch, newStatus, parentWorktreeId,
                associatedNodeId, createdAt, lastCommitHash,
                repositoryUrl, hasSubmodules, submoduleWorktreeIds, metadata
        );
    }

    /**
     * Add a submodule worktree.
     */
    public MainWorktreeContext addSubmoduleWorktree(String submoduleWorktreeId) {
        List<String> newSubmodules = new ArrayList<>(submoduleWorktreeIds);
        newSubmodules.add(submoduleWorktreeId);
        return new MainWorktreeContext(
                worktreeId, worktreePath, baseBranch, status, parentWorktreeId,
                associatedNodeId, createdAt, lastCommitHash,
                repositoryUrl, hasSubmodules, newSubmodules, metadata
        );
    }

    /**
     * Update last commit hash.
     */
    public MainWorktreeContext withLastCommit(String commitHash) {
        return new MainWorktreeContext(
                worktreeId, worktreePath, baseBranch, status, parentWorktreeId,
                associatedNodeId, createdAt, commitHash,
                repositoryUrl, hasSubmodules, submoduleWorktreeIds, metadata
        );
    }
}
