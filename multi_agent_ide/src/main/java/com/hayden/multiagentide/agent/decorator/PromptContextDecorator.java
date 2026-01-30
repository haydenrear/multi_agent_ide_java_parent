package com.hayden.multiagentide.agent.decorator;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentidelib.prompt.PromptContext;

public interface PromptContextDecorator {

    default int order() {
        return 0;
    }

    default PromptContext decorate(PromptContext promptContext, DecoratorContext context) {
        return promptContext;
    }
}
