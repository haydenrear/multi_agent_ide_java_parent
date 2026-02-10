package com.hayden.multiagentidelib.prompt.contributor;

import com.google.common.collect.Lists;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("goose")
public class GoosePromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        return Lists.newArrayList(GoosePromptContributor.INSTANCE);
    }

    public record GoosePromptContributor() implements PromptContributor {

        public static final GoosePromptContributor INSTANCE = new GoosePromptContributor();

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
            return template();
        }

        @Override
        public String template() {
            return """
                    Under no circumstances should you use the Subagent tool call.
                    This subagent tool call will not have any of the required permissions.
                    It will fail in every respect. Instead, use the routing of the agents
                    provided to perform the work.
                    
                    **Please do not use the Subagent tool call under any circumstances.**
                    """;
        }

        @Override
        public int priority() {
            return 10_000;
        }
    }

}
