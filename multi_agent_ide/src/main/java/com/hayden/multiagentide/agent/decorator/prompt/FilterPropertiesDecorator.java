package com.hayden.multiagentide.agent.decorator.prompt;

import org.springframework.stereotype.Component;

@Component
public class FilterPropertiesDecorator implements LlmCallDecorator {

    @Override
    public <T> LlmCallContext<T> decorate(LlmCallContext<T> promptContext) {
//        TODO: filter out particular properties based on context
//        return promptContext.toBuilder().templateOperations(promptContext.templateOperations().withAnnotationFilter(...));
        return promptContext;
    }
}
