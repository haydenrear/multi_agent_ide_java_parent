package com.hayden.multiagentide.adapter;

import com.hayden.multiagentide.infrastructure.EventAdapter;
import com.hayden.utilitymodule.acp.events.AgUiSerdes;
import com.hayden.utilitymodule.acp.events.Events;
import com.hayden.multiagentide.repository.EventStreamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class SseEventAdapter extends EventAdapter {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

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
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((err) -> emitters.remove(emitter));

//      catch-up
        for (Events.GraphEvent event : graphRepository.list()) {
            String payload = serdes.serializeEvent(Events.mapToEvent(event));
            try {
                emitter.send(SseEmitter.event().name("ag-ui").data(payload));
            } catch (IOException e) {
                handleAdapterError(event, e);
                emitters.remove(emitter);
            }
        }
        return emitter;
    }

    @Override
    protected void adaptEvent(Events.GraphEvent event) {
        String payload = serdes.serializeEvent(Events.mapToEvent(event));
        for (SseEmitter emitter : emitters) {
            try {
                log.info("Writing next event - {}", event);
                emitter.send(SseEmitter.event().name("ag-ui").data(payload));
                graphRepository.save(event);
            } catch (IOException e) {
                log.error("Failed writing next event - {}", event);
                handleAdapterError(event, e);
                emitters.remove(emitter);
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
}
