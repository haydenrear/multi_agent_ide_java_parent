package com.hayden.multiagentide.agent.decorator.request;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import org.springframework.stereotype.Component;

@Component
public class SetGoalRequestDecorator implements DispatchedAgentRequestDecorator {
    @Override
    public int order() {
        return -50_000;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        if (request instanceof AgentModels.OrchestratorRequest)
            return request;
        var b = BlackboardHistory.getLastFromHistory(context.operationContext(), AgentModels.OrchestratorRequest.class);

        if (b != null)
            return (T) request.withGoal(b.goal());

        return request;
    }
}
