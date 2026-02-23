package com.hayden.multiagentide.agent.decorator.result;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.WorkflowGraphService;
import com.hayden.multiagentidelib.agent.AgentContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentPretty;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentidelib.model.nodes.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowGraphResultDecorator implements ResultDecorator, DispatchedAgentResultDecorator {

    private final WorkflowGraphService workflowGraphService;

    @Override
    public int order() {
        return 10_000;
    }

    @Override
    public <T extends AgentModels.Routing> T decorate(T t, DecoratorContext context) {
        if (t == null || workflowGraphService == null || context == null) {
            return t;
        }

        OperationContext operationContext = context.operationContext();

        switch (t) {
            case AgentModels.OrchestratorRouting routing -> {
                OrchestratorNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireOrchestrator(operationContext));
                if (running != null) {
                    workflowGraphService.pendingOrchestrator(running, routing);
                }
            }
            case AgentModels.OrchestratorCollectorRouting routing -> {
                CollectorNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireOrchestratorCollector(operationContext));
                if (running != null) {
                    workflowGraphService.pendingOrchestratorCollector(operationContext, running, routing);
                }
            }
            case AgentModels.DiscoveryOrchestratorRouting routing -> {
                DiscoveryOrchestratorNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireDiscoveryOrchestrator(operationContext));
                if (running != null) {
                    workflowGraphService.pendingDiscoveryOrchestrator(running, routing);
                }
            }
            case AgentModels.DiscoveryCollectorRouting routing -> {
                DiscoveryCollectorNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireDiscoveryCollector(operationContext));
                if (running != null) {
                    workflowGraphService.pendingDiscoveryCollector(operationContext, running, routing);
                }
            }
            case AgentModels.PlanningOrchestratorRouting routing -> {
                PlanningOrchestratorNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requirePlanningOrchestrator(operationContext));
                if (running != null) {
                    workflowGraphService.pendingPlanningOrchestrator(running, routing);
                }
            }
            case AgentModels.PlanningCollectorRouting routing -> {
                PlanningCollectorNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requirePlanningCollector(operationContext));
                if (running != null) {
                    workflowGraphService.pendingPlanningCollector(operationContext, running, routing);
                }
            }
            case AgentModels.TicketOrchestratorRouting routing -> {
                TicketOrchestratorNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireTicketOrchestrator(operationContext));
                if (running != null) {
                    workflowGraphService.pendingTicketOrchestrator(running, routing);
                }
            }
            case AgentModels.TicketCollectorRouting routing -> {
                TicketCollectorNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireTicketCollector(operationContext));
                if (running != null) {
                    workflowGraphService.pendingTicketCollector(operationContext, running, routing);
                }
            }
            case AgentModels.ReviewRouting routing -> {
                ReviewNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireReviewNode(operationContext));
                if (running != null) {
                    workflowGraphService.completeReview(running, routing);
                }
            }
            case AgentModels.MergerRouting routing -> {
                MergeNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireMergeNode(operationContext));
                if (running != null) {
                    AgentModels.MergerRequest lastRequest = operationContext.last(AgentModels.MergerRequest.class);
                    String combinedSummary = resolveMergeSummary(lastRequest, routing);
                    workflowGraphService.completeMerge(running, routing, combinedSummary);
                }
            }
            case AgentModels.DiscoveryAgentRouting routing -> {
                if (routing.interruptRequest() != null) {
                    GraphNode originNode = requireNode(operationContext, routing,
                            () -> workflowGraphService.requireDiscoveryDispatch(operationContext));
                    handleRoutingInterrupt(operationContext, routing.interruptRequest(), originNode, routing);
                } else if (routing.agentResult() != null) {
                    DiscoveryNode running = resolveRunning(context, routing, DiscoveryNode.class);
                    if (running == null) {
                        reportMissingNode(operationContext, routing, null);
                    } else {
                        workflowGraphService.completeDiscoveryAgent(routing.agentResult(), running.nodeId());
                    }
                }
            }
            case AgentModels.PlanningAgentRouting routing -> {
                if (routing.interruptRequest() != null) {
                    GraphNode originNode = requireNode(operationContext, routing,
                            () -> workflowGraphService.requirePlanningDispatch(operationContext));
                    handleRoutingInterrupt(operationContext, routing.interruptRequest(), originNode, routing);
                } else if (routing.agentResult() != null) {
                    PlanningNode running = resolveRunning(context, routing, PlanningNode.class);
                    if (running == null) {
                        reportMissingNode(operationContext, routing, null);
                    } else {
                        workflowGraphService.completePlanningAgent(running, routing.agentResult());
                    }
                }
            }
            case AgentModels.TicketAgentRouting routing -> {
                if (routing.interruptRequest() != null) {
                    GraphNode originNode = requireNode(operationContext, routing,
                            () -> workflowGraphService.requireTicketDispatch(operationContext));
                    handleRoutingInterrupt(operationContext, routing.interruptRequest(), originNode, routing);
                } else if (routing.agentResult() != null) {
                    TicketNode running = resolveRunning(context, routing, TicketNode.class);
                    if (running == null) {
                        reportMissingNode(operationContext, routing, null);
                    } else {
                        workflowGraphService.completeTicketAgent(running, routing.agentResult());
                    }
                }
            }
            case AgentModels.DiscoveryAgentDispatchRouting routing -> {
                GraphNode originNode = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireDiscoveryDispatch(operationContext));
                boolean interrupted = handleRoutingInterrupt(operationContext, routing.interruptRequest(), originNode,
                        routing);
                if (!interrupted) {
                    handleRoutingResult(operationContext, routing, originNode);
                }
            }
            case AgentModels.PlanningAgentDispatchRouting routing -> {
                GraphNode originNode = requireNode(operationContext, routing,
                        () -> workflowGraphService.requirePlanningDispatch(operationContext));
                boolean interrupted = handleRoutingInterrupt(operationContext, routing.interruptRequest(), originNode,
                        routing);
                if (!interrupted) {
                    handleRoutingResult(operationContext, routing, originNode);
                }
            }
            case AgentModels.TicketAgentDispatchRouting routing -> {
                GraphNode originNode = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireTicketDispatch(operationContext));
                boolean interrupted = handleRoutingInterrupt(operationContext, routing.interruptRequest(), originNode,
                        routing);
                if (!interrupted) {
                    handleRoutingResult(operationContext, routing, originNode);
                }
            }
            case AgentModels.ContextManagerResultRouting routing -> {
                GraphNode targetNode = resolveContextManagerRoutingTarget(operationContext, routing);
                boolean interrupted = handleRoutingInterrupt(operationContext, routing.interruptRequest(), targetNode,
                        routing);
                if (!interrupted) {
                    handleRoutingResult(operationContext, routing, targetNode);
                }
            }
            case AgentModels.InterruptRouting routing -> {
                GraphNode targetNode = resolveInterruptRoutingTarget(operationContext, routing);
                handleRoutingResult(operationContext, routing, targetNode);
            }
        }

        return t;
    }

    private <T extends GraphNode> T resolveRunning(
            DecoratorContext decoratorContext,
            AgentModels.Routing routing,
            Class<T> type
    ) {
        String nodeId = resolveRequestNodeId(decoratorContext, routing);
        if (nodeId == null || nodeId.isBlank()) {
            return null;
        }
        return workflowGraphService.findNodeById(nodeId, type).orElse(null);
    }

    private String resolveRequestNodeId(DecoratorContext decoratorContext, AgentModels.Routing routing) {
        if (decoratorContext == null) {
            return null;
        }
        if (!(decoratorContext.lastRequest() instanceof AgentContext requestContext)) {
            reportMissingNode(
                    decoratorContext.operationContext(),
                    routing,
                    new IllegalStateException("Missing request context in DecoratorContext")
            );
            return null;
        }
        ArtifactKey contextId = requestContext.contextId();
        if (contextId == null || contextId.value() == null || contextId.value().isBlank()) {
            reportMissingNode(
                    decoratorContext.operationContext(),
                    routing,
                    new IllegalStateException("Missing request contextId for " + routingType(routing))
            );
            return null;
        }
        return contextId.value();
    }

    private boolean handleRoutingInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest interruptRequest,
            GraphNode originNode,
            AgentModels.Routing routing
    ) {
        if (context == null || interruptRequest == null) {
            return false;
        }
        if (originNode == null) {
            reportMissingNode(context, routing, null);
            return false;
        }
        workflowGraphService.handleAgentInterrupt(originNode, interruptRequest);
        return true;
    }

    private void handleRoutingResult(
            OperationContext context,
            AgentModels.Routing routing,
            GraphNode node
    ) {
        if (context == null || routing == null) {
            return;
        }
        if (node == null) {
            reportMissingNode(context, routing, null);
            return;
        }
        workflowGraphService.pendingNode(node);
    }

    private <T extends GraphNode> T requireNode(
            OperationContext context,
            AgentModels.Routing routing,
            java.util.function.Supplier<T> requiredNode
    ) {
        if (requiredNode == null) {
            return null;
        }
        try {
            return requiredNode.get();
        } catch (RuntimeException e) {
            reportMissingNode(context, routing, e);
            return null;
        }
    }

    private GraphNode resolveInterruptRoutingTarget(
            OperationContext context,
            AgentModels.InterruptRouting routing
    ) {
        if (routing == null) {
            return null;
        }
        if (routing.orchestratorRequest() != null || routing.contextManagerRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireOrchestrator(context));
        }
        if (routing.orchestratorCollectorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireOrchestratorCollector(context));
        }
        if (routing.discoveryCollectorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireDiscoveryCollector(context));
        }
        if (routing.discoveryOrchestratorRequest() != null || routing.discoveryAgentRequests() != null) {
            return routing.discoveryAgentRequests() != null
                    ? requireNode(context, routing, () -> workflowGraphService.requireDiscoveryDispatch(context))
                    : requireNode(context, routing, () -> workflowGraphService.requireDiscoveryOrchestrator(context));
        }
        if (routing.planningCollectorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requirePlanningCollector(context));
        }
        if (routing.planningOrchestratorRequest() != null || routing.planningAgentRequests() != null) {
            return routing.planningAgentRequests() != null
                    ? requireNode(context, routing, () -> workflowGraphService.requirePlanningDispatch(context))
                    : requireNode(context, routing, () -> workflowGraphService.requirePlanningOrchestrator(context));
        }
        if (routing.ticketCollectorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireTicketCollector(context));
        }
        if (routing.ticketOrchestratorRequest() != null || routing.ticketAgentRequests() != null) {
            return routing.ticketAgentRequests() != null
                    ? requireNode(context, routing, () -> workflowGraphService.requireTicketDispatch(context))
                    : requireNode(context, routing, () -> workflowGraphService.requireTicketOrchestrator(context));
        }
        if (routing.reviewRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireReviewNode(context));
        }
        if (routing.mergerRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireMergeNode(context));
        }
        return null;
    }

    private GraphNode resolveContextManagerRoutingTarget(
            OperationContext context,
            AgentModels.ContextManagerResultRouting routing
    ) {
        if (routing == null) {
            return null;
        }
        if (routing.orchestratorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireOrchestrator(context));
        }
        if (routing.orchestratorCollectorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireOrchestratorCollector(context));
        }
        if (routing.discoveryCollectorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireDiscoveryCollector(context));
        }
        if (routing.discoveryOrchestratorRequest() != null
                || routing.discoveryAgentRequest() != null
                || routing.discoveryAgentRequests() != null
                || routing.discoveryAgentResults() != null) {
            if (routing.discoveryAgentRequest() != null || routing.discoveryAgentRequests() != null) {
                return requireNode(context, routing, () -> workflowGraphService.requireDiscoveryDispatch(context));
            }
            if (routing.discoveryAgentResults() != null) {
                return requireNode(context, routing, () -> workflowGraphService.requireDiscoveryCollector(context));
            }
            return requireNode(context, routing, () -> workflowGraphService.requireDiscoveryOrchestrator(context));
        }
        if (routing.planningCollectorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requirePlanningCollector(context));
        }
        if (routing.planningOrchestratorRequest() != null
                || routing.planningAgentRequest() != null
                || routing.planningAgentRequests() != null
                || routing.planningAgentResults() != null) {
            if (routing.planningAgentRequest() != null || routing.planningAgentRequests() != null) {
                return requireNode(context, routing, () -> workflowGraphService.requirePlanningDispatch(context));
            }
            if (routing.planningAgentResults() != null) {
                return requireNode(context, routing, () -> workflowGraphService.requirePlanningCollector(context));
            }
            return requireNode(context, routing, () -> workflowGraphService.requirePlanningOrchestrator(context));
        }
        if (routing.ticketCollectorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireTicketCollector(context));
        }
        if (routing.ticketOrchestratorRequest() != null
                || routing.ticketAgentRequest() != null
                || routing.ticketAgentRequests() != null
                || routing.ticketAgentResults() != null) {
            if (routing.ticketAgentRequest() != null || routing.ticketAgentRequests() != null) {
                return requireNode(context, routing, () -> workflowGraphService.requireTicketDispatch(context));
            }
            if (routing.ticketAgentResults() != null) {
                return requireNode(context, routing, () -> workflowGraphService.requireTicketCollector(context));
            }
            return requireNode(context, routing, () -> workflowGraphService.requireTicketOrchestrator(context));
        }
        if (routing.reviewRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireReviewNode(context));
        }
        if (routing.mergerRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireMergeNode(context));
        }
        return requireNode(context, routing, () -> workflowGraphService.requireOrchestrator(context));
    }

    private void reportMissingNode(OperationContext context, AgentModels.Routing routing, RuntimeException e) {
        String message = "Failed to resolve graph node for " + routingType(routing);
        if (e != null && e.getMessage() != null && !e.getMessage().isBlank()) {
            message = message + ": " + e.getMessage();
        }
        log.error(message, e);
        workflowGraphService.emitDecoratorError(context, message);
    }

    private static String routingType(AgentModels.Routing routing) {
        return routing != null ? routing.getClass().getSimpleName() : "UnknownRouting";
    }

    private static String resolveMergeSummary(
            AgentModels.MergerRequest request,
            AgentModels.MergerRouting routing
    ) {
        String requestSummary = request != null ? request.prettyPrint(new AgentPretty.AgentSerializationCtx.MergeSummarySerialization()) : "";
        AgentModels.MergerAgentResult result = routing != null ? routing.mergerResult() : null;
        String resultSummary = result != null
                ? result.prettyPrint(new AgentPretty.AgentSerializationCtx.MergeSummarySerialization())
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
