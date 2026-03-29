package com.hayden.multiagentide.support;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentidelib.model.nodes.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

/**
 * Writes graph snapshots and event streams to markdown files for test trace review.
 *
 * <p>Designed to be called from {@link QueuedLlmRunner} after each LLM call
 * and from {@link TestEventListener} on each event. Produces two files:
 * <ul>
 *   <li>{@code *.graph.md} — full graph snapshot after each LLM call</li>
 *   <li>{@code *.events.md} — streaming event log as events fire</li>
 * </ul>
 */
@Slf4j
public class TestTraceWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Getter @Setter
    private Path graphLogFile;

    @Getter @Setter
    private Path eventLogFile;

    @Getter @Setter
    private String testClassName;

    @Getter @Setter
    private String testMethodName;

    private boolean graphHeaderWritten = false;
    private boolean eventHeaderWritten = false;
    private int eventIndex = 0;

    public void clear() {
        graphLogFile = null;
        eventLogFile = null;
        testClassName = null;
        testMethodName = null;
        graphHeaderWritten = false;
        eventHeaderWritten = false;
        eventIndex = 0;
    }

    // ── Graph Snapshots ──────────────────────────────────────────────────

    /**
     * Append a full graph snapshot to the graph log file.
     * Call this after each LLM call completes.
     */
    public void appendGraphSnapshot(int callIndex, String templateName, GraphRepository graphRepository) {
        if (graphLogFile == null || graphRepository == null) {
            return;
        }
        try {
            Files.createDirectories(graphLogFile.getParent());
            if (!graphHeaderWritten) {
                writeHeader(graphLogFile, "Graph Snapshot Log");
                graphHeaderWritten = true;
            }

            List<GraphNode> allNodes = graphRepository.findAll();

            StringBuilder sb = new StringBuilder();
            sb.append("## After Call %d: `%s`\n\n".formatted(callIndex, templateName));
            sb.append("**Total nodes**: %d\n\n".formatted(allNodes.size()));

            if (allNodes.isEmpty()) {
                sb.append("_(no nodes)_\n\n");
            } else {
                // Summary table
                sb.append("| # | Type | NodeId | Status | Parent | Title |\n");
                sb.append("|---|------|--------|--------|--------|-------|\n");
                int idx = 1;
                for (GraphNode node : allNodes) {
                    String type = node.getClass().getSimpleName();
                    String status = node.status() != null ? node.status().name() : "null";
                    String parent = node.parentNodeId() != null ? truncateId(node.parentNodeId()) : "—";
                    String title = node.title() != null ? node.title().replace("|", "\\|") : "";
                    if (title.length() > 60) title = title.substring(0, 57) + "...";
                    sb.append("| %d | `%s` | `%s` | %s | `%s` | %s |\n".formatted(
                            idx++, type, truncateId(node.nodeId()), status, parent, title));
                }
                sb.append("\n");

                // Detail sections for special node types
                for (GraphNode node : allNodes) {
                    if (node instanceof AgentToAgentConversationNode a2a) {
                        sb.append("### AgentToAgent: `%s`\n\n".formatted(truncateId(a2a.nodeId())));
                        sb.append("- source: `%s` (%s)\n".formatted(a2a.sourceAgentKey(), a2a.sourceAgentType()));
                        sb.append("- target: `%s` (%s)\n".formatted(a2a.targetAgentKey(), a2a.targetAgentType()));
                        sb.append("- callingNodeId: `%s`\n".formatted(a2a.callingNodeId()));
                        sb.append("- originatingA2ANodeId: `%s`\n".formatted(a2a.originatingAgentToAgentNodeId()));
                        sb.append("- targetNodeId: `%s`\n".formatted(a2a.targetNodeId()));
                        sb.append("- sourceSessionId: `%s`\n".formatted(a2a.sourceSessionId()));
                        sb.append("- chatId: `%s`\n".formatted(a2a.chatId()));
                        if (a2a.callChain() != null && !a2a.callChain().isEmpty()) {
                            sb.append("- callChain: %s\n".formatted(safeSerialize(a2a.callChain())));
                        }
                        sb.append("\n");
                    } else if (node instanceof DataLayerOperationNode dl) {
                        sb.append("### DataLayer: `%s`\n\n".formatted(truncateId(dl.nodeId())));
                        sb.append("- operationType: `%s`\n".formatted(dl.operationType()));
                        sb.append("- chatId: `%s`\n".formatted(dl.chatId()));
                        sb.append("\n");
                    } else if (node instanceof HasChatId hc) {
                        String chatKey = hc.chatId();
                        if (chatKey != null && !chatKey.isBlank()) {
                            sb.append("### %s: `%s` — chatId: `%s`\n\n".formatted(
                                    node.getClass().getSimpleName(), truncateId(node.nodeId()), chatKey));
                        }
                    }
                }
            }

            sb.append("---\n\n");
            Files.writeString(graphLogFile, sb.toString(), StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("Failed to write graph snapshot for call {} to {}", callIndex, graphLogFile, e);
        }
    }

    // ── Event Stream ─────────────────────────────────────────────────────

    /**
     * Append a single event to the event log file.
     * Call this from TestEventListener.onEvent().
     */
    public void appendEvent(Events.GraphEvent event) {
        if (eventLogFile == null || event == null) {
            return;
        }
        try {
            Files.createDirectories(eventLogFile.getParent());
            if (!eventHeaderWritten) {
                writeHeader(eventLogFile, "Event Stream Log");
                eventHeaderWritten = true;
            }

            eventIndex++;
            StringBuilder sb = new StringBuilder();
            sb.append("## Event %d: `%s`\n\n".formatted(eventIndex, event.getClass().getSimpleName()));
            sb.append("- timestamp: `%s`\n".formatted(event.timestamp()));
            sb.append("- nodeId: `%s`\n".formatted(event.nodeId()));

            // Type-specific fields
            appendEventDetails(sb, event);

            sb.append("\n```json\n");
            sb.append(safeSerialize(event));
            sb.append("\n```\n\n---\n\n");

            Files.writeString(eventLogFile, sb.toString(), StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("Failed to write event {} to {}", eventIndex, eventLogFile, e);
        }
    }

    private void appendEventDetails(StringBuilder sb, Events.GraphEvent event) {
        switch (event) {
            case Events.NodeAddedEvent e -> {
                sb.append("- nodeTitle: `%s`\n".formatted(e.nodeTitle()));
                sb.append("- nodeType: `%s`\n".formatted(e.nodeType()));
                sb.append("- parentNodeId: `%s`\n".formatted(e.parentNodeId()));
            }
            case Events.NodeStatusChangedEvent e -> {
                sb.append("- oldStatus: `%s`\n".formatted(e.oldStatus()));
                sb.append("- newStatus: `%s`\n".formatted(e.newStatus()));
            }
            case Events.AgentCallStartedEvent e -> {
                sb.append("- callerNodeId: `%s`\n".formatted(e.callerNodeId()));
                sb.append("- targetNodeId: `%s`\n".formatted(e.targetNodeId()));
                sb.append("- callId: `%s`\n".formatted(e.callId()));
            }
            case Events.AgentCallCompletedEvent e -> {
                sb.append("- callId: `%s`\n".formatted(e.callId()));
            }
            case Events.GoalCompletedEvent e -> {
                sb.append("- workflowId: `%s`\n".formatted(e.workflowId()));
            }
            case Events.ActionCompletedEvent e -> {
                sb.append("- agentName: `%s`\n".formatted(e.agentName()));
                sb.append("- actionName: `%s`\n".formatted(e.actionName()));
                sb.append("- outcomeType: `%s`\n".formatted(e.outcomeType()));
            }
            case Events.AgentCallEvent e -> {
                sb.append("- callEventType: `%s`\n".formatted(e.callEventType()));
                sb.append("- callerSessionId: `%s`\n".formatted(e.callerSessionId()));
                sb.append("- targetSessionId: `%s`\n".formatted(e.targetSessionId()));
                sb.append("- callerAgentType: `%s`\n".formatted(e.callerAgentType()));
                sb.append("- targetAgentType: `%s`\n".formatted(e.targetAgentType()));
                if (e.callChain() != null && !e.callChain().isEmpty()) {
                    sb.append("- callChain: %s\n".formatted(e.callChain()));
                }
                if (e.errorDetail() != null) {
                    sb.append("- errorDetail: `%s`\n".formatted(e.errorDetail()));
                }
            }
            case Events.InterruptStatusEvent e -> {
                sb.append("- interruptType: `%s`\n".formatted(e.interruptType()));
                sb.append("- interruptStatus: `%s`\n".formatted(e.interruptStatus()));
                sb.append("- originNodeId: `%s`\n".formatted(e.originNodeId()));
                sb.append("- resumeNodeId: `%s`\n".formatted(e.resumeNodeId()));
            }
            default -> {
                // No extra details for other event types
            }
        }
    }

    // ── Shared Helpers ───────────────────────────────────────────────────

    private void writeHeader(Path file, String title) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# QueuedLlmRunner %s\n\n".formatted(title));
            sb.append("| Field | Value |\n");
            sb.append("|-------|-------|\n");
            sb.append("| **Test class** | `%s` |\n".formatted(
                    testClassName != null ? testClassName : "unknown"));
            sb.append("| **Test method** | `%s` |\n".formatted(
                    testMethodName != null ? testMethodName : "unknown"));
            sb.append("| **Started at** | %s |\n".formatted(Instant.now()));
            sb.append("\n---\n\n");

            Files.writeString(file, sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            log.warn("Failed to write header to {}", file, e);
        }
    }

    private String truncateId(String id) {
        if (id == null) return "null";
        // Show first ak: segment + last 8 chars for readability
        if (id.length() > 30) {
            return id.substring(0, 12) + "…" + id.substring(id.length() - 8);
        }
        return id;
    }

    private String safeSerialize(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{ \"_serializationError\": \"" + e.getMessage().replace("\"", "'") + "\" }";
        }
    }
}
