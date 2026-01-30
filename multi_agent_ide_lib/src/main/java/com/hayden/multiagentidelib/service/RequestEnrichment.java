package com.hayden.multiagentidelib.service;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.ContextId;
import com.hayden.multiagentidelib.agent.PreviousContext;
import com.hayden.multiagentidelib.prompt.ContextIdService;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service for enriching request objects with ContextId and PreviousContext.
 * Centralizes the logic for setting these fields consistently across all agent types.
 */
@Service
public class RequestEnrichment {

    private final ContextIdService contextIdService;
    private final PreviousContextFactory previousContextFactory;

    public RequestEnrichment(ContextIdService contextIdService) {
        this.contextIdService = contextIdService;
        this.previousContextFactory = new PreviousContextFactory();
    }

    public <T> T enrich(T input, OperationContext context) {

        if (input instanceof AgentModels.AgentRequest a) {
            return (T) enrichAgentRequests(a, context);
        }

        return input;

    }

    /**
     * Enrich a request object by setting ContextId and PreviousContext fields.
     * Pattern matches on the input type to apply appropriate enrichment.
     *
     * @param input the request object to enrich
     * @param context the operation context for resolving IDs and previous state
     * @return the enriched request object
     */
    @SuppressWarnings("unchecked")
    public <T extends AgentModels.AgentRequest> T enrichAgentRequests(T input, OperationContext context) {
        if (input == null) {
            return null;
        }

        return switch (input) {
            case AgentModels.OrchestratorRequest req -> (T) enrichOrchestratorRequest(req, context);
            case AgentModels.OrchestratorCollectorRequest req -> (T) enrichOrchestratorCollectorRequest(req, context);
            case AgentModels.DiscoveryOrchestratorRequest req -> (T) enrichDiscoveryOrchestratorRequest(req, context);
            case AgentModels.DiscoveryAgentRequest req -> (T) enrichDiscoveryAgentRequest(req, context);
            case AgentModels.DiscoveryCollectorRequest req -> (T) enrichDiscoveryCollectorRequest(req, context);
            case AgentModels.PlanningOrchestratorRequest req -> (T) enrichPlanningOrchestratorRequest(req, context);
            case AgentModels.PlanningAgentRequest req -> (T) enrichPlanningAgentRequest(req, context);
            case AgentModels.PlanningCollectorRequest req -> (T) enrichPlanningCollectorRequest(req, context);
            case AgentModels.TicketOrchestratorRequest req -> (T) enrichTicketOrchestratorRequest(req, context);
            case AgentModels.TicketAgentRequest req -> (T) enrichTicketAgentRequest(req, context);
            case AgentModels.TicketCollectorRequest req -> (T) enrichTicketCollectorRequest(req, context);
            case AgentModels.ReviewRequest req -> (T) enrichReviewRequest(req, context);
            case AgentModels.MergerRequest req -> (T) enrichMergerRequest(req, context);
            case AgentModels.ContextOrchestratorRequest req -> (T) req; // ContextOrchestrator routing/previous context not yet implemented
            case AgentModels.DiscoveryAgentRequests req -> (T) enrichDiscoveryAgentRequests(req, context);
            case AgentModels.DiscoveryAgentResults req -> (T) enrichDiscoveryAgentResults(req, context);
            case AgentModels.PlanningAgentRequests req -> (T) enrichPlanningAgentRequests(req, context);
            case AgentModels.PlanningAgentResults req -> (T) enrichPlanningAgentResults(req, context);
            case AgentModels.TicketAgentRequests req -> (T) enrichTicketAgentRequests(req, context);
            case AgentModels.TicketAgentResults req -> (T) enrichTicketAgentResults(req, context);
            case AgentModels.InterruptRequest req -> (T) req; // InterruptRequest doesn't have enrichable fields
        };
    }

