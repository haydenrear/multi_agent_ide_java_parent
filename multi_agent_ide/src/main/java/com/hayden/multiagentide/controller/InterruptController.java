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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.hayden.acp_cdc_ai.permission.IPermissionGate;
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
import org.springframework.web.bind.annotation.RequestParam;

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
            IPermissionGate.ResolutionType resolutionType,
            String resolutionNotes,
            IPermissionGate.InterruptResult reviewResult
    ) {
    }

    public record InterruptStatusResponse(
            String interruptId,
            String status,
            String originNodeId,
            String resumeNodeId
    ) {
    }

    public record ToolCallInfo(
            String eventId,
            Instant timestamp,
            String nodeId,
            String toolCallId,
            String title,
            String kind,
            String status,
            String phase,
            Object rawInput,
            Object rawOutput
    ) {
    }

    public record InterruptDetailResponse(
            String interruptId,
            String requestId,
            String originNodeId,
            String nodeId,
            String interruptType,
            String sourceAgentType,
            String rerouteToAgentType,
            String reason,
            String contextForDecision,
            String status,
            List<ToolCallInfo> toolCalls
    ) {
    }

    @GetMapping("/detail")
    public InterruptDetailResponse detail(@RequestParam("id") String id) {
        Events.InterruptRequestEvent interruptEvent = findInterruptRequestEvent(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interrupt request not found"));

        boolean pending = permissionGate.pendingInterruptRequests().stream()
                .anyMatch(p -> Objects.equals(p.getInterruptId(), interruptEvent.requestId())
                        || Objects.equals(p.getInterruptId(), interruptEvent.nodeId()));
        String status = pending ? "PENDING" : "RESOLVED_OR_UNKNOWN";

        List<ToolCallInfo> toolCalls = eventStreamRepository.list().stream()
                .filter(Events.ToolCallEvent.class::isInstance)
                .map(Events.ToolCallEvent.class::cast)
                .filter(tc -> matchesNodeScope(interruptEvent.nodeId(), tc.nodeId())
                        || matchesNodeScope(interruptEvent.requestId(), tc.nodeId()))
                .sorted(Comparator.comparing(Events.ToolCallEvent::timestamp).reversed())
                .limit(40)
                .map(this::toToolCallInfo)
                .toList();

        return new InterruptDetailResponse(
                interruptEvent.requestId(),
                interruptEvent.requestId(),
                interruptEvent.nodeId(),
                interruptEvent.nodeId(),
                interruptEvent.interruptType().name(),
                interruptEvent.sourceAgentType(),
                interruptEvent.rerouteToAgentType(),
                interruptEvent.reason(),
                interruptEvent.contextForDecision(),
                status,
                toolCalls
        );
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

    private Optional<Events.InterruptRequestEvent> findInterruptRequestEvent(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return eventStreamRepository.list().stream()
                .filter(Events.InterruptRequestEvent.class::isInstance)
                .map(Events.InterruptRequestEvent.class::cast)
                .filter(event -> matchesInterruptIdentifier(id, event))
                .sorted(Comparator.comparing(Events.InterruptRequestEvent::timestamp).reversed())
                .findFirst();
    }

    private boolean matchesInterruptIdentifier(String id, Events.InterruptRequestEvent event) {
        if (isArtifactKey(id)) {
            return matchesNodeScope(id, event.nodeId());
        }
        return id.equals(event.requestId())
                || id.equals(event.nodeId());
    }

    private ToolCallInfo toToolCallInfo(Events.ToolCallEvent event) {
        return new ToolCallInfo(
                event.eventId(),
                event.timestamp(),
                event.nodeId(),
                event.toolCallId(),
                event.title(),
                event.kind(),
                event.status(),
                event.phase(),
                event.rawInput(),
                event.rawOutput()
        );
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
