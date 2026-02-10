package com.hayden.multiagentidelib.model.worktree;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Worktree for the main repository.
 */
@Builder(toBuilder = true)
public record MainWorktreeContext(
        String worktreeId,
        Path worktreePath,
        String baseBranch,
        String derivedBranch,
        WorktreeContext.WorktreeStatus status,
        String parentWorktreeId,
        String associatedNodeId,
        Instant createdAt,
        String lastCommitHash,
        
        // Main worktree-specific
        @JsonIgnore
        String repositoryUrl,
        boolean hasSubmodules,
        List<SubmoduleWorktreeContext> submoduleWorktrees,
        Map<String, String> metadata
) implements WorktreeContext {

    public MainWorktreeContext {
        if (worktreeId == null || worktreeId.isEmpty()) throw new IllegalArgumentException("worktreeId required");
        if (repositoryUrl == null || repositoryUrl.isEmpty()) throw new IllegalArgumentException("repositoryUrl required");
        if (worktreePath == null) throw new IllegalArgumentException("worktreePath required");
        if (submoduleWorktrees == null) submoduleWorktrees = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
    }

    /**
     * Backward-compatible view of submodule worktree IDs.
     */
    public List<String> submoduleWorktreeIds() {
        return submoduleWorktrees.stream()
                .filter(Objects::nonNull)
                .map(SubmoduleWorktreeContext::worktreeId)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Create updated version with new status.
     */
    public MainWorktreeContext withStatus(WorktreeContext.WorktreeStatus newStatus) {
        return new MainWorktreeContext(
                worktreeId, worktreePath, baseBranch, derivedBranch, newStatus, parentWorktreeId,
                associatedNodeId, createdAt, lastCommitHash,
                repositoryUrl, hasSubmodules, submoduleWorktrees, metadata
        );
    }

    /**
     * Add a submodule worktree.
     */
    public MainWorktreeContext addSubmoduleWorktree(SubmoduleWorktreeContext submoduleWorktree) {
        List<SubmoduleWorktreeContext> newSubmodules = new ArrayList<>(submoduleWorktrees);
        newSubmodules.add(submoduleWorktree);
        return new MainWorktreeContext(
                worktreeId, worktreePath, baseBranch, derivedBranch, status, parentWorktreeId,
                associatedNodeId, createdAt, lastCommitHash,
                repositoryUrl, hasSubmodules, newSubmodules, metadata
        );
    }

    /**
     * Update last commit hash.
     */
    public MainWorktreeContext withLastCommit(String commitHash) {
        return new MainWorktreeContext(
                worktreeId, worktreePath, baseBranch, derivedBranch, status, parentWorktreeId,
                associatedNodeId, createdAt, commitHash,
                repositoryUrl, hasSubmodules, submoduleWorktrees, metadata
        );
    }
}
