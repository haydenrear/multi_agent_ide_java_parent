package com.hayden.multiagentidelib.prompt;

import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.ContextId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ContextIdService {

    private final Map<String, AtomicInteger> sequenceCounters = new ConcurrentHashMap<>();

    public ContextId generate(String workflowRunId, AgentType agentType) {
        String normalized = normalizeWorkflowRunId(workflowRunId);
        String agentKey = agentType != null ? agentType.name() : "UNKNOWN";
        String key = normalized + "/" + agentKey;
        int sequence = sequenceCounters
                .computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();
        return new ContextId(normalized, agentType, sequence, Instant.now());
    }

    private String normalizeWorkflowRunId(String workflowRunId) {
        if (workflowRunId == null || workflowRunId.isBlank()) {
            return "wf-unknown";
        }
        return workflowRunId.startsWith("wf-") ? workflowRunId : "wf-" + workflowRunId;
    }
}
