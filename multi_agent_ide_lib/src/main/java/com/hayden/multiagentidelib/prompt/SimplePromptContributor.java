package com.hayden.multiagentidelib.prompt;

import com.hayden.multiagentidelib.agent.AgentType;

import java.util.Set;

public final class SimplePromptContributor implements PromptContributor {

    private final String name;
    private final String contribution;
    private final Set<AgentType> applicableAgents;
    private final int priority;

    public SimplePromptContributor(
            String name,
            String contribution,
            Set<AgentType> applicableAgents,
            int priority
    ) {
        this.name = name;
        this.contribution = contribution;
        this.applicableAgents = applicableAgents != null ? applicableAgents : Set.of(AgentType.ALL);
        this.priority = priority;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Set<AgentType> applicableAgents() {
        return applicableAgents;
    }

    @Override
    public String contribute(PromptContext context) {
        return contribution;
    }

    @Override
    public int priority() {
        return priority;
    }
}
