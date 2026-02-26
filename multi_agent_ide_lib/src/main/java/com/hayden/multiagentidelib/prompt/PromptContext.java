package com.hayden.multiagentidelib.prompt;

import com.embabel.agent.api.common.ContextualPromptElement;
import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.PreviousContext;
import com.hayden.multiagentidelib.agent.UpstreamContext;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.Builder;
import lombok.With;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Context for prompt assembly containing agent type, context identifiers,
 * upstream prev from prior workflow phases, and optional metadata.
 */
@Builder(toBuilder = true)
@With
@Slf4j
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
        Map<String, Object> model,
        String modelName,
        OperationContext operationContext
) {

    public PromptContext(AgentType agentType, ArtifactKey currentContextId, List<UpstreamContext> upstreamContexts, PreviousContext previousContext, BlackboardHistory blackboardHistory, AgentModels.AgentRequest previousRequest, AgentModels.AgentRequest currentRequest,
                         Map<String, Object> metadata, String templateName, Map<String, Object> modelWithFeedback, String modelName, OperationContext operationContext) {
        this(agentType, currentContextId, upstreamContexts, previousContext, blackboardHistory, previousRequest, currentRequest, metadata, new ArrayList<>(), templateName, Artifact.HashContext.defaultHashContext(), modelWithFeedback, modelName, operationContext);
    }

    public ArtifactKey chatId() {
        return switch(currentRequest)  {
            case AgentModels.CommitAgentRequest car ->  car.contextId().parent().orElseGet(() -> {
                log.error("CommitAgentRequest {} could not get parent. Returning regular.", car.contextId());
                return car.contextId();
            });
            case AgentModels.MergeConflictRequest mcr -> mcr.contextId().parent().orElseGet(() -> {
                log.error("MergeConflictRequest {} could not get parent. Returning regular.", mcr.contextId());
                return mcr.contextId();
            });
            case AgentModels.AgentRequest ar -> ar.contextId();
        };
    }

    /**
     * Constructor that normalizes null upstream prev and metadata.
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
