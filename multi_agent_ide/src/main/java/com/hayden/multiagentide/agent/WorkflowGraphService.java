package com.hayden.multiagentide.agent;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.orchestration.ComputationGraphOrchestrator;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.service.WorktreeService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.model.nodes.*;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowGraphService {

    private static final String META_PARENT_WORKTREE = "parent_worktree_id";
    private static final String META_DISCOVERY_CONTEXT = "discovery_context";
    private static final String META_PLANNING_CONTEXT = "planning_context";

    private final ComputationGraphOrchestrator computationGraphOrchestrator;
    private final GraphRepository graphRepository;
    private final WorktreeService worktreeService;
    private final GraphNodeFactory nodeFactory;

    public synchronized  <T> T resolveState(OperationContext context, Function<com.hayden.multiagentidelib.agent.WorkflowGraphState, T> t) {
        Optional<BlackboardHistory> bhOpt = Optional.ofNullable(context.last(BlackboardHistory.class));
        T state = bhOpt
                    .flatMap(bh -> bh.fromState(t))
                    .orElse(null);

        if (bhOpt.isEmpty())
            throw new RuntimeException("No blackboard history existed or workflow graph state existed.");

        return state;
    }

    public synchronized void updateState(OperationContext context,
                                         Function<com.hayden.multiagentidelib.agent.WorkflowGraphState, com.hayden.multiagentidelib.agent.WorkflowGraphState> state) {
        Optional<BlackboardHistory> bhOpt = Optional.ofNullable(context.last(BlackboardHistory.class));

        bhOpt.ifPresentOrElse(bh -> {
            bh.updateState(state);
        }, () -> {
            log.error("Could not find state to update for blackboard!");
        });
    }

    public OrchestratorNode requireOrchestrator(OperationContext context) {
        return resolveState(context, state -> requireNode(state.orchestratorNodeId(), OrchestratorNode.class, "orchestrator"));
    }

    public DiscoveryOrchestratorNode requireDiscoveryOrchestrator(OperationContext context) {
        return resolveState(context, s -> requireNode(s.discoveryOrchestratorNodeId(), DiscoveryOrchestratorNode.class, "discovery orchestrator"));
    }

    public DiscoveryCollectorNode requireDiscoveryCollector(OperationContext context) {
        return resolveState(context, s -> requireNode(s.discoveryCollectorNodeId(), DiscoveryCollectorNode.class, "discovery collector"));
    }

    public PlanningOrchestratorNode requirePlanningOrchestrator(OperationContext context) {
        return resolveState(context, s -> requireNode(s.planningOrchestratorNodeId(), PlanningOrchestratorNode.class, "planning orchestrator"));
    }

    public PlanningCollectorNode requirePlanningCollector(OperationContext context) {
        return resolveState(context, s -> requireNode(s.planningCollectorNodeId(), PlanningCollectorNode.class, "planning collector"));
    }

    public TicketOrchestratorNode requireTicketOrchestrator(OperationContext context) {
        return resolveState(context, state -> requireNode(state.ticketOrchestratorNodeId(), TicketOrchestratorNode.class, "ticket orchestrator"));
    }

    public TicketCollectorNode requireTicketCollector(OperationContext context) {
        return resolveState(context, state -> requireNode(state.ticketCollectorNodeId(), TicketCollectorNode.class, "ticket collector"));
    }

    public CollectorNode requireOrchestratorCollector(OperationContext context) {
        return resolveState(context, state -> requireNode(state.orchestratorCollectorNodeId(), CollectorNode.class, "orchestrator collector"));
    }

    public ReviewNode requireReviewNode(OperationContext context) {
        return resolveState(context, state -> requireNode(state.reviewNodeId(), ReviewNode.class, "review"));
    }

    public MergeNode requireMergeNode(OperationContext context) {
        return resolveState(context, state -> requireNode(state.mergeNodeId(), MergeNode.class, "merge"));
    }

    public void emitErrorEvent(GraphNode node, String message) {
        if (node == null) {
            return;
        }
        computationGraphOrchestrator.emitErrorEvent(
                node.nodeId(),
                node.title(),
                node.nodeType(),
                message
        );
    }

    public OrchestratorNode startOrchestrator(OperationContext context) {
        OrchestratorNode orchestratorNode = requireOrchestrator(context);
        return markNodeRunning(orchestratorNode);
    }

    public void completeOrchestrator(OrchestratorNode running, AgentModels.OrchestratorRouting routing) {
        String output = routing != null ? routing.toString() : "";
        OrchestratorNode withOutput = running.withOutput(output);
        graphRepository.save(withOutput);
        handleRoutingInterrupt(withOutput, routing != null ? routing.interruptRequest() : null, output);
    }

    public void completeOrchestratorCollectorResult(
            OperationContext context,
            AgentModels.OrchestratorCollectorResult input
    ) {
        resolveState(context, state -> {
            OrchestratorNode orchestratorNode =
                    requireNode(state.orchestratorNodeId(), OrchestratorNode.class, "orchestrator");
            String consolidated = input != null ? input.consolidatedOutput() : "";
            OrchestratorNode withOutput = consolidated != null && !consolidated.isBlank()
                    ? orchestratorNode.withOutput(consolidated)
                    : orchestratorNode;
            graphRepository.save(withOutput);
            if (state.orchestratorCollectorNodeId() != null) {
                findNode(state.orchestratorCollectorNodeId(), CollectorNode.class)
                        .map(node -> input != null ? node.withResult(input) : node)
                        .map(this::markNodeCompleted)
                        .ifPresent(graphRepository::save);
            }
            markNodeCompleted(withOutput);
            return null;
        });
    }

    public CollectorNode startOrchestratorCollector(
            OperationContext context,
            AgentModels.OrchestratorCollectorRequest input
    ) {
        OrchestratorNode orchestratorNode = requireOrchestrator(context);
        CollectorNode collectorNode = nodeFactory.orchestratorCollectorNode(orchestratorNode, input.goal(), input.contextId());
        computationGraphOrchestrator.addChildNodeAndEmitEvent(
                orchestratorNode.nodeId(),
                collectorNode
        );
        updateState(context, s -> s.withOrchestratorCollectorNodeId(collectorNode.nodeId()));
        return markNodeRunning(collectorNode);
    }

    public void completeOrchestratorCollector(
            OperationContext context,
            CollectorNode running,
            AgentModels.OrchestratorCollectorRouting routing
    ) {
        OrchestratorNode orchestratorNode = requireOrchestrator(context);
        AgentModels.OrchestratorCollectorResult collectorResult =
                routing != null ? routing.collectorResult() : null;
        String output = collectorResult != null ? collectorResult.consolidatedOutput() : "";
        CollectorNode withResult = running.withResult(collectorResult).withOutput(output);
        graphRepository.save(withResult);
        if (handleRoutingInterrupt(withResult, routing != null ? routing.interruptRequest() : null, output)) {
            return;
        }
        markNodeCompleted(withResult);
        if (output != null && !output.isBlank()) {
            graphRepository.save(orchestratorNode.withOutput(output));
        }
    }

    public void handleOrchestratorInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest.OrchestratorInterruptRequest request
    ) {
        OrchestratorNode orchestratorNode = requireOrchestrator(context);
        handleRoutingInterrupt(orchestratorNode, request, request != null ? request.reason() : "");
    }

    public void handleContextManagerInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest.ContextManagerInterruptRequest request
    ) {
        OrchestratorNode orchestratorNode = requireOrchestrator(context);
        handleRoutingInterrupt(orchestratorNode, request, request != null ? request.reason() : "");
    }

    public DiscoveryOrchestratorNode startDiscoveryOrchestrator(
            OperationContext context,
            AgentModels.DiscoveryOrchestratorRequest input
    ) {
        var d = resolveState(context, s -> {
            String parentId = firstNonBlank(s.orchestratorCollectorNodeId(), s.orchestratorNodeId());
            var discoveryNode = nodeFactory.discoveryOrchestratorNode(parentId, input.goal(), input.contextId());
            computationGraphOrchestrator.addChildNodeAndEmitEvent(parentId, discoveryNode);
            updateState(context,st ->  st.withDiscoveryOrchestratorNodeId(discoveryNode.nodeId()));
            return discoveryNode;
        });
        return markNodeRunning(d);
    }

    public void completeDiscoveryOrchestrator(
            DiscoveryOrchestratorNode running,
            AgentModels.DiscoveryOrchestratorRouting routing
    ) {
        String summary = routing != null ? routing.toString() : "";
        DiscoveryOrchestratorNode updated = running.withContent(summary);
        graphRepository.save(updated);
        handleRoutingInterrupt(updated, routing != null ? routing.interruptRequest() : null, summary);
    }

    public void handleDiscoveryInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest request
    ) {
        resolveState(context, state -> {
            if (state.discoveryOrchestratorNodeId() == null) {
                return null;
            }
            findNode(state.discoveryOrchestratorNodeId(), DiscoveryOrchestratorNode.class)
                    .ifPresent(node -> handleRoutingInterrupt(node, request, request != null ? request.reason() : ""));
            return null;
        });
    }

    public Optional<GraphNode> findNodeForContext(OperationContext context) {
        String nodeId = resolveNodeId(context);
        if (nodeId == null || nodeId.isBlank() || "unknown".equals(nodeId)) {
            return Optional.empty();
        }
        return graphRepository.findById(nodeId);
    }

    public void handleAgentInterrupt(
            GraphNode originNode,
            AgentModels.InterruptRequest request
    ) {
        if (originNode == null || request == null) {
            log.error("Found agent interrupt where origin node {} request node {} was null.", originNode, request);
            return;
        }
        handleRoutingInterrupt(
                originNode,
                request,
                firstNonBlank(request.reason(), request.contextForDecision())
        );
    }

    public DiscoveryNode startDiscoveryAgent(
            DiscoveryOrchestratorNode parent,
            String goal,
            String focus,
            AgentModels.DiscoveryAgentRequest enrichedRequest) {
        DiscoveryNode discoveryNode = nodeFactory.discoveryNode(
                parent.nodeId(),
                goal,
                "Discover: " + focus,
                enrichedRequest,
                enrichedRequest != null ? enrichedRequest.contextId() : null
        );
        return startChildNode(parent.nodeId(), discoveryNode);
    }

    public void completeDiscoveryAgent(AgentModels.DiscoveryAgentResult response,
                                       String nodeId) {
        var runningOpt = graphRepository.findById(nodeId);
        if (runningOpt.isEmpty())
            return;
        if (response == null) {
            markNodeCompleted(runningOpt.get());
            return;
        }

        if (runningOpt.get() instanceof DiscoveryNode running) {
            DiscoveryNode completed = running.withResult(response).withContent(response.output());
            graphRepository.save(completed);
            markNodeCompleted(completed);
        }
    }

    public DiscoveryCollectorNode startDiscoveryCollector(
            OperationContext context,
            AgentModels.DiscoveryCollectorRequest input
    ) {
        DiscoveryOrchestratorNode discoveryParent = requireDiscoveryOrchestrator(context);
        DiscoveryCollectorNode collectorNode = nodeFactory.discoveryCollectorNode(
                discoveryParent.nodeId(),
                input.goal(),
                input.contextId()
        );
        computationGraphOrchestrator.addChildNodeAndEmitEvent(
                discoveryParent.nodeId(),
                collectorNode
        );
        updateState(context, state -> state.withDiscoveryCollectorNodeId(collectorNode.nodeId()));
        return markNodeRunning(collectorNode);
    }

    public void completeDiscoveryCollector(
            OperationContext context,
            DiscoveryCollectorNode running,
            AgentModels.DiscoveryCollectorRouting routing
    ) {
        DiscoveryOrchestratorNode discoveryParent = requireDiscoveryOrchestrator(context);
        AgentModels.DiscoveryCollectorResult collectorResult =
                routing != null ? routing.collectorResult() : null;
        String output = collectorResult != null ? collectorResult.consolidatedOutput() : "";
        List<CollectedNodeStatus> collectedNodes =
                collectSiblingStatusSnapshots(discoveryParent.nodeId(), DiscoveryNode.class);
        DiscoveryCollectorNode withResult = running
                .withResult(collectorResult)
                .withContent(output)
                .withCollectedNodes(collectedNodes);
        graphRepository.save(withResult);
        if (handleRoutingInterrupt(withResult, routing != null ? routing.interruptRequest() : null, output)) {
            return;
        }
        markNodeCompleted(withResult);
        DiscoveryOrchestratorNode updatedParent = discoveryParent.withContent(output);
        graphRepository.save(updatedParent);
        markNodeCompleted(updatedParent);
    }

    public PlanningOrchestratorNode startPlanningOrchestrator(
            OperationContext context,
            AgentModels.PlanningOrchestratorRequest input
    ) {
         return resolveState(context, state -> {
             String parentId = firstNonBlank(
                     state.discoveryCollectorNodeId(),
                     state.orchestratorCollectorNodeId(),
                     state.orchestratorNodeId()
             );
             String discoveryContext = "";
             if (state.discoveryCollectorNodeId() != null) {
                 discoveryContext = findNode(state.discoveryCollectorNodeId(), DiscoveryCollectorNode.class)
                         .map(DiscoveryCollectorNode::getView)
                         .orElse("");
             }
             PlanningOrchestratorNode planningNode = nodeFactory.planningOrchestratorNode(
                     parentId,
                     input.goal(),
                     Map.of(META_DISCOVERY_CONTEXT, discoveryContext),
                     input.contextId()
             );
             computationGraphOrchestrator.addChildNodeAndEmitEvent(parentId, planningNode);
             updateState(context, s -> s.withPlanningOrchestratorNodeId(planningNode.nodeId()));
             return markNodeRunning(planningNode);
         });
    }

    public void completePlanningOrchestrator(
            PlanningOrchestratorNode running,
            AgentModels.PlanningOrchestratorRouting routing
    ) {
        String summary = routing != null ? routing.toString() : "";
        PlanningOrchestratorNode updated = running.withPlanContent(summary);
        graphRepository.save(updated);
        handleRoutingInterrupt(updated, routing != null ? routing.interruptRequest() : null, summary);
    }

    public void handlePlanningInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest request
    ) {
        resolveState(context, state -> {
            if (state.planningOrchestratorNodeId() == null) {
                return null;
            }
            findNode(state.planningOrchestratorNodeId(), PlanningOrchestratorNode.class)
                    .ifPresent(node -> handleRoutingInterrupt(node, request, request != null ? request.reason() : ""));
            return null;
        });
    }

    public PlanningNode startPlanningAgent(
            PlanningOrchestratorNode parent,
            String goal,
            String title,
            ArtifactKey artifactKey
    ) {
        String discoveryContext = parent.metadata().getOrDefault(META_DISCOVERY_CONTEXT, "");
        PlanningNode planningNode = nodeFactory.planningNode(
                parent.nodeId(),
                goal,
                title,
                Map.of(META_DISCOVERY_CONTEXT, discoveryContext),
                artifactKey
        );
        return startChildNode(parent.nodeId(), planningNode);
    }

    public PlanningNode startPlanningAgent(
            PlanningOrchestratorNode parent,
            AgentModels.PlanningAgentRequest request
    ) {
        int index = nextChildIndex(parent.nodeId(), PlanningNode.class);
        String title = "Plan segment " + index;
        return startPlanningAgent(parent, Objects.toString(request.goal(), ""), title, request != null ? request.contextId() : null);
    }

    public void completePlanningAgent(PlanningNode running, AgentModels.PlanningAgentResult response) {
        if (response == null) {
            markNodeCompleted(running);
            return;
        }
        PlanningNode completed = running.withResult(response).withPlanContent(response.output());
        graphRepository.save(completed);
        markNodeCompleted(completed);
    }

    public PlanningCollectorNode startPlanningCollector(
            OperationContext context,
            AgentModels.PlanningCollectorRequest input
    ) {
        PlanningOrchestratorNode planningParent = requirePlanningOrchestrator(context);
        PlanningCollectorNode collectorNode = nodeFactory.planningCollectorNode(
                planningParent.nodeId(),
                input.goal(),
                input.contextId()
        );
        computationGraphOrchestrator.addChildNodeAndEmitEvent(
                planningParent.nodeId(),
                collectorNode
        );
        updateState(context, state -> state.withPlanningCollectorNodeId(collectorNode.nodeId()));
        return markNodeRunning(collectorNode);
    }

    public void completePlanningCollector(
            OperationContext context,
            PlanningCollectorNode running,
            AgentModels.PlanningCollectorRouting routing
    ) {
        PlanningOrchestratorNode planningParent = requirePlanningOrchestrator(context);
        AgentModels.PlanningCollectorResult collectorResult =
                routing != null ? routing.collectorResult() : null;
        String output = collectorResult != null ? collectorResult.consolidatedOutput() : "";
        List<CollectedNodeStatus> collectedNodes =
                collectSiblingStatusSnapshots(planningParent.nodeId(), PlanningNode.class);
        PlanningCollectorNode withResult = running
                .withResult(collectorResult)
                .withPlanContent(output)
                .withCollectedNodes(collectedNodes);
        graphRepository.save(withResult);
        if (handleRoutingInterrupt(withResult, routing != null ? routing.interruptRequest() : null, output)) {
            return;
        }
        markNodeCompleted(withResult);
        PlanningOrchestratorNode updatedParent = planningParent.withPlanContent(output);
        graphRepository.save(updatedParent);
        markNodeCompleted(updatedParent);
    }

    public TicketOrchestratorNode startTicketOrchestrator(
            OperationContext context,
            AgentModels.TicketOrchestratorRequest input
    ) {
        return resolveState(context, state -> {
            String parentId = firstNonBlank(
                    state.planningCollectorNodeId(),
                    state.orchestratorCollectorNodeId(),
                    state.orchestratorNodeId()
            );
            OrchestratorNode root = requireOrchestrator(context);
            String parentWorktreeId = root.mainWorktreeId();
            String ticketBranchName = "ticket-orch-" + shortId(parentId);
            String ticketMainWorktreeId = root.mainWorktreeId();
            List<HasWorktree.WorkTree> branchedSubmodules = new ArrayList<>();
            try {
                MainWorktreeContext branched = worktreeService.branchWorktree(
                        root.mainWorktreeId(),
                        ticketBranchName,
                        parentId
                );
                ticketMainWorktreeId = branched.worktreeId();
                if (root.submoduleWorktreeIds() != null) {
                    branchedSubmodules = root.submoduleWorktreeIds()
                            .stream()
                            .map(submoduleId -> new HasWorktree.WorkTree(
                                    worktreeService.branchSubmoduleWorktree(
                                            submoduleId,
                                            ticketBranchName,
                                            parentId
                                    ).worktreeId(),
                                    submoduleId,
                                    new ArrayList<>()
                            ))
                            .toList();
                }
            } catch (Exception e) {
                ticketMainWorktreeId = root.mainWorktreeId();
            }
            Map<String, String> metadata = new ConcurrentHashMap<>();
            metadata.put(META_DISCOVERY_CONTEXT, input.discoveryCuration() != null ? input.discoveryCuration().prettyPrint() : "");
            metadata.put(META_PLANNING_CONTEXT, input.planningCuration() != null ? input.planningCuration().prettyPrint() : "");
            metadata.put(META_PARENT_WORKTREE, Optional.ofNullable(parentWorktreeId).orElse(""));
            TicketOrchestratorNode ticketNode = nodeFactory.ticketOrchestratorNode(
                    parentId,
                    input.goal(),
                    metadata,
                    new HasWorktree.WorkTree(ticketMainWorktreeId, root.mainWorktreeId(), branchedSubmodules),
                    input.contextId()
            );
            computationGraphOrchestrator.addChildNodeAndEmitEvent(parentId, ticketNode);
            updateState(context, s -> s.withTicketOrchestratorNodeId(ticketNode.nodeId()));
            return markNodeRunning(ticketNode);
        });
    }

    public void completeTicketOrchestrator(
            TicketOrchestratorNode running,
            AgentModels.TicketOrchestratorRouting routing
    ) {
        String output = routing != null ? routing.toString() : "";
        TicketOrchestratorNode updated = running.withOutput(output, 0);
        graphRepository.save(updated);
        handleRoutingInterrupt(updated, routing != null ? routing.interruptRequest() : null, output);
    }

    public void handleTicketInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest request
    ) {
        resolveState(context, state -> {
            if (state.ticketOrchestratorNodeId() == null) {
                return null;
            }
            findNode(state.ticketOrchestratorNodeId(), TicketOrchestratorNode.class)
                    .ifPresent(node -> handleRoutingInterrupt(node, request, request != null ? request.reason() : ""));
            return null;
        });
    }

    public TicketNode startTicketAgent(
            TicketOrchestratorNode parent,
            AgentModels.TicketAgentRequest request,
            int index
    ) {
        String ticketBranchName = "ticket-" + index + "-" + shortId(parent.nodeId());
        String branchedWorktreeId = parent.mainWorktreeId();
        List<HasWorktree.WorkTree> submoduleWorktrees = parent.submoduleWorktreeIds();
        try {
            MainWorktreeContext branched = worktreeService.branchWorktree(
                    parent.mainWorktreeId(),
                    ticketBranchName,
                    parent.nodeId()
            );
            branchedWorktreeId = branched.worktreeId();
            submoduleWorktrees = parent.submoduleWorktreeIds()
                    .stream()
                    .map(id -> new HasWorktree.WorkTree(
                            worktreeService.branchSubmoduleWorktree(
                                    id.worktreeId(),
                                    ticketBranchName,
                                    parent.nodeId()
                            ).worktreeId(),
                            id.worktreeId(),
                            new ArrayList<>()
                    ))
                    .toList();
        } catch (Exception e) {
            branchedWorktreeId = parent.mainWorktreeId();
        }
        Map<String, String> metadata = new ConcurrentHashMap<>();
        metadata.put(META_DISCOVERY_CONTEXT, parent.metadata().getOrDefault(META_DISCOVERY_CONTEXT, ""));
        metadata.put(META_PLANNING_CONTEXT, parent.metadata().getOrDefault(META_PLANNING_CONTEXT, ""));
        String title = firstNonBlank(request.ticketDetailsFilePath(), "Ticket " + index);
        TicketNode ticketNode = nodeFactory.ticketNode(
                parent.nodeId(),
                title,
                Objects.toString(request.ticketDetails(), ""),
                metadata,
                new HasWorktree.WorkTree(
                        branchedWorktreeId,
                        parent.mainWorktreeId(),
                        submoduleWorktrees
                ),
                request != null ? request.contextId() : null
        );
        return startChildNode(parent.nodeId(), ticketNode);
    }

    public TicketNode startTicketAgent(
            TicketOrchestratorNode parent,
            AgentModels.TicketAgentRequest request
    ) {
        int index = nextChildIndex(parent.nodeId(), TicketNode.class);
        return startTicketAgent(parent, request, index);
    }

    public void completeTicketAgent(TicketNode running, AgentModels.TicketAgentResult response) {
        if (response == null) {
            markNodeCompleted(running);
            return;
        }
        TicketNode completed = running.withTicketAgentResult(response).withOutput(response.output(), 0);
        graphRepository.save(completed);
        markNodeCompleted(completed);
    }

    public TicketCollectorNode startTicketCollector(
            OperationContext context,
            AgentModels.TicketCollectorRequest input
    ) {
        TicketOrchestratorNode ticketParent = requireTicketOrchestrator(context);
        TicketCollectorNode collectorNode = nodeFactory.ticketCollectorNode(
                ticketParent.nodeId(),
                input.goal(),
                input.contextId()
        );
        computationGraphOrchestrator.addChildNodeAndEmitEvent(
                ticketParent.nodeId(),
                collectorNode
        );
        updateState(context, state -> state.withTicketCollectorNodeId(collectorNode.nodeId()));
        return markNodeRunning(collectorNode);
    }

    public void completeTicketCollector(
            OperationContext context,
            TicketCollectorNode running,
            AgentModels.TicketCollectorRouting routing
    ) {
        TicketOrchestratorNode ticketParent = requireTicketOrchestrator(context);
        AgentModels.TicketCollectorResult collectorResult =
                routing != null ? routing.collectorResult() : null;
        String summary = collectorResult != null ? collectorResult.consolidatedOutput() : "";
        List<CollectedNodeStatus> collectedNodes =
                collectSiblingStatusSnapshots(ticketParent.nodeId(), TicketNode.class);
        TicketCollectorNode withResult = running
                .withResult(collectorResult)
                .withSummary(summary)
                .withCollectedNodes(collectedNodes);
        graphRepository.save(withResult);
        if (handleRoutingInterrupt(withResult, routing != null ? routing.interruptRequest() : null, summary)) {
            return;
        }
        markNodeCompleted(withResult);
        TicketOrchestratorNode updatedParent = ticketParent.withOutput(summary, 0);
        graphRepository.save(updatedParent);
        markNodeCompleted(updatedParent);
    }

    public ReviewNode startReview(OperationContext context, AgentModels.ReviewRequest input) {
        return resolveState(context, state -> {
            String parentId = resolveReviewParentId(input, state);
            ReviewNode reviewNode = nodeFactory.reviewNode(
                    parentId,
                    Objects.toString(input.content(), ""),
                    Objects.toString(input.content(), ""),
                    "agent-review",
                    input.contextId()
            );
            computationGraphOrchestrator.addChildNodeAndEmitEvent(parentId, reviewNode);
            updateState(context, s -> s.withReviewNodeId(reviewNode.nodeId()));
            return markNodeRunning(reviewNode);
        });
    }

    public void completeReview(ReviewNode running, AgentModels.ReviewRouting response) {
        AgentModels.ReviewAgentResult reviewResult = response != null ? response.reviewResult() : null;
        String reviewOutput = reviewResult != null ? reviewResult.output() : "";
        boolean approved = reviewOutput.toLowerCase().contains("approved");
        boolean humanNeeded = reviewOutput.toLowerCase().contains("human");
        ReviewNode withResult = running
                .withResult(reviewResult)
                .withReviewDecision(approved, reviewOutput);
        graphRepository.save(withResult);
        if (handleRoutingInterrupt(withResult, response != null ? response.interruptRequest() : null, reviewOutput)) {
            return;
        }
        if (humanNeeded && !approved) {
            ReviewNode waiting = updateNodeStatus(
                    withResult,
                    Events.NodeStatus.WAITING_INPUT
            );
            graphRepository.save(waiting);
            computationGraphOrchestrator.emitStatusChangeEvent(
                    waiting.nodeId(),
                    Events.NodeStatus.RUNNING,
                    Events.NodeStatus.WAITING_INPUT,
                    "Human feedback requested"
            );
            computationGraphOrchestrator.emitReviewRequestedEvent(
                    waiting.nodeId(),
                    waiting.nodeId(),
                    Events.ReviewType.HUMAN,
                    waiting.reviewContent()
            );
        } else {
            markNodeCompleted(withResult);
        }
    }

    public void handleReviewInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest.ReviewInterruptRequest request
    ) {
        resolveState(context, state -> {
            if (state.reviewNodeId() == null) {
                return null;
            }
            findNode(state.reviewNodeId(), ReviewNode.class)
                    .ifPresent(node -> handleRoutingInterrupt(node, request, request != null ? request.reason() : ""));
            return null;
        });
    }

    public MergeNode startMerge(OperationContext context, AgentModels.MergerRequest input) {
        return resolveState(context, state -> {
            String parentId = resolveMergeParentId(input, state);
            MergeNode mergeNode = nodeFactory.mergeNode(
                    parentId,
                    Objects.toString(input.mergeSummary(), ""),
                    Objects.toString(input.mergeSummary(), ""),
                    Map.of(),
                    input.contextId()
            );
            computationGraphOrchestrator.addChildNodeAndEmitEvent(parentId, mergeNode);
            updateState(context, s -> s.withMergeNodeId(mergeNode.nodeId()));
            return markNodeRunning(mergeNode);
        });
    }

    public void completeMerge(
            MergeNode running,
            AgentModels.MergerRouting response,
            String combinedSummary
    ) {
        AgentModels.MergerAgentResult mergeResult = response != null ? response.mergerResult() : null;
        MergeNode withResult = running.withResult(mergeResult).withContent(combinedSummary);
        graphRepository.save(withResult);
        if (handleRoutingInterrupt(withResult, response != null ? response.interruptRequest() : null, combinedSummary)) {
            return;
        }
        markNodeCompleted(withResult);
    }

    public void handleMergerInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest.MergerInterruptRequest request
    ) {
        resolveState(context, state -> {
            if (state.mergeNodeId() == null) {
                return null;
            }
            findNode(state.mergeNodeId(), MergeNode.class)
                    .ifPresent(node -> handleRoutingInterrupt(node, request, request != null ? request.reason() : ""));
            return null;
        });
    }

    private <T extends GraphNode> T startChildNode(String parentId, T node) {
        computationGraphOrchestrator.addChildNodeAndEmitEvent(parentId, node);
        return markNodeRunning(node);
    }

    private <T extends GraphNode> Optional<T> findNode(String nodeId, Class<T> type) {
        if (nodeId == null || nodeId.isBlank()) {
            return Optional.empty();
        }
        return graphRepository.findById(nodeId)
                .filter(type::isInstance)
                .map(type::cast);
    }

    private <T extends GraphNode> T requireNode(String nodeId, Class<T> type, String label) {
        return findNode(nodeId, type)
                .orElseThrow(() -> new IllegalStateException("Missing " + label + " node: " + nodeId));
    }

    private <T extends GraphNode> T markNodeRunning(T node) {
        GraphNode runningNode = updateNodeStatus(
                node,
                Events.NodeStatus.RUNNING
        );
        graphRepository.save(runningNode);
        computationGraphOrchestrator.emitStatusChangeEvent(
                node.nodeId(),
                node.status(),
                Events.NodeStatus.RUNNING,
                "Agent execution started"
        );
        return (T) runningNode;
    }

    private <T extends GraphNode> T markNodeCompleted(T node) {
        GraphNode completedNode = updateNodeStatus(
                node,
                Events.NodeStatus.COMPLETED
        );
        graphRepository.save(completedNode);
        computationGraphOrchestrator.emitStatusChangeEvent(
                node.nodeId(),
                node.status(),
                Events.NodeStatus.COMPLETED,
                "Agent execution completed successfully"
        );
        return (T) completedNode;
    }

    private <T extends GraphNode> T updateNodeStatus(
            T node,
            Events.NodeStatus newStatus
    ) {
        return (T) node.withStatus(newStatus);
    }

    private boolean handleRoutingInterrupt(
            GraphNode node,
            AgentModels.InterruptRequest interruptRequest,
            String resultPayload
    ) {
        if (interruptRequest == null) {
            return false;
        }
        InterruptContext interruptContext = new InterruptContext(
                interruptRequest.type(),
                InterruptContext.InterruptStatus.REQUESTED,
                interruptRequest.reason(),
                node.nodeId(),
                node.nodeId(),
                null,
                resultPayload
        );
        GraphNode withInterrupt = updateNodeInterruptibleContext(node, interruptContext);
        graphRepository.save(withInterrupt);
        computationGraphOrchestrator.emitInterruptStatusEvent(
                node.nodeId(),
                interruptRequest.type().name(),
                interruptContext.status().name(),
                interruptContext.originNodeId(),
                interruptContext.resumeNodeId()
        );

        Events.NodeStatus newStatus = switch (interruptRequest.type()) {
            case PAUSE, HUMAN_REVIEW, BRANCH -> Events.NodeStatus.WAITING_INPUT;
            case AGENT_REVIEW -> Events.NodeStatus.WAITING_REVIEW;
            case STOP -> Events.NodeStatus.CANCELED;
            case PRUNE -> Events.NodeStatus.PRUNED;
        };
        GraphNode updated = updateNodeStatus(withInterrupt, newStatus);
        graphRepository.save(updated);
        computationGraphOrchestrator.emitStatusChangeEvent(
                node.nodeId(),
                node.status(),
                newStatus,
                interruptRequest.reason()
        );
        emitInterruptNode(updated, interruptContext);
        return true;
    }

    private void emitInterruptNode(GraphNode node, InterruptContext context) {
        if (context.interruptNodeId() != null && !context.interruptNodeId().isBlank()) {
            return;
        }
        String interruptNodeId = nodeFactory.newNodeId();
        InterruptContext emittedContext = context.withInterruptNodeId(interruptNodeId);
        GraphNode interruptNode = switch (context.type()) {
            case HUMAN_REVIEW, AGENT_REVIEW -> buildReviewInterruptNode(node, emittedContext);
            default -> buildInterruptNode(node, emittedContext);
        };
        computationGraphOrchestrator.addChildNodeAndEmitEvent(
                node.nodeId(),
                interruptNode
        );
        GraphNode updatedOrigin = updateNodeInterruptibleContext(node, emittedContext);
        graphRepository.save(updatedOrigin);
    }

    private GraphNode updateNodeInterruptibleContext(GraphNode node, InterruptContext context) {
        return switch (node) {
            case DiscoveryNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case DiscoveryCollectorNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case DiscoveryOrchestratorNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case PlanningNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case PlanningCollectorNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case PlanningOrchestratorNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case TicketNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case TicketCollectorNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case TicketOrchestratorNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case ReviewNode n -> n.toBuilder().interruptContext(context).lastUpdatedAt(Instant.now()).build();
            case MergeNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case OrchestratorNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case CollectorNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case SummaryNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case InterruptNode n -> n.toBuilder().interruptContext(context).lastUpdatedAt(Instant.now()).build();
            case AskPermissionNode n -> n.toBuilder().lastUpdatedAt(Instant.now()).build();
        };
    }

    private InterruptNode buildInterruptNode(GraphNode node, InterruptContext context) {
        Instant now = Instant.now();
        return new InterruptNode(
                context.interruptNodeId(),
                "Interrupt",
                node.goal(),
                Events.NodeStatus.READY,
                node.nodeId(),
                new ArrayList<>(),
                new ConcurrentHashMap<>(),
                now,
                now,
                context
        );
    }

    private ReviewNode buildReviewInterruptNode(GraphNode node, InterruptContext context) {
        String reviewerType = context.type() == Events.InterruptType.HUMAN_REVIEW
                ? "human"
                : "agent-review";
        Instant now = Instant.now();
        return new ReviewNode(
                context.interruptNodeId(),
                "Review: " + node.title(),
                node.goal(),
                context.type() == Events.InterruptType.HUMAN_REVIEW
                        ? Events.NodeStatus.WAITING_INPUT
                        : Events.NodeStatus.READY,
                node.nodeId(),
                new ArrayList<>(),
                new ConcurrentHashMap<>(),
                now,
                now,
                context.originNodeId(),
                context.reason(),
                false,
                false,
                "",
                reviewerType,
                null,
                null,
                context
        );
    }

    private <T extends GraphNode> List<CollectedNodeStatus> collectSiblingStatusSnapshots(
            String parentNodeId,
            Class<T> siblingType
    ) {
        List<CollectedNodeStatus> collected = new ArrayList<>();
        if (parentNodeId == null || parentNodeId.isBlank()) {
            return collected;
        }
        List<GraphNode> children = computationGraphOrchestrator.getChildNodes(parentNodeId);
        for (GraphNode child : children) {
            if (!siblingType.isInstance(child)) {
                continue;
            }
            collected.add(new CollectedNodeStatus(
                    child.nodeId(),
                    child.title(),
                    child.nodeType(),
                    child.status()
            ));
        }
        return collected;
    }

    private <T extends GraphNode> int nextChildIndex(String parentNodeId, Class<T> type) {
        if (parentNodeId == null || parentNodeId.isBlank()) {
            return 1;
        }
        List<GraphNode> children = computationGraphOrchestrator.getChildNodes(parentNodeId);
        long count = children.stream().filter(type::isInstance).count();
        return (int) count + 1;
    }

    private String resolveReviewParentId(AgentModels.ReviewRequest input, com.hayden.multiagentidelib.agent.WorkflowGraphState state) {
        if (input.returnToTicketCollector() != null) {
            return firstNonBlank(state.ticketOrchestratorNodeId(), state.ticketCollectorNodeId(), state.orchestratorNodeId());
        }
        if (input.returnToPlanningCollector() != null) {
            return firstNonBlank(state.planningOrchestratorNodeId(), state.planningCollectorNodeId(), state.orchestratorNodeId());
        }
        if (input.returnToDiscoveryCollector() != null) {
            return firstNonBlank(state.discoveryOrchestratorNodeId(), state.discoveryCollectorNodeId(), state.orchestratorNodeId());
        }
        if (input.returnToOrchestratorCollector() != null) {
            return firstNonBlank(state.orchestratorCollectorNodeId(), state.orchestratorNodeId());
        }
        return firstNonBlank(
                state.ticketOrchestratorNodeId(),
                state.planningOrchestratorNodeId(),
                state.discoveryOrchestratorNodeId(),
                state.orchestratorNodeId()
        );
    }

    private String resolveMergeParentId(AgentModels.MergerRequest input, com.hayden.multiagentidelib.agent.WorkflowGraphState state) {
        if (state.reviewNodeId() != null && !state.reviewNodeId().isBlank()) {
            return state.reviewNodeId();
        }
        if (input.returnToTicketCollector() != null) {
            return firstNonBlank(state.ticketOrchestratorNodeId(), state.ticketCollectorNodeId(), state.orchestratorNodeId());
        }
        if (input.returnToPlanningCollector() != null) {
            return firstNonBlank(state.planningOrchestratorNodeId(), state.planningCollectorNodeId(), state.orchestratorNodeId());
        }
        if (input.returnToDiscoveryCollector() != null) {
            return firstNonBlank(state.discoveryOrchestratorNodeId(), state.discoveryCollectorNodeId(), state.orchestratorNodeId());
        }
        if (input.returnToOrchestratorCollector() != null) {
            return firstNonBlank(state.orchestratorCollectorNodeId(), state.orchestratorNodeId());
        }
        return firstNonBlank(
                state.ticketOrchestratorNodeId(),
                state.planningOrchestratorNodeId(),
                state.discoveryOrchestratorNodeId(),
                state.orchestratorNodeId()
        );
    }

    private String resolveNodeId(OperationContext context) {
        if (context == null || context.getProcessContext() == null) {
            return "unknown";
        }
        var options = context.getProcessContext().getProcessOptions();
        if (options == null) {
            return "unknown";
        }
        String contextId = options.getContextIdString();
        return contextId != null ? contextId : "unknown";
    }

    private static String shortId(String id) {
        if (id == null) {
            return "";
        }
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
