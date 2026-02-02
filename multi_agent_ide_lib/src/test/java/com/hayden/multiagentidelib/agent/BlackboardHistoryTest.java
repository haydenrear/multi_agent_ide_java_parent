package com.hayden.multiagentidelib.agent;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.ProcessContext;
import com.embabel.agent.core.ProcessOptions;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BlackboardHistory")
@ExtendWith(MockitoExtension.class)
class BlackboardHistoryTest {

    @Test
    @DisplayName("ensureSubscribed adds history and registers listener")
    void ensureSubscribedAddsHistoryAndListener() {
        EventBus eventBus = mock(EventBus.class);
        OperationContext context = mock(OperationContext.class);
        var agentProcess = mock(com.embabel.agent.core.AgentProcess.class);
        var processContext = mock(ProcessContext.class);
        var processOptions = mock(ProcessOptions.class);

        when(context.getAgentProcess()).thenReturn(agentProcess);
        when(context.getProcessContext()).thenReturn(processContext);
        when(processContext.getProcessOptions()).thenReturn(processOptions);
        when(processOptions.getContextIdString()).thenReturn("node-1");
        when(context.last(BlackboardHistory.class)).thenReturn(null);

        BlackboardHistory listener = BlackboardHistory.ensureSubscribed(eventBus, context, () -> WorkflowGraphState.initial("first"));

        assertThat(listener).isNotNull();
        verify(eventBus).subscribe(listener);
        verify(agentProcess).addObject(any(BlackboardHistory.class));
        verify(agentProcess).addObject(listener);
    }

    @Test
    @DisplayName("subscription captures message and non-message events")
    void subscriptionCapturesEvents() {
        EventBus eventBus = mock(EventBus.class);
        OperationContext context = mock(OperationContext.class);
        var agentProcess = mock(com.embabel.agent.core.AgentProcess.class);
        var processContext = mock(ProcessContext.class);
        var processOptions = mock(ProcessOptions.class);

        when(context.getAgentProcess()).thenReturn(agentProcess);
        when(context.getProcessContext()).thenReturn(processContext);
        when(processContext.getProcessOptions()).thenReturn(processOptions);
        when(processOptions.getContextIdString()).thenReturn("node-2");
        when(context.last(BlackboardHistory.class)).thenReturn(null);

        BlackboardHistory.ensureSubscribed(eventBus, context, () -> WorkflowGraphState.initial("first"));

        ArgumentCaptor<EventListener> listenerCaptor = ArgumentCaptor.forClass(EventListener.class);
        verify(eventBus).subscribe(listenerCaptor.capture());
        EventListener listener = listenerCaptor.getValue();

        Events.NodeStreamDeltaEvent messageEvent = new Events.NodeStreamDeltaEvent(
                "evt-1",
                Instant.now(),
                "node-2",
                "hello",
                3,
                false
        );
        Events.NodeAddedEvent nodeAddedEvent = new Events.NodeAddedEvent(
                "evt-2",
                Instant.now(),
                "node-2",
                "Node 2",
                Events.NodeType.WORK,
                null
        );

        listener.onEvent(messageEvent);
        listener.onEvent(messageEvent);
        listener.onEvent(nodeAddedEvent);

        ArgumentCaptor<Object> addObjectCaptor = ArgumentCaptor.forClass(Object.class);
        verify(agentProcess, org.mockito.Mockito.atLeastOnce()).addObject(addObjectCaptor.capture());
        BlackboardHistory bh = addObjectCaptor.getAllValues().stream()
                .filter(value -> value instanceof BlackboardHistory)
                .map(value -> (BlackboardHistory) value)
                .findFirst()
                .orElseThrow();

        bh.fromHistory(history -> {
            assertThat(history.entries()).hasSize(2);
            BlackboardHistory.Entry first = history.entries().get(0);
            BlackboardHistory.Entry second = history.entries().get(1);
            BlackboardHistory.MessageEntry messageEntry = first instanceof BlackboardHistory.MessageEntry
                    ? (BlackboardHistory.MessageEntry) first
                    : (BlackboardHistory.MessageEntry) second;
            BlackboardHistory.DefaultEntry defaultEntry = first instanceof BlackboardHistory.DefaultEntry
                    ? (BlackboardHistory.DefaultEntry) first
                    : (BlackboardHistory.DefaultEntry) second;

            assertThat(messageEntry.actionName()).isEqualTo("node:node-2::messages");
            assertThat(messageEntry.events()).hasSize(2);
            assertThat(defaultEntry.actionName()).isEqualTo("node:node-2::NODE_ADDED");
            return true;
        });


    }
}
