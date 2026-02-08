package com.hayden.multiagentide.cli;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.agent.AgentContext;
import com.hayden.multiagentidelib.agent.AgentModels;

import java.util.List;
import java.util.Map;

public class CliEventFormatter {

    private static final int MAX_FIELD_LENGTH = 160;

    private final ArtifactKeyFormatter artifactKeyFormatter;

    public CliEventFormatter(ArtifactKeyFormatter artifactKeyFormatter) {
        this.artifactKeyFormatter = artifactKeyFormatter;
    }

    public String format(Events.GraphEvent event) {
        if (event == null) {
            return "[EVENT] null";
        }
        return switch (event) {
            case Events.ActionStartedEvent e -> formatActionStarted(e);
            case Events.ActionCompletedEvent e -> formatActionCompleted(e);
            case Events.ToolCallEvent e -> formatToolCall(e);
            case Events.NodeAddedEvent e -> formatNodeAdded(e);
            case Events.NodeStatusChangedEvent e -> formatNodeStatusChanged(e);
            case Events.NodeErrorEvent e -> formatNodeError(e);
            case Events.NodeBranchedEvent e -> formatNodeBranched(e);
            case Events.NodePrunedEvent e -> formatNodePruned(e);
            case Events.NodeReviewRequestedEvent e -> formatNodeReviewRequested(e);
            case Events.InterruptStatusEvent e -> formatInterruptStatus(e);
            case Events.PauseEvent e -> format("INTERRUPT", e, "message=" + summarize(e.toAddMessage()));
            case Events.ResumeEvent e -> format("INTERRUPT", e, "message=" + summarize(e.message()));
            case Events.ResolveInterruptEvent e -> format("INTERRUPT", e, "message=" + summarize(e.toAddMessage()));
            case Events.StopAgentEvent e -> format("ACTION", e, "node=" + e.nodeId());
            case Events.AddMessageEvent e -> format("MESSAGE", e, "message=" + summarize(e.toAddMessage()));
            case Events.WorktreeCreatedEvent e -> formatWorktreeCreated(e);
            case Events.WorktreeBranchedEvent e -> formatWorktreeBranched(e);
            case Events.WorktreeMergedEvent e -> formatWorktreeMerged(e);
            case Events.WorktreeDiscardedEvent e -> formatWorktreeDiscarded(e);
            case Events.NodeUpdatedEvent e -> format("NODE", e, "updates=" + countOf(e.updates()));
            case Events.NodeDeletedEvent e -> format("NODE", e, "reason=" + summarize(e.reason()));
            case Events.ChatSessionCreatedEvent e -> format("CHAT", e, "nodeId=" + summarize(e.nodeId()));
            case Events.ChatSessionClosedEvent e -> format("CHAT", e, "sessionId=" + summarize(e.sessionId()));
            case Events.NodeStreamDeltaEvent e -> format("STREAM", e, "tokens=" + e.tokenCount() + " final=" + e.isFinal());
            case Events.NodeThoughtDeltaEvent e -> format("THOUGHT", e, "tokens=" + e.tokenCount() + " final=" + e.isFinal());
            case Events.GuiRenderEvent e -> format("UI", e, "sessionId=" + e.sessionId());
            case Events.UiDiffAppliedEvent e -> format("UI", e, "revision=" + e.revision() + " summary=" + summarize(e.summary()));
            case Events.UiDiffRejectedEvent e -> format("UI", e, "error=" + summarize(e.errorCode()) + " message=" + summarize(e.message()));
            case Events.UiDiffRevertedEvent e -> format("UI", e, "revision=" + e.revision() + " source=" + summarize(e.sourceEventId()));
            case Events.UiFeedbackEvent e -> format("UI", e, "message=" + summarize(e.message()));
            case Events.NodeBranchRequestedEvent e -> format("NODE", e, "message=" + summarize(e.message()));
            case Events.PlanUpdateEvent e -> format("PLAN", e, "entries=" + countOf(e.entries()));
            case Events.UserMessageChunkEvent e -> format("MESSAGE", e, "content=" + summarize(e.content()));
            case Events.CurrentModeUpdateEvent e -> format("MODE", e, "mode=" + summarize(e.currentModeId()));
            case Events.AvailableCommandsUpdateEvent e -> format("MODE", e, "commands=" + countOf(e.commands()));
            case Events.PermissionRequestedEvent e -> format("PERMISSION", e, "requestId=" + e.requestId() + " toolCallId=" + e.toolCallId());
            case Events.PermissionResolvedEvent e -> format("PERMISSION", e, "requestId=" + e.requestId() + " outcome=" + summarize(e.outcome()));
            case Events.GoalCompletedEvent e -> format("GOAL", e, "workflowId=" + summarize(e.workflowId()));
            case Events.ArtifactEvent e -> formatArtifactEvent(e);
            case Events.TuiInteractionGraphEvent e -> format("TUI", e, "sessionId=" + summarize(e.sessionId())
                    + " event=" + summarize(e.tuiEvent() == null ? null : e.tuiEvent().getClass().getSimpleName()));
            case Events.TuiSystemGraphEvent e -> format("TUI", e, "sessionId=" + summarize(e.sessionId())
                    + " event=" + summarize(e.tuiEvent() == null ? null : e.tuiEvent().getClass().getSimpleName()));
        };
    }

