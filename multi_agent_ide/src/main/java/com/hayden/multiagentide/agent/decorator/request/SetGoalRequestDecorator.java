package com.hayden.multiagentide.agent.decorator.request;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SetGoalRequestDecorator implements DispatchedAgentRequestDecorator {
    @Override
    public int order() {
        return -50_000;
    }

    public record GoalState(AgentModels.AgentRequest agentRequest, String goal) {}

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        if (request instanceof AgentModels.OrchestratorRequest
                && context.lastRequest() == null) {
            log.info("Last request was null - must be starting orchestrator request.");
            return request;
        }
        if (request instanceof AgentModels.CommitAgentRequest ca)
            return request;
        if (request instanceof AgentModels.MergeConflictRequest mcr)
            return request;

        var goal
                = BlackboardHistory.getEntireBlackboardHistory(context.operationContext())
                        .fromHistory(h -> h.getEntriesOfTypeOrSuper(AgentModels.AgentRequest.class)
                                .stream()
                                .map(ar -> {
                                    switch(ar) {
                                        case AgentModels.ContextManagerRequest contextManagerRequest -> {
                                            return new GoalState(ar, contextManagerRequest.goal());
                                        }
                                        case AgentModels.ContextManagerRoutingRequest ctx -> {
                                            return new GoalState(ar, "");
                                        }
                                        case AgentModels.DiscoveryAgentRequest ctx -> {
                                            return new GoalState(ctx, ctx.goal());
                                        }
                                        case AgentModels.DiscoveryAgentRequests ctx -> {
                                            return new GoalState(ctx, ctx.goal());
                                        }
                                        case AgentModels.DiscoveryCollectorRequest ctx -> {
                                            return new GoalState(ctx, ctx.goal());
                                        }
                                        case AgentModels.DiscoveryOrchestratorRequest ctx -> {
                                            return new GoalState(ctx, ctx.goal());
                                        }
                                        case AgentModels.InterruptRequest ctx -> {
                                            return new GoalState(ctx, "");
                                        }
                                        case AgentModels.MergerRequest ctx -> {
                                            return new GoalState(ctx, "");
                                        }
                                        case AgentModels.OrchestratorCollectorRequest ctx -> {
                                            return new GoalState(ctx, ctx.goal());
                                        }
                                        case AgentModels.OrchestratorRequest ctx -> {
                                            return new GoalState(ctx, ctx.goal());
                                        }
                                        case AgentModels.PlanningAgentRequest ctx -> {
                                            return new GoalState(ctx, ctx.goal());
                                        }
                                        case AgentModels.PlanningAgentRequests ctx -> {
                                            return new GoalState(ctx, ctx.goal());
                                        }
                                        case AgentModels.PlanningCollectorRequest ctx -> {
                                            return new GoalState(ctx, ctx.goal());
                                        }
                                        case AgentModels.PlanningOrchestratorRequest ctx -> {
                                            return new GoalState(ctx, ctx.goal());
                                        }
                                        case AgentModels.ResultsRequest ctx -> {
                                            return new GoalState(ctx, "");
                                        }
                                        case AgentModels.ReviewRequest ctx -> {
                                            return new GoalState(ctx, "");
                                        }
                                        case AgentModels.TicketAgentRequest ctx -> {
                                            return new GoalState(ctx, "");
                                        }
                                        case AgentModels.CommitAgentRequest ctx -> {
                                            return new GoalState(ctx, ctx.goal());
                                        }
                                        case AgentModels.MergeConflictRequest ctx -> {
                                            return new GoalState(ctx, ctx.goal());
                                        }
                                        case AgentModels.TicketAgentRequests ctx -> {
                                            return new GoalState(ctx, ctx.goal());
                                        }
                                        case AgentModels.TicketCollectorRequest ctx -> {
                                            return new GoalState(ctx, ctx.goal());
                                        }
                                        case AgentModels.TicketOrchestratorRequest ctx -> {
                                            return new GoalState(ctx, ctx.goal());
                                        }
                                    }
                                })
                                .filter(s -> StringUtils.isNotBlank(s.goal))
                                .toList());

        goal = goal.stream()
                .collect(Collectors.groupingBy(s -> s.goal))
                .values()
                .stream()
                .map(List::getLast)
                .toList();

        if (goal.size() == 1) {
            request = (T) request.withGoal(goal.getFirst().goal);
        } else if (!goal.isEmpty()) {

            var g = """
                    # Current Goal
                    
                    Goal: %s
                    
                    The goal may have changed a bit since we initialized.
 
                    Here is the original goal: %s
                    """
                    .formatted(goal.getLast().goal, goal.getFirst().goal);
            request = (T) request.withGoal(g);
        }

        var orchestratorRequest
                = BlackboardHistory.getLastFromHistory(context.operationContext(), AgentModels.OrchestratorRequest.class);

        if (orchestratorRequest != null) {
            request = (T) request.withPhase(resolvePhase(orchestratorRequest, context));
            return request;
        }

        return request;
    }

    private String resolvePhase(AgentModels.OrchestratorRequest orchestratorRequest, DecoratorContext context) {
        // Use the explicit phase from the orchestrator request if available
        if (orchestratorRequest.phase() != null && !orchestratorRequest.phase().isBlank()) {
            return orchestratorRequest.phase();
        }

        // Infer phase from the most recent request types in blackboard history
        return BlackboardHistory.getEntireBlackboardHistory(context.operationContext())
                .fromHistory(hist -> {

                    for (var r : hist.entries().reversed()) {
                        if (r.input() instanceof AgentModels.AgentRequest agentRequest) {
                            switch(agentRequest) {
                                case AgentModels.DiscoveryOrchestratorRequest d -> {
                                    return "DISCOVERY";
                                }
                                case AgentModels.TicketOrchestratorRequest d -> {
                                    return "TICKET";
                                }
                                case AgentModels.PlanningOrchestratorRequest d -> {
                                    return "PLANNING";
                                }
                                default -> {

                                }
                            }
                        }
                    }

                    return "ORCHESTRATOR_ONBOARDING";
                });



    }
}
