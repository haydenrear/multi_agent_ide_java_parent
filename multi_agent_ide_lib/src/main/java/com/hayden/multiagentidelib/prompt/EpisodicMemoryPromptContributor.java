package com.hayden.multiagentidelib.prompt;

import com.hayden.multiagentidelib.agent.AgentType;

import java.util.Set;

public class EpisodicMemoryPromptContributor implements PromptContributor {

    @Override
    public String name() {
        return "episodic-memory";
    }

    @Override
    public Set<AgentType> applicableAgents() {
        return Set.of(AgentType.ALL);
    }

    @Override
    public String contribute(PromptContext context) {
        return """
            ## Episodic Memory Tool

            You have access to episodic memory with three operations:
            - retain: store a new episode with context and metadata.
            - reflect: connect this episode to related memories and explain why it matters.
            - recall: retrieve relevant prior episodes before starting new work.

            Guidance:
            1) Recall before starting a new task or major decision so you reuse prior context.
            2) Retain after meaningful discoveries, decisions, or changes.
            3) Reflect after significant actions to solidify connections between memories.

            Reflection is critical: it builds the knowledge graph links that make future recall useful.
            So when you are engaging in an episode, you retain a memory with a context string and something to save.
            The context argument provides a tag for how to organize this memory. However, it is then upon reflecting
            that the retained memory is percolated and connected into the knowledge graph for this and future episodes,
            and therefore you'll need to engage in this process to make sure that the memory is properly attributed
            in the knowledge graph that is the memory tool.
            """;
    }

    @Override
    public int priority() {
        return 100;
    }
}
