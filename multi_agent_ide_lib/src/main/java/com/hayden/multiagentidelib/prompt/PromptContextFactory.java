package com.hayden.multiagentidelib.prompt;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.ContextId;
import com.hayden.multiagentidelib.agent.PreviousContext;
import com.hayden.multiagentidelib.agent.UpstreamContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Factory for creating PromptContext instances by extracting upstream contexts
 * from typed request objects using pattern matching.
 */

@Component
@RequiredArgsConstructor
public class PromptContextFactory {

    private final ContextIdService contextIdService;

    /**
     * Build a PromptContext by pattern matching on the input object to extract
     * contextId, upstream contexts, and previousContext.
     *
     * @param agentType the type of agent this context is for
     * @param input the request object containing typed curation/upstream context fields
     * @param blackboardHistory optional blackboard history
     * @return a PromptContext with all upstream contexts collected
     */
    public PromptContext build(
            AgentType agentType,
            Object input,
            BlackboardHistory.History blackboardHistory
    ) {
        ContextId contextId = null;
        List<UpstreamContext> upstreamContexts = new ArrayList<>();
        PreviousContext previousContext = null;
        AgentModels.AgentRequest request = input instanceof AgentModels.AgentRequest
                ? (AgentModels.AgentRequest) input
                : null;

        switch (input) {
            case AgentModels.OrchestratorRequest req -> {
                contextId = resolve(req.contextId());
                collectNonNull(upstreamContexts, req.discoveryCuration());
                collectNonNull(upstreamContexts, req.planningCuration());
                collectNonNull(upstreamContexts, req.ticketCuration());
                previousContext = req.previousContext();
            }
            case AgentModels.OrchestratorCollectorRequest req -> {
                contextId = resolve(req.contextId());
                collectNonNull(upstreamContexts, req.discoveryCuration());
                collectNonNull(upstreamContexts, req.planningCuration());
                collectNonNull(upstreamContexts, req.ticketCuration());
                previousContext = req.previousContext();
            }
            case AgentModels.DiscoveryOrchestratorRequest req -> {
                contextId = resolve(req.contextId());
                // Discovery orchestrator is at the start - no upstream contexts
                previousContext = req.previousContext();
            }
            case AgentModels.DiscoveryAgentResults req -> {
                contextId = resolve(req.contextId());
                // Discovery agent has no upstream curation
                previousContext = req.previousContext();
            }
            case AgentModels.DiscoveryAgentRequest req -> {
                contextId = resolve(req.contextId());
                // Discovery agent has no upstream curation
                previousContext = req.previousContext();
            }
            case AgentModels.DiscoveryCollectorRequest req -> {
                contextId = resolve(req.contextId());
                // Discovery collector has no upstream curation
                previousContext = req.previousContext();
            }
            case AgentModels.PlanningOrchestratorRequest req -> {
                contextId = resolve(req.contextId());
                collectNonNull(upstreamContexts, req.discoveryCuration());
                previousContext = req.previousContext();
            }
            case AgentModels.PlanningAgentRequest req -> {
                contextId = resolve(req.contextId());
                collectNonNull(upstreamContexts, req.discoveryCuration());
                previousContext = req.previousContext();
            }
            case AgentModels.PlanningCollectorRequest req -> {
                contextId = resolve(req.contextId());
                collectNonNull(upstreamContexts, req.discoveryCuration());
                previousContext = req.previousContext();
            }
            case AgentModels.TicketOrchestratorRequest req -> {
                contextId = resolve(req.contextId());
                collectNonNull(upstreamContexts, req.discoveryCuration());
                collectNonNull(upstreamContexts, req.planningCuration());
                previousContext = req.previousContext();
            }
            case AgentModels.TicketAgentRequest req -> {
                contextId = resolve(req.contextId());
                collectNonNull(upstreamContexts, req.discoveryCuration());
                collectNonNull(upstreamContexts, req.planningCuration());
                previousContext = req.previousContext();
            }
            case AgentModels.TicketCollectorRequest req -> {
                contextId = resolve(req.contextId());
                collectNonNull(upstreamContexts, req.discoveryCuration());
                collectNonNull(upstreamContexts, req.planningCuration());
                previousContext = req.previousContext();
            }
            case AgentModels.ReviewRequest req -> {
                contextId = resolve(req.contextId());
                previousContext = req.previousContext();
            }
            case AgentModels.MergerRequest req -> {
                contextId = resolve(req.contextId());
                previousContext = req.previousContext();
            }
            case null -> {
                // null input - return context with empty upstream list
            }
            default -> {
                // Unknown input type - return context with empty upstream list
            }
        }

        return new PromptContext(
                agentType,
                contextId,
                upstreamContexts,
                previousContext,
                blackboardHistory,
                request,
                Map.of()
        );
    }

    /**
     * Build a PromptContext with explicit upstream contexts (for cases where
     * upstream contexts are already extracted or need to be manually specified).
     */
    public PromptContext build(
            AgentType agentType,
            ContextId contextId,
            List<UpstreamContext> upstreamContexts,
            PreviousContext previousContext,
            BlackboardHistory.History blackboardHistory
    ) {
        return new PromptContext(
                agentType,
                resolve(contextId),
                upstreamContexts != null ? upstreamContexts : List.of(),
                previousContext,
                blackboardHistory,
                null,
                Map.of()
        );
    }

    /**
     * Build a PromptContext with a single upstream context (convenience method
     * for backward compatibility).
     */
    public PromptContext build(
            AgentType agentType,
            ContextId contextId,
            UpstreamContext upstreamContext,
            PreviousContext previousContext,
            BlackboardHistory.History blackboardHistory
    ) {
        List<UpstreamContext> contexts = upstreamContext != null 
                ? List.of(upstreamContext) 
                : List.of();
        return new PromptContext(
                agentType,
                resolve(contextId),
                contexts,
                previousContext,
                blackboardHistory,
                null,
                Map.of()
        );
    }

    private ContextId resolve(ContextId contextId) {
        return contextIdService.generate(contextId.workflowRunId(), contextId.agentType());
    }

    private void collectNonNull(List<UpstreamContext> list, UpstreamContext context) {
        if (context != null) {
            list.add(context);
        }
    }
}
