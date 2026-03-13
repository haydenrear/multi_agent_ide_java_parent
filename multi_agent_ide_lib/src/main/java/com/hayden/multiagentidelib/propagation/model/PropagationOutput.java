package com.hayden.multiagentidelib.propagation.model;

import lombok.Builder;

import java.util.Map;

@Builder(toBuilder = true)
public record PropagationOutput(
        String propagatedText,
        String summaryText,
        Map<String, String> metadata,
        String errorMessage
) {
}
