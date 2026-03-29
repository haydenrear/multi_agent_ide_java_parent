package com.hayden.multiagentide.service;

import com.embabel.agent.api.common.ContextualPromptElement;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.common.textio.template.CompiledTemplate;
import com.embabel.common.textio.template.TemplateRenderer;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.multiagentide.agent.AgentInterfaces.MultiAgentIdeMetadata.AgentMetadata.AgentActionMetadata;
import com.hayden.multiagentide.agent.decorator.request.DecorateRequestResults;
import com.hayden.multiagentide.filter.prompt.FilteredPromptContributorAdapter;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.DecoratorContext;
import com.hayden.multiagentidelib.llm.LlmRunner;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContextFactory;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorAdapter;
import com.hayden.multiagentidelib.prompt.PromptContributorService;
import com.hayden.multiagentidelib.tool.ToolContext;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

        // 4. Publish interrupt and block
        permissionGate.publishInterrupt(
                args.interruptId(),
                args.originNodeId(),
                args.interruptType(),
                enrichedPrompt
        );

        IPermissionGate.InterruptResolution resolution = permissionGate.awaitInterruptBlocking(args.interruptId());
        String responseText = resolution != null ? resolution.getResolutionNotes() : null;
        if (responseText == null || responseText.isBlank()) {
            responseText = "Controller acknowledged but provided no response.";
        }

        return responseText;
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
