package com.hayden.multiagentide.controller;

import com.embabel.agent.core.AgentPlatform;
import com.hayden.multiagentide.adapter.SseEventAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventStreamController {

    private final SseEventAdapter sseEventAdapter;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        return sseEventAdapter.registerEmitter();
    }
}
