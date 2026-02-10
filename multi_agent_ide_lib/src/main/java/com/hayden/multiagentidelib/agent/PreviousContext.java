package com.hayden.multiagentidelib.agent;

import com.hayden.multiagentidelib.template.DiscoveryReport;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.Builder;
import lombok.With;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    ArtifactKey previousContextId();

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
        appendLine(builder, "Context Id", keyValue(contextId()));
        appendLine(builder, "Attempt", attemptNumber() > 0 ? String.valueOf(attemptNumber()) : null);
        appendLine(builder, "Previous Context Id", keyValue(previousContextId()));
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
            default -> throw new RuntimeException("Found undesired upstream context - %s!"
                    .formatted(upstreamContext.getClass().getSimpleName()));
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

    private static String keyValue(ArtifactKey value) {
        return value == null ? "" : value.value();
    }
    private static String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : "";
    }

    private static <T extends Artifact.AgentModel> T firstChildOfType(
            List<Artifact.AgentModel> children,
            Class<T> type,
            T fallback
    ) {
        if (children == null || children.isEmpty()) {
            return fallback;
        }
        for (Artifact.AgentModel child : children) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
        }
        return fallback;
    }

    @Builder @With
    record OrchestratorPreviousContext(
            ArtifactKey contextId,
            ArtifactKey previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            UpstreamContext.DiscoveryCollectorContext previousDiscoveryCuration,
            UpstreamContext.PlanningCollectorContext previousPlanningCuration,
            UpstreamContext.TicketCollectorContext previousTicketCuration
    ) implements PreviousContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String payload = serializedOutput == null ? "" : serializedOutput;
            return hashContext.hash(payload);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (previousDiscoveryCuration != null) {
                children.add(previousDiscoveryCuration);
            }
            if (previousPlanningCuration != null) {
                children.add(previousPlanningCuration);
            }
            if (previousTicketCuration != null) {
                children.add(previousTicketCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            UpstreamContext.DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, UpstreamContext.DiscoveryCollectorContext.class, previousDiscoveryCuration);
            UpstreamContext.PlanningCollectorContext updatedPlanning =
                    firstChildOfType(children, UpstreamContext.PlanningCollectorContext.class, previousPlanningCuration);
            UpstreamContext.TicketCollectorContext updatedTicket =
                    firstChildOfType(children, UpstreamContext.TicketCollectorContext.class, previousTicketCuration);
            return (T) new OrchestratorPreviousContext(
                    contextId,
                    previousContextId,
                    serializedOutput,
                    errorMessage,
                    errorStackTrace,
                    attemptNumber,
                    previousAttemptAt,
                    updatedDiscovery,
                    updatedPlanning,
                    updatedTicket
            );
        }

        @Override
        public ArtifactKey key() {
            return contextId;
        }
    }

    @Builder @With
    record OrchestratorCollectorPreviousContext(
            ArtifactKey contextId,
            ArtifactKey previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            UpstreamContext.DiscoveryCollectorContext previousDiscoveryCuration,
            UpstreamContext.PlanningCollectorContext previousPlanningCuration,
            UpstreamContext.TicketCollectorContext previousTicketCuration
    ) implements PreviousContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String payload = serializedOutput == null ? "" : serializedOutput;
            return hashContext.hash(payload);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (previousDiscoveryCuration != null) {
                children.add(previousDiscoveryCuration);
            }
            if (previousPlanningCuration != null) {
                children.add(previousPlanningCuration);
            }
            if (previousTicketCuration != null) {
                children.add(previousTicketCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            UpstreamContext.DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, UpstreamContext.DiscoveryCollectorContext.class, previousDiscoveryCuration);
            UpstreamContext.PlanningCollectorContext updatedPlanning =
                    firstChildOfType(children, UpstreamContext.PlanningCollectorContext.class, previousPlanningCuration);
            UpstreamContext.TicketCollectorContext updatedTicket =
                    firstChildOfType(children, UpstreamContext.TicketCollectorContext.class, previousTicketCuration);
            return (T) new OrchestratorCollectorPreviousContext(
                    contextId,
                    previousContextId,
                    serializedOutput,
                    errorMessage,
                    errorStackTrace,
                    attemptNumber,
                    previousAttemptAt,
                    updatedDiscovery,
                    updatedPlanning,
                    updatedTicket
            );
        }
    }

    @Builder @With
    record DiscoveryOrchestratorPreviousContext(
            ArtifactKey contextId,
            ArtifactKey previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            UpstreamContext.DiscoveryCollectorContext previousDiscoveryCuration,
            UpstreamContext.PlanningCollectorContext previousPlanningCuration,
            UpstreamContext.TicketCollectorContext previousTicketCuration
    ) implements PreviousContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String payload = serializedOutput == null ? "" : serializedOutput;
            return hashContext.hash(payload);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (previousDiscoveryCuration != null) {
                children.add(previousDiscoveryCuration);
            }
            if (previousPlanningCuration != null) {
                children.add(previousPlanningCuration);
            }
            if (previousTicketCuration != null) {
                children.add(previousTicketCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            UpstreamContext.DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, UpstreamContext.DiscoveryCollectorContext.class, previousDiscoveryCuration);
            UpstreamContext.PlanningCollectorContext updatedPlanning =
                    firstChildOfType(children, UpstreamContext.PlanningCollectorContext.class, previousPlanningCuration);
            UpstreamContext.TicketCollectorContext updatedTicket =
                    firstChildOfType(children, UpstreamContext.TicketCollectorContext.class, previousTicketCuration);
            return (T) new DiscoveryOrchestratorPreviousContext(
                    contextId,
                    previousContextId,
                    serializedOutput,
                    errorMessage,
                    errorStackTrace,
                    attemptNumber,
                    previousAttemptAt,
                    updatedDiscovery,
                    updatedPlanning,
                    updatedTicket
            );
        }
    }

    @Builder @With
    record PlanningOrchestratorPreviousContext(
            ArtifactKey contextId,
            ArtifactKey previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            UpstreamContext.DiscoveryCollectorContext previousDiscoveryCuration,
            UpstreamContext.PlanningCollectorContext previousPlanningCuration
    ) implements PreviousContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String payload = serializedOutput == null ? "" : serializedOutput;
            return hashContext.hash(payload);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (previousDiscoveryCuration != null) {
                children.add(previousDiscoveryCuration);
            }
            if (previousPlanningCuration != null) {
                children.add(previousPlanningCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            UpstreamContext.DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, UpstreamContext.DiscoveryCollectorContext.class, previousDiscoveryCuration);
            UpstreamContext.PlanningCollectorContext updatedPlanning =
                    firstChildOfType(children, UpstreamContext.PlanningCollectorContext.class, previousPlanningCuration);
            return (T) new PlanningOrchestratorPreviousContext(
                    contextId,
                    previousContextId,
                    serializedOutput,
                    errorMessage,
                    errorStackTrace,
                    attemptNumber,
                    previousAttemptAt,
                    updatedDiscovery,
                    updatedPlanning
            );
        }
    }

    @Builder @With
    record TicketOrchestratorPreviousContext(
            ArtifactKey contextId,
            ArtifactKey previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            UpstreamContext.DiscoveryCollectorContext previousDiscoveryCuration,
            UpstreamContext.PlanningCollectorContext previousPlanningCuration,
            UpstreamContext.TicketCollectorContext previousTicketCuration
    ) implements PreviousContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String payload = serializedOutput == null ? "" : serializedOutput;
            return hashContext.hash(payload);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (previousDiscoveryCuration != null) {
                children.add(previousDiscoveryCuration);
            }
            if (previousPlanningCuration != null) {
                children.add(previousPlanningCuration);
            }
            if (previousTicketCuration != null) {
                children.add(previousTicketCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            UpstreamContext.DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, UpstreamContext.DiscoveryCollectorContext.class, previousDiscoveryCuration);
            UpstreamContext.PlanningCollectorContext updatedPlanning =
                    firstChildOfType(children, UpstreamContext.PlanningCollectorContext.class, previousPlanningCuration);
            UpstreamContext.TicketCollectorContext updatedTicket =
                    firstChildOfType(children, UpstreamContext.TicketCollectorContext.class, previousTicketCuration);
            return (T) new TicketOrchestratorPreviousContext(
                    contextId,
                    previousContextId,
                    serializedOutput,
                    errorMessage,
                    errorStackTrace,
                    attemptNumber,
                    previousAttemptAt,
                    updatedDiscovery,
                    updatedPlanning,
                    updatedTicket
            );
        }
    }

    @Builder @With
    record DiscoveryAgentPreviousContext(
            ArtifactKey contextId,
            ArtifactKey previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            DiscoveryReport previousDiscoveryResult
    ) implements PreviousContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String payload = serializedOutput == null ? "" : serializedOutput;
            return hashContext.hash(payload);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (previousDiscoveryResult != null) {
                children.add(previousDiscoveryResult);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            DiscoveryReport updatedDiscovery =
                    firstChildOfType(children, DiscoveryReport.class, previousDiscoveryResult);
            return (T) new DiscoveryAgentPreviousContext(
                    contextId,
                    previousContextId,
                    serializedOutput,
                    errorMessage,
                    errorStackTrace,
                    attemptNumber,
                    previousAttemptAt,
                    updatedDiscovery
            );
        }
    }

    @Builder @With
    record PlanningAgentPreviousContext(
            ArtifactKey contextId,
            ArtifactKey previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            AgentModels.PlanningAgentResult previousPlanningResult
    ) implements PreviousContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String payload = serializedOutput == null ? "" : serializedOutput;
            return hashContext.hash(payload);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (previousPlanningResult != null) {
                children.add(previousPlanningResult);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            AgentModels.PlanningAgentResult updatedResult =
                    firstChildOfType(children, AgentModels.PlanningAgentResult.class, previousPlanningResult);
            return (T) new PlanningAgentPreviousContext(
                    contextId,
                    previousContextId,
                    serializedOutput,
                    errorMessage,
                    errorStackTrace,
                    attemptNumber,
                    previousAttemptAt,
                    updatedResult
            );
        }
    }

    @Builder @With
    record TicketAgentPreviousContext(
            ArtifactKey contextId,
            ArtifactKey previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            AgentModels.TicketAgentResult previousTicketResult
    ) implements PreviousContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String payload = serializedOutput == null ? "" : serializedOutput;
            return hashContext.hash(payload);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (previousTicketResult != null) {
                children.add(previousTicketResult);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            AgentModels.TicketAgentResult updatedResult =
                    firstChildOfType(children, AgentModels.TicketAgentResult.class, previousTicketResult);
            return (T) new TicketAgentPreviousContext(
                    contextId,
                    previousContextId,
                    serializedOutput,
                    errorMessage,
                    errorStackTrace,
                    attemptNumber,
                    previousAttemptAt,
                    updatedResult
            );
        }
    }

    @Builder @With
    record DiscoveryCollectorPreviousContext(
            ArtifactKey contextId,
            ArtifactKey previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            AgentModels.DiscoveryCollectorResult previousDiscoveryResult,
            UpstreamContext.DiscoveryCollectorContext previousDiscoveryCuration
    ) implements PreviousContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String payload = serializedOutput == null ? "" : serializedOutput;
            return hashContext.hash(payload);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (previousDiscoveryResult != null) {
                children.add(previousDiscoveryResult);
            }
            if (previousDiscoveryCuration != null) {
                children.add(previousDiscoveryCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            AgentModels.DiscoveryCollectorResult updatedResult =
                    firstChildOfType(children, AgentModels.DiscoveryCollectorResult.class, previousDiscoveryResult);
            UpstreamContext.DiscoveryCollectorContext updatedCuration =
                    firstChildOfType(children, UpstreamContext.DiscoveryCollectorContext.class, previousDiscoveryCuration);
            return (T) new DiscoveryCollectorPreviousContext(
                    contextId,
                    previousContextId,
                    serializedOutput,
                    errorMessage,
                    errorStackTrace,
                    attemptNumber,
                    previousAttemptAt,
                    updatedResult,
                    updatedCuration
            );
        }
    }

    @Builder @With
    record PlanningCollectorPreviousContext(
            ArtifactKey contextId,
            ArtifactKey previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            AgentModels.PlanningCollectorResult previousPlanningResult,
            UpstreamContext.PlanningCollectorContext previousPlanningCuration
    ) implements PreviousContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String payload = serializedOutput == null ? "" : serializedOutput;
            return hashContext.hash(payload);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (previousPlanningResult != null) {
                children.add(previousPlanningResult);
            }
            if (previousPlanningCuration != null) {
                children.add(previousPlanningCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            AgentModels.PlanningCollectorResult updatedResult =
                    firstChildOfType(children, AgentModels.PlanningCollectorResult.class, previousPlanningResult);
            UpstreamContext.PlanningCollectorContext updatedCuration =
                    firstChildOfType(children, UpstreamContext.PlanningCollectorContext.class, previousPlanningCuration);
            return (T) new PlanningCollectorPreviousContext(
                    contextId,
                    previousContextId,
                    serializedOutput,
                    errorMessage,
                    errorStackTrace,
                    attemptNumber,
                    previousAttemptAt,
                    updatedResult,
                    updatedCuration
            );
        }
    }

    @Builder @With
    record TicketCollectorPreviousContext(
            ArtifactKey contextId,
            ArtifactKey previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            AgentModels.TicketCollectorResult previousTicketResult,
            UpstreamContext.TicketCollectorContext previousTicketCuration
    ) implements PreviousContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String payload = serializedOutput == null ? "" : serializedOutput;
            return hashContext.hash(payload);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (previousTicketResult != null) {
                children.add(previousTicketResult);
            }
            if (previousTicketCuration != null) {
                children.add(previousTicketCuration);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            AgentModels.TicketCollectorResult updatedResult =
                    firstChildOfType(children, AgentModels.TicketCollectorResult.class, previousTicketResult);
            UpstreamContext.TicketCollectorContext updatedCuration =
                    firstChildOfType(children, UpstreamContext.TicketCollectorContext.class, previousTicketCuration);
            return (T) new TicketCollectorPreviousContext(
                    contextId,
                    previousContextId,
                    serializedOutput,
                    errorMessage,
                    errorStackTrace,
                    attemptNumber,
                    previousAttemptAt,
                    updatedResult,
                    updatedCuration
            );
        }
    }

    @Builder @With
    record ReviewPreviousContext(
            ArtifactKey contextId,
            ArtifactKey previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            AgentModels.ReviewAgentResult previousReviewEvaluation
    ) implements PreviousContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String payload = serializedOutput == null ? "" : serializedOutput;
            return hashContext.hash(payload);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (previousReviewEvaluation != null) {
                children.add(previousReviewEvaluation);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            AgentModels.ReviewAgentResult updatedResult =
                    firstChildOfType(children, AgentModels.ReviewAgentResult.class, previousReviewEvaluation);
            return (T) new ReviewPreviousContext(
                    contextId,
                    previousContextId,
                    serializedOutput,
                    errorMessage,
                    errorStackTrace,
                    attemptNumber,
                    previousAttemptAt,
                    updatedResult
            );
        }
    }

    @Builder @With
    record MergerPreviousContext(
            ArtifactKey contextId,
            ArtifactKey previousContextId,
            String serializedOutput,
            String errorMessage,
            String errorStackTrace,
            int attemptNumber,
            Instant previousAttemptAt,
            AgentModels.MergerAgentResult previousMergerValidation
    ) implements PreviousContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String payload = serializedOutput == null ? "" : serializedOutput;
            return hashContext.hash(payload);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (previousMergerValidation != null) {
                children.add(previousMergerValidation);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            AgentModels.MergerAgentResult updatedResult =
                    firstChildOfType(children, AgentModels.MergerAgentResult.class, previousMergerValidation);
            return (T) new MergerPreviousContext(
                    contextId,
                    previousContextId,
                    serializedOutput,
                    errorMessage,
                    errorStackTrace,
                    attemptNumber,
                    previousAttemptAt,
                    updatedResult
            );
        }
    }
}
