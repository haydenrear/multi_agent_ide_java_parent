package com.hayden.multiagentide.service;

import com.hayden.acp_cdc_ai.acp.AcpSessionManager;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.topology.CommunicationTopologyConfig;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.prompt.contributor.NodeMappings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private @Nullable AgentType resolveAgentType(ArtifactKey sessionKey) {
        return graphRepository.findById(sessionKey.value())
                .map(NodeMappings::agentTypeFromNode)
                .orElse(null);
    }
}
