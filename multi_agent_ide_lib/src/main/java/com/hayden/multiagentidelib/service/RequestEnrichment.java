package com.hayden.multiagentidelib.service;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.PreviousContext;
import com.hayden.multiagentidelib.prompt.ContextIdService;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for enriching request objects with ArtifactKey and PreviousContext.
 * Centralizes the logic for setting these fields consistently across all agent types.
 */
@Slf4j
@Service
public class RequestEnrichment {

    private final ContextIdService contextIdService;

    private final PreviousContextFactory previousContextFactory;

    public RequestEnrichment(ContextIdService contextIdService) {
        this.contextIdService = contextIdService;
        this.previousContextFactory = new PreviousContextFactory();
    }

    public <T extends Artifact.AgentModel> T enrich(T input, OperationContext context) {
        if (input == null) {
            return null;
        }

        BlackboardHistory history = null;

        if (context != null) {
            history = context.getAgentProcess().getBlackboard().last(BlackboardHistory.class);
        }

        if (input.key() != null)
            log.error("Found input without key.");

        Artifact.AgentModel parent = findParentForInput(input, history);
        T enrich = enrich(input, context, parent);
        return enrich;
    }

    public <T> T enrich(T input, OperationContext context, Artifact.AgentModel parent) {

        if (input instanceof AgentModels.AgentRequest a) {
            return (T) enrichAgentRequests(a, context, parent);
        }
        if (input instanceof AgentModels.AgentResult r) {
            return (T) enrichAgentResult(r, context, parent);
        }
        if (input instanceof Artifact.AgentModel model) {
            return (T) enrichAgentModel(model, context, parent);
        }
        if (input instanceof AgentModels.Routing model) {
            return (T) enrichRouting(model, context, parent);
        }

        return input;
    }

    private Artifact.AgentModel findParentForInput(Artifact.AgentModel input, BlackboardHistory history) {
        if (input == null || history == null) {
            return null;
        }

        return switch (input) {
            case AgentModels.AgentRequest req ->
                    findParentForAgentRequests(history, req);
            case AgentModels.AgentResult res ->
                    findParentForAgentResultTypes(history, res);
            default -> {
                log.error("Found unknown agent model input {} in request enrichment. Returning it without enriching.", input.getClass().getSimpleName());
                yield input;
            }
        };
    }

    private Artifact.AgentModel findParentForAgentResultTypes(BlackboardHistory history, AgentModels.AgentResult res) {
        return switch (res) {
            case AgentModels.DiscoveryAgentResult ignored ->
                    findLastFromHistory(history, AgentModels.DiscoveryAgentRequest.class);
            case AgentModels.DiscoveryCollectorResult ignored ->
                    findLastFromHistory(history, AgentModels.DiscoveryCollectorRequest.class);
            case AgentModels.DiscoveryOrchestratorResult ignored ->
                    findLastFromHistory(history, AgentModels.DiscoveryOrchestratorRequest.class);
            case AgentModels.MergerAgentResult ignored ->
                    findLastFromHistory(history, AgentModels.MergerRequest.class);
            case AgentModels.OrchestratorAgentResult ignored ->
                    findLastFromHistory(history, AgentModels.OrchestratorRequest.class);
            case AgentModels.OrchestratorCollectorResult ignored ->
                    findLastFromHistory(history, AgentModels.OrchestratorCollectorRequest.class);
            case AgentModels.PlanningAgentResult ignored ->
                    findLastFromHistory(history, AgentModels.PlanningAgentRequest.class);
            case AgentModels.PlanningCollectorResult ignored ->
                    findLastFromHistory(history, AgentModels.PlanningCollectorRequest.class);
            case AgentModels.PlanningOrchestratorResult ignored ->
                    findLastFromHistory(history, AgentModels.PlanningOrchestratorRequest.class);
            case AgentModels.ReviewAgentResult ignored ->
                    findLastFromHistory(history, AgentModels.ReviewRequest.class);
            case AgentModels.TicketAgentResult ignored ->
                    findLastFromHistory(history, AgentModels.TicketAgentRequest.class);
            case AgentModels.TicketCollectorResult ignored ->
                    findLastFromHistory(history, AgentModels.TicketCollectorRequest.class);
            case AgentModels.TicketOrchestratorResult ignored ->
                    findLastFromHistory(history, AgentModels.TicketOrchestratorRequest.class);
        };
    }

