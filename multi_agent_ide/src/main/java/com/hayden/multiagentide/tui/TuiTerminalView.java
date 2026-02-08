package com.hayden.multiagentide.tui;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.cli.CliEventFormatter;
import com.hayden.multiagentide.repository.EventStreamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.component.view.control.*;
import org.springframework.shell.component.view.event.KeyEvent;
import org.springframework.shell.component.view.event.KeyHandler;
import org.springframework.shell.component.view.screen.Screen;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

@Slf4j
class TuiTerminalView extends GridView {

    interface Controller {
        void moveSelection(int delta);

        void scrollList(int delta);

        void handleEnter();

        void handleBackspace();

        void handleEscape();

        void toggleFocus();

        void focusSessionList();

        void createNewSession();

        void focusEventStream();

        void openSearch();

        void handlePrintable(char ch);

        void selectSession(String sessionId);
    }

    private static final int ESCAPE_KEY = 27;
    private static final int CTRL_F = 6;
    private static final int CTRL_N = 14;
    private static final int CTRL_S = 19;
    private static final int CTRL_E = 5;

    private final Supplier<TuiState> stateSupplier;
    private final CliEventFormatter formatter;
    private final EventStreamRepository eventStreamRepository;
    private final Controller controller;
    private final IntConsumer eventListHeightConsumer;
    private final Consumer<View> modalViewConsumer;
    private final Consumer<View> dynamicViewConfigurer;

    private final TuiSessionMenuView sessionMenu;
    private final Map<String, TuiSessionView> sessionViews = new LinkedHashMap<>();
    private final Map<View, Boolean> configuredViews = new IdentityHashMap<>();

    private TuiSessionView activeSessionView;
    private String activeSessionId;
    private String modalEventId;
    private boolean modalOpen;
    private TuiDetailTextView activeDetailView;

    TuiTerminalView(
            Supplier<TuiState> stateSupplier,
            CliEventFormatter formatter,
            EventStreamRepository eventStreamRepository,
            Controller controller,
            IntConsumer eventListHeightConsumer,
            Consumer<View> modalViewConsumer,
            Consumer<View> dynamicViewConfigurer) {
        this.stateSupplier = stateSupplier;
        this.formatter = formatter;
        this.eventStreamRepository = eventStreamRepository;
        this.controller = controller;
        this.eventListHeightConsumer = eventListHeightConsumer;
        this.modalViewConsumer = modalViewConsumer;
        this.dynamicViewConfigurer = dynamicViewConfigurer;

        this.sessionMenu = new TuiSessionMenuView();
        this.sessionMenu.setShowBorder(true);

        setShowBorders(false);
        setColumnSize(0);
        setRowSize(3, 0);
    }

    @Override
    protected void initInternal() {
        registerKeyBinding(KeyEvent.Key.CursorUp, () -> controller.moveSelection(-1));
        registerKeyBinding(KeyEvent.Key.CursorDown, () -> controller.moveSelection(1));
        registerKeyBinding(KeyEvent.Key.CursorLeft, () -> controller.scrollList(-5));
        registerKeyBinding(KeyEvent.Key.CursorRight, () -> controller.scrollList(5));
        registerKeyBinding(KeyEvent.Key.Tab, controller::toggleFocus);
        registerKeyBinding(KeyEvent.Key.Backtab, controller::toggleFocus);
        registerKeyBinding(KeyEvent.Key.Enter, controller::handleEnter);
        registerKeyBinding(KeyEvent.Key.Backspace, controller::handleBackspace);
        registerKeyBinding(ESCAPE_KEY, controller::handleEscape);
        registerKeyBinding(CTRL_F, controller::openSearch);
        registerKeyBinding(CTRL_E, controller::focusEventStream);
        registerKeyBinding(CTRL_S, controller::focusSessionList);
        registerKeyBinding(CTRL_N, controller::createNewSession);
        registerKeyBinding(KeyEvent.Key.Char, this::onChar);
    }

    @Override
    public KeyHandler getKeyHandler() {
        KeyHandler fallback = args -> {
            KeyEvent event = args.event();
            boolean consumed = handleRawKey(event);
            return KeyHandler.resultOf(event, consumed, this);
        };
        return fallback.thenIfNotConsumed(super.getKeyHandler());
    }

