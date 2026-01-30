package com.hayden.multiagentidelib.model.ui;

import com.hayden.acp_cdc_ai.acp.events.Events;

public record UiEventFeedback(
        String eventId,
        String sessionId,
        String message,
        Events.UiStateSnapshot snapshot
) {
}
