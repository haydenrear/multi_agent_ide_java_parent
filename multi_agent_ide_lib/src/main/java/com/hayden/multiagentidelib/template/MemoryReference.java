package com.hayden.multiagentidelib.template;

import java.util.Map;

public record MemoryReference(
        String referenceId,
        String memoryType,
        String summary,
        Map<String, String> metadata
) {
}
