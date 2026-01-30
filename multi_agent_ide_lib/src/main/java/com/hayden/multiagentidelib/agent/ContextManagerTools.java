package com.hayden.multiagentidelib.agent;

import com.embabel.agent.api.common.OperationContext;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.hayden.commitdiffcontext.cdc_utils.SetFromHeader;
import com.hayden.commitdiffcontext.mcp.ToolCarrier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.hayden.utilitymodule.acp.AcpChatModel.MCP_SESSION_HEADER;

/**
 * Tool definitions for Context Manager agent operations over BlackboardHistory.
 * These tools enable deliberate context reconstruction and history navigation.
 */
@Component
@RequiredArgsConstructor
public class ContextManagerTools implements ToolCarrier {

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
     * Result of creating a context snapshot
     */
    public record ContextSnapshotResult(
            @JsonPropertyDescription("Status of the operation")
            String status,
            @JsonPropertyDescription("Unique identifier for the snapshot")
            String snapshotId,
            @JsonPropertyDescription("Timestamp when snapshot was created")
            Instant created,
            @JsonPropertyDescription("Number of history entries referenced")
            int entryCount,
            @JsonPropertyDescription("Summary of the snapshot")
            String summary,
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
     * Retrieve the ordered history of events for a specific action or agent execution.
     * Used to understand what actually happened during a step.
     */
    @org.springframework.ai.tool.annotation.Tool(description = "Retrieve ordered history for a specific action")
    public HistoryTraceResult traceHistory(
            @JsonPropertyDescription("Action name to filter by (optional)")
            String actionName,
            @JsonPropertyDescription("Input type to filter by (optional)")
            String inputTypeFilter
    ) {
        try {
            BlackboardHistory.History history = getCurrentHistory();
            if (history == null) {
                return new HistoryTraceResult("empty", List.of(), 0, null);
            }

            List<BlackboardHistory.Entry> filtered = history.entries().stream()
                    .filter(entry -> actionName == null || entry.actionName().equals(actionName))
                    .filter(entry -> inputTypeFilter == null || 
                            (entry.inputType() != null && entry.inputType().getSimpleName().equals(inputTypeFilter)))
                    .toList();

            List<HistoryEntryView> views = createEntryViews(filtered);

            return new HistoryTraceResult(
                    "success",
                    views,
                    views.size(),
                    null
            );
        } catch (Exception e) {
            return new HistoryTraceResult("error", List.of(), 0, e.getMessage());
        }
    }

    /**
     * Traverse BlackboardHistory incrementally with pagination support.
     * Enables scanning backward or forward through long workflows.
     */
    @org.springframework.ai.tool.annotation.Tool(description = "List history entries with pagination")
    public HistoryListingResult listHistory(
            @JsonPropertyDescription("Number of entries to skip (offset)")
            Integer offset,
            @JsonPropertyDescription("Maximum number of entries to return")
            Integer limit,
            @JsonPropertyDescription("Filter by time range start (ISO-8601)")
            String startTime,
            @JsonPropertyDescription("Filter by time range end (ISO-8601)")
            String endTime,
            @JsonPropertyDescription("Filter by action name")
            String actionFilter
    ) {
        try {
            BlackboardHistory.History history = getCurrentHistory();
            if (history == null) {
                return new HistoryListingResult("empty", List.of(), 0, 0, 0, false, null);
            }

            int actualOffset = offset != null ? offset : 0;
            int actualLimit = limit != null ? Math.min(limit, 100) : 50; // Default 50, max 100

            List<BlackboardHistory.Entry> filtered = history.entries().stream()
                    .filter(entry -> startTime == null || entry.timestamp().isAfter(Instant.parse(startTime)))
                    .filter(entry -> endTime == null || entry.timestamp().isBefore(Instant.parse(endTime)))
                    .filter(entry -> actionFilter == null || entry.actionName().contains(actionFilter))
                    .toList();

            List<BlackboardHistory.Entry> page = filtered.stream()
                    .skip(actualOffset)
                    .limit(actualLimit)
                    .toList();

            List<HistoryEntryView> views = createEntryViews(page);

            return new HistoryListingResult(
                    "success",
                    views,
                    filtered.size(),
                    actualOffset,
                    actualLimit,
                    (actualOffset + actualLimit) < filtered.size(),
                    null
            );
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
            @JsonPropertyDescription("Search query string")
            String query,
            @JsonPropertyDescription("Maximum number of results to return")
            Integer maxResults
    ) {
        try {
            if (!StringUtils.hasText(query)) {
                return new HistorySearchResult("error", List.of(), 0, query, "Query cannot be empty");
            }

            BlackboardHistory.History history = getCurrentHistory();
            if (history == null) {
                return new HistorySearchResult("empty", List.of(), 0, query, null);
            }

            int limit = maxResults != null ? Math.min(maxResults, 50) : 20;
            String lowerQuery = query.toLowerCase();

            List<BlackboardHistory.Entry> matches = history.entries().stream()
                    .filter(entry -> {
                        if (entry.actionName().toLowerCase().contains(lowerQuery)) return true;
                        if (entry.inputType() != null && entry.inputType().getSimpleName().toLowerCase().contains(lowerQuery))
                            return true;
                        if (entry.input() != null && entry.input().toString().toLowerCase().contains(lowerQuery))
                            return true;
                        return false;
                    })
                    .limit(limit)
                    .toList();

            List<HistoryEntryView> views = createEntryViews(matches);

            return new HistorySearchResult(
                    "success",
                    views,
                    views.size(),
                    query,
                    null
            );
        } catch (Exception e) {
            return new HistorySearchResult("error", List.of(), 0, query, e.getMessage());
        }
    }

    /**
     * Fetch a specific history entry by index.
     * Used when context creation references earlier events explicitly.
     */
    @org.springframework.ai.tool.annotation.Tool(description = "Retrieve a specific history entry by index")
    public HistoryItemResult getHistoryItem(
            @JsonPropertyDescription("Zero-based index of the entry to retrieve")
            int index
    ) {
        try {
            BlackboardHistory.History history = getCurrentHistory();
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
        } catch (Exception e) {
            return new HistoryItemResult("error", null, e.getMessage());
        }
    }

    /**
     * Persist a curated context bundle derived from multiple history entries.
     * Represents newly constructed working context with links to source history items.
     */
    @org.springframework.ai.tool.annotation.Tool(description = "Create a context snapshot from history entries")
    public ContextSnapshotResult createContextSnapshot(
            @JsonPropertyDescription("Indices of history entries to include in snapshot")
            List<Integer> entryIndices,
            @JsonPropertyDescription("Summary description of this context snapshot")
            String summary,
            @JsonPropertyDescription("Reasoning for this context construction")
            String reasoning
    ) {
        try {
            BlackboardHistory.History history = getCurrentHistory();
            if (history == null) {
                return new ContextSnapshotResult("error", null, null, 0, null, "No history available");
            }

            if (entryIndices == null || entryIndices.isEmpty()) {
                return new ContextSnapshotResult("error", null, null, 0, null, "No entry indices provided");
            }

            // Validate all indices
            for (int index : entryIndices) {
                if (index < 0 || index >= history.entries().size()) {
                    return new ContextSnapshotResult("error", null, null, 0, null,
                            String.format("Index %d out of bounds", index));
                }
            }

            String snapshotId = UUID.randomUUID().toString();
            Instant created = Instant.now();

            // Store snapshot as a note in the history
            ContextSnapshot snapshot = new ContextSnapshot(
                    snapshotId,
                    created,
                    entryIndices,
                    summary,
                    reasoning
            );

            storeSnapshot(snapshot);

            return new ContextSnapshotResult(
                    "success",
                    snapshotId,
                    created,
                    entryIndices.size(),
                    summary,
                    null
            );
        } catch (Exception e) {
            return new ContextSnapshotResult("error", null, null, 0, null, e.getMessage());
        }
    }

    /**
     * Attach notes to BlackboardHistory entries.
     * Used to explain inclusion, exclusion, minimization, or routing rationale.
     */
    @org.springframework.ai.tool.annotation.Tool(description = "Add a note/annotation to history entries")
    public HistoryNoteResult addHistoryNote(
            @JsonPropertyDescription("Indices of history entries this note references")
            List<Integer> entryIndices,
            @JsonPropertyDescription("Note content explaining reasoning or classification")
            String noteContent,
            @JsonPropertyDescription("Classification tags for the note (e.g., diagnostic, routing, exclusion)")
            List<String> tags
    ) {
        try {
            if (!StringUtils.hasText(noteContent)) {
                return new HistoryNoteResult("error", null, null, "Note content cannot be empty");
            }

            BlackboardHistory.History history = getCurrentHistory();
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

            HistoryNote note = new HistoryNote(
                    noteId,
                    created,
                    entryIndices != null ? entryIndices : List.of(),
                    noteContent,
                    tags != null ? tags : List.of()
            );

            storeNote(note);

            return new HistoryNoteResult("success", noteId, created, null);
        } catch (Exception e) {
            return new HistoryNoteResult("error", null, null, e.getMessage());
        }
    }

    // ========== Helper Methods ==========

    private BlackboardHistory.History getCurrentHistory() {
        // In actual implementation, this would retrieve from OperationContext
        // For now, return null to indicate no history - subclasses can override
        return null;
    }

    private List<HistoryEntryView> createEntryViews(List<BlackboardHistory.Entry> entries) {
        return entries.stream()
                .map(entry -> createEntryView(entry, entries.indexOf(entry)))
                .collect(Collectors.toList());
    }

    private HistoryEntryView createEntryView(BlackboardHistory.Entry entry, int index) {
        String inputSummary = entry.input() != null 
                ? truncate(entry.input().toString(), 200) 
                : "null";
        
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

    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    private List<String> getNotesForEntry(int index) {
        // Placeholder - actual implementation would retrieve stored notes
        return List.of();
    }

    private void storeSnapshot(ContextSnapshot snapshot) {
        // Placeholder - actual implementation would store in context or repository
    }

    private void storeNote(HistoryNote note) {
        // Placeholder - actual implementation would store in context or repository
    }

    // ========== Data Classes for Storage ==========

    /**
     * Represents a curated context snapshot
     */
    public record ContextSnapshot(
            String id,
            Instant created,
            List<Integer> entryIndices,
            String summary,
            String reasoning
    ) {}

    /**
     * Represents a note attached to history
     */
    public record HistoryNote(
            String id,
            Instant created,
            List<Integer> referencedEntryIndices,
            String content,
            List<String> tags
    ) {}
}
