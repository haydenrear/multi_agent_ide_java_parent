package com.hayden.multiagentide.agent.decorator.prompt;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.filter.service.FilterLayerCatalog;
import com.hayden.multiagentidelib.prompt.PromptContext;
import org.springframework.stereotype.Component;

@Component
public class DefaultPromptContextDecorator implements PromptContextDecorator {

    @Override
    public PromptContext decorate(PromptContext promptContext, DecoratorContext context) {
        if (promptContext == null) {
            return null;
        }
        return promptContext.toBuilder()
                .metadata(FilterLayerCatalog.metadataForLlmCall(
                        promptContext.metadata(),
                        context == null ? null : context.agentName(),
                        context == null ? null : context.actionName(),
                        context == null ? null : context.methodName(),
                        promptContext.agentType(),
                        promptContext.currentRequest()
                ))
                .build();
    }
}
