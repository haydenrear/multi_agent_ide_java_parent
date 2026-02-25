package com.hayden.multiagentide.support;

import com.embabel.agent.api.common.OperationContext;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hayden.multiagentide.service.LlmRunner;
import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Test implementation of LlmRunner that returns pre-queued responses.
 * 
 * This allows tests to:
 * 1. Queue up expected LLM responses in order
 * 2. Let real agent code execute (no need to mock individual actions)
 * 3. Verify the workflow executes correctly with those responses
 * 4. Execute callbacks when responses are dequeued (to simulate agent side effects)
 * 5. Optionally serialize each call's input/output to a single markdown log file
 * 
 * Usage:
 * <pre>
 * queuedLlmRunner.setLogFile(Path.of("test_work/worktree_merge/my_test.md"));
 * queuedLlmRunner.enqueue(OrchestratorRouting.builder()...build());
 * queuedLlmRunner.enqueue(DiscoveryAgentRouting.builder()...build(), (response, ctx) -&gt; {
 *     // Simulate agent work: commit files to the agent's worktree
 * });
 * // ... run the workflow
 * queuedLlmRunner.assertAllConsumed(); // verify all responses were used
 * </pre>
 */
@Slf4j
public class QueuedLlmRunner implements LlmRunner {

    private static final ObjectMapper LOG_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private record QueuedResponse(Object response, BiConsumer<Object, OperationContext> callback) {
        QueuedResponse(Object response) {
            this(response, null);
        }
    }

    private final Queue<QueuedResponse> responseQueue = new LinkedList<>();
    private int callCount = 0;

    /**
     * When set, each runWithTemplate call appends request/response as a markdown
     * section to this file. The file is created with a metadata header on first write.
     */
    @Getter @Setter
    private Path logFile;

    /** Test class name written into the markdown header. */
    @Getter @Setter
    private String testClassName;

    /** Test method name written into the markdown header. */
    @Getter @Setter
    private String testMethodName;

    private boolean headerWritten = false;

    /**
     * Collected call records (always populated, regardless of logFile).
     */
    @Getter
    private final List<CallRecord> callRecords = new ArrayList<>();

    public record CallRecord(
            int callIndex,
            String templateName,
            String requestType,
            Object decoratedRequest,
            String responseType,
            Object response
    ) {}

    /**
     * Enqueue a response to be returned by the next runWithTemplate call.
     * Responses are returned in FIFO order.
     */
    public <T> void enqueue(T response) {
        responseQueue.offer(new QueuedResponse(response));
    }

    /**
     * Enqueue a response with a callback that fires when the response is dequeued.
     * The callback receives the response and the OperationContext, and fires BEFORE
     * the response is returned to the caller.
     *
     * <p>Note: The callback fires after {@code decorateRequest} has run on the input
     * (so the request on the blackboard has worktree context), but before
     * {@code decorateRouting} runs on the result. Resolve worktree info from the
     * OperationContext, not from the response.</p>
     */
    @SuppressWarnings("unchecked")
    public <T> void enqueue(T response, BiConsumer<T, OperationContext> onDequeue) {
        responseQueue.offer(new QueuedResponse(response, (BiConsumer<Object, OperationContext>) onDequeue));
    }

    public int getCallCount() {
        return callCount;
    }

    public int getRemainingCount() {
        return responseQueue.size();
    }

    public void assertAllConsumed() {
        if (!responseQueue.isEmpty()) {
            throw new AssertionError("Expected all queued responses to be consumed, but " +
                    responseQueue.size() + " remain");
        }
    }

    public void clear() {
        responseQueue.clear();
        callCount = 0;
        callRecords.clear();
        logFile = null;
        testClassName = null;
        testMethodName = null;
        headerWritten = false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T runWithTemplate(
            String templateName,
            PromptContext promptContext,
            Map<String, Object> model,
            ToolContext toolContext,
            Class<T> responseClass,
            OperationContext context
    ) {
        callCount++;

        if (responseQueue.isEmpty()) {
            throw new IllegalStateException(
                    "No more queued responses available. Called runWithTemplate " + callCount +
                    " times with template: " + templateName);
        }

        QueuedResponse queued = responseQueue.poll();
        Object response = queued.response();

        // Fire callback before returning (if present)
        if (queued.callback() != null) {
            try {
                queued.callback().accept(response, context);
            } catch (Exception e) {
                log.error("QueuedLlmRunner callback failed for template: {}", templateName, e);
                throw new IllegalStateException(
                        "QueuedLlmRunner callback failed for template: " + templateName, e);
            }
        }

        // Verify the response type matches what's expected
        if (!responseClass.isInstance(response)) {
            throw new IllegalStateException(
                    "Expected response of type " + responseClass.getName() +
                    " but got " + response.getClass().getName() +
                    " for template: " + templateName);
        }

        // Record and optionally serialize to markdown log
        Object decoratedRequest = promptContext != null ? promptContext.currentRequest() : null;
        CallRecord record = new CallRecord(
                callCount,
                templateName,
                decoratedRequest != null ? decoratedRequest.getClass().getSimpleName() : "null",
                decoratedRequest,
                response.getClass().getSimpleName(),
                response
        );
        callRecords.add(record);
        appendToLog(record);

        return (T) response;
    }

    private void appendToLog(CallRecord record) {
        if (logFile == null) {
            return;
        }
        try {
            Files.createDirectories(logFile.getParent());

            if (!headerWritten) {
                writeHeader();
                headerWritten = true;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## Call %d: `%s`\n\n".formatted(record.callIndex(), record.templateName()));
            sb.append("**Request type**: `%s`  \n".formatted(record.requestType()));
            sb.append("**Response type**: `%s`  \n\n".formatted(record.responseType()));

            if (record.decoratedRequest() != null) {
                sb.append("### Decorated Request (`%s`)\n\n".formatted(record.requestType()));
                sb.append("```json\n");
                sb.append(safeSerialize(record.decoratedRequest()));
                sb.append("\n```\n\n");
            }

            sb.append("### Response (`%s`)\n\n".formatted(record.responseType()));
            sb.append("```json\n");
            sb.append(safeSerialize(record.response()));
            sb.append("\n```\n\n");

            sb.append("---\n\n");

            Files.writeString(logFile, sb.toString(), StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("Failed to append call record {} to {}", record.callIndex(), logFile, e);
        }
    }

    private void writeHeader() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# QueuedLlmRunner Call Log\n\n");
            sb.append("| Field | Value |\n");
            sb.append("|-------|-------|\n");
            sb.append("| **Test class** | `%s` |\n".formatted(
                    testClassName != null ? testClassName : "unknown"));
            sb.append("| **Test method** | `%s` |\n".formatted(
                    testMethodName != null ? testMethodName : "unknown"));
            sb.append("| **Started at** | %s |\n".formatted(Instant.now()));
            sb.append("\n---\n\n");

            Files.writeString(logFile, sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            log.warn("Failed to write header to {}", logFile, e);
        }
    }

    private String safeSerialize(Object obj) {
        try {
            return LOG_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON serialization failed for {}: {}", obj.getClass().getSimpleName(), e.getMessage());
            return "{ \"_serializationError\": \"" + e.getMessage().replace("\"", "'") + "\", \"_type\": \"" + obj.getClass().getName() + "\" }";
        }
    }
}
