package com.hayden.multiagentide.integration;

import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.controller.OrchestrationController;
import com.hayden.multiagentide.model.DebugRun;
import com.hayden.multiagentide.repository.InMemoryEventStreamRepository;
import com.hayden.multiagentide.repository.InMemoryRunPersistenceCheckRepository;
import com.hayden.multiagentide.service.AgentControlService;
import com.hayden.multiagentide.service.DebugRunPersistenceValidationService;
import com.hayden.multiagentide.service.DebugRunQueryService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RunObservationIntegrationTest {

    @Test
    void returnsStableRunObservationAndPersistenceSummary() {
        OrchestrationController orchestrationController = mock(OrchestrationController.class);
        AgentControlService agentControlService = mock(AgentControlService.class);
        EventBus eventBus = mock(EventBus.class);
        InMemoryEventStreamRepository eventRepository = new InMemoryEventStreamRepository();

        when(orchestrationController.startGoalAsync(any()))
                .thenReturn(new OrchestrationController.StartGoalResponse("run-1"));

        DebugRunQueryService queryService = new DebugRunQueryService(
                orchestrationController,
                eventBus,
                eventRepository
        );
        DebugRunPersistenceValidationService persistenceService = new DebugRunPersistenceValidationService(
                queryService,
                new InMemoryRunPersistenceCheckRepository()
        );

        DebugRun run = queryService.startRun(new OrchestrationController.StartGoalRequest(
                "debug routing",
                "/tmp/repo",
                "main",
                "Run test"
        ));

        eventRepository.save(new Events.AddMessageEvent("m1", Instant.now(), "run-1", "hello"));
        eventRepository.save(new Events.NodeStatusChangedEvent(
                "s1",
                Instant.now(),
                "run-1",
                Events.NodeStatus.RUNNING,
                Events.NodeStatus.COMPLETED,
                "done"
        ));

        DebugRun refreshed = queryService.findRun(run.runId()).orElseThrow();
        assertEquals(DebugRun.RunStatus.COMPLETED, refreshed.status());

        var timeline = queryService.timeline("run-1", 100, null);
        assertFalse(timeline.items().isEmpty());

        var summary = persistenceService.validate("run-1");
        assertEquals("run-1", summary.runId());
        assertFalse(summary.checks().isEmpty());
    }
}
