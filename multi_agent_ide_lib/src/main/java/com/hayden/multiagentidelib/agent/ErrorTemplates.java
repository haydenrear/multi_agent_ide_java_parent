package com.hayden.multiagentidelib.agent;

import org.jspecify.annotations.Nullable;

/**
 * Per-action configuration mapping error states to alternative template names.
 * Stored as a field on AgentActionMetadata.
 */
public record ErrorTemplates(
        @Nullable String compactionFirstTemplate,
        @Nullable String compactionMultipleTemplate,
        @Nullable String parseErrorTemplate,
        @Nullable String defaultRetryTemplate
) {}
