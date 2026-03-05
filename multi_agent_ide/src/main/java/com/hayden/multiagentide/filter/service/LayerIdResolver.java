package com.hayden.multiagentide.filter.service;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.filter.repository.LayerEntity;
import com.hayden.multiagentide.filter.repository.LayerRepository;
import com.hayden.multiagentidelib.prompt.PromptContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves runtime context (agent type, node ID, controller name) to the
 * appropriate filter layer ID from the filter_layer table.
 */
@Service
@RequiredArgsConstructor
public class LayerIdResolver {

    private static final String WORKFLOW_AGENT = "WORKFLOW_AGENT";
    private static final String WORKFLOW_AGENT_ACTION = "WORKFLOW_AGENT_ACTION";
    private static final String CONTROLLER = "CONTROLLER";
    private static final String CONTROLLER_UI_EVENT_POLL = "CONTROLLER_UI_EVENT_POLL";

    private final LayerRepository layerRepository;

    /**
     * Resolve layerId for a prompt contributor context.
     * Tries WORKFLOW_AGENT (by agent type name) first, then WORKFLOW_AGENT_ACTION (by node ID).
     */
    public Optional<String> resolveForPromptContributor(PromptContext ctx) {
        Optional<String> explicitLayerId = resolveFromMetadata(ctx == null ? null : ctx.metadata(), "layerId");
        if (explicitLayerId.isPresent()) {
            return explicitLayerId;
        }
        Optional<String> sessionLayerId = resolveForSession(resolveRawMetadata(ctx == null ? null : ctx.metadata(), "sessionId"));
        if (sessionLayerId.isPresent()) {
            return sessionLayerId;
        }
        if (ctx == null) {
            return Optional.empty();
        }
        String agentType = ctx.agentType() != null ? ctx.agentType().name() : null;
        String nodeId = ctx.currentContextId() != null ? ctx.currentContextId().value() : null;
        return resolveByTypeAndKey(WORKFLOW_AGENT_ACTION, nodeId)
                .or(() -> resolveByTypeAndKey(WORKFLOW_AGENT, agentType));
    }

    /**
     * Resolve layerId for a controller context.
     * Tries CONTROLLER first, then CONTROLLER_UI_EVENT_POLL.
     */
    public Optional<String> resolveForController(String controllerId) {
        return resolveByTypeAndKey(CONTROLLER, controllerId)
                .or(() -> resolveByTypeAndKey(CONTROLLER_UI_EVENT_POLL, controllerId));
    }

    /**
     * Resolve layerId from a session-scoped context.
     */
    public Optional<String> resolveForSession(String sessionId) {
        return resolveByTypeAndKey(CONTROLLER_UI_EVENT_POLL, sessionId);
    }

    /**
     * Resolve layerId for a graph event using controller/session/node fallbacks.
     */
    public Optional<String> resolveForGraphEvent(Events.GraphEvent event) {
        if (event == null) {
            return Optional.empty();
        }
        return resolveForGraphEvent(null, event);
    }

    /**
     * Resolve layerId for a graph event with a controller override.
     */
    public Optional<String> resolveForGraphEvent(String controllerId, Events.GraphEvent event) {
        if (event == null) {
            return resolveForController(controllerId);
        }
        return resolveForGraphEvent(controllerId, extractSessionId(event), event.nodeId());
    }

    /**
     * Resolve layerId for a graph event based on provided context pieces.
     */
    public Optional<String> resolveForGraphEvent(String controllerId, String sessionId, String nodeId) {
        return resolveForController(controllerId)
                .or(() -> resolveForSession(sessionId))
                .or(() -> resolveByTypeAndKey(WORKFLOW_AGENT_ACTION, nodeId));
    }

    private Optional<String> resolveByTypeAndKey(String type, String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return layerRepository.findByLayerTypeAndLayerKey(type, key)
                .map(LayerEntity::getLayerId);
    }

    private Optional<String> resolveFromMetadata(Map<String, Object> metadata, String key) {
        String raw = resolveRawMetadata(metadata, key);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return layerRepository.findByLayerId(raw).map(LayerEntity::getLayerId);
    }

    private String resolveRawMetadata(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        String asString = String.valueOf(value).trim();
        return asString.isEmpty() ? null : asString;
    }

    private String extractSessionId(Events.GraphEvent event) {
        for (String methodName : new String[]{"sessionId", "chatSessionId"}) {
            try {
                Method method = event.getClass().getMethod(methodName);
                Object value = method.invoke(event);
                if (value instanceof String sessionId && !sessionId.isBlank()) {
                    return sessionId;
                }
            } catch (Exception ignored) {
                // best-effort extraction for mixed event types
            }
        }
        return null;
    }
}
