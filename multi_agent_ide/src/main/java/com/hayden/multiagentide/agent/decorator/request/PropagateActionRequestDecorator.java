package com.hayden.multiagentide.agent.decorator.request;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.propagation.integration.ActionRequestPropagationIntegration;
import com.hayden.multiagentidelib.agent.AgentModels;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PropagateActionRequestDecorator implements RequestDecorator, DispatchedAgentRequestDecorator {

    private final ActionRequestPropagationIntegration integration;

    @Override
    public int order() {
        return 100;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        return integration.propagate(request, context.agentName(), context.actionName(), context.methodName(), context.operationContext());
    }
}
