package com.hayden.multiagentidelib.agent;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.HasContextId;

/**
 * Sealed interface representing the error state for a given action execution.
 * Stored in BlackboardHistory entries. Matchable by AgentExecutor and PromptContributors.
 *
 * <p>Every variant — including {@link NoError} — carries a {@code contextId} that is a
 * child of the {@code AgentExecutorStartEvent.nodeId}, which is itself a child of the
 * chat session key ({@code chatId}). This preserves the hierarchical ArtifactKey structure:
 *
 * <pre>
 *   chatId (session key)
 *     └── AgentExecutorStartEvent.nodeId = chatId.createChild()
 *           └── ErrorDescriptor.contextId = startEventNodeId.createChild()
 * </pre>
 */
public sealed interface ErrorDescriptor extends HasContextId
        permits ErrorDescriptor.NoError,
                ErrorDescriptor.CompactionError,
                ErrorDescriptor.ParseError,
                ErrorDescriptor.TimeoutError,
                ErrorDescriptor.UnparsedToolCallError,
                ErrorDescriptor.NullResultError,
                ErrorDescriptor.IncompleteJsonError {

    enum CompactionStatus {
        NONE,
        FIRST,
        MULTIPLE;

        public CompactionStatus next() {
            return switch (this) {
                case NONE -> FIRST;
                case FIRST, MULTIPLE -> MULTIPLE;
            };
        }
    }

    record NoError(ArtifactKey contextId) implements ErrorDescriptor {}

    record CompactionError(
            String actionName,
            CompactionStatus compactionStatus,
            boolean compactionCompleted,
            ArtifactKey contextId
    ) implements ErrorDescriptor {}

    record ParseError(
            String actionName,
            String rawOutput,
            ArtifactKey contextId
    ) implements ErrorDescriptor {}

    record TimeoutError(
            String actionName,
            int retryCount,
            ArtifactKey contextId
    ) implements ErrorDescriptor {}

    record UnparsedToolCallError(
            String actionName,
            String toolCallText,
            ArtifactKey contextId
    ) implements ErrorDescriptor {}

    record NullResultError(
            String actionName,
            int retryCount,
            int maxRetries,
            ArtifactKey contextId
    ) implements ErrorDescriptor {}

    record IncompleteJsonError(
            String actionName,
            int retryCount,
            String rawFragment,
            ArtifactKey contextId
    ) implements ErrorDescriptor {}
}
