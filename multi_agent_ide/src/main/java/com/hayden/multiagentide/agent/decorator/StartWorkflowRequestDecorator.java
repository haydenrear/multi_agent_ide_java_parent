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
public class StartWorkflowRequestDecorator implements RequestDecorator, DispatchedAgentRequestDecorator {

    private final WorkflowGraphService workflowGraphService;

    @Override
    public int order() {
        return 10_000;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        if (request == null || workflowGraphService == null) {
            return request;
        }

        OperationContext operationContext = context.operationContext();
        if (operationContext == null) {
            return request;
        }

        switch (request) {
            case AgentModels.OrchestratorRequest req -> storeRunning(operationContext, workflowGraphService.startOrchestrator(operationContext));
            case AgentModels.OrchestratorCollectorRequest req -> storeRunning(operationContext, workflowGraphService.startOrchestratorCollector(operationContext, req));
            case AgentModels.DiscoveryOrchestratorRequest req -> storeRunning(operationContext, workflowGraphService.startDiscoveryOrchestrator(operationContext, req));
            case AgentModels.DiscoveryCollectorRequest req -> storeRunning(operationContext, workflowGraphService.startDiscoveryCollector(operationContext, req));
            case AgentModels.PlanningOrchestratorRequest req -> storeRunning(operationContext, workflowGraphService.startPlanningOrchestrator(operationContext, req));
            case AgentModels.PlanningCollectorRequest req -> storeRunning(operationContext, workflowGraphService.startPlanningCollector(operationContext, req));
            case AgentModels.TicketOrchestratorRequest req -> storeRunning(operationContext, workflowGraphService.startTicketOrchestrator(operationContext, req));
            case AgentModels.TicketCollectorRequest req -> storeRunning(operationContext, workflowGraphService.startTicketCollector(operationContext, req));
            case AgentModels.ReviewRequest req -> storeRunning(operationContext, workflowGraphService.startReview(operationContext, req));
            case AgentModels.MergerRequest req -> storeRunning(operationContext, workflowGraphService.startMerge(operationContext, req));
            case AgentModels.DiscoveryAgentRequest req -> {
                DiscoveryOrchestratorNode parent = workflowGraphService.requireDiscoveryOrchestrator(operationContext);
                String focus = firstNonBlank(req.subdomainFocus(), "Discovery");
                String goal = firstNonBlank(req.goal());
                storeRunning(operationContext, workflowGraphService.startDiscoveryAgent(parent, goal, focus, req));
            }
            case AgentModels.PlanningAgentRequest req -> {
                PlanningOrchestratorNode parent = workflowGraphService.requirePlanningOrchestrator(operationContext);
                storeRunning(operationContext, workflowGraphService.startPlanningAgent(parent, req));
            }
            case AgentModels.TicketAgentRequest req -> {
                TicketOrchestratorNode parent = workflowGraphService.requireTicketOrchestrator(operationContext);
                storeRunning(operationContext, workflowGraphService.startTicketAgent(parent, req));
            }
            case AgentModels.InterruptRequest.ContextManagerInterruptRequest req -> workflowGraphService.handleContextManagerInterrupt(operationContext, req);
            case AgentModels.InterruptRequest.OrchestratorInterruptRequest req -> workflowGraphService.handleOrchestratorInterrupt(operationContext, req);
            case AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest req -> workflowGraphService.handleDiscoveryInterrupt(operationContext, req);
            case AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest req -> workflowGraphService.handlePlanningInterrupt(operationContext, req);
            case AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest req -> workflowGraphService.handleTicketInterrupt(operationContext, req);
            case AgentModels.InterruptRequest.ReviewInterruptRequest req -> workflowGraphService.handleReviewInterrupt(operationContext, req);
            case AgentModels.InterruptRequest.MergerInterruptRequest req -> workflowGraphService.handleMergerInterrupt(operationContext, req);
            case AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest req -> handleAgentInterrupt(operationContext, req, AgentTypeHint.DISCOVERY_AGENT);
            case AgentModels.InterruptRequest.PlanningAgentInterruptRequest req -> handleAgentInterrupt(operationContext, req, AgentTypeHint.PLANNING_AGENT);
            case AgentModels.InterruptRequest.TicketAgentInterruptRequest req -> handleAgentInterrupt(operationContext, req, AgentTypeHint.TICKET_AGENT);
            default -> {
            }
        }

        return request;
    }

    private void storeRunning(OperationContext context, GraphNode node) {
    }

    private void handleAgentInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest interruptRequest,
            AgentTypeHint hint
    ) {
        if (context == null || interruptRequest == null) {
            return;
        }
        GraphNode originNode = workflowGraphService.findNodeForContext(context)
                .orElseGet(() -> switch (hint) {
                    case DISCOVERY_AGENT -> workflowGraphService.requireDiscoveryOrchestrator(context);
                    case PLANNING_AGENT -> workflowGraphService.requirePlanningOrchestrator(context);
                    case TICKET_AGENT -> workflowGraphService.requireTicketOrchestrator(context);
                });
        workflowGraphService.handleAgentInterrupt(originNode, interruptRequest);
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

    private enum AgentTypeHint {
        DISCOVERY_AGENT,
        PLANNING_AGENT,
        TICKET_AGENT
    }
}
