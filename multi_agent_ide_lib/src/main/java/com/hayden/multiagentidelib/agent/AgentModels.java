package com.hayden.multiagentidelib.agent;

import com.embabel.agent.api.common.SomeOf;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.hayden.multiagentidelib.template.ConsolidationTemplate;
import com.hayden.multiagentidelib.template.DelegationTemplate;
import com.hayden.multiagentidelib.template.DiscoveryReport;
import com.hayden.multiagentidelib.template.MemoryReference;
import com.hayden.multiagentidelib.template.PlanningTicket;
import com.hayden.utilitymodule.acp.events.Events;
import lombok.Builder;

import java.util.List;
import java.util.Map;

public interface AgentModels {

    enum InteractionType {
        AGENT_MESSAGE,
        USER_MESSAGE,
        INTERRUPT_REQUEST,
        RESULT_HANDOFF
    }

    interface AgentResult extends AgentContext {
    }

    sealed interface AgentRequest extends AgentContext
            permits
//          Here for demonstration purposes - do not delete this comment or reformat this permits clause
//          Here is the happy path ordering for the requests
            OrchestratorRequest,
            DiscoveryOrchestratorRequest,
            DiscoveryAgentRequests,
            DiscoveryAgentRequest,
            DiscoveryAgentResults,
            DiscoveryCollectorRequest,
            PlanningOrchestratorRequest,
            PlanningAgentRequests,
            PlanningAgentRequest,
            PlanningAgentResults,
            PlanningCollectorRequest,
            TicketOrchestratorRequest,
            TicketAgentRequests,
            TicketAgentRequest,
            TicketAgentResults,
            TicketCollectorRequest,
            OrchestratorCollectorRequest,
//          gets routed to by agents to refine context or after
//            merger/review to reroute to requesting agent,
//            or when in invalid status or degenerate loop
            ContextOrchestratorRequest,
//          There exist various interrupt request types for each
//            of above agents associated with the requests, can reroute,
//            then get rerouted back
            InterruptRequest,
            MergerRequest,
            ReviewRequest
    {
        String prettyPrintInterruptContinuation();

        @Override
        default String prettyPrint() {
            return prettyPrintInterruptContinuation();
        }
    }

