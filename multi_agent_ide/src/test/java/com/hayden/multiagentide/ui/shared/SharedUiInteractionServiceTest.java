package com.hayden.multiagentide.ui.shared;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.ui.state.UiSessionState;
import com.hayden.multiagentide.ui.state.UiState;
import com.hayden.multiagentide.ui.state.UiViewport;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SharedUiInteractionServiceTest {

    private final SharedUiInteractionService service = new SharedUiInteractionServiceImpl(new UiActionMapper());

    @Test
    void mapsUiCommandToTuiInteractionEvent() {
        UiActionCommand command = new UiActionCommand(
                "action-1",
                "session-1",
                UiActionCommand.ActionType.CHAT_INPUT_CHANGED,
                Map.of("text", "hello"),
                Instant.now()
        );

        Events.UiInteractionEvent event = service.toInteractionEvent(command);
        assertInstanceOf(Events.ChatInputChanged.class, event);
        Events.ChatInputChanged changed = (Events.ChatInputChanged) event;
        assertEquals("hello", changed.text());
    }

    @Test
    void reducesInteractionEventsIntoEquivalentSnapshot() {
        List<Events.GraphEvent> events = List.of(
                new Events.TuiInteractionGraphEvent(
                        "e1", Instant.now(), "session-1", "session-1", new Events.ChatInputChanged("plan run", 8)
                ),
                new Events.TuiInteractionGraphEvent(
                        "e2", Instant.now(), "session-1", "session-1", new Events.FocusEventStream("CHAT_INPUT")
                )
        );

        Path repo = Path.of("/tmp/repo");
        UiState state = UiState.initial(
                "shell-1",
                "session-1",
                List.of("session-1"),
                Map.of("session-1", UiSessionState.initial(repo)),
                repo
        );
        UiViewport viewport = new UiViewport(20);
        for (Events.GraphEvent event : events) {
            state = service.reduce(state, event, viewport, service.resolveNodeId(event, "session-1"));
        }
        UiStateSnapshot snapshot = service.toSnapshot(state);

        assertEquals("session-1", snapshot.activeNodeId());
        assertEquals("EVENT_STREAM", snapshot.focus());
        assertTrue(snapshot.nodes().containsKey("session-1"));
        assertEquals("plan run", snapshot.nodes().get("session-1").chatInput());
    }
}
