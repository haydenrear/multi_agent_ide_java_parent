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
                    Under no circumstances should you use any of the __platform tool calls.
                    Please instead use your file system tools provided:
                    
                    - read
                    - edit
                    - write
                    - bash
                    - killShell
                    - BashOutput
                    
                    to perform dev activities. Please do not assume that your structured output will
                    produce the tool calls. If you are asked to perform a task involving reading and writing,
                    please perform that task using tool calls.
                    """;
        }

        @Override
        public int priority() {
            return 10_000;
        }
    }

}