    @Override
    public KeyHandler getHotKeyHandler() {
        KeyHandler fallback = args -> {
            KeyEvent event = args.event();
            boolean consumed = handleRawKey(event);
            return KeyHandler.resultOf(event, consumed, this);
        };
        return fallback.thenIfNotConsumed(super.getKeyHandler());
    }

    @Override
    protected void drawInternal(Screen screen) {
        TuiState state = stateSupplier.get();
        if (state == null) {
            super.drawInternal(screen);
            return;
        }

        String sessionId = resolveActiveSessionId(state);
        TuiSessionState sessionState = resolveSessionState(state, sessionId);

        sessionMenu.setTitle(state.focus() == TuiFocus.SESSION_LIST ? "Sessions (focus)" : "Sessions");
        sessionMenu.setItems(buildSessionMenuItems(state, sessionId));
        ensureConfigured(sessionMenu);

        TuiSessionView sessionView = sessionViews.computeIfAbsent(
                sessionId == null ? "session-none" : sessionId,
                this::newSessionView
        );
        sessionView.update(state, sessionState);

        ensureLayoutItems(sessionView);

        super.drawInternal(screen);

        eventListHeightConsumer.accept(Math.max(1, sessionView.visibleEventRows()));
        syncDetailModal(sessionState);
    }

    private TuiSessionView newSessionView(String sessionId) {
        TuiSessionView sessionView = new TuiSessionView(sessionId, new TuiMessageStreamView(formatter));
        for (View view : sessionView.allViews()) {
            ensureConfigured(view);
        }
        return sessionView;
    }

    private void ensureConfigured(View view) {
        if (view == null || configuredViews.containsKey(view)) {
            return;
        }
        if (dynamicViewConfigurer != null) {
            dynamicViewConfigurer.accept(view);
        }
        configuredViews.put(view, Boolean.TRUE);
    }

    private List<MenuView.MenuItem> buildSessionMenuItems(TuiState state, String selectedSessionId) {
        List<MenuView.MenuItem> items = new ArrayList<>();
        for (String sessionId : state.sessionOrder()) {
            boolean selected = sessionId.equals(selectedSessionId);
            MenuView.MenuItem item = MenuView.MenuItem.of(
                    abbreviate(sessionId),
                    MenuView.MenuItemCheckStyle.RADIO,
                    () -> controller.selectSession(sessionId),
                    selected
            );
            items.add(item);
        }
        items.add(MenuView.MenuItem.of("+", MenuView.MenuItemCheckStyle.NOCHECK, controller::createNewSession));
        return items;
    }

    private void ensureLayoutItems(TuiSessionView sessionView) {
        if (activeSessionView == sessionView) {
            return;
        }
        clearItems();
        addItem(sessionMenu, 0, 0, 1, 1, 0, 0);
        addItem(sessionView, 1, 0, 1, 1, 0, 0);
        activeSessionView = sessionView;
    }

    private void syncDetailModal(TuiSessionState sessionState) {
        if (!sessionState.detailOpen() || sessionState.detailEventId() == null || sessionState.detailEventId().isBlank()) {
            closeModal();
            return;
        }

        String detailEventId = sessionState.detailEventId();
        if (modalOpen && detailEventId.equals(modalEventId)) {
            return;
        }


        String detailText = resolveDetailText(sessionState, detailEventId);
        ButtonView closeButton = new ButtonView("Close", controller::handleEscape);
        TuiDetailTextView detailView = new TuiDetailTextView(detailText);
        ensureConfigured(detailView);
        DialogView dialogView = new DialogView(detailView, closeButton);
        dialogView.setLayer(100);
        detailView.setLayer(101);
        ensureConfigured(closeButton);
        ensureConfigured(dialogView);

        modalViewConsumer.accept(dialogView);
        modalOpen = true;
        modalEventId = detailEventId;
        activeDetailView = detailView;

    }

    private void closeModal() {
        if (!modalOpen) {
            return;
        }
        modalViewConsumer.accept(null);
        modalOpen = false;
        modalEventId = null;
        activeDetailView = null;
    }

    boolean isModalOpen() {
        return modalOpen;
    }

    void scrollDetail(int delta) {
        if (activeDetailView != null) {
            activeDetailView.scroll(delta);
        }
    }

