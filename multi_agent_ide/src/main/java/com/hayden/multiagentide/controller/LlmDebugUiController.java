package com.hayden.multiagentide.controller;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.adapter.SseEventAdapter;
import com.hayden.multiagentide.cli.CliEventFormatter;
import com.hayden.multiagentide.repository.EventStreamRepository;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentidelib.model.nodes.*;
import com.hayden.multiagentide.ui.shared.SharedUiInteractionService;
import com.hayden.multiagentide.ui.shared.UiActionCommand;
import com.hayden.multiagentide.ui.shared.UiStateSnapshot;
import com.hayden.multiagentide.ui.shared.UiStateStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/llm-debug/ui")
@RequiredArgsConstructor
public class LlmDebugUiController {

    private final EventBus eventBus;
    private final SharedUiInteractionService sharedUiInteractionService;
    private final UiStateStore uiStateStore;
    private final SseEventAdapter sseEventAdapter;
    private final EventStreamRepository eventStreamRepository;
    private final GraphRepository graphRepository;
    private final CliEventFormatter cliEventFormatter;
    private final OrchestrationController orchestrationController;

    @GetMapping("/nodes/{nodeId}/state")
    public UiStateSnapshot state(@PathVariable String nodeId) {
        return uiStateStore.snapshot(nodeId);
    }

    @PostMapping("/goals/start")
    public StartGoalResponse startGoal(@RequestBody StartGoalRequest request) {
        OrchestrationController.StartGoalResponse started = orchestrationController.startGoalAsync(
                new OrchestrationController.StartGoalRequest(
                        request.goal(),
                        request.repositoryUrl(),
                        request.baseBranch(),
                        request.title()
                )
        );
        return new StartGoalResponse(started.nodeId(), started.nodeId(), "started");
    }

    @PostMapping("/nodes/{nodeId}/actions")
    public ActionResponse action(@PathVariable String nodeId, @RequestBody UiActionRequest request) {
        if (request == null || request.actionType() == null || request.actionType().isBlank()) {
            return new ActionResponse(null, "rejected");
        }
        UiActionCommand.ActionType actionType;
        try {
            actionType = UiActionCommand.ActionType.valueOf(request.actionType().trim().toUpperCase());
        } catch (Exception e) {
            return new ActionResponse(null, "rejected");
        }

        UiActionCommand command = new UiActionCommand(
                null,
                nodeId,
                actionType,
                request.payload() == null ? Map.of() : request.payload(),
                Instant.now()
        );

        eventBus.publish(new Events.TuiInteractionGraphEvent(
                command.actionId(),
                command.requestedAt(),
                nodeId,
                nodeId,
                sharedUiInteractionService.toInteractionEvent(command)
        ));

        return new ActionResponse(command.actionId(), "queued");
    }

    @PostMapping("/quick-actions")
    public QuickActionResponse quickAction(@RequestBody QuickActionRequest request) {
        String actionType = request == null || request.actionType() == null
                ? ""
                : request.actionType().trim().toUpperCase();
        return switch (actionType) {
            case "START_GOAL" -> {
                if (request.goal() == null || request.goal().isBlank()) {
                    yield new QuickActionResponse(null, "rejected", null, "goal is required");
                }
                if (request.repositoryUrl() == null || request.repositoryUrl().isBlank()) {
                    yield new QuickActionResponse(null, "rejected", null, "repositoryUrl is required");
                }
                OrchestrationController.StartGoalResponse started = orchestrationController.startGoalAsync(
                        new OrchestrationController.StartGoalRequest(
                                request.goal(),
                                request.repositoryUrl(),
                                request.baseBranch(),
                                request.title()
                        )
                );
                yield new QuickActionResponse(started.nodeId(), "started", started.nodeId(), null);
            }
            case "SEND_MESSAGE" -> {
                if (request.nodeId() == null || request.nodeId().isBlank()) {
                    yield new QuickActionResponse(null, "rejected", null, "nodeId is required");
                }
                String actionId = UUID.randomUUID().toString();
                eventBus.publish(new Events.AddMessageEvent(
                        actionId,
                        Instant.now(),
                        request.nodeId(),
                        request.message() == null ? "" : request.message()
                ));
                yield new QuickActionResponse(actionId, "queued", request.nodeId(), null);
            }
            // TODO: Add more quick actions as agent debugging workflow is expanded.
            default -> new QuickActionResponse(null, "rejected", null, "Unsupported actionType");
        };
    }

