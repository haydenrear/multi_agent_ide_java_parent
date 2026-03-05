package com.hayden.multiagentide.filter.service;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentidelib.filter.model.executor.AiFilterTool;
import com.hayden.multiagentidelib.model.nodes.ExecutionNode;
import com.hayden.multiagentidelib.prompt.PromptContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves AI filter session keys according to configured scope and evicts cached sessions on lifecycle events.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AiFilterSessionResolver implements EventListener {

    private record SessionScopeKey(String policyId,
                                   AiFilterTool.SessionMode sessionMode,
                                   String scopeNodeId,
                                   String scopeQualifier,
                                   String rootNodeId) {
    }

    private record ResolvedScope(ArtifactKey sessionParentKey, SessionScopeKey sessionScopeKey) {
    }

    private final GraphRepository graphRepository;
    private EventBus eventBus;

    private final ConcurrentHashMap<SessionScopeKey, ArtifactKey> sessionCache = new ConcurrentHashMap<>();

    public ArtifactKey resolveSessionKey(String policyId,
                                         AiFilterTool.SessionMode sessionMode,
                                         PromptContext promptContext) {
        ArtifactKey currentKey = promptContext == null ? null : promptContext.currentContextId();
        if (currentKey == null) {
            return ArtifactKey.createRoot();
        }

        AiFilterTool.SessionMode effectiveMode = sessionMode == null
                ? AiFilterTool.SessionMode.PER_INVOCATION
                : sessionMode;

        if (effectiveMode == AiFilterTool.SessionMode.PER_INVOCATION) {
            ArtifactKey created = currentKey.createChild();
            publishSessionCreatedEvent(
                    new SessionScopeKey(
                            policyId == null ? "<unknown-policy>" : policyId,
                            effectiveMode,
                            currentKey.value(),
                            resolveActionQualifier(promptContext),
                            resolveExecutionRoot(currentKey).value()
                    ),
                    currentKey,
                    created
            );
            return created;
        }

        ResolvedScope resolvedScope = resolveScope(
                policyId == null ? "<unknown-policy>" : policyId,
                effectiveMode,
                promptContext,
                currentKey
        );

        return sessionCache.computeIfAbsent(
                resolvedScope.sessionScopeKey(),
                ignored -> {
                    ArtifactKey created = resolvedScope.sessionParentKey().createChild();
                    publishSessionCreatedEvent(
                            resolvedScope.sessionScopeKey(),
                            resolvedScope.sessionParentKey(),
                            created
                    );
                    return created;
                }
        );
    }

    private ResolvedScope resolveScope(String policyId,
                                       AiFilterTool.SessionMode sessionMode,
                                       PromptContext promptContext,
                                       ArtifactKey currentKey) {
        ArtifactKey rootKey = resolveExecutionRoot(currentKey);
        return switch (sessionMode) {
            case PER_INVOCATION -> throw new IllegalStateException("PER_INVOCATION is handled before scope resolution");

            case SAME_SESSION_FOR_ALL -> new ResolvedScope(
                    rootKey,
                    new SessionScopeKey(
                            policyId,
                            sessionMode,
                            rootKey.value(),
                            "ALL",
                            rootKey.value()
                    )
            );

            case SAME_SESSION_FOR_ACTION -> {
                ArtifactKey actionScope = resolveActionScope(currentKey);
                yield new ResolvedScope(
                        actionScope,
                        new SessionScopeKey(
                                policyId,
                                sessionMode,
                                actionScope.value(),
                                resolveActionQualifier(promptContext),
                                rootKey.value()
                        )
                );
            }

            case SAME_SESSION_FOR_AGENT -> {
                ArtifactKey agentScope = resolveAgentScope(currentKey);
                yield new ResolvedScope(
                        agentScope,
                        new SessionScopeKey(
                                policyId,
                                sessionMode,
                                agentScope.value(),
                                resolveAgentQualifier(currentKey),
                                rootKey.value()
                        ));
            }
        };
    }

    private ArtifactKey resolveActionScope(ArtifactKey currentKey) {
        ArtifactKey nearestExecution = findNearestExecutionNode(currentKey);
        if (nearestExecution != null) {
            return nearestExecution;
        }
        return resolveExecutionRoot(currentKey);
    }

    private ArtifactKey resolveAgentScope(ArtifactKey currentKey) {
        ArtifactKey actionScope = resolveActionScope(currentKey);
        ArtifactKey probe = actionScope.parent().orElse(null);
        while (probe != null) {
            if (isExecutionNode(probe)) {
                return probe;
            }
            probe = probe.parent().orElse(null);
        }
        return actionScope;
    }

    private ArtifactKey resolveExecutionRoot(ArtifactKey currentKey) {
        ArtifactKey rootKey = currentKey.isRoot() ? currentKey : currentKey.root();
        ArtifactKey nearestExecution = findNearestExecutionNode(currentKey);
        if (nearestExecution == null) {
            return rootKey;
        }

        ArtifactKey topExecution = nearestExecution;
        ArtifactKey probe = topExecution.parent().orElse(null);
        while (probe != null && isExecutionNode(probe)) {
            topExecution = probe;
            probe = probe.parent().orElse(null);
        }
        return topExecution;
    }

    private ArtifactKey findNearestExecutionNode(ArtifactKey currentKey) {
        ArtifactKey probe = currentKey;
        while (probe != null) {
            if (isExecutionNode(probe)) {
                return probe;
            }
            probe = probe.parent().orElse(null);
        }
        return null;
    }

    private boolean isExecutionNode(ArtifactKey key) {
        return key != null && getExecutionNode(key).isPresent();
    }

    private Optional<ExecutionNode> getExecutionNode(ArtifactKey key) {
        return graphRepository.findById(key.value())
                .flatMap(gn -> gn instanceof ExecutionNode en ? Optional.of(en): Optional.empty());
    }

    private String resolveActionQualifier(PromptContext promptContext) {
        return promptContext != null && promptContext.agentType() != null
                ? promptContext.agentType().name()
                : "UNKNOWN_AGENT";
    }

    private String resolveAgentQualifier(ArtifactKey promptContext) {
        return getExecutionNode(promptContext)
                .map(ExecutionNode::agent)
                .orElse("UNKNOWN");
    }

    @Autowired
    @Lazy
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    private void publishSessionCreatedEvent(SessionScopeKey scopeKey,
                                            ArtifactKey nodeKey,
                                            ArtifactKey sessionContextId) {
        if (eventBus == null || scopeKey == null || nodeKey == null || sessionContextId == null) {
            return;
        }
        eventBus.publish(new Events.AiFilterSessionEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeKey.value(),
                scopeKey.policyId(),
                scopeKey.sessionMode().name(),
                scopeKey.scopeNodeId(),
                scopeKey.scopeQualifier(),
                scopeKey.rootNodeId(),
                sessionContextId
        ));
    }

    @Override
    public String listenerId() {
        return "ai-filter-session-resolver";
    }

    @Override
    public boolean isInterestedIn(Events.GraphEvent eventType) {
        return eventType instanceof Events.GoalCompletedEvent
                || eventType instanceof Events.ActionCompletedEvent;
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        switch (event) {
            case Events.GoalCompletedEvent goal -> evictGoalScopedSessions(goal.nodeId());
            case Events.ActionCompletedEvent action -> evictActionScopedSessions(action.nodeId());
            default -> {
            }
        }
    }

    private void evictGoalScopedSessions(String goalNodeId) {
        if (goalNodeId == null || goalNodeId.isBlank()) {
            return;
        }
        int before = sessionCache.size();
        sessionCache.keySet().removeIf(key ->
                goalNodeId.equals(key.rootNodeId()));
        int removed = before - sessionCache.size();
        if (removed > 0) {
            log.debug("Evicted {} AI filter session entries for completed goal {}", removed, goalNodeId);
        }
    }

    private void evictActionScopedSessions(String actionNodeId) {
        if (actionNodeId == null || actionNodeId.isBlank()) {
            return;
        }
        int before = sessionCache.size();
        sessionCache.keySet().removeIf(key ->
                key.sessionMode() == AiFilterTool.SessionMode.SAME_SESSION_FOR_ACTION
                        && actionNodeId.equals(key.scopeNodeId()));
        int removed = before - sessionCache.size();
        if (removed > 0) {
            log.debug("Evicted {} AI filter session entries for completed action {}", removed, actionNodeId);
        }
    }
}
