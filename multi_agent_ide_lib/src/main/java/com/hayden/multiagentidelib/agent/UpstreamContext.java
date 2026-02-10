package com.hayden.multiagentidelib.agent;

import com.hayden.multiagentidelib.template.ConsolidationTemplate;
import com.hayden.multiagentidelib.template.PlanningTicket;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.Builder;
import lombok.With;

import java.util.ArrayList;
import java.util.List;

public sealed interface UpstreamContext extends AgentContext
        permits
            UpstreamContext.DiscoveryAgentUpstreamContext,
            UpstreamContext.DiscoveryCollectorContext,
            UpstreamContext.DiscoveryOrchestratorUpstreamContext,
            UpstreamContext.MergerUpstreamContext,
            UpstreamContext.OrchestratorCollectorUpstreamContext,
            UpstreamContext.OrchestratorUpstreamContext,
            UpstreamContext.PlanningAgentUpstreamContext,
            UpstreamContext.PlanningCollectorContext,
            UpstreamContext.PlanningOrchestratorUpstreamContext,
            UpstreamContext.ReviewUpstreamContext,
            UpstreamContext.TicketAgentUpstreamContext,
            UpstreamContext.TicketCollectorContext,
            UpstreamContext.TicketOrchestratorUpstreamContext {

    ArtifactKey contextId();

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
        return "";
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

    private static <T extends Artifact.AgentModel> List<T> childrenOfType(
            List<Artifact.AgentModel> children,
            Class<T> type,
            List<T> fallback
    ) {
        if (children == null || children.isEmpty()) {
            return fallback;
        }
        List<T> results = new ArrayList<>();
        for (Artifact.AgentModel child : children) {
            if (type.isInstance(child)) {
                results.add(type.cast(child));
            }
        }
        return results.isEmpty() ? fallback : List.copyOf(results);
    }

    private static void appendKey(StringBuilder builder, String label, ArtifactKey key) {
        builder.append(label).append(": ");
        if (key == null || key.value() == null || key.value().isBlank()) {
            builder.append("(none)\n");
            return;
        }
        builder.append(key.value()).append("\n");
    }

    private static void appendLine(StringBuilder builder, String label, String value) {
        builder.append(label).append(": ");
        if (value == null) {
            builder.append("(none)\n");
            return;
        }
        if (value.isBlank()) {
            builder.append("(empty)\n");
            return;
        }
        builder.append(value.trim()).append("\n");
    }

    private static void appendSection(StringBuilder builder, String label, String value) {
        builder.append(label).append(":\n");
        if (value == null) {
            builder.append("\t(none)\n");
            return;
        }
        if (value.isBlank()) {
            builder.append("\t(empty)\n");
            return;
        }
        builder.append("\t").append(value.trim().replace("\n", "\n\t")).append("\n");
    }

    private static void appendContext(StringBuilder builder, String label, AgentContext context) {
        builder.append(label).append(":\n");
        if (context == null) {
            builder.append("\t(none)\n");
            return;
        }
        String rendered = context.prettyPrint();
        if (rendered == null || rendered.isBlank()) {
            builder.append("\t(empty)\n");
            return;
        }
        builder.append("\t").append(rendered.trim().replace("\n", "\n\t")).append("\n");
    }

    private static void appendStringList(StringBuilder builder, String label, List<String> values, String indent) {
        builder.append(indent).append(label).append(":\n");
        if (values == null || values.isEmpty()) {
            builder.append(indent).append("\t(none)\n");
            return;
        }
        for (String value : values) {
            if (value == null) {
                builder.append(indent).append("\t- (none)\n");
                continue;
            }
            if (value.isBlank()) {
                builder.append(indent).append("\t- (empty)\n");
                continue;
            }
            builder.append(indent).append("\t- ").append(value.trim()).append("\n");
        }
    }

    private static void appendStringMap(StringBuilder builder, String label, java.util.Map<String, String> values, String indent) {
        builder.append(indent).append(label).append(":\n");
        if (values == null || values.isEmpty()) {
            builder.append(indent).append("\t(none)\n");
            return;
        }
        for (java.util.Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey() == null || entry.getKey().isBlank() ? "(blank-key)" : entry.getKey().trim();
            String value = entry.getValue();
            if (value == null) {
                builder.append(indent).append("\t- ").append(key).append(": (none)\n");
                continue;
            }
            if (value.isBlank()) {
                builder.append(indent).append("\t- ").append(key).append(": (empty)\n");
                continue;
            }
            builder.append(indent).append("\t- ").append(key).append(": ").append(value.trim()).append("\n");
        }
    }

    private static void appendTicket(StringBuilder builder, String label, PlanningTicket ticket) {
        builder.append(label).append(":\n");
        if (ticket == null) {
            builder.append("\t(none)\n");
            return;
        }
        appendKey(builder, "\tContext Id", ticket.contextId());
        appendLine(builder, "\tSchema Version", ticket.schemaVersion());
        appendKey(builder, "\tResult Id", ticket.resultId());
        appendLine(builder, "\tTicket Id", ticket.ticketId());
        appendLine(builder, "\tTitle", ticket.title());
        appendSection(builder, "\tDescription", ticket.description());
        appendStringList(builder, "Dependencies", ticket.dependencies(), "\t");
        appendStringList(builder, "Acceptance Criteria", ticket.acceptanceCriteria(), "\t");
        appendLine(builder, "\tEffort Estimate", ticket.effortEstimate());
        appendLine(builder, "\tPriority", String.valueOf(ticket.priority()));
        builder.append("\tTasks:\n");
        if (ticket.tasks() == null || ticket.tasks().isEmpty()) {
            builder.append("\t\t(none)\n");
        } else {
            for (PlanningTicket.TicketTask task : ticket.tasks()) {
                if (task == null) {
                    builder.append("\t\t- (none)\n");
                    continue;
                }
                builder.append("\t\t- id=").append(task.taskId())
                        .append(", desc=").append(task.description())
                        .append(", hours=").append(task.estimatedHours())
                        .append("\n");
                appendStringList(builder, "Related Files", task.relatedFiles(), "\t\t");
            }
        }
        builder.append("\tDiscovery Links:\n");
        if (ticket.discoveryLinks() == null || ticket.discoveryLinks().isEmpty()) {
            builder.append("\t\t(none)\n");
        } else {
            for (PlanningTicket.DiscoveryLink link : ticket.discoveryLinks()) {
                if (link == null) {
                    builder.append("\t\t- (none)\n");
                    continue;
                }
                builder.append("\t\t- discoveryResultId=")
                        .append(link.discoveryResultId() == null ? "(none)" : link.discoveryResultId().value())
                        .append(", referenceId=").append(link.referenceId())
                        .append(", rationale=").append(link.linkRationale())
                        .append("\n");
            }
        }
        builder.append("\tMemory References:\n");
        if (ticket.memoryReferences() == null || ticket.memoryReferences().isEmpty()) {
            builder.append("\t\t(none)\n");
        } else {
            for (var memoryReference : ticket.memoryReferences()) {
                builder.append("\t\t- ");
                if (memoryReference == null) {
                    builder.append("(none)\n");
                    continue;
                }
                builder.append("referenceId=").append(memoryReference.referenceId())
                        .append(", type=").append(memoryReference.memoryType())
                        .append(", summary=").append(memoryReference.summary())
                        .append("\n");
                appendStringMap(builder, "Metadata", memoryReference.metadata(), "\t\t");
            }
        }
    }

    @With
    record OrchestratorUpstreamContext(
            ArtifactKey contextId,
            String workflowGoal,
            String phase
    ) implements UpstreamContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String goal = workflowGoal == null ? "" : workflowGoal;
            String phaseValue = phase == null ? "" : phase;
            return hashContext.hash(goal + "|" + phaseValue);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            return List.of();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Orchestrator Upstream Context\n");
            appendKey(builder, "Context Id", contextId);
            appendLine(builder, "Workflow Goal", workflowGoal);
            appendLine(builder, "Phase", phase);
            return builder.toString().trim();
        }
    }

    @With
    record OrchestratorCollectorUpstreamContext(
            ArtifactKey contextId,
            AgentModels.DiscoveryCollectorResult discoveryCollectorResult,
            AgentModels.PlanningCollectorResult planningCollectorResult,
            AgentModels.TicketCollectorResult ticketCollectorResult
    ) implements UpstreamContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            StringBuilder builder = new StringBuilder();
            if (discoveryCollectorResult != null) {
                builder.append(discoveryCollectorResult.computeHash(hashContext));
            }
            builder.append("|");
            if (planningCollectorResult != null) {
                builder.append(planningCollectorResult.computeHash(hashContext));
            }
            builder.append("|");
            if (ticketCollectorResult != null) {
                builder.append(ticketCollectorResult.computeHash(hashContext));
            }
            return hashContext.hash(builder.toString());
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (discoveryCollectorResult != null) {
                children.add(discoveryCollectorResult);
            }
            if (planningCollectorResult != null) {
                children.add(planningCollectorResult);
            }
            if (ticketCollectorResult != null) {
                children.add(ticketCollectorResult);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            AgentModels.DiscoveryCollectorResult updatedDiscovery =
                    firstChildOfType(children, AgentModels.DiscoveryCollectorResult.class, discoveryCollectorResult);
            AgentModels.PlanningCollectorResult updatedPlanning =
                    firstChildOfType(children, AgentModels.PlanningCollectorResult.class, planningCollectorResult);
            AgentModels.TicketCollectorResult updatedTicket =
                    firstChildOfType(children, AgentModels.TicketCollectorResult.class, ticketCollectorResult);
            return (T) new OrchestratorCollectorUpstreamContext(
                    contextId,
                    updatedDiscovery,
                    updatedPlanning,
                    updatedTicket
            );
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Orchestrator Collector Upstream Context\n");
            appendKey(builder, "Context Id", contextId);
            appendContext(builder, "Discovery Collector Result", discoveryCollectorResult);
            appendContext(builder, "Planning Collector Result", planningCollectorResult);
            appendContext(builder, "Ticket Collector Result", ticketCollectorResult);
            return builder.toString().trim();
        }
    }

    @With
    record DiscoveryOrchestratorUpstreamContext(
            ArtifactKey contextId,
            String orchestratorGoal,
            String phase
    ) implements UpstreamContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String goal = orchestratorGoal == null ? "" : orchestratorGoal;
            String phaseValue = phase == null ? "" : phase;
            return hashContext.hash(goal + "|" + phaseValue);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            return List.of();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Discovery Orchestrator Upstream Context\n");
            appendKey(builder, "Context Id", contextId);
            appendLine(builder, "Orchestrator Goal", orchestratorGoal);
            appendLine(builder, "Phase", phase);
            return builder.toString().trim();
        }
    }

    @With
    record DiscoveryAgentUpstreamContext(
            ArtifactKey contextId,
            String orchestratorGoal,
            String subdomainAssignment
    ) implements UpstreamContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String goal = orchestratorGoal == null ? "" : orchestratorGoal;
            String assignment = subdomainAssignment == null ? "" : subdomainAssignment;
            return hashContext.hash(goal + "|" + assignment);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            return List.of();
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Discovery Agent Upstream Context\n");
            appendKey(builder, "Context Id", contextId);
            appendLine(builder, "Orchestrator Goal", orchestratorGoal);
            appendLine(builder, "Subdomain Assignment", subdomainAssignment);
            return builder.toString().trim();
        }
    }

    @With
    record PlanningOrchestratorUpstreamContext(
            ArtifactKey contextId,
            DiscoveryCollectorContext discoveryContext
    ) implements UpstreamContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String childHash = discoveryContext == null ? "" : discoveryContext.computeHash(hashContext);
            return hashContext.hash(childHash);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (discoveryContext != null) {
                children.add(discoveryContext);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, DiscoveryCollectorContext.class, discoveryContext);
            return (T) new PlanningOrchestratorUpstreamContext(
                    contextId,
                    updatedDiscovery
            );
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Planning Orchestrator Upstream Context\n");
            appendKey(builder, "Context Id", contextId);
            appendContext(builder, "Discovery Context", discoveryContext);
            return builder.toString().trim();
        }
    }

    @With
    record PlanningAgentUpstreamContext(
            ArtifactKey contextId,
            DiscoveryCollectorContext discoveryContext
    ) implements UpstreamContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String childHash = discoveryContext == null ? "" : discoveryContext.computeHash(hashContext);
            return hashContext.hash(childHash);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (discoveryContext != null) {
                children.add(discoveryContext);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, DiscoveryCollectorContext.class, discoveryContext);
            return (T) new PlanningAgentUpstreamContext(
                    contextId,
                    updatedDiscovery
            );
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Planning Agent Upstream Context\n");
            appendKey(builder, "Context Id", contextId);
            appendContext(builder, "Discovery Context", discoveryContext);
            return builder.toString().trim();
        }
    }

    @With
    record TicketOrchestratorUpstreamContext(
            ArtifactKey contextId,
            DiscoveryCollectorContext discoveryContext,
            PlanningCollectorContext planningContext
    ) implements UpstreamContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String discoveryHash = discoveryContext == null ? "" : discoveryContext.computeHash(hashContext);
            String planningHash = planningContext == null ? "" : planningContext.computeHash(hashContext);
            return hashContext.hash(discoveryHash + "|" + planningHash);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (discoveryContext != null) {
                children.add(discoveryContext);
            }
            if (planningContext != null) {
                children.add(planningContext);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, DiscoveryCollectorContext.class, discoveryContext);
            PlanningCollectorContext updatedPlanning =
                    firstChildOfType(children, PlanningCollectorContext.class, planningContext);
            return (T) new TicketOrchestratorUpstreamContext(
                    contextId,
                    updatedDiscovery,
                    updatedPlanning
            );
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Ticket Orchestrator Upstream Context\n");
            appendKey(builder, "Context Id", contextId);
            appendContext(builder, "Discovery Context", discoveryContext);
            appendContext(builder, "Planning Context", planningContext);
            return builder.toString().trim();
        }
    }

    @With
    record TicketAgentUpstreamContext(
            ArtifactKey contextId,
            DiscoveryCollectorContext discoveryContext,
            PlanningCollectorContext planningContext,
            PlanningTicket assignedTicket
    ) implements UpstreamContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String discoveryHash = discoveryContext == null ? "" : discoveryContext.computeHash(hashContext);
            String planningHash = planningContext == null ? "" : planningContext.computeHash(hashContext);
            String ticketHash = assignedTicket == null ? "" : assignedTicket.computeHash(hashContext);
            return hashContext.hash(discoveryHash + "|" + planningHash + "|" + ticketHash);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (discoveryContext != null) {
                children.add(discoveryContext);
            }
            if (planningContext != null) {
                children.add(planningContext);
            }
            if (assignedTicket != null) {
                children.add(assignedTicket);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            DiscoveryCollectorContext updatedDiscovery =
                    firstChildOfType(children, DiscoveryCollectorContext.class, discoveryContext);
            PlanningCollectorContext updatedPlanning =
                    firstChildOfType(children, PlanningCollectorContext.class, planningContext);
            PlanningTicket updatedTicket =
                    firstChildOfType(children, PlanningTicket.class, assignedTicket);
            return (T) new TicketAgentUpstreamContext(
                    contextId,
                    updatedDiscovery,
                    updatedPlanning,
                    updatedTicket
            );
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Ticket Agent Upstream Context\n");
            appendKey(builder, "Context Id", contextId);
            appendContext(builder, "Discovery Context", discoveryContext);
            appendContext(builder, "Planning Context", planningContext);
            appendTicket(builder, "Assigned Ticket", assignedTicket);
            return builder.toString().trim();
        }
    }

    @With
    record ReviewUpstreamContext(
            ArtifactKey contextId,
            List<UpstreamContext> reviewedContexts,
            String reviewScope
    ) implements UpstreamContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            StringBuilder builder = new StringBuilder();
            if (reviewScope != null) {
                builder.append(reviewScope);
            }
            builder.append("|");
            if (reviewedContexts != null) {
                for (UpstreamContext context : reviewedContexts) {
                    if (context == null) {
                        continue;
                    }
                    builder.append(context.computeHash(hashContext)).append("|");
                }
            }
            return hashContext.hash(builder.toString());
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (reviewedContexts != null) {
                for (UpstreamContext context : reviewedContexts) {
                    if (context != null) {
                        children.add(context);
                    }
                }
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            List<UpstreamContext> updatedContexts =
                    childrenOfType(children, UpstreamContext.class, reviewedContexts);
            return (T) new ReviewUpstreamContext(
                    contextId,
                    updatedContexts,
                    reviewScope
            );
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Review Upstream Context\n");
            appendKey(builder, "Context Id", contextId);
            appendLine(builder, "Review Scope", reviewScope);
            builder.append("Reviewed Contexts:\n");
            if (reviewedContexts == null || reviewedContexts.isEmpty()) {
                builder.append("\t(none)\n");
            } else {
                int index = 1;
                for (UpstreamContext context : reviewedContexts) {
                    builder.append("\t").append(index++).append(".\n");
                    appendContext(builder, "\tContext", context);
                }
            }
            return builder.toString().trim();
        }
    }

    @With
    record MergerUpstreamContext(
            ArtifactKey contextId,
            List<UpstreamContext> mergeContexts,
            String mergeScope
    ) implements UpstreamContext {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            StringBuilder builder = new StringBuilder();
            if (mergeScope != null) {
                builder.append(mergeScope);
            }
            builder.append("|");
            if (mergeContexts != null) {
                for (UpstreamContext context : mergeContexts) {
                    if (context == null) {
                        continue;
                    }
                    builder.append(context.computeHash(hashContext)).append("|");
                }
            }
            return hashContext.hash(builder.toString());
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (mergeContexts != null) {
                for (UpstreamContext context : mergeContexts) {
                    if (context != null) {
                        children.add(context);
                    }
                }
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            List<UpstreamContext> updatedContexts =
                    childrenOfType(children, UpstreamContext.class, mergeContexts);
            return (T) new MergerUpstreamContext(
                    contextId,
                    updatedContexts,
                    mergeScope
            );
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Merger Upstream Context\n");
            appendKey(builder, "Context Id", contextId);
            appendLine(builder, "Merge Scope", mergeScope);
            builder.append("Merge Contexts:\n");
            if (mergeContexts == null || mergeContexts.isEmpty()) {
                builder.append("\t(none)\n");
            } else {
                int index = 1;
                for (UpstreamContext context : mergeContexts) {
                    builder.append("\t").append(index++).append(".\n");
                    appendContext(builder, "\tContext", context);
                }
            }
            return builder.toString().trim();
        }
    }

    @Builder
    @With
    record DiscoveryCollectorContext(
            ArtifactKey contextId,
            AgentModels.DiscoveryCuration curation,
            String selectionRationale
    ) implements UpstreamContext, ConsolidationTemplate.Curation {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String rationale = selectionRationale == null ? "" : selectionRationale;
            String curationHash = curation == null ? "" : curation.computeHash(hashContext);
            return hashContext.hash(rationale + "|" + curationHash);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (curation != null) {
                children.add(curation);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            AgentModels.DiscoveryCuration updatedCuration =
                    firstChildOfType(children, AgentModels.DiscoveryCuration.class, curation);
            return (T) new DiscoveryCollectorContext(
                    contextId,
                    updatedCuration,
                    selectionRationale
            );
        }
        
        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Discovery Collector Context\n");
            appendKey(builder, "Context Id", contextId);
            appendLine(builder, "Selection Rationale", selectionRationale);
            appendContext(builder, "Curation", curation);
            return builder.toString().trim();
        }
    }

    @With
    record PlanningCollectorContext(
            ArtifactKey contextId,
            AgentModels.PlanningCuration curation,
            String selectionRationale
    ) implements UpstreamContext, ConsolidationTemplate.Curation {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String rationale = selectionRationale == null ? "" : selectionRationale;
            String curationHash = curation == null ? "" : curation.computeHash(hashContext);
            return hashContext.hash(rationale + "|" + curationHash);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (curation != null) {
                children.add(curation);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            AgentModels.PlanningCuration updatedCuration =
                    firstChildOfType(children, AgentModels.PlanningCuration.class, curation);
            return (T) new PlanningCollectorContext(
                    contextId,
                    updatedCuration,
                    selectionRationale
            );
        }
        
        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Planning Collector Context\n");
            appendKey(builder, "Context Id", contextId);
            appendLine(builder, "Selection Rationale", selectionRationale);
            appendContext(builder, "Curation", curation);
            return builder.toString().trim();
        }
        
        public String prettyPrintTickets() {
            if (curation == null || curation.finalizedTickets() == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (var ticket : curation.finalizedTickets()) {
                sb.append("## ").append(ticket.ticketId()).append(": ").append(ticket.title()).append("\n");
                if (ticket.description() != null) {
                    sb.append(ticket.description()).append("\n");
                }
                sb.append("\n");
            }
            return sb.toString().trim();
        }
    }

    @With
    record TicketCollectorContext(
            ArtifactKey contextId,
            AgentModels.TicketCuration curation,
            String selectionRationale
    ) implements UpstreamContext, ConsolidationTemplate.Curation {
        @Override
        public String computeHash(Artifact.HashContext hashContext) {
            String rationale = selectionRationale == null ? "" : selectionRationale;
            String curationHash = curation == null ? "" : curation.computeHash(hashContext);
            return hashContext.hash(rationale + "|" + curationHash);
        }

        @Override
        public List<Artifact.AgentModel> children() {
            List<Artifact.AgentModel> children = new ArrayList<>();
            if (curation != null) {
                children.add(curation);
            }
            return List.copyOf(children);
        }

        @Override
        public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> children) {
            AgentModels.TicketCuration updatedCuration =
                    firstChildOfType(children, AgentModels.TicketCuration.class, curation);
            return (T) new TicketCollectorContext(
                    contextId,
                    updatedCuration,
                    selectionRationale
            );
        }
        
        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder("Ticket Collector Context\n");
            appendKey(builder, "Context Id", contextId);
            appendLine(builder, "Selection Rationale", selectionRationale);
            appendContext(builder, "Curation", curation);
            return builder.toString().trim();
        }
    }
}
