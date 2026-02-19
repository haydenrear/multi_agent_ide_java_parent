package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrchestratorRouteBackInterruptPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        if (context.currentRequest() instanceof AgentModels.InterruptRequest) {
            return List.of();
        }

        if (!(context.currentRequest() instanceof AgentModels.HasOrchestratorRequestRouteBack)) {
            return List.of();
        }

        return List.of(new OrchestratorRouteBackInterruptPromptContributor());
    }

    public record OrchestratorRouteBackInterruptPromptContributor() implements PromptContributor {

        private static final String TEMPLATE = """
                ## Orchestrator Route-Back Clarification Guardrail

                If you are considering routing to `orchestratorRequest`, do not route immediately.
                First emit an `interruptRequest` that explains:
                - why routing back to Orchestrator is required,
                - what is unresolved,
                - what decision/confirmation is needed.

                After clarification is received:
                - if clarification confirms route-back is required, route to Orchestrator with the clarified context,
                - otherwise continue with normal phase progression.
                """;

        @Override
        public String name() {
            return "orchestrator-route-back-interrupt-guardrail";
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
            return 46;
        }
    }
}