    private String formatActionStarted(Events.ActionStartedEvent event) {
        String hierarchy = artifactKeyFormatter.formatHierarchy(event.nodeId());
        String details = "agent=" + event.agentName()
                + " action=" + event.actionName()
                + " " + hierarchy;
        return format("ACTION", event, details);
    }

    private String formatActionCompleted(Events.ActionCompletedEvent event) {
        String hierarchy = artifactKeyFormatter.formatHierarchy(event.nodeId());
        String details = "agent=" + event.agentName()
                + " action=" + event.actionName()
                + " outcome=" + summarize(event.outcomeType())
                + " " + hierarchy;
        return format("ACTION", event, details);
    }

    private String formatToolCall(Events.ToolCallEvent event) {
        String details = "tool=" + summarize(event.title())
                + " kind=" + summarize(event.kind())
                + " status=" + summarize(event.status())
                + " phase=" + summarize(event.phase())
                + " input=" + summarize(event.rawInput())
                + " output=" + summarize(event.rawOutput());
        return format("TOOL", event, details);
    }

    private String formatNodeAdded(Events.NodeAddedEvent event) {
        String details = "title=" + summarize(event.nodeTitle())
                + " type=" + event.nodeType()
                + " parent=" + summarize(event.parentNodeId());
        return format("NODE", event, details);
    }

    private String formatNodeStatusChanged(Events.NodeStatusChangedEvent event) {
        String details = "from=" + event.oldStatus() + " to=" + event.newStatus()
                + " reason=" + summarize(event.reason());
        return format("NODE", event, details);
    }

    private String formatNodeError(Events.NodeErrorEvent event) {
        String details = "title=" + summarize(event.nodeTitle())
                + " type=" + event.nodeType()
                + " message=" + summarize(event.message());
        return format("NODE", event, details);
    }

    private String formatNodeBranched(Events.NodeBranchedEvent event) {
        String details = "original=" + summarize(event.originalNodeId())
                + " branched=" + summarize(event.branchedNodeId())
                + " goal=" + summarize(event.newGoal());
        return format("NODE", event, details);
    }

    private String formatNodePruned(Events.NodePrunedEvent event) {
        String details = "reason=" + summarize(event.reason())
                + " worktrees=" + countOf(event.pruneWorktreeIds());
        return format("NODE", event, details);
    }

    private String formatNodeReviewRequested(Events.NodeReviewRequestedEvent event) {
        String details = "reviewNode=" + summarize(event.reviewNodeId())
                + " type=" + event.reviewType();
        return format("REVIEW", event, details);
    }

    private String formatInterruptStatus(Events.InterruptStatusEvent event) {
        String details = "type=" + summarize(event.interruptType())
                + " status=" + summarize(event.interruptStatus())
                + " origin=" + summarize(event.originNodeId());
        return format("INTERRUPT", event, details);
    }

    private String formatWorktreeCreated(Events.WorktreeCreatedEvent event) {
        String details = "worktreeId=" + summarize(event.worktreeId())
                + " path=" + summarize(event.worktreePath())
                + " type=" + summarize(event.worktreeType())
                + " submodule=" + summarize(event.submoduleName());
        return format("WORKTREE", event, details);
    }

    private String formatWorktreeBranched(Events.WorktreeBranchedEvent event) {
        String details = "branch=" + summarize(event.branchName())
                + " original=" + summarize(event.originalWorktreeId())
                + " branched=" + summarize(event.branchedWorktreeId());
        return format("WORKTREE", event, details);
    }

    private String formatWorktreeMerged(Events.WorktreeMergedEvent event) {
        String details = "child=" + summarize(event.childWorktreeId())
                + " parent=" + summarize(event.parentWorktreeId());
        return format("WORKTREE", event, details);
    }

