package com.hayden.multiagentide.agent.decorator.request;

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
public class RegisterAndHideInputRequestDecorator implements DispatchedAgentRequestDecorator, RequestDecorator {

    private final BlackboardHistoryService blackboardHistoryService;

    @Override
    public int order() {
        return 10_000;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        if (request == null) {
            return null;
        }

        blackboardHistoryService.registerAndHideInput(
                context.operationContext(),
                context.methodName(),
                request
        );
        
        return request;
    }
}
