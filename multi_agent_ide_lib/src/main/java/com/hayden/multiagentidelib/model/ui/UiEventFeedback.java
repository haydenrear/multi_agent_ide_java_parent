package com.hayden.multiagentidelib.model.ui;

import com.hayden.utilitymodule.acp.events.Events;

public record UiEventFeedback(
        String eventId,
        String sessionId,
        String message,
        Events.UiStateSnapshot snapshot
) {
}
