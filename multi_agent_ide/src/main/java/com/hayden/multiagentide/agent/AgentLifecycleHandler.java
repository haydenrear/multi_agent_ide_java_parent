package com.hayden.multiagentide.agent;

import com.embabel.agent.api.event.*;
import com.embabel.agent.core.*;
import com.hayden.utilitymodule.acp.events.EventBus;
import com.hayden.utilitymodule.acp.events.Events;
import com.hayden.multiagentidelib.model.nodes.*;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentide.orchestration.ComputationGraphOrchestrator;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.multiagentide.service.WorktreeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Handles agent lifecycle events for the multi-agent system.
 * Manages node creation, updates, and removal in the computation graph
 * when agents are invoked (including when invoked by other agents).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentLifecycleHandler {


    private final ComputationGraphOrchestrator orchestrator;
    private final GraphRepository graphRepository;
    private final WorktreeRepository worktreeRepository;
    private final WorktreeService worktreeService;
    private final AgentPlatform agentPlatform;

    public Agent resolveAgent(String agentName) {
        List<Agent> agents = agentPlatform.agents();
        return agents.stream()
                .filter(agent -> agent.getName().equals(agentName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Agent not found: " + agentName));
    }

    public static AgenticEventListener agentProcessIdListener() {
        return new AgenticEventListener() {
            @Override
            public void onProcessEvent(@NonNull AgentProcessEvent event) {
                if (event instanceof LlmRequestEvent creation) {
                    EventBus.agentProcess.set(new EventBus.AgentProcessData(creation.getProcessId()));
                }
                if (event instanceof AgentProcessCreationEvent creation) {
                    EventBus.agentProcess.set(new EventBus.AgentProcessData(creation.getProcessId()));
                }
                if (event instanceof AgentProcessFinishedEvent) {
                    EventBus.agentProcess.remove();
                }
            }
        };
    }

    public <T> T runAgent(AgentInterfaces agentInterface, Object input, Class<T> outputClass, String nodeId) {
        log.info("Starting agent {}: {}, {}", agentInterface.getClass().getSimpleName(), nodeId, input);
        Agent agent = resolveAgent(agentInterface.multiAgentAgentName());
        ProcessOptions processOptions = ProcessOptions.DEFAULT.withContextId(nodeId)
                .withListener(agentProcessIdListener());

        AgentProcess process = agentPlatform.runAgentFrom(
                agent,
                processOptions,
                Map.of(IoBinding.DEFAULT_BINDING, input)
        );
        var rot = process.run().resultOfType(outputClass);
        return rot;
    }

    public void initializeOrchestrator(String repositoryUrl, String baseBranch,
                                       String goal, String title) {
        initializeOrchestrator(repositoryUrl, baseBranch, goal, title, null);
    }

    /**
     * Initialize an orchestrator node with a goal.
     * Creates main worktree, submodule worktrees, and base spec.
     */
    public void initializeOrchestrator(String repositoryUrl, String baseBranch,
                                       String goal, String title, String nodeId) {
        String resolvedNodeId = nodeId != null ? nodeId : UUID.randomUUID().toString();
        if (nodeExists(resolvedNodeId)) {
            log.info("Orchestrator node {} already exists; skipping initialization", resolvedNodeId);
            return;
        }

        // Create main worktree
        MainWorktreeContext mainWorktree = worktreeService.createMainWorktree(
                repositoryUrl, baseBranch, resolvedNodeId);

        // Create orchestrator node
        OrchestratorNode orchestrator = new OrchestratorNode(
                resolvedNodeId,
                Optional.ofNullable(title).orElse("Orchestrator"),
                goal,
                Events.NodeStatus.READY,
                null,
                new ArrayList<>(),
                new HashMap<>(),
                Instant.now(),
                Instant.now(),
                repositoryUrl,
                baseBranch,
                mainWorktree.hasSubmodules(),
                worktreeService.getSubmoduleNames(mainWorktree.worktreePath()),
                mainWorktree.worktreeId(),
                new ArrayList<>(),
                null,
                new ArrayList<>()
        );

        // Create submodule worktrees
        if (mainWorktree.hasSubmodules()) {
            List<String> submoduleWorktreeIds = new ArrayList<>();
            for (String submoduleName : orchestrator.submoduleNames()) {
                Path submodulePath = worktreeService.getSubmodulePath(
                        mainWorktree.worktreePath(), submoduleName);
                SubmoduleWorktreeContext subWorktree = worktreeService.createSubmoduleWorktree(
                        submoduleName, submodulePath.toString(),
                        mainWorktree.worktreeId(), mainWorktree.worktreePath(),
                        resolvedNodeId
                );
                submoduleWorktreeIds.add(subWorktree.worktreeId());
                worktreeRepository.save(subWorktree);

                // Emit worktree created event
                this.orchestrator.emitWorktreeCreatedEvent(subWorktree.worktreeId(), nodeId,
                        subWorktree.worktreePath().toString(), "submodule", submoduleName);
            }

            // Update orchestrator with submodule worktree IDs
            var submodule = new SubmoduleNode(
                orchestrator.nodeId(),
                orchestrator.title(),
                orchestrator.goal(),
                    orchestrator.status(),
                    orchestrator.parentNodeId(),
                    orchestrator.childNodeIds(),
                    orchestrator.metadata(),
                    orchestrator.createdAt(),
                    orchestrator.lastUpdatedAt(),
                    orchestrator.repositoryUrl(),
                    orchestrator.baseBranch(),
                    orchestrator.hasSubmodules(),
                    orchestrator.submoduleNames(),
                    orchestrator.mainWorktreeId(),
                    submoduleWorktreeIds,
                    orchestrator.orchestratorOutput()
            );

            orchestrator.submodules().add(submodule);
        }


        // Save to repositories
        graphRepository.save(orchestrator);
        worktreeRepository.save(mainWorktree);

        // Emit events
        this.orchestrator.emitNodeAddedEvent(orchestrator.nodeId(), orchestrator.title(),
                orchestrator.nodeType(), orchestrator.parentNodeId());
        this.orchestrator.emitWorktreeCreatedEvent(mainWorktree.worktreeId(), resolvedNodeId,
                mainWorktree.worktreePath().toString(), "main", null);
    }

    private boolean nodeExists(String nodeId) {
        return nodeId != null && orchestrator.getNode(nodeId).isPresent();
    }

}
