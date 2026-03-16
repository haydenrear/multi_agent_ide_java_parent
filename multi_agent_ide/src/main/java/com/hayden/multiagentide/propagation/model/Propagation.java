package com.hayden.multiagentide.propagation.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Combined propagation payload stored in {@code propagatedText}.
 * <p>
 * Both the original serialized request/result ({@code propagationRequest}) and the
 * LLM assessment ({@code llmOutput}) are captured here so the controller has the
 * full picture without needing a separate /records lookup.
 * <p>
 * {@code llmOutput} is set to a failure message string when the LLM call did not
 * succeed, ensuring an item is always created regardless of AI availability.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Propagation(
        String llmOutput,
        String propagationRequest
) {
}
