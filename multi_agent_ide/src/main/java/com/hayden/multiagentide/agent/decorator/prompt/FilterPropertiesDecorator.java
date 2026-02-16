package com.hayden.multiagentide.agent.decorator.prompt;

import com.embabel.agent.core.AgentProcess;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.agent.*;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class FilterPropertiesDecorator implements LlmCallDecorator {

    private static final Set<Class<? extends Annotation>> ALL_INTERRUPT_ROUTE_ANNOTATIONS = Set.of(
            OrchestratorRoute.class,
            DiscoveryRoute.class,
            PlanningRoute.class,
            TicketRoute.class,
            ReviewRoute.class,
            MergerRoute.class,
            ContextManagerRoute.class,
            OrchestratorCollectorRoute.class,
            DiscoveryCollectorRoute.class,
            PlanningCollectorRoute.class,
            TicketCollectorRoute.class,
            DiscoveryDispatchRoute.class,
            PlanningDispatchRoute.class,
            TicketDispatchRoute.class
    );

    private final ConcurrentMap<String, Events.InterruptRequestEvent> interruptEvents = new ConcurrentHashMap<>();

    public void storeEvent(Events.InterruptRequestEvent event) {
        if (event == null || event.nodeId() == null || event.nodeId().isBlank()) {
            return;
        }
        interruptEvents.putIfAbsent(event.nodeId(), event);
    }

    @Override
    public int order() {
        return Ordered.HIGHEST_PRECEDENCE + 50;
    }

    @Override
    public <T> LlmCallContext<T> decorate(LlmCallContext<T> ctx) {
        var operations = ctx.templateOperations();
        if (ctx == null || ctx.templateOperations() == null)
            return ctx;
        if (ctx.promptContext() == null) {
            return ctx.withTemplateOperations(operations.withAnnotationFilter(SkipPropertyFilter.class));
        }
        if (!(ctx.promptContext().currentRequest() instanceof AgentModels.InterruptRequest)) {
            return ctx.withTemplateOperations(operations.withAnnotationFilter(SkipPropertyFilter.class));
        }

        Class<? extends Annotation> targetRoute = resolveTargetRoute(ctx);
        if (targetRoute == null) {
            return ctx.withTemplateOperations(operations.withAnnotationFilter(SkipPropertyFilter.class));
        }

        Set<Class<? extends Annotation>> toFilter = new LinkedHashSet<>(ALL_INTERRUPT_ROUTE_ANNOTATIONS);
        toFilter.remove(targetRoute);
        for (Class<? extends Annotation> annotation : toFilter) {
            operations = operations.withAnnotationFilter(annotation);
        }

        return ctx.withTemplateOperations(operations.withAnnotationFilter(SkipPropertyFilter.class));
    }

    private <T> Class<? extends Annotation> resolveTargetRoute(LlmCallContext<T> ctx) {
        String nodeId = resolveNodeId(ctx);
        if (nodeId != null) {
            Events.InterruptRequestEvent event = interruptEvents.remove(nodeId);
            if (event != null) {
                Class<? extends Annotation> annotation = mapAgentTypeToRoute(event.rerouteToAgentType());
                if (annotation != null) {
                    return annotation;
                }
            }
        }
        return mapRequestToRoute(ctx.promptContext().previousRequest());
    }

    private <T> String resolveNodeId(LlmCallContext<T> ctx) {
        return Optional.ofNullable(ctx.promptContext().currentContextId())
                .map(c -> c.value())
                .filter(v -> !v.isBlank())
                .or(() -> Optional.ofNullable(ctx.op())
                        .map(op -> op.getAgentProcess())
                        .map(AgentProcess::getId)
                        .filter(v -> !v.isBlank()))
                .orElse(null);
    }

    private static Class<? extends Annotation> mapRequestToRoute(AgentModels.AgentRequest request) {
        if (request == null) {
            return null;
        }
        return switch (request) {
            case AgentModels.OrchestratorRequest ignored -> OrchestratorRoute.class;
            case AgentModels.DiscoveryOrchestratorRequest ignored -> DiscoveryRoute.class;
            case AgentModels.PlanningOrchestratorRequest ignored -> PlanningRoute.class;
            case AgentModels.TicketOrchestratorRequest ignored -> TicketRoute.class;
            case AgentModels.ReviewRequest ignored -> ReviewRoute.class;
            case AgentModels.MergerRequest ignored -> MergerRoute.class;
            case AgentModels.ContextManagerRequest ignored -> ContextManagerRoute.class;
            case AgentModels.ContextManagerRoutingRequest ignored -> ContextManagerRoute.class;
            case AgentModels.OrchestratorCollectorRequest ignored -> OrchestratorCollectorRoute.class;
            case AgentModels.DiscoveryCollectorRequest ignored -> DiscoveryCollectorRoute.class;
            case AgentModels.PlanningCollectorRequest ignored -> PlanningCollectorRoute.class;
            case AgentModels.TicketCollectorRequest ignored -> TicketCollectorRoute.class;
            case AgentModels.DiscoveryAgentRequests ignored -> DiscoveryDispatchRoute.class;
            case AgentModels.PlanningAgentRequests ignored -> PlanningDispatchRoute.class;
            case AgentModels.TicketAgentRequests ignored -> TicketDispatchRoute.class;
            case AgentModels.DiscoveryAgentRequest discoveryAgentRequest -> null;
            case AgentModels.PlanningAgentRequest planningAgentRequest -> null;
            case AgentModels.TicketAgentRequest ticketAgentRequest -> null;
            case AgentModels.DiscoveryAgentResults discoveryAgentResults -> null;
            case AgentModels.PlanningAgentResults planningAgentResults -> null;
            case AgentModels.TicketAgentResults ticketAgentResults -> null;
            case AgentModels.InterruptRequest interruptRequest -> null;
            case AgentModels.ResultsRequest resultsRequest -> null;
        };
    }

    private static Class<? extends Annotation> mapAgentTypeToRoute(String agentTypeValue) {
        AgentType agentType = AgentType.fromWireValue(agentTypeValue);
        if (agentType == null) {
            return null;
        }
        return switch (agentType) {
            case ORCHESTRATOR -> OrchestratorRoute.class;
            case DISCOVERY_ORCHESTRATOR -> DiscoveryRoute.class;
            case PLANNING_ORCHESTRATOR -> PlanningRoute.class;
            case TICKET_ORCHESTRATOR -> TicketRoute.class;
            case REVIEW_AGENT, REVIEW_RESOLUTION_AGENT -> ReviewRoute.class;
            case MERGER_AGENT -> MergerRoute.class;
            case CONTEXT_MANAGER -> ContextManagerRoute.class;
            case ORCHESTRATOR_COLLECTOR -> OrchestratorCollectorRoute.class;
            case DISCOVERY_COLLECTOR -> DiscoveryCollectorRoute.class;
            case PLANNING_COLLECTOR -> PlanningCollectorRoute.class;
            case TICKET_COLLECTOR -> TicketCollectorRoute.class;
            case DISCOVERY_AGENT_DISPATCH -> DiscoveryDispatchRoute.class;
            case PLANNING_AGENT_DISPATCH -> PlanningDispatchRoute.class;
            case TICKET_AGENT_DISPATCH -> TicketDispatchRoute.class;
            case ALL, DISCOVERY_AGENT, PLANNING_AGENT, TICKET_AGENT -> null;
        };
    }
}
