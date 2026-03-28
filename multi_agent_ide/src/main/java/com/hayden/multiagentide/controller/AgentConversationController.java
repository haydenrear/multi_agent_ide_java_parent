package com.hayden.multiagentide.controller;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.model.nodes.AgentToControllerConversationNode;
import com.hayden.multiagentidelib.model.nodes.GraphNode;
import com.hayden.multiagentidelib.model.nodes.HasChatSessionKey;
import com.hayden.multiagentidelib.prompt.contributor.NodeMappings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent-conversations")
@RequiredArgsConstructor
@Tag(name = "Agent Conversations", description = "Controller-to-agent conversation management")
@Slf4j
public class AgentConversationController {

    private final PermissionGate permissionGate;
    private final GraphRepository graphRepository;
    private final EventBus eventBus;

    // ── Request/Response DTOs ────────────────────────────────────────────────

    public record RespondRequest(
            String targetAgentKey,
            String message,
            String checklistAction,
            String interruptId
    ) {}

    public record RespondResponse(
            String status,
            String interruptId,
            String message
    ) {}

    public record ListRequest(
            String nodeId
    ) {}

    public record ConversationSummary(
            String targetKey,
            String agentType,
            String interruptId,
            String reason,
            boolean pending
    ) {}

    // ── POST /api/agent-conversations/respond ────────────────────────────────

    @PostMapping("/respond")
    @Operation(summary = "Respond to an agent's justification request",
            description = "Controller sends a response to an agent that called call_controller. "
                    + "Resolves the pending interrupt, delivering the message back to the blocked agent.")
    public ResponseEntity<RespondResponse> respond(@RequestBody RespondRequest request) {
        if (request.interruptId() == null || request.interruptId().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new RespondResponse("error", null, "interruptId is required"));
        }
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new RespondResponse("error", request.interruptId(), "message is required"));
        }

        // Find the pending interrupt
        IPermissionGate.PendingInterruptRequest pending = permissionGate.getInterruptPending(
                p -> p.getInterruptId().equals(request.interruptId()));

        if (pending == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new RespondResponse("error", request.interruptId(), "No pending interrupt found with this ID"));
        }

        // Resolve the interrupt with the controller's message
        boolean resolved = permissionGate.resolveInterrupt(
                request.interruptId(),
                IPermissionGate.ResolutionType.RESOLVED,
                request.message(),
                (IPermissionGate.InterruptResult) null
        );

        if (!resolved) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new RespondResponse("error", request.interruptId(), "Failed to resolve interrupt"));
        }

        // Emit event for observability
        eventBus.publish(new Events.AgentCallEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                pending.getOriginNodeId(),
                Events.AgentCallEventType.RETURNED,
                "controller",
                null,
                pending.getOriginNodeId(),
                null,
                List.of(),
                null,
                null,
                request.message(),
                null,
                request.checklistAction()
        ));

        return ResponseEntity.ok(new RespondResponse("resolved", request.interruptId(), "Response delivered to agent"));
    }

    // ── POST /api/agent-conversations/list ───────────────────────────────────

    @PostMapping("/list")
    @Operation(summary = "List agent conversations under a node",
            description = "Returns conversation summaries for pending and recent agent-to-controller conversations "
                    + "within the scope of the given nodeId.")
    public ResponseEntity<List<ConversationSummary>> list(@RequestBody ListRequest request) {
        if (request.nodeId() == null || request.nodeId().isBlank()) {
            return ResponseEntity.badRequest().body(List.of());
        }

        List<ConversationSummary> summaries = new ArrayList<>();

        // Find pending interrupts that originate from descendants of the given node
        for (IPermissionGate.PendingInterruptRequest pending : permissionGate.pendingInterruptRequests()) {
            String originNodeId = pending.getOriginNodeId();
            if (isDescendantOrSelf(request.nodeId(), originNodeId)) {
                GraphNode node = graphRepository.findById(originNodeId).orElse(null);
                AgentType agentType = node != null ? NodeMappings.agentTypeFromNode(node) : null;
                summaries.add(new ConversationSummary(
                        originNodeId,
                        agentType != null ? agentType.wireValue() : null,
                        pending.getInterruptId(),
                        pending.getReason(),
                        true
                ));
            }
        }

        return ResponseEntity.ok(summaries);
    }

    private boolean isDescendantOrSelf(String scopeNodeId, String candidateNodeId) {
        if (scopeNodeId == null || candidateNodeId == null) return false;
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
