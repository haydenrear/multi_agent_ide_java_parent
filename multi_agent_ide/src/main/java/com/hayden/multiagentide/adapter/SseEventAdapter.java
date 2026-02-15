package com.hayden.multiagentide.adapter;

import com.hayden.multiagentide.infrastructure.EventAdapter;
import com.hayden.acp_cdc_ai.acp.events.AgUiSerdes;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.repository.EventStreamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

@Slf4j
@Component
public class SseEventAdapter extends EventAdapter {

    private final List<RegisteredEmitter> emitters = new CopyOnWriteArrayList<>();

    private AgUiSerdes serdes;

    @Autowired
    private EventStreamRepository graphRepository;

    public SseEventAdapter() {
        super("sse-adapter");
    }

    @Autowired
    public void setSerdes(AgUiSerdes serdes) {
        this.serdes = serdes;
    }

    public SseEmitter registerEmitter() {
        return registerEmitter(event -> true);
    }

    public SseEmitter registerEmitter(Predicate<Events.GraphEvent> filter) {
        SseEmitter emitter = new SseEmitter(0L);
        RegisteredEmitter registered = new RegisteredEmitter(emitter, filter == null ? event -> true : filter);
        emitters.add(registered);
        emitter.onCompletion(() -> emitters.remove(registered));
        emitter.onTimeout(() -> emitters.remove(registered));
        emitter.onError((err) -> emitters.remove(registered));

//      catch-up
        for (Events.GraphEvent event : graphRepository.list()) {
            if (!registered.filter().test(event)) {
                continue;
            }
            String payload = serdes.serializeEvent(Events.mapToEvent(event));
            try {
                emitter.send(SseEmitter.event().name("ag-ui").data(payload));
            } catch (IOException e) {
                handleAdapterError(event, e);
                emitters.remove(registered);
            }
        }
        return emitter;
    }

    @Override
    protected void adaptEvent(Events.GraphEvent event) {
        String payload = serdes.serializeEvent(Events.mapToEvent(event));
        for (RegisteredEmitter registered : emitters) {
            if (!registered.filter().test(event)) {
                continue;
            }
            try {
                log.info("Writing next event - {}", event);
                registered.emitter().send(SseEmitter.event().name("ag-ui").data(payload));
            } catch (IOException e) {
                log.error("Failed writing next event - {}", event);
                handleAdapterError(event, e);
                emitters.remove(registered);
            }
        }
    }

    @Override
    protected void handleAdapterError(Events.GraphEvent event, Exception error) {
        log.error("SSE adapter error for event {}: {}", event.eventType(), error.getMessage());
    }

    @Override
    public String getAdapterType() {
        return "sse";
    }

    private record RegisteredEmitter(SseEmitter emitter, Predicate<Events.GraphEvent> filter) {
    }
}
