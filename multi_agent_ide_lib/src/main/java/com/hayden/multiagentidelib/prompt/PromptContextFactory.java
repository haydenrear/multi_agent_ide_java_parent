package com.hayden.multiagentidelib.prompt;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.PreviousContext;
import com.hayden.multiagentidelib.agent.UpstreamContext;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating PromptContext instances by extracting upstream contexts
 * from typed request objects using pattern matching.
 */

@Component
@RequiredArgsConstructor
public class PromptContextFactory {


    private final PromptContributorService promptContributor;

    /**
     * Build a PromptContext by pattern matching on the input object to extract
     * contextId, upstream contexts, and previousContext.
     *
     * @param agentType the type of agent this context is for
     * @param input the request object containing typed curation/upstream context fields
     * @param blackboardHistory optional blackboard history
     * @return a PromptContext with all upstream contexts collected
     */
    public PromptContext build(
            AgentType agentType,
            AgentModels.AgentRequest input,
            BlackboardHistory blackboardHistory,
            String templateName,
            Map<String, Object> model
    ) {
        return build(agentType, input, null, input, blackboardHistory, templateName, model);
    }

    /**
     * Build a PromptContext by pattern matching on the request used for context
     * extraction while also carrying previous/current requests explicitly.
     */
    public PromptContext build(
            AgentType agentType,
            AgentModels.AgentRequest contextRequest,
            AgentModels.AgentRequest previousRequest,
            AgentModels.AgentRequest currentRequest,
            BlackboardHistory blackboardHistory,
            String templateName,
            Map<String, Object> model
    ) {
        List<UpstreamContext> upstreamContexts = new ArrayList<>();
        PreviousContext previousContext = null;

        switch (contextRequest) {
            case AgentModels.OrchestratorRequest req -> {
                collectNonNull(upstreamContexts, req.discoveryCuration());
                collectNonNull(upstreamContexts, req.planningCuration());
                collectNonNull(upstreamContexts, req.ticketCuration());
                previousContext = req.previousContext();
            }
            case AgentModels.OrchestratorCollectorRequest req -> {
                collectNonNull(upstreamContexts, req.discoveryCuration());
                collectNonNull(upstreamContexts, req.planningCuration());
                collectNonNull(upstreamContexts, req.ticketCuration());
                previousContext = req.previousContext();
            }
            case AgentModels.DiscoveryOrchestratorRequest req -> {
                // Discovery orchestrator is at the start - no upstream contexts
                previousContext = req.previousContext();
            }
            case AgentModels.DiscoveryAgentResults req -> {
                // Discovery agent has no upstream curation
                previousContext = req.previousContext();
            }
            case AgentModels.DiscoveryAgentRequest req -> {
                // Discovery agent has no upstream curation
                previousContext = req.previousContext();
            }
            case AgentModels.DiscoveryCollectorRequest req -> {
                // Discovery collector has no upstream curation
                previousContext = req.previousContext();
            }
            case AgentModels.PlanningOrchestratorRequest req -> {
                collectNonNull(upstreamContexts, req.discoveryCuration());
                previousContext = req.previousContext();
            }
            case AgentModels.PlanningAgentRequest req -> {
                collectNonNull(upstreamContexts, req.discoveryCuration());
                previousContext = req.previousContext();
            }
            case AgentModels.PlanningCollectorRequest req -> {
                collectNonNull(upstreamContexts, req.discoveryCuration());
                previousContext = req.previousContext();
            }
            case AgentModels.TicketOrchestratorRequest req -> {
                collectNonNull(upstreamContexts, req.discoveryCuration());
                collectNonNull(upstreamContexts, req.planningCuration());
                previousContext = req.previousContext();
            }
            case AgentModels.TicketAgentRequest req -> {
                collectNonNull(upstreamContexts, req.discoveryCuration());
                collectNonNull(upstreamContexts, req.planningCuration());
                previousContext = req.previousContext();
            }
            case AgentModels.TicketCollectorRequest req -> {
                collectNonNull(upstreamContexts, req.discoveryCuration());
                collectNonNull(upstreamContexts, req.planningCuration());
                previousContext = req.previousContext();
            }
            case AgentModels.ReviewRequest req -> {
                previousContext = req.previousContext();
            }
            case AgentModels.MergerRequest req -> {
                previousContext = req.previousContext();
            }
            case AgentModels.ContextManagerRequest contextManagerRequest -> {
                previousContext = contextManagerRequest.previousContext();
            }
            case AgentModels.PlanningAgentResults planningAgentResults -> {
                previousContext = planningAgentResults.previousContext();
            }
            case AgentModels.TicketAgentResults ticketAgentResults -> {
                previousContext = ticketAgentResults.previousContext();
            }
            case AgentModels.ContextManagerRoutingRequest contextManagerRoutingRequest -> {
            }
            case AgentModels.DiscoveryAgentRequests discoveryAgentRequests -> {
            }
            case AgentModels.PlanningAgentRequests planningAgentRequests -> {
            }
            case AgentModels.TicketAgentRequests ticketAgentRequests -> {
            }
            case AgentModels.InterruptRequest interruptRequest -> {
                switch(interruptRequest) {
                    case AgentModels.InterruptRequest.ContextManagerInterruptRequest contextManagerInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.DiscoveryAgentDispatchInterruptRequest discoveryAgentDispatchInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest discoveryAgentInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest discoveryCollectorInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest discoveryOrchestratorInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.MergerInterruptRequest mergerInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest orchestratorCollectorInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.OrchestratorInterruptRequest orchestratorInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest planningAgentDispatchInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.PlanningAgentInterruptRequest planningAgentInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.PlanningCollectorInterruptRequest planningCollectorInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest planningOrchestratorInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.QuestionAnswerInterruptRequest questionAnswerInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.ReviewInterruptRequest reviewInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.TicketAgentDispatchInterruptRequest ticketAgentDispatchInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.TicketAgentInterruptRequest ticketAgentInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.TicketCollectorInterruptRequest ticketCollectorInterruptRequest -> {
                    }
                    case AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest ticketOrchestratorInterruptRequest -> {
                    }
                }
            }
        }

        var pc = new PromptContext(
                agentType,
                resolve(contextRequest != null ? contextRequest.contextId() : null),
                upstreamContexts,
                previousContext,
                blackboardHistory,
                previousRequest,
                currentRequest,
                Map.of(),
                templateName,
                model
        );

        return pc.toBuilder().promptContributors(this.promptContributor.getContributors(pc)).build();
    }

