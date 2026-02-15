package com.hayden.multiagentide.integration;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.ui.state.UiSessionState;
import com.hayden.multiagentide.ui.state.UiState;
import com.hayden.multiagentide.ui.state.UiStateReducer;
import com.hayden.multiagentide.ui.state.UiViewport;
import com.hayden.multiagentide.ui.shared.SharedUiInteractionService;
import com.hayden.multiagentide.ui.shared.SharedUiInteractionServiceImpl;
import com.hayden.multiagentide.ui.shared.UiActionMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiParityIntegrationTest {

    private final UiStateReducer reducer = new UiStateReducer();
    private final SharedUiInteractionService shared = new SharedUiInteractionServiceImpl(new UiActionMapper());

    @Test
    void sharedReductionMatchesReducerSemantics() {
        Path repo = Path.of("/tmp/repo");
        UiState initial = UiState.initial(
                "shell-1",
                "session-1",
                List.of("session-1"),
                Map.of("session-1", UiSessionState.initial(repo)),
                repo
        );

        Events.GraphEvent interaction = new Events.TuiInteractionGraphEvent(
                "e1",
                Instant.now(),
                "session-1",
                "session-1",
                new Events.ChatInputChanged("hello", 5)
        );

        UiState tuiState = reducer.reduce(initial, interaction, new UiViewport(20), "session-1");
        UiState sharedState = shared.reduce(initial, interaction, new UiViewport(20), "session-1");

        assertEquals(
                tuiState.sessions().get("session-1").chatInput(),
                sharedState.sessions().get("session-1").chatInput()
        );
        assertEquals(tuiState.focus(), sharedState.focus());
    }
}
