package com.hayden.multiagentidelib.agent;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultDegenerateLoopPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "context-manager-route",
            "routeToContextManager",
            "context-manager",
            "contextManagerRequest",
            "commit-agent",
            "runCommitAgent",
            "merge-conflict-agent",
            "runMergeConflictAgent",
            "path-filter",
            "runAiFilter"
    })
    void skipsHelperAndContextActionsWhenDetectingRepeatedNodes(String actionName) {
        DefaultDegenerateLoopPolicy policy = new DefaultDegenerateLoopPolicy();
        BlackboardHistory history = historyWithRepeatedAction(actionName);

        assertThat(policy.detectLoop(
                history,
                actionName,
                new AgentModels.OrchestratorRequest(ArtifactKey.createRoot(), "goal", null)
        )).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "handleUnifiedInterrupt",
            "runInterruptAgentReview"
    })
    void stillDetectsLoopsForInterruptActions(String actionName) {
        DefaultDegenerateLoopPolicy policy = new DefaultDegenerateLoopPolicy();
        policy.setEventBus(new RecordingEventBus());

        BlackboardHistory history = historyWithRepeatedAction(actionName);

        assertThat(policy.detectLoop(
                history,
                actionName,
                new AgentModels.OrchestratorRequest(ArtifactKey.createRoot(), "goal", null)
        )).isPresent();
    }

    private static BlackboardHistory historyWithRepeatedAction(String actionName) {
        BlackboardHistory history = new BlackboardHistory(new BlackboardHistory.History(), "root-node", null);
        for (int i = 0; i < 6; i++) {
            history.addEntry(
                    "root-node::ACTION_STARTED",
                    new Events.ActionStartedEvent(
                            "evt-" + i,
                            Instant.now(),
                            "root-node",
                            "agent",
                            actionName
                    )
            );
        }
        return history;
    }

    private static final class RecordingEventBus implements EventBus {

        private final List<Events.GraphEvent> events = new ArrayList<>();

        @Override
        public void subscribe(EventListener listener) {}

        @Override
        public void unsubscribe(EventListener listener) {}

        @Override
        public void publish(Events.GraphEvent event) {
            events.add(event);
        }

        @Override
        public List<EventListener> getSubscribers() {
            return List.of();
        }

        @Override
        public void clear() {}

        @Override
        public boolean hasSubscribers() {
            return false;
        }
    }
}
