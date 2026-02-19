package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RouteBackInterruptPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        if (context.currentRequest() instanceof AgentModels.InterruptRequest) {
            return List.of();
        }

        if (!(context.currentRequest() instanceof AgentModels.HasRouteBack)) {
            return List.of();
        }

        return List.of(new RouteBackInterruptPromptContributor());
    }

    public record RouteBackInterruptPromptContributor() implements PromptContributor {

        private static final String TEMPLATE = """
                ## Route-Back Clarification Guardrail

                If you are considering `ROUTE_BACK`, do not route back immediately.
                First emit an `interruptRequest` that clearly explains:
                - why route-back is required,
                - what is missing or blocked,
                - what context/action is needed before proceeding.

                After clarification is received:
                - if clarification confirms route-back is required, proceed with `ROUTE_BACK`,
                - include the clarified context in your routed request/collector output,
                - otherwise continue forward with normal flow.
                """;

        @Override
        public String name() {
            return "route-back-interrupt-guardrail";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            return TEMPLATE;
        }

        @Override
        public String template() {
            return TEMPLATE;
        }

        @Override
        public int priority() {
            return 45;
        }
    }
}

