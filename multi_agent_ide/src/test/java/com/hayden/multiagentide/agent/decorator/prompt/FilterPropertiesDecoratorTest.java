package com.hayden.multiagentide.agent.decorator.prompt;

import com.embabel.agent.api.common.nested.ObjectCreator;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.agent.*;
import com.hayden.multiagentidelib.prompt.PromptContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FilterPropertiesDecoratorTest {

    @Test
    void filtersAllRoutesExceptOriginRouteForInterruptResolution() {
        FilterPropertiesDecorator decorator = new FilterPropertiesDecorator();
        @SuppressWarnings("unchecked")
        ObjectCreator<AgentModels.InterruptRouting> creator = mock(ObjectCreator.class);
        when(creator.withAnnotationFilter(any())).thenReturn(creator);

        var ctx = buildInterruptContext(
                creator,
                ArtifactKey.createRoot(),
                AgentModels.OrchestratorRequest.builder().goal("g").phase("DISCOVERY").build()
        );

        decorator.decorate(ctx);

        verify(creator, times(14)).withAnnotationFilter(any());
        verify(creator, never()).withAnnotationFilter(OrchestratorRoute.class);
        verify(creator).withAnnotationFilter(PlanningRoute.class);
        verify(creator).withAnnotationFilter(TicketDispatchRoute.class);
    }

    @Test
    void eventRerouteOverridesOriginAndKeepsOnlyTargetRoute() {
        FilterPropertiesDecorator decorator = new FilterPropertiesDecorator();
        @SuppressWarnings("unchecked")
        ObjectCreator<AgentModels.InterruptRouting> creator = mock(ObjectCreator.class);
        when(creator.withAnnotationFilter(any())).thenReturn(creator);
        ArtifactKey contextId = ArtifactKey.createRoot();

        decorator.storeEvent(new Events.InterruptRequestEvent(
                "evt-1",
                Instant.now(),
                contextId.value(),
                AgentType.ORCHESTRATOR.wireValue(),
                AgentType.PLANNING_ORCHESTRATOR.wireValue(),
                Events.InterruptType.HUMAN_REVIEW,
                "reroute",
                java.util.List.of(),
                java.util.List.of(),
                "ctx"
        ));

        var ctx = buildInterruptContext(
                creator,
                contextId,
                AgentModels.OrchestratorRequest.builder().goal("g").phase("DISCOVERY").build()
        );

        decorator.decorate(ctx);

        verify(creator, times(14)).withAnnotationFilter(any());
        verify(creator, never()).withAnnotationFilter(PlanningRoute.class);
        verify(creator).withAnnotationFilter(OrchestratorRoute.class);
    }

    @Test
    void doesNothingForNonInterruptCurrentRequest() {
        FilterPropertiesDecorator decorator = new FilterPropertiesDecorator();
        @SuppressWarnings("unchecked")
        ObjectCreator<AgentModels.OrchestratorRouting> creator = mock(ObjectCreator.class);
        when(creator.withAnnotationFilter(any())).thenReturn(creator);

        PromptContext promptContext = PromptContext.builder()
                .currentContextId(ArtifactKey.createRoot())
                .previousRequest(AgentModels.OrchestratorRequest.builder().goal("g").phase("DISCOVERY").build())
                .currentRequest(AgentModels.OrchestratorRequest.builder().goal("g").phase("DISCOVERY").build())
                .metadata(Map.of())
                .build();

        var ctx = LlmCallDecorator.LlmCallContext.<AgentModels.OrchestratorRouting>builder()
                .promptContext(promptContext)
                .templateOperations(creator)
                .templateArgs(Map.of())
                .build();

        decorator.decorate(ctx);

        verify(creator, times(1)).withAnnotationFilter(any());
    }

    private static LlmCallDecorator.LlmCallContext<AgentModels.InterruptRouting> buildInterruptContext(
            ObjectCreator<AgentModels.InterruptRouting> creator,
            ArtifactKey contextId,
            AgentModels.AgentRequest previousRequest
    ) {
        PromptContext promptContext = PromptContext.builder()
                .currentContextId(contextId)
                .previousRequest(previousRequest)
                .currentRequest(AgentModels.InterruptRequest.OrchestratorInterruptRequest.builder()
                        .type(Events.InterruptType.HUMAN_REVIEW)
                        .reason("r")
                        .build())
                .metadata(Map.of())
                .build();
        return LlmCallDecorator.LlmCallContext.<AgentModels.InterruptRouting>builder()
                .promptContext(promptContext)
                .templateOperations(creator)
                .templateArgs(Map.of())
                .build();
    }
}
