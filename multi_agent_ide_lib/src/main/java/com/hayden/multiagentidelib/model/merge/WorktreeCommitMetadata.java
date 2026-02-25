package com.hayden.multiagentidelib.model.merge;

import lombok.Builder;
import lombok.With;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadata for commits made before merge phases to ensure dirty worktrees are merged by commit.
 */
@Builder(toBuilder = true)
@With
public record WorktreeCommitMetadata(
        String worktreeId,
        String worktreePath,
        String commitHash,
        String commitMessage,
        String summary,
        List<String> changedFiles,
        Instant committedAt
) {
    public WorktreeCommitMetadata {
        if (worktreeId == null || worktreeId.isBlank()) {
            throw new IllegalArgumentException("worktreeId required");
        }
        if (commitMessage == null || commitMessage.isBlank()) {
            throw new IllegalArgumentException("commitMessage required");
        }
        if (changedFiles == null) {
            changedFiles = new ArrayList<>();
        }
        if (committedAt == null) {
            committedAt = Instant.now();
        }
    }
}
