package com.hayden.multiagentide.tui;

import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.component.view.control.InputView;
import org.springframework.shell.component.view.event.KeyHandler;
import org.springframework.shell.component.view.screen.Screen;
import org.springframework.shell.geom.Position;
import org.springframework.shell.geom.Rectangle;

import java.util.List;

@Slf4j
class TuiChatView extends InputView {

    private TuiState state = null;
    private TuiSessionState sessionState = TuiSessionState.initial();

    TuiChatView() {
        setShowBorder(true);
    }

    void update(TuiState state, TuiSessionState sessionState) {
        this.state = state;
        this.sessionState = sessionState == null ? TuiSessionState.initial() : sessionState;
    }

    int requiredHeight(int width) {
        ChatLine chatLine = resolveChatLine();
        int contentWidth = Math.max(1, width - 2);
        List<String> lines = TuiTextLayout.wrapFixed(chatLine.prompt() + chatLine.value(), contentWidth);
        int desired = lines.size() + 2;
        return Math.max(3, Math.min(10, desired));
    }

    @Override
    public KeyHandler getKeyHandler() {
        // Let the root terminal view own key dispatch so state and GraphEvents stay in sync.
        return KeyHandler.neverConsume();
    }

    @Override
    public KeyHandler getHotKeyHandler() {
        return KeyHandler.neverConsume();
    }

    @Override
    protected void drawInternal(Screen screen) {
        ChatLine chatLine = resolveChatLine();
        if (state != null && state.focus() == TuiFocus.CHAT_SEARCH) {
            setTitle("Search");
        } else {
            setTitle("Chat");
        }

        super.drawInternal(screen);

        Rectangle inner = getInnerRect();
        int width = Math.max(1, inner.width());
        int height = Math.max(1, inner.height());

        String full = chatLine.prompt() + chatLine.value();
        List<String> wrapped = TuiTextLayout.wrapFixed(full, width);
        if (wrapped.isEmpty()) {
            wrapped = List.of("");
        }

        int start = Math.max(0, wrapped.size() - height);
        Screen.Writer writer = screen.writerBuilder().build();
        for (int row = 0; row < height; row++) {
            int lineIndex = start + row;
            String value = lineIndex < wrapped.size() ? wrapped.get(lineIndex) : "";
            writer.text(TuiTextLayout.pad(value, width), inner.x(), inner.y() + row);
        }

        if (isChatFocused()) {
            int cursorOffset = full.length();
            int cursorLine = cursorOffset / width;
            int cursorCol = cursorOffset % width;
            int visibleLine = Math.max(0, cursorLine - start);
            int cursorY = inner.y() + Math.max(0, Math.min(height - 1, visibleLine));
            int cursorX = inner.x() + Math.max(0, Math.min(width - 1, cursorCol));
            screen.setShowCursor(true);
            screen.setCursorPosition(new Position(cursorX, cursorY));
        }

    }

    private ChatLine resolveChatLine() {
        if (state != null && state.focus() == TuiFocus.CHAT_SEARCH && sessionState.chatSearch().active()) {
            return new ChatLine("Search> ", sessionState.chatSearch().query());
        }
        return new ChatLine("Chat> ", sessionState.chatInput());
    }

    private boolean isChatFocused() {
        return state != null && (state.focus() == TuiFocus.CHAT_INPUT || state.focus() == TuiFocus.CHAT_SEARCH);
    }

    private record ChatLine(String prompt, String value) {
    }
}
