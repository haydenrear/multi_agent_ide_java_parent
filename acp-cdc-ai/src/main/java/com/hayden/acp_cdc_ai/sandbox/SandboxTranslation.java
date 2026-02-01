package com.hayden.acp_cdc_ai.sandbox;

import java.util.List;
import java.util.Map;

public record SandboxTranslation(
        Map<String, String> env,
        List<String> args
) {
    public static SandboxTranslation empty() {
        return new SandboxTranslation(Map.of(), List.of());
    }
}
