package com.hayden.multiagentidelib.prompt;

import com.embabel.agent.api.common.ContextualPromptElement;
import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentidelib.agent.*;
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
        BlackboardHistory blackboardHistory,
        AgentModels.AgentRequest previousRequest,
        AgentModels.AgentRequest currentRequest,
        Map<String, Object> metadata,
        List<ContextualPromptElement> promptContributors,
        String templateName,
        Artifact.HashContext hashContext,
        Map<String, Object> model,
        String modelName,
        OperationContext operationContext,
        String agentName,
        String actionName,
        String methodName
) {

    public PromptContext(AgentType agentType, ArtifactKey currentContextId, BlackboardHistory blackboardHistory, AgentModels.AgentRequest previousRequest, AgentModels.AgentRequest currentRequest,
                         Map<String, Object> metadata, String templateName, Map<String, Object> modelWithFeedback, String modelName, OperationContext operationContext,
                         DecoratorContext decoratorContext) {
        this(agentType, currentContextId, blackboardHistory, previousRequest, currentRequest, metadata, new ArrayList<>(), templateName, Artifact.HashContext.defaultHashContext(), modelWithFeedback, modelName, operationContext,
                decoratorContext.agentName(), decoratorContext.actionName(), decoratorContext.methodName());
    }

    public PromptContext(AgentType agentType, ArtifactKey currentContextId, BlackboardHistory blackboardHistory, AgentModels.AgentRequest previousRequest, AgentModels.AgentRequest currentRequest,
                         Map<String, Object> metadata, String templateName, Map<String, Object> modelWithFeedback, String modelName, OperationContext operationContext,
                         String agentName, String actionName, String methodName) {
        this(agentType, currentContextId, blackboardHistory, previousRequest, currentRequest, metadata, new ArrayList<>(), templateName, Artifact.HashContext.defaultHashContext(), modelWithFeedback, modelName, operationContext,
                agentName, actionName, methodName);
    }

    public ArtifactKey chatId() {
        return switch(currentRequest)  {
            case AgentModels.CommitAgentRequest car -> car.chatKey() != null ? car.chatKey() : car.contextId();
            case AgentModels.MergeConflictRequest mcr -> mcr.chatKey() != null ? mcr.chatKey() : mcr.contextId();
            case AgentModels.AgentToAgentRequest aar -> aar.chatId() != null
                    ? aar.chatId()
                    : (aar.targetNodeId() != null && !aar.targetNodeId().isBlank()
                            ? new ArtifactKey(aar.targetNodeId())
                            : aar.targetAgentKey());
            case AgentModels.AgentToControllerRequest acr -> {
                log.error("chatId() called on AgentToControllerRequest — this request routes through the permission gate, not ACP. Returning sourceAgentKey as fallback.");
                yield acr.sourceAgentKey();
            }
            case AgentModels.ControllerToAgentRequest car -> car.chatId() != null
                    ? car.chatId()
                    : car.targetAgentKey();
            case AgentModels.AiFilterRequest afr -> afr.chatKey() != null ? afr.chatKey() : afr.contextId();
            case AgentModels.AiPropagatorRequest apr -> apr.chatKey() != null ? apr.chatKey() : apr.contextId();
            case AgentModels.AiTransformerRequest atr -> atr.chatKey() != null ? atr.chatKey() : atr.contextId();
            case AgentModels.AgentRequest ar -> ar.contextId();
        };
    }

    /**
     * Constructor that normalizes null upstream prev and metadata.
     */
    public PromptContext {
        if (metadata == null) {
            metadata = Map.of();
        }
        if (promptContributors == null) {
            promptContributors = List.of();
        }
        if (hashContext == null) {
            hashContext = Artifact.HashContext.defaultHashContext();
        }
    }
}