    private String formatWorktreeDiscarded(Events.WorktreeDiscardedEvent event) {
        String details = "worktreeId=" + summarize(event.worktreeId())
                + " type=" + summarize(event.worktreeType());
        return format("WORKTREE", event, details);
    }

    private String formatArtifactEvent(Events.ArtifactEvent event) {
        Artifact artifact = event.artifact();
        if (artifact instanceof Artifact.AgentModelArtifact agentModelArtifact) {
            return formatAgentModelArtifact(event, agentModelArtifact);
        }
        String details = "type=" + summarize(event.artifactType())
                + " key=" + event.artifactKey()
                + " artifact=" + summarize(artifact == null ? null : artifact.getClass().getSimpleName());
        return format("ARTIFACT", event, details);
    }

    private String formatAgentModelArtifact(Events.ArtifactEvent event, Artifact.AgentModelArtifact artifact) {
        String details = "type=" + summarize(event.artifactType())
                + " key=" + event.artifactKey()
                + " " + formatAgentModel(artifact.agentModel());
        return format("ARTIFACT", event, details);
    }

    private String formatAgentModel(Artifact.AgentModel model) {
        if (model == null) {
            return "model=none";
        }
        return switch (model) {
            case AgentModels.InterruptRequest interrupt -> "interrupt=" + formatInterruptRequest(interrupt);
            case AgentModels.AgentRequest request -> "request=" + formatAgentRequest(request);
            case AgentModels.AgentResult result -> "result=" + formatAgentResult(result);
            case AgentContext context -> "context=" + formatAgentContext(context);
            default -> "model=" + summarize(model.getClass().getSimpleName());
        };
    }

    private String formatAgentRequest(AgentModels.AgentRequest request) {
        String summary = summarize(request.prettyPrintInterruptContinuation());
        return switch (request) {
            case AgentModels.OrchestratorRequest r ->
                    "OrchestratorRequest goal=" + summarize(r.goal())
                            + " phase=" + summarize(r.phase())
                            + " summary=" + summary;
            case AgentModels.OrchestratorCollectorRequest r ->
                    "OrchestratorCollectorRequest goal=" + summarize(r.goal())
                            + " phase=" + summarize(r.phase())
                            + " summary=" + summary;
            case AgentModels.DiscoveryOrchestratorRequest r ->
                    "DiscoveryOrchestratorRequest goal=" + summarize(r.goal())
                            + " summary=" + summary;
            case AgentModels.DiscoveryAgentRequests r ->
                    "DiscoveryAgentRequests goal=" + summarize(r.goal())
                            + " requests=" + countOf(r.requests())
                            + " summary=" + summary;
            case AgentModels.DiscoveryAgentRequest r ->
                    "DiscoveryAgentRequest goal=" + summarize(r.goal())
                            + " subdomain=" + summarize(r.subdomainFocus())
                            + " summary=" + summary;
            case AgentModels.DiscoveryCollectorRequest r ->
                    "DiscoveryCollectorRequest goal=" + summarize(r.goal())
                            + " summary=" + summary;
            case AgentModels.PlanningOrchestratorRequest r ->
                    "PlanningOrchestratorRequest goal=" + summarize(r.goal())
                            + " summary=" + summary;
            case AgentModels.PlanningAgentRequests r ->
                    "PlanningAgentRequests goal=" + summarize(r.goal())
                            + " requests=" + countOf(r.requests())
                            + " summary=" + summary;
            case AgentModels.PlanningAgentRequest r ->
                    "PlanningAgentRequest goal=" + summarize(r.goal())
                            + " summary=" + summary;
            case AgentModels.PlanningCollectorRequest r ->
                    "PlanningCollectorRequest goal=" + summarize(r.goal())
                            + " summary=" + summary;
            case AgentModels.TicketOrchestratorRequest r ->
                    "TicketOrchestratorRequest goal=" + summarize(r.goal())
                            + " summary=" + summary;
            case AgentModels.TicketAgentRequests r ->
                    "TicketAgentRequests goal=" + summarize(r.goal())
                            + " requests=" + countOf(r.requests())
                            + " summary=" + summary;
            case AgentModels.TicketAgentRequest r ->
                    "TicketAgentRequest ticket=" + summarize(r.ticketDetails())
                            + " path=" + summarize(r.ticketDetailsFilePath())
                            + " summary=" + summary;
            case AgentModels.TicketCollectorRequest r ->
                    "TicketCollectorRequest goal=" + summarize(r.goal())
                            + " summary=" + summary;
            case AgentModels.ContextManagerRoutingRequest r ->
                    "ContextManagerRoutingRequest type=" + summarize(r.type())
                            + " reason=" + summarize(r.reason())
                            + " summary=" + summary;
            case AgentModels.ContextManagerRequest r ->
                    "ContextManagerRequest type=" + summarize(r.type())
                            + " reason=" + summarize(r.reason())
                            + " goal=" + summarize(r.goal())
                            + " summary=" + summary;
            case AgentModels.MergerRequest r ->
                    "MergerRequest summary=" + summarize(r.mergeSummary())
                            + " conflicts=" + summarize(r.conflictFiles())
                            + " summary=" + summary;
            case AgentModels.ReviewRequest r ->
                    "ReviewRequest criteria=" + summarize(r.criteria())
                            + " content=" + summarize(r.content())
                            + " summary=" + summary;
            case AgentModels.ResultsRequest r ->
                    "ResultsRequest mergeAggregation=" + summarize(r.mergeAggregation())
                            + " results=" + countOf(r.childResults())
                            + " summary=" + summary;
            case AgentModels.InterruptRequest r -> "InterruptRequest summary=" + summary;
        };
    }

