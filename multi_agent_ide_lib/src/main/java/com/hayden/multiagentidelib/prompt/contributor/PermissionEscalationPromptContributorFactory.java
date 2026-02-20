package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adds guardrails for permission escalation when required work is blocked by sandbox/tool denial.
 */
@Component
public class PermissionEscalationPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        if (context.currentRequest() instanceof AgentModels.InterruptRequest) {
            return List.of();
        }

        return List.of(new PermissionEscalationPromptContributor());
    }

    public record PermissionEscalationPromptContributor() implements PromptContributor {

        private static final String TEMPLATE = """
                ## Permission Escalation Guardrail

                If a required tool action is rejected immediately (for example: permission denied,
                read-only filesystem, sandbox blocked, or "not allowed"), do not silently fail and do
                not stop the workflow only because of that denial.

                Instead, raise a permission request that explicitly asks for escalation:
                - explain the blocked action and why it is required to complete the task,
                - include concrete options (grant once, grant always, continue without permission, abort),
                - include any confirmations needed to proceed safely.

                Once permission is granted, continue the original plan.
                """;

        @Override
        public String name() {
            return "permission-escalation-guardrail-v1";
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
            return 48;
        }
    }
}