    /**
     * Build a PromptContext with explicit upstream contexts (for cases where
     * upstream contexts are already extracted or need to be manually specified).
     */
    public PromptContext build(
            AgentType agentType,
            ArtifactKey contextId,
            List<UpstreamContext> upstreamContexts,
            PreviousContext previousContext,
            BlackboardHistory blackboardHistory,
            String templateName,
            Map<String, Object> model
    ) {
        var pc = new PromptContext(
                agentType,
                resolve(contextId),
                upstreamContexts != null ? upstreamContexts : List.of(),
                previousContext,
                blackboardHistory,
                null,
                null,
                Map.of(),
                templateName,
                model
        );

        return pc.toBuilder().promptContributors(this.promptContributor.getContributors(pc)).build();
    }

    /**
     * Build a PromptContext with a single upstream context (convenience method
     * for backward compatibility).
     */
    public PromptContext build(
            AgentType agentType,
            ArtifactKey contextId,
            UpstreamContext upstreamContext,
            PreviousContext previousContext,
            BlackboardHistory blackboardHistory,
            String templateName,
            Map<String, Object> model
    ) {
        List<UpstreamContext> contexts = upstreamContext != null 
                ? List.of(upstreamContext) 
                : List.of();
        var pc = new PromptContext(
                agentType,
                resolve(contextId),
                contexts,
                previousContext,
                blackboardHistory,
                null,
                null,
                Map.of(),
                templateName,
                model
        );

        return pc.toBuilder().promptContributors(this.promptContributor.getContributors(pc)).build();
    }

    private ArtifactKey resolve(ArtifactKey contextId) {
        return contextId;
    }

    private void collectNonNull(List<UpstreamContext> list, UpstreamContext context) {
        if (context != null) {
            list.add(context);
        }
    }
}
