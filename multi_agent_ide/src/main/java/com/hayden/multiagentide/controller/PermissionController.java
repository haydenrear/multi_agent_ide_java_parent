package com.hayden.multiagentide.controller;

import com.agentclientprotocol.model.PermissionOptionKind;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.repository.EventStreamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionGate permissionGate;
    private final EventStreamRepository eventStreamRepository;

    public record PermissionResolutionRequest(
            String id,
            PermissionOptionKind optionType
    ) {
    }

    public record PermissionResolutionResponse(
            String requestId,
            String status
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
            List<Map<String, Object>> content,
            List<Map<String, Object>> locations,
            Object rawInput,
            Object rawOutput
    ) {
    }

    public record PermissionDetailResponse(
            String requestId,
            String originNodeId,
            String nodeId,
            String toolCallId,
            String status,
            Object permissions,
            List<ToolCallInfo> toolCalls
    ) {
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @SuppressWarnings("unused")
    public record PermissionError(String message) {
    }

    @PostMapping("/resolve")
    public PermissionResolutionResponse resolve(
            @RequestBody PermissionResolutionRequest request
    ) {
        var id = Optional.ofNullable(request)
                .flatMap(p -> Optional.ofNullable(p.id))
                .orElse(null);

        if (request == null || id == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission request not found");

        String resolvedRequestId = id;
        boolean resolved = performResolution(id, request.optionType);
        if (!resolved && isArtifactKey(id)) {
            resolvedRequestId = resolveFromScope(id, request.optionType);
            resolved = resolvedRequestId != null;
        }

        if (!resolved) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission request not found");
        }

        return new PermissionResolutionResponse(resolvedRequestId, "RESOLVED");
    }

    @GetMapping("/detail")
    public PermissionDetailResponse detail(@RequestParam("id") String id) {
        var permissionEvent = findPermissionRequestEvent(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission request not found"));

        boolean pending = permissionGate.pendingPermissionRequests().stream()
                .anyMatch(p -> Objects.equals(p.getRequestId(), permissionEvent.requestId()));
        String status = pending ? "PENDING" : "RESOLVED_OR_UNKNOWN";

        List<ToolCallInfo> toolCalls = eventStreamRepository.list().stream()
                .filter(Events.ToolCallEvent.class::isInstance)
                .map(Events.ToolCallEvent.class::cast)
                .filter(tc -> Objects.equals(tc.toolCallId(), permissionEvent.toolCallId()))
                .sorted(Comparator.comparing(Events.ToolCallEvent::timestamp))
                .map(this::toToolCallInfo)
                .toList();

        return new PermissionDetailResponse(
                permissionEvent.requestId(),
                permissionEvent.originNodeId(),
                permissionEvent.nodeId(),
                permissionEvent.toolCallId(),
                status,
                permissionEvent.permissions(),
                toolCalls
        );
    }

    private Optional<Events.PermissionRequestedEvent> findPermissionRequestEvent(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return eventStreamRepository.list().stream()
                .filter(Events.PermissionRequestedEvent.class::isInstance)
                .map(Events.PermissionRequestedEvent.class::cast)
                .filter(event -> matchesPermissionIdentifier(id, event))
                .sorted(Comparator.comparing(Events.PermissionRequestedEvent::timestamp).reversed())
                .findFirst();
    }

    private boolean matchesPermissionIdentifier(String id, Events.PermissionRequestedEvent event) {
        if (isArtifactKey(id)) {
            return matchesNodeScope(id, event.nodeId())
                    || matchesNodeScope(id, event.originNodeId());
        }
        return id.equals(event.requestId())
                || id.equals(event.toolCallId())
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
                event.content(),
                event.locations(),
                event.rawInput(),
                event.rawOutput()
        );
    }

    private String resolveFromScope(String scopeNodeId, PermissionOptionKind optionType) {
        List<Events.PermissionRequestedEvent> candidates = eventStreamRepository.list().stream()
                .filter(Events.PermissionRequestedEvent.class::isInstance)
                .map(Events.PermissionRequestedEvent.class::cast)
                .filter(event -> matchesNodeScope(scopeNodeId, event.nodeId()))
                .sorted(Comparator.comparing(Events.PermissionRequestedEvent::timestamp).reversed())
                .toList();

        for (Events.PermissionRequestedEvent candidate : candidates) {
            if (performResolution(candidate.requestId(), optionType)) {
                return candidate.requestId();
            }
        }
        return null;
    }

    private boolean performResolution(String requestId, PermissionOptionKind optionType) {
        if (optionType == null) {
            return permissionGate.resolveCancelled(requestId);
        }
        var o = switch(optionType) {
            case ALLOW_ONCE ->
                    IPermissionGate.Companion.allowOnce();
            case ALLOW_ALWAYS ->
                    IPermissionGate.Companion.allowAlways();
            case REJECT_ONCE ->
                    IPermissionGate.Companion.rejectOnce();
            case REJECT_ALWAYS ->
                    IPermissionGate.Companion.rejectAlways();
        };
        return permissionGate.resolveSelected(requestId, o);
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
