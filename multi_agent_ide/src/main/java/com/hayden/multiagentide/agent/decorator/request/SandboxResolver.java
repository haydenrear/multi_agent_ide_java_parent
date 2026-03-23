package com.hayden.multiagentide.agent.decorator.request;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.multiagentide.service.WorktreeService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.DecoratorContext;
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
public class SandboxResolver {

    private final GraphRepository graphRepository;
    private final WorktreeRepository worktreeRepository;

    public WorktreeSandboxContext resolveSandboxContext(DecoratorContext context) {

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

    public String resolveOrchestratorNode(OperationContext context) {
        var options = context.getAgentProcess();
        String contextId = options.getId();
        return contextId;
    }

    public WorktreeSandboxContext resolveFromOrchestratorNode(OperationContext operationContext) {
        if (operationContext == null) {
            return null;
        }
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
            case AgentModels.AiFilterRequest aiFilterRequest -> {
            }
            case AgentModels.AiPropagatorRequest aiPropagatorRequest -> {
            }
            case AgentModels.AiTransformerRequest aiTransformerRequest -> {
            }
            case AgentModels.DiscoveryAgentRequest discoveryAgentRequest -> {
            }
            case AgentModels.TicketAgentRequest ticketAgentRequest -> {
            }
            case AgentModels.PlanningAgentRequest planningAgentRequest -> {
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
        }

        return null;
    }
}
