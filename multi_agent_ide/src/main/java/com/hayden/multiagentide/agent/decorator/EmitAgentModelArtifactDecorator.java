package com.hayden.multiagentide.agent.decorator;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.artifacts.ArtifactEmissionService;
import com.hayden.multiagentide.artifacts.ExecutionScopeService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmitAgentModelArtifactDecorator implements RequestDecorator, ResultDecorator, FinalResultDecorator {

    private final ExecutionScopeService exec;

    private final ArtifactEmissionService emissionService;

    @Override
    public int order() {
        return RequestDecorator.super.order();
    }

    @Override
    public <T extends AgentModels.Routing> T decorateFinalResult(T t, FinalResultDecoratorContext context) {
        return t;
    }

    @Override
    public <T extends AgentModels.Routing> T decorate(T t, DecoratorContext context) {
        return t;
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorateFinalResult(T t, FinalResultDecoratorContext context) {
        emissionService.emitAgentModel(t, context.decoratorContext().hashContext());
        return t;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T t, DecoratorContext context) {
        emissionService.emitAgentModel(t, context.hashContext());
        return t;
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorate(T t, DecoratorContext context) {
        emissionService.emitAgentModel(t, context.hashContext());
        return t;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorateRequestResult(T t, DecoratorContext context) {
        emissionService.emitAgentModel(t, context.hashContext());
        return t;
    }
}
