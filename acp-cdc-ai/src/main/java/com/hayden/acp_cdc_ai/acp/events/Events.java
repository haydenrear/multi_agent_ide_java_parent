package com.hayden.acp_cdc_ai.acp.events;

import com.agui.core.types.BaseEvent;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface Events {

    Logger log = LoggerFactory.getLogger(Events.class);

    static BaseEvent mapToEvent(GraphEvent toMap) {
        if (toMap == null) {
            log.warn("Skipping null GraphEvent mapping.");
            return null;
        }
        return AgUiEventMappingRegistry.map(toMap);
    }

    enum ReviewType {
        AGENT, HUMAN;

        public InterruptType toInterruptType() {
            return this == AGENT ? InterruptType.AGENT_REVIEW : InterruptType.HUMAN_REVIEW;
        }
    }

    /**
     * Node status values.
     */
    enum NodeStatus {
        PENDING,           // Not yet ready
        READY,             // Ready to execute
        RUNNING,           // Currently executing
        WAITING_REVIEW,    // Awaiting human/agent review
        WAITING_INPUT,     // Awaiting user input
        COMPLETED,         // Successfully completed
        FAILED,            // Execution failed
        CANCELED,          // Manually canceled
        PRUNED,            // Removed from graph
    }

    /**
     * Node type for classification.
     */
    enum NodeType {
        ORCHESTRATOR,
        PLANNING,
        WORK,
        HUMAN_REVIEW,
        AGENT_REVIEW,
        SUMMARY,
        INTERRUPT,
        PERMISSION
    }

    enum InterruptType {
        HUMAN_REVIEW,
        AGENT_REVIEW,
        PAUSE,
        STOP,
        BRANCH,
        PRUNE
    }

    @JsonClassDescription("""
            Decision type (ADVANCE_PHASE, ROUTE_BACK, STOP).
            ADVANCE_PHASE: advance to the next orchestrator.
                Discovery -> Planning
                Planning -> Ticket
                Ticket -> OrchestratorCollector (finish)
            ROUTE_BACK: route back to the orchestrator
                DiscoveryCollector -> DiscoveryOrchestrator
                PlanningCollector -> PlanningOrchestrator
                TicketCollector -> TicketOrchestrator
            STOP: route to the OrchestratorCollector, no matter what stage of execution
            """)
    enum CollectorDecisionType {
        ROUTE_BACK,
        ADVANCE_PHASE,
        STOP
    }

    /**
     * Base interface for all graph and worktree events.
     * Sealed to restrict implementations.
     */
    sealed interface GraphEvent {
        /**
         * Unique event ID.
         */
        String eventId();

        String nodeId();

        /**
         * Timestamp when event was created.
         */
        Instant timestamp();

        /**
         * Type of event for classification.
         */
        String eventType();

        default String prettyPrint() {
            return switch (this) {
                case NodeAddedEvent e -> formatEvent("Node Added Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("nodeTitle", e.nodeTitle()),
                        line("nodeType", e.nodeType()),
                        line("parentNodeId", e.parentNodeId()));
                case ActionStartedEvent e -> formatEvent("Action Started Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("agentName", e.agentName()),
                        line("actionName", e.actionName()));
                case ActionCompletedEvent e -> formatEvent("Action Completed Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("agentName", e.agentName()),
                        line("actionName", e.actionName()),
                        line("outcomeType", e.outcomeType()),
                        line("agentModel", summarizeObject(e.agentModel())));
                case StopAgentEvent e -> formatEvent("Stop Agent Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()));
                case PauseEvent e -> formatEvent("Pause Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("toAddMessage", e.toAddMessage()));
                case ResumeEvent e -> formatEvent("Resume Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("message", e.message()));
                case ResolveInterruptEvent e -> formatEvent("Resolve Interrupt Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("toAddMessage", e.toAddMessage()));
                case AddMessageEvent e -> formatEvent("Add Message Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("toAddMessage", e.toAddMessage()));
                case NodeStatusChangedEvent e -> formatEvent("Node Status Changed Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("oldStatus", e.oldStatus()),
                        line("newStatus", e.newStatus()),
                        block("reason", e.reason()));
                case NodeErrorEvent e -> formatEvent("Node Error Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("nodeTitle", e.nodeTitle()),
                        line("nodeType", e.nodeType()),
                        block("message", e.message()));
                case NodeBranchedEvent e -> formatEvent("Node Branched Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("originalNodeId", e.originalNodeId()),
                        line("branchedNodeId", e.branchedNodeId()),
                        block("newGoal", e.newGoal()),
                        line("mainWorktreeId", e.mainWorktreeId()),
                        list("submoduleWorktreeIds", e.submoduleWorktreeIds()));
                case NodePrunedEvent e -> formatEvent("Node Pruned Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("reason", e.reason()),
                        list("pruneWorktreeIds", e.pruneWorktreeIds()));
                case NodeReviewRequestedEvent e -> formatEvent("Node Review Requested Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("reviewNodeId", e.reviewNodeId()),
                        line("reviewType", e.reviewType()),
                        block("contentToReview", e.contentToReview()));
                case InterruptStatusEvent e -> formatEvent("Interrupt Status Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("interruptType", e.interruptType()),
                        line("interruptStatus", e.interruptStatus()),
                        line("originNodeId", e.originNodeId()),
                        line("resumeNodeId", e.resumeNodeId()));
                case GoalCompletedEvent e -> formatEvent("Goal Completed Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("orchestratorNodeId", e.orchestratorNodeId()),
                        line("workflowId", e.workflowId()),
                        line("model", summarizeObject(e.model())));
                case WorktreeCreatedEvent e -> formatEvent("Worktree Created Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("worktreeId", e.worktreeId()),
                        line("associatedNodeId", e.associatedNodeId()),
                        line("worktreePath", e.worktreePath()),
                        line("worktreeType", e.worktreeType()),
                        line("submoduleName", e.submoduleName()));
                case WorktreeBranchedEvent e -> formatEvent("Worktree Branched Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("originalWorktreeId", e.originalWorktreeId()),
                        line("branchedWorktreeId", e.branchedWorktreeId()),
                        line("branchName", e.branchName()),
                        line("worktreeType", e.worktreeType()),
                        line("nodeId", e.nodeId()));
                case WorktreeMergedEvent e -> formatEvent("Worktree Merged Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("childWorktreeId", e.childWorktreeId()),
                        line("parentWorktreeId", e.parentWorktreeId()),
                        line("mergeCommitHash", e.mergeCommitHash()),
                        line("conflictDetected", e.conflictDetected()),
                        list("conflictFiles", e.conflictFiles()),
                        line("worktreeType", e.worktreeType()),
                        line("nodeId", e.nodeId()));
                case WorktreeDiscardedEvent e -> formatEvent("Worktree Discarded Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("worktreeId", e.worktreeId()),
                        block("reason", e.reason()),
                        line("worktreeType", e.worktreeType()),
                        line("nodeId", e.nodeId()));
                case NodeUpdatedEvent e -> formatEvent("Node Updated Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        map("updates", e.updates()));
                case NodeDeletedEvent e -> formatEvent("Node Deleted Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("reason", e.reason()));
                case ChatSessionCreatedEvent e -> formatEvent("Chat Session Created Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()));
                case ChatSessionClosedEvent e -> formatEvent("Chat Session Closed Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("sessionId", e.sessionId()));
                case NodeStreamDeltaEvent e -> formatEvent("Node Stream Delta Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("tokenCount", e.tokenCount()),
                        line("isFinal", e.isFinal()),
                        block("deltaContent", e.deltaContent()));
                case NodeThoughtDeltaEvent e -> formatEvent("Node Thought Delta Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("tokenCount", e.tokenCount()),
                        line("isFinal", e.isFinal()),
                        block("deltaContent", e.deltaContent()));
                case ToolCallEvent e -> formatEvent("Tool Call Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("toolCallId", e.toolCallId()),
                        line("title", e.title()),
                        line("kind", e.kind()),
                        line("status", e.status()),
                        line("phase", e.phase()),
                        list("content", e.content()),
                        list("locations", e.locations()),
                        line("rawInput", summarizeObject(e.rawInput())),
                        line("rawOutput", summarizeObject(e.rawOutput())));
                case GuiRenderEvent e -> formatEvent("GUI Render Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionId", e.sessionId()),
                        line("payload", summarizeObject(e.payload())));
                case UiDiffAppliedEvent e -> formatEvent("UI Diff Applied Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionId", e.sessionId()),
                        line("revision", e.revision()),
                        line("renderTree", summarizeObject(e.renderTree())),
                        block("summary", e.summary()));
                case UiDiffRejectedEvent e -> formatEvent("UI Diff Rejected Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionId", e.sessionId()),
                        line("errorCode", e.errorCode()),
                        block("message", e.message()));
                case UiDiffRevertedEvent e -> formatEvent("UI Diff Reverted Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionId", e.sessionId()),
                        line("revision", e.revision()),
                        line("renderTree", summarizeObject(e.renderTree())),
                        line("sourceEventId", e.sourceEventId()));
                case UiFeedbackEvent e -> formatEvent("UI Feedback Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionId", e.sessionId()),
                        line("sourceEventId", e.sourceEventId()),
                        block("message", e.message()),
                        line("snapshot", summarizeObject(e.snapshot())));
                case NodeBranchRequestedEvent e -> formatEvent("Node Branch Requested Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("message", e.message()));
                case PlanUpdateEvent e -> formatEvent("Plan Update Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        list("entries", e.entries()));
                case UserMessageChunkEvent e -> formatEvent("User Message Chunk Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("content", e.content()));
                case CurrentModeUpdateEvent e -> formatEvent("Current Mode Update Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("currentModeId", e.currentModeId()));
                case AvailableCommandsUpdateEvent e -> formatEvent("Available Commands Update Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        list("commands", e.commands()));
                case PermissionRequestedEvent e -> formatEvent("Permission Requested Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("originNodeId", e.originNodeId()),
                        line("requestId", e.requestId()),
                        line("toolCallId", e.toolCallId()),
                        line("permissions", summarizeObject(e.permissions())));
                case PermissionResolvedEvent e -> formatEvent("Permission Resolved Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("originNodeId", e.originNodeId()),
                        line("requestId", e.requestId()),
                        line("toolCallId", e.toolCallId()),
                        line("outcome", e.outcome()),
                        line("selectedOptionId", e.selectedOptionId()));
                case TuiInteractionGraphEvent e -> formatEvent("TUI Interaction Graph Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionId", e.sessionId()),
                        line("tuiEvent", summarizeObject(e.tuiEvent())));
                case TuiSystemGraphEvent e -> formatEvent("TUI System Graph Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionId", e.sessionId()),
                        line("tuiEvent", summarizeObject(e.tuiEvent())));
                case ArtifactEvent e -> formatEvent("Artifact Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("artifactType", e.artifactType()),
                        line("parentArtifactKey", e.parentArtifactKey()),
                        line("artifactKey", e.artifactKey() == null ? null : e.artifactKey().value()),
                        line("artifact", summarizeObject(e.artifact())));
            };
        }

    }

    private static String formatEvent(String title, String eventType, String... lines) {
        StringBuilder builder = new StringBuilder();
        builder.append(title).append("\n");
        builder.append("type: ").append(eventType == null ? "(none)" : eventType).append("\n");
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            builder.append(line).append("\n");
        }
        return builder.toString().trim();
    }

    private static String line(String label, Object value) {
        if (value == null) {
            return label + ": (none)";
        }
        String rendered = String.valueOf(value);
        if (rendered.isBlank()) {
            return label + ": (empty)";
        }
        return label + ": " + rendered;
    }

    private static String block(String label, String value) {
        if (value == null) {
            return label + ":\n\t(none)";
        }
        if (value.isBlank()) {
            return label + ":\n\t(empty)";
        }
        return label + ":\n\t" + value.trim().replace("\n", "\n\t");
    }

    private static String list(String label, List<?> values) {
        if (values == null || values.isEmpty()) {
            return label + ":\n\t(none)";
        }
        StringBuilder builder = new StringBuilder(label).append(":\n");
        int index = 1;
        for (Object value : values) {
            builder.append("\t").append(index++).append(". ")
                    .append(value == null ? "(none)" : summarizeObject(value))
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private static String map(String label, Map<?, ?> values) {
        if (values == null || values.isEmpty()) {
            return label + ":\n\t(none)";
        }
        StringBuilder builder = new StringBuilder(label).append(":\n");
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            builder.append("\t- ")
                    .append(entry.getKey() == null ? "(null)" : entry.getKey())
                    .append(": ")
                    .append(entry.getValue() == null ? "(none)" : summarizeObject(entry.getValue()))
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private static String summarizeObject(Object value) {
        if (value == null) {
            return "(none)";
        }
        if (value instanceof String s) {
            return s.isBlank() ? "(empty)" : s;
        }
        if (value instanceof ArtifactKey key) {
            return key.value();
        }
        return String.valueOf(value);
    }

    sealed interface AgentEvent extends GraphEvent {
        String nodeId();
    }

    /**
     * Emitted when a new node is added to the graph.
     */
    record NodeAddedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String nodeTitle,
            NodeType nodeType,
            String parentNodeId
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "NODE_ADDED";
        }
    }

    record ActionStartedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String agentName,
            String actionName
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "ACTION_STARTED";
        }
    }

    record ActionCompletedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String agentName,
            String actionName,
            String outcomeType,
            Artifact.AgentModel agentModel
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "ACTION_COMPLETED";
        }
    }

    record StopAgentEvent(
            String eventId,
            Instant timestamp,
            String nodeId
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "STOP_AGENT";
        }
    }

    /**
     * Pause execution for an agent to view results.
     * @param eventId
     * @param timestamp
     * @param nodeId
     * @param toAddMessage
     */
    record PauseEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String toAddMessage
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "PAUSE_EVENT";
        }
    }

    record ResumeEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String message
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "RESUME_EVENT";
        }
    }

    record ResolveInterruptEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String toAddMessage
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "RESOLVE_INTERRUPT";
        }
    }

    record AddMessageEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String toAddMessage
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "ADD_MESSAGE_EVENT";
        }
    }

    /**
     * Emitted when a node's status changes.
     */
    record NodeStatusChangedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            NodeStatus oldStatus,
            NodeStatus newStatus,
            String reason
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "NODE_STATUS_CHANGED";
        }
    }

    @Builder
    record NodeErrorEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String nodeTitle,
            NodeType nodeType,
            String message
    ) implements GraphEvent {

        public static NodeErrorEvent err(String errorMessage, ArtifactKey curr) {
            return NodeErrorEvent.builder()
                    .message(errorMessage)
                    .nodeId(curr.value())
                    .nodeType(NodeType.SUMMARY)
                    .nodeTitle("Found an error.")
                    .timestamp(Instant.now())
                    .build();
        }

        @Override
        public String eventType() {
            return "NODE_ERROR";
        }
    }

    /**
     * Emitted when a node is branched with modified goal, or the same goal.
     *  Sometimes, we want to have another agent do the same thing, or do something
     *  just a bit different to try it. Additionally, in the future, as agents become
     *  more and more cheap, we'll even try with automated branching, where an meta-
     *  orchestrator starts branching agents with modified goals to predict what the
     *  user might want to see. This will be an experiment with coding entire architectures
     *  generatively to test ideas, predicting how would this look, what's wrong with this
     *  - that's a plugin point for being able to change entire code-bases to test a single
     *    change - sort of like - adding a lifetime specifier to a rust codebase and that
     *    propagating through the whole code base in an instance in a work-tree, or just
     *    moving to an event based system - like - here's with events, and oh wow it ran
     *    into a sever issue with consistency, nope, doesn't look good, etc.
     *  - this helps sort of "test the attractors"
     */
    record NodeBranchedEvent(
            String eventId,
            Instant timestamp,
            String originalNodeId,
            String branchedNodeId,
            String newGoal,
            String mainWorktreeId,
            List<String> submoduleWorktreeIds
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "NODE_BRANCHED";
        }

        @Override
        public String nodeId() {
            return branchedNodeId;
        }
    }

    /**
     * Emitted when a node is pruned.
     */
    record NodePrunedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String reason,
            List<String> pruneWorktreeIds
    ) implements Events.GraphEvent {
        @Override
        public String eventType() {
            return "NODE_PRUNED";
        }
    }

    /**
     * Emitted when a review is requested.
     */
    record NodeReviewRequestedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String reviewNodeId,
            ReviewType reviewType,
            // "human", "agent", or specific agent type
            String contentToReview
    ) implements Events.AgentEvent {
        @Override
        public String eventType() {
            return "NODE_REVIEW_REQUESTED";
        }
    }

    /**
     * Emitted when interrupt status changes or is recorded.
     */
    record InterruptStatusEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String interruptType,
            String interruptStatus,
            String originNodeId,
            String resumeNodeId
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "INTERRUPT_STATUS";
        }
    }

    /**
     * Emitted when overall goal is completed.
     */
    record GoalCompletedEvent(
            String eventId,
            Instant timestamp,
            String orchestratorNodeId,
            String workflowId,
            Artifact.AgentModel model
    ) implements Events.GraphEvent {
        @Override
        public String nodeId() {
            return orchestratorNodeId;
        }

        @Override
        public String eventType() {
            return "GOAL_COMPLETED";
        }
    }

