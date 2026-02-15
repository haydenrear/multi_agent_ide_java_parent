package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.*;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.hayden.multiagentidelib.prompt.contributor.NodeMappings.DISPLAY_NAMES;

@Component
public class PreviousContextPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.previousContext() == null) {
            return List.of();
        }

        return List.of(new PreviousContextPromptContributor(context.previousContext()));
    }


    public record PreviousContextPromptContributor(PreviousContext prev)
            implements PromptContributor {

        private static final String TEMPLATE = """
                --- Curated Workflow Previous Context ---
                
                You are currently at agent: {{current_request}}
                This agent just ran: {{previous_request}}
                
                This particular agent, {{current_request}}, has ran before, and here is the information
                being provided from it:
                
                {{previous_context}}
                """;

        @Override
        public String name() {
            return this.getClass().getSimpleName();
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            String template = template();
            return template
                    .replace("{{current_request}}", NodeMappings.displayName(context.currentRequest().getClass()))
                    .replace("{{previous_request}}", Optional.ofNullable(context.previousContext())
                            .flatMap(c -> Optional.ofNullable(DISPLAY_NAMES.get(c.getClass())))
                            .orElse("No previous request found"))
                    .replace("{{previous_context}}", prev.prettyPrint());
        }

        @Override
        public String template() {
            return TEMPLATE;
        }

        @Override
        public int priority() {
            return 10_000;
        }
    }


}
