package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class EpisodicMemoryPromptContributor implements PromptContributor {

    @Override
    public String name() {
        return "episodic-memory";
    }

    public boolean include(PromptContext promptContext) {
        return true;
    }

    @Override
    public String contribute(PromptContext context) {
        return template();
    }

    @Override
    public String template() {
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
