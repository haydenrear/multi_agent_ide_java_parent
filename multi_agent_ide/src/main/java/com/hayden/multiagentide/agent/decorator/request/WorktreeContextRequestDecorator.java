package com.hayden.multiagentide.agent.decorator.request;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.multiagentide.service.WorktreeService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.events.DegenerateLoopException;
import com.hayden.multiagentidelib.model.nodes.GraphNode;
import com.hayden.multiagentidelib.model.nodes.OrchestratorNode;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorktreeContextRequestDecorator implements RequestDecorator, DispatchedAgentRequestDecorator {

    private final GraphRepository graphRepository;
    private final WorktreeRepository worktreeRepository;
    private final WorktreeService worktreeService;

    private EventBus eventBus;

    @Autowired @Lazy
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public int order() {
        return -5_000;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        if (request == null || request.worktreeContext() != null) {
            return request;
        }

        if (request instanceof AgentModels.DispatchedRequest || request instanceof AgentModels.ResultsRequest) {
            String formatted = "Worktree context was not attached correctly to dispatched request %s.".formatted(request);
            log.error(formatted);
            eventBus.publish(Events.NodeErrorEvent.err(formatted, request.key()));
        }

        WorktreeSandboxContext sandboxContext = resolveSandboxContext(context);

        if (sandboxContext == null) {
            log.error("Sandbox context could not be resolved.");
            String message = """
                    No worktree was provided by orchestrator during it's request.
                    And therefore the worktree could not be provided for the downstream request.
                    """;
            eventBus.publish(Events.NodeErrorEvent.err(message, request.key()));
            throw new DegenerateLoopException(message, request.artifactType(), request.getClass(), 1);
        }

        return (T) withWorktreeContext(request, sandboxContext);
    }

    /**
     * Retrieve the worktree sandbox context from which we will branch from.
     * @param req
     * @param context
     * @return
     */
    private WorktreeSandboxContext resolveFromAgentRequest(AgentModels.AgentRequest req, DecoratorContext context) {
        switch(req) {
            case AgentModels.ResultsRequest resultsRequest -> {
            }
            case AgentModels.TicketAgentRequests ticketAgentRequests -> {
                return resolveFromOrchestratorNode(context.operationContext());
            }
            case AgentModels.PlanningAgentRequests planningAgentRequests -> {
                return resolveFromOrchestratorNode(context.operationContext());
            }
            case AgentModels.DiscoveryAgentRequests discoveryAgentRequests -> {
                return resolveFromOrchestratorNode(context.operationContext());
            }
            case AgentModels.OrchestratorCollectorRequest orchestratorCollectorRequest -> {
                return resolveFromOrchestratorNode(context.operationContext());
            }
            case AgentModels.OrchestratorRequest orchestratorRequest -> {
                return resolveFromOrchestratorNode(context.operationContext());
            }
            case AgentModels.DiscoveryOrchestratorRequest discoveryOrchestratorRequest -> {
                return resolveFromOrchestratorNode(context.operationContext());
            }
            case AgentModels.PlanningOrchestratorRequest planningOrchestratorRequest -> {
                return resolveFromOrchestratorNode(context.operationContext());
            }
            case AgentModels.TicketOrchestratorRequest ticketOrchestratorRequest -> {
                return resolveFromOrchestratorNode(context.operationContext());
            }
            case AgentModels.DiscoveryCollectorRequest discoveryCollectorRequest -> {
                return resolveFromOrchestratorNode(context.operationContext());
            }
            case AgentModels.PlanningCollectorRequest planningCollectorRequest -> {
                return resolveFromOrchestratorNode(context.operationContext());
            }
            case AgentModels.TicketCollectorRequest ticketCollectorRequest -> {
                return resolveFromOrchestratorNode(context.operationContext());
            }
            case AgentModels.DiscoveryAgentRequest discoveryAgentRequest -> {
            }
            case AgentModels.TicketAgentRequest ticketAgentRequest -> {
            }
            case AgentModels.PlanningAgentRequest planningAgentRequest -> {
            }
            case AgentModels.AiFilterRequest aiFilterRequest -> {
            }
            case AgentModels.CommitAgentRequest commitAgentRequest -> {
            }
            case AgentModels.ContextManagerRequest contextManagerRequest -> {
            }
            case AgentModels.ContextManagerRoutingRequest contextManagerRoutingRequest -> {
            }
            case AgentModels.InterruptRequest interruptRequest -> {
            }
            case AgentModels.MergeConflictRequest mergeConflictRequest -> {
            }
            case AgentModels.MergerRequest mergerRequest -> {
            }
            case AgentModels.ReviewRequest reviewRequest -> {
            }
        }

        return null;
    }

    private @Nullable WorktreeSandboxContext resolveSandboxContext(DecoratorContext context) {

        if (context.agentRequest() instanceof AgentModels.AgentRequest req) {
            var s = resolveFromAgentRequest(req, context);
            if (s != null)
                return s;
        }

        WorktreeSandboxContext sandboxContext = null;

        if (context.lastRequest() instanceof AgentModels.AgentRequest lastRequest
                && lastRequest.worktreeContext() != null) {
            sandboxContext = lastRequest.worktreeContext();
        }

        if (sandboxContext == null) {
            sandboxContext = resolveFromOrchestratorNode(context.operationContext());
        }
        return sandboxContext;
    }

    AgentModels.AgentRequest withWorktreeContext(AgentModels.AgentRequest request, WorktreeSandboxContext worktreeContext) {
        if (request == null || worktreeContext == null) {
            return request;
        }
        return switch (request) {
            case AgentModels.DiscoveryAgentRequests r ->
                    worktreeService.attachWorktreesToDiscoveryRequests(r.toBuilder().worktreeContext(worktreeContext).build(), request.contextId().value());
            case AgentModels.PlanningAgentRequests r ->
                    worktreeService.attachWorktreesToPlanningRequests(r.toBuilder().worktreeContext(worktreeContext).build(), request.contextId().value());
            case AgentModels.TicketAgentRequests r ->
                    worktreeService.attachWorktreesToTicketRequests(r.toBuilder().worktreeContext(worktreeContext).build(), request.contextId().value());
            case AgentModels.OrchestratorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.OrchestratorCollectorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.DiscoveryOrchestratorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.DiscoveryAgentRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.DiscoveryCollectorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.DiscoveryAgentResults r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.PlanningOrchestratorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.PlanningAgentRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.PlanningCollectorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.PlanningAgentResults r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.TicketOrchestratorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.TicketAgentRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.CommitAgentRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.MergeConflictRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.TicketCollectorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.TicketAgentResults r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.ReviewRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.MergerRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.ContextManagerRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.ContextManagerRoutingRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.OrchestratorInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.DiscoveryAgentDispatchInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.PlanningAgentInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.PlanningCollectorInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.TicketAgentInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.TicketCollectorInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.TicketAgentDispatchInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.ReviewInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.MergerInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.ContextManagerInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.QuestionAnswerInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.AiFilterRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
        };
    }

    private String resolveOrchestratorNode(OperationContext context) {
        var options = context.getProcessContext().getAgentProcess();
        String contextId = options.getId();
        return contextId;
    }

    private WorktreeSandboxContext resolveFromOrchestratorNode(OperationContext operationContext) {
        String nodeId = resolveOrchestratorNode(operationContext);

        if (StringUtils.isBlank(nodeId)) {
            return null;
        }

        Optional<GraphNode> nodeOpt = graphRepository.findById(nodeId);

        if (nodeOpt.isEmpty()) {
            return null;
        }

        GraphNode node = nodeOpt.get();
        String mainWorktreeId;
        List<String> submoduleIds = List.of();

        if (node instanceof OrchestratorNode orchestratorNode) {
            mainWorktreeId = orchestratorNode.mainWorktreeId();
            return buildSandboxContextFrom(mainWorktreeId, orchestratorNode.submoduleWorktrees());
        }

        return null;
    }

    private WorktreeSandboxContext buildSandboxContextFrom(String mainWorktreeId, List<SubmoduleWorktreeContext> submodules) {
        if (mainWorktreeId == null || mainWorktreeId.isBlank()) {
            return null;
        }
        MainWorktreeContext mainContext = worktreeRepository.findById(mainWorktreeId)
                .filter(MainWorktreeContext.class::isInstance)
                .map(MainWorktreeContext.class::cast)
                .orElse(null);
        if (mainContext == null) {
            return null;
        }
        List<SubmoduleWorktreeContext> resolved = submodules != null ? submodules : List.of();
        return new WorktreeSandboxContext(mainContext, resolved);
    }

    private WorktreeSandboxContext buildSandboxContext(String mainWorktreeId, List<String> submoduleIds) {
        if (mainWorktreeId == null || mainWorktreeId.isBlank()) {
            return null;
        }
        MainWorktreeContext mainContext = worktreeRepository.findById(mainWorktreeId)
                .filter(MainWorktreeContext.class::isInstance)
                .map(MainWorktreeContext.class::cast)
                .orElse(null);
        if (mainContext == null) {
            return null;
        }
        List<SubmoduleWorktreeContext> submodules = new ArrayList<>();
        if (submoduleIds != null) {
            for (String submoduleId : submoduleIds) {
                if (submoduleId == null || submoduleId.isBlank()) {
                    continue;
                }
                WorktreeContext found = worktreeRepository.findById(submoduleId).orElse(null);
                if (found instanceof SubmoduleWorktreeContext submoduleContext) {
                    submodules.add(submoduleContext);
                }
            }
        }
        return new WorktreeSandboxContext(mainContext, submodules);
    }

}
