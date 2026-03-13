package com.hayden.multiagentide.cli;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.agent.AgentModels;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CliEventFormatterPropagationEventTest {

    private final CliEventFormatter formatter = new CliEventFormatter(new ArtifactKeyFormatter());

    @Test
    void propagationEventsRenderFullPayloadWithoutTruncation() {
        AgentModels.AiPropagatorRequest payload = AgentModels.AiPropagatorRequest.builder()
                .goal("review propagated payload")
                .input("x".repeat(600))
                .sourceName("workflow")
                .sourceNodeId("ak:01ARZ3NDEKTSV4RRFFQ69G5FAV")
                .metadata(Map.of("kind", "request"))
                .build();
        Events.PropagationEvent event = new Events.PropagationEvent(
                "prop-event-1",
                Instant.parse("2026-03-12T10:15:30Z"),
                "ak:01ARZ3NDEKTSV4RRFFQ69G5FAV",
                "propagator-1",
                "workflow-agent/coordinateWorkflow",
                "ACTION_REQUEST",
                "PASSTHROUGH",
                "ak:01ARZ3NDEKTSV4RRFFQ69G5FAV",
                "workflow",
                payload.getClass().getName(),
                payload,
                "corr-1"
        );

        String rendered = formatter.format(new CliEventFormatter.CliEventArgs(80, event, true));

        assertThat(rendered).contains("PROPAGATION");
        assertThat(rendered).contains(payload.getClass().getName());
        assertThat(rendered).contains("x".repeat(400));
        assertThat(rendered).contains("payload:");
    }
}
