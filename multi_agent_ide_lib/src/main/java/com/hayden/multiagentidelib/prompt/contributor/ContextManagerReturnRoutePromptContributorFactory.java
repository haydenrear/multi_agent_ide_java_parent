package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * When the current request is a {@link AgentModels.ContextManagerRequest}, this factory
 * inspects the blackboard history to determine which agent was last active and instructs
 * the LLM to route back to that agent via the correct field on
 * {@link AgentModels.ContextManagerResultRouting}.
 *
 * <p>If no previous agent can be identified, it instructs routing back to the orchestrator
 * with a clearly defined goal derived from the current context.</p>
 */
@Component
public class ContextManagerReturnRoutePromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null || context.blackboardHistory() == null) {
            return List.of();
        }

        if (!(context.currentRequest() instanceof AgentModels.ContextManagerRequest)) {
            return List.of();
        }

        AgentModels.AgentRequest lastAgent = ContextManagerReturnRoutes
                .findLastNonContextManagerRequest(context.blackboardHistory());

        if (lastAgent != null) {
            ContextManagerReturnRoutes.ReturnRouteMapping mapping =
                    ContextManagerReturnRoutes.findMapping(lastAgent.getClass());
            if (mapping != null) {
                return List.of(new ContextManagerReturnRoutePromptContributor(mapping, lastAgent));
            }
        }

        // No identifiable previous agent - instruct to route to orchestrator
        return List.of(new ContextManagerFallbackRoutePromptContributor());
    }

    public record ContextManagerReturnRoutePromptContributor(
            ContextManagerReturnRoutes.ReturnRouteMapping mapping,
            AgentModels.AgentRequest lastAgent
    ) implements PromptContributor {

        private static final String TEMPLATE = """
                ## Context Manager Return Route
                
                The last active agent before the Context Manager was invoked was \
                **{{display_name}}** (`{{request_type}}`).
                
                ### Previous Agent Context
                {{agent_context}}
                
                ### Required Routing
                You MUST set `{{field_name}}` on your `ContextManagerResultRouting` response \
                to route back to the **{{display_name}}**. Populate it with the relevant context \
                gathered from the context manager tools, incorporating what the agent needs to \
                continue its work.
                
                Do not route to a different agent unless you have a strong reason. The workflow \
                expects to return to the agent that requested context.
                """;

        @Override
        public String name() {
            return "context-manager-return-route";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            String agentContext = lastAgent.prettyPrintInterruptContinuation();
            if (agentContext == null || agentContext.isBlank()) {
                agentContext = lastAgent.prettyPrint();
            }
            if (agentContext == null || agentContext.isBlank()) {
                agentContext = "(No detailed context available)";
            }

            return TEMPLATE
                    .replace("{{display_name}}", mapping.displayName())
                    .replace("{{request_type}}", mapping.requestType().getSimpleName())
                    .replace("{{field_name}}", mapping.fieldName())
                    .replace("{{agent_context}}", agentContext.trim());
        }

        @Override
        public String template() {
            return TEMPLATE;
        }

        @Override
        public int priority() {
            return 10;
        }
    }

    public record ContextManagerFallbackRoutePromptContributor() implements PromptContributor {

        private static final String TEMPLATE = """
                ## Context Manager Return Route
                
                No previous agent could be identified from the blackboard history. \
                You should route back to the **Orchestrator** to re-establish workflow direction.
                
                ### Required Routing
                Set `orchestratorRequest` on your `ContextManagerResultRouting` response with:
                - A clearly defined `goal` that summarizes the current workflow state and what \
                  needs to happen next, based on the context you have gathered
                - Include any relevant context from your tools in the goal description
                
                This ensures the workflow resumes with clear direction rather than getting stuck.
                """;

        @Override
        public String name() {
            return "context-manager-fallback-route";
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
            return 10;
        }
    }
}
