package com.hayden.multiagentide.agent.decorator.result;

import com.hayden.multiagentidelib.agent.DecoratorContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistoryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Request decorator that registers the input in blackboard history and hides it.
 * Uses order 0 (default) to run after emitActionStarted but before other decorators.
 */
@Component
@RequiredArgsConstructor
public class RegisterAndHideInputResultDecorator implements DispatchedAgentResultDecorator, ResultDecorator, FinalResultDecorator {

    private static final Logger log = LoggerFactory.getLogger(RegisterAndHideInputResultDecorator.class);

    private final BlackboardHistoryService blackboardHistoryService;

    /**
     * This one had to happen after every other decorator, because if any of
     * of the other decorators failed or threw an exception, then it wouldn't be able to
     * retry.
     */
    @Override
    public int order() {
        return 10_005;
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorate(T request, DecoratorContext context) {
        if (request == null) {
            return null;
        }

        log.info("hideInput (result) — agent={} action={} resultType={}",
                context.agentName(), context.actionName(),
                request.getClass().getSimpleName());
        blackboardHistoryService.hideInput(
                context.operationContext()
        );

        return request;
    }

    @Override
    public <T extends AgentModels.Routing> T decorate(T t, DecoratorContext context) {
        log.info("hideInput (routing) — agent={} action={} routingType={}",
                context.agentName(), context.actionName(),
                t != null ? t.getClass().getSimpleName() : "(null)");
        blackboardHistoryService.hideInput(
                context.operationContext()
        );

        return t;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorateRequestResult(T t, DecoratorContext context) {
        log.info("hideInput (requestResult) — agent={} action={} requestType={}",
                context.agentName(), context.actionName(),
                t != null ? t.getClass().getSimpleName() : "(null)");
        blackboardHistoryService.hideInput(
                context.operationContext()
        );

        return t;
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorateFinalResult(T t, FinalResultDecoratorContext context) {
        log.info("hideInput (finalResult) — agent={} action={} resultType={}",
                context.decoratorContext().agentName(), context.decoratorContext().actionName(),
                t != null ? t.getClass().getSimpleName() : "(null)");
        blackboardHistoryService.hideInput(
                context.decoratorContext().operationContext()
        );

        return t;
    }
}
