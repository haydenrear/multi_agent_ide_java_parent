package com.hayden.multiagentide.repository;

import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Slf4j
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

    @Override
    public <T extends Events.GraphEvent> Optional<T> getLastMatching(Class<T> v, Predicate<T> toMatch) {
        return events.values().stream()
                .flatMap(Collection::stream)
                .filter(gn -> gn.getClass().equals(v) || v.isAssignableFrom(gn.getClass()))
                .map(ge -> (T) ge)
                .filter(toMatch)
                .max(Comparator.comparing(Events.GraphEvent::timestamp))
                .map(t -> {
                    log.info("Found last matching {}:{}, {}", t.getClass(), t.nodeId(), t);
                    return t;
                });
    }
}
