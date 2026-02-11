package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;

import java.util.Optional;

/**
 * Utility for resolving where the context manager should route back to, based on the
 * blackboard history. Delegates to {@link NodeMappings} for the type-to-field-name mapping.
 *
 * <p>Used by both {@link ContextManagerReturnRoutePromptContributorFactory} and
 * {@link InterruptLoopBreakerPromptContributorFactory}.</p>
 */
public final class ContextManagerReturnRoutes {

    private ContextManagerReturnRoutes() {}

    /**
     * Resolved return route: the field name and display name for routing back.
     */
    public record ReturnRouteMapping(
            Class<?> requestType,
            String fieldName,
            String displayName
    ) {}

    public static ReturnRouteMapping findMapping(Class<?> requestType) {
        NodeMappings.NodeMapping mapping = NodeMappings.findByType(requestType);
        if (mapping != null && mapping.fieldName() != null) {
            return new ReturnRouteMapping(mapping.requestType(), mapping.fieldName(), mapping.displayName());
        }
        return null;
    }

    /**
     * Finds the last non-context-manager, non-interrupt request from the blackboard history.
     */
    public static AgentModels.AgentRequest findLastNonContextManagerRequest(BlackboardHistory history) {
        if (history == null) {
            return null;
        }
        return history.getValue(entry -> {
            Object input = switch (entry) {
                case BlackboardHistory.DefaultEntry defaultEntry -> defaultEntry.input();
                case BlackboardHistory.MessageEntry ignored -> null;
            };
            if (input instanceof AgentModels.AgentRequest agentRequest
                    && !(agentRequest instanceof AgentModels.ContextManagerRoutingRequest)
                    && !(agentRequest instanceof AgentModels.ContextManagerRequest)
                    && !(agentRequest instanceof AgentModels.InterruptRequest)) {
                return Optional.of(agentRequest);
            }
            return Optional.empty();
        }).orElse(null);
    }

    /**
     * Resolves the specific field name and display name for the context manager to route back to,
     * based on the blackboard history.
     *
     * @return the mapping for the last active agent, or {@code null} if no agent could be identified
     */
    public static ReturnRouteMapping resolveReturnRoute(BlackboardHistory history) {
        AgentModels.AgentRequest lastAgent = findLastNonContextManagerRequest(history);
        if (lastAgent == null) {
            return null;
        }
        return findMapping(lastAgent.getClass());
    }
}
