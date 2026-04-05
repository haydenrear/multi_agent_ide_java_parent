package com.hayden.multiagentide.model.ui;

public record GuiEmissionResult(
        String status,
        String errorCode,
        String message,
        boolean retryable
) {
}
