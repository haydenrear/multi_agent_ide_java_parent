package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * Prompt contributor that provides guidance for agents that can route to the Context Manager.
 * This contributor is dynamically created by {@link ContextManagerRoutingPromptContributorFactory}
 * based on whether the current agent's routing type includes a ContextManagerRoutingRequest field.
 */
@RequiredArgsConstructor
public class ContextManagerRoutingPromptContributor implements PromptContributor {

    private static final String TEMPLATE = """
            ## Context Manager Routing Option

            You have the ability to route to the **Context Manager** agent. The Context Manager has
            access to tools that can query and retrieve the shared state across all agents in the
            workflow. This includes the blackboard history, artifact store, and execution traces
            from previous agent invocations.

            ### When to Route to Context Manager

            Route to Context Manager by populating `contextManagerRequest` in your routing response when
            you need more context from a previous agent's execution:

            - **Missing execution details** - You need to understand what a previous agent discovered,
              decided, or produced, but that information was not passed forward in the request
            - **Incomplete handoff** - The upstream agent's results are truncated or summarized, and
              you need the full details to proceed
            - **Cross-phase context** - You need information from an earlier workflow phase (e.g.,
              discovery findings while in planning, or planning decisions while executing tickets)
            - **Artifact retrieval** - You need to access artifacts, curations, or intermediate results
              that were produced but not included in your current request

            ### Context Manager Capabilities

            The Context Manager can:
            1. Query the blackboard history to retrieve previous agent requests and results
            2. Access the artifact store to find curations, code maps, and other persisted data
            3. Trace the execution path to understand how the workflow reached the current state
            4. Reconstruct context from multiple sources and consolidate it for your use

            ### Important

            - Only route to Context Manager when you genuinely need context from previous execution
            - Be specific about what information you need so the Context Manager can retrieve it efficiently
            - The Context Manager will reconstruct the needed context and route back to continue your work
            """;

    @Override
    public String name() {
        return "context-manager-routing";
    }

    @Override
    public boolean include(PromptContext promptContext) {
        return true;
    }

    @Override
    public String contribute(PromptContext context) {
        return template();
    }

    @Override
    public String template() {
        return TEMPLATE;
    }

    @Override
    public int priority() {
        return 50;
    }
}
