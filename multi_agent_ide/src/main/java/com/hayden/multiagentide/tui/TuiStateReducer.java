package com.hayden.multiagentide.tui;

import com.hayden.acp_cdc_ai.acp.events.Events;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class TuiStateReducer {

    public TuiState reduce(TuiState state, Events.GraphEvent event, TuiViewport viewport, String sessionId) {
        if (event instanceof Events.TuiInteractionGraphEvent interaction) {
            return applyInteraction(state, interaction.tuiEvent(), viewport);
        }
        if (event instanceof Events.TuiSystemGraphEvent system) {
            return applySystemEvent(state, system.tuiEvent());
        }
        return appendGraphEvent(state, event, viewport, sessionId);
    }

    private TuiState appendGraphEvent(TuiState state, Events.GraphEvent event, TuiViewport viewport, String sessionId) {
        Map<String, TuiSessionState> sessions = new LinkedHashMap<>(state.sessions());
        TuiSessionState sessionState = sessions.getOrDefault(sessionId, initialSessionFromState(state));
        List<Events.GraphEvent> updated = new ArrayList<>(sessionState.events());
        updated.add(event);

        int newSelected = sessionState.selectedIndex();
        int newScroll = sessionState.scrollOffset();
        boolean followTail = sessionState.autoFollow();
        if (followTail) {
            newSelected = updated.size() - 1;
            int height = viewport == null ? 0 : viewport.eventListHeight();
            if (height > 0) {
                newScroll = Math.max(0, updated.size() - height);
            }
        }

        TuiSessionState updatedSession = sessionState.toBuilder()
                .events(List.copyOf(updated))
                .selectedIndex(clampIndex(newSelected, updated.size()))
                .scrollOffset(Math.max(0, newScroll))
                .build();

        sessions.put(sessionId, updatedSession);
        List<String> order = new ArrayList<>(state.sessionOrder());
        if (sessionId != null && !order.contains(sessionId)) {
            order.add(sessionId);
        }
        return state.toBuilder()
                .sessionOrder(List.copyOf(order))
                .sessions(sessions)
                .build();
    }

    private TuiState applySystemEvent(TuiState state, Events.TuiSystemEvent event) {
        return state;
    }

    private TuiState applyInteraction(TuiState state, Events.TuiInteractionEvent event, TuiViewport viewport) {
        if (state.activeSessionId() == null || state.activeSessionId().isBlank()) {
            return state;
        }
        Map<String, TuiSessionState> sessions = new LinkedHashMap<>(state.sessions());
        TuiSessionState sessionState = sessions.getOrDefault(state.activeSessionId(), initialSessionFromState(state));
        return switch (event) {
            case Events.EventStreamMoveSelection e -> {
                int target = clampIndex(e.newSelectedIndex(), sessionState.events().size());
                int scroll = sessionState.scrollOffset();
                if (viewport != null) {
                    scroll = adjustScrollForSelection(target, scroll, viewport.eventListHeight());
                }
                boolean follow = target >= Math.max(0, sessionState.events().size() - 1);
                yield stateWithSelection(state, sessions, sessionState, target, scroll, follow);
            }
            case Events.EventStreamScroll e -> {
                int scroll = Math.max(0, e.newScrollOffset());
                yield stateWithSelection(state, sessions, sessionState, sessionState.selectedIndex(), scroll, false);
            }
            case Events.EventStreamOpenDetail e -> state.toBuilder()
                    .sessions(updateSession(sessions, state.activeSessionId(), sessionState.toBuilder()
                            .detailOpen(true)
                            .detailEventId(e.eventId())
                            .build()))
                    .build();
            case Events.EventStreamCloseDetail e -> state.toBuilder()
                    .sessions(updateSession(sessions, state.activeSessionId(), sessionState.toBuilder()
                            .detailOpen(false)
                            .detailEventId(null)
                            .build()))
                    .build();
            case Events.FocusChatInput e -> state.toBuilder()
                    .sessions(sessions)
                    .focus(TuiFocus.CHAT_INPUT)
                    .build();
            case Events.FocusEventStream e -> state.toBuilder()
                    .sessions(sessions)
                    .focus(TuiFocus.EVENT_STREAM)
                    .build();
            case Events.FocusSessionList e -> state.toBuilder()
                    .sessions(sessions)
                    .focus(TuiFocus.SESSION_LIST)
                    .build();
            case Events.ChatInputChanged e -> state.toBuilder()
                    .sessions(updateSession(sessions, state.activeSessionId(), sessionState.toBuilder()
                            .chatInput(e.text())
                            .build()))
                    .build();
            case Events.ChatInputSubmitted e -> state.toBuilder()
                    .sessions(updateSession(sessions, state.activeSessionId(), sessionState.toBuilder()
                            .chatInput("")
                            .build()))
                    .build();
            case Events.ChatSearchOpened e -> state.toBuilder()
                    .sessions(updateSession(sessions, state.activeSessionId(), sessionState.toBuilder()
                            .chatSearch(new TuiChatSearch(true, e.initialQuery(), List.of(), -1))
                            .build()))
                    .focus(TuiFocus.CHAT_SEARCH)
                    .build();
            case Events.ChatSearchQueryChanged e -> applySearchQueryChange(state, e.query(), viewport);
            case Events.ChatSearchResultNavigate e -> applySearchNavigation(state, e.delta(), viewport);
            case Events.ChatSearchClosed e -> state.toBuilder()
                    .sessions(updateSession(sessions, state.activeSessionId(), sessionState.toBuilder()
                            .chatSearch(TuiChatSearch.inactive())
                            .build()))
                    .focus(TuiFocus.EVENT_STREAM)
                    .build();
            case Events.SessionSelected e -> state.toBuilder()
                    .activeSessionId(e.sessionId())
                    .sessionOrder(ensureSessionOrder(state.sessionOrder(), e.sessionId()))
                    .sessions(ensureSessionExists(state, sessions, e.sessionId()))
                    .build();
            case Events.SessionCreated e -> {
                Map<String, TuiSessionState> updatedSessions = new LinkedHashMap<>(sessions);
                if (!updatedSessions.containsKey(e.sessionId())) {
                    updatedSessions.put(e.sessionId(), initialSessionFromState(state));
                }
                List<String> order = new ArrayList<>(state.sessionOrder());
                if (!order.contains(e.sessionId())) {
                    order.add(e.sessionId());
                }
                yield state.toBuilder()
                        .activeSessionId(e.sessionId())
                        .sessionOrder(List.copyOf(order))
                        .sessions(updatedSessions)
                        .build();
            }
        };
    }

    private TuiState applySearchQueryChange(TuiState state, String query, TuiViewport viewport) {
        Map<String, TuiSessionState> sessions = new LinkedHashMap<>(state.sessions());
        TuiSessionState sessionState = sessions.getOrDefault(state.activeSessionId(), initialSessionFromState(state));
        String trimmed = query == null ? "" : query.trim();
        List<Integer> results = new ArrayList<>();
        if (!trimmed.isBlank()) {
            for (int i = 0; i < sessionState.events().size(); i++) {
                Events.GraphEvent event = sessionState.events().get(i);
                if (event instanceof Events.AddMessageEvent addMessageEvent) {
                    if (containsIgnoreCase(addMessageEvent.toAddMessage(), trimmed)) {
                        results.add(i);
                    }
                } else if (event instanceof Events.UserMessageChunkEvent chunkEvent) {
                    if (containsIgnoreCase(chunkEvent.content(), trimmed)) {
                        results.add(i);
                    }
                }
            }
        }
        int selectedResultIndex = results.isEmpty() ? -1 : 0;
        int selectedEventIndex = results.isEmpty() ? sessionState.selectedIndex() : results.get(0);
        int scroll = sessionState.scrollOffset();
        if (viewport != null) {
            scroll = adjustScrollForSelection(selectedEventIndex, scroll, viewport.eventListHeight());
        }
        return state.toBuilder()
                .sessions(updateSession(sessions, state.activeSessionId(), sessionState.toBuilder()
                        .selectedIndex(selectedEventIndex)
                        .scrollOffset(scroll)
                        .autoFollow(false)
                        .chatSearch(new TuiChatSearch(true, trimmed, List.copyOf(results), selectedResultIndex))
                        .build()))
                .focus(TuiFocus.CHAT_SEARCH)
                .build();
    }

    private TuiState applySearchNavigation(TuiState state, int delta, TuiViewport viewport) {
        Map<String, TuiSessionState> sessions = new LinkedHashMap<>(state.sessions());
        TuiSessionState sessionState = sessions.getOrDefault(state.activeSessionId(), initialSessionFromState(state));
        TuiChatSearch search = sessionState.chatSearch();
        if (!search.active() || search.resultIndices().isEmpty()) {
            return state;
        }
        int next = search.selectedResultIndex() + delta;
        if (next < 0) {
            next = 0;
        } else if (next >= search.resultIndices().size()) {
            next = search.resultIndices().size() - 1;
        }
        int selectedEventIndex = search.resultIndices().get(next);
        int scroll = sessionState.scrollOffset();
        if (viewport != null) {
            scroll = adjustScrollForSelection(selectedEventIndex, scroll, viewport.eventListHeight());
        }
        return state.toBuilder()
                .sessions(updateSession(sessions, state.activeSessionId(), sessionState.toBuilder()
                        .selectedIndex(selectedEventIndex)
                        .scrollOffset(scroll)
                        .autoFollow(false)
                        .chatSearch(new TuiChatSearch(true, search.query(), search.resultIndices(), next))
                        .build()))
                .focus(TuiFocus.CHAT_SEARCH)
                .build();
    }

    private TuiState stateWithSelection(
            TuiState state,
            Map<String, TuiSessionState> sessions,
            TuiSessionState sessionState,
            int selected,
            int scroll,
            boolean autoFollow
    ) {
        return state.toBuilder()
                .sessions(updateSession(sessions, state.activeSessionId(), sessionState.toBuilder()
                        .selectedIndex(selected)
                        .scrollOffset(scroll)
                        .autoFollow(autoFollow)
                        .build()))
                .build();
    }

    private Map<String, TuiSessionState> updateSession(Map<String, TuiSessionState> sessions, String sessionId, TuiSessionState state) {
        Map<String, TuiSessionState> updated = new LinkedHashMap<>(sessions);
        if (sessionId != null) {
            updated.put(sessionId, state);
        }
        return updated;
    }

    private List<String> ensureSessionOrder(List<String> order, String sessionId) {
        List<String> updated = new ArrayList<>(order);
        if (sessionId != null && !updated.contains(sessionId)) {
            updated.add(sessionId);
        }
        return List.copyOf(updated);
    }

    private Map<String, TuiSessionState> ensureSessionExists(TuiState state, Map<String, TuiSessionState> sessions, String sessionId) {
        Map<String, TuiSessionState> updated = new LinkedHashMap<>(sessions);
        if (sessionId != null && !updated.containsKey(sessionId)) {
            updated.put(sessionId, initialSessionFromState(state));
        }
        return updated;
    }

    private TuiSessionState initialSessionFromState(TuiState state) {
        if (state == null || state.activeSessionId() == null) {
            return TuiSessionState.initial(state.repo());
        }
        TuiSessionState active = state.sessions().get(state.activeSessionId());
        if (active != null && active.repo() != null) {
            return TuiSessionState.initial(active.repo());
        }
        return TuiSessionState.initial(state.repo());
    }

    private int adjustScrollForSelection(int selectedIndex, int scrollOffset, int height) {
        if (height <= 0) {
            return scrollOffset;
        }
        int minVisible = scrollOffset;
        int maxVisible = scrollOffset + height - 1;
        if (selectedIndex < minVisible) {
            return selectedIndex;
        }
        if (selectedIndex > maxVisible) {
            return Math.max(0, selectedIndex - height + 1);
        }
        return scrollOffset;
    }

    private int clampIndex(int index, int size) {
        if (size <= 0) {
            return 0;
        }
        if (index < 0) {
            return 0;
        }
        if (index >= size) {
            return size - 1;
        }
        return index;
    }

    private boolean containsIgnoreCase(String text, String query) {
        if (text == null || query == null) {
            return false;
        }
        return text.toLowerCase().contains(query.toLowerCase());
    }
}