    private String formatAgentResult(AgentModels.AgentResult result) {
        String summary = summarize(result.prettyPrint());
        return switch (result) {
            case AgentModels.OrchestratorAgentResult r -> "OrchestratorAgentResult summary=" + summary;
            case AgentModels.OrchestratorCollectorResult r ->
                    "OrchestratorCollectorResult decision=" + summarize(r.decision())
                            + " summary=" + summary;
            case AgentModels.DiscoveryOrchestratorResult r -> "DiscoveryOrchestratorResult summary=" + summary;
            case AgentModels.DiscoveryCollectorResult r ->
                    "DiscoveryCollectorResult decision=" + summarize(r.decision())
                            + " summary=" + summary;
            case AgentModels.DiscoveryAgentResult r ->
                    "DiscoveryAgentResult children=" + countOf(r.children())
                            + " summary=" + summary;
            case AgentModels.PlanningOrchestratorResult r -> "PlanningOrchestratorResult summary=" + summary;
            case AgentModels.PlanningCollectorResult r ->
                    "PlanningCollectorResult decision=" + summarize(r.decision())
                            + " summary=" + summary;
            case AgentModels.PlanningAgentResult r ->
                    "PlanningAgentResult children=" + countOf(r.children())
                            + " summary=" + summary;
            case AgentModels.TicketOrchestratorResult r -> "TicketOrchestratorResult summary=" + summary;
            case AgentModels.TicketCollectorResult r ->
                    "TicketCollectorResult decision=" + summarize(r.decision())
                            + " summary=" + summary;
            case AgentModels.TicketAgentResult r ->
                    "TicketAgentResult summary=" + summary;
            case AgentModels.ReviewAgentResult r ->
                    "ReviewAgentResult summary=" + summary;
            case AgentModels.MergerAgentResult r ->
                    "MergerAgentResult summary=" + summary;
        };
    }

    private String formatAgentContext(AgentContext context) {
        String summary = summarize(context.prettyPrint());
        return switch (context) {
            case AgentModels.DiscoveryCuration c ->
                    "DiscoveryCuration reports=" + countOf(c.discoveryReports())
                            + " recommendations=" + countOf(c.recommendations())
                            + " summary=" + summary;
            case AgentModels.PlanningCuration c ->
                    "PlanningCuration tickets=" + countOf(c.finalizedTickets())
                            + " results=" + countOf(c.planningAgentResults())
                            + " summary=" + summary;
            case AgentModels.TicketCuration c ->
                    "TicketCuration results=" + countOf(c.ticketAgentResults())
                            + " followUps=" + countOf(c.followUps())
                            + " summary=" + summary;
            default -> """
                    %s
                    Context summary=%s
                    """.formatted(context.getClass().getSimpleName(), summary);
        };
    }

