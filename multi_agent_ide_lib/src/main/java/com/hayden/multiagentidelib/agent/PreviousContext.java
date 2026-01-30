package com.hayden.multiagentidelib.agent;

import com.hayden.multiagentidelib.template.DiscoveryReport;
import lombok.Builder;

import java.time.Instant;

public sealed interface PreviousContext extends AgentContext permits
       PreviousContext.OrchestratorPreviousContext,
       PreviousContext.OrchestratorCollectorPreviousContext,
       PreviousContext.DiscoveryOrchestratorPreviousContext,
       PreviousContext.PlanningOrchestratorPreviousContext,
       PreviousContext.TicketOrchestratorPreviousContext,
       PreviousContext.DiscoveryAgentPreviousContext,
       PreviousContext.PlanningAgentPreviousContext,
       PreviousContext.TicketAgentPreviousContext,
       PreviousContext.DiscoveryCollectorPreviousContext,
       PreviousContext.PlanningCollectorPreviousContext,
       PreviousContext.TicketCollectorPreviousContext,
       PreviousContext.ReviewPreviousContext,
       PreviousContext.MergerPreviousContext {

    ContextId previousContextId();

    String serializedOutput();

    String errorMessage();

    String errorStackTrace();

    int attemptNumber();

    Instant previousAttemptAt();

    @Override
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
        };
    }

    @Override
    default String prettyPrint() {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "Attempt", attemptNumber() > 0 ? String.valueOf(attemptNumber()) : null);
        appendLine(builder, "Previous Context Id", valueOf(previousContextId()));
        appendLine(builder, "Previous Attempt At", formatInstant(previousAttemptAt()));
        appendLine(builder, "Error Message", errorMessage());
        appendLine(builder, "Error Stack Trace", errorStackTrace());
        appendSection(builder, "Serialized Output", serializedOutput());

        switch (this) {
            case OrchestratorPreviousContext ctx -> {
                appendCuration(builder, ctx.previousDiscoveryCuration(), "Previous Discovery Curation");
                appendCuration(builder, ctx.previousPlanningCuration(), "Previous Planning Curation");
                appendCuration(builder, ctx.previousTicketCuration(), "Previous Ticket Curation");
            }
            case OrchestratorCollectorPreviousContext ctx -> {
                appendCuration(builder, ctx.previousDiscoveryCuration(), "Previous Discovery Curation");
                appendCuration(builder, ctx.previousPlanningCuration(), "Previous Planning Curation");
                appendCuration(builder, ctx.previousTicketCuration(), "Previous Ticket Curation");
            }
            case DiscoveryOrchestratorPreviousContext ctx -> {
                appendCuration(builder, ctx.previousDiscoveryCuration(), "Previous Discovery Curation");
                appendCuration(builder, ctx.previousPlanningCuration(), "Previous Planning Curation");
                appendCuration(builder, ctx.previousTicketCuration(), "Previous Ticket Curation");
            }
            case PlanningOrchestratorPreviousContext ctx -> {
                appendCuration(builder, ctx.previousDiscoveryCuration(), "Previous Discovery Curation");
                appendCuration(builder, ctx.previousPlanningCuration(), "Previous Planning Curation");
            }
            case TicketOrchestratorPreviousContext ctx -> {
                appendCuration(builder, ctx.previousDiscoveryCuration(), "Previous Discovery Curation");
                appendCuration(builder, ctx.previousPlanningCuration(), "Previous Planning Curation");
                appendCuration(builder, ctx.previousTicketCuration(), "Previous Ticket Curation");
            }
            case DiscoveryAgentPreviousContext ctx ->
                    appendSection(builder, "Previous Discovery Result", prettyPrint(ctx.previousDiscoveryResult()));
            case PlanningAgentPreviousContext ctx ->
                    appendSection(builder, "Previous Planning Result", prettyPrint(ctx.previousPlanningResult()));
            case TicketAgentPreviousContext ctx ->
                    appendSection(builder, "Previous Ticket Result", prettyPrint(ctx.previousTicketResult()));
            case DiscoveryCollectorPreviousContext ctx -> {
                appendSection(builder, "Previous Discovery Result", prettyPrint(ctx.previousDiscoveryResult()));
                appendCuration(builder, ctx.previousDiscoveryCuration(), "Previous Discovery Curation");
            }
            case PlanningCollectorPreviousContext ctx -> {
                appendSection(builder, "Previous Planning Result", prettyPrint(ctx.previousPlanningResult()));
                appendCuration(builder, ctx.previousPlanningCuration(), "Previous Planning Curation");
            }
            case TicketCollectorPreviousContext ctx -> {
                appendSection(builder, "Previous Ticket Result", prettyPrint(ctx.previousTicketResult()));
                appendCuration(builder, ctx.previousTicketCuration(), "Previous Ticket Curation");
            }
            case ReviewPreviousContext ctx ->
                    appendSection(builder, "Previous Review Evaluation", prettyPrint(ctx.previousReviewEvaluation()));
            case MergerPreviousContext ctx ->
                    appendSection(builder, "Previous Merger Validation", prettyPrint(ctx.previousMergerValidation()));
        }

        return builder.toString().trim();
    }

    private static void appendCuration(StringBuilder builder, UpstreamContext upstreamContext, String label) {
        if (upstreamContext == null) {
            return;
        }
        String value = switch (upstreamContext) {
            case UpstreamContext.DiscoveryCollectorContext discovery -> discovery.prettyPrint();
            case UpstreamContext.PlanningCollectorContext planning -> planning.prettyPrint();
            case UpstreamContext.TicketCollectorContext ticket -> ticket.prettyPrint();
            default -> throw new RuntimeException("Found undesired!");
        };
        appendSection(builder, label, value);
    }

    private static void appendLine(StringBuilder builder, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        builder.append(label).append(": ").append(value.trim()).append("\n");
    }

    private static void appendSection(StringBuilder builder, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        builder.append(label).append(":\n").append(value.trim()).append("\n");
    }

    private static String prettyPrint(AgentContext context) {
        return context == null
                ? ""
                : context.prettyPrint();
    }

    private static String valueOf(Object value) {
        return value == null ? "" : value.toString();
    }
    private static String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : "";
    }

    @Builder
    record OrchestratorPreviousContext(
            ContextId previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            UpstreamContext.DiscoveryCollectorContext previousDiscoveryCuration,
            UpstreamContext.PlanningCollectorContext previousPlanningCuration,
            UpstreamContext.TicketCollectorContext previousTicketCuration
    ) implements PreviousContext {
    }

    @Builder
    record OrchestratorCollectorPreviousContext(
            ContextId previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            UpstreamContext.DiscoveryCollectorContext previousDiscoveryCuration,
            UpstreamContext.PlanningCollectorContext previousPlanningCuration,
            UpstreamContext.TicketCollectorContext previousTicketCuration
    ) implements PreviousContext {
    }

    @Builder
    record DiscoveryOrchestratorPreviousContext(
            ContextId previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            UpstreamContext.DiscoveryCollectorContext previousDiscoveryCuration,
            UpstreamContext.PlanningCollectorContext previousPlanningCuration,
            UpstreamContext.TicketCollectorContext previousTicketCuration
    ) implements PreviousContext {
    }

    @Builder
    record PlanningOrchestratorPreviousContext(
            ContextId previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            UpstreamContext.DiscoveryCollectorContext previousDiscoveryCuration,
            UpstreamContext.PlanningCollectorContext previousPlanningCuration
    ) implements PreviousContext {
    }

    @Builder
    record TicketOrchestratorPreviousContext(
            ContextId previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            UpstreamContext.DiscoveryCollectorContext previousDiscoveryCuration,
            UpstreamContext.PlanningCollectorContext previousPlanningCuration,
            UpstreamContext.TicketCollectorContext previousTicketCuration
    ) implements PreviousContext {
    }

    @Builder
    record DiscoveryAgentPreviousContext(
            ContextId previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            DiscoveryReport previousDiscoveryResult
    ) implements PreviousContext {
    }

    @Builder
    record PlanningAgentPreviousContext(
            ContextId previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            AgentModels.PlanningAgentResult previousPlanningResult
    ) implements PreviousContext {
    }

    @Builder
    record TicketAgentPreviousContext(
            ContextId previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            AgentModels.TicketAgentResult previousTicketResult
    ) implements PreviousContext {
    }

    @Builder
    record DiscoveryCollectorPreviousContext(
            ContextId previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            AgentModels.DiscoveryCollectorResult previousDiscoveryResult,
            UpstreamContext.DiscoveryCollectorContext previousDiscoveryCuration
    ) implements PreviousContext {
    }

    @Builder
    record PlanningCollectorPreviousContext(
            ContextId previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            AgentModels.PlanningCollectorResult previousPlanningResult,
            UpstreamContext.PlanningCollectorContext previousPlanningCuration
    ) implements PreviousContext {
    }

    @Builder
    record TicketCollectorPreviousContext(
            ContextId previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            AgentModels.TicketCollectorResult previousTicketResult,
            UpstreamContext.TicketCollectorContext previousTicketCuration
    ) implements PreviousContext {
    }

    @Builder
    record ReviewPreviousContext(
            ContextId previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            AgentModels.ReviewAgentResult previousReviewEvaluation
    ) implements PreviousContext {
    }

    @Builder
    record MergerPreviousContext(
            ContextId previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            AgentModels.MergerAgentResult previousMergerValidation
    ) implements PreviousContext {
    }
}
