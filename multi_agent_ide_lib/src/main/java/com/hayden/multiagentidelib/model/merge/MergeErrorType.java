package com.hayden.multiagentidelib.model.merge;

/**
 * Coarse classification for merge failures.
 */
public enum MergeErrorType {
    NONE,
    AUTO_COMMIT_FAILED,
    MERGE_CONFLICT,
    MERGE_EXECUTION_FAILED,
    CONFLICT_AGENT_FAILED,
    CONTEXT_MISSING,
    UNKNOWN
}