// ============ WORKTREE EVENTS ============

    /**
     * Emitted when a worktree is created (main or submodule).
     */
    record WorktreeCreatedEvent(
            String eventId,
            Instant timestamp,
            String worktreeId,
            String associatedNodeId,
            String worktreePath,
            String worktreeType,
            // "main" or "submodule"
            String submoduleName
            // Only if submodule
    ) implements Events.GraphEvent {
        @Override
        public String nodeId() {
            return associatedNodeId;
        }

        @Override
        public String eventType() {
            return "WORKTREE_CREATED";
        }
    }

    /**
     * Emitted when a worktree is branched.
     */
    record WorktreeBranchedEvent(
            String eventId,
            Instant timestamp,
            String originalWorktreeId,
            String branchedWorktreeId,
            String branchName,
            String worktreeType,
            String nodeId
            // "main" or "submodule"
    ) implements Events.GraphEvent {
        @Override
        public String eventType() {
            return "WORKTREE_BRANCHED";
        }
    }

    /**
     * Emitted when a child worktree is merged into parent.
     */
    record WorktreeMergedEvent(
            String eventId,
            Instant timestamp,
            String childWorktreeId,
            String parentWorktreeId,
            String mergeCommitHash,
            boolean conflictDetected,
            List<String> conflictFiles,
            String worktreeType,
            String nodeId
    ) implements Events.GraphEvent {
        @Override
        public String eventType() {
            return "WORKTREE_MERGED";
        }
    }

    /**
     * Emitted when a worktree is discarded/removed.
     */
    record WorktreeDiscardedEvent(
            String eventId,
            Instant timestamp,
            String worktreeId,
            String reason,
            String worktreeType,
            String nodeId
            // "main" or "submodule"
    ) implements Events.GraphEvent {
        @Override
        public String eventType() {
            return "WORKTREE_DISCARDED";
        }
    }

