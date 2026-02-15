package com.hayden.multiagentide.model;

import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
public record DebugRun(
        String runId,
        String goal,
        String repositoryUrl,
        String baseBranch,
        String title,
        RunStatus status,
        OutcomeClass outcomeClass,
        boolean loopRisk,
        boolean degraded,
        Instant startedAt,
        Instant endedAt
) {
    public DebugRun {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId is required");
        }
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("goal is required");
        }
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new IllegalArgumentException("repositoryUrl is required");
        }
        if (status == null) {
            status = RunStatus.QUEUED;
        }
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public enum RunStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        STOPPED,
        PRUNED
    }

    public enum OutcomeClass {
        SUCCESS,
        FAILURE,
        STALLED,
        LOOP_RISK,
        DEGRADED
    }
}
