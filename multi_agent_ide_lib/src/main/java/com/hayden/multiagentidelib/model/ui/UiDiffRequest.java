package com.hayden.multiagentidelib.model.ui;

public record UiDiffRequest(
        String baseRevision,
        Object diff,
        String summary
) {
}
