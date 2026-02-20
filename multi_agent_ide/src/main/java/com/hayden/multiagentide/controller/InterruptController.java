package com.hayden.multiagentide.controller;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.service.AgentControlService;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.repository.EventStreamRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import com.hayden.multiagentidelib.agent.AgentModels;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interrupts")
@RequiredArgsConstructor
public class InterruptController {

    private final AgentControlService agentControlService;
    private final EventBus eventBus;
    private final PermissionGate permissionGate;
    private final EventStreamRepository eventStreamRepository;

    @PostMapping
    public InterruptStatusResponse requestInterrupt(@RequestBody InterruptRequest request) {
        String interruptId = UUID.randomUUID().toString();
        String reason = request.reason() != null ? request.reason() : "Interrupt requested";
        Events.InterruptType type = request.type();
        switch (type) {
            case PAUSE -> agentControlService.requestPause(request.originNodeId(), reason);
            case STOP -> agentControlService.requestStop(request.originNodeId());
            case HUMAN_REVIEW -> agentControlService.requestReview(request.originNodeId(), reason);
            case PRUNE -> {
                eventBus.publish(new Events.NodePrunedEvent(
                        interruptId,
                        Instant.now(),
                        request.originNodeId(),
                        reason,
                        List.of()
                ));
            }
            default -> throw new IllegalArgumentException("Unsupported interrupt type: " + type);
        }
        return new InterruptStatusResponse(interruptId, "REQUESTED", request.originNodeId(), request.originNodeId());
    }

    @PostMapping("/resolve")
    public InterruptStatusResponse resolveInterrupt(
            @RequestBody InterruptResolution request
    ) {
        String interruptId = request.id();
        if (interruptId == null || interruptId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
        }
        String message = request.resolutionNotes() != null ? request.resolutionNotes() : "Interrupt resolved";
        boolean resolved = permissionGate.resolveInterrupt(
                interruptId,
                request.resolutionType(),
                message,
                request.reviewResult()
        );
        String resolvedInterruptId = interruptId;

        if (!resolved && isArtifactKey(interruptId)) {
            for (String candidateInterruptId : findInterruptIdsInScope(interruptId)) {
                resolved = permissionGate.resolveInterrupt(
                        candidateInterruptId,
                        request.resolutionType(),
                        message,
                        request.reviewResult()
                );
                if (resolved) {
                    resolvedInterruptId = candidateInterruptId;
                    break;
                }
            }
        }

        if (!resolved) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Interrupt request not found");
        }
        String originNodeId = request.originNodeId() != null ? request.originNodeId() : resolvedInterruptId;
        eventBus.publish(new Events.ResolveInterruptEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                new ArtifactKey(originNodeId).createChild().value(),
                originNodeId,
                message,
                Events.InterruptType.HUMAN_REVIEW
        ));
        return new InterruptStatusResponse(resolvedInterruptId, "RESOLVED", originNodeId, originNodeId);
    }

    @GetMapping("/{interruptId}")
    public InterruptStatusResponse getStatus(@PathVariable String interruptId) {
        return new InterruptStatusResponse(interruptId, "UNKNOWN", null, null);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @SuppressWarnings("unused")
    public record InterruptError(String message) {
    }

    public record InterruptRequest(
            Events.InterruptType type,
            String originNodeId,
            String reason
    ) {
    }

    public record InterruptResolution(
            String id,
            String originNodeId,
            String resolutionType,
            String resolutionNotes,
            AgentModels.ReviewAgentResult reviewResult
    ) {
    }

    public record InterruptStatusResponse(
            String interruptId,
            String status,
            String originNodeId,
            String resumeNodeId
    ) {
    }

    private List<String> findInterruptIdsInScope(String scopeNodeId) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        eventStreamRepository.list().stream()
                .sorted(Comparator.comparing(Events.GraphEvent::timestamp).reversed())
                .forEach(event -> {
                    if (event instanceof Events.InterruptRequestEvent interruptEvent
                            && matchesNodeScope(scopeNodeId, interruptEvent.nodeId())) {
                        Stream.of(interruptEvent.requestId(), interruptEvent.nodeId())
                                .filter(Objects::nonNull)
                                .filter(s -> !s.isBlank())
                                .forEach(candidates::add);
                    }
                    if (event instanceof Events.NodeReviewRequestedEvent reviewRequested
                            && matchesNodeScope(scopeNodeId, reviewRequested.nodeId())
                            && reviewRequested.reviewNodeId() != null
                            && !reviewRequested.reviewNodeId().isBlank()) {
                        candidates.add(reviewRequested.reviewNodeId());
                    }
                });
        return candidates.stream().toList();
    }

    private boolean isArtifactKey(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            new ArtifactKey(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean matchesNodeScope(String scopeNodeId, String eventNodeId) {
        if (scopeNodeId == null || scopeNodeId.isBlank() || eventNodeId == null || eventNodeId.isBlank()) {
            return false;
        }
        if (scopeNodeId.equals(eventNodeId)) {
            return true;
        }
        try {
            ArtifactKey candidate = new ArtifactKey(eventNodeId);
            ArtifactKey scope = new ArtifactKey(scopeNodeId);
            return candidate.isDescendantOf(scope);
        } catch (Exception ignored) {
            return eventNodeId.startsWith(scopeNodeId + "/");
        }
    }
}
