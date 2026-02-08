package com.hayden.multiagentide.cli;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.multiagentide.config.CliModeConfig;
import com.hayden.multiagentide.controller.OrchestrationController;
import com.hayden.multiagentide.repository.EventStreamRepository;
import com.hayden.multiagentide.repository.InMemoryEventStreamRepository;
import com.hayden.multiagentide.tui.TuiFocus;
import com.hayden.multiagentide.tui.TuiSession;
import com.hayden.multiagentide.tui.TuiSessionState;
import com.hayden.multiagentide.tui.TuiState;
import com.hayden.utilitymodule.config.EnvConfigProps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.shell.test.ShellTestClient;
import org.springframework.shell.test.autoconfigure.ShellTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ShellTest(properties = {
        "spring.profiles.active=cli,clitest",
        "spring.main.lazy-initialization=true",
        "spring.docker.compose.enabled=false"
}, terminalWidth = 70, terminalHeight = 120)
class CliTuiShellFlowTest {

    private static final String CTRL_C = String.valueOf((char) 3);
    private static final String CTRL_E = String.valueOf((char) 5);
    private static final String ESC = String.valueOf((char) 27);
    private static final String ENTER = "\r";
    private static final String KEY_UP = ESC + "[A";
    private static final String KEY_RIGHT = ESC + "[C";

    private ShellTestClient.BaseShellSession<?> currentSession;

    @Autowired
    private ShellTestClient shellTestClient;
    @Autowired
    private EventBus eventBus;
    @Autowired
    private TestEventBus testEventBus;
    @Autowired
    private OrchestrationController orchestrationController;
    @Autowired
    private TuiSession tuiSession;
    @Autowired
    private CliEventFormatter eventFormatter;

    @TestConfiguration
    @Import(CliModeConfig.class)
    @ComponentScan(basePackageClasses = {TuiSession.class, CliTuiRunner.class})
    static class TestConfig {

        @Bean
        @Primary
        CliOutputWriter cliOutputWriter() {
            return mock(CliOutputWriter.class);
        }

        @Bean
        @Primary
        OrchestrationController orchestrationController() {
            return mock(OrchestrationController.class);
        }

        @Bean
        @Primary
        EnvConfigProps envConfigProps() {
            EnvConfigProps props = mock(EnvConfigProps.class);
            when(props.getProjectDir()).thenReturn(java.nio.file.Path.of("/tmp"));
            return props;
        }

        @Bean
        @Primary
        EventStreamRepository eventStreamRepository() {
            return new InMemoryEventStreamRepository();
        }

        @Bean
        @Primary
        IPermissionGate permissionGate() {
            IPermissionGate gate = mock(IPermissionGate.class);
            when(gate.pendingPermissionRequests()).thenReturn(List.of());
            when(gate.pendingInterruptRequests()).thenReturn(List.of());
            return gate;
        }

        @Bean
        @Primary
        TestEventBus eventBus(EventStreamRepository eventStreamRepository) {
            return new TestEventBus(eventStreamRepository);
        }
    }

    @AfterEach
    void tearDown() {
        if (currentSession != null && !currentSession.isComplete()) {
            currentSession.write(CTRL_C);
            sleep(50);
        }
    }

    @Test
    void focusStreamScrollExpandCollapseAndAutoFollow() {
        String nodeId = ArtifactKey.createRoot().value();
        when(orchestrationController.startGoalAsync(any())).thenReturn(new OrchestrationController.StartGoalResponse(nodeId));

        currentSession = startTui();

        String goal = "build the ticket automation flow";
        publishInteraction(new Events.ChatInputChanged(goal, goal.length()));
        awaitState(state -> activeSession(state).chatInput().contains(goal));
        publishInteraction(new Events.ChatInputSubmitted(goal));
        awaitState(state -> nodeId.equals(state.activeSessionId()));

        for (int i = 1; i <= 40; i++) {
            eventBus.publish(new Events.AddMessageEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    nodeId,
                    "EV-" + String.format("%03d", i) + " event stream payload"
            ));
        }

