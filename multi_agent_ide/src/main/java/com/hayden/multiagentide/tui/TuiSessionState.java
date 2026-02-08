package com.hayden.multiagentide.tui;

import com.hayden.acp_cdc_ai.acp.events.Events;

import java.util.List;

public record TuiSessionState(
        List<Events.GraphEvent> events,
        int selectedIndex,
        int scrollOffset,
        boolean autoFollow,
        boolean detailOpen,
        String detailEventId,
        String chatInput,
        TuiChatSearch chatSearch
) {
    public TuiSessionState {
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

    public static TuiSessionState initial() {
        return new TuiSessionState(
                List.of(),
                0,
                0,
                true,
                false,
                null,
                "",
                TuiChatSearch.inactive()
        );
    }
}
