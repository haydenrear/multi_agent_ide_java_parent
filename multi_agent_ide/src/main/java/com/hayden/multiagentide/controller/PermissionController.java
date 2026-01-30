package com.hayden.multiagentide.controller;

import com.hayden.multiagentide.gate.PermissionGate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionGate permissionGate;

    public record PermissionResolutionRequest(
            String outcome,
            String optionId
    ) {
    }

    public record PermissionResolutionResponse(
            String requestId,
            String status
    ) {
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @SuppressWarnings("unused")
    public record PermissionError(String message) {
    }

    @PostMapping("/{requestId}/resolve")
    public PermissionResolutionResponse resolve(
            @PathVariable String requestId,
            @RequestBody PermissionResolutionRequest request
    ) {
        boolean resolved;
        if (request == null) {
            resolved = permissionGate.resolveCancelled(requestId);
        } else if (isSelected(request)) {
            resolved = permissionGate.resolveSelected(requestId, request.optionId());
        } else {
            resolved = permissionGate.resolveCancelled(requestId);
        }

        if (!resolved) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission request not found");
        }

        return new PermissionResolutionResponse(requestId, "RESOLVED");
    }

    private boolean isSelected(PermissionResolutionRequest request) {
        if (request.optionId() != null && !request.optionId().isBlank()) {
            return true;
        }
        String outcome = request.outcome();
        return outcome != null && (outcome.equalsIgnoreCase("SELECTED")
                || outcome.equalsIgnoreCase("GRANTED")
                || outcome.equalsIgnoreCase("ALLOW")
                || outcome.equalsIgnoreCase("APPROVED"));
    }

}
