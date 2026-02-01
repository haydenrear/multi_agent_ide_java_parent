package com.hayden.multiagentide.agent;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.AgentProcess;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentide.service.InterruptService;
import com.hayden.multiagentide.service.LlmRunner;
import com.hayden.multiagentidelib.agent.*;
import com.hayden.multiagentidelib.prompt.ContextIdService;
import com.hayden.multiagentidelib.prompt.PromptAssembly;
import com.hayden.multiagentidelib.prompt.PromptContextFactory;
import com.hayden.multiagentidelib.service.RequestEnrichment;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowAgentRouteToContextManagerTest {

    @Test
    void routeToContextManager_buildsReturnRouteFromLastRequest() {
        EventBus eventBus = mock(EventBus.class);
        WorkflowGraphService workflowGraphService = mock(WorkflowGraphService.class);
        InterruptService interruptService = mock(InterruptService.class);
        ContextIdService contextIdService = mock(ContextIdService.class);
        PromptContextFactory promptContextFactory = mock(PromptContextFactory.class);
        RequestEnrichment requestEnrichment = mock(RequestEnrichment.class);
        LlmRunner llmRunner = mock(LlmRunner.class);
        ContextManagerTools contextManagerTools = mock(ContextManagerTools.class);
        BlackboardHistoryService blackboardHistoryService = new BlackboardHistoryService(new ArrayList<>());

        AgentInterfaces.WorkflowAgent workflowAgent = new AgentInterfaces.WorkflowAgent(
                workflowGraphService,
                interruptService,
                promptContextFactory,
                llmRunner,
                contextManagerTools,
                blackboardHistoryService
        );

        OperationContext context = mock(OperationContext.class);
        AgentProcess agentProcess = mock(AgentProcess.class);
        when(context.getAgentProcess()).thenReturn(agentProcess);
        when(requestEnrichment.enrich(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        AgentModels.OrchestratorRequest orchestratorRequest =
                new AgentModels.OrchestratorRequest(contextIdService.generate(UUID.randomUUID().toString(), AgentType.ORCHESTRATOR), "Need context", "DISCOVERY");
        AgentModels.ContextManagerRoutingRequest routingRequest =
                new AgentModels.ContextManagerRoutingRequest(
                        contextIdService.generate(UUID.randomUUID().toString(), AgentType.CONTEXT_MANAGER),
                        "Missing info",
                        AgentModels.ContextManagerRequestType.INTROSPECT_AGENT_CONTEXT
                );

        BlackboardHistory.History history = new BlackboardHistory.History(List.of(
                new BlackboardHistory.DefaultEntry(Instant.now(), "coordinateWorkflow", orchestratorRequest,
                        AgentModels.OrchestratorRequest.class),
                new BlackboardHistory.DefaultEntry(Instant.now(), "routeToContextManager", routingRequest,
                        AgentModels.ContextManagerRoutingRequest.class)
        ));

        var bh = new BlackboardHistory(history, "orchestrator", WorkflowGraphState.initial("orchestrator"));
        when(context.last(BlackboardHistory.class)).thenReturn(bh);

        AgentModels.ContextManagerRequest result = workflowAgent.routeToContextManager(routingRequest, context);

        assertThat(result.reason()).isEqualTo("Missing info");
        assertThat(result.type()).isEqualTo(AgentModels.ContextManagerRequestType.INTROSPECT_AGENT_CONTEXT);
        assertThat(result.returnToOrchestrator()).isNotNull();
        assertThat(result.returnToOrchestrator().goal()).isEqualTo("Need context");
    }
}
