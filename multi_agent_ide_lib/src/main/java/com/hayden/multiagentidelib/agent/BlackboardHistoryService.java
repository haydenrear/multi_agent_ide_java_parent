package com.hayden.multiagentidelib.agent;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BlackboardHistoryService {

    private final List<DegenerateLoopPolicy> degenerateLoopPolicies;

    public BlackboardHistoryService(List<DegenerateLoopPolicy> degenerateLoopPolicies) {
        this.degenerateLoopPolicies = degenerateLoopPolicies;
    }

    /**
     * Register an input in the blackboard history and hide it from the blackboard.
     * This overload allows enriching the input with ArtifactKey and PreviousContext before storing.
     *
     * @param context The operation context
     * @param actionName The name of the action being executed
     * @param input The input object to register and hide
     * @return Updated history with the new entry
     */
    public BlackboardHistory register(
            OperationContext context,
            String actionName,
            Artifact.AgentModel input
    ) {
        BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);

        history.addEntry(actionName, input);

        if (degenerateLoopPolicies != null && !degenerateLoopPolicies.isEmpty()) {
            for (DegenerateLoopPolicy policy : degenerateLoopPolicies) {
                if (policy == null) {
                    continue;
                }
                policy.detectLoop(history, actionName, input)
                        .ifPresent(exception -> {
                            throw exception;
                        });
            }
        }

        return history;
    }

    public BlackboardHistory hideInput(
            OperationContext context
    ) {
        BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);

        context.getAgentProcess().clear();

        context.getAgentProcess().addObject(history);

        return history;
    }
}
