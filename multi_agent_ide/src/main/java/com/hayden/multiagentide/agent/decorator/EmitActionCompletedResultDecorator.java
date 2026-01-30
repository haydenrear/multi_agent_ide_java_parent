package com.hayden.multiagentide.agent.decorator;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.artifacts.ExecutionScopeService;
import com.hayden.multiagentide.embabel.EmbabelUtil;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Result decorator that emits ActionCompletedEvent after all other decorators have run.
 * Uses Integer.MAX_VALUE ordering to ensure it executes last with the fully decorated result.
 */
@Component
@RequiredArgsConstructor
public class EmitActionCompletedResultDecorator implements FinalResultDecorator, ResultDecorator {

    private final EventBus eventBus;

    private final ExecutionScopeService exec;

    @Override
    public int order() {
        return 10_000;
    }

    @Override
    public <T extends AgentModels.Routing> T decorate(T t, DecoratorContext context) {
        if (t == null || eventBus == null) {
            return t;
        }

        OperationContext operationContext = context.operationContext();
        String agentName = context.agentName();
        String actionName = context.actionName();

        // Handle unsubscription based on result type
        if (t instanceof AgentModels.OrchestratorCollectorRouting routing
                && routing.collectorResult() != null) {
            BlackboardHistory.unsubscribe(eventBus, operationContext);
        }
        if (t instanceof AgentModels.DiscoveryAgentRouting routing
                && routing.agentResult() != null) {
            BlackboardHistory.unsubscribe(eventBus, operationContext);
        }
        if (t instanceof AgentModels.PlanningAgentRouting routing
                && routing.agentResult() != null) {
            BlackboardHistory.unsubscribe(eventBus, operationContext);
        }
        if (t instanceof AgentModels.TicketAgentRouting routing
                && routing.agentResult() != null) {
            BlackboardHistory.unsubscribe(eventBus, operationContext);
        }

        // Emit the action completed event
        String nodeId = resolveNodeId(operationContext);
        String outcomeType = t.getClass().getSimpleName();
        eventBus.publish(new Events.ActionCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                agentName,
                actionName,
                outcomeType
        ));

        return t;
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorate(T t, DecoratorContext context) {
        if (t == null || eventBus == null) {
            return t;
        }

        OperationContext operationContext = context.operationContext();
        String agentName = context.agentName();
        String actionName = context.actionName();

        if (t instanceof AgentModels.OrchestratorCollectorResult) {
            BlackboardHistory.unsubscribe(eventBus, operationContext);
        }

        String nodeId = resolveNodeId(operationContext);
        String outcomeType = t.getClass().getSimpleName();
        eventBus.publish(new Events.ActionCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                agentName,
                actionName,
                outcomeType
        ));

        if (t instanceof AgentModels.OrchestratorCollectorResult res) {
            eventBus.publish(new Events.GoalCompletedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    nodeId,
                    EmbabelUtil.extractWorkflowRunId(context.operationContext()),
                    res
            ));

//          make sure this happens last
            exec.completeExecution(context.operationContext().getAgentProcess().getId(), Artifact.ExecutionStatus.COMPLETED);
        }

        return t;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorateRequestResult(T t, DecoratorContext context) {
        if (t == null || eventBus == null) {
            return t;
        }

        OperationContext operationContext = context.operationContext();
        String agentName = context.agentName();
        String actionName = context.actionName();

        String nodeId = resolveNodeId(operationContext);
        String outcomeType = t.getClass().getSimpleName();
        eventBus.publish(new Events.ActionCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                agentName,
                actionName,
                outcomeType
        ));

        return t;
    }

    private static String resolveNodeId(OperationContext context) {
        if (context == null || context.getProcessContext() == null) {
            return "unknown";
        }
        var options = context.getProcessContext().getProcessOptions();
        if (options == null) {
            return "unknown";
        }
        String contextId = options.getContextIdString();
        return contextId != null ? contextId : "unknown";
    }
}