    private String formatInterruptRequest(AgentModels.InterruptRequest request) {
        String base = "type=" + summarize(request.type())
                + " reason=" + summarize(request.reason())
                + " context=" + summarize(request.contextForDecision())
                + " choices=" + countOf(request.choices())
                + " confirmations=" + countOf(request.confirmationItems());
        return switch (request) {
            case AgentModels.InterruptRequest.OrchestratorInterruptRequest r ->
                    "OrchestratorInterruptRequest " + base
                            + " phase=" + summarize(r.phase())
                            + " goal=" + summarize(r.goal());
            case AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest r ->
                    "OrchestratorCollectorInterruptRequest " + base
                            + " phaseDecision=" + summarize(r.phaseDecision())
                            + " phaseOptions=" + countOf(r.phaseOptions());
            case AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest r ->
                    "DiscoveryOrchestratorInterruptRequest " + base
                            + " scope=" + summarize(r.scope())
                            + " subdomains=" + countOf(r.subdomainPartitioning());
            case AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest r ->
                    "DiscoveryAgentInterruptRequest " + base
                            + " codeFindings=" + summarize(r.codeFindings())
                            + " files=" + countOf(r.fileReferences());
            case AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest r ->
                    "DiscoveryCollectorInterruptRequest " + base
                            + " consolidation=" + summarize(r.consolidationDecisions())
                            + " recommendations=" + countOf(r.recommendations());
            case AgentModels.InterruptRequest.DiscoveryAgentDispatchInterruptRequest r ->
                    "DiscoveryAgentDispatchInterruptRequest " + base
                            + " assignments=" + countOf(r.agentAssignments())
                            + " routing=" + summarize(r.routingRationale());
            case AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest r ->
                    "PlanningOrchestratorInterruptRequest " + base
                            + " ticketDecomposition=" + summarize(r.ticketDecomposition())
                            + " scope=" + summarize(r.planningScope());
            case AgentModels.InterruptRequest.PlanningAgentInterruptRequest r ->
                    "PlanningAgentInterruptRequest " + base
                            + " ticketDesign=" + summarize(r.ticketDesign())
                            + " proposedTickets=" + countOf(r.proposedTickets());
            case AgentModels.InterruptRequest.PlanningCollectorInterruptRequest r ->
                    "PlanningCollectorInterruptRequest " + base
                            + " ticketConsolidation=" + summarize(r.ticketConsolidation())
                            + " consolidatedTickets=" + countOf(r.consolidatedTickets());
            case AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest r ->
                    "PlanningAgentDispatchInterruptRequest " + base
                            + " assignments=" + countOf(r.agentAssignments())
                            + " routing=" + summarize(r.routingRationale());
            case AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest r ->
                    "TicketOrchestratorInterruptRequest " + base
                            + " scope=" + summarize(r.implementationScope())
                            + " strategy=" + summarize(r.executionStrategy());
            case AgentModels.InterruptRequest.TicketAgentInterruptRequest r ->
                    "TicketAgentInterruptRequest " + base
                            + " approach=" + summarize(r.implementationApproach())
                            + " files=" + countOf(r.filesToModify());
            case AgentModels.InterruptRequest.TicketCollectorInterruptRequest r ->
                    "TicketCollectorInterruptRequest " + base
                            + " status=" + summarize(r.completionStatus())
                            + " followUps=" + countOf(r.followUps());
            case AgentModels.InterruptRequest.TicketAgentDispatchInterruptRequest r ->
                    "TicketAgentDispatchInterruptRequest " + base
                            + " assignments=" + countOf(r.agentAssignments())
                            + " routing=" + summarize(r.routingRationale());
            case AgentModels.InterruptRequest.ReviewInterruptRequest r ->
                    "ReviewInterruptRequest " + base
                            + " criteria=" + summarize(r.reviewCriteria())
                            + " recommendation=" + summarize(r.approvalRecommendation());
            case AgentModels.InterruptRequest.MergerInterruptRequest r ->
                    "MergerInterruptRequest " + base
                            + " conflictFiles=" + countOf(r.conflictFiles())
                            + " mergeApproach=" + summarize(r.mergeApproach());
            case AgentModels.InterruptRequest.ContextManagerInterruptRequest r ->
                    "ContextManagerInterruptRequest " + base
                            + " findings=" + summarize(r.contextFindings())
                            + " sources=" + countOf(r.sourceReferences());
            case AgentModels.InterruptRequest.QuestionAnswerInterruptRequest r ->
                    "QuestionAnswerInterruptRequest " + base;
        };
    }

    private String formatFallback(Events.GraphEvent event) {
        String details = "node=" + summarize(event.nodeId());
        return format("EVENT", event, details);
    }

    private String format(String category, Events.GraphEvent event, String details) {
        String prefix = "[" + category + "]";
        return prefix + " " + event.eventType() + " node=" + summarize(event.nodeId())
                + " " + details;
    }

    private String summarize(Object value) {
        if (value == null) {
            return "none";
        }
        String text = String.valueOf(value);
        if (text.length() <= MAX_FIELD_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_FIELD_LENGTH) + "...";
    }

    private int countOf(List<?> items) {
        return items == null ? 0 : items.size();
    }

    private int countOf(Map<?, ?> items) {
        return items == null ? 0 : items.size();
    }
}
