package com.hayden.multiagentide.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.agent.WorkflowGraphService;
import com.hayden.multiagentide.agent.WorkflowGraphState;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.utilitymodule.acp.events.Events;
import com.hayden.multiagentidelib.model.nodes.*;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeContext;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.multiagentide.support.AgentTestBase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AgentRunnerWorkflowTest extends AgentTestBase {

    @Autowired
    private WorkflowGraphService workflowGraphService;

    @Autowired
    private GraphRepository graphRepository;

    @Autowired
    private WorktreeRepository worktreeRepository;

    @BeforeEach
    void setUp() {
        graphRepository.clear();
        worktreeRepository.clear();
    }

    @Test
    void reviewApprovalCreatesMergeNode() {
        TicketOrchestratorNode ticketOrchestrator = ticketOrchestrator("orchestrator-1", new HasWorktree.WorkTree("parent-wt", null, new ArrayList<>()));
        TicketNode ticketNode = ticketNode("ticket-1", ticketOrchestrator.nodeId(), new HasWorktree.WorkTree("child-wt", "parent-wt", new ArrayList<>()));
        ReviewNode reviewNode = reviewNode("review-1", ticketNode.nodeId());

        graphRepository.save(ticketOrchestrator);
        graphRepository.save(ticketNode);
        graphRepository.save(reviewNode);

        workflowGraphService.completeReview(
                reviewNode,
                new AgentModels.ReviewRouting(
                        null,
                        new AgentModels.ReviewAgentResult("approved"),
                        null,
                        null,
                        null,
                        null
                )
        );

        WorkflowGraphState state = new WorkflowGraphState(
                ticketOrchestrator.nodeId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                reviewNode.nodeId(),
                null
        );
        OperationContext context = Mockito.mock(OperationContext.class);
        when(context.last(WorkflowGraphState.class)).thenReturn(state);
        workflowGraphService.startMerge(
                context,
                new AgentModels.MergerRequest(
                        "merge context",
                        "merge summary",
                        "",
                        new AgentModels.OrchestratorCollectorRequest("Review ticket", "COMPLETE"),
                        null,
                        null,
                        null
                )
        );

        Optional<GraphNode> updatedReview = graphRepository.findById(reviewNode.nodeId());
        assertThat(updatedReview).isPresent();
        assertThat(updatedReview.get().status()).isEqualTo(Events.NodeStatus.COMPLETED);

        List<GraphNode> children = graphRepository.findByParentId(reviewNode.nodeId());
        assertThat(children).hasSize(1);
        assertThat(children.get(0)).isInstanceOf(MergeNode.class);

        MergeNode mergeNode = (MergeNode) children.get(0);
        assertThat(mergeNode.parentNodeId()).isEqualTo(reviewNode.nodeId());
    }

    @Test
    void reviewRejectionCreatesRevisionNode() {
        TicketOrchestratorNode ticketOrchestrator = ticketOrchestrator("orchestrator-2", new HasWorktree.WorkTree("parent-wt-2", null, new ArrayList<>()));
        TicketNode ticketNode = ticketNode("ticket-2", ticketOrchestrator.nodeId(), new HasWorktree.WorkTree("child-wt-2", "parent-wt-2", new ArrayList<>()));
        ReviewNode reviewNode = reviewNode("review-2", ticketNode.nodeId());

        graphRepository.save(ticketOrchestrator);
        graphRepository.save(ticketNode);
        graphRepository.save(reviewNode);

        workflowGraphService.completeReview(
                reviewNode,
                new AgentModels.ReviewRouting(
                        null,
                        new AgentModels.ReviewAgentResult("human review needed"),
                        null,
                        null,
                        null,
                        null
                )
        );

        GraphNode updatedReview = graphRepository.findById(reviewNode.nodeId()).orElseThrow();
        assertThat(updatedReview.status()).isEqualTo(Events.NodeStatus.WAITING_INPUT);
    }

    @Test
    void mergeConflictsMovesNodeToWaitingInput() throws Exception {
        String childWorktreeId = "child-wt-conflict";
        String parentWorktreeId = "parent-wt-conflict";
        Path worktreePath = Files.createTempDirectory("merge-conflict-wt");

        MainWorktreeContext childWorktree = new MainWorktreeContext(
            childWorktreeId,
            worktreePath,
            "main",
            WorktreeContext.WorktreeStatus.ACTIVE,
            parentWorktreeId,
            "node-1",
            Instant.now(),
            "abc123",
            "repo-url",
            false,
            new ArrayList<>(),
            new HashMap<>()
        );
        worktreeRepository.save(childWorktree);

        MergeNode mergeNode = new MergeNode(
            "merge-1",
            "Merge: Ticket 1",
            "Merge ticket",
            Events.NodeStatus.READY,
            "review-1",
            new ArrayList<>(),
            Map.of(
                "child_worktree_id", childWorktreeId,
                "target_worktree_id", parentWorktreeId,
                "merge_scope", "ticket"
            ),
            Instant.now(),
            Instant.now(),
            "",
            0,
            0
        );
        graphRepository.save(mergeNode);

        workflowGraphService.completeMerge(
                mergeNode,
                new AgentModels.MergerRouting(
                        new AgentModels.MergerInterruptRequest(
                                Events.InterruptType.HUMAN_REVIEW,
                                "conflicts"
                        ),
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                "conflicts"
        );

        GraphNode updated = graphRepository.findById(mergeNode.nodeId()).orElseThrow();
        assertThat(updated.status()).isEqualTo(Events.NodeStatus.WAITING_INPUT);

        WorktreeContext reloaded = worktreeRepository.findById(childWorktreeId).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(WorktreeContext.WorktreeStatus.ACTIVE);
    }

    private TicketOrchestratorNode ticketOrchestrator(String nodeId, HasWorktree.WorkTree mainWorktreeId) {
        return new TicketOrchestratorNode(
            nodeId,
            "Ticket Orchestrator",
            "Implement goal",
            Events.NodeStatus.READY,
            null,
            new ArrayList<>(),
            new HashMap<>(),
            Instant.now(),
            Instant.now(),
            mainWorktreeId,
            0,
            0,
            "ticket-orchestrator",
            "",
            true,
            0
        );
    }

    private TicketNode ticketNode(String nodeId, String parentId, HasWorktree.WorkTree mainWorktreeId) {
        return new TicketNode(
            nodeId,
            "Ticket 1",
            "Implement ticket",
            Events.NodeStatus.READY,
            parentId,
            new ArrayList<>(),
            new HashMap<>(),
            Instant.now(),
            Instant.now(),
            mainWorktreeId,
            0,
            0,
            "ticket-agent",
            "",
            true,
            0
        );
    }

    private ReviewNode reviewNode(String nodeId, String reviewedNodeId) {
        return new ReviewNode(
            nodeId,
            "Review",
            "Review ticket",
            Events.NodeStatus.READY,
            reviewedNodeId,
            new ArrayList<>(),
            new HashMap<>(),
            Instant.now(),
            Instant.now(),
            reviewedNodeId,
            "implementation",
            false,
            false,
            "",
            "agent-review",
            null
        );
    }
}