    private String resolveDetailText(TuiSessionState sessionState, String eventId) {
        Optional<Events.GraphEvent> fromRepository = eventStreamRepository.findById(eventId);
        if (fromRepository.isPresent()) {
            return formatDetail(fromRepository.get());
        }
        for (Events.GraphEvent event : sessionState.events()) {
            if (eventId.equals(event.eventId())) {
                return formatDetail(event);
            }
        }
        return "(event not found)";
    }

    private String formatDetail(Events.GraphEvent event) {
        return event.eventType()
                + "\nid=" + event.eventId()
                + "\nnode=" + event.nodeId()
                + "\n\n"
                + formatter.format(event);
    }

    private void onChar(KeyEvent event) {
        if (event == null) {
            return;
        }
        String data = event.data();
        if (data != null && !data.isEmpty()) {
            for (int i = 0; i < data.length(); i++) {
                char ch = data.charAt(i);
                if (ch == '\r' || ch == '\n') {
                    controller.handleEnter();
                    continue;
                }
                if (ch == 127 || ch == 8) {
                    controller.handleBackspace();
                    continue;
                }
                if (ch == 27) {
                    controller.handleEscape();
                    continue;
                }
                if (!event.hasCtrl() && ch >= 32) {
                    controller.handlePrintable(ch);
                }
            }
            return;
        }
        if (event.hasCtrl()) {
            return;
        }
        int plainKey = event.getPlainKey();
        if (isPrintablePlainKey(plainKey)) {
            controller.handlePrintable((char) plainKey);
        }
    }

    private boolean handleRawKey(KeyEvent event) {
        if (event == null) {
            return false;
        }
        if (event.isKey(KeyEvent.Key.Enter)) {
            controller.handleEnter();
            return true;
        }
        if (event.isKey(KeyEvent.Key.Backspace)) {
            controller.handleBackspace();
            return true;
        }
        if (event.isKey(KeyEvent.Key.CursorUp)) {
            controller.moveSelection(-1);
            return true;
        }
        if (event.isKey(KeyEvent.Key.CursorDown)) {
            controller.moveSelection(1);
            return true;
        }
        if (event.isKey(KeyEvent.Key.CursorLeft)) {
            controller.scrollList(-5);
            return true;
        }
        if (event.isKey(KeyEvent.Key.CursorRight)) {
            controller.scrollList(5);
            return true;
        }
        if (event.isKey(KeyEvent.Key.Tab) || event.isKey(KeyEvent.Key.Backtab)) {
            controller.toggleFocus();
            return true;
        }
        if (event.isKey(KeyEvent.Key.Char)) {
            onChar(event);
            return true;
        }
        if (event.isKey(ESCAPE_KEY)) {
            controller.handleEscape();
            return true;
        }
        if (event.hasCtrl()) {
            char ctrl = Character.toLowerCase((char) event.getPlainKey());
            if (ctrl == 'f') {
                controller.openSearch();
                return true;
            }
            if (ctrl == 'e') {
                controller.focusEventStream();
                return true;
            }
            if (ctrl == 's') {
                controller.focusSessionList();
                return true;
            }
            if (ctrl == 'n') {
                controller.createNewSession();
                return true;
            }
            return false;
        }

        if (event.data() != null && !event.data().isEmpty()) {
            onChar(event);
            return true;
        }
        if (isPrintablePlainKey(event.getPlainKey())) {
            controller.handlePrintable((char) event.getPlainKey());
            return true;
        }
        return false;
    }

    private boolean isPrintablePlainKey(int plainKey) {
        if (plainKey < 32 || plainKey > Character.MAX_VALUE) {
            return false;
        }
        char ch = (char) plainKey;
        return !Character.isISOControl(ch);
    }

    private String resolveActiveSessionId(TuiState state) {
        if (state.activeSessionId() != null && !state.activeSessionId().isBlank()) {
            activeSessionId = state.activeSessionId();
            return state.activeSessionId();
        }
        if (!state.sessionOrder().isEmpty()) {
            activeSessionId = state.sessionOrder().get(0);
            return activeSessionId;
        }
        return activeSessionId;
    }

    private TuiSessionState resolveSessionState(TuiState state, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return TuiSessionState.initial();
        }
        return state.sessions().getOrDefault(sessionId, TuiSessionState.initial());
    }

    private String abbreviate(String text) {
        if (text == null || text.isBlank()) {
            return "none";
        }
        if (text.length() <= 12) {
            return text;
        }
        return text.substring(0, 12) + "...";
    }
}
