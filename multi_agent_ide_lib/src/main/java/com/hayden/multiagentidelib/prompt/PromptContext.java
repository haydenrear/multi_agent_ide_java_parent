package com.hayden.multiagentidelib.prompt;

import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.ContextId;
import com.hayden.multiagentidelib.agent.PreviousContext;
import com.hayden.multiagentidelib.agent.UpstreamContext;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.AgentModels;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Context for prompt assembly containing agent type, context identifiers,
 * upstream contexts from prior workflow phases, and optional metadata.
 */
@Builder(toBuilder = true)
public record PromptContext(
        AgentType agentType,
        ContextId currentContextId,
        List<UpstreamContext> upstreamContexts,
        PreviousContext previousContext,
        BlackboardHistory.History blackboardHistory,
        AgentModels.AgentRequest request,
        Map<String, Object> metadata
) {
    /**
     * Constructor that normalizes null upstream contexts and metadata.
     */
    public PromptContext {
        if (upstreamContexts == null) {
            upstreamContexts = List.of();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
