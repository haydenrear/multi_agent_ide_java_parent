package com.hayden.multiagentidelib.agent;

import java.time.Instant;

public record ContextId(
        String workflowRunId,
        AgentType agentType,
        int sequenceNumber,
        Instant timestamp
) {


    public ContextId(String workflowRunId, AgentType agentType, int sequenceNumber) {
        this(workflowRunId, agentType, sequenceNumber, Instant.now());
    }

    public String toStringId() {
        String agent = agentType != null ? agentType.wireValue() : "unknown";
        return workflowRunId + "/" + agent + "/" + String.format("%03d", sequenceNumber) + "/" + timestamp;
    }

    @Override
    public String toString() {
        return toStringId();
    }
}
