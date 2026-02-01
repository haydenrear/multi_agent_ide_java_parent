package com.hayden.multiagentidelib.model.worktree;

public record SandboxValidationResult(
        boolean allowed,
        String reason,
        String normalizedPath
) {
    public static SandboxValidationResult allowed(String normalizedPath) {
        return new SandboxValidationResult(true, null, normalizedPath);
    }

    public static SandboxValidationResult denied(String reason, String normalizedPath) {
        return new SandboxValidationResult(false, reason, normalizedPath);
    }
}