    private Artifact.AgentModel findParentForAgentRequests(BlackboardHistory history, AgentModels.AgentRequest req) {
        return switch (req) {
            // Existing cases (migrated from old switch)
            case AgentModels.ContextManagerRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.ContextManagerRoutingRequest.class,
                            AgentModels.AgentRequest.class,
                            AgentModels.AgentResult.class);
            case AgentModels.ContextManagerRoutingRequest ignored ->
                    findLastFromHistory(history, AgentModels.AgentRequest.class, AgentModels.AgentResult.class);
            case AgentModels.DiscoveryAgentRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.DiscoveryAgentRequests.class);
            case AgentModels.DiscoveryAgentRequests ignored ->
                    findLastFromHistory(history,
                            AgentModels.DiscoveryOrchestratorRequest.class);
            case AgentModels.DiscoveryAgentResults ignored ->
                    findLastFromHistory(history,
                            AgentModels.DiscoveryAgentRequests.class);
            case AgentModels.DiscoveryCollectorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.DiscoveryOrchestratorRequest.class);
            case AgentModels.DiscoveryOrchestratorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.OrchestratorRequest.class);
            case AgentModels.InterruptRequest interruptRequest ->
                    findParentForInterruptTypes(history, interruptRequest);
            case AgentModels.MergerRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.AgentRequest.class,
                            AgentModels.AgentResult.class);
            case AgentModels.OrchestratorCollectorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.OrchestratorRequest.class);
            case AgentModels.OrchestratorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.OrchestratorRequest.class);
            case AgentModels.PlanningAgentRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.PlanningOrchestratorRequest.class);
            case AgentModels.PlanningAgentRequests ignored ->
                    findLastFromHistory(history,
                            AgentModels.PlanningOrchestratorRequest.class);
            case AgentModels.PlanningAgentResults ignored ->
                    findLastFromHistory(history,
                            AgentModels.PlanningAgentRequests.class);
            case AgentModels.PlanningCollectorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.PlanningOrchestratorRequest.class);
            case AgentModels.PlanningOrchestratorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.DiscoveryOrchestratorRequest.class,
                            AgentModels.OrchestratorRequest.class);
            case AgentModels.ReviewRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.AgentRequest.class,
                            AgentModels.AgentResult.class);
            case AgentModels.TicketAgentRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.TicketOrchestratorRequest.class);
            case AgentModels.TicketAgentRequests ignored ->
                    findLastFromHistory(history,
                            AgentModels.TicketOrchestratorRequest.class);
            case AgentModels.TicketAgentResults ignored ->
                    findLastFromHistory(history,
                            AgentModels.TicketAgentRequests.class);
            case AgentModels.TicketCollectorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.TicketOrchestratorRequest.class);
            case AgentModels.TicketOrchestratorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.PlanningOrchestratorRequest.class,
                            AgentModels.DiscoveryOrchestratorRequest.class,
                            AgentModels.OrchestratorRequest.class);
        };
    }

    private Artifact.AgentModel findParentForInterruptTypes(BlackboardHistory history, AgentModels.InterruptRequest interruptRequest) {
        return switch (interruptRequest) {
            case AgentModels.InterruptRequest.ContextManagerInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.ContextManagerRequest.class);
            case AgentModels.InterruptRequest.DiscoveryAgentDispatchInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.DiscoveryAgentRequests.class);
            case AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.DiscoveryAgentRequest.class);
            case AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.DiscoveryCollectorRequest.class);
            case AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.DiscoveryOrchestratorRequest.class);
            case AgentModels.InterruptRequest.MergerInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.MergerRequest.class);
            case AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.OrchestratorCollectorRequest.class);
            case AgentModels.InterruptRequest.OrchestratorInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.OrchestratorRequest.class);
            case AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.PlanningAgentRequests.class);
            case AgentModels.InterruptRequest.PlanningAgentInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.PlanningAgentRequest.class);
            case AgentModels.InterruptRequest.PlanningCollectorInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.PlanningCollectorRequest.class);
            case AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.PlanningOrchestratorRequest.class);
            case AgentModels.InterruptRequest.QuestionAnswerInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.ContextManagerRequest.class);
            case AgentModels.InterruptRequest.ReviewInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.ReviewRequest.class);
            case AgentModels.InterruptRequest.TicketAgentDispatchInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.TicketAgentRequests.class);
            case AgentModels.InterruptRequest.TicketAgentInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.TicketAgentRequest.class);
            case AgentModels.InterruptRequest.TicketCollectorInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.TicketCollectorRequest.class);
            case AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.TicketOrchestratorRequest.class);
        };
    }

    private Artifact.AgentModel findLastFromHistory(BlackboardHistory history, Class<?>... types) {
        if (history == null || types == null || types.length == 0) {
            return null;
        }
        List<BlackboardHistory.Entry> entries = history.copyOfEntries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            BlackboardHistory.Entry entry = entries.get(i);
            if (entry == null) {
                continue;
            }
            Object input = entry.input();
            if (!(input instanceof Artifact.AgentModel model)) {
                continue;
            }
            for (Class<?> type : types) {
                if (type != null && type.isInstance(model)) {
                    return model;
                }
            }
        }
        return null;
    }


    private <T extends AgentModels.Routing> T enrichRouting(T model, OperationContext context, Artifact.AgentModel parent) {
//        doesn't need to be enriched - any request will be enriched in the action it routes to when it routes to that action.
        return model;
    }

    /**
     * Enrich a request object by setting ArtifactKey and PreviousContext fields.
     * Pattern matches on the input type to apply appropriate enrichment.
     *
     * @param input   the request object to enrich
     * @param context the operation context for resolving IDs and previous state
     * @return the enriched request object
     */
    @SuppressWarnings("unchecked")
    public <T extends AgentModels.AgentRequest> T enrichAgentRequests(T input, OperationContext context, Artifact.AgentModel parent) {
        if (input == null) {
            return null;
        }

        return switch (input) {
            case AgentModels.OrchestratorRequest req ->
                    (T) enrichOrchestratorRequest(req, context, parent);
            case AgentModels.OrchestratorCollectorRequest req ->
                    (T) enrichOrchestratorCollectorRequest(req, context, parent);
            case AgentModels.DiscoveryOrchestratorRequest req ->
                    (T) enrichDiscoveryOrchestratorRequest(req, context, parent);
            case AgentModels.DiscoveryAgentRequest req ->
                    (T) enrichDiscoveryAgentRequest(req, context, parent);
            case AgentModels.DiscoveryCollectorRequest req ->
                    (T) enrichDiscoveryCollectorRequest(req, context, parent);
            case AgentModels.PlanningOrchestratorRequest req ->
                    (T) enrichPlanningOrchestratorRequest(req, context, parent);
            case AgentModels.PlanningAgentRequest req ->
                    (T) enrichPlanningAgentRequest(req, context, parent);
            case AgentModels.PlanningCollectorRequest req ->
                    (T) enrichPlanningCollectorRequest(req, context, parent);
            case AgentModels.TicketOrchestratorRequest req ->
                    (T) enrichTicketOrchestratorRequest(req, context, parent);
            case AgentModels.TicketAgentRequest req ->
                    (T) enrichTicketAgentRequest(req, context, parent);
            case AgentModels.TicketCollectorRequest req ->
                    (T) enrichTicketCollectorRequest(req, context, parent);
            case AgentModels.ReviewRequest req ->
                    (T) enrichReviewRequest(req, context, parent);
            case AgentModels.MergerRequest req ->
                    (T) enrichMergerRequest(req, context, parent);
            case AgentModels.DiscoveryAgentRequests req ->
                    (T) enrichDiscoveryAgentRequests(req, context, parent);
            case AgentModels.DiscoveryAgentResults req ->
                    (T) enrichDiscoveryAgentResults(req, context, parent);
            case AgentModels.PlanningAgentRequests req ->
                    (T) enrichPlanningAgentRequests(req, context, parent);
            case AgentModels.PlanningAgentResults req ->
                    (T) enrichPlanningAgentResults(req, context, parent);
            case AgentModels.TicketAgentRequests req ->
                    (T) enrichTicketAgentRequests(req, context, parent);
            case AgentModels.TicketAgentResults req ->
                    (T) enrichTicketAgentResults(req, context, parent);
            case AgentModels.InterruptRequest req ->
                    (T) enrichInterruptRequest(req, context, parent);
            case AgentModels.ContextManagerRequest req ->
                    (T) enrichContextManagerRequest(req, context, parent);
            case AgentModels.ContextManagerRoutingRequest req ->
                    (T) req.toBuilder()
                            .contextId(resolveContextId(context, req, parent))
                            .build();
        };
    }

    private <T extends AgentModels.AgentResult> T enrichAgentResult(T input, OperationContext context, Artifact.AgentModel parent) {
        AgentModels.AgentResult res = switch (input) {
            case AgentModels.DiscoveryAgentResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.DISCOVERY_AGENT, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.PlanningOrchestratorResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.PLANNING_ORCHESTRATOR, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.DiscoveryOrchestratorResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.DISCOVERY_ORCHESTRATOR, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.MergerAgentResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.MERGER_AGENT, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.OrchestratorAgentResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.ORCHESTRATOR, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.PlanningAgentResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.PLANNING_AGENT, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.ReviewAgentResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.REVIEW_AGENT, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.TicketAgentResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.TICKET_AGENT, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.TicketOrchestratorResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.TICKET_ORCHESTRATOR, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.TicketCollectorResult collectorResult -> {
                collectorResult = collectorResult.toBuilder()
                        .contextId(resolveContextId(context, AgentType.TICKET_COLLECTOR, parent))
                        .collectorDecision(wrapCollectorDecision(collectorResult.collectorDecision()))
                        .build();
                yield withEnrichedChildren(collectorResult, collectorResult.children(), context);
            }
            case AgentModels.DiscoveryCollectorResult collectorResult -> {
                collectorResult = collectorResult.toBuilder()
                        .contextId(resolveContextId(context, AgentType.DISCOVERY_COLLECTOR, parent))
                        .collectorDecision(wrapCollectorDecision(collectorResult.collectorDecision()))
                        .build();
                yield withEnrichedChildren(collectorResult, collectorResult.children(), context);
            }
            case AgentModels.OrchestratorCollectorResult collectorResult -> {
                collectorResult = collectorResult.toBuilder()
                        .contextId(resolveContextId(context, AgentType.ORCHESTRATOR_COLLECTOR, parent))
                        .collectorDecision(wrapCollectorDecision(collectorResult.collectorDecision()))
                        .build();
                yield withEnrichedChildren(collectorResult, collectorResult.children(), context);
            }
            case AgentModels.PlanningCollectorResult collectorResult -> {
                collectorResult = collectorResult.toBuilder()
                        .contextId(resolveContextId(context, AgentType.PLANNING_COLLECTOR, parent))
                        .collectorDecision(wrapCollectorDecision(collectorResult.collectorDecision()))
                        .build();
                yield withEnrichedChildren(collectorResult, collectorResult.children(), context);
            }
        };

        return (T) res;
    }

    public AgentModels.CollectorDecision wrapCollectorDecision(@Nullable AgentModels.CollectorDecision c) {
        return Optional.ofNullable(c)
                .map(cd -> {
                    if (cd.decisionType() == null)
                        return cd.toBuilder()
                                .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                                .build();

                    return cd;
                })
                .orElseGet(() -> AgentModels.CollectorDecision.builder()
                        .decisionType(Events.CollectorDecisionType.ADVANCE_PHASE)
                        .build());
    }

    private <T extends Artifact.AgentModel> T enrichAgentModel(T input, OperationContext context, Artifact.AgentModel parent) {
        return withEnrichedChildren(input, input.children(), context);
    }

    private AgentModels.InterruptRequest enrichInterruptRequest(
            AgentModels.InterruptRequest req,
            OperationContext context,
            Artifact.AgentModel parent
    ) {
        if (req == null) {
            return null;
        }
        return switch (req) {
            case AgentModels.InterruptRequest.OrchestratorInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.DiscoveryAgentDispatchInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.PlanningAgentInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.PlanningCollectorInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.TicketAgentInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.TicketCollectorInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.TicketAgentDispatchInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.ReviewInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.MergerInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.ContextManagerInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.QuestionAnswerInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
        };
    }

    private AgentModels.OrchestratorRequest enrichOrchestratorRequest(
            AgentModels.OrchestratorRequest req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder();
        if ((req.key() == null || req.key().value() == null) && parent == null) {
            String processId = context.getProcessContext().getAgentProcess().getId();
            log.error("Orchestrator request key was null. This isnt' supposed to happen. Manually creating ArtifactKey with ID as OperationContext.processId({})",
                    processId);
            reqBuilder = reqBuilder.contextId(new ArtifactKey(processId));
        } else if (parent != null) {
            if (parent == req) {
                log.error("Found strange instance where was same request.");
            }

            reqBuilder = reqBuilder.contextId(resolveContextId(context, req, parent));
        }

        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildOrchestratorPreviousContext(context));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }

    private AgentModels.OrchestratorCollectorRequest enrichOrchestratorCollectorRequest(
            AgentModels.OrchestratorCollectorRequest req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(context, req, parent));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildOrchestratorCollectorPreviousContext(context));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }

    private AgentModels.DiscoveryOrchestratorRequest enrichDiscoveryOrchestratorRequest(
            AgentModels.DiscoveryOrchestratorRequest req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(context, req, parent));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildDiscoveryOrchestratorPreviousContext(context));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }

    private AgentModels.DiscoveryAgentRequest enrichDiscoveryAgentRequest(
            AgentModels.DiscoveryAgentRequest req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(context, req, parent));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildDiscoveryAgentPreviousContext(context));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }

    private AgentModels.DiscoveryCollectorRequest enrichDiscoveryCollectorRequest(
            AgentModels.DiscoveryCollectorRequest req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(context, req, parent));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildDiscoveryCollectorPreviousContext(context));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }

    private AgentModels.PlanningOrchestratorRequest enrichPlanningOrchestratorRequest(
            AgentModels.PlanningOrchestratorRequest req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(context, req, parent));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildPlanningOrchestratorPreviousContext(context));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }

    private AgentModels.PlanningAgentRequest enrichPlanningAgentRequest(
            AgentModels.PlanningAgentRequest req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(context, req, parent));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildPlanningAgentPreviousContext(context));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }

    private AgentModels.PlanningCollectorRequest enrichPlanningCollectorRequest(
            AgentModels.PlanningCollectorRequest req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(context, req, parent));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildPlanningCollectorPreviousContext(context));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }

    private AgentModels.TicketOrchestratorRequest enrichTicketOrchestratorRequest(
            AgentModels.TicketOrchestratorRequest req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(context, req, parent));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildTicketOrchestratorPreviousContext(context));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }

    private AgentModels.TicketAgentRequest enrichTicketAgentRequest(
            AgentModels.TicketAgentRequest req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(context, req, parent));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildTicketAgentPreviousContext(context));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }

    private AgentModels.TicketCollectorRequest enrichTicketCollectorRequest(
            AgentModels.TicketCollectorRequest req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(context, req, parent));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildTicketCollectorPreviousContext(context));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }

    private AgentModels.ReviewRequest enrichReviewRequest(
            AgentModels.ReviewRequest req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(context, req, parent));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildReviewPreviousContext(context));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }

    private AgentModels.MergerRequest enrichMergerRequest(
            AgentModels.MergerRequest req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(context, req, parent));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildMergerPreviousContext(context));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }

    private AgentModels.DiscoveryAgentResults enrichDiscoveryAgentResults(
            AgentModels.DiscoveryAgentResults req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(context, req, parent));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildDiscoveryCollectorPreviousContext(context));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }

    private AgentModels.PlanningAgentResults enrichPlanningAgentResults(
            AgentModels.PlanningAgentResults req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(context, req, parent));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildPlanningCollectorPreviousContext(context));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }


    private AgentModels.DiscoveryAgentRequests enrichDiscoveryAgentRequests(
            AgentModels.DiscoveryAgentRequests req, OperationContext context, Artifact.AgentModel parent) {
        log.info("enrichDiscoveryAgentRequests: BEFORE enrichment | containerContextId={} | childCount={} | parentKey={}",
                req.contextId() != null ? req.contextId().value() : "null",
                req.requests() != null ? req.requests().size() : 0,
                parent != null && parent.key() != null ? parent.key().value() : "null");
        AgentModels.DiscoveryAgentRequests built = req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build();
        log.info("enrichDiscoveryAgentRequests: AFTER container enrichment | containerContextId={}", 
                built.contextId() != null ? built.contextId().value() : "null");
        AgentModels.DiscoveryAgentRequests result = (AgentModels.DiscoveryAgentRequests) built
                .withChildren(enrichChildren(built.children(), context, built));
        if (result.requests() != null) {
            for (int i = 0; i < result.requests().size(); i++) {
                var child = result.requests().get(i);
                log.info("enrichDiscoveryAgentRequests: CHILD[{}] contextId={} | subdomain={}",
                        i, child.contextId() != null ? child.contextId().value() : "null",
                        child.subdomainFocus());
            }
        }
        return result;
    }

    private AgentModels.PlanningAgentRequests enrichPlanningAgentRequests(
            AgentModels.PlanningAgentRequests req, OperationContext context, Artifact.AgentModel parent) {
        AgentModels.PlanningAgentRequests built = req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build();
        return built
                .withChildren(enrichChildren(built.children(), context, built));
    }

    private AgentModels.TicketAgentRequests enrichTicketAgentRequests(
            AgentModels.TicketAgentRequests req, OperationContext context, Artifact.AgentModel parent) {
        AgentModels.TicketAgentRequests build = req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build();
        return build
                .withChildren(enrichChildren(build.children(), context, build));
    }

    private AgentModels.TicketAgentResults enrichTicketAgentResults(
            AgentModels.TicketAgentResults req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(context, req, parent));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildTicketCollectorPreviousContext(context));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }

    private AgentModels.ContextManagerRequest enrichContextManagerRequest(
            AgentModels.ContextManagerRequest req, OperationContext context, Artifact.AgentModel parent) {
        return withEnrichedChildren(
                req.toBuilder()
                        .contextId(resolveContextId(context, req, parent))
                        .build(),
                req.children(),
                context
        );
    }

    private <T extends Artifact.AgentModel> T withEnrichedChildren(
            T model,
            List<Artifact.AgentModel> children,
            OperationContext context
    ) {
        if (model == null) {
            return null;
        }
        if (children == null || children.isEmpty()) {
            return model;
        }
        return model.withChildren(enrichChildren(children, context, model));
    }

    private List<Artifact.AgentModel> enrichChildren(
            List<Artifact.AgentModel> children,
            OperationContext context,
            Artifact.AgentModel parent
    ) {
        if (children == null || children.isEmpty()) {
            return List.of();
        }

        return children.stream()
                .map(child -> enrich(child, context, parent))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private ArtifactKey resolveContextId(OperationContext context, AgentType agentType, Artifact.AgentModel parent) {
        if (contextIdService == null) {
            return null;
        }
        return contextIdService.generate(resolveWorkflowRunId(context), agentType, parent);
    }

    /**
     * Resolve context ID for a request, recycling previous context IDs for non-dispatched agents.
     * <p>
     * Rules:
     * - Dispatched agents (Discovery/Planning/Ticket Agent): Always create new child
     * - All other agents: Try to find previous request of same type and reuse its contextId
     */
    private ArtifactKey resolveContextId(OperationContext context, AgentModels.AgentRequest currentRequest, Artifact.AgentModel parent) {
        if (contextIdService == null) {
            return null;
        }

        String requestType = currentRequest.getClass().getSimpleName();
        String parentKey = parent != null && parent.key() != null ? parent.key().value() : "null";
        String workflowRunId = resolveWorkflowRunId(context);

        if (shouldCreateNewSession(currentRequest)) {
            ArtifactKey newKey = contextIdService.generate(workflowRunId, null, parent);
            log.info("resolveContextId: NEW SESSION for {} | parentKey={} | workflowRunId={} | generatedKey={}",
                    requestType, parentKey, workflowRunId, newKey != null ? newKey.value() : "null");
            return newKey;
        }

        // For orchestrators, collectors, and dispatchers: try to recycle previous contextId
        BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(
                context.getAgentProcess().getBlackboard()
        );
        if (history != null) {
            ArtifactKey recycled = findPreviousContextId(history, currentRequest);
            if (recycled != null) {
                log.info("resolveContextId: RECYCLED for {} | recycledKey={}", requestType, recycled.value());
                return recycled;
            }
        }

        ArtifactKey fallbackKey = contextIdService.generate(workflowRunId, null, parent);
        log.info("resolveContextId: FALLBACK (no previous) for {} | parentKey={} | workflowRunId={} | generatedKey={}",
                requestType, parentKey, workflowRunId, fallbackKey != null ? fallbackKey.value() : "null");
        return fallbackKey;
    }

    /**
     * Dispatched agents (Discovery/Planning/Ticket Agent) always get new sessions.
     */
    private boolean shouldCreateNewSession(Artifact.AgentModel model) {
        return model instanceof AgentModels.DiscoveryAgentRequest
                || model instanceof AgentModels.PlanningAgentRequest
                || model instanceof AgentModels.TicketAgentRequest;
    }

    /**
     * Find a previous request of the same type in BlackboardHistory and return its contextId.
     */
    private ArtifactKey findPreviousContextId(BlackboardHistory history, AgentModels.AgentRequest currentRequest) {
        AgentModels.AgentRequest previous = history.getLastOfType(currentRequest.getClass());
        if (previous != null && (previous.contextId() != null && previous.contextId().value() != null)) {
            return previous.contextId();
        }
        return null;
    }

    private String resolveWorkflowRunId(OperationContext context) {
        if (context == null || context.getProcessContext() == null) {
            return null;
        }
        var options = context.getProcessContext().getProcessOptions();
        if (options == null) {
            return null;
        }
        String nodeId = options.getContextIdString();
        if (nodeId == null || nodeId.isBlank()) {
            return null;
        }
        return nodeId;
    }

    /**
     * Factory for building typed PreviousContext objects from operation context.
     */
    public static class PreviousContextFactory {

        public PreviousContext.OrchestratorPreviousContext buildOrchestratorPreviousContext(OperationContext context) {
            AgentModels.OrchestratorRouting lastRouting = context != null
                    ? context.last(AgentModels.OrchestratorRouting.class)
                    : null;
            if (lastRouting == null) {
                return null;
            }
            AgentModels.OrchestratorRequest lastRequest = context.last(AgentModels.OrchestratorRequest.class);
            var builder = PreviousContext.OrchestratorPreviousContext.builder()
                    .contextId(resolvePreviousContextKey(lastRequest != null ? lastRequest.contextId() : null))
                    .serializedOutput(lastRouting.toString())
                    .attemptNumber(countAttempts(context, AgentModels.OrchestratorRouting.class))
                    .previousAttemptAt(Instant.now());

            if (lastRequest != null) {
                builder = builder.previousContextId(lastRequest.contextId())
                        .previousDiscoveryCuration(lastRequest.discoveryCuration())
                        .previousPlanningCuration(lastRequest.planningCuration())
                        .previousTicketCuration(lastRequest.ticketCuration());
            }

            return builder.build();
        }

        public PreviousContext.OrchestratorCollectorPreviousContext buildOrchestratorCollectorPreviousContext(OperationContext context) {
            AgentModels.OrchestratorCollectorRouting lastRouting = context != null
                    ? context.last(AgentModels.OrchestratorCollectorRouting.class)
                    : null;
            if (lastRouting == null) {
                return null;
            }
            AgentModels.OrchestratorCollectorRequest lastRequest = context.last(AgentModels.OrchestratorCollectorRequest.class);
            var builder = PreviousContext.OrchestratorCollectorPreviousContext.builder()
                    .contextId(resolvePreviousContextKey(lastRequest != null ? lastRequest.contextId() : null))
                    .serializedOutput(lastRouting.toString())
                    .attemptNumber(countAttempts(context, AgentModels.OrchestratorCollectorRouting.class))
                    .previousAttemptAt(Instant.now());

            if (lastRequest != null) {
                builder = builder.previousContextId(lastRequest.contextId())
                        .previousDiscoveryCuration(lastRequest.discoveryCuration())
                        .previousPlanningCuration(lastRequest.planningCuration())
                        .previousTicketCuration(lastRequest.ticketCuration());
            }

            return builder.build();
        }

        public PreviousContext.DiscoveryOrchestratorPreviousContext buildDiscoveryOrchestratorPreviousContext(OperationContext context) {
            AgentModels.DiscoveryOrchestratorRouting lastRouting = context != null
                    ? context.last(AgentModels.DiscoveryOrchestratorRouting.class)
                    : null;
            if (lastRouting == null) {
                return null;
            }
            AgentModels.DiscoveryOrchestratorRequest lastRequest = context.last(AgentModels.DiscoveryOrchestratorRequest.class);
            var builder = PreviousContext.DiscoveryOrchestratorPreviousContext.builder()
                    .contextId(resolvePreviousContextKey(lastRequest != null ? lastRequest.contextId() : null))
                    .serializedOutput(lastRouting.toString())
                    .attemptNumber(countAttempts(context, AgentModels.DiscoveryOrchestratorRouting.class))
                    .previousAttemptAt(Instant.now());

            if (lastRequest != null) {
                builder = builder.previousContextId(lastRequest.contextId());
            }

            return builder.build();
        }

        public PreviousContext.PlanningOrchestratorPreviousContext buildPlanningOrchestratorPreviousContext(OperationContext context) {
            AgentModels.PlanningOrchestratorRouting lastRouting = context != null
                    ? context.last(AgentModels.PlanningOrchestratorRouting.class)
                    : null;
            if (lastRouting == null) {
                return null;
            }
            AgentModels.PlanningOrchestratorRequest lastRequest = context.last(AgentModels.PlanningOrchestratorRequest.class);
            var builder = PreviousContext.PlanningOrchestratorPreviousContext.builder()
                    .contextId(resolvePreviousContextKey(lastRequest != null ? lastRequest.contextId() : null))
                    .serializedOutput(lastRouting.toString())
                    .attemptNumber(countAttempts(context, AgentModels.PlanningOrchestratorRouting.class))
                    .previousAttemptAt(Instant.now());

            if (lastRequest != null) {
                builder = builder.previousContextId(lastRequest.contextId())
                        .previousDiscoveryCuration(lastRequest.discoveryCuration());
            }

            return builder.build();
        }

        public PreviousContext.TicketOrchestratorPreviousContext buildTicketOrchestratorPreviousContext(OperationContext context) {
            AgentModels.TicketOrchestratorRouting lastRouting = context != null
                    ? context.last(AgentModels.TicketOrchestratorRouting.class)
                    : null;
            if (lastRouting == null) {
                return null;
            }
            AgentModels.TicketOrchestratorRequest lastRequest = context.last(AgentModels.TicketOrchestratorRequest.class);
            var builder = PreviousContext.TicketOrchestratorPreviousContext.builder()
                    .contextId(resolvePreviousContextKey(lastRequest != null ? lastRequest.contextId() : null))
                    .serializedOutput(lastRouting.toString())
                    .attemptNumber(countAttempts(context, AgentModels.TicketOrchestratorRouting.class))
                    .previousAttemptAt(Instant.now());

            if (lastRequest != null) {
                builder = builder.previousContextId(lastRequest.contextId())
                        .previousDiscoveryCuration(lastRequest.discoveryCuration())
                        .previousPlanningCuration(lastRequest.planningCuration());
            }

            return builder.build();
        }

        public PreviousContext.DiscoveryAgentPreviousContext buildDiscoveryAgentPreviousContext(OperationContext context) {
            AgentModels.DiscoveryAgentRouting lastRouting = context != null
                    ? context.last(AgentModels.DiscoveryAgentRouting.class)
                    : null;
            if (lastRouting == null) {
                return null;
            }
            AgentModels.DiscoveryAgentResult lastResult = lastRouting.agentResult();
            var builder = PreviousContext.DiscoveryAgentPreviousContext.builder()
                    .contextId(resolvePreviousContextKey(lastResult != null ? lastResult.contextId() : null))
                    .serializedOutput(lastRouting.toString())
                    .attemptNumber(countAttempts(context, AgentModels.DiscoveryAgentRouting.class))
                    .previousAttemptAt(Instant.now());

            if (lastResult != null) {
                builder = builder.previousDiscoveryResult(lastResult.report());
            }

            return builder.build();
        }

        public PreviousContext.PlanningAgentPreviousContext buildPlanningAgentPreviousContext(OperationContext context) {
            AgentModels.PlanningAgentRouting lastRouting = context != null
                    ? context.last(AgentModels.PlanningAgentRouting.class)
                    : null;
            if (lastRouting == null) {
                return null;
            }
            ArtifactKey parentKey = lastRouting.agentResult() != null ? lastRouting.agentResult().contextId() : null;
            return PreviousContext.PlanningAgentPreviousContext.builder()
                    .contextId(resolvePreviousContextKey(parentKey))
                    .serializedOutput(lastRouting.toString())
                    .attemptNumber(countAttempts(context, AgentModels.PlanningAgentRouting.class))
                    .previousAttemptAt(Instant.now())
                    .previousPlanningResult(lastRouting.agentResult())
                    .build();
        }

        public PreviousContext.TicketAgentPreviousContext buildTicketAgentPreviousContext(OperationContext context) {
            AgentModels.TicketAgentRouting lastRouting = context != null
                    ? context.last(AgentModels.TicketAgentRouting.class)
                    : null;
            if (lastRouting == null) {
                return null;
            }
            ArtifactKey parentKey = lastRouting.agentResult() != null ? lastRouting.agentResult().contextId() : null;
            return PreviousContext.TicketAgentPreviousContext.builder()
                    .contextId(resolvePreviousContextKey(parentKey))
                    .serializedOutput(lastRouting.toString())
                    .attemptNumber(countAttempts(context, AgentModels.TicketAgentRouting.class))
                    .previousAttemptAt(Instant.now())
                    .previousTicketResult(lastRouting.agentResult())
                    .build();
        }

        public PreviousContext.DiscoveryCollectorPreviousContext buildDiscoveryCollectorPreviousContext(OperationContext context) {
            AgentModels.DiscoveryCollectorRouting lastRouting = context != null
                    ? context.last(AgentModels.DiscoveryCollectorRouting.class)
                    : null;
            if (lastRouting == null) {
                return null;
            }
            var builder = PreviousContext.DiscoveryCollectorPreviousContext.builder()
                    .contextId(resolvePreviousContextKey(lastRouting.collectorResult() != null
                            ? lastRouting.collectorResult().contextId()
                            : null))
                    .serializedOutput(lastRouting.toString())
                    .attemptNumber(countAttempts(context, AgentModels.DiscoveryCollectorRouting.class))
                    .previousAttemptAt(Instant.now());

            if (lastRouting.collectorResult() != null) {
                builder = builder.previousDiscoveryResult(lastRouting.collectorResult())
                        .previousDiscoveryCuration(lastRouting.collectorResult().discoveryCollectorContext());
            }

            return builder.build();
        }

        public PreviousContext.PlanningCollectorPreviousContext buildPlanningCollectorPreviousContext(OperationContext context) {
            AgentModels.PlanningCollectorRouting lastRouting = context != null
                    ? context.last(AgentModels.PlanningCollectorRouting.class)
                    : null;
            if (lastRouting == null) {
                return null;
            }
            var builder = PreviousContext.PlanningCollectorPreviousContext.builder()
                    .contextId(resolvePreviousContextKey(lastRouting.collectorResult() != null
                            ? lastRouting.collectorResult().contextId()
                            : null))
                    .serializedOutput(lastRouting.toString())
                    .attemptNumber(countAttempts(context, AgentModels.PlanningCollectorRouting.class))
                    .previousAttemptAt(Instant.now());

            if (lastRouting.collectorResult() != null) {
                builder = builder.previousPlanningResult(lastRouting.collectorResult())
                        .previousPlanningCuration(lastRouting.collectorResult().planningCuration());
            }

            return builder.build();
        }

        public PreviousContext.TicketCollectorPreviousContext buildTicketCollectorPreviousContext(OperationContext context) {
            AgentModels.TicketCollectorRouting lastRouting = context != null
                    ? context.last(AgentModels.TicketCollectorRouting.class)
                    : null;
            if (lastRouting == null) {
                return null;
            }
            var builder = PreviousContext.TicketCollectorPreviousContext.builder()
                    .contextId(resolvePreviousContextKey(lastRouting.collectorResult() != null
                            ? lastRouting.collectorResult().contextId()
                            : null))
                    .serializedOutput(lastRouting.toString())
                    .attemptNumber(countAttempts(context, AgentModels.TicketCollectorRouting.class))
                    .previousAttemptAt(Instant.now());

            if (lastRouting.collectorResult() != null) {
                builder = builder.previousTicketResult(lastRouting.collectorResult())
                        .previousTicketCuration(lastRouting.collectorResult().ticketCuration());
            }

            return builder.build();
        }

        public PreviousContext.ReviewPreviousContext buildReviewPreviousContext(OperationContext context) {
            AgentModels.ReviewRouting lastRouting = context != null
                    ? context.last(AgentModels.ReviewRouting.class)
                    : null;
            if (lastRouting == null) {
                return null;
            }
            ArtifactKey parentKey = lastRouting.reviewResult() != null ? lastRouting.reviewResult().contextId() : null;
            return PreviousContext.ReviewPreviousContext.builder()
                    .contextId(resolvePreviousContextKey(parentKey))
                    .serializedOutput(lastRouting.toString())
                    .attemptNumber(countAttempts(context, AgentModels.ReviewRouting.class))
                    .previousAttemptAt(Instant.now())
                    .previousReviewEvaluation(lastRouting.reviewResult())
                    .build();
        }

        public PreviousContext.MergerPreviousContext buildMergerPreviousContext(OperationContext context) {
            AgentModels.MergerRouting lastRouting = context != null
                    ? context.last(AgentModels.MergerRouting.class)
                    : null;
            if (lastRouting == null) {
                return null;
            }
            ArtifactKey parentKey = lastRouting.mergerResult() != null ? lastRouting.mergerResult().contextId() : null;
            return PreviousContext.MergerPreviousContext.builder()
                    .contextId(resolvePreviousContextKey(parentKey))
                    .serializedOutput(lastRouting.toString())
                    .attemptNumber(countAttempts(context, AgentModels.MergerRouting.class))
                    .previousAttemptAt(Instant.now())
                    .previousMergerValidation(lastRouting.mergerResult())
                    .build();
        }

        private ArtifactKey resolvePreviousContextKey(ArtifactKey parentKey) {
            return parentKey != null ? parentKey.createChild() : ArtifactKey.createRoot();
        }

        private int countAttempts(OperationContext context, Class<?> routingClass) {
            if (context == null) {
                return 1;
            }
            // Count how many times this routing type appears in the context
            // For now, return 1 as a placeholder - the actual implementation would count from blackboard
            return 1;
        }
    }
}
