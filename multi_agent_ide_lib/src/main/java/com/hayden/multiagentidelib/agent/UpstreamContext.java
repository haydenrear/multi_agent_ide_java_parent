package com.hayden.multiagentidelib.agent;

import com.hayden.multiagentidelib.template.ConsolidationTemplate;
import com.hayden.multiagentidelib.template.PlanningTicket;

import java.util.List;

public sealed interface UpstreamContext extends AgentContext permits UpstreamContext.DiscoveryAgentUpstreamContext, UpstreamContext.DiscoveryCollectorContext, UpstreamContext.DiscoveryOrchestratorUpstreamContext, UpstreamContext.MergerUpstreamContext, UpstreamContext.OrchestratorCollectorUpstreamContext, UpstreamContext.OrchestratorUpstreamContext, UpstreamContext.PlanningAgentUpstreamContext, UpstreamContext.PlanningCollectorContext, UpstreamContext.PlanningOrchestratorUpstreamContext, UpstreamContext.ReviewUpstreamContext, UpstreamContext.TicketAgentUpstreamContext, UpstreamContext.TicketCollectorContext, UpstreamContext.TicketOrchestratorUpstreamContext {

    ContextId contextId();

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

    record OrchestratorUpstreamContext(
            ContextId contextId,
            String workflowGoal,
            String phase
    ) implements UpstreamContext {
    }

    record OrchestratorCollectorUpstreamContext(
            ContextId contextId,
            AgentModels.DiscoveryCollectorResult discoveryCollectorResult,
            AgentModels.PlanningCollectorResult planningCollectorResult,
            AgentModels.TicketCollectorResult ticketCollectorResult
    ) implements UpstreamContext {
    }

    record DiscoveryOrchestratorUpstreamContext(
            ContextId contextId,
            String orchestratorGoal,
            String phase
    ) implements UpstreamContext {
    }

    record DiscoveryAgentUpstreamContext(
            ContextId contextId,
            String orchestratorGoal,
            String subdomainAssignment
    ) implements UpstreamContext {
    }

    record PlanningOrchestratorUpstreamContext(
            ContextId contextId,
            DiscoveryCollectorContext discoveryContext
    ) implements UpstreamContext {
    }

    record PlanningAgentUpstreamContext(
            ContextId contextId,
            DiscoveryCollectorContext discoveryContext
    ) implements UpstreamContext {
    }

    record TicketOrchestratorUpstreamContext(
            ContextId contextId,
            DiscoveryCollectorContext discoveryContext,
            PlanningCollectorContext planningContext
    ) implements UpstreamContext {
    }

    record TicketAgentUpstreamContext(
            ContextId contextId,
            DiscoveryCollectorContext discoveryContext,
            PlanningCollectorContext planningContext,
            PlanningTicket assignedTicket
    ) implements UpstreamContext {
    }

    record ReviewUpstreamContext(
            ContextId contextId,
            List<ContextId> reviewedContextIds,
            String reviewScope
    ) implements UpstreamContext {
    }

    record MergerUpstreamContext(
            ContextId contextId,
            List<ContextId> mergeContextIds,
            String mergeScope
    ) implements UpstreamContext {
    }

    record DiscoveryCollectorContext(
            ContextId contextId,
            ContextId sourceResultId,
            AgentModels.DiscoveryCuration curation,
            String selectionRationale
    ) implements UpstreamContext, ConsolidationTemplate.Curation {
        
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
            ContextId contextId,
            ContextId sourceResultId,
            AgentModels.PlanningCuration curation,
            String selectionRationale
    ) implements UpstreamContext, ConsolidationTemplate.Curation {
        
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
            ContextId contextId,
            ContextId sourceResultId,
            AgentModels.TicketCuration curation,
            String selectionRationale
    ) implements UpstreamContext, ConsolidationTemplate.Curation {
        
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
