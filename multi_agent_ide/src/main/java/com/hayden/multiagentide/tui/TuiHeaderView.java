package com.hayden.multiagentide.tui;

import org.springframework.shell.component.view.control.BoxView;
import org.springframework.shell.component.view.screen.Screen;
import org.springframework.shell.geom.Rectangle;

import java.util.ArrayList;
import java.util.List;

class TuiHeaderView extends BoxView {

    private String sessionId = "";
    private TuiState state = null;
    private TuiSessionState sessionState = TuiSessionState.initial();

    TuiHeaderView() {
        setShowBorder(true);
    }

    void update(TuiState state, String sessionId, TuiSessionState sessionState) {
        this.state = state;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.sessionState = sessionState == null ? TuiSessionState.initial() : sessionState;
    }

    @Override
    protected void drawInternal(Screen screen) {
        setTitle("Session " + abbreviate(sessionId));

        Rectangle inner = getInnerRect();
        int width = Math.max(1, inner.width());
        int y = inner.y();

        String focus = state == null || state.focus() == null ? TuiFocus.CHAT_INPUT.name() : state.focus().name();
        String topLine = "events=" + sessionState.events().size() + " selected=" + sessionState.selectedIndex() + " focus=" + focus;
        String keyLine = "Tab focus  Ctrl+S sessions  Ctrl+E events  Ctrl+F search  Ctrl+N new";

        List<String> lines = new ArrayList<>();
        lines.addAll(TuiTextLayout.wrapFixed(topLine, width));
        lines.addAll(TuiTextLayout.wrapFixed(keyLine, width));

        Screen.Writer writer = screen.writerBuilder().build();
        int row = 0;
        for (; row < Math.min(lines.size(), inner.height()); row++) {
            writer.text(TuiTextLayout.pad(lines.get(row), width), inner.x(), y + row);
        }
        for (; row < inner.height(); row++) {
            writer.text(TuiTextLayout.pad("", width), inner.x(), y + row);
        }

        super.drawInternal(screen);
    }

    private String abbreviate(String text) {
        if (text == null || text.isBlank()) {
            return "none";
        }
        if (text.length() <= 10) {
            return text;
        }
        return text.substring(0, 10) + "...";
    }
}
