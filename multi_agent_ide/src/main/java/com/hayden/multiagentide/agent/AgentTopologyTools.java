package com.hayden.multiagentide.agent;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.AgentPlatform;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.commitdiffcontext.cdc_utils.SetFromHeader;
import com.hayden.commitdiffcontext.mcp.ToolCarrier;
import com.hayden.multiagentide.agent.decorator.request.DecorateRequestResults;
import com.hayden.multiagentide.service.AgentCommunicationService;
import com.hayden.multiagentide.service.SessionKeyResolutionService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.DecoratorContext;
import com.hayden.multiagentidelib.llm.LlmRunner;
import com.hayden.multiagentidelib.model.nodes.AgentToAgentConversationNode;
import com.hayden.multiagentidelib.model.nodes.GraphNode;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContextFactory;
import com.hayden.multiagentidelib.prompt.contributor.NodeMappings;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

import static com.hayden.acp_cdc_ai.acp.AcpChatModel.MCP_SESSION_HEADER;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentTopologyTools implements ToolCarrier {

    private final AgentCommunicationService agentCommunicationService;
    private final SessionKeyResolutionService sessionKeyResolutionService;
    private final ObjectMapper objectMapper;

    private AgentPlatform agentPlatform;
    private DecorateRequestResults decorateRequestResults;
    private LlmRunner llmRunner;
    private PromptContextFactory promptContextFactory;

    @Autowired
    public void setAgentPlatform(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    @Autowired
    public void setDecorateRequestResults(DecorateRequestResults decorateRequestResults) {
        this.decorateRequestResults = decorateRequestResults;
    }

    @Autowired
    public void setLlmRunner(LlmRunner llmRunner) {
        this.llmRunner = llmRunner;
    }

    @Autowired
    public void setPromptContextFactory(PromptContextFactory promptContextFactory) {
        this.promptContextFactory = promptContextFactory;
    }

    @Tool(name = "list_agents", description = "Lists all available agent sessions that can be communicated with. "
            + "Returns a JSON array of agents with their keys, types, busy status, and whether the current agent "
            + "is permitted to call them based on topology rules. Use this to discover which agents are available "
            + "before calling them with the call_agent tool.")
    public String listAgents(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId
    ) {
        if (!StringUtils.hasText(sessionId)) {
            return "{\"error\": \"missing session id\"}";
        }
        GraphNode callingNode = sessionKeyResolutionService.resolveNodeBySessionKey(sessionId);
        AgentType callingAgentType = callingNode != null ? NodeMappings.agentTypeFromNode(callingNode) : null;
        var agents = agentCommunicationService.listAvailableAgents(sessionId, callingAgentType);
        return toJson(agents);
    }

    @Tool(name = "call_agent", description = "Sends a message to another agent and returns their response. "
            + "Use list_agents first to discover available agents. The target agent must be available and "
            + "communication must be permitted by topology rules. Call chain tracking and loop detection "
            + "are handled automatically.")
    public String callAgent(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId,
            String targetAgentKey,
            String message
    ) {
        if (!StringUtils.hasText(sessionId)) {
            return "ERROR: Missing session id.";
        }
        if (!StringUtils.hasText(targetAgentKey)) {
            return "ERROR: Missing target agent key.";
        }
        if (!StringUtils.hasText(message)) {
            return "ERROR: Message cannot be empty.";
        }

        ArtifactKey callingKey;
        ArtifactKey targetKey;
        try {
            callingKey = new ArtifactKey(sessionId);
        } catch (IllegalArgumentException e) {
            return "ERROR: Invalid session ID - not your fault - propagate up the chain from agent topology tools callAgent function.";
        }
        try {
            targetKey = new ArtifactKey(targetAgentKey);
        } catch (IllegalArgumentException e) {
            return "ERROR: Invalid target agent key format.";
        }

        // Resolve the calling agent's graph node (by nodeId or chatSessionKey)
        GraphNode callingNode = sessionKeyResolutionService.resolveNodeBySessionKey(sessionId);
        AgentType callingType = callingNode != null ? NodeMappings.agentTypeFromNode(callingNode) : null;

        // Resolve the target agent's graph node (by nodeId or chatSessionKey)
        GraphNode targetNode = sessionKeyResolutionService.resolveNodeBySessionKey(targetAgentKey);
        AgentType targetType = targetNode != null ? NodeMappings.agentTypeFromNode(targetNode) : null;
        String targetNodeId = targetNode != null ? targetNode.nodeId() : null;

        // Derive call chain from the workflow graph
        List<AgentModels.CallChainEntry> callChain = sessionKeyResolutionService.buildCallChainFromGraph(sessionId);

        // Validate the call
        AgentCommunicationService.CallValidationResult validation =
                agentCommunicationService.validateCall(callingKey, callingType, targetKey, targetType, callChain);

        if (!validation.valid()) {
            return validation.error();
        }

        // Find the originating AgentToAgentConversationNode (first in chain)
        // and the immediate parent (the node that called the current agent)
        String callingNodeId = callingNode != null ? callingNode.nodeId() : null;
        AgentToAgentConversationNode incomingCallNode = sessionKeyResolutionService.findIncomingCallNode(sessionId);
        String originatingAgentToAgentNodeId = null;
        if (incomingCallNode != null) {
            // We're in a chain — use the originating node from the incoming call,
            // or the incoming call itself if it's the first hop
            originatingAgentToAgentNodeId = incomingCallNode.originatingAgentToAgentNodeId() != null
                    ? incomingCallNode.originatingAgentToAgentNodeId()
                    : incomingCallNode.nodeId();
        }

        // Resolve OperationContext from the calling agent's active process
        OperationContext operationContext = resolveOperationContext(callingKey);
        if (operationContext == null) {
            return "ERROR: Could not resolve operation context for calling agent. Agent process not found.";
        }

        try {
            AgentModels.AgentRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(operationContext, AgentModels.AgentRequest.class);

            // Build the AgentToAgentRequest with all three tracking fields
            ArtifactKey contextId = callingKey.createChild();
            AgentModels.AgentToAgentRequest request = AgentModels.AgentToAgentRequest.builder()
                    .contextId(contextId)
                    .sourceAgentKey(callingKey)
                    .sourceAgentType(callingType)
                    .targetAgentKey(targetKey)
                    .targetAgentType(targetType)
                    .message(message)
                    .callChain(callChain)
                    .callingNodeId(callingNodeId)
                    .originatingAgentToAgentNodeId(originatingAgentToAgentNodeId)
                    .targetNodeId(targetNodeId)
                    .chatSessionKey(sessionId)
                    .build();

            // 1. Decorate request (StartWorkflowRequestDecorator creates the node here)
            AgentModels.AgentToAgentRequest enrichedRequest = decorateRequestResults.decorateRequest(
                    new DecorateRequestResults.DecorateRequestArgs<>(
                            request, operationContext,
                            AgentInterfaces.AGENT_NAME_AGENT_CALL,
                            AgentInterfaces.ACTION_AGENT_CALL,
                            AgentInterfaces.METHOD_CALL_AGENT,
                            lastRequest
                    ));

            // 2. Build and decorate prompt context
            BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(operationContext);
            DecoratorContext decoratorContext = new DecoratorContext(
                    operationContext,
                    AgentInterfaces.AGENT_NAME_AGENT_CALL,
                    AgentInterfaces.ACTION_AGENT_CALL,
                    AgentInterfaces.METHOD_CALL_AGENT,
                    lastRequest,
                    enrichedRequest
            );

            Map<String, Object> templateModel = new java.util.HashMap<>(Map.of(
                    "sourceAgentKey", callingKey.value(),
                    "message", message
            ));
            if (callingType != null) {
                templateModel.put("sourceAgentType", callingType.wireValue());
            }
            if (!CollectionUtils.isEmpty(callChain)) {
                templateModel.put("callChain", callChain);
            }

            PromptContext promptContext = promptContextFactory.build(
                    AgentType.AGENT_CALL,
                    enrichedRequest,
                    lastRequest,
                    enrichedRequest,
                    history,
                    AgentInterfaces.TEMPLATE_COMMUNICATION_AGENT_CALL,
                    templateModel,
                    operationContext,
                    decoratorContext
            );
            promptContext = decorateRequestResults.decoratePromptContext(
                    new DecorateRequestResults.DecoratePromptContextArgs(promptContext, decoratorContext)
            );

            // 3. Build and decorate tool context
            com.hayden.multiagentidelib.tool.ToolContext toolContext = decorateRequestResults.decorateToolContext(
                    new DecorateRequestResults.DecorateToolArgs(
                            com.hayden.multiagentidelib.tool.ToolContext.empty(),
                            enrichedRequest,
                            lastRequest,
                            operationContext,
                            AgentInterfaces.AGENT_NAME_AGENT_CALL,
                            AgentInterfaces.ACTION_AGENT_CALL,
                            AgentInterfaces.METHOD_CALL_AGENT
                    )
            );

            // 4. Call LLM through the target agent's session (routed by PromptContext.chatId())
            AgentModels.AgentCallRouting routing = llmRunner.runWithTemplate(
                    AgentInterfaces.TEMPLATE_COMMUNICATION_AGENT_CALL,
                    promptContext,
                    templateModel,
                    toolContext,
                    AgentModels.AgentCallRouting.class,
                    operationContext
            );

            // 5. Decorate routing result (WorkflowGraphResultDecorator completes the node here)
            routing = decorateRequestResults.decorateRouting(
                    new DecorateRequestResults.DecorateRoutingArgs<>(
                            routing, operationContext,
                            AgentInterfaces.AGENT_NAME_AGENT_CALL,
                            AgentInterfaces.ACTION_AGENT_CALL,
                            AgentInterfaces.METHOD_CALL_AGENT,
                            lastRequest
                    )
            );

            String responseText = routing != null ? routing.response() : null;
            if (responseText == null || responseText.isBlank()) {
                responseText = "Agent responded but returned no content.";
            }

            return responseText;
        } catch (Exception e) {
            log.error("Failed to call agent {}: {}", targetAgentKey, e.getMessage(), e);
            return "ERROR: Agent %s is down. Try an alternative route. Detail: %s"
                    .formatted(targetAgentKey, e.getMessage());
        }
    }

    private OperationContext resolveOperationContext(ArtifactKey key) {
        if (agentPlatform == null) {
            return null;
        }

        ArtifactKey searchThrough = key;
        while (searchThrough != null) {
            OperationContext ctx = java.util.Optional.ofNullable(agentPlatform.getAgentProcess(searchThrough.value()))
                    .map(process -> AgentInterfaces.buildOpContext(process, AgentInterfaces.AGENT_NAME_AGENT_CALL))
                    .orElse(null);
            if (ctx != null) {
                return ctx;
            }
            searchThrough = searchThrough.parent().orElse(null);
        }
        return null;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize to JSON", e);
            return "{\"error\": \"serialization failed\"}";
        }
    }
}
