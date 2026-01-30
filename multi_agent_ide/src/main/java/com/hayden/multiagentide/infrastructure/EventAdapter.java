package com.hayden.multiagentide.infrastructure;

import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;

/**
 * Abstract base class for event adapters that transform events for specific targets
 * (e.g., WebSocket, logging, external systems).
 */
public abstract class EventAdapter implements EventListener {

    private final String adapterId;
    private boolean subscribed = false;

    protected EventAdapter(String adapterId) {
        this.adapterId = adapterId;
    }

    protected synchronized void subscribe(EventBus eventBus) {
        if (!subscribed) {
            eventBus.subscribe(this);
            subscribed = true;
        }
    }

    @Override
    public String listenerId() {
        return adapterId;
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        try {
            adaptEvent(event);
        } catch (Exception e) {
            handleAdapterError(event, e);
        }
    }

    /**
     * Transform and handle the event in adapter-specific way.
     * @param event the event to adapt
     */
    protected abstract void adaptEvent(Events.GraphEvent event);

    /**
     * Handle errors during adaptation.
     * @param event the event that caused error
     * @param error the error
     */
    protected abstract void handleAdapterError(Events.GraphEvent event, Exception error);

    /**
     * Get adapter type name.
     * @return the adapter type
     */
    public abstract String getAdapterType();
}