    private AgentModels.OrchestratorRequest enrichOrchestratorRequest(
            AgentModels.OrchestratorRequest req, OperationContext context) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(req.contextId(), context, AgentType.ORCHESTRATOR));
        if (req.previousContext() == null)
            return reqBuilder.previousContext(previousContextFactory.buildOrchestratorPreviousContext(context))
                    .build();

        return reqBuilder.build();
    }

    private AgentModels.OrchestratorCollectorRequest enrichOrchestratorCollectorRequest(
            AgentModels.OrchestratorCollectorRequest req, OperationContext context) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(req.contextId(), context, AgentType.ORCHESTRATOR_COLLECTOR));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildOrchestratorCollectorPreviousContext(context));
        }

        return reqBuilder.build();
    }

    private AgentModels.DiscoveryOrchestratorRequest enrichDiscoveryOrchestratorRequest(
            AgentModels.DiscoveryOrchestratorRequest req, OperationContext context) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(req.contextId(), context, AgentType.DISCOVERY_ORCHESTRATOR));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildDiscoveryOrchestratorPreviousContext(context));
        }

        return reqBuilder.build();
    }

    private AgentModels.DiscoveryAgentRequest enrichDiscoveryAgentRequest(
            AgentModels.DiscoveryAgentRequest req, OperationContext context) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(req.contextId(), context, AgentType.DISCOVERY_AGENT));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildDiscoveryAgentPreviousContext(context));
        }

        return reqBuilder.build();
    }

    private AgentModels.DiscoveryCollectorRequest enrichDiscoveryCollectorRequest(
            AgentModels.DiscoveryCollectorRequest req, OperationContext context) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(req.contextId(), context, AgentType.DISCOVERY_COLLECTOR));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildDiscoveryCollectorPreviousContext(context));
        }

        return reqBuilder.build();
    }

    private AgentModels.PlanningOrchestratorRequest enrichPlanningOrchestratorRequest(
            AgentModels.PlanningOrchestratorRequest req, OperationContext context) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(req.contextId(), context, AgentType.PLANNING_ORCHESTRATOR));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildPlanningOrchestratorPreviousContext(context));
        }

        return reqBuilder.build();
    }

    private AgentModels.PlanningAgentRequest enrichPlanningAgentRequest(
            AgentModels.PlanningAgentRequest req, OperationContext context) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(req.contextId(), context, AgentType.PLANNING_AGENT));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildPlanningAgentPreviousContext(context));
        }

        return reqBuilder.build();
    }

    private AgentModels.PlanningCollectorRequest enrichPlanningCollectorRequest(
            AgentModels.PlanningCollectorRequest req, OperationContext context) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(req.contextId(), context, AgentType.PLANNING_COLLECTOR));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildPlanningCollectorPreviousContext(context));
        }

        return reqBuilder.build();
    }

    private AgentModels.TicketOrchestratorRequest enrichTicketOrchestratorRequest(
            AgentModels.TicketOrchestratorRequest req, OperationContext context) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(req.contextId(), context, AgentType.TICKET_ORCHESTRATOR));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildTicketOrchestratorPreviousContext(context));
        }

        return reqBuilder.build();
    }

    private AgentModels.TicketAgentRequest enrichTicketAgentRequest(
            AgentModels.TicketAgentRequest req, OperationContext context) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(req.contextId(), context, AgentType.TICKET_AGENT));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildTicketAgentPreviousContext(context));
        }

        return reqBuilder.build();
    }

    private AgentModels.TicketCollectorRequest enrichTicketCollectorRequest(
            AgentModels.TicketCollectorRequest req, OperationContext context) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(req.contextId(), context, AgentType.TICKET_COLLECTOR));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildTicketCollectorPreviousContext(context));
        }

        return reqBuilder.build();
    }

    private AgentModels.ReviewRequest enrichReviewRequest(
            AgentModels.ReviewRequest req, OperationContext context) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(req.contextId(), context, AgentType.REVIEW_AGENT));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildReviewPreviousContext(context));
        }

        return reqBuilder.build();
    }

    private AgentModels.MergerRequest enrichMergerRequest(
            AgentModels.MergerRequest req, OperationContext context) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(req.contextId(), context, AgentType.MERGER_AGENT));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildMergerPreviousContext(context));
        }

        return reqBuilder.build();
    }

    private AgentModels.DiscoveryAgentResults enrichDiscoveryAgentResults(
            AgentModels.DiscoveryAgentResults req, OperationContext context) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(req.contextId(), context, AgentType.DISCOVERY_AGENT_DISPATCH));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildDiscoveryCollectorPreviousContext(context));
        }

        return reqBuilder.build();
    }

    private AgentModels.PlanningAgentResults enrichPlanningAgentResults(
            AgentModels.PlanningAgentResults req, OperationContext context) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(req.contextId(), context, AgentType.PLANNING_AGENT_DISPATCH));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildPlanningCollectorPreviousContext(context));
        }

        return reqBuilder.build();
    }


    private AgentModels.DiscoveryAgentRequests enrichDiscoveryAgentRequests(
            AgentModels.DiscoveryAgentRequests req, OperationContext context) {
        return req.toBuilder()
                .resultId(resolveContextId(req.resultId(), context, AgentType.DISCOVERY_AGENT_DISPATCH))
                .build();
    }

    private AgentModels.PlanningAgentRequests enrichPlanningAgentRequests(
            AgentModels.PlanningAgentRequests req, OperationContext context) {
        return req.toBuilder()
                .resultId(resolveContextId(req.resultId(), context, AgentType.PLANNING_AGENT_DISPATCH))
                .build();
    }

    private AgentModels.TicketAgentRequests enrichTicketAgentRequests(
            AgentModels.TicketAgentRequests req, OperationContext context) {
        return req.toBuilder()
                .resultId(resolveContextId(req.resultId(), context, AgentType.TICKET_AGENT_DISPATCH))
                .build();
    }

    private AgentModels.TicketAgentResults enrichTicketAgentResults(
            AgentModels.TicketAgentResults req, OperationContext context) {
        var reqBuilder = req.toBuilder()
                .contextId(resolveContextId(req.contextId(), context, AgentType.TICKET_AGENT_DISPATCH));
        if (req.previousContext() == null) {
            reqBuilder = reqBuilder.previousContext(previousContextFactory.buildTicketCollectorPreviousContext(context));
        }

        return reqBuilder.build();
    }

    private ContextId resolveContextId(ContextId existing, OperationContext context, AgentType agentType) {
        if (existing != null) {
            return existing;
        }
        if (contextIdService == null) {
            return null;
        }
        return contextIdService.generate(resolveWorkflowRunId(context), agentType);
    }

    private String resolveWorkflowRunId(OperationContext context) {
        if (context == null || context.getProcessContext() == null) {
            return "wf-unknown";
        }
        var options = context.getProcessContext().getProcessOptions();
        if (options == null) {
            return "wf-unknown";
        }
        String nodeId = options.getContextIdString();
        if (nodeId == null || nodeId.isBlank()) {
            return "wf-unknown";
        }
        return nodeId.startsWith("wf-") ? nodeId : "wf-" + nodeId;
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
            return PreviousContext.PlanningAgentPreviousContext.builder()
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
            return PreviousContext.TicketAgentPreviousContext.builder()
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
                    .serializedOutput(lastRouting.toString())
                    .attemptNumber(countAttempts(context, AgentModels.DiscoveryCollectorRouting.class))
                    .previousAttemptAt(Instant.now());
            
            if (lastRouting.collectorResult() != null) {
                builder = builder.previousDiscoveryResult(lastRouting.collectorResult())
                        .previousDiscoveryCuration(lastRouting.collectorResult().discoveryCuration());
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
            return PreviousContext.ReviewPreviousContext.builder()
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
            return PreviousContext.MergerPreviousContext.builder()
                    .serializedOutput(lastRouting.toString())
                    .attemptNumber(countAttempts(context, AgentModels.MergerRouting.class))
                    .previousAttemptAt(Instant.now())
                    .previousMergerValidation(lastRouting.mergerResult())
                    .build();
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
