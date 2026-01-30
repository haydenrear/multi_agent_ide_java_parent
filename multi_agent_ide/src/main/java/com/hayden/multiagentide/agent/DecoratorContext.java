package com.hayden.multiagentide.agent;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.Artifact;

/**
 * Context passed to decorators containing action metadata.
 */
public record DecoratorContext(
        OperationContext operationContext,
        String agentName,
        String actionName,
        String methodName,
        Artifact.AgentModel lastRequest,
        Artifact.HashContext hashContext
) {

    public DecoratorContext(OperationContext operationContext, String agentName, String actionName, String methodName, Artifact.AgentModel lastRequest) {
        this(operationContext, agentName, actionName, methodName, lastRequest, Artifact.HashContext.defaultHashContext());
    }
}
