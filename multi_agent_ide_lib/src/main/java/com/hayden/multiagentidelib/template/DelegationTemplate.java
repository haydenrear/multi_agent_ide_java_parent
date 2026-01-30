package com.hayden.multiagentidelib.template;

import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.ContextId;

import java.util.List;
import java.util.Map;

public interface DelegationTemplate {

    String schemaVersion();

    ContextId resultId();

    ContextId upstreamContextId();

    String goal();

    String delegationRationale();

    List<AgentAssignment> assignments();

    List<ContextSelection> contextSelections();

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
            ContextId sourceContextId,
            String selectedContent,
            String selectionRationale
    ) {
    }
}
