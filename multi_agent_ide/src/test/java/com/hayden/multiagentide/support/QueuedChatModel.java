package com.hayden.multiagentide.support;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Test ChatModel that returns pre-queued responses serialized as JSON.
 *
 * This sits below DefaultLlmRunner in the stack, replacing AcpChatModel.
 * When {@code call(Prompt)} is invoked by the embabel framework, it dequeues
 * the next item: either an object (serialized to JSON and wrapped in a ChatResponse)
 * or a RuntimeException (thrown to trigger framework retry).
 *
 * Usage:
 * <pre>
 * queuedChatModel.enqueue(OrchestratorRouting.builder()...build());
 * queuedChatModel.enqueueError(new CompactionException("compacting", "sess1"));
 * queuedChatModel.enqueue(OrchestratorRouting.builder()...build()); // retry success
 * </pre>
 */
@Slf4j
public class QueuedChatModel implements ChatModel {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private record QueuedItem(Object response, RuntimeException errorToThrow) {
        QueuedItem(Object response) {
            this(response, null);
        }

        static QueuedItem error(RuntimeException exception) {
            return new QueuedItem(null, exception);
        }

        boolean isError() {
            return errorToThrow != null;
        }
    }

    private final Queue<QueuedItem> queue = new LinkedList<>();
    private int callCount = 0;

    public <T> void enqueue(T response) {
        queue.offer(new QueuedItem(response));
    }

    public void enqueueError(RuntimeException exception) {
        queue.offer(QueuedItem.error(exception));
    }

    public int getCallCount() {
        return callCount;
    }

    public int getRemainingCount() {
        return queue.size();
    }

    public void assertAllConsumed() {
        if (!queue.isEmpty()) {
            throw new AssertionError("Expected all queued ChatModel responses consumed, but " +
                    queue.size() + " remain after " + callCount + " calls");
        }
    }

    public void clear() {
        queue.clear();
        callCount = 0;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        callCount++;

        if (queue.isEmpty()) {
            throw new IllegalStateException(
                    "No more queued ChatModel responses. call() invoked " + callCount +
                    " times. Prompt: " + truncate(prompt.toString(), 200));
        }

        QueuedItem item = queue.poll();

        if (item.isError()) {
            log.info("QueuedChatModel: throwing enqueued error (call {}): {}",
                    callCount, item.errorToThrow().getMessage());
            throw item.errorToThrow();
        }

        try {
            String json = MAPPER.writeValueAsString(item.response());
            log.info("QueuedChatModel: returning JSON response (call {}, type={}): {}",
                    callCount, item.response().getClass().getSimpleName(), truncate(json, 300));
            return ChatResponse.builder()
                    .generations(List.of(new Generation(new AssistantMessage(json))))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("QueuedChatModel: failed to serialize response: " + e.getMessage(), e);
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
