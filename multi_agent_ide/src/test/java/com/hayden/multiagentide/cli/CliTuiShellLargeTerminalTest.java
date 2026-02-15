package com.hayden.multiagentide.cli;

import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.multiagentide.config.CliModeConfig;
import com.hayden.multiagentide.controller.OrchestrationController;
import com.hayden.multiagentide.repository.EventStreamRepository;
import com.hayden.multiagentide.repository.InMemoryEventStreamRepository;
import com.hayden.multiagentide.tui.TuiSession;
import com.hayden.multiagentide.ui.state.UiState;
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
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ShellTest(properties = {
        "spring.profiles.active=cli,clitest",
        "spring.main.lazy-initialization=true",
        "spring.docker.compose.enabled=false"
}, terminalWidth = 120, terminalHeight = 40)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CliTuiShellLargeTerminalTest {

    private static final String CTRL_C = String.valueOf((char) 3);

    @Autowired
    private ShellTestClient shellTestClient;
    @Autowired
    private TuiSession tuiSession;

    private ShellTestClient.NonInteractiveShellSession currentSession;

    @TestConfiguration
    @Import(CliModeConfig.class)
    @ComponentScan(basePackageClasses = {TuiSession.class, CliTuiRunner.class})
    static class TestConfig {

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
            currentSession.write(CTRL_C).run();
        }
    }

    @Test
    void adaptsLayoutForLargeTerminal() {
        currentSession = shellTestClient.nonInterative("tui").run();
        awaitState(state -> state != null && state.activeSessionId() != null);

        List<String> lines = currentSession.screen().lines();
        assertThat(lines).isNotEmpty();

        await().until(() -> {
            var l = currentSession.screen().lines();
            return l.stream().anyMatch(line -> line != null && line.contains("Chat>"));
        });
        lines = currentSession.screen().lines();
        assertThat(lines).anyMatch(line -> line != null && line.contains("Chat>"));
        assertThat(lines).allMatch(line -> line == null || line.length() <= 120);
        assertThat(tuiSession.eventListHeightForTests()).isGreaterThan(20);
    }

    private void awaitState(Predicate<UiState> condition) {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            UiState state = tuiSession.snapshotForTests();
            if (state != null && condition.test(state)) {
                return;
            }
            sleep(25);
        }
        fail("Timed out waiting for UiState condition");
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
            repository.save(event);
            for (EventListener listener : subscribers) {
                if (listener.isInterestedIn(event)) {
                    listener.onEvent(event);
                }
            }
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
