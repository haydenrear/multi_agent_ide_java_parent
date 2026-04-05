package com.hayden.multiagentidelib.llm;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.tool.ToolContext;
import lombok.Builder;

import java.util.Map;

/**
 * LLM executor that wraps calls with agent-session-aware infrastructure:
 * event emission (start/complete), error descriptor resolution from blackboard history,
 * and error-specific template overrides.
 * <p>
 * Callers that already build their own PromptContext / ToolContext should use this
 * instead of raw {@link LlmRunner} to get retry observability and error-aware prompt filtering.
 */
public interface AgentLlmExecutor {

    /**
     * Execute an LLM call with agent executor semantics (events, error descriptors, error templates).
     *
     * @param args pre-built contexts and metadata
     * @return the structured response from the LLM
     */
    <T> T runDirect(DirectExecutorArgs<T> args);

    @Builder
    record DirectExecutorArgs<T>(
            Class<T> responseClazz,
            String agentName,
            String actionName,
            String methodName,
            String template,
            PromptContext promptContext,
            Map<String, Object> templateModel,
            ToolContext toolContext,
            OperationContext operationContext
    ) {}
}
