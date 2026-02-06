package com.hayden.multiagentide.controller;

import com.agentclientprotocol.model.PermissionOptionKind;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
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

import java.util.Optional;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionGate permissionGate;

    public record PermissionResolutionRequest(
            String requestId,
            PermissionOptionKind optionType
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

    @PostMapping("/resolve")
    public PermissionResolutionResponse resolve(
            @RequestBody PermissionResolutionRequest request
    ) {
        boolean resolved;
        var requestId = Optional.ofNullable(request)
                .flatMap(p -> Optional.ofNullable(p.requestId))
                .orElse(null);

        if (request == null || requestId == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission request not found");

        if (request.optionType != null) {
            resolved = performResolution(requestId, request);
        } else {
            resolved = permissionGate.resolveCancelled(requestId);
        }

        if (!resolved) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Permission request not found");
        }

        return new PermissionResolutionResponse(requestId, "RESOLVED");
    }

    private boolean performResolution(String requestId, PermissionResolutionRequest request) {
        boolean resolved;
        var o = switch(request.optionType) {
            case ALLOW_ONCE ->
                    IPermissionGate.Companion.allowOnce();
            case ALLOW_ALWAYS ->
                    IPermissionGate.Companion.allowAlways();
            case REJECT_ONCE ->
                    IPermissionGate.Companion.rejectOnce();
            case REJECT_ALWAYS ->
                    IPermissionGate.Companion.rejectAlways();
        };
        resolved = permissionGate.resolveSelected(requestId, o);
        return resolved;
    }

}
