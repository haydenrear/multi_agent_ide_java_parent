package com.hayden.multiagentide.service;

import com.embabel.agent.core.AgentProcess;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.CompactionException;
import com.hayden.multiagentidelib.agent.ErrorDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.retry.RetryContext;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ActionRetryListenerImplTest {

    private static final ErrorDescriptor.ErrorContext EC = ErrorDescriptor.ErrorContext.EMPTY;

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

    private static Events.AgentExecutorStartEvent startEvent(String actionName, String sessionKey,
                                                              String nodeId, String requestContextId) {
        return new Events.AgentExecutorStartEvent(
                "evt-" + System.nanoTime(), Instant.now(), nodeId, sessionKey, actionName, requestContextId);
    }

    private static Events.AgentExecutorCompleteEvent completeEvent(String actionName, String sessionKey,
                                                                    String nodeId, String requestContextId,
                                                                    String startNodeId) {
        return new Events.AgentExecutorCompleteEvent(
                "evt-" + System.nanoTime(), Instant.now(), nodeId, sessionKey, actionName, requestContextId, startNodeId);
    }

    @Nested
    class Classification {

        private final String actionName = "coordinateWorkflow";
        private final String sessionKey = "test-session";
        private final ArtifactKey contextId = ArtifactKey.createRoot();

        private BlackboardHistory emptyHistory() {
            return new BlackboardHistory(new BlackboardHistory.History(), "test-node", null);
        }

        @Test
        void compactionException_classifiesAsCompactionError() {
            var history = emptyHistory();
            var result = listener.classify(
                    new CompactionException("session compacting", "session-1"),
                    actionName, sessionKey, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.CompactionError.class);
            var ce = (ErrorDescriptor.CompactionError) result;
            assertThat(ce.actionName()).isEqualTo(actionName);
            assertThat(ce.sessionKey()).isEqualTo(sessionKey);
            assertThat(ce.compactionStatus()).isEqualTo(ErrorDescriptor.CompactionStatus.FIRST);
            assertThat(ce.compactionCompleted()).isFalse();
        }

        @Test
        void compactingMessage_classifiesAsCompactionError() {
            var history = emptyHistory();
            var result = listener.classify(
                    new RuntimeException("ACP session Compacting..."),
                    actionName, sessionKey, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.CompactionError.class);
        }

        @Test
        void promptTooLong_classifiesAsCompactionError() {
            var history = emptyHistory();
            var result = listener.classify(
                    new RuntimeException("Prompt is too long for this model"),
                    actionName, sessionKey, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.CompactionError.class);
        }

        @Test
        void timeoutException_classifiesAsTimeoutError() {
            var history = emptyHistory();
            var result = listener.classify(
                    new java.util.concurrent.TimeoutException("request timed out"),
                    actionName, sessionKey, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.TimeoutError.class);
            assertThat(((ErrorDescriptor.TimeoutError) result).retryCount()).isEqualTo(1);
            assertThat(((ErrorDescriptor.TimeoutError) result).sessionKey()).isEqualTo(sessionKey);
        }

        @Test
        void timeoutMessage_classifiesAsTimeoutError() {
            var history = emptyHistory();
            var result = listener.classify(
                    new RuntimeException("Connection timeout after 30s"),
                    actionName, sessionKey, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.TimeoutError.class);
        }

        @Test
        void jsonParseException_classifiesAsParseError() {
            var history = emptyHistory();
            var result = listener.classify(
                    new com.fasterxml.jackson.core.JsonParseException(null, "Unexpected token"),
                    actionName, sessionKey, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.ParseError.class);
            assertThat(((ErrorDescriptor.ParseError) result).actionName()).isEqualTo(actionName);
            assertThat(((ErrorDescriptor.ParseError) result).sessionKey()).isEqualTo(sessionKey);
        }

        @Test
        void toolCallMessage_classifiesAsUnparsedToolCallError() {
            var history = emptyHistory();
            var result = listener.classify(
                    new RuntimeException("Unparsed tool call in response"),
                    actionName, sessionKey, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.UnparsedToolCallError.class);
            assertThat(((ErrorDescriptor.UnparsedToolCallError) result).sessionKey()).isEqualTo(sessionKey);
        }

        @Test
        void unknownException_fallsBackToParseError() {
            var history = emptyHistory();
            var result = listener.classify(
                    new RuntimeException("Something went wrong"),
                    actionName, sessionKey, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.ParseError.class);
        }

        @Test
        void secondCompaction_statusIsMultiple() {
            var history = emptyHistory();
            // Seed a start event so errorCountForRetrySequence works
            history.addEntry("test", startEvent("coordinateWorkflow", sessionKey, "node-1", "req-1"));
            // First compaction
            history.addError(new ErrorDescriptor.CompactionError(
                    actionName, sessionKey, ErrorDescriptor.CompactionStatus.FIRST, false, "", contextId, EC));
            // Second compaction
            var result = listener.classify(
                    new CompactionException("compacting again", "session-1"),
                    actionName, sessionKey, contextId, history);
            assertThat(result).isInstanceOf(ErrorDescriptor.CompactionError.class);
            assertThat(((ErrorDescriptor.CompactionError) result).compactionStatus())
                    .isEqualTo(ErrorDescriptor.CompactionStatus.MULTIPLE);
        }

        @Test
        void errorContextId_isChildOfStartEventNodeId() {
            var history = emptyHistory();
            var parentId = ArtifactKey.createRoot();
            var childId = parentId.createChild();
            var result = listener.classify(
                    new RuntimeException("Something went wrong"),
                    actionName, sessionKey, childId, history);
            // The contextId should be the child we passed in (ActionRetryListenerImpl creates the child)
            assertThat(result.contextId()).isEqualTo(childId);
            assertThat(result.contextId()).isNotEqualTo(parentId);
        }
    }

    @Nested
    class FindLastUnmatchedStart {

        @Test
        void noStartEvent_returnsNull() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            assertThat(listener.findLastUnmatchedStart(history)).isNull();
        }

        @Test
        void unmatchedStart_returnsIt() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            var start = startEvent("action", "session", "node-1", "req-1");
            history.addEntry("test", start);

            var result = listener.findLastUnmatchedStart(history);
            assertThat(result).isNotNull();
            assertThat(result.requestContextId()).isEqualTo("req-1");
        }

        @Test
        void matchedStart_returnsNull() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            history.addEntry("test", startEvent("action", "session", "node-1", "req-1"));
            history.addEntry("test", completeEvent("action", "session", "node-1", "req-1", "node-1"));

            assertThat(listener.findLastUnmatchedStart(history)).isNull();
        }

        @Test
        void multipleStarts_lastUnmatched_returnsIt() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            // First execution — matched
            history.addEntry("test", startEvent("action", "session", "node-1", "req-1"));
            history.addEntry("test", completeEvent("action", "session", "node-1", "req-1", "node-1"));
            // Second execution — unmatched (retry in progress)
            history.addEntry("test", startEvent("action", "session", "node-2", "req-2"));

            var result = listener.findLastUnmatchedStart(history);
            assertThat(result).isNotNull();
            assertThat(result.requestContextId()).isEqualTo("req-2");
        }

        @Test
        void differentStartNodeId_doesNotCrossMatch() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            // Start with nodeId=node-1, complete with startNodeId=different-node — not a match
            history.addEntry("test", startEvent("action", "session", "node-1", "req-1"));
            history.addEntry("test", completeEvent("action", "session", "node-1", "req-1", "different-node"));

            var result = listener.findLastUnmatchedStart(history);
            // The complete's startNodeId doesn't match the start's nodeId, so the start is unmatched
            assertThat(result).isNotNull();
            assertThat(result.nodeId()).isEqualTo("node-1");
        }
    }

    @Nested
    class OnActionRetry {

        @Test
        void recordsErrorInBlackboardHistory() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test-node", null);
            // Seed an unmatched start event — required for onActionRetry to proceed
            var nodeId = ArtifactKey.createRoot();
            history.addEntry("test", startEvent("coordinateWorkflow", "session-1", nodeId.value(), "req-1"));

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
        void errorContextId_isChildOfStartEventNodeId() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test-node", null);
            var nodeId = ArtifactKey.createRoot();
            history.addEntry("test", startEvent("coordinateWorkflow", "session-1",
                    nodeId.value(), "req-1"));

            AgentProcess process = mock(AgentProcess.class);
            when(process.last(BlackboardHistory.class)).thenReturn(history);
            when(process.getId()).thenReturn("test-process");

            RetryContext retryContext = mock(RetryContext.class);
            when(retryContext.getRetryCount()).thenReturn(1);

            listener.onActionRetry(retryContext,
                    new RuntimeException("Something went wrong"), process);

            ErrorDescriptor error = history.errorType();
            assertThat(error).isNotNull();
            // The error's contextId should be a child of the start event's nodeId
            assertThat(error.contextId().value()).startsWith(nodeId.value());
            assertThat(error.contextId()).isNotEqualTo(nodeId);
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

        @Test
        void noStartEvent_returnsEarly() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test-node", null);

            AgentProcess process = mock(AgentProcess.class);
            when(process.last(BlackboardHistory.class)).thenReturn(history);
            when(process.getId()).thenReturn("test-process");

            RetryContext retryContext = mock(RetryContext.class);

            listener.onActionRetry(retryContext,
                    new RuntimeException("error"), process);

            // No error should be recorded
            assertThat(history.errorType()).isNull();
        }

        @Test
        void completedExecution_returnsEarly() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test-node", null);
            // Start event followed by complete event with same requestContextId = already done
            history.addEntry("test", startEvent("coordinateWorkflow", "session-1", "node-1", "req-1"));
            history.addEntry("test", completeEvent("coordinateWorkflow", "session-1", "node-1", "req-1", "node-1"));

            AgentProcess process = mock(AgentProcess.class);
            when(process.last(BlackboardHistory.class)).thenReturn(history);
            when(process.getId()).thenReturn("test-process");

            RetryContext retryContext = mock(RetryContext.class);

            listener.onActionRetry(retryContext,
                    new RuntimeException("error"), process);

            // No new error should be recorded
            assertThat(history.errorType()).isNull();
        }
    }

    @Nested
    class BlackboardHistoryErrorMethods {

        private final String sessionKey = "test-session";

        @Test
        void addError_andErrorType_returnsLatest() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            var key = ArtifactKey.createRoot();

            assertThat(history.errorType()).isNull();

            history.addError(new ErrorDescriptor.ParseError("action1", sessionKey, "bad json", "", key, EC));
            assertThat(history.errorType()).isInstanceOf(ErrorDescriptor.ParseError.class);

            history.addError(new ErrorDescriptor.TimeoutError("action2", sessionKey, 1, "", key, EC));
            assertThat(history.errorType()).isInstanceOf(ErrorDescriptor.TimeoutError.class);
        }

        @Test
        void compactionStatus_tracksCompactionErrors() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            var key = ArtifactKey.createRoot();

            assertThat(history.compactionStatus()).isEqualTo(ErrorDescriptor.CompactionStatus.NONE);

            history.addError(new ErrorDescriptor.CompactionError(
                    "action1", sessionKey, ErrorDescriptor.CompactionStatus.FIRST, false, "", key, EC));
            assertThat(history.compactionStatus()).isEqualTo(ErrorDescriptor.CompactionStatus.FIRST);

            history.addError(new ErrorDescriptor.CompactionError(
                    "action1", sessionKey, ErrorDescriptor.CompactionStatus.MULTIPLE, false, "", key, EC));
            assertThat(history.compactionStatus()).isEqualTo(ErrorDescriptor.CompactionStatus.MULTIPLE);
        }

        @Test
        void errorCountForRetrySequence_spansEntireRetryChain() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            var key = ArtifactKey.createRoot();

            // Errors before any start event — not counted
            history.addError(new ErrorDescriptor.ParseError("old", sessionKey, "msg", "", key, EC));

            // First attempt (unmatched — no complete)
            history.addEntry("test", startEvent("action1", sessionKey, "node-1", "req-1"));
            history.addError(new ErrorDescriptor.TimeoutError("a", sessionKey, 1, "", key, EC));

            // Second attempt (also unmatched — retry chain continues)
            history.addEntry("test", startEvent("action1", sessionKey, "node-2", "req-2"));
            history.addError(new ErrorDescriptor.TimeoutError("a", sessionKey, 2, "", key, EC));

            // Counts from req-1 (first unmatched) forward: 2 timeouts
            assertThat(history.errorCountForRetrySequence(ErrorDescriptor.TimeoutError.class, sessionKey)).isEqualTo(2);
            // The pre-chain parse error is excluded
            assertThat(history.errorCountForRetrySequence(ErrorDescriptor.ParseError.class, sessionKey)).isEqualTo(0);
        }

        @Test
        void errorCountForRetrySequence_excludesCompletedExecutions() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            var key = ArtifactKey.createRoot();

            // Completed execution — should not be in retry chain
            history.addEntry("test", startEvent("action1", sessionKey, "node-1", "req-1"));
            history.addError(new ErrorDescriptor.TimeoutError("a", sessionKey, 1, "", key, EC));
            history.addEntry("test", completeEvent("action1", sessionKey, "node-1", "req-1", "node-1"));

            // New retry chain starts here
            history.addEntry("test", startEvent("action1", sessionKey, "node-2", "req-2"));
            history.addError(new ErrorDescriptor.TimeoutError("a", sessionKey, 1, "", key, EC));

            // Only 1 timeout — the one after req-2
            assertThat(history.errorCountForRetrySequence(ErrorDescriptor.TimeoutError.class, sessionKey)).isEqualTo(1);
        }

        @Test
        void errorCount_countsByType() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            var key = ArtifactKey.createRoot();

            assertThat(history.errorCount(ErrorDescriptor.ParseError.class)).isZero();

            history.addError(new ErrorDescriptor.ParseError("a", sessionKey, "msg", "", key, EC));
            history.addError(new ErrorDescriptor.ParseError("b", sessionKey, "msg2", "", key, EC));
            history.addError(new ErrorDescriptor.TimeoutError("c", sessionKey, 1, "", key, EC));

            assertThat(history.errorCount(ErrorDescriptor.ParseError.class)).isEqualTo(2);
            assertThat(history.errorCount(ErrorDescriptor.TimeoutError.class)).isEqualTo(1);
            assertThat(history.errorCount(ErrorDescriptor.CompactionError.class)).isZero();
        }

        @Test
        void isLastExecutionComplete_detectsCompletedExecution() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);

            assertThat(history.isLastExecutionComplete()).isFalse();

            history.addEntry("test", startEvent("action1", sessionKey, "node-1", "req-1"));
            assertThat(history.isLastExecutionComplete()).isFalse();

            history.addEntry("test", completeEvent("action1", sessionKey, "node-1", "req-1", "node-1"));
            assertThat(history.isLastExecutionComplete()).isTrue();
        }

        @Test
        void noError_returns_noError() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            assertThat(history.errorType()).isNull();
            assertThat(history.compactionStatus()).isEqualTo(ErrorDescriptor.CompactionStatus.NONE);
        }

        @Test
        void errorType_withSessionKey_returnsLastInRetrySequence() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            var key = ArtifactKey.createRoot();

            history.addEntry("test", startEvent("action1", sessionKey, "node-1", "req-1"));
            history.addError(new ErrorDescriptor.ParseError("action1", sessionKey, "first", "", key, EC));
            history.addError(new ErrorDescriptor.TimeoutError("action1", sessionKey, 1, "", key, EC));

            // Should return the last error (TimeoutError), not the first
            var result = history.errorType(sessionKey);
            assertThat(result).isInstanceOf(ErrorDescriptor.TimeoutError.class);
        }

        @Test
        void errorType_withSessionKey_excludesCompletedExecution() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            var key = ArtifactKey.createRoot();

            // Completed execution with errors
            history.addEntry("test", startEvent("action1", sessionKey, "node-1", "req-1"));
            history.addError(new ErrorDescriptor.ParseError("action1", sessionKey, "old error", "", key, EC));
            history.addEntry("test", completeEvent("action1", sessionKey, "node-1", "req-1", "node-1"));

            // No active retry sequence — should return null
            assertThat(history.errorType(sessionKey)).isNull();
        }

        @Test
        void errorType_withSessionKey_noActiveRetry_returnsNull() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            // No start events at all
            assertThat(history.errorType(sessionKey)).isNull();
        }

        @Test
        void errorType_withSessionKey_ignoresOtherSessions() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            var key = ArtifactKey.createRoot();
            String otherSession = "other-session";

            // Start for our session
            history.addEntry("test", startEvent("action1", sessionKey, "node-1", "req-1"));
            // Error from a different session interleaved
            history.addError(new ErrorDescriptor.ParseError("action1", otherSession, "other session error", "", key, EC));
            // Error from our session
            history.addError(new ErrorDescriptor.TimeoutError("action1", sessionKey, 1, "", key, EC));

            var result = history.errorType(sessionKey);
            assertThat(result).isInstanceOf(ErrorDescriptor.TimeoutError.class);
            assertThat(((ErrorDescriptor.TimeoutError) result).sessionKey()).isEqualTo(sessionKey);
        }

        @Test
        void errorContext_accumulatesAcrossMultipleStartsAndErrors() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            var key = ArtifactKey.createRoot();

            // First attempt — unmatched start, then error
            history.addEntry("test", startEvent("action1", sessionKey, "node-1", "req-1"));
            // First error: no previous errors, so ErrorContext is EMPTY
            var error1 = new ErrorDescriptor.ParseError("action1", sessionKey, "bad json", "", key, EC);
            history.addError(error1);

            // Second attempt — another unmatched start (framework retried), then error
            history.addEntry("test", startEvent("action1", sessionKey, "node-2", "req-2"));
            // Build cumulative context: previous error was error1
            ErrorDescriptor previousError = history.errorType(sessionKey);
            assertThat(previousError).isNotNull();
            var ctx2 = previousError.errorContext().withError(previousError);
            var error2 = new ErrorDescriptor.TimeoutError("action1", sessionKey, 1, "", key, ctx2);
            history.addError(error2);

            // Third attempt — yet another unmatched start, then error
            history.addEntry("test", startEvent("action1", sessionKey, "node-3", "req-3"));
            previousError = history.errorType(sessionKey);
            assertThat(previousError).isNotNull();
            var ctx3 = previousError.errorContext().withError(previousError);
            var error3 = new ErrorDescriptor.CompactionError("action1", sessionKey,
                    ErrorDescriptor.CompactionStatus.FIRST, false, "", key, ctx3);
            history.addError(error3);

            // Verify cumulative context on error3: should have error1 and error2
            assertThat(error3.errorContext().errorCount()).isEqualTo(2);
            assertThat(error3.errorContext().previousErrors().get(0).errorType()).isEqualTo("ParseError");
            assertThat(error3.errorContext().previousErrors().get(1).errorType()).isEqualTo("TimeoutError");

            // And error2 should only have error1
            assertThat(error2.errorContext().errorCount()).isEqualTo(1);
            assertThat(error2.errorContext().previousErrors().get(0).errorType()).isEqualTo("ParseError");

            // error1 should have empty context
            assertThat(error1.errorContext().hasErrors()).isFalse();
        }

        @Test
        void findFirstUnmatchedStartIndex_scopesBySessionKey() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            String otherSession = "other-session";

            // Completed execution for our session
            history.addEntry("test", startEvent("action1", sessionKey, "node-1", "req-1"));
            history.addEntry("test", completeEvent("action1", sessionKey, "node-1", "req-1", "node-1"));

            // Unmatched start for a different session — should be ignored
            history.addEntry("test", startEvent("action1", otherSession, "node-2", "req-2"));

            // Unmatched start for our session — this is the one we want
            history.addEntry("test", startEvent("action1", sessionKey, "node-3", "req-3"));

            int idx = history.findFirstUnmatchedStartIndex(sessionKey);
            assertThat(idx).isGreaterThanOrEqualTo(0);

            // Verify we found the right start by checking the entry
            var entries = history.copyOfEntries();
            var entry = (BlackboardHistory.DefaultEntry) entries.get(idx);
            var startEvt = (Events.AgentExecutorStartEvent) entry.input();
            assertThat(startEvt.requestContextId()).isEqualTo("req-3");
            assertThat(startEvt.sessionKey()).isEqualTo(sessionKey);
        }

        @Test
        void errorContext_restartsAfterCompletedExecution() {
            var history = new BlackboardHistory(new BlackboardHistory.History(), "test", null);
            var key = ArtifactKey.createRoot();

            // First execution — completes successfully after an error
            history.addEntry("test", startEvent("action1", sessionKey, "node-1", "req-1"));
            history.addError(new ErrorDescriptor.ParseError("action1", sessionKey, "old", "", key, EC));
            history.addEntry("test", completeEvent("action1", sessionKey, "node-1", "req-1", "node-1"));

            // New execution — new retry sequence, errors should NOT carry over from completed one
            history.addEntry("test", startEvent("action1", sessionKey, "node-2", "req-2"));

            // errorType should return null (no errors in the new retry sequence yet)
            assertThat(history.errorType(sessionKey)).isNull();

            // Add an error in the new sequence
            var newError = new ErrorDescriptor.TimeoutError("action1", sessionKey, 1, "", key, EC);
            history.addError(newError);

            var result = history.errorType(sessionKey);
            assertThat(result).isInstanceOf(ErrorDescriptor.TimeoutError.class);
            // The error context should be empty — previous execution's errors don't carry over
            assertThat(result.errorContext().hasErrors()).isFalse();
        }
    }
}
