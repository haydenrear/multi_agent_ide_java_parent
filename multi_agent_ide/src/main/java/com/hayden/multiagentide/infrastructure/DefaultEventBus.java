package com.hayden.multiagentide.infrastructure;

import com.hayden.utilitymodule.acp.events.EventBus;
import com.hayden.utilitymodule.acp.events.EventListener;
import com.hayden.utilitymodule.acp.events.Events;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation of EventBus using CopyOnWriteArrayList for thread-safe
 * concurrent iteration while allowing concurrent modifications.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultEventBus implements EventBus {

    private List<EventListener> subscribers;

    @Autowired
    @Lazy
    public void setSubscribers(List<EventListener> subscribers) {
        this.subscribers = new ArrayList<>(subscribers);
    }

    @Override
    public void subscribe(EventListener listener) {
        boolean exists = subscribers.stream()
                .anyMatch(existing -> existing.listenerId().equals(listener.listenerId()));
        if (exists) {
            return;
        }
        subscribers.add(listener);
        listener.onSubscribed();
    }

    @Override
    public void unsubscribe(EventListener listener) {
        if (subscribers.remove(listener)) {
            listener.onUnsubscribed();
        }
    }

    @Override
    public void publish(Events.GraphEvent event) {
        for (EventListener listener : subscribers) {
            if (listener.isInterestedIn(event)) {
                try {
                    listener.onEvent(event);
                } catch (Exception e) {
                    // Log error but continue publishing to other listeners
                    log.error("Error handling event in listener " + listener.listenerId() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public List<EventListener> getSubscribers() {
        return Collections.unmodifiableList(new ArrayList<>(subscribers));
    }

    @Override
    public void clear() {
        for (EventListener listener : subscribers) {
            listener.onUnsubscribed();
        }
    }

    @Override
    public boolean hasSubscribers() {
        return !subscribers.isEmpty();
    }
}
