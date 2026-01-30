package com.hayden.multiagentide.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.model.ui.UiDiffResult;
import com.hayden.multiagentidelib.service.UiStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/ui")
@RequiredArgsConstructor
public class UiController {

    private final EventBus eventBus;
    private final UiStateService uiStateService;
    private final ObjectMapper objectMapper;

    @PostMapping("/feedback")
    public UiFeedbackResponse submitFeedback(@RequestBody UiFeedbackRequest request) {
        String nodeId = request.nodeId() != null ? request.nodeId() : "unknown";
        Events.UiStateSnapshot snapshot = request.snapshot();
        if (snapshot == null && request.nodeId() != null) {
            snapshot = uiStateService.getSnapshot(request.nodeId());
        }

        String message = request.message();
        String enriched = message;
        if (snapshot != null) {
            try {
                enriched = message + "\n\nUI snapshot:\n" + objectMapper.writeValueAsString(snapshot);
            }
            catch (JsonProcessingException ignored) {
            }
        }

        String eventId = UUID.randomUUID().toString();
        eventBus.publish(new Events.AddMessageEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                enriched
        ));
        eventBus.publish(new Events.UiFeedbackEvent(
                eventId,
                Instant.now(),
                nodeId,
                nodeId,
                request.eventId(),
                message,
                snapshot
        ));
        return new UiFeedbackResponse("received");
    }

    @PostMapping("/diff/revert")
    public UiDiffResult revertDiff(@RequestBody UiRevertRequest request) {
        String sessionId = request.nodeId() != null ? request.nodeId() : "unknown";
        UiDiffResult result = uiStateService.revert(sessionId);
        Events.UiStateSnapshot snapshot = uiStateService.getSnapshot(sessionId);

        if ("reverted".equalsIgnoreCase(result.status()) && snapshot != null) {
            eventBus.publish(new Events.UiDiffRevertedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    sessionId,
                    sessionId,
                    snapshot.revision(),
                    snapshot.renderTree(),
                    request.eventId()
            ));
        } else if (!"reverted".equalsIgnoreCase(result.status())) {
            eventBus.publish(new Events.UiDiffRejectedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    sessionId,
                    sessionId,
                    result.errorCode(),
                    result.message()
            ));
        }
        return result;
    }

    @PostMapping("/message")
    public UiFeedbackResponse submitMessage(@RequestBody UiMessageRequest request) {
        String nodeId = request.nodeId() != null ? request.nodeId() : "unknown";
        String message = request.message() != null ? request.message() : "";
        if (message.isBlank()) {
            return new UiFeedbackResponse("ignored");
        }
        eventBus.publish(new Events.AddMessageEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                message
        ));
        return new UiFeedbackResponse("received");
    }

    public record UiFeedbackRequest(
            String eventId,
            String nodeId,
            String message,
            Events.UiStateSnapshot snapshot
    ) {
    }

    public record UiFeedbackResponse(String status) {
    }

    public record UiRevertRequest(String eventId, String nodeId) {
    }

    public record UiMessageRequest(String nodeId, String message) {
    }
}
