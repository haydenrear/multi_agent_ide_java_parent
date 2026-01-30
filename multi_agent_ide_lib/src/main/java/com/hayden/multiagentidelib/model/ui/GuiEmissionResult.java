package com.hayden.multiagentidelib.model.ui;

public record GuiEmissionResult(
        String status,
        String errorCode,
        String message,
        boolean retryable
) {
}
