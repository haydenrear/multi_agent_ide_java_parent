package com.hayden.multiagentide.agent.decorator.result;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Request decorator that registers the input in blackboard history and hides it.
 * Uses order 0 (default) to run after emitActionStarted but before other decorators.
 */
@Component
@RequiredArgsConstructor
public class RegisterAndHideInputResultDecorator implements DispatchedAgentResultDecorator, ResultDecorator {

    private final BlackboardHistoryService blackboardHistoryService;

    @Override
    public int order() {
        return 10_000;
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorate(T request, DecoratorContext context) {
        if (request == null) {
            return null;
        }

        blackboardHistoryService.hideInput(
                context.operationContext()
        );
        
        return request;
    }

    @Override
    public <T extends AgentModels.Routing> T decorate(T t, DecoratorContext context) {
        blackboardHistoryService.hideInput(
                context.operationContext()
        );

        return t;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorateRequestResult(T t, DecoratorContext context) {
        blackboardHistoryService.hideInput(
                context.operationContext()
        );

        return t;
    }
}
