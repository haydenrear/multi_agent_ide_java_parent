package com.hayden.multiagentide.tui;

import com.hayden.acp_cdc_ai.acp.events.Events;

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
        TuiSessionState sessionState = sessions.getOrDefault(sessionId, TuiSessionState.initial());
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
        TuiSessionState updatedSession = new TuiSessionState(
                List.copyOf(updated),
                clampIndex(newSelected, updated.size()),
                Math.max(0, newScroll),
                sessionState.autoFollow(),
                sessionState.detailOpen(),
                sessionState.detailEventId(),
                sessionState.chatInput(),
                sessionState.chatSearch()
        );
        sessions.put(sessionId, updatedSession);
        List<String> order = new ArrayList<>(state.sessionOrder());
        if (sessionId != null && !order.contains(sessionId)) {
            order.add(sessionId);
        }
        return new TuiState(
                state.sessionId(),
                state.activeSessionId(),
                List.copyOf(order),
                sessions,
                state.focus(),
                state.chatScrollOffset()
        );
    }

    private TuiState applySystemEvent(TuiState state, Events.TuiSystemEvent event) {
        return state;
    }

    private TuiState applyInteraction(TuiState state, Events.TuiInteractionEvent event, TuiViewport viewport) {
        if (state.activeSessionId() == null || state.activeSessionId().isBlank()) {
            return state;
        }
        Map<String, TuiSessionState> sessions = new LinkedHashMap<>(state.sessions());
        TuiSessionState sessionState = sessions.getOrDefault(state.activeSessionId(), TuiSessionState.initial());
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
            case Events.EventStreamOpenDetail e -> new TuiState(
                    state.sessionId(),
                    state.activeSessionId(),
                    state.sessionOrder(),
                    updateSession(sessions, state.activeSessionId(), new TuiSessionState(
                            sessionState.events(),
                            sessionState.selectedIndex(),
                            sessionState.scrollOffset(),
                            sessionState.autoFollow(),
                            true,
                            e.eventId(),
                            sessionState.chatInput(),
                            sessionState.chatSearch()
                    )),
                    state.focus(),
                    state.chatScrollOffset()
            );
            case Events.EventStreamCloseDetail e -> new TuiState(
                    state.sessionId(),
                    state.activeSessionId(),
                    state.sessionOrder(),
                    updateSession(sessions, state.activeSessionId(), new TuiSessionState(
                            sessionState.events(),
                            sessionState.selectedIndex(),
                            sessionState.scrollOffset(),
                            sessionState.autoFollow(),
                            false,
                            null,
                            sessionState.chatInput(),
                            sessionState.chatSearch()
                    )),
                    state.focus(),
                    state.chatScrollOffset()
            );
            case Events.FocusChatInput e -> new TuiState(
                    state.sessionId(),
                    state.activeSessionId(),
                    state.sessionOrder(),
                    sessions,
                    TuiFocus.CHAT_INPUT,
                    state.chatScrollOffset()
            );
            case Events.FocusEventStream e -> new TuiState(
                    state.sessionId(),
                    state.activeSessionId(),
                    state.sessionOrder(),
                    sessions,
                    TuiFocus.EVENT_STREAM,
                    state.chatScrollOffset()
            );
            case Events.FocusSessionList e -> new TuiState(
                    state.sessionId(),
                    state.activeSessionId(),
                    state.sessionOrder(),
                    sessions,
                    TuiFocus.SESSION_LIST,
                    state.chatScrollOffset()
            );
            case Events.ChatInputChanged e -> new TuiState(
                    state.sessionId(),
                    state.activeSessionId(),
                    state.sessionOrder(),
                    updateSession(sessions, state.activeSessionId(), new TuiSessionState(
                            sessionState.events(),
                            sessionState.selectedIndex(),
                            sessionState.scrollOffset(),
                            sessionState.autoFollow(),
                            sessionState.detailOpen(),
                            sessionState.detailEventId(),
                            e.text(),
                            sessionState.chatSearch()
                    )),
                    state.focus(),
                    state.chatScrollOffset()
            );
            case Events.ChatInputSubmitted e -> new TuiState(
                    state.sessionId(),
                    state.activeSessionId(),
                    state.sessionOrder(),
                    updateSession(sessions, state.activeSessionId(), new TuiSessionState(
                            sessionState.events(),
                            sessionState.selectedIndex(),
                            sessionState.scrollOffset(),
                            sessionState.autoFollow(),
                            sessionState.detailOpen(),
                            sessionState.detailEventId(),
                            "",
                            sessionState.chatSearch()
                    )),
                    state.focus(),
                    state.chatScrollOffset()
            );
            case Events.ChatSearchOpened e -> new TuiState(
                    state.sessionId(),
                    state.activeSessionId(),
                    state.sessionOrder(),
                    updateSession(sessions, state.activeSessionId(), new TuiSessionState(
                            sessionState.events(),
                            sessionState.selectedIndex(),
                            sessionState.scrollOffset(),
                            sessionState.autoFollow(),
                            sessionState.detailOpen(),
                            sessionState.detailEventId(),
                            sessionState.chatInput(),
                            new TuiChatSearch(true, e.initialQuery(), List.of(), -1)
                    )),
                    TuiFocus.CHAT_SEARCH,
                    state.chatScrollOffset()
            );
            case Events.ChatSearchQueryChanged e -> applySearchQueryChange(state, e.query(), viewport);
            case Events.ChatSearchResultNavigate e -> applySearchNavigation(state, e.delta(), viewport);
            case Events.ChatSearchClosed e -> new TuiState(
                    state.sessionId(),
                    state.activeSessionId(),
                    state.sessionOrder(),
                    updateSession(sessions, state.activeSessionId(), new TuiSessionState(
                            sessionState.events(),
                            sessionState.selectedIndex(),
                            sessionState.scrollOffset(),
                            sessionState.autoFollow(),
                            sessionState.detailOpen(),
                            sessionState.detailEventId(),
                            sessionState.chatInput(),
                            TuiChatSearch.inactive()
                    )),
                    TuiFocus.EVENT_STREAM,
                    state.chatScrollOffset()
            );
            case Events.SessionSelected e -> new TuiState(
                    state.sessionId(),
                    e.sessionId(),
                    ensureSessionOrder(state.sessionOrder(), e.sessionId()),
                    ensureSessionExists(sessions, e.sessionId()),
                    state.focus(),
                    state.chatScrollOffset()
            );
            case Events.SessionCreated e -> {
                Map<String, TuiSessionState> updatedSessions = new LinkedHashMap<>(sessions);
                if (!updatedSessions.containsKey(e.sessionId())) {
                    updatedSessions.put(e.sessionId(), TuiSessionState.initial());
                }
                List<String> order = new ArrayList<>(state.sessionOrder());
                if (!order.contains(e.sessionId())) {
                    order.add(e.sessionId());
                }
                yield new TuiState(
                        state.sessionId(),
                        e.sessionId(),
                        List.copyOf(order),
                        updatedSessions,
                        state.focus(),
                        state.chatScrollOffset()
                );
            }
        };
    }

    private TuiState applySearchQueryChange(TuiState state, String query, TuiViewport viewport) {
        Map<String, TuiSessionState> sessions = new LinkedHashMap<>(state.sessions());
        TuiSessionState sessionState = sessions.getOrDefault(state.activeSessionId(), TuiSessionState.initial());
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
        return new TuiState(
                state.sessionId(),
                state.activeSessionId(),
                state.sessionOrder(),
                updateSession(sessions, state.activeSessionId(), new TuiSessionState(
                        sessionState.events(),
                        selectedEventIndex,
                        scroll,
                        false,
                        sessionState.detailOpen(),
                        sessionState.detailEventId(),
                        sessionState.chatInput(),
                        new TuiChatSearch(true, trimmed, List.copyOf(results), selectedResultIndex)
                )),
                TuiFocus.CHAT_SEARCH,
                state.chatScrollOffset()
        );
    }

    private TuiState applySearchNavigation(TuiState state, int delta, TuiViewport viewport) {
        Map<String, TuiSessionState> sessions = new LinkedHashMap<>(state.sessions());
        TuiSessionState sessionState = sessions.getOrDefault(state.activeSessionId(), TuiSessionState.initial());
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
        return new TuiState(
                state.sessionId(),
                state.activeSessionId(),
                state.sessionOrder(),
                updateSession(sessions, state.activeSessionId(), new TuiSessionState(
                        sessionState.events(),
                        selectedEventIndex,
                        scroll,
                        false,
                        sessionState.detailOpen(),
                        sessionState.detailEventId(),
                        sessionState.chatInput(),
                        new TuiChatSearch(true, search.query(), search.resultIndices(), next)
                )),
                TuiFocus.CHAT_SEARCH,
                state.chatScrollOffset()
        );
    }

    private TuiState stateWithSelection(
            TuiState state,
            Map<String, TuiSessionState> sessions,
            TuiSessionState sessionState,
            int selected,
            int scroll,
            boolean autoFollow
    ) {
        return new TuiState(
                state.sessionId(),
                state.activeSessionId(),
                state.sessionOrder(),
                updateSession(sessions, state.activeSessionId(), new TuiSessionState(
                        sessionState.events(),
                        selected,
                        scroll,
                        autoFollow,
                        sessionState.detailOpen(),
                        sessionState.detailEventId(),
                        sessionState.chatInput(),
                        sessionState.chatSearch()
                )),
                state.focus(),
                state.chatScrollOffset()
        );
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

    private Map<String, TuiSessionState> ensureSessionExists(Map<String, TuiSessionState> sessions, String sessionId) {
        Map<String, TuiSessionState> updated = new LinkedHashMap<>(sessions);
        if (sessionId != null && !updated.containsKey(sessionId)) {
            updated.put(sessionId, TuiSessionState.initial());
        }
        return updated;
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
