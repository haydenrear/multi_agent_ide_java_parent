package com.hayden.multiagentide.agent.decorator.request;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.decorator.prompt.PromptContextDecorator;
import com.hayden.multiagentide.agent.decorator.prompt.ToolContextDecorator;
import com.hayden.multiagentide.agent.decorator.result.ResultDecorator;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.DecoratorContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.tool.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DecorateRequestResults {

    @Autowired(required = false)
    @Lazy
    private List<RequestDecorator> requestDecorators = new ArrayList<>();

    @Autowired(required = false)
    @Lazy
    private List<PromptContextDecorator> promptContextDecorators = new ArrayList<>();

    @Autowired(required = false)
    @Lazy
    private List<ToolContextDecorator> toolContextDecorators = new ArrayList<>();

    @Autowired(required = false)
    @Lazy
    private List<ResultDecorator> resultDecorators = new ArrayList<>();

    // ---- arg records ----

    public record DecorateRequestArgs<T extends AgentModels.AgentRequest>(
            T request,
            OperationContext context,
            String agentName,
            String actionName,
            String methodName,
            AgentModels.AgentRequest lastRequest
    ) {}

    public record DecoratePromptContextArgs(
            PromptContext promptContext,
            DecoratorContext decoratorContext
    ) {}

    public record DecorateToolArgs(
            ToolContext toolContext,
            AgentModels.AgentRequest enrichedRequest,
            AgentModels.AgentRequest lastRequest,
            OperationContext context,
            String agentName,
            String actionName,
            String methodName
    ) {}

    public record DecorateResultArgs<T extends AgentModels.AgentResult>(
            T result,
            OperationContext context,
            String agentName,
            String actionName,
            String methodName,
            Artifact.AgentModel lastRequest
    ) {}

    // ---- delegate methods ----

    public <T extends AgentModels.AgentRequest> T decorateRequest(DecorateRequestArgs<T> args) {
        return AgentInterfaces.decorateRequest(
                args.request,
                args.context,
                requestDecorators,
                args.agentName,
                args.actionName,
                args.methodName,
                args.lastRequest);
    }

    public PromptContext decoratePromptContext(DecoratePromptContextArgs args) {
        return AgentInterfaces.decoratePromptContext(
                args.promptContext,
                promptContextDecorators,
                args.decoratorContext());
    }

    public ToolContext decorateToolContext(DecorateToolArgs args) {
        return AgentInterfaces.decorateToolContext(
                args.toolContext,
                args.enrichedRequest,
                args.lastRequest,
                args.context,
                toolContextDecorators,
                args.agentName,
                args.actionName,
                args.methodName);
    }

    public <T extends AgentModels.AgentResult> T decorateResult(DecorateResultArgs<T> args) {
        return AgentInterfaces.decorateResult(
                args.result,
                args.context,
                resultDecorators,
                args.agentName,
                args.actionName,
                args.methodName,
                args.lastRequest);
    }
}