// ============ GENERIC GRAPH EVENTS ============

    /**
     * Generic event for updates to nodes.
     */
    record NodeUpdatedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            Map<String, String> updates
    ) implements Events.GraphEvent {
        @Override
        public String eventType() {
            return "NODE_UPDATED";
        }
    }

    /**
     * Event for deletion of nodes (less common than pruning).
     */
    record NodeDeletedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String reason
    ) implements Events.GraphEvent {
        @Override
        public String eventType() {
            return "NODE_DELETED";
        }
    }

    record ChatSessionCreatedEvent(
            String eventId,
            Instant timestamp,
            String nodeId
    ) implements Events.GraphEvent {
        @Override
        public String eventType() {
            return "CHAT_SESSION_CREATED";
        }
    }

    record ChatSessionClosedEvent(
            String eventId,
            Instant timestamp,
            String sessionId
    ) implements Events.GraphEvent {
        @Override
        public String nodeId() {
            return sessionId;
        }

        @Override
        public String eventType() {
            return "CHAT_SESSION_CLOSED";
        }
    }

    /**
     * Emitted during streaming output from an agent (e.g., code generation).
     */
    record NodeStreamDeltaEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String deltaContent,
            int tokenCount,
            boolean isFinal
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "NODE_STREAM_DELTA";
        }
    }

    record NodeThoughtDeltaEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String deltaContent,
            int tokenCount,
            boolean isFinal
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "NODE_THOUGHT_DELTA";
        }
    }

    record ToolCallEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String toolCallId,
            String title,
            String kind,
            String status,
            String phase,
            List<Map<String, Object>> content,
            List<Map<String, Object>> locations,
            Object rawInput,
            Object rawOutput
    ) implements GraphEvent {
        @Override
        public String eventType() {
            String normalized = phase != null ? phase.toUpperCase(Locale.ROOT) : "UPDATE";
            return "TOOL_CALL_" + normalized;
        }
    }

    record GuiRenderEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionId,
            Object payload
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "GUI_RENDER";
        }
    }

    record UiDiffAppliedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionId,
            String revision,
            Object renderTree,
            String summary
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "UI_DIFF_APPLIED";
        }
    }

    record UiDiffRejectedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionId,
            String errorCode,
            String message
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "UI_DIFF_REJECTED";
        }
    }

    record UiDiffRevertedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionId,
            String revision,
            Object renderTree,
            String sourceEventId
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "UI_DIFF_REVERTED";
        }
    }

    record UiFeedbackEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionId,
            String sourceEventId,
            String message,
            UiStateSnapshot snapshot
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "UI_FEEDBACK";
        }
    }

    record NodeBranchRequestedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String message
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "NODE_BRANCH_REQUESTED";
        }
    }

    record PlanUpdateEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            List<Map<String, Object>> entries
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "PLAN_UPDATE";
        }
    }

    record UserMessageChunkEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String content
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "USER_MESSAGE_CHUNK";
        }
    }

    record CurrentModeUpdateEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String currentModeId
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "CURRENT_MODE_UPDATE";
        }
    }

    record AvailableCommandsUpdateEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            List<Map<String, Object>> commands
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "AVAILABLE_COMMANDS_UPDATE";
        }
    }

    record PermissionRequestedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String originNodeId,
            String requestId,
            String toolCallId,
            Object permissions
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "PERMISSION_REQUESTED";
        }
    }

    record PermissionResolvedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String originNodeId,
            String requestId,
            String toolCallId,
            String outcome,
            String selectedOptionId
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "PERMISSION_RESOLVED";
        }
    }

    // ============ TUI EVENTS ============

    sealed interface TuiEvent {
    }

    sealed interface TuiInteractionEvent extends TuiEvent
            permits
            EventStreamMoveSelection,
            EventStreamScroll,
            EventStreamOpenDetail,
            EventStreamCloseDetail,
            FocusChatInput,
            FocusEventStream,
            ChatInputChanged,
            ChatInputSubmitted,
            ChatSearchOpened,
            ChatSearchQueryChanged,
            ChatSearchResultNavigate,
            ChatSearchClosed,
            SessionSelected,
            SessionCreated,
            FocusSessionList {
    }

    sealed interface TuiSystemEvent extends TuiEvent
            permits
            EventStreamAppended,
            EventStreamTrimmed,
            ChatMessageAppended,
            ChatHistoryTrimmed {
    }

    record TuiInteractionGraphEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionId,
            TuiInteractionEvent tuiEvent
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "TUI_INTERACTION";
        }
    }

    record TuiSystemGraphEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionId,
            TuiSystemEvent tuiEvent
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "TUI_SYSTEM";
        }
    }

    record EventStreamMoveSelection(
            int delta,
            int newSelectedIndex
    ) implements TuiInteractionEvent {
    }

    record EventStreamScroll(
            int delta,
            int newScrollOffset
    ) implements TuiInteractionEvent {
    }

    record EventStreamOpenDetail(
            String eventId
    ) implements TuiInteractionEvent {
    }

    record EventStreamCloseDetail(
            String eventId
    ) implements TuiInteractionEvent {
    }

    record FocusChatInput(
            String previousFocus
    ) implements TuiInteractionEvent {
    }

    record FocusEventStream(
            String previousFocus
    ) implements TuiInteractionEvent {
    }

    record ChatInputChanged(
            String text,
            int cursorPosition
    ) implements TuiInteractionEvent {
    }

    record ChatInputSubmitted(
            String text
    ) implements TuiInteractionEvent {
    }

    record ChatSearchOpened(
            String initialQuery
    ) implements TuiInteractionEvent {
    }

    record ChatSearchQueryChanged(
            String query,
            int cursorPosition
    ) implements TuiInteractionEvent {
    }

    record ChatSearchResultNavigate(
            int delta,
            int resultIndex
    ) implements TuiInteractionEvent {
    }

    record ChatSearchClosed(
            String query
    ) implements TuiInteractionEvent {
    }

    record SessionSelected(
            String sessionId
    ) implements TuiInteractionEvent {
    }

    record SessionCreated(
            String sessionId
    ) implements TuiInteractionEvent {
    }

    record FocusSessionList(
            String previousFocus
    ) implements TuiInteractionEvent {
    }

    record EventStreamAppended(
            String graphEventId,
            int rowIndex
    ) implements TuiSystemEvent {
    }

    record EventStreamTrimmed(
            int removedCount
    ) implements TuiSystemEvent {
    }

    record ChatMessageAppended(
            String messageId,
            int rowIndex
    ) implements TuiSystemEvent {
    }

    record ChatHistoryTrimmed(
            int removedCount
    ) implements TuiSystemEvent {
    }

    record UiStateSnapshot(
            String sessionId,
            String revision,
            Instant timestamp,
            Object renderTree
    ) {
    }

    // ============ ARTIFACT EVENTS ============

    /**
     * Event emitted when an artifact is created during execution.
     * Used by ArtifactEventListener to build and persist the artifact tree.
     */
    record ArtifactEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String artifactType,
            String parentArtifactKey,
            Artifact artifact
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "ARTIFACT_EMITTED";
        }

        public ArtifactKey artifactKey() {
            return artifact.artifactKey();
        }
    }
}

// ============ NODE EVENTS ============
