package com.hayden.multiagentide.agent.decorator.request;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.artifacts.ExecutionScopeService;
import com.hayden.multiagentide.embabel.EmbabelUtil;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.WorkflowGraphState;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Request decorator that emits ActionStartedEvent before action execution.
 * Uses Integer.MIN_VALUE ordering to ensure it runs first.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmitActionStartedRequestDecorator implements RequestDecorator, DispatchedAgentRequestDecorator {

    private final EventBus eventBus;

    private final ExecutionScopeService scopeService;

    @Override
    public int order() {
        return 10_000;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        if (eventBus == null) {
            return request;
        }

        OperationContext operationContext = context.operationContext();
        String agentName = context.agentName();
        String actionName = context.actionName();

        BlackboardHistory.ensureSubscribed(
                eventBus, operationContext,
                () -> WorkflowGraphState.initial(request.key().value()));

        String nodeId = request.key().value();

        eventBus.publish(new Events.ActionStartedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                agentName,
                actionName
        ));

        if (request instanceof AgentModels.OrchestratorRequest first
                && context.lastRequest() == null) {
            String workflowRunId = EmbabelUtil.extractWorkflowRunId(context.operationContext());
            ArtifactKey key = first.key();

            if (!Objects.equals(workflowRunId, key.value())) {
                log.error("Agent process ID must be the same as the first OrchestratorRequest.");
            }

            scopeService.startExecution(workflowRunId, key);
        }

        return request;
    }

}
