package com.hayden.multiagentidelib.prompt;

import com.hayden.multiagentidelib.agent.AgentType;

import java.util.Set;

public interface PromptContributor {

    String name();

    Set<AgentType> applicableAgents();

    String contribute(PromptContext context);

    int priority();

    default boolean isApplicable(AgentType agentType) {
        if (agentType == null) {
            return false;
        }
        Set<AgentType> targets = applicableAgents();
        return targets != null && (targets.contains(agentType) || targets.contains(AgentType.ALL));
    }
}
