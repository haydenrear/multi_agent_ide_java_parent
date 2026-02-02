package com.hayden.multiagentidelib.agent;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.support.InMemoryBlackboard;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ContextManagerTools")
@ExtendWith(MockitoExtension.class)
class ContextManagerToolsTest {

    @Test
    @DisplayName("listHistory returns message entry id and listMessageEvents pages events")
    void listHistoryAndMessagePaging() {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        BlackboardHistory history = buildHistory();
        blackboard.addObject(history);

        AgentProcess agentProcess = mock(AgentProcess.class);
        when(agentProcess.getBlackboard()).thenReturn(blackboard);

        AgentPlatform platform = mock(AgentPlatform.class);
        when(platform.getAgentProcess("session-1")).thenReturn(agentProcess);

        ContextManagerTools tools = new ContextManagerTools(platform);

        ContextManagerTools.HistoryListingResult listing = tools.listHistory(
                "session-1",
                0,
                10,
                null,
                null,
                null
        );

        assertThat(listing.status()).isEqualTo("success");
        assertThat(listing.entries()).hasSize(2);
        assertThat(listing.entries().get(1).inputType()).isEqualTo("MessageEventPage");
        assertThat(listing.entries().get(1).inputSummary()).contains("messages:1");

        ContextManagerTools.MessagePageResult page = tools.listMessageEvents(
                "session-1",
                "messages:1",
                0,
                1
        );

        assertThat(page.status()).isEqualTo("success");
        assertThat(page.events()).hasSize(1);
        assertThat(page.totalCount()).isEqualTo(2);
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    @DisplayName("searchHistory can scope to message entry id")
    void searchHistoryScopedToMessageEntry() {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        BlackboardHistory history = buildHistory();
        blackboard.addObject(history);

        AgentProcess agentProcess = mock(AgentProcess.class);
        when(agentProcess.getBlackboard()).thenReturn(blackboard);

        AgentPlatform platform = mock(AgentPlatform.class);
        when(platform.getAgentProcess("session-2")).thenReturn(agentProcess);

        ContextManagerTools tools = new ContextManagerTools(platform);

        ContextManagerTools.HistorySearchResult result = tools.searchHistory(
                "session-2",
                "hello",
                10,
                "messages:1"
        );

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).actionName()).contains("messages:1::NODE_STREAM_DELTA");
    }

    @Test
    @DisplayName("listHistory fails without session id")
    void listHistoryRequiresSessionId() {
        AgentPlatform platform = mock(AgentPlatform.class);
        ContextManagerTools tools = new ContextManagerTools(platform);

        ContextManagerTools.HistoryListingResult listing = tools.listHistory(
                null,
                0,
                10,
                null,
                null,
                null
        );

        assertThat(listing.status()).isEqualTo("error");
        assertThat(listing.error()).isEqualTo(ContextManagerTools.SESSION_ID_MISSING_MESSAGE);
    }

    private BlackboardHistory buildHistory() {
        List<BlackboardHistory.Entry> entries = new ArrayList<>();
        entries.add(new BlackboardHistory.DefaultEntry(
                Instant.now(),
                "node:node-1::ACTION_STARTED",
                "payload",
                String.class
        ));

        Events.NodeStreamDeltaEvent eventOne = new Events.NodeStreamDeltaEvent(
                "evt-1",
                Instant.now(),
                "node-1",
                "hello",
                5,
                false
        );
        Events.NodeStreamDeltaEvent eventTwo = new Events.NodeStreamDeltaEvent(
                "evt-2",
                Instant.now(),
                "node-1",
                "world",
                5,
                true
        );
        entries.add(new BlackboardHistory.MessageEntry(
                Instant.now(),
                "node:node-1::messages",
                new ArrayList<>(List.of(eventOne, eventTwo))
        ));
        String value = ArtifactKey.createRoot().value();
        return new BlackboardHistory(new BlackboardHistory.History(entries), value, WorkflowGraphState.initial(value));
    }
}
