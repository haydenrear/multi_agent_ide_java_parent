package com.hayden.multiagentide.service;

import com.embabel.agent.api.common.ContextualPromptElement;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.common.ai.prompt.PromptElement;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Default implementation of LlmRunner using Embabel's native prompt contribution pattern.
 * 
 * This implementation:
 * 1. Retrieves applicable PromptContributors based on AgentType
 * 2. Converts them to Embabel's ContextualPromptElement
 * 3. Uses withPromptElements() to inject dynamic content
 * 4. Uses withTemplate() for Jinja template rendering
 * 5. Returns structured responses via createObject()
 */
@Service
@RequiredArgsConstructor
public class DefaultLlmRunner implements LlmRunner {
    
    private final PromptContributorService promptContributorService;

    @Override
    public <T> T runWithTemplate(
            String templateName,
            PromptContext promptContext,
            Map<String, Object> model,
            Class<T> responseClass,
            OperationContext context
    ) {
        // Get applicable prompt contributors using the full PromptContext
        PromptElement[] contributors = promptContributorService.getContributors(promptContext)
                .toArray(ContextualPromptElement[]::new);

        var aiQuery = context.ai()
                .withDefaultLlm()
                .withPromptElements(contributors)
                .withTemplate(templateName);

        // Execute and return
        return aiQuery.createObject(responseClass, model);
    }
}
