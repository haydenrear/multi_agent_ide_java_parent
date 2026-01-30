package com.hayden.multiagentide.agent.decorator;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.WorkflowGraphService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.nodes.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkflowGraphResultDecorator implements ResultDecorator, DispatchedAgentResultDecorator {

    private final WorkflowGraphService workflowGraphService;

    @Override
    public int order() {
        return 10_000;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends AgentModels.Routing> T decorate(T t, DecoratorContext context) {
        if (t == null || workflowGraphService == null) {
            return t;
        }

        OperationContext operationContext = context.operationContext();

        switch (t) {
            case AgentModels.OrchestratorRouting routing -> {
                OrchestratorNode running = resolveRunning(operationContext, OrchestratorNode.class,
                        () -> workflowGraphService.requireOrchestrator(operationContext));
                if (running != null) {
                    workflowGraphService.completeOrchestrator(running, routing);
                }
            }
            case AgentModels.OrchestratorCollectorRouting routing -> {
                CollectorNode running = resolveRunning(operationContext, CollectorNode.class,
                        () -> workflowGraphService.requireOrchestratorCollector(operationContext));
                if (running != null) {
                    workflowGraphService.completeOrchestratorCollector(operationContext, running, routing);
                }
            }
            case AgentModels.DiscoveryOrchestratorRouting routing -> {
                DiscoveryOrchestratorNode running = resolveRunning(operationContext, DiscoveryOrchestratorNode.class,
                        () -> workflowGraphService.requireDiscoveryOrchestrator(operationContext));
                if (running != null) {
                    workflowGraphService.completeDiscoveryOrchestrator(running, routing);
                }
            }
            case AgentModels.DiscoveryCollectorRouting routing -> {
                DiscoveryCollectorNode running = resolveRunning(operationContext, DiscoveryCollectorNode.class,
                        () -> workflowGraphService.requireDiscoveryCollector(operationContext));
                if (running != null) {
                    workflowGraphService.completeDiscoveryCollector(operationContext, running, routing);
                }
            }
            case AgentModels.PlanningOrchestratorRouting routing -> {
                PlanningOrchestratorNode running = resolveRunning(operationContext, PlanningOrchestratorNode.class,
                        () -> workflowGraphService.requirePlanningOrchestrator(operationContext));
                if (running != null) {
                    workflowGraphService.completePlanningOrchestrator(running, routing);
                }
            }
            case AgentModels.PlanningCollectorRouting routing -> {
                PlanningCollectorNode running = resolveRunning(operationContext, PlanningCollectorNode.class,
                        () -> workflowGraphService.requirePlanningCollector(operationContext));
                if (running != null) {
                    workflowGraphService.completePlanningCollector(operationContext, running, routing);
                }
            }
            case AgentModels.TicketOrchestratorRouting routing -> {
                TicketOrchestratorNode running = resolveRunning(operationContext, TicketOrchestratorNode.class,
                        () -> workflowGraphService.requireTicketOrchestrator(operationContext));
                if (running != null) {
                    workflowGraphService.completeTicketOrchestrator(running, routing);
                }
            }
            case AgentModels.TicketCollectorRouting routing -> {
                TicketCollectorNode running = resolveRunning(operationContext, TicketCollectorNode.class,
                        () -> workflowGraphService.requireTicketCollector(operationContext));
                if (running != null) {
                    workflowGraphService.completeTicketCollector(operationContext, running, routing);
                }
            }
            case AgentModels.ReviewRouting routing -> {
                ReviewNode running = resolveRunning(operationContext, ReviewNode.class,
                        () -> workflowGraphService.requireReviewNode(operationContext));
                if (running != null) {
                    workflowGraphService.completeReview(running, routing);
                }
            }
            case AgentModels.MergerRouting routing -> {
                MergeNode running = resolveRunning(operationContext, MergeNode.class,
                        () -> workflowGraphService.requireMergeNode(operationContext));
                if (running != null) {
                    AgentModels.MergerRequest lastRequest = operationContext.last(AgentModels.MergerRequest.class);
                    String combinedSummary = resolveMergeSummary(lastRequest, routing);
                    workflowGraphService.completeMerge(running, routing, combinedSummary);
                }
            }
            case AgentModels.DiscoveryAgentRouting routing -> {
                DiscoveryNode running = resolveRunning(operationContext, DiscoveryNode.class, () -> null);
                if (running != null && routing.agentResult() != null) {
                    workflowGraphService.completeDiscoveryAgent(routing.agentResult(), running.nodeId());
                }
            }
            case AgentModels.PlanningAgentRouting routing -> {
                PlanningNode running = resolveRunning(operationContext, PlanningNode.class, () -> null);
                if (running != null && routing.agentResult() != null) {
                    workflowGraphService.completePlanningAgent(running, routing.agentResult());
                }
            }
            case AgentModels.TicketAgentRouting routing -> {
                TicketNode running = resolveRunning(operationContext, TicketNode.class, () -> null);
                if (running != null && routing.agentResult() != null) {
                    workflowGraphService.completeTicketAgent(running, routing.agentResult());
                }
            }
            default -> {
            }
        }

        return t;
    }

    private <T extends GraphNode> T resolveRunning(
            OperationContext context,
            Class<T> type,
            java.util.function.Supplier<T> fallback
    ) {
        if (context == null) {
            return null;
        }
        T running = context.last(type);
        if (running != null) {
            return running;
        }
        return fallback != null ? fallback.get() : null;
    }

    private static String resolveMergeSummary(
            AgentModels.MergerRequest request,
            AgentModels.MergerRouting routing
    ) {
        String requestSummary = request != null ? request.prettyPrint(new com.hayden.multiagentidelib.agent.AgentContext.AgentSerializationCtx.MergeSummarySerialization()) : "";
        AgentModels.MergerAgentResult result = routing != null ? routing.mergerResult() : null;
        String resultSummary = result != null
                ? result.prettyPrint(new com.hayden.multiagentidelib.agent.AgentContext.AgentSerializationCtx.MergeSummarySerialization())
                : "";
        return firstNonBlank(requestSummary, resultSummary);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
