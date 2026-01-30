package com.hayden.multiagentidelib.model.nodes;

import com.hayden.utilitymodule.acp.events.Events;

/**
 * Capability mixin for nodes that can be interrupted.
 */
public interface Interruptible {

    InterruptContext interruptibleContext();

    default boolean isInterrupted() {
        InterruptContext context = interruptibleContext();
        return context != null && context.status() != InterruptContext.InterruptStatus.RESOLVED;
    }

    default Events.InterruptType interruptType() {
        InterruptContext context = interruptibleContext();
        return context != null ? context.type() : null;
    }

    default String interruptReason() {
        InterruptContext context = interruptibleContext();
        return context != null ? context.reason() : null;
    }

    default String interruptOriginNodeId() {
        InterruptContext context = interruptibleContext();
        return context != null ? context.originNodeId() : null;
    }

    default String interruptResumeNodeId() {
        InterruptContext context = interruptibleContext();
        return context != null ? context.resumeNodeId() : null;
    }

    default String interruptNodeId() {
        InterruptContext context = interruptibleContext();
        return context != null ? context.interruptNodeId() : null;
    }
}
