package com.hayden.multiagentide.template;

import com.hayden.multiagentide.agent.AgentType;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;

import java.util.Map;

public interface DelegationTemplate {

    ArtifactKey contextId();

    String goal();

    String delegationRationale();

    Map<String, String> metadata();

    record AgentAssignment(
            String agentId,
            AgentType agentType,
            String assignedGoal,
            String subdomainFocus,
            Map<String, String> contextToPass
    ) {
    }

    record ContextSelection(
            String selectionId,
            ArtifactKey sourceContextId,
            String selectedContent,
            String selectionRationale
    ) {
    }
}
