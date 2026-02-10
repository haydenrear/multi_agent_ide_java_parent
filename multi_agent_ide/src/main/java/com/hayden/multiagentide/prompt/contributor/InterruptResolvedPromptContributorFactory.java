package com.hayden.multiagentide.prompt.contributor;

import com.google.common.collect.Lists;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import io.micrometer.common.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contributes review guidance when the current request is an interrupt
 * with AGENT_REVIEW type. Provides the LLM with instructions on how
 * to perform the review, including the interrupt reason and context.
 */
@Component
public class InterruptResolvedPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        if (!(context.currentRequest() instanceof AgentModels.InterruptRequest interruptRequest)) {
            return List.of();
        }
        if (!context.model().containsKey("interruptFeedback")
                || StringUtils.isBlank(String.valueOf(context.model().get("interruptFeedback")))) {
            return List.of();
        }

        return Lists.newArrayList(new InterruptReviewPromptContributor(interruptRequest, context.model()));
    }

    public record InterruptReviewPromptContributor(AgentModels.InterruptRequest interruptRequest,
                                                   Map<String, Object> interruptFeedback) implements PromptContributor {

        @Override
        public String name() {
            return "interrupt-review-resolution";
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

            String f = template()
                    .replace("{{interrupt_reason}}", reason)
                    .replace("{{context_for_decision}}", contextForDecision)
                    .replace("{{interrupt_feedback}}", String.valueOf(interruptFeedback.get("interruptFeedback")));

            return Optional.ofNullable(context.previousRequest())
                    .map(ar -> f.replace("{{last_request}}", """
                        Here is the agent request that routed to you for the review, for context, and so you will know where to route back to in your response:
                        Name: %s
                        Printed:
                        %s
                        """.formatted(ar.getClass().getName(), ar.prettyPrint())))
                    .orElse(f);
        }

        @Override
        public String template() {
            return """
                    ## Interrupt Review Resolution
                    
                    You are performing the routing after an interrupt resolution from a user. Your job is to route back to the appropriate agent
                    that requested the review. Please do not route back again to an interrupt request.
                    
                    ### Review Context
                    - **Interrupt Reason**: {{interrupt_reason}}
                    - **Decision Context**: {{context_for_decision}}
                    - **Decision**: {{interrupt_feedback}}
                   
                    Now that we have resolution, please route to the appropriate agent accordingly. Please do not reroute back to another interrupt
                    request - please route back to the agent that routed to you.
                    
                    Here is the request that routed to you:
                    
                    ---
                    
                    {{last_request}}
                    
                    ---
                    
                    Please route back to that agent with the given feedback provided in the goal field.
                    """;
        }

        @Override
        public int priority() {
            return 50;
        }
    }
}
