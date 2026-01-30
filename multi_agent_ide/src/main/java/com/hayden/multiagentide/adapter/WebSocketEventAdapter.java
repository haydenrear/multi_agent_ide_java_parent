package com.hayden.multiagentide.adapter;

import com.hayden.multiagentide.infrastructure.EventAdapter;
import com.hayden.acp_cdc_ai.acp.events.AgUiSerdes;
import com.hayden.acp_cdc_ai.acp.events.Events;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebSocket event adapter that streams events to connected clients in real-time.
 */
@Component
public class WebSocketEventAdapter extends EventAdapter {

    private final List<WebSocketSession> connectedClients = new CopyOnWriteArrayList<>();

    private AgUiSerdes serdes;

    public WebSocketEventAdapter() {
        super("websocket-adapter");
    }

    @Autowired
    public void setSerdes(AgUiSerdes serdes) {
        this.serdes = serdes;
    }

    /**
     * Register a connected WebSocket client.
     */
    public void registerClient(WebSocketSession session) {
        connectedClients.add(session);
    }

    /**
     * Unregister a disconnected WebSocket client.
     */
    public void unregisterClient(WebSocketSession session) {
        connectedClients.remove(session);
    }

    /**
     * Get count of connected clients.
     */
    public int getConnectedClientCount() {
        return connectedClients.size();
    }

    @Override
    protected void adaptEvent(Events.GraphEvent event) {
        String eventJson = serializeEvent(event);

        for (WebSocketSession session : connectedClients) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(eventJson));
                } catch (IOException e) {
                    // If send fails, remove the client
                    unregisterClient(session);
                }
            }
        }
    }

    @Override
    protected void handleAdapterError(Events.GraphEvent event, Exception error) {
        System.err.println("WebSocket adapter error for event " + event.eventType() + ": " + error.getMessage());
    }

    @Override
    public String getAdapterType() {
        return "websocket";
    }

    /**
     * Serialize event to JSON.
     * In production, use Jackson or similar.
     */
    private String serializeEvent(Events.GraphEvent event) {
        return serdes.serializeEvent(Events.mapToEvent(event));
    }

}
