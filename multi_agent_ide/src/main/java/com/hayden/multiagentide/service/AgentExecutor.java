package com.hayden.multiagentide.service;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.agent.AgentInterfaces.MultiAgentIdeMetadata.AgentMetadata.AgentActionMetadata;
import com.hayden.multiagentide.agent.decorator.request.DecorateRequestResults;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.DecoratorContext;
import com.hayden.multiagentidelib.llm.LlmRunner;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContextFactory;
import com.hayden.multiagentidelib.tool.ToolContext;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * TODO: colocate all of the decorator/llm logic here so as to abstract over it
 */
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

    private final DecorateRequestResults requestResultsDecorator;
    private final LlmRunner llmRunner;
    private final PromptContextFactory promptContextFactory;

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

}
