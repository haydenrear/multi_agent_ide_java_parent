package com.hayden.multiagentide.repository;

import com.hayden.acp_cdc_ai.acp.events.Events;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryEventStreamRepository implements EventStreamRepository {

    private final Map<String, List<Events.GraphEvent>> events = new ConcurrentHashMap<>();
    private final Map<String, Events.GraphEvent> byId = new ConcurrentHashMap<>();

    @Override
    public void save(Events.GraphEvent graphEvent) {
        events.compute(graphEvent.nodeId(), (key, prev) -> {
            if (prev == null)
                prev = new ArrayList<>();

            prev.add(graphEvent);

            return prev;
        });
        if (graphEvent != null && graphEvent.eventId() != null) {
            byId.put(graphEvent.eventId(), graphEvent);
        }
    }

    @Override
    public List<Events.GraphEvent> list() {
        return events.values()
                .stream()
                .flatMap(Collection::stream)
                .toList();
    }

    @Override
    public java.util.Optional<Events.GraphEvent> findById(String eventId) {
        if (eventId == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(byId.get(eventId));
    }

}
