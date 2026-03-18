package com.hayden.multiagentidelib.agent;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.HasContextId;
import com.hayden.multiagentidelib.prompt.contributor.NodeMappings;

import java.time.Instant;
import java.util.List;

/**
 * Sealed algebra of composite / interpreted history entries. Each record represents a pattern
 * detected over the raw {@link BlackboardHistory.Entry} stream by a
 * {@link com.hayden.multiagentidelib.agent.history.HistoryInterpreter}.
 *
 * <p>Because {@code ContextAlgebra extends Entry}, algebra items live directly in the
 * {@link BlackboardHistory.History#entries()} list. Methods that need to skip algebra items
 * use a single {@code instanceof ContextAlgebra} check.</p>
 */
public sealed interface ContextAlgebra extends BlackboardHistory.Entry, AgentPretty
        permits
        // Standard workflow transitions — orchestrator-level
        ContextAlgebra.OrchestratorToDiscoveryOrchestrator,
        ContextAlgebra.DiscoveryCollectorToPlanningOrchestrator,
        ContextAlgebra.PlanningCollectorToTicketOrchestrator,
        ContextAlgebra.TicketCollectorToOrchestratorCollector,
        // Standard workflow transitions — orchestrator-to-dispatch
        ContextAlgebra.DiscoveryOrchestratorToDiscoveryAgentDispatch,
        ContextAlgebra.PlanningOrchestratorToPlanningAgentDispatch,
        ContextAlgebra.TicketOrchestratorToTicketAgentDispatch,
        // Dispatch/Agent transitions (per sub-graph)
        ContextAlgebra.DiscoveryAgentDispatchToDiscoveryAgent,
        ContextAlgebra.DiscoveryAgentToDiscoveryAgent,
        ContextAlgebra.DiscoveryAgentToDiscoveryAgentDispatch,
        ContextAlgebra.DiscoveryAgentDispatchToDiscoveryCollector,
        ContextAlgebra.PlanningAgentDispatchToPlanningAgent,
        ContextAlgebra.PlanningAgentToPlanningAgent,
        ContextAlgebra.PlanningAgentToPlanningAgentDispatch,
        ContextAlgebra.PlanningAgentDispatchToPlanningCollector,
        ContextAlgebra.TicketAgentDispatchToTicketAgent,
        ContextAlgebra.TicketAgentToTicketAgent,
        ContextAlgebra.TicketAgentToTicketAgentDispatch,
        ContextAlgebra.TicketAgentDispatchToTicketCollector,
        // Interrupt transitions
        ContextAlgebra.AgentToInterrupt,
        ContextAlgebra.InterruptCycleCompleted,
        ContextAlgebra.HumanInterruptReroute,
        // Agent action (request + messages + response)
        ContextAlgebra.AgentAction,
        // Context Manager
        ContextAlgebra.ContextManagerLoop,
        // Sub-graphs
        ContextAlgebra.AgentSubGraph,
        // Commit & Merge
        ContextAlgebra.AgentCommitAndMerge,
        ContextAlgebra.DispatchBatchCommitAndMerge,
        // Recovery / error
        ContextAlgebra.StuckHandlerInvoked,
        ContextAlgebra.ErrorOccurred,
        ContextAlgebra.RetrySequence,
        // Loop detection
        ContextAlgebra.LoopDetected {

    /** Range of raw entry indices this interpretation spans. */
    int sourceStartIndex();
    int sourceEndIndex();

    // -------------------------------------------------------------------
    // Default Entry methods
    // -------------------------------------------------------------------

    @Override
    default Class<?> inputType() {
        return this.getClass();
    }

    @Override
    default ArtifactKey contextId() {
        return input() != null ? input().contextId() : null;
    }

    // -------------------------------------------------------------------
    // AgentPretty defaults
    // -------------------------------------------------------------------

    @Override
    default String prettyPrintInterruptContinuation() {
        return prettyPrint();
    }

    // -------------------------------------------------------------------
    // Shared helper
    // -------------------------------------------------------------------

    private static String displayName(Class<?> type) {
        return NodeMappings.displayName(type);
    }

    // ===================================================================
    // Standard workflow transitions — orchestrator-level
    // ===================================================================

    record OrchestratorToDiscoveryOrchestrator(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId fromEntry, HasContextId toEntry
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Orchestrator → Discovery Orchestrator";
        }
    }

    record DiscoveryCollectorToPlanningOrchestrator(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId fromEntry, HasContextId toEntry
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Discovery Collector → Planning Orchestrator";
        }
    }

    record PlanningCollectorToTicketOrchestrator(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId fromEntry, HasContextId toEntry
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Planning Collector → Ticket Orchestrator";
        }
    }

    record TicketCollectorToOrchestratorCollector(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId fromEntry, HasContextId toEntry
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Ticket Collector → Orchestrator Collector";
        }
    }

    // ===================================================================
    // Orchestrator-to-dispatch transitions
    // ===================================================================

    record DiscoveryOrchestratorToDiscoveryAgentDispatch(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId fromEntry, HasContextId toEntry
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Discovery Orchestrator → Discovery Agent Dispatch";
        }
    }

    record PlanningOrchestratorToPlanningAgentDispatch(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId fromEntry, HasContextId toEntry
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Planning Orchestrator → Planning Agent Dispatch";
        }
    }

    record TicketOrchestratorToTicketAgentDispatch(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId fromEntry, HasContextId toEntry
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Ticket Orchestrator → Ticket Agent Dispatch";
        }
    }

    // ===================================================================
    // Discovery dispatch/agent transitions
    // ===================================================================

    record DiscoveryAgentDispatchToDiscoveryAgent(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId dispatchRequest, HasContextId agentRequest
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Discovery Agent Dispatch → Discovery Agent";
        }
    }

    record DiscoveryAgentToDiscoveryAgent(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId previousAgentResult, HasContextId nextAgentRequest
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Discovery Agent → Discovery Agent";
        }
    }

    record DiscoveryAgentToDiscoveryAgentDispatch(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId lastAgentResult, HasContextId dispatchResult
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Discovery Agent → Discovery Agent Dispatch";
        }
    }

    record DiscoveryAgentDispatchToDiscoveryCollector(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId dispatchResult, HasContextId collectorRequest
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Discovery Agent Dispatch → Discovery Collector";
        }
    }

    // ===================================================================
    // Planning dispatch/agent transitions
    // ===================================================================

    record PlanningAgentDispatchToPlanningAgent(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId dispatchRequest, HasContextId agentRequest
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Planning Agent Dispatch → Planning Agent";
        }
    }

    record PlanningAgentToPlanningAgent(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId previousAgentResult, HasContextId nextAgentRequest
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Planning Agent → Planning Agent";
        }
    }

    record PlanningAgentToPlanningAgentDispatch(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId lastAgentResult, HasContextId dispatchResult
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Planning Agent → Planning Agent Dispatch";
        }
    }

    record PlanningAgentDispatchToPlanningCollector(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId dispatchResult, HasContextId collectorRequest
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Planning Agent Dispatch → Planning Collector";
        }
    }

    // ===================================================================
    // Ticket dispatch/agent transitions
    // ===================================================================

    record TicketAgentDispatchToTicketAgent(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId dispatchRequest, HasContextId agentRequest
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Ticket Agent Dispatch → Ticket Agent";
        }
    }

    record TicketAgentToTicketAgent(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId previousAgentResult, HasContextId nextAgentRequest
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Ticket Agent → Ticket Agent";
        }
    }

    record TicketAgentToTicketAgentDispatch(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId lastAgentResult, HasContextId dispatchResult
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Ticket Agent → Ticket Agent Dispatch";
        }
    }

    record TicketAgentDispatchToTicketCollector(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId dispatchResult, HasContextId collectorRequest
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Transition] Ticket Agent Dispatch → Ticket Collector";
        }
    }

    // ===================================================================
    // Interrupt transitions
    // ===================================================================

    record AgentToInterrupt(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            Class<?> originatingAgentType,
            HasContextId originatingRequest,
            AgentModels.InterruptRequest interruptRequest
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Interrupt] %s → %s (reason: %s)".formatted(
                    displayName(originatingAgentType),
                    interruptRequest.type(),
                    interruptRequest.reason() != null ? interruptRequest.reason() : "none");
        }
    }

    record InterruptCycleCompleted(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            Class<?> originatingAgentType,
            AgentModels.InterruptRequest interruptRequest,
            Class<?> returnAgentType
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Interrupt Cycle Completed] %s → interrupt → %s".formatted(
                    displayName(originatingAgentType),
                    displayName(returnAgentType));
        }
    }

    record HumanInterruptReroute(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            Class<?> originatingAgentType,
            AgentModels.InterruptRequest interruptRequest,
            Class<?> reroutedToAgentType
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Human Interrupt Reroute] %s → interrupt → rerouted to %s".formatted(
                    displayName(originatingAgentType),
                    displayName(reroutedToAgentType));
        }
    }

    // ===================================================================
    // Agent action (request + messages + response)
    // ===================================================================

    record AgentAction(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            BlackboardHistory.Entry requestEntry,
            BlackboardHistory.MessageEntry messageEntry,
            BlackboardHistory.Entry resultEntry,
            Class<?> agentRequestType
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            int msgCount = messageEntry != null && messageEntry.events() != null
                    ? messageEntry.events().events().size() : 0;
            return "[Action] %s (messages: %d)".formatted(
                    displayName(agentRequestType), msgCount);
        }
    }

    // ===================================================================
    // Context Manager
    // ===================================================================

    record ContextManagerLoop(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            HasContextId routingRequest,
            AgentModels.ContextManagerRequest contextManagerRequest,
            List<BlackboardHistory.Entry> containedEntries,
            HasContextId terminatingEntry
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Context Manager Loop] routed by %s (%d entries)".formatted(
                    routingRequest != null ? routingRequest.getClass().getSimpleName() : "unknown",
                    containedEntries != null ? containedEntries.size() : 0);
        }
    }

    // ===================================================================
    // Sub-graphs
    // ===================================================================

    enum SubGraphType {
        DISCOVERY, PLANNING, TICKET, ORCHESTRATOR
    }

    record AgentSubGraph(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            SubGraphType subGraphType,
            List<BlackboardHistory.Entry> entries
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Sub-Graph] %s (%d entries, indices %d–%d)".formatted(
                    subGraphType,
                    entries != null ? entries.size() : 0,
                    sourceStartIndex, sourceEndIndex);
        }
    }

    // ===================================================================
    // Commit & Merge
    // ===================================================================

    record AgentCommitAndMerge(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            AgentModels.CommitAgentResult commitResult,
            AgentModels.MergeConflictResult mergeConflictResult,
            String agentIdentity
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            boolean hasConflict = mergeConflictResult != null;
            return "[Commit & Merge] %s (conflict: %s)".formatted(
                    agentIdentity != null ? agentIdentity : "unknown",
                    hasConflict ? "yes" : "no");
        }
    }

    record DispatchBatchCommitAndMerge(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            Class<?> dispatchRequestType,
            List<AgentCommitAndMerge> agentCommitAndMerges
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Batch Commit & Merge] %s (%d agents)".formatted(
                    displayName(dispatchRequestType),
                    agentCommitAndMerges != null ? agentCommitAndMerges.size() : 0);
        }
    }

    // ===================================================================
    // Recovery / error
    // ===================================================================

    record StuckHandlerInvoked(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            AgentModels.ContextManagerRequest stuckRequest,
            AgentModels.AgentRequest lastWorkflowRequest,
            String loopSummary
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            String lastReqName = lastWorkflowRequest != null
                    ? displayName(lastWorkflowRequest.getClass())
                    : "unknown";
            return "[Stuck Handler] triggered after %s".formatted(lastReqName);
        }
    }

    record ErrorOccurred(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            String errorMessage,
            String failedOutputTypeName,
            String failedInputTypeName
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Error] %s: %s".formatted(
                    failedOutputTypeName != null ? failedOutputTypeName : "unknown",
                    errorMessage != null ? errorMessage : "no message");
        }
    }

    record RetrySequence(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            Class<?> requestType,
            int retryCount,
            List<BlackboardHistory.Entry> errorEntries
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Retry] %s x%d (errors: %d)".formatted(
                    displayName(requestType), retryCount,
                    errorEntries != null ? errorEntries.size() : 0);
        }
    }

    // ===================================================================
    // Loop detection
    // ===================================================================

    record LoopDetected(
            Instant timestamp, String actionName, HasContextId input,
            int sourceStartIndex, int sourceEndIndex,
            List<String> repeatedPattern,
            int repetitionCount
    ) implements ContextAlgebra {
        @Override public String prettyPrint() {
            return "[Loop Detected] pattern %s repeated %d times".formatted(
                    repeatedPattern, repetitionCount);
        }
    }
}
