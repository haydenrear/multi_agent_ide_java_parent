package com.hayden.utilitymodule.acp.events;

import com.agui.core.types.BaseEvent;
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
            String outcomeType
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

    record NodeErrorEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String nodeTitle,
            NodeType nodeType,
            String message
    ) implements GraphEvent {
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
            String finalSummary,
            int totalNodesCompleted,
            int totalNodesFailed,
            long executionTimeMs
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

    record UiStateSnapshot(
            String sessionId,
            String revision,
            Instant timestamp,
            Object renderTree
    ) {
    }
}

// ============ NODE EVENTS ============
