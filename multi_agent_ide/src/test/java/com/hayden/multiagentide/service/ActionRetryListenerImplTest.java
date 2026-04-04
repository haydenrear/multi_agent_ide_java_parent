package com.hayden.multiagentide.service;

import com.embabel.agent.core.AgentProcess;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.CompactionException;
import com.hayden.multiagentidelib.agent.ErrorDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.retry.RetryContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ActionRetryListenerImplTest {

    private ActionRetryListenerImpl listener;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        listener = new ActionRetryListenerImpl();
        eventBus = mock(EventBus.class);
        // Inject via reflection since it's @Autowired @Lazy
        try {
            var field = ActionRetryListenerImpl.class.getDeclaredField("eventBus");
            field.setAccessible(true);
            field.set(listener, eventBus);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    class Classification {

        private final String actionName = "coordinateWorkflow";
        private final ArtifactKey contextId = ArtifactKey.createRoot();

        private BlackboardHistory emptyHistory() {
            return new BlackboardHistory(new BlackboardHistory.History(), "test-node", null);
        }

        @Test
        void compactionException_classifiesAsCompactionError() {
            var history = emptyHistory();
            var result = listener.classify(
                    new CompactionException("session compacting", "session-1"),
                    actionName, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.CompactionError.class);
            var ce = (ErrorDescriptor.CompactionError) result;
            assertThat(ce.actionName()).isEqualTo(actionName);
            assertThat(ce.compactionStatus()).isEqualTo(ErrorDescriptor.CompactionStatus.FIRST);
            assertThat(ce.compactionCompleted()).isFalse();
        }

        @Test
        void compactingMessage_classifiesAsCompactionError() {
            var history = emptyHistory();
            var result = listener.classify(
                    new RuntimeException("ACP session Compacting..."),
                    actionName, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.CompactionError.class);
        }

        @Test
        void promptTooLong_classifiesAsCompactionError() {
            var history = emptyHistory();
            var result = listener.classify(
                    new RuntimeException("Prompt is too long for this model"),
                    actionName, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.CompactionError.class);
        }

        @Test
        void timeoutException_classifiesAsTimeoutError() {
            var history = emptyHistory();
            var result = listener.classify(
                    new java.util.concurrent.TimeoutException("request timed out"),
                    actionName, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.TimeoutError.class);
            assertThat(((ErrorDescriptor.TimeoutError) result).retryCount()).isEqualTo(1);
        }

        @Test
        void timeoutMessage_classifiesAsTimeoutError() {
            var history = emptyHistory();
            var result = listener.classify(
                    new RuntimeException("Connection timeout after 30s"),
                    actionName, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.TimeoutError.class);
        }

        @Test
        void jsonParseException_classifiesAsParseError() {
            var history = emptyHistory();
            var result = listener.classify(
                    new com.fasterxml.jackson.core.JsonParseException(null, "Unexpected token"),
                    actionName, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.ParseError.class);
            assertThat(((ErrorDescriptor.ParseError) result).actionName()).isEqualTo(actionName);
        }

        @Test
        void toolCallMessage_classifiesAsUnparsedToolCallError() {
            var history = emptyHistory();
            var result = listener.classify(
                    new RuntimeException("Unparsed tool call in response"),
                    actionName, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.UnparsedToolCallError.class);
        }

        @Test
        void unknownException_fallsBackToParseError() {
            var history = emptyHistory();
            var result = listener.classify(
                    new RuntimeException("Something went wrong"),
                    actionName, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.ParseError.class);
        }

        @Test
        void secondCompaction_statusIsMultiple() {
            var history = emptyHistory();
            // First compaction
            history.addError(new ErrorDescriptor.CompactionError(
                    actionName, ErrorDescriptor.CompactionStatus.FIRST, false, contextId));
            // Second compaction
            var result = listener.classify(
                    new CompactionException("compacting again", "session-1"),
                    actionName, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.CompactionError.class);
            assertThat(((ErrorDescriptor.CompactionError) result).compactionStatus())
                    .isEqualTo(ErrorDescriptor.CompactionStatus.MULTIPLE);
        }
    }

    @Nested
    class OnActionRetry {

        @Test
        void recordsErrorInBlackboardHistory() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test-node", null);
            // Seed an entry so resolveActionName/resolveContextId work
            history.addError(new ErrorDescriptor.NoError(ArtifactKey.createRoot()));

            AgentProcess process = mock(AgentProcess.class);
            when(process.last(BlackboardHistory.class)).thenReturn(history);
            when(process.getId()).thenReturn("test-process");

            RetryContext retryContext = mock(RetryContext.class);
            when(retryContext.getRetryCount()).thenReturn(1);

            listener.onActionRetry(retryContext,
                    new RuntimeException("Something went wrong"), process);

            assertThat(history.errorType()).isNotNull();
            assertThat(history.errorType()).isInstanceOf(ErrorDescriptor.ParseError.class);
        }

        @Test
        void noBlackboardHistory_logsWarningAndReturns() {
            AgentProcess process = mock(AgentProcess.class);
            when(process.last(BlackboardHistory.class)).thenReturn(null);
            when(process.getId()).thenReturn("test-process");

            RetryContext retryContext = mock(RetryContext.class);

            // Should not throw
            listener.onActionRetry(retryContext,
                    new RuntimeException("error"), process);

            // No error recorded (no history to record in)
            verify(process).last(BlackboardHistory.class);
        }
    }

    @Nested
    class BlackboardHistoryErrorMethods {

        @Test
        void addError_andErrorType_returnsLatest() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            var key = ArtifactKey.createRoot();

            assertThat(history.errorType()).isNull();

            history.addError(new ErrorDescriptor.ParseError("action1", "bad json", key));
            assertThat(history.errorType()).isInstanceOf(ErrorDescriptor.ParseError.class);

            history.addError(new ErrorDescriptor.TimeoutError("action2", 1, key));
            assertThat(history.errorType()).isInstanceOf(ErrorDescriptor.TimeoutError.class);
        }

        @Test
        void compactionStatus_tracksCompactionErrors() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            var key = ArtifactKey.createRoot();

            assertThat(history.compactionStatus()).isEqualTo(ErrorDescriptor.CompactionStatus.NONE);

            history.addError(new ErrorDescriptor.CompactionError(
                    "action1", ErrorDescriptor.CompactionStatus.FIRST, false, key));
            assertThat(history.compactionStatus()).isEqualTo(ErrorDescriptor.CompactionStatus.FIRST);

            history.addError(new ErrorDescriptor.CompactionError(
                    "action1", ErrorDescriptor.CompactionStatus.MULTIPLE, false, key));
            assertThat(history.compactionStatus()).isEqualTo(ErrorDescriptor.CompactionStatus.MULTIPLE);
        }

        @Test
        void errorCount_countsByType() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            var key = ArtifactKey.createRoot();

            assertThat(history.errorCount(ErrorDescriptor.ParseError.class)).isZero();

            history.addError(new ErrorDescriptor.ParseError("a", "msg", key));
            history.addError(new ErrorDescriptor.ParseError("b", "msg2", key));
            history.addError(new ErrorDescriptor.TimeoutError("c", 1, key));

            assertThat(history.errorCount(ErrorDescriptor.ParseError.class)).isEqualTo(2);
            assertThat(history.errorCount(ErrorDescriptor.TimeoutError.class)).isEqualTo(1);
            assertThat(history.errorCount(ErrorDescriptor.CompactionError.class)).isZero();
        }

        @Test
        void noError_returns_noError() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            assertThat(history.errorType()).isNull();
            assertThat(history.compactionStatus()).isEqualTo(ErrorDescriptor.CompactionStatus.NONE);
        }
    }
}
