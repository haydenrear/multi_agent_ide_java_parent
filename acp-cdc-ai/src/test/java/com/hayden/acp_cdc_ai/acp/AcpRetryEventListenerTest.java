package com.hayden.acp_cdc_ai.acp;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AcpRetryEventListenerTest {

    private AcpRetryEventListener listener;
    /** A valid ArtifactKey whose .value() we use as the session key in the map. */
    private ArtifactKey sessionAk;
    private String sessionKey;

    @BeforeEach
    void setUp() {
        listener = new AcpRetryEventListener();
        sessionAk = ArtifactKey.createRoot();
        sessionKey = sessionAk.value();
    }

    private Events.ChatSessionCreatedEvent sessionCreated() {
        return new Events.ChatSessionCreatedEvent(
                "evt-1", Instant.now(), "node-1", sessionAk);
    }

    private Events.AgentExecutorStartEvent executorStart() {
        return new Events.AgentExecutorStartEvent(
                "evt-2", Instant.now(), "node-1", sessionKey, "someAction");
    }

    private Events.AgentExecutorCompleteEvent executorComplete() {
        return new Events.AgentExecutorCompleteEvent(
                "evt-3", Instant.now(), "node-1", sessionKey, "someAction");
    }

    private Events.CompactionEvent compaction() {
        // CompactionEvent's nodeId must match sessionKey for lookup
        return new Events.CompactionEvent(
                "evt-4", Instant.now(), sessionKey, "prompt too long");
    }

    private Events.ChatSessionClosedEvent sessionClosed() {
        return new Events.ChatSessionClosedEvent(
                "evt-5", Instant.now(), sessionKey);
    }

    @Nested
    class SessionLifecycle {

        @Test
        void sessionCreatedCreatesContext() {
            listener.onEvent(sessionCreated());

            assertThat(listener.retryContextFor(sessionKey)).isNotNull();
            assertThat(listener.retryContextFor(sessionKey).isRetry()).isFalse();
        }

        @Test
        void sessionClosedRemovesContext() {
            listener.onEvent(sessionCreated());
            listener.onEvent(sessionClosed());

            assertThat(listener.retryContextFor(sessionKey)).isNull();
        }

        @Test
        void unknownSessionReturnsNull() {
            assertThat(listener.retryContextFor("unknown")).isNull();
        }
    }

    @Nested
    class ExecutorLifecycle {

        @Test
        void executorStartClearsRetryState() {
            listener.onEvent(sessionCreated());
            listener.onEvent(compaction());
            assertThat(listener.retryContextFor(sessionKey).isRetry()).isTrue();

            listener.onEvent(executorStart());
            assertThat(listener.retryContextFor(sessionKey).isRetry()).isFalse();
        }

        @Test
        void executorCompleteClearsRetryState() {
            listener.onEvent(sessionCreated());
            listener.onEvent(compaction());
            assertThat(listener.retryContextFor(sessionKey).isRetry()).isTrue();

            listener.onEvent(executorComplete());
            assertThat(listener.retryContextFor(sessionKey).isRetry()).isFalse();
        }

        @Test
        void executorStartOnUnknownSessionIsNoOp() {
            listener.onEvent(new Events.AgentExecutorStartEvent(
                    "evt", Instant.now(), "node", "unknown", "action"));
        }
    }

    @Nested
    class CompactionTracking {

        @Test
        void compactionRecordsRetryState() {
            listener.onEvent(sessionCreated());
            listener.onEvent(compaction());

            var ctx = listener.retryContextFor(sessionKey);
            assertThat(ctx.isRetry()).isTrue();
            assertThat(ctx.errorCategory()).isEqualTo(AcpSessionRetryContext.ErrorCategory.COMPACTION);
            assertThat(ctx.compactionStatus()).isEqualTo(AcpSessionRetryContext.CompactionStatus.SINGLE);
        }

        @Test
        void secondCompactionSetsMultipleStatus() {
            listener.onEvent(sessionCreated());
            listener.onEvent(compaction());
            listener.onEvent(compaction());

            var ctx = listener.retryContextFor(sessionKey);
            assertThat(ctx.compactionStatus()).isEqualTo(AcpSessionRetryContext.CompactionStatus.MULTIPLE);
            assertThat(ctx.retryCount()).isEqualTo(2);
        }

        @Test
        void compactionOnUnknownNodeIsNoOp() {
            listener.onEvent(new Events.CompactionEvent(
                    "evt", Instant.now(), "unknown-node", "msg"));
            assertThat(listener.retryContextFor("unknown-node")).isNull();
        }
    }

    @Test
    void listenerIdIsStable() {
        assertThat(listener.listenerId()).isEqualTo("AcpRetryEventListener");
    }
}
