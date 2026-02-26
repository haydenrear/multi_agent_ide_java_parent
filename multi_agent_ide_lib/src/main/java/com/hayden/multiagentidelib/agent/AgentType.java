package com.hayden.multiagentidelib.agent;

import java.util.Locale;

public enum AgentType {
    ALL,
    ORCHESTRATOR,
    ORCHESTRATOR_COLLECTOR,
    DISCOVERY_ORCHESTRATOR,
    DISCOVERY_AGENT,
    DISCOVERY_AGENT_DISPATCH,
    DISCOVERY_COLLECTOR,
    PLANNING_ORCHESTRATOR,
    PLANNING_AGENT,
    PLANNING_AGENT_DISPATCH,
    PLANNING_COLLECTOR,
    TICKET_ORCHESTRATOR,
    TICKET_AGENT,
    TICKET_AGENT_DISPATCH,
    TICKET_COLLECTOR,
    COMMIT_AGENT,
    MERGE_CONFLICT_AGENT,
    REVIEW_AGENT,
    REVIEW_RESOLUTION_AGENT,
    MERGER_AGENT,
    CONTEXT_MANAGER;

    public String wireValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static AgentType fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return AgentType.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
