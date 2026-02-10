package com.hayden.multiagentidelib.prompt;

import com.embabel.agent.api.common.ContextualPromptElement;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.PreviousContext;
import com.hayden.multiagentidelib.agent.UpstreamContext;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.Builder;
import lombok.With;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Context for prompt assembly containing agent type, context identifiers,
 * upstream contexts from prior workflow phases, and optional metadata.
 */
@Builder(toBuilder = true)
@With
public record PromptContext(
        AgentType agentType,
        ArtifactKey currentContextId,
        List<UpstreamContext> upstreamContexts,
        PreviousContext previousContext,
        BlackboardHistory blackboardHistory,
        AgentModels.AgentRequest previousRequest,
        AgentModels.AgentRequest currentRequest,
        Map<String, Object> metadata,
        List<ContextualPromptElement> promptContributors,
        String templateName,
        Artifact.HashContext hashContext,
        Map<String, Object> model
) {

    public PromptContext(AgentType agentType, ArtifactKey currentContextId, List<UpstreamContext> upstreamContexts, PreviousContext previousContext, BlackboardHistory blackboardHistory, AgentModels.AgentRequest previousRequest, AgentModels.AgentRequest currentRequest,
                         Map<String, Object> metadata, String templateName, Map<String, Object> modelWithFeedback) {
        this(agentType, currentContextId, upstreamContexts, previousContext, blackboardHistory, previousRequest, currentRequest, metadata, new ArrayList<>(), templateName, Artifact.HashContext.defaultHashContext(), modelWithFeedback);
    }

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
