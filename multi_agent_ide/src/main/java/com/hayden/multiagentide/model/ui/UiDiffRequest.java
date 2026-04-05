package com.hayden.multiagentide.model.ui;

public record UiDiffRequest(
        String baseRevision,
        Object diff,
        String summary
) {
}
