package com.hayden.multiagentide.tui;

import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.Builder;
import lombok.With;

import java.nio.file.Path;
import java.util.List;

@Builder(toBuilder = true)
@With
public record TuiSessionState(
        List<Events.GraphEvent> events,
        int selectedIndex,
        int scrollOffset,
        boolean autoFollow,
        boolean detailOpen,
        String detailEventId,
        String chatInput,
        TuiChatSearch chatSearch,
        Path repo
) {
    public TuiSessionState {
        if (repo == null)
            throw new IllegalArgumentException("");
        if (events == null) {
            events = List.of();
        }
        if (chatInput == null) {
            chatInput = "";
        }
        if (chatSearch == null) {
            chatSearch = TuiChatSearch.inactive();
        }
    }

    public static TuiSessionState initial(Path repo) {
        return new TuiSessionState(
                List.of(),
                0,
                0,
                true,
                false,
                null,
                "",
                TuiChatSearch.inactive(),
                repo
        );
    }
}
