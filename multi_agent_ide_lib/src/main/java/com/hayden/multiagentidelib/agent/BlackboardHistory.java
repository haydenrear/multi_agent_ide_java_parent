package com.hayden.multiagentidelib.agent;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.Blackboard;
import com.hayden.commitdiffcontext.events.EventSubscriber;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Blackboard history management for tracking action inputs and preventing
 * unwanted state accumulation through clearing.
 */
@Slf4j
public class BlackboardHistory implements EventListener, EventSubscriber<Events.GraphEvent> {

    private static final int DEFAULT_LOOP_THRESHOLD = 3;

    private static volatile int loopThreshold = DEFAULT_LOOP_THRESHOLD;

    private final String listenerId;
    private final String nodeId;

    private volatile WorkflowGraphState state;
    private volatile History history;

    public static AgentModels.AgentRequest findLastRequest(BlackboardHistory bh,
                                                       Predicate<AgentModels.AgentRequest> r) {
        if (bh == null)
            return null;

        return bh.fromHistory(history -> {
            if (history == null || history.entries() == null) {
                return null;
            }
            List<Entry> entries = history.entries();
            for (int i = entries.size() - 1; i >= 0; i--) {
                Entry entry = entries.get(i);
                if (entry == null) {
                    continue;
                }
                Object input = switch (entry) {
                    case DefaultEntry defaultEntry ->
                            defaultEntry.input();
                    case MessageEntry ignored ->
                            null;
                };
                if (input instanceof AgentModels.AgentRequest agentRequest && r.test(agentRequest)) {
                    return agentRequest;
                }
            }
            return null;
        });
    }

    public static AgentModels.@Nullable AgentRequest findLastNonContextRequest(BlackboardHistory history) {
        AgentModels.AgentRequest lastRequest = findLastRequest(
                history,
                a -> !(a instanceof AgentModels.InterruptRequest)
                        && !(a instanceof AgentModels.ContextManagerRequest)
                        && !(a instanceof AgentModels.ContextManagerRoutingRequest));
        return lastRequest;
    }

    /**
     * Tracks a single historical blackboard state entry.
     * Each entry represents a previous action's input that has been archived.
     */
    public sealed interface Entry permits DefaultEntry, MessageEntry {
        Instant timestamp();
        String actionName();
        Object input();
        Class<?> inputType();
    }

    public BlackboardHistory(History history, String nodeId, WorkflowGraphState state) {
        this.history = history == null ? new History() : history;
        this.nodeId = nodeId;
        this.listenerId = "BlackboardHistory-" + System.identityHashCode(this);
        this.state = state;
    }

    public synchronized <T> T fromHistory(Function<History, T> t) {
        return t.apply(history);
    }

    public synchronized <T> T getLastOfType(Class<T> t)  {
        return history.getLastOfType(t).orElse(null);
    }

    public synchronized <T> Optional<T> fromState(Function<WorkflowGraphState, T> t) {
        if (state == null)
            return Optional.empty();
        return Optional.ofNullable(t.apply(state));
    }

    public synchronized void updateState(Function<@Nullable WorkflowGraphState, WorkflowGraphState> t) {
        this.state = t.apply(this.state);
    }

    synchronized void addEntry(String actionName, Object enrichedInput) {
        this.history = this.history.withEntry(actionName, enrichedInput);
    }

    public synchronized <T> Optional<T> getValue(Function<Entry, Optional<T>> findValue) {
        if (history == null || history.entries() == null) {
            return Optional.empty();
        }
        List<Entry> entries = history.entries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (entry == null) {
                continue;
            }

            var f = findValue.apply(entry);

            if (f.isPresent())
                return f;
        }

