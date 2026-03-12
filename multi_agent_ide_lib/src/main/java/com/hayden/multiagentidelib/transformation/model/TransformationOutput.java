package com.hayden.multiagentidelib.transformation.model;

import lombok.Builder;

import java.util.Map;

@Builder(toBuilder = true)
public record TransformationOutput(
        String transformedText,
        Map<String, String> metadata,
        String errorMessage
) {
}
