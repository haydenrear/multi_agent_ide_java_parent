package com.hayden.multiagentide.filter.service;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.filter.repository.LayerEntity;
import com.hayden.multiagentide.filter.repository.LayerRepository;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.prompt.PromptContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class LayerIdResolverTest {

    @Mock
    private LayerRepository layerRepository;

    private LayerIdResolver layerIdResolver;

    @BeforeEach
    void setUp() {
        layerIdResolver = new LayerIdResolver(layerRepository);
        Set<String> knownLayerIds = new HashSet<>();
        FilterLayerCatalog.layerDefinitions().stream()
                .map(FilterLayerCatalog.LayerDefinition::layerId)
                .forEach(knownLayerIds::add);
        lenient().when(layerRepository.findByLayerId(anyString()))
                .thenAnswer(invocation -> {
                    String layerId = invocation.getArgument(0, String.class);
                    if (!knownLayerIds.contains(layerId)) {
                        return Optional.empty();
                    }
                    return Optional.of(LayerEntity.builder()
                            .layerId(layerId)
                            .build());
                });
    }

    @Test
    void resolvesPromptContributorLayerFromWorkflowAgentMetadata() {
        PromptContext promptContext = PromptContext.builder()
                .agentType(AgentType.ORCHESTRATOR)
                .currentRequest(AgentModels.OrchestratorRequest.builder().build())
                .metadata(new HashMap<>())
                .agentName(AgentInterfaces.WORKFLOW_AGENT_NAME)
                .actionName(AgentInterfaces.ACTION_ORCHESTRATOR)
                .methodName(AgentInterfaces.METHOD_COORDINATE_WORKFLOW)
                .agentType(AgentType.ORCHESTRATOR)
                .build();

        assertThat(layerIdResolver.resolveForPromptContributor(promptContext))
                .contains("workflow-agent/coordinateWorkflow");
    }

    @Test
    void resolvesRepeatedSubagentMethodUsingAgentNameAndMethodName() {
        PromptContext promptContext = PromptContext.builder()
                .agentType(AgentType.DISCOVERY_AGENT)
                .currentRequest(AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest.builder().build())
                .metadata(new HashMap<>())
                .agentName(AgentInterfaces.WORKFLOW_DISCOVERY_DISPATCH_SUBAGENT)
                .actionName(AgentInterfaces.ACTION_DISCOVERY_AGENT_INTERRUPT)
                .methodName(AgentInterfaces.METHOD_TRANSITION_TO_INTERRUPT_STATE)
                .agentType(AgentType.DISCOVERY_AGENT)
                .build();

        assertThat(layerIdResolver.resolveForPromptContributor(promptContext))
                .contains("discovery-dispatch-subagent/transitionToInterruptState");
    }

    @Test
    void resolvesControllerRequestsToSharedUiEventLayer() {
        assertThat(layerIdResolver.resolveForController("LlmDebugUiController"))
                .contains("controller-ui-event-poll");
    }

    @Test
    void resolvesActionEventsFromAgentAndActionNames() {
        Events.ActionStartedEvent event = new Events.ActionStartedEvent(
                "evt-1",
                Instant.now(),
                "node-1",
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_ORCHESTRATOR
        );

        assertThat(layerIdResolver.resolveForGraphEvent(event))
                .contains("workflow-agent/coordinateWorkflow");
    }
}
