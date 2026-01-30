package com.hayden.multiagentidelib.model.ui;

public record UiDiffResult(
        String status,
        String revision,
        String errorCode,
        String message
) {
}
