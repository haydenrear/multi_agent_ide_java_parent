package com.hayden.multiagentide.agent.decorator.prompt;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.nested.TemplateOperations;
import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import lombok.Builder;

import java.util.Map;

public interface LlmCallDecorator {

    default int order() {
        return 0;
    }

    @Builder(toBuilder = true)
    record LlmCallContext(
            PromptContext promptContext,
            ToolContext tcc,
            TemplateOperations templateOperations,
            Map<String, Object> templateArgs,
            OperationContext op
    ) {}

    default LlmCallContext decorate(LlmCallContext promptContext) {
        return promptContext;
    }
}
