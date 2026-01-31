package com.hayden.multiagentide.support;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.service.LlmRunner;
import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.multiagentidelib.prompt.PromptContext;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * Test implementation of LlmRunner that returns pre-queued responses.
 * 
 * This allows tests to:
 * 1. Queue up expected LLM responses in order
 * 2. Let real agent code execute (no need to mock individual actions)
 * 3. Verify the workflow executes correctly with those responses
 * 
 * Usage:
 * <pre>
 * queuedLlmRunner.enqueue(OrchestratorRouting.builder()...build());
 * queuedLlmRunner.enqueue(DiscoveryOrchestratorRouting.builder()...build());
 * // ... run the workflow
 * queuedLlmRunner.assertAllConsumed(); // verify all responses were used
 * </pre>
 */
public class QueuedLlmRunner implements LlmRunner {
    
    private final Queue<Object> responseQueue = new LinkedList<>();
    private int callCount = 0;
    
    /**
     * Enqueue a response to be returned by the next runWithTemplate call.
     * Responses are returned in FIFO order.
     */
    public <T> void enqueue(T response) {
        responseQueue.offer(response);
    }
    
    /**
     * Get the number of times runWithTemplate was called.
     */
    public int getCallCount() {
        return callCount;
    }
    
    /**
     * Get the number of remaining queued responses.
     */
    public int getRemainingCount() {
        return responseQueue.size();
    }
    
    /**
     * Assert that all queued responses have been consumed.
     */
    public void assertAllConsumed() {
        if (!responseQueue.isEmpty()) {
            throw new AssertionError("Expected all queued responses to be consumed, but " + 
                    responseQueue.size() + " remain");
        }
    }
    
    /**
     * Clear all queued responses and reset counters.
     */
    public void clear() {
        responseQueue.clear();
        callCount = 0;
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
        
        Object response = responseQueue.poll();
        
        // Verify the response type matches what's expected
        if (!responseClass.isInstance(response)) {
            throw new IllegalStateException(
                    "Expected response of type " + responseClass.getName() + 
                    " but got " + response.getClass().getName() + 
                    " for template: " + templateName);
        }
        
        return (T) response;
    }
}