        awaitState(state -> activeSession(state).events().size() == 40);
        awaitState(state -> activeSession(state).selectedIndex() == 39);

        publishInteraction(new Events.FocusEventStream(TuiFocus.CHAT_INPUT.name()));
        awaitState(state -> state.focus() == TuiFocus.EVENT_STREAM);

        int selectedBefore = activeSession(tuiSession.snapshotForTests()).selectedIndex();
        for (int i = 0; i < 8; i++) {
            TuiSessionState current = activeSession(tuiSession.snapshotForTests());
            publishInteraction(new Events.EventStreamMoveSelection(-1, current.selectedIndex() - 1));
        }
        awaitState(state -> activeSession(state).selectedIndex() < selectedBefore);

        TuiSessionState selectedState = activeSession(tuiSession.snapshotForTests());
        String selectedEventId = selectedState.events().get(selectedState.selectedIndex()).eventId();
        publishInteraction(new Events.EventStreamOpenDetail(selectedEventId));
        awaitState(state -> activeSession(state).detailOpen());

        TuiSessionState detailState = activeSession(tuiSession.snapshotForTests());
        publishInteraction(new Events.EventStreamCloseDetail(detailState.detailEventId()));
        awaitState(state -> !activeSession(state).detailOpen());

        publishInteraction(new Events.EventStreamMoveSelection(1, 39));
        awaitState(state -> activeSession(state).selectedIndex() == 39);
        awaitState(state -> activeSession(state).autoFollow());

        for (int i = 41; i <= 80; i++) {
            eventBus.publish(new Events.AddMessageEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    nodeId,
                    "EV-" + String.format("%03d", i) + " follow tail"
            ));
        }

        awaitState(state -> activeSession(state).events().size() == 80);
        awaitState(state -> activeSession(state).selectedIndex() == 79);
    }

//    @Test
    void keyboardInputPublishesChatInteractionEventsAndStartsGoal() {
        String nodeId = ArtifactKey.createRoot().value();
        when(orchestrationController.startGoalAsync(any())).thenReturn(new OrchestrationController.StartGoalResponse(nodeId));

        currentSession = startTui();
        testEventBus.clearPublishedEvents();

        String goal = "keyboard-start-goal";
        currentSession.write(goal);

        awaitState(state -> goal.equals(activeSession(state).chatInput()));
        awaitContains(currentSession, goal);
        awaitInteraction(interaction -> interaction.tuiEvent() instanceof Events.ChatInputChanged changed
                && goal.equals(changed.text()));

        currentSession.write(ENTER);

        awaitInteraction(interaction -> interaction.tuiEvent() instanceof Events.ChatInputSubmitted submitted
                && goal.equals(submitted.text()));
        awaitState(state -> nodeId.equals(state.activeSessionId()));
    }

