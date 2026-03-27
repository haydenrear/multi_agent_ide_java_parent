package com.hayden.multiagentide.service;

import com.hayden.acp_cdc_ai.acp.AcpSessionManager;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.topology.CommunicationTopologyConfig;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.prompt.contributor.NodeMappings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Provides agent-to-agent communication capabilities: session discovery, topology enforcement,
 * and self-call filtering.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentCommunicationService {

    private final AcpSessionManager acpSessionManager;
    private final GraphRepository graphRepository;
    private final CommunicationTopologyConfig topologyConfig;
    private final SessionKeyResolutionService sessionKeyResolutionService;

    public record AgentAvailabilityEntry(
            String agentKey,
            String agentType,
            boolean busy,
            boolean callableByCurrentAgent
    ) {
    }

    /**
     * Result of call validation — either valid or contains an error message.
     */
    public record CallValidationResult(boolean valid, @Nullable String error) {
        public static CallValidationResult ok() { return new CallValidationResult(true, null); }
        public static CallValidationResult reject(String error) { return new CallValidationResult(false, error); }
    }

    /**
     * List all available agents that the calling agent can communicate with.
     * Returns agents filtered by: topology-permitted AND session-open AND not-self.
     */
    public @org.jspecify.annotations.NonNull List<AgentAvailabilityEntry> listAvailableAgents(@Nullable String callingSessionKey, @Nullable AgentType callingAgentType) {
        if (callingSessionKey == null || callingSessionKey.isBlank()) {
            return List.of();
        }

        ArtifactKey callingKey;
        try {
            callingKey = new ArtifactKey(callingSessionKey);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid calling session key: {}", callingSessionKey);
            return List.of();
        }

        // Collect all open session keys with their agent types
        Map<ArtifactKey, AgentType> sessionAgentTypes = new LinkedHashMap<>();
        for (Map.Entry<Object, AcpSessionManager.AcpSessionContext> entry : acpSessionManager.getSessionContexts().entrySet()) {
            ArtifactKey sessionKey = entry.getValue().getChatKey();
            if (sessionKey == null) {
                continue;
            }
            AgentType agentType = resolveAgentType(sessionKey);
            if (agentType != null) {
                sessionAgentTypes.put(sessionKey, agentType);
            }
        }

        // Filter out self-calls
        Set<ArtifactKey> filteredKeys = sessionKeyResolutionService.filterSelfCalls(callingKey, sessionAgentTypes.keySet());

        // Build result with topology check
        List<AgentAvailabilityEntry> result = new ArrayList<>();
        for (ArtifactKey key : filteredKeys) {
            AgentType targetType = sessionAgentTypes.get(key);
            boolean topologyPermitted = callingAgentType != null
                    && topologyConfig.isCommunicationAllowed(callingAgentType, targetType);

            result.add(new AgentAvailabilityEntry(
                    key.value(),
                    targetType.wireValue(),
                    false, // busy detection deferred to Phase 6
                    topologyPermitted
            ));
        }

        return result;
    }

    /**
     * Validates whether a call from caller to target is permitted.
     * Checks: topology, self-call, active call-chain cycle, max depth, session availability.
     */
    public @NonNull CallValidationResult validateCall(
            @NonNull ArtifactKey callingKey, @Nullable AgentType callingType,
            @NonNull ArtifactKey targetKey, @Nullable AgentType targetType,
            @Nullable List<AgentModels.CallChainEntry> callChain
    ) {
        // 1. Self-call check (includes shared-session and graph-derived cycle detection)
        Set<ArtifactKey> filtered = sessionKeyResolutionService.filterSelfCalls(callingKey, Set.of(targetKey), callChain);
        if (filtered.isEmpty()) {
            return CallValidationResult.reject(
                    "ERROR: Cannot call self or agent on the same active session. Call rejected.");
        }

        // 2. Topology check
        if (callingType != null && targetType != null
                && !topologyConfig.isCommunicationAllowed(callingType, targetType)) {
            return CallValidationResult.reject(
                    "ERROR: Communication not permitted by topology rules. %s cannot call %s."
                            .formatted(callingType.wireValue(), targetType.wireValue()));
        }

        // 3. Call chain depth check
        int depth = callChain != null ? callChain.size() : 0;
        if (depth >= topologyConfig.maxCallChainDepth()) {
            return CallValidationResult.reject(
                    "ERROR: Call chain depth %d exceeds maximum %d. Call rejected."
                            .formatted(depth, topologyConfig.maxCallChainDepth()));
        }

        // 4. Loop detection via call chain parameter (explicit chain from tool parameter)
        if (callChain != null && callChain.stream().anyMatch(e -> e.agentKey().equals(targetKey))) {
            String chain = formatCallChain(callChain, targetKey);
            return CallValidationResult.reject(
                    "ERROR: Loop detected in call chain: %s. Call rejected.".formatted(chain));
        }

        // 5. Target session availability
        AcpSessionManager.AcpSessionContext targetSession = findTargetSession(targetKey);
        if (targetSession == null) {
            return CallValidationResult.reject(
                    "ERROR: Agent %s is no longer available. Try an alternative route."
                            .formatted(targetKey.value()));
        }

        return CallValidationResult.ok();
    }

    /**
     * Finds the ACP session context for the given target agent key.
     */
    public @Nullable AcpSessionManager.AcpSessionContext findTargetSession(@NonNull ArtifactKey targetKey) {
        for (Map.Entry<Object, AcpSessionManager.AcpSessionContext> entry : acpSessionManager.getSessionContexts().entrySet()) {
            ArtifactKey sessionKey = entry.getValue().getChatKey();
            if (sessionKey != null && sessionKey.equals(targetKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String formatCallChain(@NonNull List<AgentModels.CallChainEntry> chain, @NonNull ArtifactKey target) {
        StringBuilder sb = new StringBuilder();
        for (AgentModels.CallChainEntry entry : chain) {
            sb.append(entry.agentKey().value()).append(" -> ");
        }
        sb.append(target.value());
        return sb.toString();
    }

    private @Nullable AgentType resolveAgentType(ArtifactKey sessionKey) {
        return graphRepository.findById(sessionKey.value())
                .map(NodeMappings::agentTypeFromNode)
                .orElse(null);
    }

    public @Nullable AgentType resolveAgentTypePublic(@NonNull ArtifactKey key) {
        return resolveAgentType(key);
    }
}
