package com.hayden.multiagentidelib.agent;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.hayden.commitdiffcontext.cdc_utils.SetFromHeader;
import com.hayden.commitdiffcontext.mcp.ToolCarrier;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;

import static com.hayden.acp_cdc_ai.acp.AcpChatModel.MCP_SESSION_HEADER;

/**
 * Tool definitions for Context Manager agent operations over BlackboardHistory.
 * These tools enable deliberate context reconstruction and history navigation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextManagerTools implements ToolCarrier {

    public static final String SESSION_ID_MISSING_MESSAGE =
            "Session id is required - let them know that it failed - this is not your fault!";

    private final AgentPlatform agentPlatform;

    /**
     * Result of a history trace operation
     */
    public record HistoryTraceResult(
            @JsonPropertyDescription("Status of the operation")
            String status,
            @JsonPropertyDescription("List of history entries for the specified action/agent")
            List<HistoryEntryView> entries,
            @JsonPropertyDescription("Total number of entries found")
            int totalCount,
            @JsonPropertyDescription("Error message if operation failed")
            String error
    ) {}

    /**
     * Result of a history listing operation
     */
    public record HistoryListingResult(
            @JsonPropertyDescription("Status of the operation")
            String status,
            @JsonPropertyDescription("List of history entries in the page")
            List<HistoryEntryView> entries,
            @JsonPropertyDescription("Total number of entries available")
            int totalCount,
            @JsonPropertyDescription("Current page offset")
            int offset,
            @JsonPropertyDescription("Page size limit")
            int limit,
            @JsonPropertyDescription("Whether there are more entries available")
            boolean hasMore,
            @JsonPropertyDescription("Error message if operation failed")
            String error
    ) {}

    /**
     * Result of a history search operation
     */
    public record HistorySearchResult(
            @JsonPropertyDescription("Status of the operation")
            String status,
            @JsonPropertyDescription("Matching history entries")
            List<HistoryEntryView> matches,
            @JsonPropertyDescription("Total number of matches")
            int matchCount,
            @JsonPropertyDescription("Search query used")
            String query,
            @JsonPropertyDescription("Error message if operation failed")
            String error
    ) {}

    /**
     * Result of retrieving a specific history item
     */
    public record HistoryItemResult(
            @JsonPropertyDescription("Status of the operation")
            String status,
            @JsonPropertyDescription("The requested history entry")
            HistoryEntryView entry,
            @JsonPropertyDescription("Error message if operation failed")
            String error
    ) {}

    /**
     * Result of adding a note to history
     */
    public record HistoryNoteResult(
            @JsonPropertyDescription("Status of the operation")
            String status,
            @JsonPropertyDescription("Unique identifier for the note")
            String noteId,
            @JsonPropertyDescription("Timestamp when note was created")
            Instant created,
            @JsonPropertyDescription("Error message if operation failed")
            String error
    ) {}

    /**
     * Result of paging through message events.
     */
    public record MessagePageResult(
            @JsonPropertyDescription("Status of the operation")
            String status,
            @JsonPropertyDescription("Identifier for the message entry being paged")
            String entryId,
            @JsonPropertyDescription("Message events in this page")
            List<MessageEventView> events,
            @JsonPropertyDescription("Total number of events available")
            int totalCount,
            @JsonPropertyDescription("Current page offset")
            int offset,
            @JsonPropertyDescription("Page size limit")
            int limit,
            @JsonPropertyDescription("Whether there are more events available")
            boolean hasMore,
            @JsonPropertyDescription("Error message if operation failed")
            String error
    ) {}

    /**
     * Simplified view of a history entry for tool responses
     */
    public record HistoryEntryView(
            @JsonPropertyDescription("Entry index in history")
            int index,
            @JsonPropertyDescription("When the entry was created")
            Instant timestamp,
            @JsonPropertyDescription("Action name that created this entry")
            String actionName,
            @JsonPropertyDescription("Type of input stored")
            String inputType,
            @JsonPropertyDescription("String representation of the input")
            String inputSummary,
            @JsonPropertyDescription("Any notes attached to this entry")
            List<String> notes
    ) {}


    /**
     * Simplified view of a message event in a message entry.
     */
    public record MessageEventView(
            @JsonPropertyDescription("Event index within the message entry")
            int index,
            @JsonPropertyDescription("When the event was created")
            Instant timestamp,
            @JsonPropertyDescription("Event type")
            String eventType,
            @JsonPropertyDescription("Node ID associated with the event")
            String nodeId,
            @JsonPropertyDescription("String representation of the event")
            String summary
    ) {}

    /**
     * Retrieve the ordered history of events for a specific action or agent execution.
     * Used to understand what actually happened during a step.
     */
    @org.springframework.ai.tool.annotation.Tool(description = "Retrieve ordered history for a specific action")
    public HistoryTraceResult traceHistory(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId,
            @JsonPropertyDescription("Action name to filter by (optional) - accepts regex also.")
            String actionName,
            @JsonPropertyDescription("Input type to filter by (optional)")
            String inputTypeFilter
    ) {
        try {
            if (!StringUtils.hasText(sessionId)) {
                return new HistoryTraceResult("error", List.of(), 0, SESSION_ID_MISSING_MESSAGE);
            }

            var c = getCurrentHistory(sessionId);

            if (c == null)
                return new HistoryTraceResult("empty", List.of(), 0, null);

            return c.fromHistory(history -> {
                if (history == null) {
                    return new HistoryTraceResult("empty", List.of(), 0, null);
                }

                List<BlackboardHistory.Entry> filtered = history.entries().stream()
                        .filter(entry -> matchesActionName(actionName, entry.actionName()))
                        .filter(entry -> matchesInputType(inputTypeFilter, entry.inputType()))
                        .toList();

                List<HistoryEntryView> views = createEntryViews(filtered);

                return new HistoryTraceResult(
                        "success",
                        views,
                        views.size(),
                        null
                );
            });
        } catch (Exception e) {
            return new HistoryTraceResult("error", List.of(), 0, e.getMessage());
        }
    }

    private static boolean matchesInputType(String inputTypeFilter, Class<?> aClass) {
        return inputTypeFilter == null || (aClass != null && aClass.getSimpleName().equalsIgnoreCase(inputTypeFilter));
    }

    private static boolean matchesActionName(String actionNameFilter, String s) {
        return actionNameFilter == null
                || s.toLowerCase().contains(actionNameFilter.toLowerCase())
                || s.toLowerCase().matches(actionNameFilter)
                || s.matches(actionNameFilter);
    }

    /**
     * Traverse BlackboardHistory incrementally with pagination support.
     * Enables scanning backward or forward through long workflows.
     */
    @org.springframework.ai.tool.annotation.Tool(description = "List history entries with pagination")
    public HistoryListingResult listHistory(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId,
            @JsonPropertyDescription("Number of entries to skip (offset)")
            Integer offset,
            @JsonPropertyDescription("Maximum number of entries to return")
            Integer limit,
            @JsonPropertyDescription("Filter by time range start (ISO-8601)")
            String startTime,
            @JsonPropertyDescription("Filter by time range end (ISO-8601)")
            String endTime,
            @JsonPropertyDescription("Filter by action name - accepts regex also")
            String actionFilter
    ) {
        try {
            if (!StringUtils.hasText(sessionId)) {
                return new HistoryListingResult("error", List.of(), 0, 0, 0, false, SESSION_ID_MISSING_MESSAGE);
            }

            var c = getCurrentHistory(sessionId);

            if (c == null)
                return new HistoryListingResult("empty", List.of(), 0, 0, 0, false, null);

            return c.fromHistory(history -> {
                if (history == null) {
                    return new HistoryListingResult("empty", List.of(), 0, 0, 0, false, null);
                }

                int actualOffset = offset != null ? offset : 0;
                int actualLimit = limit != null ? Math.min(limit, 100) : 50; // Default 50, max 100

                List<BlackboardHistory.Entry> filtered = history.entries().stream()
                        .filter(entry -> startTime == null || entry.timestamp().isAfter(Instant.parse(startTime)))
                        .filter(entry -> endTime == null || entry.timestamp().isBefore(Instant.parse(endTime)))
                        .filter(entry -> matchesActionName(actionFilter, entry.actionName()))
                        .toList();

                List<BlackboardHistory.Entry> page = filtered.stream()
                        .skip(actualOffset)
                        .limit(actualLimit)
                        .toList();

                List<HistoryEntryView> views = createListingViews(history.entries(), page);

                return new HistoryListingResult(
                        "success",
                        views,
                        filtered.size(),
                        actualOffset,
                        actualLimit,
                        (actualOffset + actualLimit) < filtered.size(),
                        null
                );
            });
        } catch (Exception e) {
            return new HistoryListingResult("error", List.of(), 0, 0, 0, false, e.getMessage());
        }
    }

    /**
     * Search across BlackboardHistory contents.
     * Used to locate relevant prior decisions, errors, or artifacts.
     */
    @org.springframework.ai.tool.annotation.Tool(description = "Search history entries by content")
    public HistorySearchResult searchHistory(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId,
            @JsonPropertyDescription("Search query string")
            String query,
            @JsonPropertyDescription("Maximum number of results to return")
            Integer maxResults,
            @JsonPropertyDescription("Optional message entry id to scope search to")
            String entryId
    ) {
        try {
            if (!StringUtils.hasText(sessionId)) {
                return new HistorySearchResult("error", List.of(), 0, query, SESSION_ID_MISSING_MESSAGE);
            }
            if (!StringUtils.hasText(query)) {
                return new HistorySearchResult("error", List.of(), 0, query, "Query cannot be empty");
            }

            var c = getCurrentHistory(sessionId);

            if (c == null)
                return new HistorySearchResult("empty", List.of(), 0, query, null);

            return c.fromHistory(history -> {
                if (history == null) {
                    return new HistorySearchResult("empty", List.of(), 0, query, null);
                }

                int limit = maxResults != null ? Math.min(maxResults, 50) : 20;
                String lowerQuery = query.toLowerCase();

                List<HistoryEntryView> views;
                if (StringUtils.hasText(entryId)) {
                    BlackboardHistory.MessageEntry messageEntry = resolveMessageEntry(history, entryId);
                    if (messageEntry == null) {
                        return new HistorySearchResult("error", List.of(), 0, query, "Message entry not found");
                    }
                    List<MessageEventView> matches = messageEntry.events().stream()
                            .filter(event -> matchesQuery(event, lowerQuery))
                            .limit(limit)
                            .map(event -> createMessageEventView(event, messageEntry.events().indexOf(event)))
                            .toList();
                    views = matches.stream()
                            .map(view -> new HistoryEntryView(
                                    view.index(),
                                    view.timestamp(),
                                    entryId + "::" + view.eventType(),
                                    "MessageEvent",
                                    view.summary(),
                                    List.of()
                            ))
                            .toList();
                } else {
                    List<BlackboardHistory.Entry> matches = history.entries().stream()
                            .filter(entry -> matchesQuery(entry, lowerQuery))
                            .limit(limit)
                            .toList();
                    views = createEntryViews(matches);
                }

                return new HistorySearchResult(
                        "success",
                        views,
                        views.size(),
                        query,
                        null
                );
            });
        } catch (Exception e) {
            return new HistorySearchResult("error", List.of(), 0, query, e.getMessage());
        }
    }

    /**
     * Page through message events stored under a message entry.
     */
    @org.springframework.ai.tool.annotation.Tool(description = "Page through message events for a specific entry")
    public MessagePageResult listMessageEvents(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId,
            @JsonPropertyDescription("Message entry id from listHistory")
            String entryId,
            @JsonPropertyDescription("Number of events to skip (offset)")
            Integer offset,
            @JsonPropertyDescription("Maximum number of events to return")
            Integer limit
    ) {
        try {
            if (!StringUtils.hasText(sessionId)) {
                return new MessagePageResult("error", entryId, List.of(), 0, 0, 0, false, SESSION_ID_MISSING_MESSAGE);
            }
            if (!StringUtils.hasText(entryId)) {
                return new MessagePageResult("error", entryId, List.of(), 0, 0, 0, false, "Entry id is required");
            }

            var c = getCurrentHistory(sessionId);

            if (c == null)
                return new MessagePageResult("error", entryId, List.of(), 0, 0, 0, false, "No history available");

            return c.fromHistory(history -> {
                if (history == null) {
                    return new MessagePageResult("error", entryId, List.of(), 0, 0, 0, false, "No history available");
                }

                BlackboardHistory.MessageEntry messageEntry = resolveMessageEntry(history, entryId);
                if (messageEntry == null) {
                    return new MessagePageResult("error", entryId, List.of(), 0, 0, 0, false, "Message entry not found");
                }

                int actualOffset = offset != null ? offset : 0;
                int actualLimit = limit != null ? Math.min(limit, 100) : 50;

                List<MessageEventView> page = IntStream.range(0, messageEntry.events().size())
                        .skip(actualOffset)
                        .limit(actualLimit)
                        .mapToObj(index -> createMessageEventView(messageEntry.events().get(index), index))
                        .toList();

                return new MessagePageResult(
                        "success",
                        entryId,
                        page,
                        messageEntry.events().size(),
                        actualOffset,
                        actualLimit,
                        (actualOffset + actualLimit) < messageEntry.events().size(),
                        null
                );
            });
        } catch (Exception e) {
            return new MessagePageResult("error", entryId, List.of(), 0, 0, 0, false, e.getMessage());
        }
    }

    /**
     * Fetch a specific history entry by index.
     * Used when context creation references earlier events explicitly.
     */
    @org.springframework.ai.tool.annotation.Tool(description = "Retrieve a specific history entry by index")
    public HistoryItemResult getHistoryItem(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId,
            @JsonPropertyDescription("Zero-based index of the entry to retrieve")
            int index
    ) {
        try {
            if (!StringUtils.hasText(sessionId)) {
                return new HistoryItemResult("error", null, SESSION_ID_MISSING_MESSAGE);
            }
            var c = getCurrentHistory(sessionId);

            if (c == null)
                return new HistoryItemResult("error", null, "No history available");

            return c.fromHistory(history -> {
                if (history == null) {
                    return new HistoryItemResult("error", null, "No history available");
                }

                if (index < 0 || index >= history.entries().size()) {
                    return new HistoryItemResult("error", null,
                            String.format("Index %d out of bounds (0-%d)", index, history.entries().size() - 1));
                }

                BlackboardHistory.Entry entry = history.entries().get(index);
                HistoryEntryView view = createEntryView(entry, index);

                return new HistoryItemResult("success", view, null);
            });
        } catch (Exception e) {
            return new HistoryItemResult("error", null, e.getMessage());
        }
    }

    /**
     * Attach notes to BlackboardHistory entries.
     * Used to explain inclusion, exclusion, minimization, or routing rationale.
     */
    @org.springframework.ai.tool.annotation.Tool(description = "Add a note/annotation to history entries")
    public HistoryNoteResult addHistoryNote(
            @SetFromHeader(MCP_SESSION_HEADER)
            String sessionId,
            @JsonPropertyDescription("Indices of history entries this note references")
            List<Integer> entryIndices,
            @JsonPropertyDescription("Note content explaining reasoning or classification")
            String noteContent,
            @JsonPropertyDescription("Classification tags for the note (e.g., diagnostic, routing, exclusion)")
            List<String> tags
    ) {
        try {
            if (!StringUtils.hasText(sessionId)) {
                return new HistoryNoteResult("error", null, null, SESSION_ID_MISSING_MESSAGE);
            }
            if (!StringUtils.hasText(noteContent)) {
                return new HistoryNoteResult("error", null, null, "Note content cannot be empty");
            }

            var c = getCurrentHistory(sessionId);

            if (c == null)
                return new HistoryNoteResult("error", null, null, "No history available");

            return c.fromHistory(history -> {
                if (history == null) {
                    return new HistoryNoteResult("error", null, null, "No history available");
                }

                // Validate indices
                if (entryIndices != null) {
                    for (int index : entryIndices) {
                        if (index < 0 || index >= history.entries().size()) {
                            return new HistoryNoteResult("error", null, null,
                                    String.format("Index %d out of bounds", index));
                        }
                    }
                }

                String noteId = UUID.randomUUID().toString();
                Instant created = Instant.now();

                BlackboardHistory.HistoryNote note = new BlackboardHistory.HistoryNote(
                        noteId,
                        created,
                        noteContent,
                        tags != null ? tags : List.of(),
                        null
                );

                storeNote(note, entryIndices != null ? entryIndices : List.of());

                return new HistoryNoteResult("success", noteId, created, null);
            });
        } catch (Exception e) {
            return new HistoryNoteResult("error", null, null, e.getMessage());
        }
    }


    private BlackboardHistory getCurrentHistory(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        if (agentPlatform == null) {
            return null;
        }
        AgentProcess agentProcess = agentPlatform.getAgentProcess(sessionId);
        if (agentProcess == null) {
            return null;
        }
        return Optional.ofNullable(agentProcess.getBlackboard().last(BlackboardHistory.class))
                .or(() -> {
                    log.error("Blackboard history was not found.");
                    return Optional.empty();
                })
                .orElse(null);
    }

    private List<HistoryEntryView> createEntryViews(List<BlackboardHistory.Entry> entries) {
        return IntStream.range(0, entries.size())
                .mapToObj(index -> createEntryView(entries.get(index), index))
                .toList();
    }

    private List<HistoryEntryView> createListingViews(
            List<BlackboardHistory.Entry> allEntries,
            List<BlackboardHistory.Entry> pageEntries
    ) {
        return pageEntries.stream()
                .map(entry -> {
                    int index = allEntries.indexOf(entry);
                    if (index < 0) {
                        index = pageEntries.indexOf(entry);
                    }
                    return createEntryView(entry, index);
                })
                .toList();
    }

    private HistoryEntryView createEntryView(BlackboardHistory.Entry entry, int index) {
        return switch (entry) {
            case BlackboardHistory.MessageEntry messageEntry -> createMessageEntryView(messageEntry, index);
            case BlackboardHistory.DefaultEntry defaultEntry -> createDefaultEntryView(defaultEntry, index);
        };
    }

    private HistoryEntryView createDefaultEntryView(BlackboardHistory.DefaultEntry entry, int index) {
        String inputSummary = summarizeEntryInput(entry.input());

        String inputType = entry.inputType() != null
                ? entry.inputType().getSimpleName()
                : "unknown";

        return new HistoryEntryView(
                index,
                entry.timestamp(),
                entry.actionName(),
                inputType,
                inputSummary,
                getNotesForEntry(index)
        );
    }

    private HistoryEntryView createMessageEntryView(BlackboardHistory.MessageEntry entry, int index) {
        int count = entry.events().size();
        String summary = "Message entry id=" + messageEntryId(index) + ", totalEvents=" + count;
        return new HistoryEntryView(
                index,
                entry.timestamp(),
                entry.actionName(),
                "MessageEventPage",
                summary,
                getNotesForEntry(index)
        );
    }

    private MessageEventView createMessageEventView(Events.GraphEvent event, int index) {
        return new MessageEventView(
                index,
                event.timestamp(),
                event.eventType(),
                event.nodeId(),
                summarizeEvent(event)
        );
    }

    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    private String summarizeEntryInput(Object input) {
        if (input == null) {
            return "null";
        }
        if (input instanceof List<?> list) {
            return "Message events: " + list.size();
        }
        return truncate(input.toString(), 200);
    }

    private List<String> getNotesForEntry(int index) {
        // Placeholder - actual implementation would retrieve stored notes
        return List.of();
    }

    private String messageEntryId(int index) {
        return "messages:" + index;
    }

    private Integer parseMessageEntryIndex(String entryId) {
        if (!StringUtils.hasText(entryId) || !entryId.startsWith("messages:")) {
            return null;
        }
        try {
            return Integer.parseInt(entryId.substring("messages:".length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BlackboardHistory.MessageEntry resolveMessageEntry(BlackboardHistory.History history, String entryId) {
        Integer index = parseMessageEntryIndex(entryId);
        if (index == null) {
            return null;
        }
        if (index < 0 || index >= history.entries().size()) {
            return null;
        }
        BlackboardHistory.Entry entry = history.entries().get(index);
        return switch (entry) {
            case BlackboardHistory.MessageEntry messageEntry -> messageEntry;
            case BlackboardHistory.DefaultEntry ignored -> null;
        };
    }

    private boolean matchesQuery(BlackboardHistory.Entry entry, String query) {
        return switch (entry) {
            case BlackboardHistory.DefaultEntry defaultEntry -> {
                if (defaultEntry.actionName().toLowerCase().contains(query)) {
                    yield true;
                }
                if (defaultEntry.inputType() != null
                        && defaultEntry.inputType().getSimpleName().toLowerCase().contains(query)) {
                    yield true;
                }
                yield defaultEntry.input() != null && defaultEntry.input().toString().toLowerCase().contains(query);
            }
            case BlackboardHistory.MessageEntry messageEntry -> {
                if (messageEntry.actionName().toLowerCase().contains(query)) {
                    yield true;
                }
                yield false;
            }
        };
    }

    private boolean matchesQuery(Events.GraphEvent event, String query) {
        if (event.eventType().toLowerCase().contains(query)) {
            return true;
        }
        if (event.nodeId() != null && event.nodeId().toLowerCase().contains(query)) {
            return true;
        }
        return summarizeEvent(event).toLowerCase().contains(query);
    }

    private String summarizeEvent(Events.GraphEvent event) {
        if (event == null) {
            return "null";
        }
        return truncate(event.toString(), 200);
    }

    private void storeNote(BlackboardHistory.HistoryNote note, List<Integer> entryIndices) {
        // Placeholder - actual implementation would store in context or repository
    }
}
