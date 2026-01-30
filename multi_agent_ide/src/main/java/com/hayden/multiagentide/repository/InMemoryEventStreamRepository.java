package com.hayden.multiagentide.repository;

import com.hayden.utilitymodule.acp.events.Events;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryEventStreamRepository implements EventStreamRepository {

    private final Map<String, List<Events.GraphEvent>> events = new ConcurrentHashMap<>();

    @Override
    public void save(Events.GraphEvent graphEvent) {
        events.compute(graphEvent.nodeId(), (key, prev) -> {
            if (prev == null)
                prev = new ArrayList<>();

            prev.add(graphEvent);

            return prev;
        });
    }

    @Override
    public List<Events.GraphEvent> list() {
        return events.values()
                .stream()
                .flatMap(Collection::stream)
                .toList();
    }

}
