package com.hayden.multiagentide.controller;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.adapter.SseEventAdapter;
import com.hayden.multiagentide.cli.CliEventFormatter;
import com.hayden.multiagentide.repository.EventStreamRepository;
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
import java.util.HashMap;
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

        List<Events.GraphEvent> scopedEvents = eventStreamRepository.list().stream()
                .filter(Objects::nonNull)
                .filter(event -> matchesNodeScope(rootNodeId, event.nodeId()))
                .sorted(Comparator.comparing(Events.GraphEvent::timestamp)
                        .thenComparing(Events.GraphEvent::eventId, Comparator.nullsLast(String::compareTo)))
                .toList();

        Map<String, NodeAccumulator> nodes = new LinkedHashMap<>();
        Map<String, Integer> eventTypeCounts = new LinkedHashMap<>();
        Map<String, Integer> recentErrorsByNodeType = new LinkedHashMap<>();
        int recentErrorCount = 0;
        long totalThoughtTokens = 0L;
        long totalStreamTokens = 0L;
        int chatSessionEvents = 0;
        int chatMessageEvents = 0;

        for (Events.GraphEvent event : scopedEvents) {
            eventTypeCounts.merge(event.eventType(), 1, Integer::sum);
            if (event.nodeId() == null || event.nodeId().isBlank()) {
                continue;
            }
            NodeAccumulator node = nodes.computeIfAbsent(event.nodeId(), NodeAccumulator::new);
            node.totalEvents++;
            node.lastEventAt = event.timestamp();
            node.eventTypeCounts.merge(event.eventType(), 1, Integer::sum);
            String parent = parentNodeId(event.nodeId());
            if (node.parentNodeId == null && parent != null) {
                node.parentNodeId = parent;
            }

            boolean countedAsSpecificMetric = true;
            switch (event) {
                case Events.NodeAddedEvent added -> {
                    node.title = added.nodeTitle();
                    node.nodeType = added.nodeType() == null ? null : added.nodeType().name();
                    if (added.parentNodeId() != null && !added.parentNodeId().isBlank()) {
                        node.parentNodeId = added.parentNodeId();
                    }
                }
                case Events.ActionStartedEvent action -> {
                    node.actionName = action.actionName();
                }
                case Events.NodeStatusChangedEvent statusChanged -> {
                    node.currentStatus = statusChanged.newStatus().name();
                    if (statusChanged.reason() != null && !statusChanged.reason().isBlank()) {
                        node.statusReason = statusChanged.reason();
                        String reasonLower = statusChanged.reason().toLowerCase();
                        if (reasonLower.contains("route_back") || reasonLower.contains("route back")
                                || reasonLower.contains("routed back")) {
                            node.routeBackCount++;
                        }
                    }
                }
                case Events.NodeErrorEvent error -> {
                    node.metrics.nodeErrorCount++;
                    if (!event.timestamp().isBefore(errorWindowStart)) {
                        recentErrorCount++;
                        String key = error.nodeType() == null ? "UNKNOWN" : error.nodeType().name();
                        recentErrorsByNodeType.merge(key, 1, Integer::sum);
                    }
                }
                case Events.ChatSessionCreatedEvent ignored -> {
                    node.metrics.chatSessionEvents++;
                    chatSessionEvents++;
                }
                case Events.AddMessageEvent ignored -> {
                    node.metrics.chatMessageEvents++;
                    chatMessageEvents++;
                }
                case Events.NodeThoughtDeltaEvent thought -> {
                    node.metrics.thoughtDeltas++;
                    node.metrics.thoughtTokens += thought.tokenCount();
                    totalThoughtTokens += thought.tokenCount();
                }
                case Events.NodeStreamDeltaEvent stream -> {
                    node.metrics.streamDeltas++;
                    node.metrics.streamTokens += stream.tokenCount();
                    totalStreamTokens += stream.tokenCount();
                }
                case Events.ToolCallEvent ignored -> node.metrics.toolEvents++;
                case Events.PermissionRequestedEvent perm -> {
                    node.metrics.pendingItems.add(new PendingItem(
                            perm.requestId(),
                            perm.nodeId(),
                            "PERMISSION",
                            "Permission requested for tool call " + perm.toolCallId()
                                    + ". Resolve with: resolve-permission --id " + perm.requestId() + " --option-type ALLOW_ONCE"
                    ));
                }
                case Events.PermissionResolvedEvent permResolved -> {
                    node.metrics.pendingItems.removeIf(p ->
                            "PERMISSION".equals(p.type()) && p.id().equals(permResolved.requestId()));
                }
                case Events.InterruptRequestEvent interrupt -> {
                    node.metrics.pendingItems.add(new PendingItem(
                            interrupt.requestId(),
                            interrupt.nodeId(),
                            "INTERRUPT",
                            "Interrupt requested: " + interrupt.reason()
                                    + ". Resolve with: resolve-interrupt --id " + interrupt.requestId()
                                    + " --origin-node-id " + interrupt.nodeId()
                    ));
                }
                case Events.InterruptStatusEvent interruptStatus -> {
                    if (!"REQUESTED".equalsIgnoreCase(interruptStatus.interruptStatus())) {
                        node.metrics.pendingItems.removeIf(p ->
                                "INTERRUPT".equals(p.type()) && p.nodeId().equals(interruptStatus.originNodeId()));
                    }
                }
                case Events.NodeReviewRequestedEvent review -> {
                    node.metrics.pendingItems.add(new PendingItem(
                            review.reviewNodeId(),
                            review.nodeId(),
                            "REVIEW",
                            "Review requested (" + review.reviewType() + "). Review content at node " + review.reviewNodeId()
                    ));
                }
                default -> {
                    countedAsSpecificMetric = false;
                }
            }
            if (!countedAsSpecificMetric) {
                node.metrics.otherEvents++;
            }
        }

        // Ensure every node has a parent assigned where possible.
        for (NodeAccumulator node : nodes.values()) {
            if ((node.parentNodeId == null || node.parentNodeId.isBlank()) && !rootNodeId.equals(node.nodeId)) {
                node.parentNodeId = parentNodeId(node.nodeId);
            }
        }
        nodes.computeIfAbsent(rootNodeId, NodeAccumulator::new);

        // Collapse: merge unnamed nodes into nearest named ancestor.
        // The root node is always kept even if unnamed.
        Map<String, String> collapseTarget = new HashMap<>();
        for (NodeAccumulator node : nodes.values()) {
            if (node.isNamed() || node.nodeId.equals(rootNodeId)) {
                collapseTarget.put(node.nodeId, node.nodeId);
            } else {
                String target = findNamedAncestor(node.nodeId, nodes, rootNodeId);
                collapseTarget.put(node.nodeId, target);
            }
        }
        for (NodeAccumulator node : nodes.values()) {
            String target = collapseTarget.get(node.nodeId);
            if (target != null && !target.equals(node.nodeId)) {
                NodeAccumulator ancestor = nodes.get(target);
                if (ancestor != null) {
                    ancestor.mergeFrom(node);
                }
            }
        }

        // Build children map using only named nodes (+ root).
        // Use the ArtifactKey hierarchy to determine parent-child relationships:
        // for each named node, walk up its AK segments to find the nearest named ancestor.
        Map<String, List<String>> children = new HashMap<>();
        for (NodeAccumulator node : nodes.values()) {
            if (!node.isNamed() && !node.nodeId.equals(rootNodeId)) continue;
            if (node.nodeId.equals(rootNodeId)) continue;
            String namedParent = findNamedAncestor(node.nodeId, nodes, rootNodeId);
            if (namedParent == null || namedParent.equals(node.nodeId)) continue;
            children.computeIfAbsent(namedParent, ignored -> new ArrayList<>()).add(node.nodeId);
            node.parentNodeId = namedParent;
        }
        for (List<String> childIds : children.values()) {
            childIds.sort(Comparator.naturalOrder());
        }

        // Compute per-action error counts from named nodes.
        Map<String, Integer> errorsByAction = new LinkedHashMap<>();
        for (NodeAccumulator node : nodes.values()) {
            if (node.isNamed() && node.actionName != null && node.metrics.nodeErrorCount > 0) {
                errorsByAction.merge(node.actionName, node.metrics.nodeErrorCount, Integer::sum);
            }
        }

        long namedNodeCount = nodes.values().stream()
                .filter(n -> n.isNamed() || n.nodeId.equals(rootNodeId))
                .count();

        WorkflowNode root = toWorkflowNode(rootNodeId, nodes, children);
        WorkflowGraphStats stats = new WorkflowGraphStats(
                scopedEvents.size(),
                (int) namedNodeCount,
                eventTypeCounts,
                safeErrorWindowSeconds,
                recentErrorCount,
                recentErrorsByNodeType,
                errorsByAction,
                chatSessionEvents,
                chatMessageEvents,
                totalThoughtTokens,
                totalStreamTokens
        );
        return new WorkflowGraphResponse(
                nodeId,
                rootNodeId,
                now,
                stats,
                root
        );
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

    /**
     * Walk up the ArtifactKey hierarchy to find the nearest named ancestor node.
     * Returns rootNodeId as fallback if no named ancestor is found.
     */
    private String findNamedAncestor(String nodeId, Map<String, NodeAccumulator> nodes, String rootNodeId) {
        String current = nodeId;
        while (current != null && !current.isBlank()) {
            NodeAccumulator acc = nodes.get(current);
            if (acc != null && (acc.isNamed() || current.equals(rootNodeId))) {
                return current;
            }
            current = parentNodeId(current);
        }
        return rootNodeId;
    }

    private WorkflowNode toWorkflowNode(
            String nodeId,
            Map<String, NodeAccumulator> nodes,
            Map<String, List<String>> children
    ) {
        NodeAccumulator node = nodes.computeIfAbsent(nodeId, NodeAccumulator::new);
        List<WorkflowNode> childNodes = children.getOrDefault(nodeId, List.of()).stream()
                .map(childId -> toWorkflowNode(childId, nodes, children))
                .toList();
        return new WorkflowNode(
                node.nodeId,
                node.parentNodeId,
                node.title,
                node.nodeType,
                node.currentStatus,
                node.actionName,
                node.statusReason,
                node.routeBackCount,
                node.lastEventAt,
                node.totalEvents,
                node.metrics.toSnapshot(),
                childNodes
        );
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

    private static final class NodeAccumulator {
        private final String nodeId;
        private String parentNodeId;
        private String title;
        private String nodeType;
        private String currentStatus;
        private String actionName;
        private String statusReason;
        private int routeBackCount;
        private Instant lastEventAt;
        private int totalEvents;
        private final Map<String, Integer> eventTypeCounts = new LinkedHashMap<>();
        private final MetricsAccumulator metrics = new MetricsAccumulator();

        private NodeAccumulator(String nodeId) {
            this.nodeId = nodeId;
        }

        private boolean isNamed() {
            return title != null && !title.isBlank();
        }

        private void mergeFrom(NodeAccumulator other) {
            totalEvents += other.totalEvents;
            for (var entry : other.eventTypeCounts.entrySet()) {
                eventTypeCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
            metrics.mergeFrom(other.metrics);
            if (other.lastEventAt != null && (lastEventAt == null || other.lastEventAt.isAfter(lastEventAt))) {
                lastEventAt = other.lastEventAt;
            }
        }
    }

    private static final class MetricsAccumulator {
        private int nodeErrorCount;
        private int chatSessionEvents;
        private int chatMessageEvents;
        private int thoughtDeltas;
        private long thoughtTokens;
        private int streamDeltas;
        private long streamTokens;
        private int toolEvents;
        private int otherEvents;
        private final List<PendingItem> pendingItems = new ArrayList<>();

        private void mergeFrom(MetricsAccumulator other) {
            nodeErrorCount += other.nodeErrorCount;
            chatSessionEvents += other.chatSessionEvents;
            chatMessageEvents += other.chatMessageEvents;
            thoughtDeltas += other.thoughtDeltas;
            thoughtTokens += other.thoughtTokens;
            streamDeltas += other.streamDeltas;
            streamTokens += other.streamTokens;
            toolEvents += other.toolEvents;
            otherEvents += other.otherEvents;
            pendingItems.addAll(other.pendingItems);
        }

        private WorkflowNodeMetrics toSnapshot() {
            return new WorkflowNodeMetrics(
                    nodeErrorCount,
                    chatSessionEvents,
                    chatMessageEvents,
                    thoughtDeltas,
                    thoughtTokens,
                    streamDeltas,
                    streamTokens,
                    toolEvents,
                    otherEvents,
                    List.copyOf(pendingItems)
            );
        }
    }
}