    @GetMapping("/nodes/{nodeId}/events")
    public UiEventPage events(
            @PathVariable String nodeId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "180") int truncate,
            @RequestParam(defaultValue = "asc") String sort
    ) {
        int safeLimit = Math.max(1, Math.min(500, limit));
        int safeTruncate = Math.max(80, Math.min(10_000, truncate));

        List<Events.GraphEvent> scopedEvents = eventStreamRepository.list().stream()
                .filter(Objects::nonNull)
                .filter(event -> matchesNodeScope(nodeId, event.nodeId()))
                .sorted(Comparator.comparing(Events.GraphEvent::timestamp)
                        .thenComparing(Events.GraphEvent::eventId, Comparator.nullsLast(String::compareTo)))
                .toList();

        int offset;
        if ("desc".equalsIgnoreCase(sort) && cursor == null) {
            offset = Math.max(0, scopedEvents.size() - safeLimit);
        } else {
            offset = parseCursor(cursor);
        }

        int toIndex = Math.min(scopedEvents.size(), offset + safeLimit);
        List<Events.GraphEvent> pageEvents = offset >= scopedEvents.size() ? List.of() : scopedEvents.subList(offset, toIndex);
        List<UiEventSummary> items = pageEvents.stream()
                .map(event -> new UiEventSummary(
                        event.eventId(),
                        event.nodeId(),
                        event.eventType(),
                        event.timestamp(),
                        summarize(event, safeTruncate)
                ))
                .toList();

        String nextCursor = toIndex < scopedEvents.size() ? Integer.toString(toIndex) : null;
        return new UiEventPage(
                items,
                new PageMeta(safeLimit, cursor, nextCursor, nextCursor != null),
                scopedEvents.size()
        );
    }

    @GetMapping("/nodes/{nodeId}/events/{eventId}")
    public UiEventDetail eventDetail(
            @PathVariable String nodeId,
            @PathVariable String eventId,
            @RequestParam(defaultValue = "true") boolean pretty,
            @RequestParam(defaultValue = "20000") int maxFieldLength
    ) {
        Events.GraphEvent event = eventStreamRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Event not found: " + eventId));
        if (!matchesNodeScope(nodeId, event.nodeId())) {
            throw new ResponseStatusException(NOT_FOUND, "Event not in node scope: " + eventId);
        }
        int safeMaxFieldLength = Math.max(120, Math.min(80_000, maxFieldLength));
        String formatted = cliEventFormatter.format(new CliEventFormatter.CliEventArgs(safeMaxFieldLength, event, pretty));
        return new UiEventDetail(
                event.eventId(),
                event.nodeId(),
                event.eventType(),
                event.timestamp(),
                summarize(event, 220),
                formatted
        );
    }

    @GetMapping(value = "/nodes/{nodeId}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String nodeId) {
        return sseEventAdapter.registerEmitter(event -> matchesNodeScope(nodeId, event.nodeId()));
    }

    @GetMapping("/workflow-graph")
    public WorkflowGraphResponse workflowGraph(
            @RequestParam String nodeId,
            @RequestParam(defaultValue = "180") int errorWindowSeconds
    ) {
        int safeErrorWindowSeconds = Math.max(30, Math.min(3600, errorWindowSeconds));
        String rootNodeId = toRootNodeId(nodeId);
        Instant now = Instant.now();
        Instant errorWindowStart = now.minusSeconds(safeErrorWindowSeconds);

        // Tree structure from GraphRepository
        Map<String, GraphNode> allNodes = graphRepository.findSubtree(rootNodeId);

        // Global event stats
        EventStreamRepository.ScopedEventStats globalStats =
                eventStreamRepository.computeScopedStats(rootNodeId, errorWindowStart);

        // Per-node event metrics (keyed by graph node ID)
        Map<String, EventStreamRepository.NodeEventMetrics> metricsMap = new LinkedHashMap<>();
        for (String nid : allNodes.keySet()) {
            eventStreamRepository.computeMetrics(nid).ifPresent(m -> metricsMap.put(nid, m));
        }

        // Compute per-action error counts
        Map<String, Integer> errorsByAction = new LinkedHashMap<>();
        for (GraphNode gn : allNodes.values()) {
            String action = actionName(gn);
            EventStreamRepository.NodeEventMetrics m = metricsMap.get(gn.nodeId());
            if (action != null && m != null && m.nodeErrorCount() > 0) {
                errorsByAction.merge(action, m.nodeErrorCount(), Integer::sum);
            }
        }

        // Build tree recursively from root
        WorkflowNode root = buildWorkflowNode(rootNodeId, allNodes, metricsMap);

        WorkflowGraphStats stats = new WorkflowGraphStats(
                globalStats.totalEvents(),
                allNodes.size(),
                globalStats.eventTypeCounts(),
                safeErrorWindowSeconds,
                globalStats.recentErrorCount(),
                globalStats.recentErrorsByNodeType(),
                errorsByAction,
                globalStats.chatSessionEvents(),
                globalStats.chatMessageEvents(),
                globalStats.totalThoughtTokens(),
                globalStats.totalStreamTokens()
        );

        return new WorkflowGraphResponse(nodeId, rootNodeId, now, stats, root);
    }

    private WorkflowNode buildWorkflowNode(
            String nid,
            Map<String, GraphNode> allNodes,
            Map<String, EventStreamRepository.NodeEventMetrics> metricsMap
    ) {
        GraphNode gn = allNodes.get(nid);
        if (gn == null) return null;

        EventStreamRepository.NodeEventMetrics m = metricsMap.getOrDefault(nid,
                new EventStreamRepository.NodeEventMetrics(0, 0, 0, 0, 0, 0L, 0, 0L, 0, 0));

        // Collect pending items and filter children
        List<PendingItem> pendingItems = new ArrayList<>();
        List<WorkflowNode> children = new ArrayList<>();

        for (String childId : gn.childNodeIds()) {
            GraphNode child = allNodes.get(childId);
            if (child == null) continue;

            if (child instanceof AskPermissionNode perm) {
                if (perm.status() == Events.NodeStatus.COMPLETED) continue; // exclude resolved
                pendingItems.add(new PendingItem(
                        perm.toolCallId(), perm.nodeId(), "PERMISSION",
                        "Permission requested for tool call " + perm.toolCallId()
                                + ". Resolve with: resolve-permission --id " + perm.toolCallId() + " --option-type ALLOW_ONCE"
                ));
                children.add(buildWorkflowNode(childId, allNodes, metricsMap));
            } else if (child instanceof InterruptNode interrupt) {
                InterruptContext ctx = interrupt.interruptContext();
                if (ctx.status() == InterruptContext.InterruptStatus.RESOLVED
                        || interrupt.status() == Events.NodeStatus.COMPLETED) continue; // exclude resolved
                pendingItems.add(new PendingItem(
                        ctx.interruptNodeId() != null ? ctx.interruptNodeId() : interrupt.nodeId(),
                        interrupt.nodeId(), "INTERRUPT",
                        "Interrupt requested: " + ctx.reason()
                                + ". Resolve with: resolve-interrupt --id " + interrupt.nodeId()
                                + " --origin-node-id " + ctx.originNodeId()
                ));
                children.add(buildWorkflowNode(childId, allNodes, metricsMap));
            } else if (child instanceof ReviewNode review) {
                // Reviews always included in children
                InterruptContext ctx = review.interruptContext();
                if (ctx != null && ctx.status() != InterruptContext.InterruptStatus.RESOLVED
                        && review.status() != Events.NodeStatus.COMPLETED) {
                    pendingItems.add(new PendingItem(
                            review.nodeId(), review.nodeId(), "REVIEW",
                            "Review requested (" + review.reviewerAgentType()
                                    + "). Review content at node " + review.nodeId()
                    ));
                }
                children.add(buildWorkflowNode(childId, allNodes, metricsMap));
            } else {
                // Structural nodes (orchestrators, dispatchers, collectors, agents, merge, summary) — always included
                children.add(buildWorkflowNode(childId, allNodes, metricsMap));
            }
        }

        // Sort children by createdAt
        children.sort(Comparator.comparing(
                (WorkflowNode wn) -> wn != null && wn.lastEventAt() != null ? wn.lastEventAt() : Instant.MAX
        ).thenComparing(wn -> wn != null ? wn.nodeId() : ""));

        // Route-back count from WorkflowContext
        int routeBackCount = (gn instanceof HasWorkflowContext<?> hwc && hwc.workflowContext() != null)
                ? hwc.workflowContext().runCount()
                : 0;

        WorkflowNodeMetrics metrics = new WorkflowNodeMetrics(
                m.nodeErrorCount(), m.chatSessionEvents(), m.chatMessageEvents(),
                m.thoughtDeltas(), m.thoughtTokens(), m.streamDeltas(), m.streamTokens(),
                m.toolEvents(), m.otherEvents(), List.copyOf(pendingItems)
        );

        return new WorkflowNode(
                gn.nodeId(),
                gn.parentNodeId(),
                gn.title(),
                gn.nodeType() != null ? gn.nodeType().name() : null,
                gn.status() != null ? gn.status().name() : null,
                actionName(gn),
                gn.goal(),
                routeBackCount,
                gn.lastUpdatedAt(),
                m.totalEvents(),
                metrics,
                children.stream().filter(Objects::nonNull).toList()
        );
    }

    private static String actionName(GraphNode gn) {
        return switch (gn) {
            case OrchestratorNode ignored -> "orchestrator";
            case DiscoveryOrchestratorNode ignored -> "discovery-orchestrator";
            case DiscoveryDispatchAgentNode ignored -> "discovery-dispatch";
            case DiscoveryNode ignored -> "discovery-agent";
            case DiscoveryCollectorNode ignored -> "discovery-collector";
            case PlanningOrchestratorNode ignored -> "planning-orchestrator";
            case PlanningDispatchAgentNode ignored -> "planning-dispatch";
            case PlanningNode ignored -> "planning-agent";
            case PlanningCollectorNode ignored -> "planning-collector";
            case TicketOrchestratorNode ignored -> "ticket-orchestrator";
            case TicketDispatchAgentNode ignored -> "ticket-dispatch";
            case TicketNode ignored -> "ticket-agent";
            case TicketCollectorNode ignored -> "ticket-collector";
            default -> null;
        };
    }

    private String summarize(Events.GraphEvent event, int maxLength) {
        String formatted = cliEventFormatter.format(event);
        if (formatted == null || formatted.length() <= maxLength) {
            return formatted;
        }
        return formatted.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(cursor));
        } catch (NumberFormatException ignored) {
            return 0;
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

    private String toRootNodeId(String nodeId) {
        try {
            ArtifactKey current = new ArtifactKey(nodeId);
            while (current.parent().isPresent()) {
                current = current.parent().get();
            }
            return current.value();
        } catch (Exception ignored) {
            int idx = nodeId.indexOf('/');
            return idx > 0 ? nodeId.substring(0, idx) : nodeId;
        }
    }

    private String parentNodeId(String nodeId) {
        try {
            return new ArtifactKey(nodeId).parent().map(ArtifactKey::value).orElse(null);
        } catch (Exception ignored) {
            int idx = nodeId.lastIndexOf('/');
            return idx > 0 ? nodeId.substring(0, idx) : null;
        }
    }

    public record UiActionRequest(String actionType, java.util.Map<String, Object> payload) {
    }

    public record ActionResponse(String actionId, String status) {
    }

    public record StartGoalRequest(String goal, String repositoryUrl, String baseBranch, String title) {
    }

    public record StartGoalResponse(String nodeId, String runId, String status) {
    }

    public record QuickActionRequest(
            String actionType,
            String nodeId,
            String message,
            String goal,
            String repositoryUrl,
            String baseBranch,
            String title
    ) {
    }

    public record QuickActionResponse(String actionId, String status, String nodeId, String error) {
    }

    public record UiEventSummary(String eventId, String nodeId, String eventType, Instant timestamp, String summary) {
    }

    public record UiEventDetail(
            String eventId,
            String nodeId,
            String eventType,
            Instant timestamp,
            String summary,
            String formatted
    ) {
    }

    public record UiEventPage(List<UiEventSummary> items, PageMeta page, int total) {
    }

    public record PageMeta(int limit, String cursor, String nextCursor, boolean hasNext) {
    }

    public record WorkflowGraphResponse(
            String requestedNodeId,
            String rootNodeId,
            Instant capturedAt,
            WorkflowGraphStats stats,
            WorkflowNode root
    ) {
    }

    public record WorkflowGraphStats(
            int totalEvents,
            int totalNodes,
            Map<String, Integer> eventTypeCounts,
            int errorWindowSeconds,
            int recentErrorCount,
            Map<String, Integer> recentErrorsByNodeType,
            Map<String, Integer> errorsByAction,
            int chatSessionEvents,
            int chatMessageEvents,
            long thoughtTokens,
            long streamTokens
    ) {
    }

    public record WorkflowNode(
            String nodeId,
            String parentNodeId,
            String title,
            String nodeType,
            String currentStatus,
            String actionName,
            String statusReason,
            int routeBackCount,
            Instant lastEventAt,
            int totalEvents,
            WorkflowNodeMetrics metrics,
            List<WorkflowNode> children
    ) {
    }

    public record WorkflowNodeMetrics(
            int nodeErrorCount,
            int chatSessionEvents,
            int chatMessageEvents,
            int thoughtDeltas,
            long thoughtTokens,
            int streamDeltas,
            long streamTokens,
            int toolEvents,
            int otherEvents,
            List<PendingItem> pendingItems
    ) {
    }

    public record PendingItem(
            String id,
            String nodeId,
            String type,
            String description
    ) {
    }

}
