package com.hayden.multiagentidelib.agent;

import com.hayden.multiagentidelib.template.ConsolidationTemplate;
import com.hayden.multiagentidelib.template.PlanningTicket;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.Builder;
import org.immutables.encode.Encoding;

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

    ArtifactKey artifactKey();

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
        return toString();
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

    record OrchestratorUpstreamContext(
            ArtifactKey artifactKey,
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
    }

    record OrchestratorCollectorUpstreamContext(
            ArtifactKey artifactKey,
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
                    artifactKey,
                    updatedDiscovery,
                    updatedPlanning,
                    updatedTicket
            );
        }
    }

    record DiscoveryOrchestratorUpstreamContext(
            ArtifactKey artifactKey,
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
    }

    record DiscoveryAgentUpstreamContext(
            ArtifactKey artifactKey,
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
    }

    record PlanningOrchestratorUpstreamContext(
            ArtifactKey artifactKey,
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
                    artifactKey,
                    updatedDiscovery
            );
        }
    }

    record PlanningAgentUpstreamContext(
            ArtifactKey artifactKey,
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
                    artifactKey,
                    updatedDiscovery
            );
        }
    }

    record TicketOrchestratorUpstreamContext(
            ArtifactKey artifactKey,
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
                    artifactKey,
                    updatedDiscovery,
                    updatedPlanning
            );
        }
    }

    record TicketAgentUpstreamContext(
            ArtifactKey artifactKey,
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
                    artifactKey,
                    updatedDiscovery,
                    updatedPlanning,
                    updatedTicket
            );
        }
    }

    record ReviewUpstreamContext(
            ArtifactKey artifactKey,
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
                    artifactKey,
                    updatedContexts,
                    reviewScope
            );
        }
    }

    record MergerUpstreamContext(
            ArtifactKey artifactKey,
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
                    artifactKey,
                    updatedContexts,
                    mergeScope
            );
        }
    }

    @Builder
    record DiscoveryCollectorContext(
            ArtifactKey artifactKey,
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
                    artifactKey,
                    updatedCuration,
                    selectionRationale
            );
        }
        
        @Override
        public String prettyPrint() {
            if (curation == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            if (curation.consolidationSummary() != null && curation.consolidationSummary().consolidatedOutput() != null) {
                sb.append(curation.consolidationSummary().consolidatedOutput()).append("\n");
            }
            if (curation.recommendations() != null) {
                for (var rec : curation.recommendations()) {
                    sb.append("- ").append(rec.title()).append(": ").append(rec.description()).append("\n");
                }
            }
            return sb.toString().trim();
        }
    }

    record PlanningCollectorContext(
            ArtifactKey artifactKey,
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
                    artifactKey,
                    updatedCuration,
                    selectionRationale
            );
        }
        
        @Override
        public String prettyPrint() {
            if (curation == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            if (curation.consolidationSummary() != null && curation.consolidationSummary().consolidatedOutput() != null) {
                sb.append(curation.consolidationSummary().consolidatedOutput()).append("\n");
            }
            if (curation.finalizedTickets() != null) {
                sb.append("Tickets:\n");
                for (var ticket : curation.finalizedTickets()) {
                    sb.append("- ").append(ticket.ticketId()).append(": ").append(ticket.title()).append("\n");
                    if (ticket.description() != null) {
                        sb.append("  ").append(ticket.description()).append("\n");
                    }
                }
            }
            return sb.toString().trim();
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

    record TicketCollectorContext(
            ArtifactKey artifactKey,
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
                    artifactKey,
                    updatedCuration,
                    selectionRationale
            );
        }
        
        @Override
        public String prettyPrint() {
            if (curation == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            if (curation.completionStatus() != null) {
                sb.append("Status: ").append(curation.completionStatus()).append("\n");
            }
            if (curation.consolidationSummary() != null && curation.consolidationSummary().consolidatedOutput() != null) {
                sb.append(curation.consolidationSummary().consolidatedOutput()).append("\n");
            }
            if (curation.followUps() != null && !curation.followUps().isEmpty()) {
                sb.append("Follow-ups:\n");
                for (var followUp : curation.followUps()) {
                    sb.append("- ").append(followUp).append("\n");
                }
            }
            return sb.toString().trim();
        }
    }
}
