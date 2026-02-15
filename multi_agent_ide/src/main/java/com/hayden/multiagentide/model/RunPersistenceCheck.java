package com.hayden.multiagentide.model;

import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
public record RunPersistenceCheck(
        String checkId,
        String runId,
        Category category,
        Status status,
        String details,
        Instant checkedAt
) {

    public enum Category {
        RUN_METADATA,
        TIMELINE,
        STATUS_SUMMARY,
        ARTIFACT_LINKS
    }

    public enum Status {
        PASS,
        FAIL
    }
}
