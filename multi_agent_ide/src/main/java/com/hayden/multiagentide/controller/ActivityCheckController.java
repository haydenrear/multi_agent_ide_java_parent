package com.hayden.multiagentide.controller;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.multiagentide.gate.PermissionGate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ui")
@RequiredArgsConstructor
@Tag(name = "UI Activity", description = "Lightweight activity polling for controller UI")
public class ActivityCheckController {

    private final PermissionGate permissionGate;

    public record ActivityCheckRequest(String nodeId) {}

    public record ActivityCheckResponse(
            int pendingPermissions,
            int pendingInterrupts,
            int pendingConversations,
            boolean hasActivity
    ) {}

    @PostMapping("/activity-check")
    @Operation(summary = "Check for pending activity under a node scope",
            description = "Fast check — no graph traversal or propagation queries. Returns counts of pending "
                    + "permissions, interrupts, and conversations within the scope of the given nodeId.")
    public ResponseEntity<ActivityCheckResponse> activityCheck(@RequestBody ActivityCheckRequest request) {
        String nodeId = request.nodeId();

        int permissions = 0;
        int interrupts = 0;
        int conversations = 0;

        for (IPermissionGate.PendingPermissionRequest p : permissionGate.pendingPermissionRequests()) {
            if (matchesScope(nodeId, p.getOriginNodeId())) {
                permissions++;
            }
        }

        for (IPermissionGate.PendingInterruptRequest p : permissionGate.pendingInterruptRequests()) {
            if (matchesScope(nodeId, p.getOriginNodeId())) {
                if (p.getType() != null && p.getType().name().equals("HUMAN_REVIEW")) {
                    conversations++;
                } else {
                    interrupts++;
                }
            }
        }

        boolean hasActivity = permissions > 0 || interrupts > 0 || conversations > 0;
        return ResponseEntity.ok(new ActivityCheckResponse(permissions, interrupts, conversations, hasActivity));
    }

    private boolean matchesScope(String scopeNodeId, String candidateNodeId) {
        if (scopeNodeId == null || scopeNodeId.isBlank()) return true;
        if (candidateNodeId == null) return false;
        if (scopeNodeId.equals(candidateNodeId)) return true;
        try {
            ArtifactKey candidate = new ArtifactKey(candidateNodeId);
            ArtifactKey scope = new ArtifactKey(scopeNodeId);
            return candidate.isDescendantOf(scope);
        } catch (Exception e) {
            return candidateNodeId.startsWith(scopeNodeId + "/");
        }
    }
}
