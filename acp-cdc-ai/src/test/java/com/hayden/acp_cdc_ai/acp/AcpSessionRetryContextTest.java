package com.hayden.acp_cdc_ai.acp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AcpSessionRetryContextTest {

    private AcpSessionRetryContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new AcpSessionRetryContext("session-1");
    }

    @Test
    void initialStateIsNotRetry() {
        assertThat(ctx.isRetry()).isFalse();
        assertThat(ctx.retryCount()).isZero();
        assertThat(ctx.errorCategory()).isEqualTo(AcpSessionRetryContext.ErrorCategory.NONE);
        assertThat(ctx.compactionStatus()).isEqualTo(AcpSessionRetryContext.CompactionStatus.NONE);
        assertThat(ctx.errorDetail()).isNull();
        assertThat(ctx.sessionKey()).isEqualTo("session-1");
    }

    @Nested
    class RecordError {

        @Test
        void recordErrorSetsRetryState() {
            ctx.recordError(AcpSessionRetryContext.ErrorCategory.PARSE, "bad json");

            assertThat(ctx.isRetry()).isTrue();
            assertThat(ctx.retryCount()).isEqualTo(1);
            assertThat(ctx.errorCategory()).isEqualTo(AcpSessionRetryContext.ErrorCategory.PARSE);
            assertThat(ctx.errorDetail()).isEqualTo("bad json");
        }

        @Test
        void multipleErrorsIncrementRetryCount() {
            ctx.recordError(AcpSessionRetryContext.ErrorCategory.TIMEOUT, "timeout1");
            ctx.recordError(AcpSessionRetryContext.ErrorCategory.PARSE, "parse1");

            assertThat(ctx.retryCount()).isEqualTo(2);
            assertThat(ctx.errorCategory()).isEqualTo(AcpSessionRetryContext.ErrorCategory.PARSE);
        }
    }

    @Nested
    class RecordCompaction {

        @Test
        void firstCompactionSetsSingleStatus() {
            ctx.recordCompaction("ctx-1");

            assertThat(ctx.isRetry()).isTrue();
            assertThat(ctx.errorCategory()).isEqualTo(AcpSessionRetryContext.ErrorCategory.COMPACTION);
            assertThat(ctx.compactionStatus()).isEqualTo(AcpSessionRetryContext.CompactionStatus.SINGLE);
            assertThat(ctx.errorDetail()).isEqualTo("ctx-1");
        }

        @Test
        void secondCompactionSetsMultipleStatus() {
            ctx.recordCompaction("ctx-1");
            ctx.recordCompaction("ctx-2");

            assertThat(ctx.compactionStatus()).isEqualTo(AcpSessionRetryContext.CompactionStatus.MULTIPLE);
            assertThat(ctx.retryCount()).isEqualTo(2);
        }
    }

    @Nested
    class ClearOnSuccess {

        @Test
        void clearResetsAllState() {
            ctx.recordCompaction("ctx-1");
            ctx.recordError(AcpSessionRetryContext.ErrorCategory.TIMEOUT, "t");
            assertThat(ctx.isRetry()).isTrue();

            ctx.clearOnSuccess();

            assertThat(ctx.isRetry()).isFalse();
            assertThat(ctx.retryCount()).isZero();
            assertThat(ctx.errorCategory()).isEqualTo(AcpSessionRetryContext.ErrorCategory.NONE);
            assertThat(ctx.compactionStatus()).isEqualTo(AcpSessionRetryContext.CompactionStatus.NONE);
            assertThat(ctx.errorDetail()).isNull();
        }
    }

    @Nested
    class CompactionStatusNext {

        @Test
        void noneToSingle() {
            assertThat(AcpSessionRetryContext.CompactionStatus.NONE.next())
                    .isEqualTo(AcpSessionRetryContext.CompactionStatus.SINGLE);
        }

        @Test
        void singleToMultiple() {
            assertThat(AcpSessionRetryContext.CompactionStatus.SINGLE.next())
                    .isEqualTo(AcpSessionRetryContext.CompactionStatus.MULTIPLE);
        }

        @Test
        void multipleStaysMultiple() {
            assertThat(AcpSessionRetryContext.CompactionStatus.MULTIPLE.next())
                    .isEqualTo(AcpSessionRetryContext.CompactionStatus.MULTIPLE);
        }
    }
}