        return Optional.empty();

    }

    private History history() {
        return history;
    }

    public synchronized String summary() {
        return Optional.ofNullable(history)
                .flatMap(h -> Optional.of(h.getSummary()))
                .orElse(null);
    }

    public static BlackboardHistory getEntireBlackboardHistory(Blackboard context) {
        return context.last(BlackboardHistory.class);
    }

    public static <T> T getLastFromHistory(Blackboard context, Class<T> inputType) {
        var history = getEntireBlackboardHistory(context);

        if (history == null) {
            return null;
        }

        return history.getLastOfType(inputType);
    }

    public static void setLoopThreshold(int threshold) {
        if (threshold <= 0) {
            return;
        }
        loopThreshold = threshold;
    }

    public static int getLoopThreshold() {
        return loopThreshold;
    }

    public static boolean detectLoop(OperationContext context, Class<?> inputType) {
        return Optional.ofNullable(getEntireBlackboardHistory(context))
                .map(bh -> bh.fromHistory(h -> h.detectLoop(inputType)))
                .orElse(false);
    }

    public static BlackboardHistory ensureSubscribed(EventBus eventBus, OperationContext context, Supplier<WorkflowGraphState> factory) {
        if (eventBus == null || context == null) {
            return null;
        }
        BlackboardHistory existing = context.last(BlackboardHistory.class);
        if (existing != null) {
            return existing;
        }

        BlackboardHistory listener = new BlackboardHistory(new History(), resolveNodeId(context), factory.get());
        context.getAgentProcess().addObject(listener);
        eventBus.subscribe(listener);
        return listener;
    }

    public static void unsubscribe(EventBus eventBus, OperationContext context) {
        if (eventBus == null || context == null) {
            return;
        }

        BlackboardHistory existing = context.last(BlackboardHistory.class);

        if (existing != null)
            eventBus.unsubscribe(existing);
        else
            log.error("Attempted to unsubscribe from blackboard history not found for {}.",
                       context.getAgentProcess().getId());
    }

    private static String resolveNodeId(OperationContext context) {
        if (context == null) {
            return null;
        }
        var options = context.getProcessContext().getProcessOptions();
        return options.getContextIdString();
    }

    public long countType(Class<?> requestType) {
        return history.countType(requestType);
    }

    public List<Entry> copyOfEntries() {
        return List.copyOf(this.history.entries());
    }


    public record DefaultEntry(
            Instant timestamp,
            String actionName,
            Object input,
            Class<?> inputType
    ) implements Entry {
    }

    public record MessageEntry(
            Instant timestamp,
            String actionName,
            List<Events.GraphEvent> events
    ) implements Entry {
        @Override
        public Object input() {
            return events;
        }

        @Override
        public Class<?> inputType() {
            return List.class;
        }
    }

    /**
     * The main history container.
     * Instead of clearing the blackboard, inputs are transferred here.
     */
    public record History(
            List<Entry> entries
    ) {
        public History() {
            this(Collections.synchronizedList(new ArrayList<>()));
        }

        boolean detectLoop(OperationContext context, Class<?> inputType, int threshold) {
            return this.detectLoop(inputType, threshold);
        }

        /**
         * Add an entry to the history
         */
        public History withEntry(String actionName, Object input) {
            List<Entry> newEntries = new ArrayList<>(entries);
            newEntries.add(new DefaultEntry(
                    Instant.now(),
                    actionName,
                    input,
                    input != null ? input.getClass() : null
            ));
            return new History(newEntries);
        }

        /**
         * Check if we've seen this input type before (indicating a retry/loop)
         */
        public boolean hasSeenType(Class<?> type) {
            return entries.stream()
                    .anyMatch(entry -> entry.inputType() != null && entry.inputType().equals(type));
        }

        /**
         * Count how many times we've seen this input type
         */
        public long countType(Class<?> type) {
            return entries.stream()
                    .filter(entry -> entry.inputType() != null && entry.inputType().equals(type))
                    .count();
        }

        /**
         * Get all entries of a specific type
         */
        @SuppressWarnings("unchecked")
        public <T> List<T> getEntriesOfType(Class<T> type) {
            return entries.stream()
                    .filter(entry -> entry.inputType() != null && entry.inputType().equals(type))
                    .map(entry -> (T) entry.input())
                    .collect(Collectors.toList());
        }

        public <T> List<T> getEntriesOfTypeOrSuper(Class<T> type) {
            return entries.stream()
                    .filter(entry -> entry.inputType() != null && (entry.inputType().equals(type) || type.isAssignableFrom(entry.inputType())))
                    .map(entry -> (T) entry.input())
                    .collect(Collectors.toList());
        }

        /**
         * Get the most recent entry of a specific type
         */
        @SuppressWarnings("unchecked")
        public <T> Optional<T> getLastOfType(Class<T> type) {
            return entries.stream()
                    .filter(entry -> entry.inputType() != null && entry.inputType().equals(type))
                    .map(entry -> (T) entry.input())
                    .reduce((first, second) -> second)
                    .or(() -> entries.stream()
                            .filter(entry -> isTy(type, entry))
                            .map(entry -> (T) entry.input())
                            .reduce((first, second) -> second));
        }

        private <T> boolean isTy(Class<T> type, Entry entry) {
            return entry.inputType() != null
                    && (type.isAssignableFrom(entry.inputType()) || type.equals(entry.inputType()));
        }

        /**
         * Check if this is a retry of a specific action
         */
        public boolean isRetry(String actionName) {
            return entries.stream()
                    .anyMatch(entry -> entry.actionName().equals(actionName));
        }

        /**
         * Get retry count for a specific action
         */
        public long getRetryCount(String actionName) {
            return entries.stream()
                    .filter(entry -> entry.actionName().equals(actionName))
                    .count();
        }

        public boolean detectLoop(Class<?> type) {
            return detectLoop(type, BlackboardHistory.getLoopThreshold());
        }

        /**
         * Check if we're in a loop by detecting repeated patterns
         */
        public boolean detectLoop(Class<?> type, int threshold) {
            return countType(type) >= threshold;
        }

        /**
         * Get a summary of all historical entries
         */
        public String getSummary() {
            if (entries.isEmpty()) {
                return "No historical entries";
            }

            Map<String, Long> actionCounts = entries.stream()
                    .collect(Collectors.groupingBy(
                            Entry::actionName,
                            Collectors.counting()
                    ));

            StringBuilder summary = new StringBuilder("History Summary:\n");
            actionCounts.forEach((action, count) ->
                    summary.append(String.format("  - %s: %d attempts\n", action, count))
            );

            return summary.toString();
        }

        public Object last() {
            return Optional.ofNullable(entries.getLast()).flatMap(e -> Optional.ofNullable(e.input()));
        }

        private void addEvent(Events.GraphEvent event, String actionName) {
            if (event == null) {
                return;
            }
            entries.add(new DefaultEntry(
                    event.timestamp(),
                    actionName != null ? actionName : event.eventType(),
                    event,
                    event.getClass()
            ));
        }


        private void addMessageEvent(Events.GraphEvent event, String actionName) {
            if (event == null || actionName == null) {
                return;
            }
            for (Entry entry : entries) {
                if (actionName.equals(entry.actionName()) && entry instanceof MessageEntry messageEntry) {
                    messageEntry.events().add(event);
                    return;
                }
            }
            List<Events.GraphEvent> events = new ArrayList<>();
            events.add(event);
            entries.add(new MessageEntry(
                    event.timestamp(),
                    actionName,
                    events
            ));
        }

        public <T> List<T> getLast(Class<T> contextManagerRequestClass) {
            if (!isTy(contextManagerRequestClass, this.entries.getLast())) {
                return new ArrayList<>();
            }

            return this.entries.reversed().stream()
                    .takeWhile(e -> isTy(contextManagerRequestClass, e))
                    .map(e -> (T) e.input())
                    .toList();
        }
    }

    @Override
    public String listenerId() {
        return listenerId;
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        if (event == null) {
            return;
        }
        if (nodeId != null && !nodeId.isBlank() && !nodeId.equals(event.nodeId())) {
            return;
        }
        List<String> targets = classifyEventTargets(event);
        if (targets.isEmpty()) {
            return;
        }
        boolean isMessage = isMessageEvent(event);
        for (String target : targets) {
            String actionName = isMessage ? target + "::messages" : target + "::" + event.eventType();
            if (isMessage) {
                history.addMessageEvent(event, actionName);
            } else {
                history.addEvent(event, actionName);
            }
        }
    }

    @Override
    public Class<Events.GraphEvent> eventType() {
        return Events.GraphEvent.class;
    }

    private static List<String> classifyEventTargets(Events.GraphEvent event) {
        return switch (event) {
            case Events.NodeAddedEvent nodeAdded -> buildTargets(event.nodeId(), nodeAdded.parentNodeId());
            case Events.ActionStartedEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.ActionCompletedEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.StopAgentEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.PauseEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.ResumeEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.ResolveInterruptEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.AddMessageEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.InterruptRequestEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.NodeStatusChangedEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.NodeErrorEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.NodeBranchedEvent nodeBranched -> buildTargets(event.nodeId(), nodeBranched.originalNodeId());
            case Events.NodePrunedEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.NodeReviewRequestedEvent reviewRequested ->
                    buildTargets(reviewRequested.reviewNodeId(), reviewRequested.nodeId());
            case Events.InterruptStatusEvent interruptStatus ->
                    buildTargets(event.nodeId(), interruptStatus.originNodeId(), interruptStatus.resumeNodeId());
            case Events.GoalCompletedEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.WorktreeCreatedEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.WorktreeBranchedEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.WorktreeMergedEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.WorktreeDiscardedEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.NodeUpdatedEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.NodeDeletedEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.NodeStreamDeltaEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.NodeThoughtDeltaEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.ToolCallEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.GuiRenderEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.UiDiffAppliedEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.UiDiffRejectedEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.UiDiffRevertedEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.UiFeedbackEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.NodeBranchRequestedEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.PlanUpdateEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.UserMessageChunkEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.CurrentModeUpdateEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.AvailableCommandsUpdateEvent ignored -> buildTargets(event.nodeId(), null);
            case Events.PermissionRequestedEvent permissionRequested ->
                    buildTargets(event.nodeId(), permissionRequested.originNodeId());
            case Events.PermissionResolvedEvent permissionResolved ->
                    buildTargets(event.nodeId(), permissionResolved.originNodeId());
            case Events.ArtifactEvent artifactEvent ->
                    new ArrayList<>();
            case Events.ChatSessionCreatedEvent chatSessionCreatedEvent ->
                    new ArrayList<>();
            case Events.ChatSessionClosedEvent chatSessionClosedEvent ->
                    new ArrayList<>();
            case Events.TuiInteractionGraphEvent tuiInteractionGraphEvent ->
                    new ArrayList<>();
            case Events.TuiSystemGraphEvent tuiSystemGraphEvent ->
                    new ArrayList<>();
        };
    }

    private static List<String> buildTargets(String nodeId, String parentNodeId, String... additionalParents) {
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        String nodeTarget = nodeKey(nodeId);
        if (hasText(nodeTarget)) {
            targets.add(nodeTarget);
        }
        String parentTarget = parentKey(parentNodeId);
        if (hasText(parentTarget)) {
            targets.add(parentTarget);
        }
        if (additionalParents != null) {
            for (String parent : additionalParents) {
                String target = parentKey(parent);
                if (hasText(target)) {
                    targets.add(target);
                }
            }
        }
        return List.copyOf(targets);
    }

    private static String nodeKey(String nodeId) {
        if (!hasText(nodeId)) {
            return null;
        }
        return "node:" + nodeId;
    }

    private static String parentKey(String parentNodeId) {
        if (!hasText(parentNodeId)) {
            return null;
        }
        return "parent:" + parentNodeId;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isMessageEvent(Events.GraphEvent event) {
        return event instanceof Events.NodeStreamDeltaEvent
                || event instanceof Events.NodeThoughtDeltaEvent
                || event instanceof Events.ToolCallEvent
                || event instanceof Events.AddMessageEvent
                || event instanceof Events.UserMessageChunkEvent;
    }

    /**
     * Represents a note attached to history entries.
     */
    public record HistoryNote(
            String noteId,
            Instant timestamp,
            String content,
            List<String> tags,
            String authorAgent
    ) {
    }

}
