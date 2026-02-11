package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentModels.InterruptRequest;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects degenerate loops between orchestrator methods and their interrupt handlers.
 * When the same interrupt type has been routed to multiple times, this factory injects
 * a prompt contribution instructing the LLM to stop producing interrupts and instead
 * consolidate feedback and proceed to the correct next workflow step.
 */
@Slf4j
@Component
public class InterruptLoopBreakerPromptContributorFactory implements PromptContributorFactory {

    private static final int INTERRUPT_LOOP_THRESHOLD = 2;

    record InterruptLoopMapping(
            Class<? extends InterruptRequest> interruptType,
            Class<?> orchestratorType,
            String correctNextStep,
            String correctRoutingField
    ) {}

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.blackboardHistory() == null || context.currentRequest() == null) {
            return List.of();
        }

        if (context.currentRequest() instanceof InterruptRequest) {
            return List.of();
        }

        InterruptLoopMapping mapping = resolveMapping(context);

        if (mapping == null) {
            return List.of();
        }

        long interruptCount = context.blackboardHistory()
                .fromHistory(h -> h.countType(mapping.interruptType()));

        if (interruptCount < INTERRUPT_LOOP_THRESHOLD) {
            return List.of();
        }

        log.info("Executing interrupt loop breaker logic.");

        List<InterruptRequest> interruptHistory = context.blackboardHistory()
                .fromHistory(h -> h.getEntriesOfType(mapping.interruptType()).stream()
                        .<InterruptRequest>map(r -> r)
                        .toList());

        return List.of(new InterruptLoopBreakerPromptContributor(
                mapping, interruptCount, interruptHistory));
    }

    /**
     * Pattern-matches on the current request type and dynamically produces the
     * {@link InterruptLoopMapping}. The context manager case is computed on the fly
     * by inspecting blackboard history to determine the correct return route.
     */
    private static @Nullable InterruptLoopMapping resolveMapping(PromptContext context) {
        return switch (context.currentRequest()) {
            // Orchestrator level
            case AgentModels.OrchestratorRequest ignored -> new InterruptLoopMapping(
                    InterruptRequest.OrchestratorInterruptRequest.class,
                    AgentModels.OrchestratorRequest.class,
                    "DiscoveryOrchestratorRequest or OrchestratorCollectorRequest",
                    "discoveryOrchestratorRequest or collectorRequest"
            );
            case AgentModels.OrchestratorCollectorRequest ignored -> new InterruptLoopMapping(
                    InterruptRequest.OrchestratorCollectorInterruptRequest.class,
                    AgentModels.OrchestratorCollectorRequest.class,
                    "OrchestratorCollectorResult with a CollectorDecision",
                    "collectorResult"
            );

            // Discovery phase
            case AgentModels.DiscoveryOrchestratorRequest ignored -> new InterruptLoopMapping(
                    InterruptRequest.DiscoveryOrchestratorInterruptRequest.class,
                    AgentModels.DiscoveryOrchestratorRequest.class,
                    "DiscoveryAgentRequests to dispatch discovery agents",
                    "agentRequests"
            );
            case AgentModels.DiscoveryAgentRequest ignored -> new InterruptLoopMapping(
                    InterruptRequest.DiscoveryAgentInterruptRequest.class,
                    AgentModels.DiscoveryAgentRequest.class,
                    "DiscoveryAgentResult with discovery findings",
                    "agentResult"
            );
            case AgentModels.DiscoveryCollectorRequest ignored -> new InterruptLoopMapping(
                    InterruptRequest.DiscoveryCollectorInterruptRequest.class,
                    AgentModels.DiscoveryCollectorRequest.class,
                    "DiscoveryCollectorResult with a CollectorDecision",
                    "collectorResult"
            );
            case AgentModels.DiscoveryAgentRequests ignored -> new InterruptLoopMapping(
                    InterruptRequest.DiscoveryAgentDispatchInterruptRequest.class,
                    AgentModels.DiscoveryAgentRequests.class,
                    "DiscoveryCollectorRequest to consolidate results",
                    "collectorRequest"
            );

            // Planning phase
            case AgentModels.PlanningOrchestratorRequest ignored -> new InterruptLoopMapping(
                    InterruptRequest.PlanningOrchestratorInterruptRequest.class,
                    AgentModels.PlanningOrchestratorRequest.class,
                    "PlanningAgentRequests to dispatch planning agents",
                    "agentRequests"
            );
            case AgentModels.PlanningAgentRequest ignored -> new InterruptLoopMapping(
                    InterruptRequest.PlanningAgentInterruptRequest.class,
                    AgentModels.PlanningAgentRequest.class,
                    "PlanningAgentResult with planning tickets",
                    "agentResult"
            );
            case AgentModels.PlanningCollectorRequest ignored -> new InterruptLoopMapping(
                    InterruptRequest.PlanningCollectorInterruptRequest.class,
                    AgentModels.PlanningCollectorRequest.class,
                    "PlanningCollectorResult with a CollectorDecision",
                    "collectorResult"
            );
            case AgentModels.PlanningAgentRequests ignored -> new InterruptLoopMapping(
                    InterruptRequest.PlanningAgentDispatchInterruptRequest.class,
                    AgentModels.PlanningAgentRequests.class,
                    "PlanningCollectorRequest to consolidate results",
                    "planningCollectorRequest"
            );

            // Ticket phase
            case AgentModels.TicketOrchestratorRequest ignored -> new InterruptLoopMapping(
                    InterruptRequest.TicketOrchestratorInterruptRequest.class,
                    AgentModels.TicketOrchestratorRequest.class,
                    "TicketAgentRequests to dispatch ticket agents",
                    "agentRequests"
            );
            case AgentModels.TicketAgentRequest ignored -> new InterruptLoopMapping(
                    InterruptRequest.TicketAgentInterruptRequest.class,
                    AgentModels.TicketAgentRequest.class,
                    "TicketAgentResult with implementation results",
                    "agentResult"
            );
            case AgentModels.TicketCollectorRequest ignored -> new InterruptLoopMapping(
                    InterruptRequest.TicketCollectorInterruptRequest.class,
                    AgentModels.TicketCollectorRequest.class,
                    "TicketCollectorResult with a CollectorDecision",
                    "collectorResult"
            );
            case AgentModels.TicketAgentRequests ignored -> new InterruptLoopMapping(
                    InterruptRequest.TicketAgentDispatchInterruptRequest.class,
                    AgentModels.TicketAgentRequests.class,
                    "TicketCollectorRequest to consolidate results",
                    "ticketCollectorRequest"
            );

            // Review and Merger
            case AgentModels.ReviewRequest ignored -> new InterruptLoopMapping(
                    InterruptRequest.ReviewInterruptRequest.class,
                    AgentModels.ReviewRequest.class,
                    "ReviewAgentResult with review findings",
                    "reviewResult"
            );
            case AgentModels.MergerRequest ignored -> new InterruptLoopMapping(
                    InterruptRequest.MergerInterruptRequest.class,
                    AgentModels.MergerRequest.class,
                    "MergerAgentResult with merge validation",
                    "mergerResult"
            );

            // Context Manager - dynamically resolved from blackboard history
            case AgentModels.ContextManagerRequest ignored -> resolveContextManagerMapping(context);
            case AgentModels.ContextManagerRoutingRequest ignored -> resolveContextManagerMapping(context);
            case AgentModels.ResultsRequest ignored -> null;
            case InterruptRequest ignored -> null;
        };
    }

    /**
     * Resolves the context manager interrupt loop mapping by inspecting the blackboard history
     * to find which agent was last active, then directing routing back to that agent.
     */
    private static InterruptLoopMapping resolveContextManagerMapping(PromptContext context) {
        ContextManagerReturnRoutes.ReturnRouteMapping returnRoute =
                ContextManagerReturnRoutes.resolveReturnRoute(context.blackboardHistory());

        if (returnRoute != null) {
            return new InterruptLoopMapping(
                    InterruptRequest.ContextManagerInterruptRequest.class,
                    AgentModels.ContextManagerRequest.class,
                    returnRoute.displayName() + " (" + returnRoute.requestType().getSimpleName() + ")",
                    returnRoute.fieldName()
            );
        }

        // Fallback: no identifiable previous agent, advise routing to orchestrator
        return new InterruptLoopMapping(
                InterruptRequest.ContextManagerInterruptRequest.class,
                AgentModels.ContextManagerRequest.class,
                "Orchestrator with a clearly defined goal summarizing the current state",
                "orchestratorRequest"
        );
    }

    public record InterruptLoopBreakerPromptContributor(
            InterruptLoopMapping mapping,
            long interruptCount,
            List<InterruptRequest> interruptHistory
    ) implements PromptContributor {

        private static final String TEMPLATE = """
                ## CRITICAL: Interrupt Loop Detected
                
                You have routed to **{{interrupt_type}}** {{interrupt_count}} time(s) already.
                Each time, the interrupt was resolved and you were routed back here to \
                **{{orchestrator_type}}**, but you keep producing another interrupt instead of \
                progressing the workflow. This is not advisable! You should be advancing the process.
                
                ### Previous Interrupt Summary
                {{interrupt_summary}}
                
                ### REQUIRED ACTION
                **DO NOT** produce another `interruptRequest`. The information from the previous \
                interrupts has been incorporated. You MUST now proceed to the next workflow step.
                
                **Set `{{correct_routing_field}}`** to produce a **{{correct_next_step}}** and \
                continue the workflow. Consolidate any unresolved concerns from the interrupt \
                feedback into the goal or context fields of your routing output.
                
                If you route to another interrupt, the workflow will fail.
                """;

        @Override
        public String name() {
            return "interrupt-loop-breaker";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            String interruptTypeName = NodeMappings.displayName(mapping.interruptType());
            String orchestratorTypeName = NodeMappings.displayName(mapping.orchestratorType());

            StringBuilder summaryBuilder = new StringBuilder();
            if (interruptHistory != null && !interruptHistory.isEmpty()) {
                int idx = 1;
                for (InterruptRequest interrupt : interruptHistory) {
                    summaryBuilder.append(idx++).append(". ");
                    summaryBuilder.append("Type: ").append(interrupt.type());
                    if (interrupt.reason() != null && !interrupt.reason().isBlank()) {
                        summaryBuilder.append(" | Reason: ").append(interrupt.reason().trim());
                    }
                    summaryBuilder.append("\n");
                }
            } else {
                summaryBuilder.append("(No detailed interrupt history available)\n");
            }

            return TEMPLATE
                    .replace("{{interrupt_type}}", interruptTypeName)
                    .replace("{{interrupt_count}}", String.valueOf(interruptCount))
                    .replace("{{orchestrator_type}}", orchestratorTypeName)
                    .replace("{{interrupt_summary}}", summaryBuilder.toString().trim())
                    .replace("{{correct_routing_field}}", mapping.correctRoutingField())
                    .replace("{{correct_next_step}}", mapping.correctNextStep());
        }

        @Override
        public String template() {
            return TEMPLATE;
        }

        @Override
        public int priority() {
            return 5;
        }
    }
}
