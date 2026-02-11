package com.hayden.multiagentidelib.prompt.contributor;

import com.google.common.collect.Lists;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Factory that creates {@link ContextManagerRoutingPromptContributor} instances
 * for agents whose routing types include a ContextManagerRoutingRequest field.
 *
 * <p>The factory matches the current request type from the PromptContext to determine
 * if the agent can route to the Context Manager. The mapping is based on analyzing
 * which Routing record types contain a ContextManagerRoutingRequest field:</p>
*/
@Component
public class ContextManagerRoutingPromptContributorFactory implements PromptContributorFactory {

    /**
     * Maps request types to their corresponding AgentType for agents that can route to Context Manager.
     */
    private static final Set<Class<? extends AgentModels.AgentRequest>> REQUEST_TO_AGENT_TYPE = Set.of(
            AgentModels.DiscoveryOrchestratorRequest.class,
            AgentModels.DiscoveryAgentRequests.class,
            AgentModels.DiscoveryCollectorRequest.class,
            AgentModels.PlanningOrchestratorRequest.class,
            AgentModels.PlanningAgentRequests.class,
            AgentModels.PlanningCollectorRequest.class,
            AgentModels.TicketOrchestratorRequest.class,
            AgentModels.TicketAgentRequests.class,
            AgentModels.TicketCollectorRequest.class,
            AgentModels.OrchestratorCollectorRequest.class
    );

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        if (context.blackboardHistory().copyOfEntries().size() < 3) {
            return new ArrayList<>();
        }

        if (REQUEST_TO_AGENT_TYPE.contains(context.currentRequest().getClass()))
            return Lists.newArrayList(new ContextManagerRoutingPromptContributor());

        return new ArrayList<>();
    }

}
