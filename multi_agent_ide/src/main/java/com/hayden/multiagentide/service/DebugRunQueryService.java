package com.hayden.multiagentide.service;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.controller.OrchestrationController;
import com.hayden.multiagentide.model.DebugRun;
import com.hayden.multiagentide.model.RunTimelineEvent;
import com.hayden.multiagentide.repository.EventStreamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class DebugRunQueryService {

    private final OrchestrationController orchestrationController;
    private final EventBus eventBus;
    private final EventStreamRepository eventStreamRepository;

    private final Map<String, DebugRun> runs = new ConcurrentHashMap<>();

    public DebugRun startRun(OrchestrationController.StartGoalRequest request) {
        OrchestrationController.StartGoalResponse started = orchestrationController.startGoalAsync(request);
        DebugRun run = DebugRun.builder()
                .runId(started.nodeId())
                .goal(request.goal())
                .repositoryUrl(request.repositoryUrl())
                .baseBranch(request.baseBranch())
                .title(request.title())
                .status(DebugRun.RunStatus.RUNNING)
                .loopRisk(false)
                .degraded(false)
                .startedAt(Instant.now())
                .build();
        runs.put(run.runId(), run);
        return run;
    }

    public Optional<DebugRun> findRun(String runId) {
        refreshFromEvents();
        return Optional.ofNullable(runs.get(runId));
    }

    public DebugRunPage listRuns(int limit, String cursor) {
        refreshFromEvents();
        List<DebugRun> ordered = runs.values().stream()
                .sorted(Comparator.comparing(DebugRun::startedAt).reversed().thenComparing(DebugRun::runId))
                .toList();

        int safeLimit = Math.max(1, Math.min(200, limit));
        int offset = parseCursor(cursor);
        int toIndex = Math.min(ordered.size(), offset + safeLimit);
        List<DebugRun> items = offset >= ordered.size() ? List.of() : ordered.subList(offset, toIndex);

        String nextCursor = toIndex < ordered.size() ? Integer.toString(toIndex) : null;
        return new DebugRunPage(items, new PageMeta(safeLimit, cursor, nextCursor, nextCursor != null));
    }

    public RunTimelinePage timeline(String runId, int limit, String cursor) {
        int safeLimit = Math.max(1, Math.min(1000, limit));
        List<RunTimelineEvent> all = collectTimeline(runId);
        int offset = parseCursor(cursor);
        int toIndex = Math.min(all.size(), offset + safeLimit);
        List<RunTimelineEvent> items = offset >= all.size() ? List.of() : all.subList(offset, toIndex);

        String nextCursor = toIndex < all.size() ? Integer.toString(toIndex) : null;
        return new RunTimelinePage(items, new PageMeta(safeLimit, cursor, nextCursor, nextCursor != null));
    }

    public ActionResponse applyAction(String runId, RunActionRequest request) {
        String targetNodeId = request.nodeId() == null || request.nodeId().isBlank() ? runId : request.nodeId();
        String action = request.actionType() == null ? "" : request.actionType().trim().toUpperCase();

        return switch (action) {
            // TODO: Re-enable control actions once LLM debug workflow behavior is finalized.
            // case "PAUSE" -> new ActionResponse(agentControlService.requestPause(targetNodeId, request.message()), "queued");
            // case "STOP" -> new ActionResponse(agentControlService.requestStop(targetNodeId), "queued");
            // case "RESUME" -> new ActionResponse(agentControlService.requestResume(targetNodeId, request.message()), "queued");
            // case "PRUNE" -> new ActionResponse(agentControlService.requestPrune(targetNodeId, request.message()), "queued");
            // case "BRANCH" -> new ActionResponse(agentControlService.requestBranch(targetNodeId, request.message()), "queued");
            // case "REVIEW_REQUEST" -> new ActionResponse(agentControlService.requestReview(targetNodeId, request.message()), "queued");
            case "SEND_MESSAGE" -> {
                String actionId = UUID.randomUUID().toString();
                eventBus.publish(new Events.AddMessageEvent(actionId, Instant.now(), targetNodeId, request.message() == null ? "" : request.message()));
                yield new ActionResponse(actionId, "queued");
            }
            default -> new ActionResponse(UUID.randomUUID().toString(), "rejected");
        };
    }

    public List<RunTimelineEvent> collectTimeline(String runId) {
        List<Events.GraphEvent> events = eventStreamRepository.list();
        List<RunTimelineEvent> timeline = new ArrayList<>();
        long sequence = 0L;

        for (Events.GraphEvent event : events.stream().sorted(Comparator.comparing(Events.GraphEvent::timestamp)).toList()) {
            if (!belongsToRun(event, runId)) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", event.eventType());
            payload.put("nodeId", event.nodeId());
            timeline.add(new RunTimelineEvent(
                    UUID.randomUUID().toString(),
                    runId,
                    sequence++,
                    event.eventId(),
                    event.eventType(),
                    event.nodeId(),
                    runId,
                    payload,
                    event.timestamp()
            ));
        }
        return timeline;
    }

    private void refreshFromEvents() {
        List<Events.GraphEvent> events = eventStreamRepository.list();
        for (DebugRun existing : new ArrayList<>(runs.values())) {
            DebugRun.RunStatus status = resolveStatus(existing.runId(), events).orElse(existing.status());
            DebugRun updated = existing;
            if (status != existing.status()) {
                updated = updated.toBuilder()
                        .status(status)
                        .endedAt(isTerminal(status) ? Instant.now() : null)
                        .build();
            }
            runs.put(updated.runId(), updated);
        }
    }

    private Optional<DebugRun.RunStatus> resolveStatus(String runId, List<Events.GraphEvent> events) {
        return events.stream()
                .filter(e -> belongsToRun(e, runId))
                .filter(Events.NodeStatusChangedEvent.class::isInstance)
                .map(Events.NodeStatusChangedEvent.class::cast)
                .max(Comparator.comparing(Events.NodeStatusChangedEvent::timestamp))
                .map(nodeStatusChangedEvent -> mapStatus(nodeStatusChangedEvent.newStatus()));
    }

    private DebugRun.RunStatus mapStatus(Events.NodeStatus nodeStatus) {
        if (nodeStatus == null) {
            return DebugRun.RunStatus.RUNNING;
        }
        return switch (nodeStatus) {
            case COMPLETED -> DebugRun.RunStatus.COMPLETED;
            case FAILED -> DebugRun.RunStatus.FAILED;
            case CANCELED -> DebugRun.RunStatus.STOPPED;
            case PRUNED -> DebugRun.RunStatus.PRUNED;
            case PENDING, READY, RUNNING, WAITING_REVIEW, WAITING_INPUT -> DebugRun.RunStatus.RUNNING;
        };
    }

    private boolean belongsToRun(Events.GraphEvent event, String runId) {
        if (event == null || runId == null || runId.isBlank()) {
            return false;
        }
        String eventNodeId = event.nodeId();
        if (eventNodeId == null || eventNodeId.isBlank()) {
            return false;
        }
        if (runId.equals(eventNodeId)) {
            return true;
        }
        try {
            ArtifactKey candidate = new ArtifactKey(eventNodeId);
            ArtifactKey scope = new ArtifactKey(runId);
            return candidate.isDescendantOf(scope);
        } catch (Exception ignored) {
            return eventNodeId.startsWith(runId + "/");
        }
    }

    private boolean isTerminal(DebugRun.RunStatus status) {
        return status == DebugRun.RunStatus.COMPLETED
                || status == DebugRun.RunStatus.FAILED
                || status == DebugRun.RunStatus.STOPPED
                || status == DebugRun.RunStatus.PRUNED;
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

    public record RunActionRequest(String actionType, String nodeId, String message) {
    }

    public record ActionResponse(String actionId, String status) {
    }

    public record PageMeta(int limit, String cursor, String nextCursor, boolean hasNext) {
    }

    public record DebugRunPage(List<DebugRun> items, PageMeta page) {
    }

    public record RunTimelinePage(List<RunTimelineEvent> items, PageMeta page) {
    }
}
