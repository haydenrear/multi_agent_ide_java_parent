package com.hayden.multiagentidelib.prompt;

import com.embabel.agent.api.common.ContextualPromptElement;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.agent.AgentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service that provides PromptContributors adapted for use with Embabel's
 * native prompt contribution system.
 * 
 * Usage:
 * <pre>
 * var contributors = promptContributorService.getContributors(AgentType.ORCHESTRATOR);
 * 
 * var result = context.ai()
 *     .withDefaultLlm()
 *     .withPromptElements(contributors)
 *     .withTemplate("workflow/orchestrator")
 *     .createObject(MyClass.class, model);
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptContributorService {
    
    private final PromptContributorRegistry registry;

    private final List<PromptContributorFactory> factories;

    private final PromptContributorAdapterFactory filteredPromptContributorAdapterFactory;

    @Autowired
    @Lazy
    private EventBus eventBus;
    
    /**
     * Get contributors for the given prompt context.
     * 
     * @param promptContext The prompt context
     * @return List of ContextualPromptElements
     */
    public List<ContextualPromptElement> getContributors(PromptContext promptContext) {
        AgentType agentType = promptContext.agentType();
        if (agentType == null) {
            promptContext = promptContext.toBuilder().agentType(AgentType.ALL).build();
        }

        return retrievePromptContributors(promptContext);
    }

    private @NonNull List<ContextualPromptElement> retrievePromptContributors(PromptContext promptContext) {
        List<PromptContributor> contributors = new java.util.ArrayList<>(registry.getContributors(promptContext));
        if (!CollectionUtils.isEmpty(factories)) {
            for (PromptContributorFactory factory : factories) {
                if (factory == null) {
                    continue;
                }
                try {
                    List<PromptContributor> created = factory.create(promptContext);
                    if (created != null && !created.isEmpty()) {
                        contributors.addAll(created);
                    }
                } catch (Exception e) {
                    log.error("Error {}.", e.getMessage(), e);
                    eventBus.publish(Events.NodeErrorEvent.err("Error with prompt contributor factory %s: %s.".formatted(factory.getClass().getName(), e.getMessage()),
                            promptContext.currentContextId()));
                }
            }
        }

        contributors.sort(
                Comparator.comparingInt(PromptContributor::priority)
                        .thenComparing(PromptContributor::name, String.CASE_INSENSITIVE_ORDER));

        return contributors.stream()
                .map(contributor -> filteredPromptContributorAdapterFactory.create(contributor, promptContext))
                .collect(Collectors.toList());
    }
}
