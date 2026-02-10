package com.hayden.multiagentide.tui;

import lombok.Builder;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Builder(toBuilder = true)
public record TuiState(
        String sessionId,
        String activeSessionId,
        List<String> sessionOrder,
        Map<String, TuiSessionState> sessions,
        TuiFocus focus,
        int chatScrollOffset,
        Path repo
) {
    public TuiState {
        if (repo == null)
            throw new IllegalArgumentException("");
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

    public static TuiState initial(String sessionId, String activeSessionId, List<String> sessionOrder, Map<String, TuiSessionState> sessions,
                                   Path repo) {
        return TuiState.builder()
                .sessionId(sessionId)
                .activeSessionId(activeSessionId)
                .sessionOrder(sessionOrder)
                .sessions(sessions)
                .focus(TuiFocus.CHAT_INPUT)
                .chatScrollOffset(0)
                .repo(repo)
                .build();
    }
}
