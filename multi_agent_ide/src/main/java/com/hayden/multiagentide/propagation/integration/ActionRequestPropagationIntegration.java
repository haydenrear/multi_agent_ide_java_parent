package com.hayden.multiagentide.propagation.integration;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.filter.service.FilterLayerCatalog;
import com.hayden.multiagentide.propagation.service.PropagationExecutionService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.propagation.model.PropagatorMatchOn;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class ActionRequestPropagationIntegration {

    @Autowired
    @Lazy
    private PropagationExecutionService propagationExecutionService;

    public <T extends AgentModels.AgentRequest> T propagate(T request,
                                                            String agentName,
                                                            String actionName,
                                                            String methodName,
                                                            OperationContext operationContext) {
        if (FilterLayerCatalog.isInternalAutomationAction(agentName, actionName, methodName)) {
            return request;
        }
        FilterLayerCatalog.resolveActionLayer(agentName, actionName, methodName)
                .ifPresent(layerId -> propagationExecutionService.execute(
                        layerId,
                        PropagatorMatchOn.ACTION_REQUEST,
                        request,
                        resolveNodeId(operationContext),
                        FilterLayerCatalog.canonicalActionName(agentName, actionName, methodName),
                        operationContext
                ));
        return request;
    }

    private String resolveNodeId(OperationContext context) {
        if (context == null || context.getProcessContext() == null || context.getProcessContext().getProcessOptions() == null) {
            return "unknown";
        }
        String contextId = context.getProcessContext().getProcessOptions().getContextIdString();
        return contextId == null ? "unknown" : contextId;
    }
}
