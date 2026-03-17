package com.hayden.multiagentidelib.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface AgentPretty {

    @JsonIgnore
    String prettyPrint();

    /**
     * Thread-local tracking the active serialization context during a prettyPrint call.
     * Set by prettyPrint(AgentSerializationCtx) for context types that need to influence
     * helper methods (e.g. SkipWorktreeContextSerializationCtx suppresses worktree context output).
     */
    ThreadLocal<AgentSerializationCtx> ACTIVE_SERIALIZATION_CTX = new ThreadLocal<>();

    sealed interface AgentSerializationCtx {

        record StdReceiverSerialization() implements AgentSerializationCtx {
        }

        record InterruptSerialization() implements AgentSerializationCtx {
        }

        record GoalResolutionSerialization() implements AgentSerializationCtx {
        }

        record MergeSummarySerialization() implements AgentSerializationCtx {
        }

        record ResultsSerialization() implements AgentSerializationCtx {
        }

        /**
         * Suppresses worktree context serialization in any prettyPrint call.
         * Worktree context is provided once, authoritatively, by WorktreeSandboxPromptContributorFactory.
         * Emitting it for every historical request or result causes agents to resolve relative
         * file paths against the repository URL (tmp repo) instead of the worktree path.
         */
        record SkipWorktreeContextSerializationCtx() implements AgentSerializationCtx {
        }

        /**
         * Signals that a request is being rendered as a historical workflow entry, not the
         * current active request. Implementations should suppress fields that are only
         * meaningful for the current step (routing guardrails, phase instructions) to avoid
         * confusing downstream agents about where they are in the workflow.
         * Also suppresses worktree context (same as SkipWorktreeContextSerializationCtx).
         */
        record HistoricalRequestSerializationCtx() implements AgentSerializationCtx {
        }

    }

    default String prettyPrint(AgentSerializationCtx serializationCtx) {
        return switch (serializationCtx) {
            case AgentSerializationCtx.StdReceiverSerialization stdReceiverSerialization ->
                    prettyPrint();
            case AgentSerializationCtx.InterruptSerialization interruptSerialization ->
                    prettyPrintInterruptContinuation();
            case AgentSerializationCtx.GoalResolutionSerialization goalResolutionSerialization ->
                    prettyPrint();
            case AgentSerializationCtx.MergeSummarySerialization mergeSummarySerialization ->
                    prettyPrint();
            case AgentSerializationCtx.ResultsSerialization resultsSerialization ->
                    prettyPrint();
            case AgentSerializationCtx.SkipWorktreeContextSerializationCtx skipWorktreeCtx -> {
                ACTIVE_SERIALIZATION_CTX.set(skipWorktreeCtx);
                try {
                    yield prettyPrint();
                } finally {
                    ACTIVE_SERIALIZATION_CTX.remove();
                }
            }
            case AgentSerializationCtx.HistoricalRequestSerializationCtx historicalCtx -> {
                ACTIVE_SERIALIZATION_CTX.set(historicalCtx);
                try {
                    yield prettyPrint();
                } finally {
                    ACTIVE_SERIALIZATION_CTX.remove();
                }
            }
        };
    }

    @JsonIgnore
    default String prettyPrintInterruptContinuation() {
        return prettyPrint();
    }

}
