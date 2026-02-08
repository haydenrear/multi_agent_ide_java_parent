package com.hayden.multiagentide.tui;

import java.util.List;
import java.util.Map;

public record TuiState(
        String sessionId,
        String activeSessionId,
        List<String> sessionOrder,
        Map<String, TuiSessionState> sessions,
        TuiFocus focus,
        int chatScrollOffset
) {
    public TuiState {
        if (focus == null) {
            focus = TuiFocus.CHAT_INPUT;
        }
        if (sessionOrder == null) {
            sessionOrder = List.of();
        }
        if (sessions == null) {
            sessions = Map.of();
        }
    }

    public static TuiState initial(String sessionId, String activeSessionId, List<String> sessionOrder, Map<String, TuiSessionState> sessions) {
        return new TuiState(
                sessionId,
                activeSessionId,
                sessionOrder,
                sessions,
                TuiFocus.CHAT_INPUT,
                0
        );
    }
}
