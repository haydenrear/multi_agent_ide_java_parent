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
         * Used when serializing results for collector/dispatch routing prompts.
         * Outputs only the parent (main) worktree — omits all submodule worktree
         * entries and replaces them with a count note, e.g. "(with 25 submodules)".
         * This prevents prompt bloat when multiple agents' results are consolidated.
         */
        record CollectorSerialization() implements AgentSerializationCtx {
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

        /**
         * Compact one-line summary of ContextAlgebra entries, for use in
         * WeAreHere execution history tables and similar overview contexts.
         */
        record AlgebraSummarySerialization() implements AgentSerializationCtx {
        }

        /**
         * Full-detail rendering of ContextAlgebra entries, for use in
         * CurationHistory and context manager prompts that need rich context.
         */
        record AlgebraDetailSerialization() implements AgentSerializationCtx {
        }

        /**
         * Commit-and-merge focused rendering of ContextAlgebra entries, for use
         * in MergeAggregation prompt contributors.
         */
        record AlgebraCommitMergeSerialization() implements AgentSerializationCtx {
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
            case AgentSerializationCtx.CollectorSerialization collectorCtx -> {
                ACTIVE_SERIALIZATION_CTX.set(collectorCtx);
                try {
                    yield prettyPrint();
                } finally {
                    ACTIVE_SERIALIZATION_CTX.remove();
                }
            }
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
            case AgentSerializationCtx.AlgebraSummarySerialization ignored ->
                    prettyPrint();
            case AgentSerializationCtx.AlgebraDetailSerialization ignored ->
                    prettyPrint();
            case AgentSerializationCtx.AlgebraCommitMergeSerialization ignored ->
                    prettyPrint();
        };
    }

    @JsonIgnore
    default String prettyPrintInterruptContinuation() {
        return prettyPrint();
    }

}
