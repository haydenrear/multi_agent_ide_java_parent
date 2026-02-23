package com.hayden.multiagentide.cli;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentPretty;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CliEventFormatter {

    private static final int MAX_FIELD_LENGTH = 160;
    private final ArtifactKeyFormatter artifactKeyFormatter;

    public CliEventFormatter(ArtifactKeyFormatter artifactKeyFormatter) {
        this.artifactKeyFormatter = artifactKeyFormatter;
    }

    public String format(Events.GraphEvent event) {
        return format(new CliEventArgs(MAX_FIELD_LENGTH, event));
    }

    public String format(CliEventArgs args) {
        if (args == null) {
            return "[EVENT] null";
        }
        Events.GraphEvent event = args.graphEvent();
        if (event == null) {
            return "[EVENT] null";
        }
        CliEventArgs normalizedArgs = normArgs(args, event);
        return switch (event) {
                case Events.ActionStartedEvent e -> formatActionStarted(normalizedArgs, e);
                case Events.ActionCompletedEvent e -> formatActionCompleted(normalizedArgs, e);
                case Events.ToolCallEvent e -> formatToolCall(normalizedArgs, e);
                case Events.NodeAddedEvent e -> formatNodeAdded(normalizedArgs, e);
                case Events.AddChildNodeEvent e -> formatAddChildNode(normalizedArgs, e);
                case Events.NodeStatusChangedEvent e -> formatNodeStatusChanged(normalizedArgs, e);
                case Events.NodeErrorEvent e -> formatNodeError(normalizedArgs, e);
                case Events.NodeBranchedEvent e -> formatNodeBranched(normalizedArgs, e);
                case Events.NodePrunedEvent e -> formatNodePruned(normalizedArgs, e);
                case Events.NodeReviewRequestedEvent e -> formatNodeReviewRequested(normalizedArgs, e);
                case Events.InterruptStatusEvent e -> formatInterruptStatus(normalizedArgs, e);
                case Events.PauseEvent e -> format(args, "INTERRUPT", e, "message=" + summarize(args, e.toAddMessage()));
                case Events.ResumeEvent e -> format(args, "INTERRUPT", e, "message=" + summarize(args, e.message()));
                case Events.ResolveInterruptEvent e -> format(args, "INTERRUPT", e, "message=" + summarize(args, e.toAddMessage()));
                case Events.StopAgentEvent e -> format(args, "ACTION", e, "node=" + e.nodeId());
                case Events.AddMessageEvent e -> format(args, "MESSAGE", e, "message=" + summarize(args, e.toAddMessage()));
                case Events.InterruptRequestEvent e -> format(args, "INTERRUPT", e,
                        "source=" + summarize(args, e.sourceAgentType())
                                + " reroute=" + summarize(args, e.rerouteToAgentType())
                                + " reason=" + summarize(args, e.reason()));
                case Events.WorktreeCreatedEvent e -> formatWorktreeCreated(normalizedArgs, e);
                case Events.WorktreeBranchedEvent e -> formatWorktreeBranched(normalizedArgs, e);
                case Events.WorktreeMergedEvent e -> formatWorktreeMerged(normalizedArgs, e);
                case Events.WorktreeDiscardedEvent e -> formatWorktreeDiscarded(normalizedArgs, e);
                case Events.NodeUpdatedEvent e -> format(args, "NODE", e, "updates=" + countOf(e.updates()));
                case Events.NodeDeletedEvent e -> format(args, "NODE", e, "reason=" + summarize(args, e.reason()));
                case Events.ChatSessionCreatedEvent e -> format(args, "CHAT", e, "nodeId=" + summarize(args, e.nodeId()));
                case Events.ChatSessionClosedEvent e -> format(args, "CHAT", e, "sessionId=" + summarize(args, e.sessionId()));
                case Events.NodeStreamDeltaEvent e -> format(args, "STREAM", e, "tokens=" + e.tokenCount() + " final=" + e.isFinal());
                case Events.NodeThoughtDeltaEvent e -> format(args, "THOUGHT", e, "tokens=" + e.tokenCount() + " final=" + e.isFinal());
                case Events.GuiRenderEvent e -> format(args, "UI", e, "sessionId=" + e.sessionId());
                case Events.UiDiffAppliedEvent e -> format(args, "UI", e, "revision=" + e.revision() + " summary=" + summarize(args, e.summary()));
                case Events.UiDiffRejectedEvent e -> format(args, "UI", e, "error=" + summarize(args, e.errorCode()) + " message=" + summarize(args, e.message()));
                case Events.UiDiffRevertedEvent e -> format(args, "UI", e, "revision=" + e.revision() + " source=" + summarize(args, e.sourceEventId()));
                case Events.UiFeedbackEvent e -> format(args, "UI", e, "message=" + summarize(args, e.message()));
                case Events.NodeBranchRequestedEvent e -> format(args, "NODE", e, "message=" + summarize(args, e.message()));
                case Events.PlanUpdateEvent e -> format(args, "PLAN", e, "entries=" + countOf(e.entries()));
                case Events.UserMessageChunkEvent e -> format(args, "MESSAGE", e, "content=" + summarize(args, e.content()));
                case Events.CurrentModeUpdateEvent e -> format(args, "MODE", e, "mode=" + summarize(args, e.currentModeId()));
                case Events.AvailableCommandsUpdateEvent e -> format(args, "MODE", e, "commands=" + countOf(e.commands()));
                case Events.PermissionRequestedEvent e -> format(args, "PERMISSION", e, "requestId=" + e.requestId() + " toolCallId=" + e.toolCallId());
                case Events.PermissionResolvedEvent e -> format(args, "PERMISSION", e, "requestId=" + e.requestId() + " outcome=" + summarize(args, e.outcome()));
                case Events.GoalCompletedEvent e -> format(args, "GOAL", e, "workflowId=" + summarize(args, e.workflowId()));
                case Events.ArtifactEvent e -> formatArtifactEvent(normalizedArgs, e);
                case Events.TuiInteractionGraphEvent e -> format(args, "TUI", e, "sessionId=" + summarize(args, e.sessionId())
                        + " event=" + summarize(args, e.tuiEvent() == null ? null : e.tuiEvent().getClass().getSimpleName()));
                case Events.TuiSystemGraphEvent e -> format(args, "TUI", e, "sessionId=" + summarize(args, e.sessionId())
                        + " event=" + summarize(args, e.tuiEvent() == null ? null : e.tuiEvent().getClass().getSimpleName()));
            };
    }

    private static @NonNull CliEventArgs normArgs(CliEventArgs args, Events.GraphEvent event) {
        int maxFieldLength = Math.max(0, args.maxFieldLength());
        CliEventArgs normalizedArgs = maxFieldLength == args.maxFieldLength()
                ? args
                : new CliEventArgs(maxFieldLength, event);
        return normalizedArgs;
    }

    public record CliEventArgs(int maxFieldLength, Events.GraphEvent graphEvent, boolean prettyPrint) {
        public CliEventArgs(int maxFieldLength, Events.GraphEvent graphEvent) {
            this(maxFieldLength, graphEvent, false);
        }
    }

    private String formatActionStarted(CliEventArgs args, Events.ActionStartedEvent event) {
        String hierarchy = artifactKeyFormatter.formatHierarchy(event.nodeId());
        String details = "agent=" + event.agentName()
                + " action=" + event.actionName()
                + " " + hierarchy;
        return format(args, "ACTION", event, details);
    }

    private String formatActionCompleted(CliEventArgs args, Events.ActionCompletedEvent event) {
        String hierarchy = artifactKeyFormatter.formatHierarchy(event.nodeId());
        String details = "agent=" + event.agentName()
                + " action=" + event.actionName()
                + " outcome=" + summarize(args, event.outcomeType())
                + " " + hierarchy;
        return format(args, "ACTION", event, details);
    }

    private String formatToolCall(CliEventArgs args, Events.ToolCallEvent event) {
        ToolCallRenderer renderer = ToolCallRendererFactory.rendererFor(event);
        if (args.prettyPrint()) {
            String header = "[TOOL] " + event.eventType() + " node=" + summarize(args, event.nodeId());
            return header + "\n" + indent(renderer.formatDetail(args, event), 1);
        }
        String details = renderer.formatSummary(args, event);
        return format(args, "TOOL", event, details);
    }

    private String formatNodeAdded(CliEventArgs args, Events.NodeAddedEvent event) {
        String details = "title=" + summarize(args, event.nodeTitle())
                + " type=" + event.nodeType()
                + " parent=" + summarize(args, event.parentNodeId());
        return format(args, "NODE", event, details);
    }

    private String formatAddChildNode(CliEventArgs args, Events.AddChildNodeEvent event) {
        String details = "child=" + summarize(args, event.nodeId())
                + " parent=" + summarize(args, event.parentNodeId());
        return format(args, "NODE", event, details);
    }

    private String formatNodeStatusChanged(CliEventArgs args, Events.NodeStatusChangedEvent event) {
        String details = "from=" + event.oldStatus() + " to=" + event.newStatus()
                + " reason=" + summarize(args, event.reason());
        return format(args, "NODE", event, details);
    }

    private String formatNodeError(CliEventArgs args, Events.NodeErrorEvent event) {
        String details = "title=" + summarize(args, event.nodeTitle())
                + " type=" + event.nodeType()
                + " message=" + summarize(args, event.message());
        return format(args, "NODE", event, details);
    }

    private String formatNodeBranched(CliEventArgs args, Events.NodeBranchedEvent event) {
        String details = "original=" + summarize(args, event.originalNodeId())
                + " branched=" + summarize(args, event.branchedNodeId())
                + " goal=" + summarize(args, event.newGoal());
        return format(args, "NODE", event, details);
    }

    private String formatNodePruned(CliEventArgs args, Events.NodePrunedEvent event) {
        String details = "reason=" + summarize(args, event.reason())
                + " worktrees=" + countOf(event.pruneWorktreeIds());
        return format(args, "NODE", event, details);
    }

    private String formatNodeReviewRequested(CliEventArgs args, Events.NodeReviewRequestedEvent event) {
        String details = "reviewNode=" + summarize(args, event.reviewNodeId())
                + " type=" + event.reviewType();
        return format(args, "REVIEW", event, details);
    }

    private String formatInterruptStatus(CliEventArgs args, Events.InterruptStatusEvent event) {
        String details = "type=" + summarize(args, event.interruptType())
                + " status=" + summarize(args, event.interruptStatus())
                + " origin=" + summarize(args, event.originNodeId());
        return format(args, "INTERRUPT", event, details);
    }

    private String formatWorktreeCreated(CliEventArgs args, Events.WorktreeCreatedEvent event) {
        String details = "worktreeId=" + summarize(args, event.worktreeId())
                + " path=" + summarize(args, event.worktreePath())
                + " type=" + summarize(args, event.worktreeType())
                + " submodule=" + summarize(args, event.submoduleName());
        return format(args, "WORKTREE", event, details);
    }

    private String formatWorktreeBranched(CliEventArgs args, Events.WorktreeBranchedEvent event) {
        String details = "branch=" + summarize(args, event.branchName())
                + " original=" + summarize(args, event.originalWorktreeId())
                + " branched=" + summarize(args, event.branchedWorktreeId());
        return format(args, "WORKTREE", event, details);
    }

    private String formatWorktreeMerged(CliEventArgs args, Events.WorktreeMergedEvent event) {
        String details = "child=" + summarize(args, event.childWorktreeId())
                + " parent=" + summarize(args, event.parentWorktreeId());
        return format(args, "WORKTREE", event, details);
    }

    private String formatWorktreeDiscarded(CliEventArgs args, Events.WorktreeDiscardedEvent event) {
        String details = "worktreeId=" + summarize(args, event.worktreeId())
                + " type=" + summarize(args, event.worktreeType());
        return format(args, "WORKTREE", event, details);
    }

    private String formatArtifactEvent(CliEventArgs args, Events.ArtifactEvent event) {

        Artifact artifact = event.artifact();
        if (artifact instanceof Artifact.AgentModelArtifact agentModelArtifact)
            return formatAgentModelArtifact(args, event, agentModelArtifact);

        String details = "type=" + summarize(args, event.artifactType())
                + " key=" + event.artifactKey()
                + " artifact=" + summarize(args, artifact == null ? null : artifact.getClass().getSimpleName());
        return format(args, "ARTIFACT", event, details);
    }

    private String formatAgentModelArtifact(CliEventArgs args, Events.ArtifactEvent event, Artifact.AgentModelArtifact artifact) {
        String details = "type=" + summarize(args, event.artifactType())
                + " key=" + event.artifactKey()
                + " " + formatAgentModel(args, artifact.agentModel());
        return format(args, "ARTIFACT", event, details);
    }

    private String formatAgentModel(CliEventArgs args, Artifact.AgentModel model) {
        if (model == null) {
            return "model=none";
        }

        if (args.prettyPrint && model instanceof AgentPretty c)
            return c.prettyPrint();

        return switch (model) {
            case AgentModels.InterruptRequest interrupt -> "interrupt=" + formatInterruptRequest(args, interrupt);
            case AgentModels.AgentRequest request -> "request=" + formatAgentRequest(args, request);
            case AgentModels.AgentResult result -> "result=" + formatAgentResult(args, result);
            case AgentPretty context -> "context=" + formatAgentContext(args, context);
            default -> "model=" + summarize(args, model.getClass().getSimpleName());
        };
    }

    private String formatAgentRequest(CliEventArgs args, AgentModels.AgentRequest request) {
        String summary = summarize(args, request.prettyPrintInterruptContinuation());
        return switch (request) {
            case AgentModels.OrchestratorRequest r ->
                    "OrchestratorRequest goal=" + summarize(args, r.goal())
                            + " phase=" + summarize(args, r.phase())
                            + " summary=" + summary;
            case AgentModels.OrchestratorCollectorRequest r ->
                    "OrchestratorCollectorRequest goal=" + summarize(args, r.goal())
                            + " phase=" + summarize(args, r.phase())
                            + " summary=" + summary;
            case AgentModels.DiscoveryOrchestratorRequest r ->
                    "DiscoveryOrchestratorRequest goal=" + summarize(args, r.goal())
                            + " summary=" + summary;
            case AgentModels.DiscoveryAgentRequests r ->
                    "DiscoveryAgentRequests goal=" + summarize(args, r.goal())
                            + " requests=" + countOf(r.requests())
                            + " summary=" + summary;
            case AgentModels.DiscoveryAgentRequest r ->
                    "DiscoveryAgentRequest goal=" + summarize(args, r.goal())
                            + " subdomain=" + summarize(args, r.subdomainFocus())
                            + " summary=" + summary;
            case AgentModels.DiscoveryCollectorRequest r ->
                    "DiscoveryCollectorRequest goal=" + summarize(args, r.goal())
                            + " summary=" + summary;
            case AgentModels.PlanningOrchestratorRequest r ->
                    "PlanningOrchestratorRequest goal=" + summarize(args, r.goal())
                            + " summary=" + summary;
            case AgentModels.PlanningAgentRequests r ->
                    "PlanningAgentRequests goal=" + summarize(args, r.goal())
                            + " requests=" + countOf(r.requests())
                            + " summary=" + summary;
            case AgentModels.PlanningAgentRequest r ->
                    "PlanningAgentRequest goal=" + summarize(args, r.goal())
                            + " summary=" + summary;
            case AgentModels.PlanningCollectorRequest r ->
                    "PlanningCollectorRequest goal=" + summarize(args, r.goal())
                            + " summary=" + summary;
            case AgentModels.TicketOrchestratorRequest r ->
                    "TicketOrchestratorRequest goal=" + summarize(args, r.goal())
                            + " summary=" + summary;
            case AgentModels.TicketAgentRequests r ->
                    "TicketAgentRequests goal=" + summarize(args, r.goal())
                            + " requests=" + countOf(r.requests())
                            + " summary=" + summary;
            case AgentModels.TicketAgentRequest r ->
                    "TicketAgentRequest ticket=" + summarize(args, r.ticketDetails())
                            + " path=" + summarize(args, r.ticketDetailsFilePath())
                            + " summary=" + summary;
            case AgentModels.TicketCollectorRequest r ->
                    "TicketCollectorRequest goal=" + summarize(args, r.goal())
                            + " summary=" + summary;
            case AgentModels.ContextManagerRoutingRequest r ->
                    "ContextManagerRoutingRequest type=" + summarize(args, r.type())
                            + " reason=" + summarize(args, r.reason())
                            + " summary=" + summary;
            case AgentModels.ContextManagerRequest r ->
                    "ContextManagerRequest type=" + summarize(args, r.type())
                            + " reason=" + summarize(args, r.reason())
                            + " goal=" + summarize(args, r.goal())
                            + " summary=" + summary;
            case AgentModels.MergerRequest r ->
                    "MergerRequest summary=" + summarize(args, r.mergeSummary())
                            + " conflicts=" + summarize(args, r.conflictFiles())
                            + " summary=" + summary;
            case AgentModels.ReviewRequest r ->
                    "ReviewRequest criteria=" + summarize(args, r.criteria())
                            + " content=" + summarize(args, r.content())
                            + " summary=" + summary;
            case AgentModels.ResultsRequest r ->
                    "ResultsRequest mergeAggregation=" + summarize(args, r.mergeAggregation())
                            + " results=" + countOf(r.childResults())
                            + " summary=" + summary;
            case AgentModels.InterruptRequest r -> "InterruptRequest summary=" + summary;
        };
    }

    private String formatAgentResult(CliEventArgs args, AgentModels.AgentResult result) {
        String summary = summarize(args, result.prettyPrint());
        return switch (result) {
            case AgentModels.OrchestratorAgentResult r -> "OrchestratorAgentResult summary=" + summary;
            case AgentModels.OrchestratorCollectorResult r ->
                    "OrchestratorCollectorResult decision=" + summarize(args, r.decision())
                            + " summary=" + summary;
            case AgentModels.DiscoveryOrchestratorResult r -> "DiscoveryOrchestratorResult summary=" + summary;
            case AgentModels.DiscoveryCollectorResult r ->
                    "DiscoveryCollectorResult decision=" + summarize(args, r.decision())
                            + " summary=" + summary;
            case AgentModels.DiscoveryAgentResult r ->
                    "DiscoveryAgentResult children=" + countOf(r.children())
                            + " summary=" + summary;
            case AgentModels.PlanningOrchestratorResult r -> "PlanningOrchestratorResult summary=" + summary;
            case AgentModels.PlanningCollectorResult r ->
                    "PlanningCollectorResult decision=" + summarize(args, r.decision())
                            + " summary=" + summary;
            case AgentModels.PlanningAgentResult r ->
                    "PlanningAgentResult children=" + countOf(r.children())
                            + " summary=" + summary;
            case AgentModels.TicketOrchestratorResult r -> "TicketOrchestratorResult summary=" + summary;
            case AgentModels.TicketCollectorResult r ->
                    "TicketCollectorResult decision=" + summarize(args, r.decision())
                            + " summary=" + summary;
            case AgentModels.TicketAgentResult r ->
                    "TicketAgentResult summary=" + summary;
            case AgentModels.ReviewAgentResult r ->
                    "ReviewAgentResult summary=" + summary;
            case AgentModels.MergerAgentResult r ->
                    "MergerAgentResult summary=" + summary;
        };
    }

    private String formatAgentContext(CliEventArgs args, AgentPretty context) {
        String summary = summarize(args, context.prettyPrint());
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

    private String formatInterruptRequest(CliEventArgs args, AgentModels.InterruptRequest request) {
        String base = "type=" + summarize(args, request.type())
                + " reason=" + summarize(args, request.reason())
                + " context=" + summarize(args, request.contextForDecision())
                + " choices=" + countOf(request.choices())
                + " confirmations=" + countOf(request.confirmationItems());
        return switch (request) {
            case AgentModels.InterruptRequest.OrchestratorInterruptRequest r ->
                    "OrchestratorInterruptRequest " + base
                            + " phase=" + summarize(args, r.phase())
                            + " goal=" + summarize(args, r.goal());
            case AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest r ->
                    "OrchestratorCollectorInterruptRequest " + base
                            + " phaseOptions=" + countOf(r.phaseOptions());
            case AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest r ->
                    "DiscoveryOrchestratorInterruptRequest " + base
                            + " scope=" + summarize(args, r.scope())
                            + " subdomains=" + countOf(r.subdomainPartitioning());
            case AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest r ->
                    "DiscoveryAgentInterruptRequest " + base
                            + " codeFindings=" + summarize(args, r.codeFindings())
                            + " files=" + countOf(r.fileReferences());
            case AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest r ->
                    "DiscoveryCollectorInterruptRequest " + base
                            + " consolidation=" + summarize(args, r.consolidationDecisions())
                            + " recommendations=" + countOf(r.recommendations());
            case AgentModels.InterruptRequest.DiscoveryAgentDispatchInterruptRequest r ->
                    "DiscoveryAgentDispatchInterruptRequest " + base
                            + " assignments=" + countOf(r.agentAssignments())
                            + " routing=" + summarize(args, r.routingRationale());
            case AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest r ->
                    "PlanningOrchestratorInterruptRequest " + base
                            + " ticketDecomposition=" + summarize(args, r.ticketDecomposition())
                            + " scope=" + summarize(args, r.planningScope());
            case AgentModels.InterruptRequest.PlanningAgentInterruptRequest r ->
                    "PlanningAgentInterruptRequest " + base
                            + " ticketDesign=" + summarize(args, r.ticketDesign())
                            + " proposedTickets=" + countOf(r.proposedTickets());
            case AgentModels.InterruptRequest.PlanningCollectorInterruptRequest r ->
                    "PlanningCollectorInterruptRequest " + base
                            + " ticketConsolidation=" + summarize(args, r.ticketConsolidation())
                            + " consolidatedTickets=" + countOf(r.consolidatedTickets());
            case AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest r ->
                    "PlanningAgentDispatchInterruptRequest " + base
                            + " assignments=" + countOf(r.agentAssignments())
                            + " routing=" + summarize(args, r.routingRationale());
            case AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest r ->
                    "TicketOrchestratorInterruptRequest " + base
                            + " scope=" + summarize(args, r.implementationScope())
                            + " strategy=" + summarize(args, r.executionStrategy());
            case AgentModels.InterruptRequest.TicketAgentInterruptRequest r ->
                    "TicketAgentInterruptRequest " + base
                            + " approach=" + summarize(args, r.implementationApproach())
                            + " files=" + countOf(r.filesToModify());
            case AgentModels.InterruptRequest.TicketCollectorInterruptRequest r ->
                    "TicketCollectorInterruptRequest " + base
                            + " status=" + summarize(args, r.completionStatus())
                            + " followUps=" + countOf(r.followUps());
            case AgentModels.InterruptRequest.TicketAgentDispatchInterruptRequest r ->
                    "TicketAgentDispatchInterruptRequest " + base
                            + " assignments=" + countOf(r.agentAssignments())
                            + " routing=" + summarize(args, r.routingRationale());
            case AgentModels.InterruptRequest.ReviewInterruptRequest r ->
                    "ReviewInterruptRequest " + base
                            + " criteria=" + summarize(args, r.reviewCriteria())
                            + " recommendation=" + summarize(args, r.approvalRecommendation());
            case AgentModels.InterruptRequest.MergerInterruptRequest r ->
                    "MergerInterruptRequest " + base
                            + " conflictFiles=" + countOf(r.conflictFiles())
                            + " mergeApproach=" + summarize(args, r.mergeApproach());
            case AgentModels.InterruptRequest.ContextManagerInterruptRequest r ->
                    "ContextManagerInterruptRequest " + base
                            + " findings=" + summarize(args, r.contextFindings())
                            + " sources=" + countOf(r.sourceReferences());
            case AgentModels.InterruptRequest.QuestionAnswerInterruptRequest r ->
                    "QuestionAnswerInterruptRequest " + base;
        };
    }

    private String format(CliEventArgs args, String category, Events.GraphEvent event, String details) {
        String prefix = "[" + category + "]";
        String header = prefix + " " + event.eventType() + " node=" + summarize(args, event.nodeId());
        String payload = serializeEvent(args, event);
        if (args.prettyPrint()) {
            return header
                    + "\n\tDetails: " + details
                    + "\n\tPayload:\n"
                    + indent(payload, 2);
        }
        return header
                + " " + details
                + " payload=" + summarize(args, payload.replace('\n', ' '));
    }

    private String serializeEvent(CliEventArgs args, Events.GraphEvent event) {
        String pretty = event.prettyPrint();
        if (pretty == null || pretty.isBlank()) {
            return summarize(args, event.eventType());
        }
        return pretty;
    }

    private String summarize(CliEventArgs args, Object value) {
        if (value == null) {
            return "none";
        }
        String text = String.valueOf(value);
        int maxFieldLength = Math.max(0, args.maxFieldLength());
        if (text.length() <= maxFieldLength) {
            return text;
        }
        return text.substring(0, maxFieldLength) + "...";
    }

    private String indent(String text, int depth) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String prefix = "\t".repeat(Math.max(0, depth));
        return text.replace("\r\n", "\n")
                .replace("\r", "\n")
                .lines()
                .map(line -> prefix + line)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private int countOf(List<?> items) {
        return items == null ? 0 : items.size();
    }

    private int countOf(Map<?, ?> items) {
        return items == null ? 0 : items.size();
    }
}
