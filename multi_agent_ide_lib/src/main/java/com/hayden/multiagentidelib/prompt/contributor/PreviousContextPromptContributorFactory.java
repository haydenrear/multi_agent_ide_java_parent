package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import com.hayden.multiagentidelib.prompt.SimplePromptContributor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class PreviousContextPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.previousContext() == null) {
            return List.of();
        }
        AgentType agentType = context.agentType() != null ? context.agentType() : AgentType.ALL;
        String contribution = context.previousContext()
                .prettyPrint();
        if (contribution.isBlank()) {
            return List.of();
        }
        return List.of(new SimplePromptContributor(
                "previous-context",
                contribution,
                Set.of(agentType),
                25
        ));
    }

}
