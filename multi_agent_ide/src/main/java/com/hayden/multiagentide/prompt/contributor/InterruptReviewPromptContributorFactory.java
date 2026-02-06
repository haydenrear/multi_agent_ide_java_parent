package com.hayden.multiagentide.prompt.contributor;

import com.google.common.collect.Lists;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Contributes review guidance when the current request is an interrupt
 * with AGENT_REVIEW type. Provides the LLM with instructions on how
 * to perform the review, including the interrupt reason and context.
 */
@Component
public class InterruptReviewPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        if (!(context.currentRequest() instanceof AgentModels.InterruptRequest interruptRequest)) {
            return List.of();
        }

        if (interruptRequest.type() != Events.InterruptType.AGENT_REVIEW) {
            return List.of();
        }

        return Lists.newArrayList(new InterruptReviewPromptContributor(interruptRequest));
    }

    public record InterruptReviewPromptContributor(
            AgentModels.InterruptRequest interruptRequest
    ) implements PromptContributor {

        @Override
        public String name() {
            return "interrupt-review-guidance";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            String reason = interruptRequest.reason() != null ? interruptRequest.reason() : "";
            String contextForDecision = interruptRequest.contextForDecision() != null
                    ? interruptRequest.contextForDecision() : "";

            return template()
                    .replace("{{interrupt_reason}}", reason)
                    .replace("{{context_for_decision}}", contextForDecision);
        }

        @Override
        public String template() {
            return """
                    ## Agent Review Instructions
                    
                    You are performing an agent review for an interrupt request. Your role is to carefully
                    evaluate the content being reviewed and provide a thorough assessment.
                    
                    ### Review Context
                    - **Interrupt Reason**: {{interrupt_reason}}
                    - **Decision Context**: {{context_for_decision}}
                    
                    ### Review Guidelines
                    - Assess correctness: verify the content is logically sound and free of errors.
                    - Assess completeness: verify nothing important is missing from the output.
                    - If the content is correct and complete, respond with an approved status.
                    - If there are issues, clearly explain what is wrong and suggest corrections.
                    - Consider the broader workflow context when making your assessment.
                    - Be specific in your feedback so the originating agent can act on it.
                    """;
        }

        @Override
        public int priority() {
            return 50;
        }
    }
}
