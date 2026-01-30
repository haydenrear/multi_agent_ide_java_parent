package com.hayden.multiagentidelib.service;

import com.hayden.utilitymodule.acp.events.Events;
import com.hayden.multiagentidelib.model.ui.UiDiffRequest;
import com.hayden.multiagentidelib.model.ui.UiDiffResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UiStateService {

    private static final int MAX_HISTORY = 20;

    private final Map<String, Deque<Events.UiStateSnapshot>> snapshots = new ConcurrentHashMap<>();
    private final AtomicLong revisionCounter = new AtomicLong();

    public Events.UiStateSnapshot captureSnapshot(String sessionId, Object renderTree) {
        if (sessionId == null || sessionId.isBlank() || renderTree == null) {
            return null;
        }
        String revision = nextRevision();
        Events.UiStateSnapshot snapshot = new Events.UiStateSnapshot(sessionId, revision, Instant.now(), renderTree);
        Deque<Events.UiStateSnapshot> history = snapshots.computeIfAbsent(sessionId, ignored -> new ArrayDeque<>());
        synchronized (history) {
            history.addFirst(snapshot);
            while (history.size() > MAX_HISTORY) {
                history.removeLast();
            }
        }
        return snapshot;
    }

    public Events.UiStateSnapshot getSnapshot(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        Deque<Events.UiStateSnapshot> history = snapshots.get(sessionId);
        if (history == null) {
            return null;
        }
        synchronized (history) {
            return history.peekFirst();
        }
    }

    public UiDiffResult applyDiff(UiDiffRequest request, String sessionId) {
        Events.UiStateSnapshot current = getSnapshot(sessionId);
        if (current == null) {
            return new UiDiffResult("rejected", null, "missing_snapshot", "No UI snapshot available.");
        }
        if (request.baseRevision() != null
                && current.revision() != null
                && !request.baseRevision().equals(current.revision())) {
            return new UiDiffResult("rejected", current.revision(), "revision_mismatch",
                    "Base revision does not match current snapshot.");
        }
        Object nextRenderTree = mergeRenderTree(current.renderTree(), request.diff());
        Events.UiStateSnapshot updated = captureSnapshot(sessionId, nextRenderTree);
        return new UiDiffResult("applied", updated != null ? updated.revision() : null, null, "Diff applied.");
    }

    public UiDiffResult revert(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return new UiDiffResult("rejected", null, "missing_session", "Session id is required.");
        }
        Deque<Events.UiStateSnapshot> history = snapshots.get(sessionId);
        if (history == null) {
            return new UiDiffResult("rejected", null, "missing_snapshot", "No UI snapshot available.");
        }
        Events.UiStateSnapshot snapshot;
        synchronized (history) {
            if (history.size() < 2) {
                return new UiDiffResult("rejected", history.peekFirst() != null ? history.peekFirst().revision() : null,
                        "no_previous_revision", "No previous snapshot to revert to.");
            }
            history.removeFirst();
            snapshot = history.peekFirst();
        }
        return new UiDiffResult("reverted", snapshot != null ? snapshot.revision() : null, null, "Reverted.");
    }

    private String nextRevision() {
        return Long.toString(revisionCounter.incrementAndGet());
    }

    private Object mergeRenderTree(Object current, Object diff) {
        if (diff == null) {
            return current;
        }
        if (current instanceof Map<?, ?> currentMap && diff instanceof Map<?, ?> diffMap) {
            Map<Object, Object> merged = new LinkedHashMap<>();
            merged.putAll(currentMap);
            merged.putAll(diffMap);
            return merged;
        }
        return diff;
    }
}
