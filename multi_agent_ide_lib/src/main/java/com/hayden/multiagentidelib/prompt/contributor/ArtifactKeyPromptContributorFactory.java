package com.hayden.multiagentidelib.prompt.contributor;

import com.google.common.collect.Lists;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class ArtifactKeyPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        return Lists.newArrayList(ArtifactKeyPromptContributor.INSTANCE);
    }

    public record ArtifactKeyPromptContributor() implements PromptContributor {

        public static final ArtifactKeyPromptContributor INSTANCE = new ArtifactKeyPromptContributor();

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
                    Please do not set any of the contextId field or property contextId on any result or type in your
                    JSON response. This is for internal purposes only.
                    """;
        }

        @Override
        public int priority() {
            return 10_000;
        }
    }

}
