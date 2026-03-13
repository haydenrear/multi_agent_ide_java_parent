package com.hayden.multiagentide.agent.decorator.prompt;

import com.hayden.multiagentidelib.agent.DecoratorContext;
import com.hayden.multiagentide.filter.service.FilterLayerCatalog;
import com.hayden.multiagentidelib.prompt.PromptContext;
import org.springframework.stereotype.Component;

@Component
public class DefaultPromptContextDecorator implements PromptContextDecorator {

    @Override
    public PromptContext decorate(PromptContext promptContext, DecoratorContext context) {
        return promptContext;
    }
}