    non-sealed interface InterruptRequest extends AgentRequest {

        Events.InterruptType type();

        String reason();

        List<StructuredChoice> choices();

        List<ConfirmationItem> confirmationItems();

        String contextForDecision();

        default String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            builder.append("Interrupt received: ");
            builder.append(type());
            if (reason() != null && !reason().isBlank()) {
                builder.append(" with reason (").append(reason().trim()).append(")");
            }
            builder.append("\n");
            if (contextForDecision() != null && !contextForDecision().isBlank()) {
                builder.append("Context:\n");
                builder.append(contextForDecision().trim()).append("\n");
            }
            appendStructuredChoices(builder, choices());
            appendConfirmationItems(builder, confirmationItems());
            return builder.toString();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Interrupt request for orchestrator-level workflow steering.")
    record OrchestratorInterruptRequest(
            @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
            Events.InterruptType type,
            @JsonPropertyDescription("Natural language explanation of the uncertainty.")
            String reason,
            @JsonPropertyDescription("Structured decision choices for the controller.")
            List<StructuredChoice> choices,
            @JsonPropertyDescription("Yes/no confirmations required for continuation.")
            List<ConfirmationItem> confirmationItems,
            @JsonPropertyDescription("Concise context needed to make the decision.")
            String contextForDecision,
            @JsonPropertyDescription("Current workflow phase (DISCOVERY, PLANNING, TICKETS, COMPLETE).")
            String phase,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("High-level workflow context or state summary.")
            String workflowContext
    ) implements InterruptRequest {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Interrupt request for orchestrator collector phase decisions.")
    record OrchestratorCollectorInterruptRequest(
            @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
            Events.InterruptType type,
            @JsonPropertyDescription("Natural language explanation of the uncertainty.")
            String reason,
            @JsonPropertyDescription("Structured decision choices for the controller.")
            List<StructuredChoice> choices,
            @JsonPropertyDescription("Yes/no confirmations required for continuation.")
            List<ConfirmationItem> confirmationItems,
            @JsonPropertyDescription("Concise context needed to make the decision.")
            String contextForDecision,
            @JsonPropertyDescription("Proposed phase decision or outcome.")
            String phaseDecision,
            @JsonPropertyDescription("Summary of collector results driving the decision.")
            String collectorResults,
            @JsonPropertyDescription("Rationale for advancing or routing back.")
            String advancementRationale,
            @JsonPropertyDescription("Available phase options for routing.")
            List<String> phaseOptions
    ) implements InterruptRequest {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Interrupt request for discovery orchestration scope and partitioning.")
    record DiscoveryOrchestratorInterruptRequest(
            @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
            Events.InterruptType type,
            @JsonPropertyDescription("Natural language explanation of the uncertainty.")
            String reason,
            @JsonPropertyDescription("Structured decision choices for the controller.")
            List<StructuredChoice> choices,
            @JsonPropertyDescription("Yes/no confirmations required for continuation.")
            List<ConfirmationItem> confirmationItems,
            @JsonPropertyDescription("Concise context needed to make the decision.")
            String contextForDecision,
            @JsonPropertyDescription("Proposed partitioning of discovery subdomains.")
            List<String> subdomainPartitioning,
            @JsonPropertyDescription("Coverage goals for the discovery phase.")
            String coverageGoals,
            @JsonPropertyDescription("Discovery strategy or approach summary.")
            String discoveryStrategy,
            @JsonPropertyDescription("Scope boundaries for discovery.")
            String scope
    ) implements InterruptRequest {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Interrupt request for discovery agent code finding clarification.")
    record DiscoveryAgentInterruptRequest(
            @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
            Events.InterruptType type,
            @JsonPropertyDescription("Natural language explanation of the uncertainty.")
            String reason,
            @JsonPropertyDescription("Structured decision choices for the controller.")
            List<StructuredChoice> choices,
            @JsonPropertyDescription("Yes/no confirmations required for continuation.")
            List<ConfirmationItem> confirmationItems,
            @JsonPropertyDescription("Concise context needed to make the decision.")
            String contextForDecision,
            @JsonPropertyDescription("Summary of key code findings and questions.")
            String codeFindings,
            @JsonPropertyDescription("Relevant file paths or references.")
            List<String> fileReferences,
            @JsonPropertyDescription("Decisions or questions about boundaries/ownership.")
            List<String> boundaryDecisions,
            @JsonPropertyDescription("Observed architectural patterns or conventions.")
            List<String> patternObservations
    ) implements InterruptRequest {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Interrupt request for discovery collector consolidation decisions.")
    record DiscoveryCollectorInterruptRequest(
            @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
            Events.InterruptType type,
            @JsonPropertyDescription("Natural language explanation of the uncertainty.")
            String reason,
            @JsonPropertyDescription("Structured decision choices for the controller.")
            List<StructuredChoice> choices,
            @JsonPropertyDescription("Yes/no confirmations required for continuation.")
            List<ConfirmationItem> confirmationItems,
            @JsonPropertyDescription("Concise context needed to make the decision.")
            String contextForDecision,
            @JsonPropertyDescription("Consolidation decisions or tradeoffs under consideration.")
            String consolidationDecisions,
            @JsonPropertyDescription("Recommendations derived from discovery results.")
            List<String> recommendations,
            @JsonPropertyDescription("Key code references supporting the consolidation.")
            List<String> codeReferences,
            @JsonPropertyDescription("Subdomain boundaries used in the consolidation.")
            List<String> subdomainBoundaries
    ) implements InterruptRequest {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Interrupt request for discovery dispatch routing decisions.")
    record DiscoveryAgentDispatchInterruptRequest(
            @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
            Events.InterruptType type,
            @JsonPropertyDescription("Natural language explanation of the uncertainty.")
            String reason,
            @JsonPropertyDescription("Structured decision choices for the controller.")
            List<StructuredChoice> choices,
            @JsonPropertyDescription("Yes/no confirmations required for continuation.")
            List<ConfirmationItem> confirmationItems,
            @JsonPropertyDescription("Concise context needed to make the decision.")
            String contextForDecision,
            @JsonPropertyDescription("Summary of dispatch decisions under consideration.")
            String dispatchDecisions,
            @JsonPropertyDescription("Proposed agent assignments.")
            List<String> agentAssignments,
            @JsonPropertyDescription("Workload distribution notes or constraints.")
            List<String> workloadDistribution,
            @JsonPropertyDescription("Rationale for the routing decision.")
            String routingRationale
    ) implements InterruptRequest {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Interrupt request for planning orchestration and decomposition decisions.")
    record PlanningOrchestratorInterruptRequest(
            @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
            Events.InterruptType type,
            @JsonPropertyDescription("Natural language explanation of the uncertainty.")
            String reason,
            @JsonPropertyDescription("Structured decision choices for the controller.")
            List<StructuredChoice> choices,
            @JsonPropertyDescription("Yes/no confirmations required for continuation.")
            List<ConfirmationItem> confirmationItems,
            @JsonPropertyDescription("Concise context needed to make the decision.")
            String contextForDecision,
            @JsonPropertyDescription("Planned ticket decomposition summary.")
            String ticketDecomposition,
            @JsonPropertyDescription("Strategy for breaking down tickets.")
            String ticketBreakdownStrategy,
            @JsonPropertyDescription("How discovery context informed planning.")
            String discoveryContextUsage,
            @JsonPropertyDescription("Scope boundaries for planning.")
            String planningScope
    ) implements InterruptRequest {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Interrupt request for planning agent ticket design decisions.")
    record PlanningAgentInterruptRequest(
            @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
            Events.InterruptType type,
            @JsonPropertyDescription("Natural language explanation of the uncertainty.")
            String reason,
            @JsonPropertyDescription("Structured decision choices for the controller.")
            List<StructuredChoice> choices,
            @JsonPropertyDescription("Yes/no confirmations required for continuation.")
            List<ConfirmationItem> confirmationItems,
            @JsonPropertyDescription("Concise context needed to make the decision.")
            String contextForDecision,
            @JsonPropertyDescription("Ticket design summary or open questions.")
            String ticketDesign,
            @JsonPropertyDescription("Architecture decisions or tradeoffs being considered.")
            List<String> architectureDecisions,
            @JsonPropertyDescription("Proposed planning tickets.")
            List<PlanningTicket> proposedTickets,
            @JsonPropertyDescription("Discovery references informing planning.")
            List<MemoryReference> discoveryReferences
    ) implements InterruptRequest {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Interrupt request for planning collector consolidation decisions.")
    record PlanningCollectorInterruptRequest(
            @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
            Events.InterruptType type,
            @JsonPropertyDescription("Natural language explanation of the uncertainty.")
            String reason,
            @JsonPropertyDescription("Structured decision choices for the controller.")
            List<StructuredChoice> choices,
            @JsonPropertyDescription("Yes/no confirmations required for continuation.")
            List<ConfirmationItem> confirmationItems,
            @JsonPropertyDescription("Concise context needed to make the decision.")
            String contextForDecision,
            @JsonPropertyDescription("Summary of ticket consolidation decisions.")
            String ticketConsolidation,
            @JsonPropertyDescription("Dependency resolution strategy or conflicts.")
            String dependencyResolution,
            @JsonPropertyDescription("Consolidated tickets under consideration.")
            List<PlanningTicket> consolidatedTickets,
            @JsonPropertyDescription("Conflicting dependencies needing resolution.")
            List<String> dependencyConflicts
    ) implements InterruptRequest {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Interrupt request for planning dispatch routing decisions.")
    record PlanningAgentDispatchInterruptRequest(
            @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
            Events.InterruptType type,
            @JsonPropertyDescription("Natural language explanation of the uncertainty.")
            String reason,
            @JsonPropertyDescription("Structured decision choices for the controller.")
            List<StructuredChoice> choices,
            @JsonPropertyDescription("Yes/no confirmations required for continuation.")
            List<ConfirmationItem> confirmationItems,
            @JsonPropertyDescription("Concise context needed to make the decision.")
            String contextForDecision,
            @JsonPropertyDescription("Proposed agent assignments for tickets.")
            List<String> agentAssignments,
            @JsonPropertyDescription("Ticket distribution or batching notes.")
            List<String> ticketDistribution,
            @JsonPropertyDescription("Routing notes about discovery context usage.")
            String discoveryContextRouting,
            @JsonPropertyDescription("Rationale for the routing decision.")
            String routingRationale
    ) implements InterruptRequest {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Interrupt request for ticket orchestration and execution scope decisions.")
    record TicketOrchestratorInterruptRequest(
            @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
            Events.InterruptType type,
            @JsonPropertyDescription("Natural language explanation of the uncertainty.")
            String reason,
            @JsonPropertyDescription("Structured decision choices for the controller.")
            List<StructuredChoice> choices,
            @JsonPropertyDescription("Yes/no confirmations required for continuation.")
            List<ConfirmationItem> confirmationItems,
            @JsonPropertyDescription("Concise context needed to make the decision.")
            String contextForDecision,
            @JsonPropertyDescription("Implementation scope under consideration.")
            String implementationScope,
            @JsonPropertyDescription("How planning context informed execution.")
            String planningContextUsage,
            @JsonPropertyDescription("Execution strategy or sequencing notes.")
            String executionStrategy
    ) implements InterruptRequest {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Interrupt request for ticket agent implementation decisions.")
    record TicketAgentInterruptRequest(
            @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
            Events.InterruptType type,
            @JsonPropertyDescription("Natural language explanation of the uncertainty.")
            String reason,
            @JsonPropertyDescription("Structured decision choices for the controller.")
            List<StructuredChoice> choices,
            @JsonPropertyDescription("Yes/no confirmations required for continuation.")
            List<ConfirmationItem> confirmationItems,
            @JsonPropertyDescription("Concise context needed to make the decision.")
            String contextForDecision,
            @JsonPropertyDescription("Implementation approach under consideration.")
            String implementationApproach,
            @JsonPropertyDescription("Summary of expected file changes.")
            List<String> fileChanges,
            @JsonPropertyDescription("Specific files to modify or create.")
            List<String> filesToModify,
            @JsonPropertyDescription("Planned test strategies or verification steps.")
            List<String> testStrategies
    ) implements InterruptRequest {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Interrupt request for ticket collector completion decisions.")
    record TicketCollectorInterruptRequest(
            @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
            Events.InterruptType type,
            @JsonPropertyDescription("Natural language explanation of the uncertainty.")
            String reason,
            @JsonPropertyDescription("Structured decision choices for the controller.")
            List<StructuredChoice> choices,
            @JsonPropertyDescription("Yes/no confirmations required for continuation.")
            List<ConfirmationItem> confirmationItems,
            @JsonPropertyDescription("Concise context needed to make the decision.")
            String contextForDecision,
            @JsonPropertyDescription("Completion status summary or open questions.")
            String completionStatus,
            @JsonPropertyDescription("Follow-up items or remaining work.")
            List<String> followUps,
            @JsonPropertyDescription("Verification or test result summaries.")
            List<String> verificationResults
    ) implements InterruptRequest {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Interrupt request for ticket dispatch routing decisions.")
    record TicketAgentDispatchInterruptRequest(
            @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
            Events.InterruptType type,
            @JsonPropertyDescription("Natural language explanation of the uncertainty.")
            String reason,
            @JsonPropertyDescription("Structured decision choices for the controller.")
            List<StructuredChoice> choices,
            @JsonPropertyDescription("Yes/no confirmations required for continuation.")
            List<ConfirmationItem> confirmationItems,
            @JsonPropertyDescription("Concise context needed to make the decision.")
            String contextForDecision,
            @JsonPropertyDescription("Proposed agent assignments for tickets.")
            List<String> agentAssignments,
            @JsonPropertyDescription("Ticket distribution or batching notes.")
            List<String> ticketDistribution,
            @JsonPropertyDescription("Context routing notes for execution.")
            String contextRouting,
            @JsonPropertyDescription("Rationale for the routing decision.")
            String routingRationale
    ) implements InterruptRequest {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Interrupt request for review agent assessment decisions.")
    record ReviewInterruptRequest(
            @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
            Events.InterruptType type,
            @JsonPropertyDescription("Natural language explanation of the uncertainty.")
            String reason,
            @JsonPropertyDescription("Structured decision choices for the controller.")
            List<StructuredChoice> choices,
            @JsonPropertyDescription("Yes/no confirmations required for continuation.")
            List<ConfirmationItem> confirmationItems,
            @JsonPropertyDescription("Concise context needed to make the decision.")
            String contextForDecision,
            @JsonPropertyDescription("Review criteria under consideration.")
            String reviewCriteria,
            @JsonPropertyDescription("Assessment findings or concerns.")
            List<String> assessmentFindings,
            @JsonPropertyDescription("Approval recommendation or stance.")
            String approvalRecommendation
    ) implements InterruptRequest {
    }

    @JsonClassDescription("Interrupt request for merger conflict resolution decisions.")
    record MergerInterruptRequest(
            @JsonPropertyDescription("Interrupt type (HUMAN_REVIEW, AGENT_REVIEW, PAUSE, STOP).")
            Events.InterruptType type,
            @JsonPropertyDescription("Natural language explanation of the uncertainty.")
            String reason,
            @JsonPropertyDescription("Structured decision choices for the controller.")
            List<StructuredChoice> choices,
            @JsonPropertyDescription("Yes/no confirmations required for continuation.")
            List<ConfirmationItem> confirmationItems,
            @JsonPropertyDescription("Concise context needed to make the decision.")
            String contextForDecision,
            @JsonPropertyDescription("Conflicting file paths or identifiers.")
            List<String> conflictFiles,
            @JsonPropertyDescription("Resolution strategies being considered.")
            List<String> resolutionStrategies,
            @JsonPropertyDescription("Preferred merge approach.")
            String mergeApproach
    ) implements InterruptRequest {
        public MergerInterruptRequest(Events.InterruptType type, String reason) {
            this(type, reason, List.of(), List.of(), "", List.of(), List.of(), "");
        }
    }

    record ContextOrchestratorInterruptRequest(
            Events.InterruptType type,
            String reason,
            List<StructuredChoice> choices,
            List<ConfirmationItem> confirmationItems,
            String contextForDecision,
            String contextGatheringScope,
            List<String> sourcePriorities,
            String relevanceCriteria
    ) implements InterruptRequest {
    }

    record ContextAgentInterruptRequest(
            Events.InterruptType type,
            String reason,
            List<StructuredChoice> choices,
            List<ConfirmationItem> confirmationItems,
            String contextForDecision,
            String contextFindings,
            List<String> relevanceAssessments,
            List<String> sourceReferences
    ) implements InterruptRequest {
    }

    record ContextCollectorInterruptRequest(
            Events.InterruptType type,
            String reason,
            List<StructuredChoice> choices,
            List<ConfirmationItem> confirmationItems,
            String contextForDecision,
            String consolidatedContext,
            List<String> relevanceRankings,
            List<String> pruningDecisions
    ) implements InterruptRequest {
    }

    record ContextAgentDispatchInterruptRequest(
            Events.InterruptType type,
            String reason,
            List<StructuredChoice> choices,
            List<ConfirmationItem> confirmationItems,
            String contextForDecision,
            List<String> agentAssignments,
            List<String> sourceDistribution,
            String gatheringStrategy,
            String routingRationale
    ) implements InterruptRequest {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Multiple-choice decision with optional write-in.")
    record StructuredChoice(
            @JsonPropertyDescription("Stable identifier for this choice.")
            String choiceId,
            @JsonPropertyDescription("Decision question posed to the controller.")
            String question,
            @JsonPropertyDescription("Additional context for the decision.")
            String context,
            @JsonPropertyDescription("Options map with keys A/B/C/CUSTOM.")
            Map<String, String> options,
            @JsonPropertyDescription("Recommended option key if applicable.")
            String recommended
    ) {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Yes/no confirmation item for interrupt decisions.")
    record ConfirmationItem(
            @JsonPropertyDescription("Stable identifier for this confirmation.")
            String confirmationId,
            @JsonPropertyDescription("Confirmation statement to approve or deny.")
            String statement,
            @JsonPropertyDescription("Additional context for the confirmation.")
            String context,
            @JsonPropertyDescription("Default value when unspecified.")
            Boolean defaultValue,
            @JsonPropertyDescription("Impact if the confirmation is denied.")
            String impactIfNo
    ) {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Controller response resolving an interrupt request.")
    record InterruptResolution(
            @JsonPropertyDescription("Selected option per choiceId (A/B/C/CUSTOM).")
            Map<String, String> selectedChoices,
            @JsonPropertyDescription("Custom inputs keyed by choiceId when CUSTOM is selected.")
            Map<String, String> customInputs,
            @JsonPropertyDescription("Confirmation decisions keyed by confirmationId.")
            Map<String, Boolean> confirmations
    ) {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Collector branch decision used to route workflow phases.")
    record CollectorDecision(
            @JsonPropertyDescription("Decision type (ADVANCE_PHASE, ROUTE_BACK, STOP).")
            Events.CollectorDecisionType decisionType,
            @JsonPropertyDescription("Rationale for the decision.")
            String rationale,
            @JsonPropertyDescription("Requested phase when advancing.")
            String requestedPhase
    ) {
    }

    /**
     * Shared data models describing agent interactions, results, and interrupts.
     * These are intentionally transport-agnostic and can be serialized as needed.
     */
    @Builder(toBuilder=true)
    record AgentInteraction(
            InteractionType interactionType,
            String message
    ) {
    }

    /**
     * Defines how to kick off sub-agents for orchestrated work.
     */
    @Builder(toBuilder=true)
    record DelegationPlan(
            String summary,
            Map<String, String> subAgentGoals
    ) {
    }

    /**
     * Orchestrator-specific results.
     * Note: Results do NOT implement DelegationTemplate - delegation templates are the
     * *AgentRequests types that contain multiple sub-agent requests.
     */
    @Builder(toBuilder=true)
    record OrchestratorAgentResult(
            String schemaVersion,
            ContextId resultId,
            ContextId upstreamContextId,
            String output
    ) implements AgentResult {
        public OrchestratorAgentResult(String output) {
            this(null, null, null, output);
        }

        @Override
        public String prettyPrint() {
            return output == null ? "" : output.trim();
        }
    }

    @Builder(toBuilder=true)
    record DiscoveryOrchestratorResult(
            String schemaVersion,
            ContextId resultId,
            ContextId upstreamContextId,
            String output
    ) implements AgentResult {
        public DiscoveryOrchestratorResult(String output) {
            this(null, null, null, output);
        }

        @Override
        public String prettyPrint() {
            return output == null ? "" : output.trim();
        }
    }

    @Builder(toBuilder=true)
    record PlanningOrchestratorResult(
            String schemaVersion,
            ContextId resultId,
            ContextId upstreamContextId,
            String output
    ) implements AgentResult {
        public PlanningOrchestratorResult(String output) {
            this(null, null, null, output);
        }

        @Override
        public String prettyPrint() {
            return output == null ? "" : output.trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Result payload from the ticket orchestrator.")
    record TicketOrchestratorResult(
            @JsonPropertyDescription("Schema version for this result payload.")
            String schemaVersion,
            @JsonPropertyDescription("Unique context id for this result.")
            ContextId resultId,
            @JsonPropertyDescription("Upstream context id driving this result.")
            ContextId upstreamContextId,
            @JsonPropertyDescription("Human-readable summary output.")
            String output
    ) implements AgentResult {
        public TicketOrchestratorResult(String output) {
            this(null, null, null, output);
        }

        @Override
        public String prettyPrint() {
            return output == null ? "" : output.trim();
        }
    }

    /**
     * Results for non-orchestrator agents.
     */
//    TODO: this should be a code-map-like report result -
//        something cool about this
    @Builder(toBuilder=true)
    @JsonClassDescription("Result payload from a discovery agent.")
    record DiscoveryAgentResult(
            @JsonPropertyDescription("Schema version for this result payload.")
            String schemaVersion,
            @JsonPropertyDescription("Unique context id for this result.")
            ContextId resultId,
            @JsonPropertyDescription("Upstream context id driving this result.")
            ContextId upstreamContextId,
            @JsonPropertyDescription("Structured discovery report.")
            DiscoveryReport report,
            @JsonPropertyDescription("Human-readable summary output.")
            String output
    ) implements AgentResult {
        public DiscoveryAgentResult(String output) {
            this(null, null, null, null, output);
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (output != null && !output.isBlank()) {
                builder.append(output.trim());
            }
            if (report != null) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append(report.prettyPrint());
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Result payload from a planning agent.")
    record PlanningAgentResult(
            @JsonPropertyDescription("Schema version for this result payload.")
            String schemaVersion,
            @JsonPropertyDescription("Unique context id for this result.")
            ContextId resultId,
            @JsonPropertyDescription("Upstream context id driving this result.")
            ContextId upstreamContextId,
            @JsonPropertyDescription("Discovery result id referenced during planning.")
            ContextId discoveryResultId,
            @JsonPropertyDescription("Proposed planning tickets.")
            List<PlanningTicket> tickets,
            @JsonPropertyDescription("Human-readable summary output.")
            String output
    ) implements AgentResult {
        public PlanningAgentResult(String output) {
            this(null, null, null, null, null, output);
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (output != null && !output.isBlank()) {
                builder.append(output.trim());
            }
            if (tickets != null && !tickets.isEmpty()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Tickets:\n");
                for (PlanningTicket ticket : tickets) {
                    if (ticket == null) {
                        continue;
                    }
                    builder.append("- ").append(ticket.prettyPrint()).append("\n");
                }
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Result payload from a ticket execution agent.")
    record TicketAgentResult(
            @JsonPropertyDescription("Schema version for this result payload.")
            String schemaVersion,
            @JsonPropertyDescription("Unique context id for this result.")
            ContextId resultId,
            @JsonPropertyDescription("Upstream context id driving this result.")
            ContextId upstreamContextId,
            @JsonPropertyDescription("Ticket identifier being implemented.")
            String ticketId,
            @JsonPropertyDescription("Discovery result id referenced during execution.")
            ContextId discoveryResultId,
            @JsonPropertyDescription("Summary of implementation completed.")
            String implementationSummary,
            @JsonPropertyDescription("Files modified during execution.")
            List<String> filesModified,
            @JsonPropertyDescription("Test results or verification output.")
            List<String> testResults,
            @JsonPropertyDescription("Commit references or hashes.")
            List<String> commits,
            @JsonPropertyDescription("Verification status summary.")
            String verificationStatus,
            @JsonPropertyDescription("Full upstream context chain for traceability.")
            List<ContextId> upstreamContextChain,
            @JsonPropertyDescription("Memory references used during execution.")
            List<MemoryReference> memoryReferences,
            @JsonPropertyDescription("Human-readable summary output.")
            String output
    ) implements AgentResult {
        public TicketAgentResult(String output) {
            this(null, null, null, null, null, null, null, null, null, null, null, null, output);
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (ticketId != null && !ticketId.isBlank()) {
                builder.append("Ticket Id: ").append(ticketId.trim()).append("\n");
            }
            if (verificationStatus != null && !verificationStatus.isBlank()) {
                builder.append("Verification Status: ").append(verificationStatus.trim()).append("\n");
            }
            if (implementationSummary != null && !implementationSummary.isBlank()) {
                builder.append("Implementation Summary:\n").append(implementationSummary.trim()).append("\n");
            }
            appendList(builder, "Files Modified", filesModified);
            appendList(builder, "Test Results", testResults);
            appendList(builder, "Commits", commits);
            if (output != null && !output.isBlank()) {
                builder.append("Output:\n").append(output.trim()).append("\n");
            }
            return builder.toString().trim();
        }
    }


    @Builder(toBuilder=true)
    record ReviewAgentResult(
            String schemaVersion,
            ContextId resultId,
            ContextId upstreamContextId,
            String assessmentStatus,
            String feedback,
            List<String> suggestions,
            List<String> contentLinks,
            String output
    ) implements AgentResult {
        public ReviewAgentResult(String output) {
            this(null, null, null, null, output, null, null, output);
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (assessmentStatus != null && !assessmentStatus.isBlank()) {
                builder.append("Assessment Status: ").append(assessmentStatus.trim()).append("\n");
            }
            if (feedback != null && !feedback.isBlank()) {
                builder.append("Feedback:\n").append(feedback.trim()).append("\n");
            }
            if (suggestions != null && !suggestions.isEmpty()) {
                builder.append("Suggestions:\n");
                for (String suggestion : suggestions) {
                    if (suggestion == null || suggestion.isBlank()) {
                        continue;
                    }
                    builder.append("- ").append(suggestion.trim()).append("\n");
                }
            }
            if (contentLinks != null && !contentLinks.isEmpty()) {
                builder.append("Content Links:\n");
                for (String link : contentLinks) {
                    if (link == null || link.isBlank()) {
                        continue;
                    }
                    builder.append("- ").append(link.trim()).append("\n");
                }
            }
            if (output != null && !output.isBlank()) {
                builder.append("Output:\n").append(output.trim()).append("\n");
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Result payload from a merger agent.")
    record MergerAgentResult(
            @JsonPropertyDescription("Schema version for this result payload.")
            String schemaVersion,
            @JsonPropertyDescription("Unique context id for this result.")
            ContextId resultId,
            @JsonPropertyDescription("Upstream context id driving this result.")
            ContextId upstreamContextId,
            @JsonPropertyDescription("Acceptability assessment of the merge.")
            String acceptability,
            @JsonPropertyDescription("Conflict details identified during review.")
            List<String> conflictDetails,
            @JsonPropertyDescription("Guidance for resolving conflicts.")
            List<String> resolutionGuidance,
            @JsonPropertyDescription("Human-readable summary output.")
            String output
    ) implements AgentResult {
        public MergerAgentResult(String output) {
            this(null, null, null, null, null, null, output);
        }

        @Override
        public String prettyPrint(AgentSerializationCtx serializationCtx) {
            return switch (serializationCtx) {
                case AgentSerializationCtx.MergeSummarySerialization mergeSummarySerialization ->
                        output == null ? "" : output.trim();
                default -> AgentResult.super.prettyPrint(serializationCtx);
            };
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (acceptability != null && !acceptability.isBlank()) {
                builder.append("Acceptability: ").append(acceptability.trim()).append("\n");
            }
            if (conflictDetails != null && !conflictDetails.isEmpty()) {
                builder.append("Conflict Details:\n");
                for (String conflict : conflictDetails) {
                    if (conflict == null || conflict.isBlank()) {
                        continue;
                    }
                    builder.append("- ").append(conflict.trim()).append("\n");
                }
            }
            if (resolutionGuidance != null && !resolutionGuidance.isEmpty()) {
                builder.append("Resolution Guidance:\n");
                for (String guidance : resolutionGuidance) {
                    if (guidance == null || guidance.isBlank()) {
                        continue;
                    }
                    builder.append("- ").append(guidance.trim()).append("\n");
                }
            }
            if (output != null && !output.isBlank()) {
                builder.append("Output:\n").append(output.trim()).append("\n");
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    record SummaryAgentResult(
            String output
    ) implements AgentResult {
        @Override
        public String prettyPrint() {
            return output == null ? "" : output.trim();
        }
    }

    @Builder(toBuilder=true)
    record ContextAgentResult(
            String output
    ) implements AgentResult {
        @Override
        public String prettyPrint() {
            return output == null ? "" : output.trim();
        }
    }

    /**
     * Results for collector agents.
     */
//    TODO: this should be a code-map-like report returned
    @Builder(toBuilder=true)
    @JsonClassDescription("Consolidated discovery results and routing decision.")
    record DiscoveryCollectorResult(
            @JsonPropertyDescription("Schema version for this result payload.")
            String schemaVersion,
            @JsonPropertyDescription("Unique context id for this result.")
            ContextId resultId,
            @JsonPropertyDescription("Input references consolidated into this result.")
            List<ConsolidationTemplate.InputReference> inputs,
            @JsonPropertyDescription("Merge strategy used for consolidation.")
            String mergeStrategy,
            @JsonPropertyDescription("Conflict resolutions applied during merge.")
            List<ConsolidationTemplate.ConflictResolution> conflictResolutions,
            @JsonPropertyDescription("Aggregated metrics from inputs.")
            Map<String, Double> aggregatedMetrics,
            @JsonPropertyDescription("Unified consolidated output summary.")
            String consolidatedOutput,
            @JsonPropertyDescription("Collector decision for routing.")
            CollectorDecision collectorDecision,
            @JsonPropertyDescription("Upstream context chain for traceability.")
            List<ContextId> upstreamContextChain,
            @JsonPropertyDescription("Additional metadata for the result.")
            Map<String, String> metadata,
            @JsonPropertyDescription("Unified code map derived from discovery.")
            CodeMap unifiedCodeMap,
            @JsonPropertyDescription("Recommendations derived from discovery.")
            List<Recommendation> recommendations,
            @JsonPropertyDescription("Query-specific findings keyed by query name.")
            Map<String, QueryFindings> querySpecificFindings,
            @JsonPropertyDescription("Curated discovery context for downstream agents.")
            UpstreamContext.DiscoveryCollectorContext discoveryCuration
    ) implements ConsolidationTemplate, AgentResult {
        public DiscoveryCollectorResult(String consolidatedOutput, CollectorDecision collectorDecision) {
            this(null, null, List.of(), null, List.of(), Map.of(), consolidatedOutput, collectorDecision, List.of(), Map.of(), null, List.of(), Map.of(), null);
        }

        @Override
        public List<Curation> curations() {
            return discoveryCuration == null ? List.of() : List.of(discoveryCuration);
        }

        @Override
        public CollectorDecision decision() {
            return collectorDecision;
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (consolidatedOutput != null && !consolidatedOutput.isBlank()) {
                builder.append(consolidatedOutput.trim()).append("\n");
            }
            if (collectorDecision != null) {
                builder.append("Decision: ").append(collectorDecision.decisionType());
                if (collectorDecision.rationale() != null && !collectorDecision.rationale().isBlank()) {
                    builder.append(" - ").append(collectorDecision.rationale().trim());
                }
                builder.append("\n");
            }
            if (discoveryCuration != null) {
                builder.append("Curation:\n").append(discoveryCuration.prettyPrint()).append("\n");
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Consolidated planning results and routing decision.")
    record PlanningCollectorResult(
            @JsonPropertyDescription("Schema version for this result payload.")
            String schemaVersion,
            @JsonPropertyDescription("Unique context id for this result.")
            ContextId resultId,
            @JsonPropertyDescription("Input references consolidated into this result.")
            List<ConsolidationTemplate.InputReference> inputs,
            @JsonPropertyDescription("Merge strategy used for consolidation.")
            String mergeStrategy,
            @JsonPropertyDescription("Conflict resolutions applied during merge.")
            List<ConsolidationTemplate.ConflictResolution> conflictResolutions,
            @JsonPropertyDescription("Aggregated metrics from inputs.")
            Map<String, Double> aggregatedMetrics,
            @JsonPropertyDescription("Unified consolidated output summary.")
            String consolidatedOutput,
            @JsonPropertyDescription("Collector decision for routing.")
            CollectorDecision collectorDecision,
            @JsonPropertyDescription("Upstream context chain for traceability.")
            List<ContextId> upstreamContextChain,
            @JsonPropertyDescription("Additional metadata for the result.")
            Map<String, String> metadata,
            @JsonPropertyDescription("Finalized planning tickets.")
            List<PlanningTicket> finalizedTickets,
            @JsonPropertyDescription("Dependency graph between tickets.")
            List<TicketDependency> dependencyGraph,
            @JsonPropertyDescription("Discovery result id referenced during planning.")
            ContextId discoveryResultId,
            @JsonPropertyDescription("Curated planning context for downstream agents.")
            UpstreamContext.PlanningCollectorContext planningCuration
    ) implements ConsolidationTemplate, AgentResult {
        public PlanningCollectorResult(String consolidatedOutput, CollectorDecision collectorDecision) {
            this(null, null, List.of(), null, List.of(), Map.of(), consolidatedOutput, collectorDecision, List.of(), Map.of(), List.of(), List.of(), null, null);
        }

        @Override
        public List<Curation> curations() {
            return planningCuration == null ? List.of() : List.of(planningCuration);
        }

        @Override
        public CollectorDecision decision() {
            return collectorDecision;
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (consolidatedOutput != null && !consolidatedOutput.isBlank()) {
                builder.append(consolidatedOutput.trim()).append("\n");
            }
            if (collectorDecision != null) {
                builder.append("Decision: ").append(collectorDecision.decisionType());
                if (collectorDecision.rationale() != null && !collectorDecision.rationale().isBlank()) {
                    builder.append(" - ").append(collectorDecision.rationale().trim());
                }
                builder.append("\n");
            }
            if (finalizedTickets != null && !finalizedTickets.isEmpty()) {
                builder.append("Tickets:\n");
                for (PlanningTicket ticket : finalizedTickets) {
                    if (ticket == null) {
                        continue;
                    }
                    builder.append("- ").append(ticket.prettyPrint()).append("\n");
                }
            }
            if (planningCuration != null) {
                builder.append("Curation:\n").append(planningCuration.prettyPrint()).append("\n");
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    record ContextCollectorResult(
            String consolidatedOutput,
            CollectorDecision collectorDecision
    ) implements AgentResult {
        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (consolidatedOutput != null && !consolidatedOutput.isBlank()) {
                builder.append(consolidatedOutput.trim()).append("\n");
            }
            if (collectorDecision != null) {
                builder.append("Decision: ").append(collectorDecision.decisionType());
                if (collectorDecision.rationale() != null && !collectorDecision.rationale().isBlank()) {
                    builder.append(" - ").append(collectorDecision.rationale().trim());
                }
                builder.append("\n");
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Final consolidated workflow result and routing decision.")
    record OrchestratorCollectorResult(
            @JsonPropertyDescription("Schema version for this result payload.")
            String schemaVersion,
            @JsonPropertyDescription("Unique context id for this result.")
            ContextId resultId,
            @JsonPropertyDescription("Input references consolidated into this result.")
            List<ConsolidationTemplate.InputReference> inputs,
            @JsonPropertyDescription("Merge strategy used for consolidation.")
            String mergeStrategy,
            @JsonPropertyDescription("Conflict resolutions applied during merge.")
            List<ConsolidationTemplate.ConflictResolution> conflictResolutions,
            @JsonPropertyDescription("Aggregated metrics from inputs.")
            Map<String, Double> aggregatedMetrics,
            @JsonPropertyDescription("Unified consolidated output summary.")
            String consolidatedOutput,
            @JsonPropertyDescription("Collector decision for routing.")
            CollectorDecision collectorDecision,
            @JsonPropertyDescription("Upstream context chain for traceability.")
            List<ContextId> upstreamContextChain,
            @JsonPropertyDescription("Additional metadata for the result.")
            Map<String, String> metadata,
            @JsonPropertyDescription("Discovery collector result included in consolidation.")
            DiscoveryCollectorResult discoveryCollectorResult,
            @JsonPropertyDescription("Planning collector result included in consolidation.")
            PlanningCollectorResult planningCollectorResult,
            @JsonPropertyDescription("Ticket collector result included in consolidation.")
            TicketCollectorResult ticketCollectorResult
    ) implements ConsolidationTemplate, AgentResult {
        public OrchestratorCollectorResult(String consolidatedOutput, CollectorDecision collectorDecision) {
            this(null, null, List.of(), null, List.of(), Map.of(), consolidatedOutput, collectorDecision, List.of(), Map.of(), null, null, null);
        }

        @Override
        public List<Curation> curations() {
            return List.of();
        }

        @Override
        public CollectorDecision decision() {
            return collectorDecision;
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (consolidatedOutput != null && !consolidatedOutput.isBlank()) {
                builder.append(consolidatedOutput.trim()).append("\n");
            }
            if (collectorDecision != null) {
                builder.append("Decision: ").append(collectorDecision.decisionType());
                if (collectorDecision.rationale() != null && !collectorDecision.rationale().isBlank()) {
                    builder.append(" - ").append(collectorDecision.rationale().trim());
                }
                builder.append("\n");
            }
            if (discoveryCollectorResult != null) {
                builder.append("Discovery Result:\n").append(discoveryCollectorResult.prettyPrint()).append("\n");
            }
            if (planningCollectorResult != null) {
                builder.append("Planning Result:\n").append(planningCollectorResult.prettyPrint()).append("\n");
            }
            if (ticketCollectorResult != null) {
                builder.append("Ticket Result:\n").append(ticketCollectorResult.prettyPrint()).append("\n");
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Consolidated ticket execution results and routing decision.")
    record TicketCollectorResult(
            @JsonPropertyDescription("Schema version for this result payload.")
            String schemaVersion,
            @JsonPropertyDescription("Unique context id for this result.")
            ContextId resultId,
            @JsonPropertyDescription("Input references consolidated into this result.")
            List<ConsolidationTemplate.InputReference> inputs,
            @JsonPropertyDescription("Merge strategy used for consolidation.")
            String mergeStrategy,
            @JsonPropertyDescription("Conflict resolutions applied during merge.")
            List<ConsolidationTemplate.ConflictResolution> conflictResolutions,
            @JsonPropertyDescription("Aggregated metrics from inputs.")
            Map<String, Double> aggregatedMetrics,
            @JsonPropertyDescription("Unified consolidated output summary.")
            String consolidatedOutput,
            @JsonPropertyDescription("Collector decision for routing.")
            CollectorDecision collectorDecision,
            @JsonPropertyDescription("Upstream context chain for traceability.")
            List<ContextId> upstreamContextChain,
            @JsonPropertyDescription("Additional metadata for the result.")
            Map<String, String> metadata,
            @JsonPropertyDescription("Completion status summary.")
            String completionStatus,
            @JsonPropertyDescription("Follow-up items or remaining work.")
            List<String> followUps,
            @JsonPropertyDescription("Curated ticket context for downstream agents.")
            UpstreamContext.TicketCollectorContext ticketCuration
    ) implements ConsolidationTemplate, AgentResult {
        public TicketCollectorResult(String consolidatedOutput, CollectorDecision collectorDecision) {
            this(null, null, List.of(), null, List.of(), Map.of(), consolidatedOutput, collectorDecision, List.of(), Map.of(), null, List.of(), null);
        }

        @Override
        public List<Curation> curations() {
            return ticketCuration == null ? List.of() : List.of(ticketCuration);
        }

        @Override
        public CollectorDecision decision() {
            return collectorDecision;
        }

        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (consolidatedOutput != null && !consolidatedOutput.isBlank()) {
                builder.append(consolidatedOutput.trim()).append("\n");
            }
            if (completionStatus != null && !completionStatus.isBlank()) {
                builder.append("Completion Status: ").append(completionStatus.trim()).append("\n");
            }
            appendList(builder, "Follow Ups", followUps);
            if (collectorDecision != null) {
                builder.append("Decision: ").append(collectorDecision.decisionType());
                if (collectorDecision.rationale() != null && !collectorDecision.rationale().isBlank()) {
                    builder.append(" - ").append(collectorDecision.rationale().trim());
                }
                builder.append("\n");
            }
            if (ticketCuration != null) {
                builder.append("Curation:\n").append(ticketCuration.prettyPrint()).append("\n");
            }
            return builder.toString().trim();
        }
    }

    private static void appendList(StringBuilder builder, String label, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        builder.append(label).append(":\n");
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            builder.append("- ").append(value.trim()).append("\n");
        }
    }

    private static void appendStructuredChoices(StringBuilder builder, List<StructuredChoice> choices) {
        if (choices == null || choices.isEmpty()) {
            return;
        }
        builder.append("Choices:\n");
        for (StructuredChoice choice : choices) {
            if (choice == null) {
                continue;
            }
            String choiceId = choice.choiceId();
            String question = choice.question();
            builder.append("- ");
            if (choiceId != null && !choiceId.isBlank()) {
                builder.append(choiceId.trim()).append(": ");
            }
            builder.append(question == null ? "" : question.trim()).append("\n");
            if (choice.context() != null && !choice.context().isBlank()) {
                builder.append("  Context: ").append(choice.context().trim()).append("\n");
            }
            appendChoiceOptions(builder, choice.options());
            if (choice.recommended() != null && !choice.recommended().isBlank()) {
                builder.append("  Recommended: ").append(choice.recommended().trim()).append("\n");
            }
        }
    }

    private static void appendChoiceOptions(StringBuilder builder, Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        builder.append("  Options:\n");
        for (String key : List.of("A", "B", "C", "CUSTOM")) {
            String option = options.get(key);
            if (option == null || option.isBlank()) {
                continue;
            }
            builder.append("  - ").append(key).append(": ").append(option.trim()).append("\n");
        }
    }

    private static void appendConfirmationItems(StringBuilder builder, List<ConfirmationItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        builder.append("Confirmations:\n");
        for (ConfirmationItem item : items) {
            if (item == null) {
                continue;
            }
            builder.append("- ");
            if (item.confirmationId() != null && !item.confirmationId().isBlank()) {
                builder.append(item.confirmationId().trim()).append(": ");
            }
            builder.append(item.statement() == null ? "" : item.statement().trim()).append("\n");
            if (item.context() != null && !item.context().isBlank()) {
                builder.append("  Context: ").append(item.context().trim()).append("\n");
            }
            if (item.defaultValue() != null) {
                builder.append("  Default: ").append(item.defaultValue()).append("\n");
            }
            if (item.impactIfNo() != null && !item.impactIfNo().isBlank()) {
                builder.append("  Impact if no: ").append(item.impactIfNo().trim()).append("\n");
            }
        }
    }

    static String serializeResults(List<? extends AgentResult> results) {
        if (results == null || results.isEmpty()) {
            return "(none)";
        }
        StringBuilder builder = new StringBuilder();
        for (AgentResult result : results) {
            if (result == null) {
                continue;
            }
            String summary = result.prettyPrint(new AgentContext.AgentSerializationCtx.ResultsSerialization());
            if (summary == null || summary.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append("- ").append(summary.replace("\n", "\n  ").trim());
        }
        return builder.isEmpty() ? "(none)" : builder.toString();
    }

    @Builder(toBuilder=true)
    record ModuleOverview(
            String moduleName,
            String summary,
            List<String> relatedFiles,
            List<String> dependencies
    ) {
    }

    @Builder(toBuilder=true)
    record CodeMap(
            List<ModuleOverview> modules,
            List<DiscoveryReport.FileReference> deduplicatedReferences,
            DiscoveryReport.DiagramRepresentation unifiedDiagram,
            List<DiscoveryReport.SemanticTag> mergedTags
    ) {
    }

    enum RecommendationPriority {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }

    @Builder(toBuilder=true)
    record Recommendation(
            String recommendationId,
            String title,
            String description,
            RecommendationPriority priority,
            List<ContextId> supportingDiscoveryIds,
            List<String> relatedFilePaths,
            String estimatedImpact
    ) {
    }

    @Builder(toBuilder=true)
    record QueryFindings(
            String originalQuery,
            List<DiscoveryReport.FileReference> results,
            double confidenceScore,
            String summary
    ) {
    }

    @Builder(toBuilder=true)
    record TicketDependency(
            String fromTicketId,
            String toTicketId,
            String dependencyType
    ) {
    }

    @Builder(toBuilder=true)
    record DiscoveryCuration(
            List<DiscoveryReport> discoveryReports,
            CodeMap unifiedCodeMap,
            List<Recommendation> recommendations,
            Map<String, QueryFindings> querySpecificFindings,
            List<MemoryReference> memoryReferences,
            ConsolidationTemplate.ConsolidationSummary consolidationSummary
    ) implements AgentContext {
        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (consolidationSummary != null && consolidationSummary.consolidatedOutput() != null) {
                builder.append(consolidationSummary.consolidatedOutput().trim()).append("\n");
            }
            if (recommendations != null && !recommendations.isEmpty()) {
                builder.append("Recommendations:\n");
                for (Recommendation recommendation : recommendations) {
                    if (recommendation == null) {
                        continue;
                    }
                    builder.append("- ").append(recommendation.title()).append(": ")
                            .append(recommendation.description()).append("\n");
                }
            }
            if (discoveryReports != null && !discoveryReports.isEmpty()) {
                builder.append("Discovery Reports:\n");
                for (DiscoveryReport report : discoveryReports) {
                    if (report == null) {
                        continue;
                    }
                    builder.append("- ").append(report.prettyPrint()).append("\n");
                }
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    record PlanningCuration(
            List<PlanningAgentResult> planningAgentResults,
            List<PlanningTicket> finalizedTickets,
            List<TicketDependency> dependencyGraph,
            ContextId discoveryResultId,
            List<MemoryReference> memoryReferences,
            ConsolidationTemplate.ConsolidationSummary consolidationSummary
    ) implements AgentContext {
        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (consolidationSummary != null && consolidationSummary.consolidatedOutput() != null) {
                builder.append(consolidationSummary.consolidatedOutput().trim()).append("\n");
            }
            if (finalizedTickets != null && !finalizedTickets.isEmpty()) {
                builder.append("Tickets:\n");
                for (PlanningTicket ticket : finalizedTickets) {
                    if (ticket == null) {
                        continue;
                    }
                    builder.append("- ").append(ticket.prettyPrint()).append("\n");
                }
            }
            if (planningAgentResults != null && !planningAgentResults.isEmpty()) {
                builder.append("Planning Results:\n");
                for (PlanningAgentResult result : planningAgentResults) {
                    if (result == null) {
                        continue;
                    }
                    builder.append("- ").append(result.prettyPrint()).append("\n");
                }
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    record TicketCuration(
            List<TicketAgentResult> ticketAgentResults,
            String completionStatus,
            List<String> followUps,
            List<MemoryReference> memoryReferences,
            ConsolidationTemplate.ConsolidationSummary consolidationSummary
    ) implements AgentContext {
        @Override
        public String prettyPrint() {
            StringBuilder builder = new StringBuilder();
            if (completionStatus != null && !completionStatus.isBlank()) {
                builder.append("Status: ").append(completionStatus.trim()).append("\n");
            }
            if (consolidationSummary != null && consolidationSummary.consolidatedOutput() != null) {
                builder.append(consolidationSummary.consolidatedOutput().trim()).append("\n");
            }
            if (followUps != null && !followUps.isEmpty()) {
                builder.append("Follow Ups:\n");
                for (String followUp : followUps) {
                    if (followUp == null || followUp.isBlank()) {
                        continue;
                    }
                    builder.append("- ").append(followUp.trim()).append("\n");
                }
            }
            if (ticketAgentResults != null && !ticketAgentResults.isEmpty()) {
                builder.append("Ticket Results:\n");
                for (TicketAgentResult result : ticketAgentResults) {
                    if (result == null) {
                        continue;
                    }
                    builder.append("- ").append(result.prettyPrint()).append("\n");
                }
            }
            return builder.toString().trim();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Top-level orchestrator request to coordinate workflow phases.")
    record OrchestratorRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            ContextId contextId,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Current workflow phase.")
            String phase,
            @JsonPropertyDescription("Curated discovery context from discovery collector.")
            UpstreamContext.DiscoveryCollectorContext discoveryCuration,
            @JsonPropertyDescription("Curated planning context from planning collector.")
            UpstreamContext.PlanningCollectorContext planningCuration,
            @JsonPropertyDescription("Curated ticket context from ticket collector.")
            UpstreamContext.TicketCollectorContext ticketCuration,
            @JsonPropertyDescription("Previous orchestrator context for reruns.")
            PreviousContext.OrchestratorPreviousContext previousContext
    ) implements AgentRequest {
        public OrchestratorRequest(ContextId contextId, String goal, String phase) {
            this(contextId, goal, phase, null, null, null, null);
        }

        public OrchestratorRequest(String goal, String phase) {
            this(null, goal, phase, null, null, null, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim());
            }
            if (phase != null && !phase.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Phase: ").append(phase.trim());
            }
            return builder.isEmpty() ? "Goal: (none)" : builder.toString();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Orchestrator collector request to finalize workflow outputs.")
    record OrchestratorCollectorRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            ContextId contextId,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Current workflow phase.")
            String phase,
            @JsonPropertyDescription("Curated discovery context from discovery collector.")
            UpstreamContext.DiscoveryCollectorContext discoveryCuration,
            @JsonPropertyDescription("Curated planning context from planning collector.")
            UpstreamContext.PlanningCollectorContext planningCuration,
            @JsonPropertyDescription("Curated ticket context from ticket collector.")
            UpstreamContext.TicketCollectorContext ticketCuration,
            @JsonPropertyDescription("Previous orchestrator collector context for reruns.")
            PreviousContext.OrchestratorCollectorPreviousContext previousContext
    ) implements AgentRequest {
        public OrchestratorCollectorRequest(String goal, String phase) {
            this(null, goal, phase, null, null, null, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim());
            }
            if (phase != null && !phase.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Phase: ").append(phase.trim());
            }
            return builder.isEmpty() ? "Goal: (none)" : builder.toString();
        }
    }

    /**
     * Embabel uses SomeOf to determine which fields are requests to be added to the blackboard.
     * SomeOf contains in it's fields the requests that are added to the blackboard.
     * I've had quite a few issues with inability to interpolate Embabel for this.
     */
    sealed interface Routing extends SomeOf permits
            OrchestratorRouting,
            OrchestratorCollectorRouting,
            DiscoveryOrchestratorRouting,
            DiscoveryAgentRouting,
            DiscoveryCollectorRouting,
            DiscoveryAgentDispatchRouting,
            PlanningOrchestratorRouting,
            PlanningAgentRouting,
            PlanningCollectorRouting,
            PlanningAgentDispatchRouting,
            TicketOrchestratorRouting,
            TicketAgentRouting,
            TicketCollectorRouting,
            TicketAgentDispatchRouting,
            ReviewRouting,
            MergerRouting {}

    /**
     * Routing type for orchestrator - routes to interrupt, collector, or discovery orchestrator.
     * Note: Routing types do NOT implement DelegationTemplate - they are routing containers.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the orchestrator.")
    record OrchestratorRouting(
            @JsonPropertyDescription("Interrupt request for orchestration decisions.")
            OrchestratorInterruptRequest interruptRequest,
            @JsonPropertyDescription("Route to orchestrator collector for finalization.")
            OrchestratorCollectorRequest collectorRequest,
            @JsonPropertyDescription("Route to discovery orchestrator to start discovery.")
            DiscoveryOrchestratorRequest orchestratorRequest
    ) implements Routing {
        public OrchestratorRouting(OrchestratorInterruptRequest interruptRequest, OrchestratorCollectorRequest collectorRequest) {
            this(interruptRequest, collectorRequest, null);
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the orchestrator collector.")
    record OrchestratorCollectorRouting(
            @JsonPropertyDescription("Interrupt request for collector decisions.")
            OrchestratorCollectorInterruptRequest interruptRequest,
            @JsonPropertyDescription("Collector result to drive branching decisions.")
            OrchestratorCollectorResult collectorResult,
            @JsonPropertyDescription("Route back to orchestrator.")
            OrchestratorRequest orchestratorRequest,
            @JsonPropertyDescription("Route to discovery orchestrator.")
            DiscoveryOrchestratorRequest discoveryRequest,
            @JsonPropertyDescription("Route to planning orchestrator.")
            PlanningOrchestratorRequest planningRequest,
            @JsonPropertyDescription("Route to ticket orchestrator.")
            TicketOrchestratorRequest ticketRequest,
            @JsonPropertyDescription("Route to review agent.")
            ReviewRequest reviewRequest,
            @JsonPropertyDescription("Route to merger agent.")
            MergerRequest mergerRequest
    ) implements Routing {
        public OrchestratorCollectorRouting(OrchestratorCollectorResult collectorResult) {
            this(null, collectorResult, null, null, null, null, null, null);
        }
    }

    @Builder(toBuilder=true)
    record DiscoveryOrchestratorRequested(DiscoveryOrchestratorRequest request) {

    }

    /**
     * Request for discovery orchestrator. Uses typed curation fields from upstream collectors.
     * The model does not set upstream context - it receives curated context from the framework.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for the discovery orchestrator to partition and dispatch discovery work.")
    record DiscoveryOrchestratorRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            ContextId contextId,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Previous discovery orchestrator context for reruns.")
            PreviousContext.DiscoveryOrchestratorPreviousContext previousContext
    ) implements AgentRequest {
        public DiscoveryOrchestratorRequest(String goal) {
            this(null, goal, null);
        }

        public DiscoveryOrchestratorRequested to() {
            return new DiscoveryOrchestratorRequested(this);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            return goal == null || goal.isBlank() ? "Goal: (none)" : "Goal: " + goal.trim();
        }

    }

    /**
     * Request for discovery agent. No upstream context field - discovery agents are at the start of the pipeline.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for a discovery agent to inspect a subdomain.")
    record DiscoveryAgentRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            ContextId contextId,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Subdomain focus for this discovery task.")
            String subdomainFocus,
            @JsonPropertyDescription("Previous discovery agent context for reruns.")
            PreviousContext.DiscoveryAgentPreviousContext previousContext
    ) implements AgentRequest {
        public DiscoveryAgentRequest(String goal, String subdomainFocus) {
            this(null, goal, subdomainFocus, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim());
            }
            if (subdomainFocus != null && !subdomainFocus.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Subdomain Focus: ").append(subdomainFocus.trim());
            }
            return builder.isEmpty() ? "Goal: (none)" : builder.toString();
        }
    }

    /**
     * DelegationTemplate for discovery orchestrator - contains multiple sub-agent requests.
     * The model returns this to delegate work to discovery agents.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Delegation template carrying multiple discovery agent requests.")
    record DiscoveryAgentRequests(
            @JsonPropertyDescription("List of discovery agent requests.")
            List<DiscoveryAgentRequest> requests,
            @JsonPropertyDescription("Schema version for this delegation payload.")
            String schemaVersion,
            @JsonPropertyDescription("Unique context id for this delegation result.")
            ContextId resultId,
            @JsonPropertyDescription("Upstream context id driving this delegation.")
            ContextId upstreamContextId,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Rationale for delegation and assignments.")
            String delegationRationale,
            @JsonPropertyDescription("Agent assignments describing distribution.")
            List<DelegationTemplate.AgentAssignment> assignments,
            @JsonPropertyDescription("Context selections for each assignment.")
            List<DelegationTemplate.ContextSelection> contextSelections,
            @JsonPropertyDescription("Additional metadata for delegation.")
            Map<String, String> metadata
    ) implements DelegationTemplate, AgentRequest {
        public DiscoveryAgentRequests(List<DiscoveryAgentRequest> requests) {
            this(requests, null, null, null, null, null, null, null, null);
        }

        @Override
        public String prettyPrint(AgentSerializationCtx serializationCtx) {
            return switch (serializationCtx) {
                case AgentSerializationCtx.GoalResolutionSerialization goalResolutionSerialization ->
                        resolveGoal();
                default -> AgentRequest.super.prettyPrint(serializationCtx);
            };
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim()).append("\n");
            }
            if (requests == null || requests.isEmpty()) {
                builder.append("Requests: (none)");
                return builder.toString().trim();
            }
            builder.append("Requests:\n");
            int index = 0;
            for (DiscoveryAgentRequest request : requests) {
                if (request == null) {
                    continue;
                }
                index++;
                builder.append(index).append(". ");
                String summary = request.prettyPrintInterruptContinuation();
                builder.append(summary.replace("\n", " | "));
                builder.append("\n");
            }
            return builder.toString().trim();
        }

        private String resolveGoal() {
            if (goal != null && !goal.isBlank()) {
                return goal.trim();
            }
            if (requests != null) {
                for (DiscoveryAgentRequest request : requests) {
                    if (request != null && request.goal() != null && !request.goal().isBlank()) {
                        return request.goal().trim();
                    }
                }
            }
            return "";
        }
    }

    /**
     * Request for discovery collector. No upstream context field - receives discovery agent results directly.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for the discovery collector to consolidate agent findings.")
    record DiscoveryCollectorRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            ContextId contextId,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Serialized discovery results to consolidate.")
            String discoveryResults,
            @JsonPropertyDescription("Previous discovery collector context for reruns.")
            PreviousContext.DiscoveryCollectorPreviousContext previousContext
    ) implements AgentRequest {
        public DiscoveryCollectorRequest(String goal, String discoveryResults) {
            this(null, goal, discoveryResults, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim());
            }
            if (discoveryResults != null && !discoveryResults.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Discovery Results:\n").append(discoveryResults.trim());
            }
            return builder.isEmpty() ? "Goal: (none)" : builder.toString();
        }
    }

    /**
     * Routing type for discovery orchestrator - routes to interrupt, agent requests, or collector.
     * Note: Routing types do NOT implement DelegationTemplate - the agentRequests field IS the DelegationTemplate.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the discovery orchestrator.")
    record DiscoveryOrchestratorRouting(
            @JsonPropertyDescription("Interrupt request for discovery orchestration decisions.")
            DiscoveryOrchestratorInterruptRequest interruptRequest,
            @JsonPropertyDescription("Delegation payload for discovery agents.")
            DiscoveryAgentRequests agentRequests,
            @JsonPropertyDescription("Route to discovery collector.")
            DiscoveryCollectorRequest collectorRequest
    ) implements Routing {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for a discovery agent.")
    record DiscoveryAgentRouting(
            @JsonPropertyDescription("Interrupt request for discovery agent decisions.")
            DiscoveryAgentInterruptRequest interruptRequest,
            @JsonPropertyDescription("Discovery agent result payload.")
            DiscoveryAgentResult agentResult,
            @JsonPropertyDescription("Optional route to planning orchestrator.")
            AgentModels.PlanningOrchestratorRequest planningOrchestratorRequest
    ) implements Routing {
        public DiscoveryAgentRouting(DiscoveryAgentInterruptRequest interruptRequest, DiscoveryAgentResult agentResult) {
            this(interruptRequest, agentResult, null);
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the discovery collector.")
    record DiscoveryCollectorRouting(
            @JsonPropertyDescription("Interrupt request for discovery collector decisions.")
            DiscoveryCollectorInterruptRequest interruptRequest,
            @JsonPropertyDescription("Collector result to drive branching decisions.")
            DiscoveryCollectorResult collectorResult,
            @JsonPropertyDescription("Route to orchestrator.")
            OrchestratorRequest orchestratorRequest,
            @JsonPropertyDescription("Route back to discovery orchestrator.")
            DiscoveryOrchestratorRequest discoveryRequest,
            @JsonPropertyDescription("Route to planning orchestrator.")
            PlanningOrchestratorRequest planningRequest,
            @JsonPropertyDescription("Route to ticket orchestrator.")
            TicketOrchestratorRequest ticketRequest,
            @JsonPropertyDescription("Route to context orchestrator.")
            ContextOrchestratorRequest contextRequest,
            @JsonPropertyDescription("Route to review agent.")
            ReviewRequest reviewRequest,
            @JsonPropertyDescription("Route to merger agent.")
            MergerRequest mergerRequest
    ) implements Routing {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for discovery agent dispatch.")
    record DiscoveryAgentDispatchRouting(
            @JsonPropertyDescription("Interrupt request for dispatch decisions.")
            DiscoveryAgentDispatchInterruptRequest interruptRequest,
            @JsonPropertyDescription("Route to discovery collector with aggregated results.")
            DiscoveryCollectorRequest collectorRequest
    ) implements Routing { }

    /**
     * Request for planning orchestrator. Uses typed curation field from discovery collector.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for the planning orchestrator to decompose work.")
    record PlanningOrchestratorRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            ContextId contextId,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Curated discovery context from discovery collector.")
            UpstreamContext.DiscoveryCollectorContext discoveryCuration,
            @JsonPropertyDescription("Previous planning orchestrator context for reruns.")
            PreviousContext.PlanningOrchestratorPreviousContext previousContext
    ) implements AgentRequest {
        public PlanningOrchestratorRequest(String goal) {
            this(null, goal, null, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            return goal == null || goal.isBlank() ? "Goal: (none)" : "Goal: " + goal.trim();
        }
    }

    /**
     * Request for planning agent. Uses typed curation field from discovery collector.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for a planning agent to produce tickets.")
    record PlanningAgentRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            ContextId contextId,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Curated discovery context from discovery collector.")
            UpstreamContext.DiscoveryCollectorContext discoveryCuration,
            @JsonPropertyDescription("Previous planning agent context for reruns.")
            PreviousContext.PlanningAgentPreviousContext previousContext
    ) implements AgentRequest {
        public PlanningAgentRequest(String goal) {
            this(null, goal, null, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            return goal == null || goal.isBlank() ? "Goal: (none)" : "Goal: " + goal.trim();
        }
    }

    /**
     * DelegationTemplate for planning orchestrator - contains multiple sub-agent requests.
     * The model returns this to delegate work to planning agents.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Delegation template carrying multiple planning agent requests.")
    record PlanningAgentRequests(
            @JsonPropertyDescription("List of planning agent requests.")
            List<PlanningAgentRequest> requests,
            @JsonPropertyDescription("Schema version for this delegation payload.")
            String schemaVersion,
            @JsonPropertyDescription("Unique context id for this delegation result.")
            ContextId resultId,
            @JsonPropertyDescription("Upstream context id driving this delegation.")
            ContextId upstreamContextId,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Rationale for delegation and assignments.")
            String delegationRationale,
            @JsonPropertyDescription("Agent assignments describing distribution.")
            List<DelegationTemplate.AgentAssignment> assignments,
            @JsonPropertyDescription("Context selections for each assignment.")
            List<DelegationTemplate.ContextSelection> contextSelections,
            @JsonPropertyDescription("Additional metadata for delegation.")
            Map<String, String> metadata
    ) implements DelegationTemplate, AgentRequest {
        public PlanningAgentRequests(List<PlanningAgentRequest> requests) {
            this(requests, null, null, null, null, null, null, null, null);
        }

        @Override
        public String prettyPrint(AgentSerializationCtx serializationCtx) {
            return switch (serializationCtx) {
                case AgentSerializationCtx.GoalResolutionSerialization goalResolutionSerialization ->
                        resolveGoal();
                default -> AgentRequest.super.prettyPrint(serializationCtx);
            };
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim()).append("\n");
            }
            if (requests == null || requests.isEmpty()) {
                builder.append("Requests: (none)");
                return builder.toString().trim();
            }
            builder.append("Requests:\n");
            int index = 0;
            for (PlanningAgentRequest request : requests) {
                if (request == null) {
                    continue;
                }
                index++;
                builder.append(index).append(". ");
                String summary = request.prettyPrintInterruptContinuation();
                builder.append(summary.replace("\n", " | "));
                builder.append("\n");
            }
            return builder.toString().trim();
        }

        private String resolveGoal() {
            if (goal != null && !goal.isBlank()) {
                return goal.trim();
            }
            if (requests != null) {
                for (PlanningAgentRequest request : requests) {
                    if (request != null && request.goal() != null && !request.goal().isBlank()) {
                        return request.goal().trim();
                    }
                }
            }
            return "";
        }
    }

    /**
     * Request for planning collector. Uses typed curation field from discovery collector.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for the planning collector to consolidate tickets.")
    record PlanningCollectorRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            ContextId contextId,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Serialized planning results to consolidate.")
            String planningResults,
            @JsonPropertyDescription("Curated discovery context from discovery collector.")
            UpstreamContext.DiscoveryCollectorContext discoveryCuration,
            @JsonPropertyDescription("Previous planning collector context for reruns.")
            PreviousContext.PlanningCollectorPreviousContext previousContext
    ) implements AgentRequest {
        public PlanningCollectorRequest(String goal, String planningResults) {
            this(null, goal, planningResults, null, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim());
            }
            if (planningResults != null && !planningResults.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Planning Results:\n").append(planningResults.trim());
            }
            return builder.isEmpty() ? "Goal: (none)" : builder.toString();
        }
    }

    /**
     * Routing type for planning orchestrator - routes to interrupt, agent requests, or collector.
     * Note: Routing types do NOT implement DelegationTemplate - the agentRequests field IS the DelegationTemplate.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the planning orchestrator.")
    record PlanningOrchestratorRouting(
            @JsonPropertyDescription("Interrupt request for planning orchestration decisions.")
            PlanningOrchestratorInterruptRequest interruptRequest,
            @JsonPropertyDescription("Delegation payload for planning agents.")
            PlanningAgentRequests agentRequests,
            @JsonPropertyDescription("Route to planning collector.")
            PlanningCollectorRequest collectorRequest
    ) implements Routing {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for a planning agent.")
    record PlanningAgentRouting(
            @JsonPropertyDescription("Interrupt request for planning agent decisions.")
            PlanningAgentInterruptRequest interruptRequest,
            @JsonPropertyDescription("Planning agent result payload.")
            PlanningAgentResult agentResult
    ) implements Routing {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the planning collector.")
    record PlanningCollectorRouting(
            @JsonPropertyDescription("Interrupt request for planning collector decisions.")
            PlanningCollectorInterruptRequest interruptRequest,
            @JsonPropertyDescription("Collector result to drive branching decisions.")
            PlanningCollectorResult collectorResult,
            @JsonPropertyDescription("Route back to planning orchestrator.")
            PlanningOrchestratorRequest planningRequest,
            @JsonPropertyDescription("Route back to discovery orchestrator.")
            DiscoveryOrchestratorRequest discoveryOrchestratorRequest,
            @JsonPropertyDescription("Route to review agent.")
            ReviewRequest reviewRequest,
            @JsonPropertyDescription("Route to merger agent.")
            MergerRequest mergerRequest,
            @JsonPropertyDescription("Route to ticket orchestrator.")
            TicketOrchestratorRequest ticketOrchestratorRequest,
            @JsonPropertyDescription("Route to orchestrator collector.")
            OrchestratorCollectorRequest orchestratorCollectorRequest
    ) implements Routing {
        public PlanningCollectorRouting(PlanningCollectorInterruptRequest interruptRequest, PlanningCollectorResult collectorResult, PlanningOrchestratorRequest planningRequest, DiscoveryOrchestratorRequest discoveryOrchestratorRequest, ReviewRequest reviewRequest, MergerRequest mergerRequest) {
            this(interruptRequest, collectorResult, planningRequest, discoveryOrchestratorRequest, reviewRequest, mergerRequest, null, null);
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for planning agent dispatch.")
    record PlanningAgentDispatchRouting(
            @JsonPropertyDescription("Interrupt request for dispatch decisions.")
            PlanningAgentDispatchInterruptRequest interruptRequest,
            @JsonPropertyDescription("Route to planning collector with aggregated results.")
            PlanningCollectorRequest planningCollectorRequest
    ) implements Routing {
    }

    /**
     * Request for ticket orchestrator. Uses typed curation fields from discovery and planning collectors.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for the ticket orchestrator to execute planned work.")
    record TicketOrchestratorRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            ContextId contextId,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Curated discovery context from discovery collector.")
            UpstreamContext.DiscoveryCollectorContext discoveryCuration,
            @JsonPropertyDescription("Curated planning context from planning collector.")
            UpstreamContext.PlanningCollectorContext planningCuration,
            @JsonPropertyDescription("Previous ticket orchestrator context for reruns.")
            PreviousContext.TicketOrchestratorPreviousContext previousContext
    ) implements AgentRequest {
        public TicketOrchestratorRequest(String goal) {
            this(null, goal, null, null, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            return goal == null || goal.isBlank() ? "Goal: (none)" : "Goal: " + goal.trim();
        }
    }

    /**
     * Request for ticket agent. Uses typed curation fields from discovery and planning collectors.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for a ticket agent to implement a ticket.")
    record TicketAgentRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            ContextId contextId,
            @JsonPropertyDescription("Ticket details or instructions.")
            String ticketDetails,
            @JsonPropertyDescription("File path containing ticket details.")
            String ticketDetailsFilePath,
            @JsonPropertyDescription("Curated discovery context from discovery collector.")
            UpstreamContext.DiscoveryCollectorContext discoveryCuration,
            @JsonPropertyDescription("Curated planning context from planning collector.")
            UpstreamContext.PlanningCollectorContext planningCuration,
            @JsonPropertyDescription("Previous ticket agent context for reruns.")
            PreviousContext.TicketAgentPreviousContext previousContext
    ) implements AgentRequest {
        public TicketAgentRequest(String ticketDetails, String ticketDetailsFilePath) {
            this(null, ticketDetails, ticketDetailsFilePath, null, null, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (ticketDetails != null && !ticketDetails.isBlank()) {
                builder.append("Ticket Details: ").append(ticketDetails.trim());
            }
            if (ticketDetailsFilePath != null && !ticketDetailsFilePath.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Ticket File Path: ").append(ticketDetailsFilePath.trim());
            }
            return builder.isEmpty() ? "Ticket Details: (none)" : builder.toString();
        }
    }

    /**
     * DelegationTemplate for ticket orchestrator - contains multiple sub-agent requests.
     * The model returns this to delegate work to ticket agents.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Delegation template carrying multiple ticket agent requests.")
    record TicketAgentRequests(
            @JsonPropertyDescription("List of ticket agent requests.")
            List<TicketAgentRequest> requests,
            @JsonPropertyDescription("Schema version for this delegation payload.")
            String schemaVersion,
            @JsonPropertyDescription("Unique context id for this delegation result.")
            ContextId resultId,
            @JsonPropertyDescription("Upstream context id driving this delegation.")
            ContextId upstreamContextId,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Rationale for delegation and assignments.")
            String delegationRationale,
            @JsonPropertyDescription("Agent assignments describing distribution.")
            List<DelegationTemplate.AgentAssignment> assignments,
            @JsonPropertyDescription("Context selections for each assignment.")
            List<DelegationTemplate.ContextSelection> contextSelections,
            @JsonPropertyDescription("Additional metadata for delegation.")
            Map<String, String> metadata
    ) implements DelegationTemplate, AgentRequest {
        @Override
        public String prettyPrint(AgentSerializationCtx serializationCtx) {
            return switch (serializationCtx) {
                case AgentSerializationCtx.GoalResolutionSerialization goalResolutionSerialization ->
                        resolveGoal();
                default -> AgentRequest.super.prettyPrint(serializationCtx);
            };
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim()).append("\n");
            }
            if (requests == null || requests.isEmpty()) {
                builder.append("Requests: (none)");
                return builder.toString().trim();
            }
            builder.append("Requests:\n");
            int index = 0;
            for (TicketAgentRequest request : requests) {
                if (request == null) {
                    continue;
                }
                index++;
                builder.append(index).append(". ");
                String summary = request.prettyPrintInterruptContinuation();
                builder.append(summary.replace("\n", " | "));
                builder.append("\n");
            }
            return builder.toString().trim();
        }

        private String resolveGoal() {
            if (goal != null && !goal.isBlank()) {
                return goal.trim();
            }
            return "";
        }
    }

    /**
     * Request for ticket collector. Uses typed curation fields from discovery and planning collectors.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for the ticket collector to consolidate execution results.")
    record TicketCollectorRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            ContextId contextId,
            @JsonPropertyDescription("Workflow goal statement.")
            String goal,
            @JsonPropertyDescription("Serialized ticket results to consolidate.")
            String ticketResults,
            @JsonPropertyDescription("Curated discovery context from discovery collector.")
            UpstreamContext.DiscoveryCollectorContext discoveryCuration,
            @JsonPropertyDescription("Curated planning context from planning collector.")
            UpstreamContext.PlanningCollectorContext planningCuration,
            @JsonPropertyDescription("Previous ticket collector context for reruns.")
            PreviousContext.TicketCollectorPreviousContext previousContext
    ) implements AgentRequest {
        public TicketCollectorRequest(String goal, String ticketResults) {
            this(null, goal, ticketResults, null, null, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim());
            }
            if (ticketResults != null && !ticketResults.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Ticket Results:\n").append(ticketResults.trim());
            }
            return builder.isEmpty() ? "Goal: (none)" : builder.toString();
        }
    }

    /**
     * Routing type for ticket orchestrator - routes to interrupt, agent requests, or collector.
     * Note: Routing types do NOT implement DelegationTemplate - the agentRequests field IS the DelegationTemplate.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the ticket orchestrator.")
    record TicketOrchestratorRouting(
            @JsonPropertyDescription("Interrupt request for ticket orchestration decisions.")
            TicketOrchestratorInterruptRequest interruptRequest,
            @JsonPropertyDescription("Delegation payload for ticket agents.")
            TicketAgentRequests agentRequests,
            @JsonPropertyDescription("Route to ticket collector.")
            TicketCollectorRequest collectorRequest
    ) implements Routing {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for a ticket agent.")
    record TicketAgentRouting(
            @JsonPropertyDescription("Interrupt request for ticket agent decisions.")
            TicketAgentInterruptRequest interruptRequest,
            @JsonPropertyDescription("Ticket agent result payload.")
            TicketAgentResult agentResult
    ) implements Routing {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the ticket collector.")
    record TicketCollectorRouting(
            @JsonPropertyDescription("Interrupt request for ticket collector decisions.")
            TicketCollectorInterruptRequest interruptRequest,
            @JsonPropertyDescription("Collector result to drive branching decisions.")
            TicketCollectorResult collectorResult,
            @JsonPropertyDescription("Route back to ticket orchestrator.")
            TicketOrchestratorRequest ticketRequest,
            @JsonPropertyDescription("Route to orchestrator collector.")
            AgentModels.OrchestratorCollectorRequest orchestratorCollectorRequest,
            @JsonPropertyDescription("Route to review agent.")
            ReviewRequest reviewRequest,
            @JsonPropertyDescription("Route to merger agent.")
            MergerRequest mergerRequest
    ) implements Routing {
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for ticket agent dispatch.")
    record TicketAgentDispatchRouting(
            @JsonPropertyDescription("Interrupt request for dispatch decisions.")
            TicketAgentDispatchInterruptRequest interruptRequest,
            @JsonPropertyDescription("Route to ticket collector with aggregated results.")
            TicketCollectorRequest ticketCollectorRequest
    ) implements Routing {
    }

    /**
     * Request for review agent. No upstream context - review content is passed directly.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for the review agent to assess content.")
    record ReviewRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            ContextId contextId,
            @JsonPropertyDescription("Content to review.")
            String content,
            @JsonPropertyDescription("Review criteria or rubric.")
            String criteria,
            @JsonPropertyDescription("Previous review context for reruns.")
            PreviousContext.ReviewPreviousContext previousContext,
            @JsonPropertyDescription("Return route to orchestrator collector.")
            OrchestratorCollectorRequest returnToOrchestratorCollector,
            @JsonPropertyDescription("Return route to discovery collector.")
            DiscoveryCollectorRequest returnToDiscoveryCollector,
            @JsonPropertyDescription("Return route to planning collector.")
            PlanningCollectorRequest returnToPlanningCollector,
            @JsonPropertyDescription("Return route to ticket collector.")
            TicketCollectorRequest returnToTicketCollector
    ) implements AgentRequest {
        public ReviewRequest(String content, String criteria, OrchestratorCollectorRequest returnToOrchestratorCollector, DiscoveryCollectorRequest returnToDiscoveryCollector, PlanningCollectorRequest returnToPlanningCollector, TicketCollectorRequest returnToTicketCollector) {
            this(null, content, criteria, null, returnToOrchestratorCollector, returnToDiscoveryCollector, returnToPlanningCollector, returnToTicketCollector);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (criteria != null && !criteria.isBlank()) {
                builder.append("Criteria: ").append(criteria.trim());
            }
            if (content != null && !content.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Content:\n").append(content.trim());
            }
            return builder.isEmpty() ? "Review Request: (none)" : builder.toString();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the review agent.")
    record ReviewRouting(
            @JsonPropertyDescription("Interrupt request for review decisions.")
            ReviewInterruptRequest interruptRequest,
            @JsonPropertyDescription("Review agent result payload.")
            ReviewAgentResult reviewResult,
            @JsonPropertyDescription("Route to orchestrator collector.")
            OrchestratorCollectorRequest orchestratorCollectorRequest,
            @JsonPropertyDescription("Route to discovery collector.")
            DiscoveryCollectorRequest discoveryCollectorRequest,
            @JsonPropertyDescription("Route to planning collector.")
            PlanningCollectorRequest planningCollectorRequest,
            @JsonPropertyDescription("Route to ticket collector.")
            TicketCollectorRequest ticketCollectorRequest
    ) implements Routing {
    }

    /**
     * Request for merger agent. No upstream context - merge content is passed directly.
     */
    @Builder(toBuilder=true)
    @JsonClassDescription("Request for the merger agent to validate a merge.")
    record MergerRequest(
            @JsonPropertyDescription("Unique context id for this request.")
            ContextId contextId,
            @JsonPropertyDescription("Merge context or details.")
            String mergeContext,
            @JsonPropertyDescription("Summary of merge changes.")
            String mergeSummary,
            @JsonPropertyDescription("Conflicting files or paths.")
            String conflictFiles,
            @JsonPropertyDescription("Previous merger context for reruns.")
            PreviousContext.MergerPreviousContext previousContext,
            @JsonPropertyDescription("Return route to orchestrator collector.")
            OrchestratorCollectorRequest returnToOrchestratorCollector,
            @JsonPropertyDescription("Return route to discovery collector.")
            DiscoveryCollectorRequest returnToDiscoveryCollector,
            @JsonPropertyDescription("Return route to planning collector.")
            PlanningCollectorRequest returnToPlanningCollector,
            @JsonPropertyDescription("Return route to ticket collector.")
            TicketCollectorRequest returnToTicketCollector
    ) implements AgentRequest {
        public MergerRequest(String mergeContext, String mergeSummary, String conflictFiles, OrchestratorCollectorRequest returnToOrchestratorCollector, DiscoveryCollectorRequest returnToDiscoveryCollector, PlanningCollectorRequest returnToPlanningCollector, TicketCollectorRequest returnToTicketCollector) {
            this(null, mergeContext, mergeSummary, conflictFiles, null, returnToOrchestratorCollector, returnToDiscoveryCollector, returnToPlanningCollector, returnToTicketCollector);
        }

        @Override
        public String prettyPrint(AgentSerializationCtx serializationCtx) {
            return switch (serializationCtx) {
                case AgentSerializationCtx.MergeSummarySerialization mergeSummarySerialization ->
                        mergeSummary == null ? "" : mergeSummary.trim();
                default -> AgentRequest.super.prettyPrint(serializationCtx);
            };
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (mergeSummary != null && !mergeSummary.isBlank()) {
                builder.append("Merge Summary: ").append(mergeSummary.trim());
            }
            if (conflictFiles != null && !conflictFiles.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Conflicting Files:\n").append(conflictFiles.trim());
            }
            if (mergeContext != null && !mergeContext.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Merge Context:\n").append(mergeContext.trim());
            }
            return builder.isEmpty() ? "Merge Request: (none)" : builder.toString();
        }
    }

    @Builder(toBuilder=true)
    @JsonClassDescription("Routing result for the merger agent.")
    record MergerRouting(
            @JsonPropertyDescription("Interrupt request for merger decisions.")
            MergerInterruptRequest interruptRequest,
            @JsonPropertyDescription("Merger agent result payload.")
            MergerAgentResult mergerResult,
            @JsonPropertyDescription("Route to orchestrator collector.")
            OrchestratorCollectorRequest orchestratorCollectorRequest,
            @JsonPropertyDescription("Route to discovery collector.")
            DiscoveryCollectorRequest discoveryCollectorRequest,
            @JsonPropertyDescription("Route to planning collector.")
            PlanningCollectorRequest planningCollectorRequest,
            @JsonPropertyDescription("Route to ticket collector.")
            TicketCollectorRequest ticketCollectorRequest
    ) implements Routing {
    }

    /**
     * Request for context orchestrator. Uses typed curation fields from collectors.
     */
    @Builder(toBuilder=true)
    record ContextOrchestratorRequest(
            ContextId contextId,
            String goal,
            String phase,
            UpstreamContext.DiscoveryCollectorContext discoveryCuration,
            UpstreamContext.PlanningCollectorContext planningCuration,
            UpstreamContext.TicketCollectorContext ticketCuration,
            PreviousContext previousContext
    ) implements AgentRequest {
        public ContextOrchestratorRequest(String goal, String phase) {
            this(null, goal, phase, null, null, null, null);
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            StringBuilder builder = new StringBuilder();
            if (goal != null && !goal.isBlank()) {
                builder.append("Goal: ").append(goal.trim());
            }
            if (phase != null && !phase.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append("Phase: ").append(phase.trim());
            }
            return builder.isEmpty() ? "Goal: (none)" : builder.toString();
        }
    }

    @Builder(toBuilder = true)
    @JsonClassDescription("Wrapper for planning agent results used in dispatch.")
    record PlanningAgentResults(
            @JsonPropertyDescription("Unique context id for this request.")
            ContextId contextId,
            @JsonPropertyDescription("Planning agent results to consolidate.")
            List<PlanningAgentResult> planningAgentResults,
            @JsonPropertyDescription("Previous planning collector context for reruns.")
            PreviousContext.PlanningCollectorPreviousContext previousContext
    ) implements AgentRequest {
        @Override
        public String prettyPrint(AgentSerializationCtx serializationCtx) {
            return switch (serializationCtx) {
                case AgentSerializationCtx.ResultsSerialization resultsSerialization ->
                        AgentModels.serializeResults(planningAgentResults);
                default -> AgentRequest.super.prettyPrint(serializationCtx);
            };
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            return "";
        }
    }

    @Builder(toBuilder = true)
    @JsonClassDescription("Wrapper for ticket agent results used in dispatch.")
    record TicketAgentResults(
            @JsonPropertyDescription("Unique context id for this request.")
            ContextId contextId,
            @JsonPropertyDescription("Ticket agent results to consolidate.")
            List<TicketAgentResult> ticketAgentResults,
            @JsonPropertyDescription("Previous ticket collector context for reruns.")
            PreviousContext.TicketCollectorPreviousContext previousContext
    ) implements AgentRequest {
        @Override
        public String prettyPrint(AgentSerializationCtx serializationCtx) {
            return switch (serializationCtx) {
                case AgentSerializationCtx.ResultsSerialization resultsSerialization ->
                        AgentModels.serializeResults(ticketAgentResults);
                default -> AgentRequest.super.prettyPrint(serializationCtx);
            };
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            return "";
        }
    }

    @Builder(toBuilder = true)
    @JsonClassDescription("Wrapper for discovery agent results used in dispatch.")
    record DiscoveryAgentResults(
            @JsonPropertyDescription("Unique context id for this request.")
            ContextId contextId,
            @JsonPropertyDescription("Discovery agent results to consolidate.")
            List<DiscoveryAgentResult> result,
            @JsonPropertyDescription("Previous discovery collector context for reruns.")
            PreviousContext.DiscoveryCollectorPreviousContext previousContext
    ) implements AgentRequest{
        @Override
        public String prettyPrint(AgentSerializationCtx serializationCtx) {
            return switch (serializationCtx) {
                case AgentSerializationCtx.ResultsSerialization resultsSerialization ->
                        AgentModels.serializeResults(result);
                default -> AgentRequest.super.prettyPrint(serializationCtx);
            };
        }

        @Override
        public String prettyPrintInterruptContinuation() {
            return "";
        }
    }

//    record ContextAgentRequest(String goal, String phase) {
//    }

//    record ContextAgentRequests(List<ContextAgentRequest> requests) {
//    }

//    record ContextCollectorRequest(String goal, String phase) {
//    }

//    record ContextOrchestratorRouting(
//            ContextOrchestratorInterruptRequest interruptRequest,
//            ContextAgentRequests agentRequests,
//            ContextCollectorRequest orchestratorCollectorResult
//    ) implements SomeOf {
//    }

//    record ContextAgentRouting(
//            ContextAgentInterruptRequest interruptRequest,
//            ContextAgentResult agentResult
//    ) implements SomeOf {
//    }

//    record ContextCollectorRouting(
//            ContextCollectorInterruptRequest interruptRequest,
//            ContextCollectorResult collectorResult,
//            OrchestratorRequest orchestratorRequest,
//            DiscoveryOrchestratorRequest discoveryRequest,
//            PlanningOrchestratorRequest planningRequest,
//            TicketOrchestratorRequest ticketRequest,
//            ContextOrchestratorRequest contextRequest,
//            ReviewRequest reviewRequest,
//            MergerRequest mergerRequest
//    ) implements SomeOf {
//    }

    //    TODO: Need to add all of the ...AgentDispatchResult, routing to their appropriate collectors
//    record ContextAgentDispatchRouting(
//            ContextAgentDispatchInterruptRequest interruptRequest,
//    ) implements SomeOf {
//    }
}
