package com.hayden.multiagentide.agent.decorator;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.multiagentide.service.WorktreeService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.events.DegenerateLoopException;
import com.hayden.multiagentidelib.model.nodes.GraphNode;
import com.hayden.multiagentidelib.model.nodes.HasWorktree;
import com.hayden.multiagentidelib.model.nodes.OrchestratorNode;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorktreeContextRequestDecorator implements RequestDecorator {

    private final GraphRepository graphRepository;
    private final WorktreeRepository worktreeRepository;
    private final WorktreeService worktreeService;

    @Override
    public int order() {
        return 20_000;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        if (request == null) {
            return request;
        }

        WorktreeSandboxContext sandboxContext = null;

        if (context.lastRequest() instanceof AgentModels.AgentRequest lastRequest
                && lastRequest.worktreeContext() != null) {
            sandboxContext = lastRequest.worktreeContext();
        }

        if (sandboxContext == null) {
            sandboxContext = resolveFromOrchestratorNode(context.operationContext());
        }

        if (sandboxContext == null) {
            log.error("Sandbox context could not be resolved.");
            throw new DegenerateLoopException("""
                    No worktree was provided by orchestrator during it's request.
                    And therefore the worktree could not be provided for the downstream request.
                    """, request.artifactType(), request.getClass(), 1);
        }

        return (T) withWorktreeContext(request, sandboxContext);
    }

    AgentModels.AgentRequest withWorktreeContext(AgentModels.AgentRequest request, WorktreeSandboxContext worktreeContext) {
        if (request == null || worktreeContext == null) {
            return request;
        }
        return switch (request) {
            case AgentModels.DiscoveryAgentRequests r ->
                    worktreeService.attachWorktreesToDiscoveryRequests(r.toBuilder().worktreeContext(worktreeContext).build(), request.artifactKey().value());
            case AgentModels.PlanningAgentRequests r ->
                    worktreeService.attachWorktreesToPlanningRequests(r.toBuilder().worktreeContext(worktreeContext).build(), request.artifactKey().value());
            case AgentModels.TicketAgentRequests r ->
                    worktreeService.attachWorktreesToTicketRequests(r.toBuilder().worktreeContext(worktreeContext).build(), request.artifactKey().value());
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
        } else if (node instanceof HasWorktree hasWorktree && hasWorktree.worktree() != null) {
            mainWorktreeId = hasWorktree.worktree().worktreeId();
            if (hasWorktree.worktree().submoduleWorktrees() != null) {
                submoduleIds = hasWorktree.worktree().submoduleWorktrees().stream()
                        .map(HasWorktree.WorkTree::worktreeId)
                        .toList();
            }
            return buildSandboxContext(mainWorktreeId, submoduleIds);
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
