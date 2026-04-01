package com.hayden.multiagentide.service;

import com.embabel.agent.api.common.ContextualPromptElement;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.common.textio.template.CompiledTemplate;
import com.embabel.common.textio.template.TemplateRenderer;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.AgentInterfaces.MultiAgentIdeMetadata.AgentMetadata.AgentActionMetadata;
import com.hayden.multiagentide.agent.decorator.request.DecorateRequestResults;
import com.hayden.multiagentide.embabel.EmbabelUtil;
import com.hayden.multiagentide.filter.prompt.FilteredPromptContributorAdapter;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.DecoratorContext;
import com.hayden.multiagentidelib.llm.LlmRunner;
import com.hayden.multiagentidelib.model.nodes.GraphNode;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContextFactory;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorAdapter;
import com.hayden.multiagentidelib.prompt.PromptContributorService;
import com.hayden.multiagentidelib.prompt.contributor.NodeMappings;
import com.hayden.multiagentidelib.tool.ToolContext;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Colocates decorator/LLM/interrupt logic so callers don't duplicate the pipeline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentExecutor {

    @Builder(toBuilder = true)
    public record AgentExecutorArgs<T extends AgentModels.AgentRequest, U extends AgentModels.AgentRouting, V extends AgentModels.AgentResult, RES>(
            Class<RES> responseClazz,
            AgentActionMetadata<T, U, V> agentActionMetadata,
            AgentModels.AgentRequest previousRequest,
            T currentRequest,
            Map<String, Object> templateModel,
            OperationContext operationContext,
            ToolContext baseToolContext
    ) {}

    @Builder(toBuilder = true)
    public record ControllerExecutionArgs(
            AgentActionMetadata<AgentModels.AgentToControllerRequest, ?, ?> agentActionMetadata,
            AgentModels.AgentRequest previousRequest,
            AgentModels.AgentToControllerRequest currentRequest,
            Map<String, Object> templateModel,
            OperationContext operationContext,
            String interruptId,
            String originNodeId,
            Events.InterruptType interruptType
    ) {}

    private final DecorateRequestResults requestResultsDecorator;
    private final LlmRunner llmRunner;
    private final PromptContextFactory promptContextFactory;
    private final ObjectProvider<PromptContributorService> promptContributorServiceProvider;
    private final PermissionGate permissionGate;
    private final InterruptService interruptService;
    private final AgentPlatform agentPlatform;
    private final EventBus eventBus;
    private final GraphRepository graphRepository;

    public <T extends AgentModels.AgentRequest, U extends AgentModels.AgentRouting, V extends AgentModels.AgentResult> U run(
            AgentExecutorArgs<T, U, V, U> args
    ) {
        var meta = args.agentActionMetadata();
        var context = args.operationContext();

        // 1. Decorate request
        T enrichedRequest = requestResultsDecorator.decorateRequest(
                new DecorateRequestResults.DecorateRequestArgs<>(
                        args.currentRequest(), context, meta.agentName(), meta.actionName(),
                        meta.methodName(), args.previousRequest()
                ));

        // 2. Build/decorate prompt context
        BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);
        DecoratorContext decoratorContext = new DecoratorContext(
                context, meta.agentName(), meta.actionName(), meta.methodName(),
                args.previousRequest(), enrichedRequest
        );
        PromptContext promptContext = promptContextFactory.build(
                meta.agentType(), enrichedRequest, args.previousRequest(), enrichedRequest,
                history, meta.template(), args.templateModel(), context, decoratorContext
        );
        promptContext = requestResultsDecorator.decoratePromptContext(
                new DecorateRequestResults.DecoratePromptContextArgs(promptContext, decoratorContext)
        );

        // 3. Build/decorate tool context
        ToolContext baseToolCtx = args.baseToolContext() != null ? args.baseToolContext() : ToolContext.empty();
        ToolContext toolContext = requestResultsDecorator.decorateToolContext(
                new DecorateRequestResults.DecorateToolArgs(
                        baseToolCtx, enrichedRequest, args.previousRequest(), context,
                        meta.agentName(), meta.actionName(), meta.methodName()
                )
        );

        // 4. Call LLM
        U result = llmRunner.runWithTemplate(
                meta.template(), promptContext, args.templateModel(), toolContext,
                args.responseClazz(), context
        );

        // 5. Decorate result
        DecorateRequestResults.DecorateRoutingArgs<U> uDecorateRoutingArgs = new DecorateRequestResults.DecorateRoutingArgs<U>(
                result, context, meta.agentName(),
                meta.actionName(), meta.methodName(), args.previousRequest()
        );

        U decorated = requestResultsDecorator.decorateRouting(uDecorateRoutingArgs);
        return decorated;
    }

    /**
     * Runs the full decoration pipeline (request → prompt context → prompt contributors)
     * but instead of calling the LLM, publishes an interrupt with the rendered prompt text
     * and blocks until the interrupt is resolved. Returns the resolution text.
     */
    public String controllerExecution(
            ControllerExecutionArgs args
    ) {
        var meta = args.agentActionMetadata();
        var context = args.operationContext();

        // 1. Decorate request
        AgentModels.AgentToControllerRequest enrichedRequest = requestResultsDecorator.decorateRequest(
                new DecorateRequestResults.DecorateRequestArgs<>(
                        args.currentRequest(), context, meta.agentName(), meta.actionName(),
                        meta.methodName(), args.previousRequest()
                ));

        // 2. Build/decorate prompt context
        BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);
        DecoratorContext decoratorContext = new DecoratorContext(
                context, meta.agentName(), meta.actionName(), meta.methodName(),
                args.previousRequest(), enrichedRequest
        );
        PromptContext promptContext = promptContextFactory.build(
                meta.agentType(), enrichedRequest, args.previousRequest(), enrichedRequest,
                history, meta.template(), args.templateModel(), context, decoratorContext
        );
        promptContext = requestResultsDecorator.decoratePromptContext(
                new DecorateRequestResults.DecoratePromptContextArgs(promptContext, decoratorContext)
        );

        // 3. Assemble prompt: rendered template + prompt contributors
        String enrichedPrompt = assemblePrompt(promptContext, args.templateModel(), context);

        // 4. Publish interrupt (with interruptibleContext on origin node) and block
        return interruptService.publishAndAwaitControllerInterrupt(
                args.interruptId(),
                args.originNodeId(),
                args.interruptType(),
                enrichedPrompt
        );
    }

    @Builder(toBuilder = true)
    public record ControllerResponseArgs(
            String interruptId,
            String originNodeId,
            String message,
            String checklistAction,
            boolean expectResponse,
            String targetAgentKey
    ) {}

    public record ControllerResponseResult(
            boolean resolved,
            String errorMessage
    ) {
        public static ControllerResponseResult success() {
            return new ControllerResponseResult(true, null);
        }
        public static ControllerResponseResult error(String message) {
            return new ControllerResponseResult(false, message);
        }
    }

    /**
     * Mirror of {@link #controllerExecution}: processes the controller's response back to the agent
     * through the full decoration pipeline (request → prompt context → template + prompt contributors),
     * resolves the interrupt with the rendered text, and emits observability events.
     *
     * Key hierarchy: The interruptId IS the contextId of the original AgentToControllerRequest
     * (callingKey.createChild()). The controller response is a child of that conversation node:
     * - contextId = interruptKey.createChild() (new child under the conversation)
     * - chatId = interruptKey.parent() (the agent's session key — target of this response)
     * - sourceKey = same as contextId (controller's side of the conversation)
     */
    public ControllerResponseResult controllerResponseExecution(ControllerResponseArgs args) {
        // 1. Derive key hierarchy from the interruptId
        //    interruptId = callingKey.createChild() (set in AgentTopologyTools.callController)
        ArtifactKey interruptKey = new ArtifactKey(args.interruptId());
        ArtifactKey contextId = interruptKey.createChild();
        ArtifactKey agentChatId = interruptKey.parent().orElse(interruptKey);

        // 2. Resolve OperationContext from the origin node's artifact key
        ArtifactKey originKey = new ArtifactKey(args.originNodeId());
        OperationContext operationContext = EmbabelUtil.resolveOperationContext(
                agentPlatform, originKey, AgentInterfaces.AGENT_NAME_CONTROLLER_RESPONSE);

        // 3. Resolve target agent type from graph
        GraphNode targetNode = args.targetAgentKey() != null
                ? graphRepository.findById(args.targetAgentKey()).orElse(null)
                : graphRepository.findById(args.originNodeId()).orElse(null);
        AgentType targetAgentType = targetNode != null ? NodeMappings.agentTypeFromNode(targetNode) : null;

        if (operationContext == null) {
            return ControllerResponseResult.error(
                    "No OperationContext available for controller response to " + args.originNodeId()
                            + ". Cannot render response template.");
        }

        // 4. Retrieve the AgentToControllerRequest that initiated this conversation as lastRequest
        AgentModels.AgentRequest lastRequest = BlackboardHistory.getLastFromHistory(
                operationContext, AgentModels.AgentToControllerRequest.class);
        if (lastRequest == null) {
            return ControllerResponseResult.error(
                    "No AgentToControllerRequest found in blackboard history for " + args.originNodeId()
                            + ". Cannot process controller response without the originating request.");
        }

        // 5. Build template model
        Map<String, Object> templateModel = new HashMap<>();
        templateModel.put("message", args.message());
        templateModel.put("targetAgentKey", args.targetAgentKey() != null ? args.targetAgentKey() : args.originNodeId());
        if (targetAgentType != null) {
            templateModel.put("targetAgentType", targetAgentType.wireValue());
        }
        if (args.checklistAction() != null) {
            templateModel.put("checklistAction", args.checklistAction());
        }

        // 6. Build ControllerToAgentRequest with correct key hierarchy
        AgentModels.ChecklistAction checklistActionRecord = args.checklistAction() != null
                ? new AgentModels.ChecklistAction(args.checklistAction(), null, null)
                : null;
        ArtifactKey targetKey = args.targetAgentKey() != null ? new ArtifactKey(args.targetAgentKey()) : originKey;
        AgentModels.ControllerToAgentRequest request = AgentModels.ControllerToAgentRequest.builder()
                .contextId(contextId)
                .sourceKey(contextId)
                .targetAgentKey(targetKey)
                .targetAgentType(targetAgentType)
                .chatId(agentChatId)
                .message(args.message())
                .checklistAction(checklistActionRecord)
                .build();

        // 7. Render through decoration pipeline
        var meta = AgentInterfaces.MultiAgentIdeMetadata.AgentMetadata.RESPOND_TO_AGENT;

        AgentModels.ControllerToAgentRequest enrichedRequest = requestResultsDecorator.decorateRequest(
                new DecorateRequestResults.DecorateRequestArgs<>(
                        request, operationContext, meta.agentName(), meta.actionName(),
                        meta.methodName(), lastRequest
                ));

        BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(operationContext);
        DecoratorContext decoratorContext = new DecoratorContext(
                operationContext, meta.agentName(), meta.actionName(), meta.methodName(),
                lastRequest, enrichedRequest
        );
        PromptContext promptContext = promptContextFactory.build(
                meta.agentType(), enrichedRequest, lastRequest, enrichedRequest,
                history, meta.template(), templateModel, operationContext, decoratorContext
        );
        promptContext = requestResultsDecorator.decoratePromptContext(
                new DecorateRequestResults.DecoratePromptContextArgs(promptContext, decoratorContext)
        );

        String resolvedMessage = assemblePrompt(promptContext, templateModel, operationContext);

        // 8. Resolve the interrupt with the fully rendered text
        boolean resolved = permissionGate.resolveInterrupt(
                args.interruptId(),
                IPermissionGate.ResolutionType.RESOLVED,
                resolvedMessage,
                (IPermissionGate.InterruptResult) null
        );

        if (!resolved) {
            return ControllerResponseResult.error("Failed to resolve interrupt");
        }

        // 9. Emit observability event
        eventBus.publish(new Events.AgentCallEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                args.originNodeId(),
                Events.AgentCallEventType.RETURNED,
                "controller",
                null,
                args.originNodeId(),
                null,
                List.of(),
                null,
                null,
                args.message(),
                null,
                args.checklistAction()
        ));

        return ControllerResponseResult.success();
    }

    /**
     * Assembles the full prompt text: rendered template + prompt contributors in priority order.
     * Same pipeline as DefaultLlmRunner / PromptHealthCheckLlmCallDecorator, but returns the
     * text instead of sending it to an LLM.
     */
    private String assemblePrompt(PromptContext promptContext, Map<String, Object> templateModel, OperationContext context) {
        StringBuilder sb = new StringBuilder();

        // 1. Render the template
        String templateText = renderTemplate(promptContext, templateModel, context);
        if (templateText != null && !templateText.isBlank()) {
            sb.append(templateText.trim());
        }

        // 2. Resolve and render prompt contributors
        PromptContributorService contributorService = promptContributorServiceProvider.getIfAvailable();
        List<ContextualPromptElement> elements = contributorService != null
                ? contributorService.getContributors(promptContext)
                : promptContext.promptContributors();

        List<PromptContributor> contributors = unwrapContributors(elements);
        contributors.sort(Comparator.comparingInt(PromptContributor::priority));

        for (int i = 0; i < contributors.size(); i++) {
            PromptContributor pc = contributors.get(i);
            String content;
            try {
                content = pc.contribute(promptContext);
            } catch (Exception e) {
                log.debug("Contributor {} threw during prompt assembly — skipping", pc.name(), e);
                continue;
            }
            if (content == null || content.isBlank()) {
                continue;
            }
            if (i == 0) {
                sb.append("\n\n--- start [").append(pc.name()).append("] ---\n");
            } else {
                sb.append("\n--- end [").append(contributors.get(i - 1).name()).append("] ");
                sb.append("--- start [").append(pc.name()).append("] ---\n");
            }
            sb.append(content.trim());
        }

        if (!contributors.isEmpty()) {
            sb.append("\n--- end [").append(contributors.getLast().name()).append("] ---");
        }

        String result = sb.toString().trim();
        if (result.isEmpty() && promptContext.currentRequest() != null) {
            String goal = promptContext.currentRequest().goalExtraction();
            if (goal != null && !goal.isBlank()) {
                return goal;
            }
            // Fall back to justification message for controller requests
            if (promptContext.currentRequest() instanceof AgentModels.AgentToControllerRequest acr
                    && acr.justificationMessage() != null) {
                return acr.justificationMessage();
            }
        }
        return result;
    }

    private String renderTemplate(PromptContext promptContext, Map<String, Object> templateModel, OperationContext context) {
        try {
            TemplateRenderer renderer = context.agentPlatform().getPlatformServices().getTemplateRenderer();
            if (renderer == null) {
                return null;
            }
            CompiledTemplate compiled = renderer.compileLoadedTemplate(promptContext.templateName());
            return compiled.render(templateModel);
        } catch (Exception e) {
            log.debug("Could not render template for controller execution", e);
            return null;
        }
    }

    private List<PromptContributor> unwrapContributors(List<ContextualPromptElement> elements) {
        List<PromptContributor> result = new ArrayList<>();
        if (elements == null) {
            return result;
        }
        for (var element : elements) {
            if (element instanceof FilteredPromptContributorAdapter f) {
                result.add(f.getContributor());
            } else if (element instanceof PromptContributorAdapter adapter) {
                result.add(adapter.getContributor());
            }
        }
        return result;
    }

}