//    @Test
    void keyboardNavigationPublishesInteractionEventsAndUpdatesTui() {
        String nodeId = ArtifactKey.createRoot().value();
        when(orchestrationController.startGoalAsync(any())).thenReturn(new OrchestrationController.StartGoalResponse(nodeId));

        currentSession = startTui();
        String goal = "keyboard-navigation-goal";
        currentSession.write(goal);
        currentSession.write(ENTER);
        awaitState(state -> nodeId.equals(state.activeSessionId()));

        for (int i = 1; i <= 20; i++) {
            eventBus.publish(new Events.AddMessageEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    nodeId,
                    "KEY-EV-" + String.format("%03d", i)
            ));
        }
        awaitState(state -> activeSession(state).events().size() == 20);
        awaitContains(currentSession, "Events total=20");

        testEventBus.clearPublishedEvents();
        int selectedBefore = activeSession(tuiSession.snapshotForTests()).selectedIndex();

        currentSession.write(CTRL_E);
        awaitState(state -> state.focus() == TuiFocus.EVENT_STREAM);
        awaitContains(currentSession, "(focus)");
        awaitInteraction(interaction -> interaction.tuiEvent() instanceof Events.FocusEventStream);

        currentSession.write(KEY_UP);
        awaitInteraction(interaction -> interaction.tuiEvent() instanceof Events.EventStreamMoveSelection move
                && move.delta() < 0);
        awaitState(state -> activeSession(state).selectedIndex() < selectedBefore);

        currentSession.write(KEY_RIGHT);
        awaitInteraction(interaction -> interaction.tuiEvent() instanceof Events.EventStreamScroll scroll
                && scroll.delta() > 0);

        currentSession.write(ENTER);
        awaitInteraction(interaction -> interaction.tuiEvent() instanceof Events.EventStreamOpenDetail);
        awaitState(state -> activeSession(state).detailOpen());

        currentSession.write(ESC);
        awaitInteraction(interaction -> interaction.tuiEvent() instanceof Events.EventStreamCloseDetail);
        awaitState(state -> !activeSession(state).detailOpen());
    }

    @Test
    void wrapsEventAndChatAndRendersOnSmallTerminal() {
        String nodeId = ArtifactKey.createRoot().value();
        when(orchestrationController.startGoalAsync(any())).thenReturn(new OrchestrationController.StartGoalResponse(nodeId));

        currentSession = startTui();
        awaitState(state -> activeSession(state).events().isEmpty());

        int baseEventHeight = tuiSession.eventListHeightForTests();

        String longGoal = "goal-wrap-" + "abcdefghijklmnopqrstuvwxyz0123456789".repeat(5);
        publishInteraction(new Events.ChatInputChanged(longGoal, longGoal.length()));
        awaitState(state -> activeSession(state).chatInput().contains("goal-wrap-"));
        int reducedEventHeight = tuiSession.eventListHeightForTests();
        assertThat(reducedEventHeight).isLessThanOrEqualTo(baseEventHeight);
        awaitContains(currentSession, "goal-wrap-");

        publishInteraction(new Events.ChatInputSubmitted(longGoal));
        awaitState(state -> nodeId.equals(state.activeSessionId()));

        String longEvent = "WRAP-EVENT-HEAD " + "x".repeat(80) + " WRAP-EVENT-TAIL";
        eventBus.publish(new Events.AddMessageEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                longEvent
        ));

        awaitState(state -> activeSession(state).events().stream()
                .filter(e -> e instanceof Events.AddMessageEvent)
                .map(e -> (Events.AddMessageEvent) e)
                .anyMatch(e -> longEvent.equals(e.toAddMessage())));

        awaitContains(currentSession, "Chat>");
        assertThat(currentSession.screen().lines()).allMatch(line -> line == null || line.length() <= 70);
    }

    @Test
    void rendersBoxesForSingleTerminalSize() {
        currentSession = startTui();
        List<String> lines = currentSession.screen().lines();

        assertThat(lines).isNotEmpty();
        assertThat(lines).anyMatch(line -> line != null && line.contains("Chat>"));
//        assertThat(lines).anyMatch(line -> line != null && (line.contains("│") || line.contains("|")));
//        assertThat(lines).anyMatch(line -> line != null && (line.contains("┌") || line.contains("+")));
//        assertThat(lines).anyMatch(line -> line != null && (line.contains("└") || line.contains("+")));
        assertThat(tuiSession.eventListHeightForTests()).isGreaterThan(0);
    }

    @Test
    void expandedDetailUsesStructuredFormatterOutput() {
        currentSession = startTui();

        String sessionId = tuiSession.snapshotForTests().activeSessionId();
        Events.AddMessageEvent event = new Events.AddMessageEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                sessionId,
                "detail-check " + "x".repeat(40)
        );
        eventBus.publish(event);
        awaitState(state -> activeSession(state).events().size() == 1);

        publishInteraction(new Events.FocusEventStream(TuiFocus.CHAT_INPUT.name()));
        publishInteraction(new Events.EventStreamOpenDetail(event.eventId()));
        awaitState(state -> activeSession(state).detailOpen());
        awaitState(state -> event.eventId().equals(activeSession(state).detailEventId()));

        String formatted = eventFormatter.format(event);
        assertThat(formatted).contains("[MESSAGE]");
        assertThat(formatted).contains("ADD_MESSAGE");
        assertThat(formatted).contains("message=detail-check");
        assertThat(formatted).doesNotContain("{\"eventId\"");
    }

    private ShellTestClient.NonInteractiveShellSession startTui() {
        ShellTestClient.NonInteractiveShellSession session = shellTestClient.nonInterative("tui").run();
        awaitState(state -> state != null && state.activeSessionId() != null);
        awaitContains(session, "Chat>");
        return session;
    }

    private void publishInteraction(Events.TuiInteractionEvent event) {
        TuiState current = tuiSession.snapshotForTests();
        String sessionId = current != null && current.activeSessionId() != null
                ? current.activeSessionId()
                : "session-test";
        eventBus.publish(new Events.TuiInteractionGraphEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                sessionId,
                sessionId,
                event
        ));
    }

    private static void awaitContains(ShellTestClient.BaseShellSession<?> session, String text) {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            String screen = screenText(session);
            if (screen.contains(text)) {
                return;
            }
            sleep(50);
        }
        fail("Timed out waiting for screen to contain: " + text + "\nScreen:\n" + screenText(session));
    }

    private static String screenText(ShellTestClient.BaseShellSession<?> session) {
        return String.join("\n", session.screen().lines());
    }

    private void awaitState(Predicate<TuiState> condition) {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            TuiState state = tuiSession.snapshotForTests();
            if (state != null && condition.test(state)) {
                return;
            }
            sleep(25);
        }
        fail("Timed out waiting for TuiState condition");
    }

    private void awaitInteraction(Predicate<Events.TuiInteractionGraphEvent> condition) {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            for (Events.GraphEvent event : testEventBus.snapshotPublishedEvents()) {
                if (event instanceof Events.TuiInteractionGraphEvent interaction && condition.test(interaction)) {
                    return;
                }
            }
            sleep(25);
        }
        fail("Timed out waiting for TuiInteractionGraphEvent");
    }

    private static TuiSessionState activeSession(TuiState state) {
        if (state == null || state.activeSessionId() == null) {
            return TuiSessionState.initial();
        }
        return state.sessions().getOrDefault(state.activeSessionId(), TuiSessionState.initial());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static class TestEventBus implements EventBus {

        private final EventStreamRepository repository;
        private final List<EventListener> subscribers = new CopyOnWriteArrayList<>();
        private final List<Events.GraphEvent> publishedEvents = new CopyOnWriteArrayList<>();

        TestEventBus(EventStreamRepository repository) {
            this.repository = repository;
        }

        @Autowired
        @Lazy
        public void setSubscribers(List<EventListener> subscribers) {
            this.subscribers.clear();
            this.subscribers.addAll(subscribers);
        }

        @Override
        public void subscribe(EventListener listener) {
            if (subscribers.stream().noneMatch(existing -> existing.listenerId().equals(listener.listenerId()))) {
                subscribers.add(listener);
            }
        }

        @Override
        public void unsubscribe(EventListener listener) {
            subscribers.remove(listener);
        }

        @Override
        public void publish(Events.GraphEvent event) {
            if (event == null) {
                return;
            }
            publishedEvents.add(event);
            repository.save(event);
            for (EventListener listener : subscribers) {
                if (listener.isInterestedIn(event)) {
                    listener.onEvent(event);
                }
            }
        }

        List<Events.GraphEvent> snapshotPublishedEvents() {
            return new ArrayList<>(publishedEvents);
        }

        void clearPublishedEvents() {
            publishedEvents.clear();
        }

        @Override
        public List<EventListener> getSubscribers() {
            return new ArrayList<>(subscribers);
        }

        @Override
        public void clear() {
            subscribers.clear();
        }

        @Override
        public boolean hasSubscribers() {
            return !subscribers.isEmpty();
        }
    }
}
