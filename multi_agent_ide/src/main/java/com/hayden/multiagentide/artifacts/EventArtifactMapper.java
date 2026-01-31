package com.hayden.multiagentide.artifacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactHashing;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.MessageStreamArtifact;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Maps GraphEvent instances to EventArtifact nodes.
 * 
 * All GraphEvent variants are captured as EventArtifact nodes in the execution tree
 * to enable full replay and debugging.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventArtifactMapper {
    
    private final ObjectMapper objectMapper;
    
    /**
     * Maps a GraphEvent to an EventArtifact.
     * 
     * @param event The source GraphEvent
     * @param parentKey The parent artifact key to nest under
     * @return The mapped EventArtifact
     */
    public Artifact.EventArtifact mapToEventArtifact(Events.GraphEvent event, ArtifactKey parentKey) {
        ArtifactKey artifactKey = parentKey.createChild(event.timestamp());
        
        Map<String, Object> payload = eventToPayload(event);
        String contentHash = ArtifactHashing.hashJson(payload);
        
        return Artifact.EventArtifact.builder()
                .artifactKey(artifactKey)
                .eventId(event.eventId())
                .eventTimestamp(event.timestamp())
                .eventType(event.eventType())
                .payloadJson(payload)
                .hash(contentHash)
                .metadata(Map.of())
                .children(List.of())
                .build();
    }
    
    /**
     * Maps stream-related events to MessageStreamArtifact.
     */
    public MessageStreamArtifact mapToStreamArtifact(Events.GraphEvent event, ArtifactKey parentKey) {
        if (!isStreamEvent(event)) {
            throw new IllegalArgumentException("Event is not a stream event: " + event.eventType());
        }
        
        ArtifactKey artifactKey = parentKey.createChild(event.timestamp());
        Map<String, Object> payload = eventToPayload(event);
        String contentHash = ArtifactHashing.hashJson(payload);
        
        MessageStreamArtifact.StreamType streamType = mapStreamType(event);
        
        return MessageStreamArtifact.builder()
                .artifactKey(artifactKey)
                .streamType(streamType)
                .nodeId(event.nodeId())
                .eventTimestamp(event.timestamp())
                .payloadJson(payload)
                .hash(contentHash)
                .metadata(Map.of())
                .children(List.of())
                .build();
    }
    
    /**
     * Checks if the event is a stream-related event that should be mapped to MessageStreamArtifact.
     */
    public boolean isStreamEvent(Events.GraphEvent event) {
        return event instanceof Events.NodeStreamDeltaEvent
                || event instanceof Events.NodeThoughtDeltaEvent
                || event instanceof Events.UserMessageChunkEvent
                || event instanceof Events.AddMessageEvent;
    }
    
    /**
     * Checks if the event should be captured as an artifact.
     */
    public boolean shouldCapture(Events.GraphEvent event) {
        // Capture all non-artifact events (ArtifactEvent is handled separately)
        return !(event instanceof Events.ArtifactEvent);
    }
    
    // ========== Event Type Mapping ==========
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> eventToPayload(Events.GraphEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("Failed to convert event to payload: {}", event, e);
            return Map.of(
                    "eventType", event.eventType(),
                    "eventId", event.eventId(),
                    "error", "Failed to serialize: " + e.getMessage()
            );
        }
    }
    
    private MessageStreamArtifact.StreamType mapStreamType(Events.GraphEvent event) {
        return switch (event) {
            case Events.NodeStreamDeltaEvent ignored -> MessageStreamArtifact.StreamType.NODE_STREAM_DELTA;
            case Events.NodeThoughtDeltaEvent ignored -> MessageStreamArtifact.StreamType.NODE_THOUGHT_DELTA;
            case Events.UserMessageChunkEvent ignored -> MessageStreamArtifact.StreamType.USER_MESSAGE_CHUNK;
            case Events.AddMessageEvent ignored -> MessageStreamArtifact.StreamType.ADD_MESSAGE;
            default -> throw new IllegalArgumentException("Unknown stream event type: " + event.getClass());
        };
    }
    
    // ========== Specific Event Mappers ==========
    
    /**
     * Maps a ToolCallEvent to a ToolCallArtifact.
     */
    public Artifact.ToolCallArtifact mapToolCallEvent(Events.ToolCallEvent event, ArtifactKey parentKey) {
        ArtifactKey artifactKey = parentKey.createChild(event.timestamp());
        
        String inputJson = serializeToJson(event.rawInput());
        String outputJson = serializeToJson(event.rawOutput());
        
        String inputHash = inputJson != null ? ArtifactHashing.hashText(inputJson) : null;
        String outputHash = outputJson != null ? ArtifactHashing.hashText(outputJson) : null;
        
        return Artifact.ToolCallArtifact.builder()
                .artifactKey(artifactKey)
                .toolCallId(event.toolCallId())
                .toolName(event.title())
                .inputJson(inputJson)
                .inputHash(inputHash)
                .outputJson(outputJson)
                .outputHash(outputHash)
                .metadata(Map.of(
                        "kind", event.kind() != null ? event.kind() : "",
                        "status", event.status() != null ? event.status() : "",
                        "phase", event.phase() != null ? event.phase() : ""
                ))
                .children(List.of())
                .build();
    }
    
    /**
     * Maps an OutcomeEvidence-related event.
     */
    public Artifact.OutcomeEvidenceArtifact mapOutcomeEvent(
            String evidenceType, 
            Object payload, 
            ArtifactKey parentKey) {
        
        ArtifactKey artifactKey = parentKey.createChild();
        String payloadJson = serializeToJson(payload);
        String contentHash = payloadJson != null ? ArtifactHashing.hashText(payloadJson) : null;
        
        return Artifact.OutcomeEvidenceArtifact.builder()
                .artifactKey(artifactKey)
                .evidenceType(evidenceType)
                .payload(payloadJson)
                .hash(contentHash)
                .metadata(Map.of())
                .children(List.of())
                .build();
    }
    
    private String serializeToJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize object to JSON: {}", e.getMessage());
            return null;
        }
    }
}
