package com.hayden.multiagentide.controller;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.repository.EventStreamRepository;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentidelib.model.nodes.*;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LlmDebugUiControllerWorkflowGraphTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventStreamRepository eventStreamRepository;

    @Autowired
    private GraphRepository graphRepository;

    private static final Instant NOW = Instant.now();

    /**
     * Simple test: OrchestratorNode with one SummaryNode child.
     * Verifies tree structure from GraphRepository and metrics from EventStreamRepository.
     */
    @Test
    void workflowGraph_returns_hierarchy_and_metrics_for_root_scope() throws Exception {
        ArtifactKey rootKey = ArtifactKey.createRoot();
        ArtifactKey childKey = rootKey.createChild();
        ArtifactKey chatKey = childKey.createChild();

        String rootId = rootKey.value();
        String childId = childKey.value();

        // --- Graph nodes ---
        OrchestratorNode orchestrator = OrchestratorNode.builder()
                .nodeId(rootId)
                .title("Orchestrator")
                .goal("Test goal")
                .status(Events.NodeStatus.RUNNING)
                .parentNodeId(null)
                .childNodeIds(new ArrayList<>(List.of(childId)))
                .createdAt(NOW.minusSeconds(120))
                .lastUpdatedAt(NOW.minusSeconds(100))
                .worktreeContext(testWorktreeContext(rootId))
                .build();
        graphRepository.save(orchestrator);

        SummaryNode summaryChild = SummaryNode.builder()
                .nodeId(childId)
                .title("Discovery Agent")
                .goal("Discover things")
                .status(Events.NodeStatus.RUNNING)
                .parentNodeId(rootId)
                .childNodeIds(new ArrayList<>())
                .createdAt(NOW.minusSeconds(110))
                .lastUpdatedAt(NOW.minusSeconds(80))
                .build();
        graphRepository.save(summaryChild);

        // --- Events for metrics ---
        // Chat session + message under child
        eventStreamRepository.save(new Events.ChatSessionCreatedEvent(
                uid(), NOW.minusSeconds(95), childId, chatKey));
        eventStreamRepository.save(new Events.AddMessageEvent(
                uid(), NOW.minusSeconds(90), childId, "Working on it."));
        // Thought + stream deltas under child
        eventStreamRepository.save(new Events.NodeThoughtDeltaEvent(
                uid(), NOW.minusSeconds(85), childId, chatKey, "Thinking", 7, true));
        eventStreamRepository.save(new Events.NodeStreamDeltaEvent(
                uid(), NOW.minusSeconds(80), childId, chatKey, "Output", 11, true));
        // Errors (one recent, one old)
        eventStreamRepository.save(new Events.NodeErrorEvent(
                uid(), NOW.minusSeconds(70), childId, "Discovery Agent",
                Events.NodeType.SUMMARY, "Recent failure"));
        eventStreamRepository.save(new Events.NodeErrorEvent(
                uid(), NOW.minusSeconds(500), childId, "Discovery Agent",
                Events.NodeType.SUMMARY, "Old failure"));
        // Action started for global stats
        eventStreamRepository.save(new Events.ActionStartedEvent(
                uid(), NOW.minusSeconds(100), childId, "WorkflowDiscoveryAgent", "discovery"));

        mockMvc.perform(get("/api/llm-debug/ui/workflow-graph")
                        .param("nodeId", childId)
                        .param("errorWindowSeconds", "180"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedNodeId").value(childId))
                .andExpect(jsonPath("$.rootNodeId").value(rootId))
                .andExpect(jsonPath("$.stats.totalNodes").value(2))
                .andExpect(jsonPath("$.stats.eventTypeCounts.ACTION_STARTED").value(1))
                .andExpect(jsonPath("$.stats.recentErrorCount").value(1))
                .andExpect(jsonPath("$.stats.recentErrorsByNodeType.SUMMARY").value(1))
                .andExpect(jsonPath("$.stats.chatSessionEvents").value(1))
                .andExpect(jsonPath("$.stats.chatMessageEvents").value(1))
                .andExpect(jsonPath("$.stats.thoughtTokens").value(7))
                .andExpect(jsonPath("$.stats.streamTokens").value(11))
                .andExpect(jsonPath("$.root.nodeId").value(rootId))
                .andExpect(jsonPath("$.root.children[0].nodeId").value(childId))
                .andExpect(jsonPath("$.root.children[0].metrics.nodeErrorCount").value(2))
                .andExpect(jsonPath("$.root.children[0].metrics.otherEvents").value(1))
                .andExpect(jsonPath("$.root.children[0].routeBackCount").value(0))
                .andExpect(jsonPath("$.root.children[0].metrics.pendingItems").isEmpty());
    }

    /**
     * Full workflow test with proper GraphNode types:
     *   OrchestratorNode (root)
     *   ├── DiscoveryOrchestratorNode
     *   │   ├── DiscoveryNode (has completed ReviewNode child — always shown)
     *   │   └── DiscoveryCollectorNode
     *   ├── PlanningOrchestratorNode (routeBackCount=1)
     *   │   ├── PlanningNode
     *   │   ├── PlanningCollectorNode
     *   │   └── MergeNode (always shown)
     *   └── TicketOrchestratorNode
     *       └── TicketNode (has pending AskPermissionNode + pending InterruptNode)
     *
     * Each agent node has its own chat session + tool call events underneath.
     */
    @Test
    void workflowGraph_full_hierarchy_with_review_merge_interrupt_permission() throws Exception {
        // Build artifact key hierarchy
        ArtifactKey rootKey = ArtifactKey.createRoot();
        ArtifactKey discOrchKey = rootKey.createChild();
        ArtifactKey discAgentKey = discOrchKey.createChild();
        ArtifactKey discCollectorKey = discOrchKey.createChild();
        ArtifactKey planOrchKey = rootKey.createChild();
        ArtifactKey planAgentKey = planOrchKey.createChild();
        ArtifactKey planCollectorKey = planOrchKey.createChild();
        ArtifactKey ticketOrchKey = rootKey.createChild();
        ArtifactKey ticketAgentKey = ticketOrchKey.createChild();

        String rootId = rootKey.value();
        String discOrchId = discOrchKey.value();
        String discAgentId = discAgentKey.value();
        String discCollectorId = discCollectorKey.value();
        String planOrchId = planOrchKey.value();
        String planAgentId = planAgentKey.value();
        String planCollectorId = planCollectorKey.value();
        String ticketOrchId = ticketOrchKey.value();
        String ticketAgentId = ticketAgentKey.value();

        // --- IDs for review, merge, permission, interrupt ---
        String reviewNodeId = uid();
        String mergeNodeId = uid();
        String permNodeId = uid();
        String permToolCallId = "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String interruptNodeId = uid();

        // ============================================
        //  GRAPH NODES
        // ============================================

        // Root Orchestrator
        OrchestratorNode orchestrator = OrchestratorNode.builder()
                .nodeId(rootId)
                .title("Orchestrator")
                .goal("Add health check endpoint")
                .status(Events.NodeStatus.RUNNING)
                .parentNodeId(null)
                .childNodeIds(new ArrayList<>(List.of(discOrchId, planOrchId, ticketOrchId)))
                .createdAt(NOW.minusSeconds(300))
                .lastUpdatedAt(NOW.minusSeconds(100))
                .worktreeContext(testWorktreeContext(rootId))
                .build();
        graphRepository.save(orchestrator);

        // Discovery Orchestrator
        DiscoveryOrchestratorNode discOrch = DiscoveryOrchestratorNode.builder()
                .nodeId(discOrchId)
                .title("Discovery Orchestrator")
                .goal("Discover repository")
                .status(Events.NodeStatus.COMPLETED)
                .parentNodeId(rootId)
                .childNodeIds(new ArrayList<>(List.of(discAgentId, discCollectorId)))
                .createdAt(NOW.minusSeconds(280))
                .lastUpdatedAt(NOW.minusSeconds(220))
                .build();
        graphRepository.save(discOrch);

        // Discovery Agent (with a completed ReviewNode child)
        DiscoveryNode discAgent = DiscoveryNode.builder()
                .nodeId(discAgentId)
                .title("Discover: Repository overview")
                .goal("Analyze repo structure")
                .status(Events.NodeStatus.COMPLETED)
                .parentNodeId(discOrchId)
                .childNodeIds(new ArrayList<>(List.of(reviewNodeId)))
                .createdAt(NOW.minusSeconds(270))
                .lastUpdatedAt(NOW.minusSeconds(250))
                .build();
        graphRepository.save(discAgent);

        // ReviewNode on discovery agent — completed, but should ALWAYS be shown in children
        ReviewNode reviewNode = ReviewNode.builder()
                .nodeId(reviewNodeId)
                .title("Review: Discovery output")
                .goal("Review discovery")
                .status(Events.NodeStatus.COMPLETED)
                .parentNodeId(discAgentId)
                .childNodeIds(new ArrayList<>())
                .createdAt(NOW.minusSeconds(255))
                .lastUpdatedAt(NOW.minusSeconds(252))
                .reviewedNodeId(discAgentId)
                .reviewContent("Discovery output looks correct")
                .approved(true)
                .humanFeedbackRequested(false)
                .agentFeedback("Approved")
                .reviewerAgentType("agent")
                .reviewCompletedAt(NOW.minusSeconds(252))
                .interruptContext(new InterruptContext(
                        Events.InterruptType.AGENT_REVIEW,
                        InterruptContext.InterruptStatus.RESOLVED,
                        "Review discovery output",
                        discAgentId, discAgentId, reviewNodeId, "approved"))
                .build();
        graphRepository.save(reviewNode);

        // Discovery Collector
        DiscoveryCollectorNode discCollector = DiscoveryCollectorNode.builder()
                .nodeId(discCollectorId)
                .title("Discovery Collector")
                .goal("Collect discovery results")
                .status(Events.NodeStatus.COMPLETED)
                .parentNodeId(discOrchId)
                .childNodeIds(new ArrayList<>())
                .createdAt(NOW.minusSeconds(240))
                .lastUpdatedAt(NOW.minusSeconds(220))
                .build();
        graphRepository.save(discCollector);

        // Planning Orchestrator (routeBackCount=1)
        PlanningOrchestratorNode planOrch = PlanningOrchestratorNode.builder()
                .nodeId(planOrchId)
                .title("Planning Orchestrator")
                .goal("Plan implementation")
                .status(Events.NodeStatus.COMPLETED)
                .parentNodeId(rootId)
                .childNodeIds(new ArrayList<>(List.of(planAgentId, planCollectorId, mergeNodeId)))
                .createdAt(NOW.minusSeconds(200))
                .lastUpdatedAt(NOW.minusSeconds(140))
                .workflowContext(new WorkflowContext(1))
                .build();
        graphRepository.save(planOrch);

        // Planning Agent
        PlanningNode planAgent = PlanningNode.builder()
                .nodeId(planAgentId)
                .title("Plan segment 1")
                .goal("Plan implementation")
                .status(Events.NodeStatus.COMPLETED)
                .parentNodeId(planOrchId)
                .childNodeIds(new ArrayList<>())
                .createdAt(NOW.minusSeconds(190))
                .lastUpdatedAt(NOW.minusSeconds(170))
                .build();
        graphRepository.save(planAgent);

        // Planning Collector
        PlanningCollectorNode planCollector = PlanningCollectorNode.builder()
                .nodeId(planCollectorId)
                .title("Planning Collector")
                .goal("Collect planning results")
                .status(Events.NodeStatus.COMPLETED)
                .parentNodeId(planOrchId)
                .childNodeIds(new ArrayList<>())
                .createdAt(NOW.minusSeconds(160))
                .lastUpdatedAt(NOW.minusSeconds(140))
                .build();
        graphRepository.save(planCollector);

        // MergeNode on planning orchestrator — always shown
        MergeNode mergeNode = MergeNode.builder()
                .nodeId(mergeNodeId)
                .title("Merge: Planning results")
                .goal("Merge planning")
                .status(Events.NodeStatus.COMPLETED)
                .parentNodeId(planOrchId)
                .childNodeIds(new ArrayList<>())
                .createdAt(NOW.minusSeconds(145))
                .lastUpdatedAt(NOW.minusSeconds(141))
                .summaryContent("Merged planning output")
                .build();
        graphRepository.save(mergeNode);

        // Ticket Orchestrator
        HasWorktree.WorkTree ticketWorktree = new HasWorktree.WorkTree("wt-ticket-1", null, List.of());
        TicketOrchestratorNode ticketOrch = TicketOrchestratorNode.builder()
                .nodeId(ticketOrchId)
                .title("Ticket Orchestrator")
                .goal("Execute tickets")
                .status(Events.NodeStatus.RUNNING)
                .parentNodeId(rootId)
                .childNodeIds(new ArrayList<>(List.of(ticketAgentId)))
                .createdAt(NOW.minusSeconds(120))
                .lastUpdatedAt(NOW.minusSeconds(50))
                .worktree(ticketWorktree)
                .build();
        graphRepository.save(ticketOrch);

        // Ticket Agent (with pending permission + pending interrupt)
        HasWorktree.WorkTree ticketAgentWorktree = new HasWorktree.WorkTree("wt-ticket-agent-1", "wt-ticket-1", List.of());
        TicketNode ticketAgent = TicketNode.builder()
                .nodeId(ticketAgentId)
                .title("Ticket 1")
                .goal("Implement health check")
                .status(Events.NodeStatus.RUNNING)
                .parentNodeId(ticketOrchId)
                .childNodeIds(new ArrayList<>(List.of(permNodeId, interruptNodeId)))
                .createdAt(NOW.minusSeconds(100))
                .lastUpdatedAt(NOW.minusSeconds(50))
                .worktree(ticketAgentWorktree)
                .build();
        graphRepository.save(ticketAgent);

        // Pending AskPermissionNode
        AskPermissionNode permNode = AskPermissionNode.builder()
                .nodeId(permNodeId)
                .title("Permission: write_file")
                .goal("Permission for tool call")
                .status(Events.NodeStatus.PENDING)
                .parentNodeId(ticketAgentId)
                .childNodeIds(new ArrayList<>())
                .createdAt(NOW.minusSeconds(55))
                .lastUpdatedAt(NOW.minusSeconds(55))
                .toolCallId(permToolCallId)
                .optionIds(List.of("ALLOW_ONCE", "DENY"))
                .build();
        graphRepository.save(permNode);

        // Pending InterruptNode
        InterruptContext pendingInterrupt = new InterruptContext(
                Events.InterruptType.AGENT_REVIEW,
                InterruptContext.InterruptStatus.REQUESTED,
                "Route back requested for ticket agent",
                ticketAgentId, ticketAgentId, interruptNodeId, null);
        InterruptNode interruptNode = InterruptNode.builder()
                .nodeId(interruptNodeId)
                .title("Interrupt: Route back")
                .goal("Route back")
                .status(Events.NodeStatus.PENDING)
                .parentNodeId(ticketAgentId)
                .childNodeIds(new ArrayList<>())
                .createdAt(NOW.minusSeconds(52))
                .lastUpdatedAt(NOW.minusSeconds(52))
                .interruptContext(pendingInterrupt)
                .build();
        graphRepository.save(interruptNode);

        // ============================================
        //  EVENTS (for metrics — each agent has a chat session + activity)
        // ============================================

        // Discovery agent: session + thought
        ArtifactKey discChatKey = discAgentKey.createChild();
        eventStreamRepository.save(new Events.ChatSessionCreatedEvent(
                uid(), NOW.minusSeconds(268), discAgentId, discChatKey));
        eventStreamRepository.save(new Events.NodeThoughtDeltaEvent(
                uid(), NOW.minusSeconds(260), discAgentId, discChatKey, "thinking", 190, true));
        eventStreamRepository.save(new Events.ToolCallEvent(
                uid(), NOW.minusSeconds(255), discAgentId, discChatKey,
                uid(), "read_file", "tool", "complete", "update",
                List.of(), List.of(), Map.of("path", "README.md"), null));

        // Discovery collector: session + stream
        ArtifactKey discCollChatKey = discCollectorKey.createChild();
        eventStreamRepository.save(new Events.ChatSessionCreatedEvent(
                uid(), NOW.minusSeconds(238), discCollectorId, discCollChatKey));
        eventStreamRepository.save(new Events.NodeStreamDeltaEvent(
                uid(), NOW.minusSeconds(230), discCollectorId, discCollChatKey, "collector output", 533, true));

        // Planning agent: session + stream
        ArtifactKey planChatKey = planAgentKey.createChild();
        eventStreamRepository.save(new Events.ChatSessionCreatedEvent(
                uid(), NOW.minusSeconds(188), planAgentId, planChatKey));
        eventStreamRepository.save(new Events.NodeStreamDeltaEvent(
                uid(), NOW.minusSeconds(180), planAgentId, planChatKey, "plan output", 525, true));

        // Planning collector: session + stream
        ArtifactKey planCollChatKey = planCollectorKey.createChild();
        eventStreamRepository.save(new Events.ChatSessionCreatedEvent(
                uid(), NOW.minusSeconds(158), planCollectorId, planCollChatKey));
        eventStreamRepository.save(new Events.NodeStreamDeltaEvent(
                uid(), NOW.minusSeconds(150), planCollectorId, planCollChatKey, "collector output", 833, true));

        // Ticket agent: session + thought + tool call
        ArtifactKey ticketChatKey = ticketAgentKey.createChild();
        eventStreamRepository.save(new Events.ChatSessionCreatedEvent(
                uid(), NOW.minusSeconds(98), ticketAgentId, ticketChatKey));
        eventStreamRepository.save(new Events.NodeThoughtDeltaEvent(
                uid(), NOW.minusSeconds(90), ticketAgentId, ticketChatKey, "working", 124, true));
        eventStreamRepository.save(new Events.ToolCallEvent(
                uid(), NOW.minusSeconds(60), ticketAgentId, ticketChatKey,
                uid(), "write_file", "tool", "complete", "update",
                List.of(), List.of(), Map.of("path", "src/main/java/HealthCheck.java"), null));

        // ============================================
        //  ASSERTIONS
        // ============================================

        mockMvc.perform(get("/api/llm-debug/ui/workflow-graph")
                        .param("nodeId", rootId)
                        .param("errorWindowSeconds", "180"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootNodeId").value(rootId))

                // Total nodes: orch + discOrch + discAgent + reviewNode + discCollector
                //   + planOrch + planAgent + planCollector + mergeNode
                //   + ticketOrch + ticketAgent + permNode + interruptNode = 13
                .andExpect(jsonPath("$.stats.totalNodes").value(13))

                // Root orchestrator
                .andExpect(jsonPath("$.root.title").value("Orchestrator"))
                .andExpect(jsonPath("$.root.actionName").value("orchestrator"))
                .andExpect(jsonPath("$.root.routeBackCount").value(0))
                .andExpect(jsonPath("$.root.children.length()").value(3))

                // Child 0: Discovery Orchestrator
                .andExpect(jsonPath("$.root.children[0].title").value("Discovery Orchestrator"))
                .andExpect(jsonPath("$.root.children[0].actionName").value("discovery-orchestrator"))
                .andExpect(jsonPath("$.root.children[0].children.length()").value(2))

                // Discovery Agent has ReviewNode child (always shown even though completed)
                .andExpect(jsonPath("$.root.children[0].children[0].title").value("Discover: Repository overview"))
                .andExpect(jsonPath("$.root.children[0].children[0].actionName").value("discovery-agent"))
                .andExpect(jsonPath("$.root.children[0].children[0].children.length()").value(1))
                .andExpect(jsonPath("$.root.children[0].children[0].children[0].title").value("Review: Discovery output"))
                // Completed review — no pending items on the discovery agent for this
                .andExpect(jsonPath("$.root.children[0].children[0].metrics.pendingItems").isEmpty())

                // Discovery Collector
                .andExpect(jsonPath("$.root.children[0].children[1].title").value("Discovery Collector"))
                .andExpect(jsonPath("$.root.children[0].children[1].actionName").value("discovery-collector"))

                // Child 1: Planning Orchestrator (routeBackCount=1)
                .andExpect(jsonPath("$.root.children[1].title").value("Planning Orchestrator"))
                .andExpect(jsonPath("$.root.children[1].actionName").value("planning-orchestrator"))
                .andExpect(jsonPath("$.root.children[1].routeBackCount").value(1))
                .andExpect(jsonPath("$.root.children[1].children.length()").value(3))

                // Sorted ascending by lastUpdatedAt:
                // Planning Agent (-170s) < Merge (-141s) < Planning Collector (-140s)
                .andExpect(jsonPath("$.root.children[1].children[0].title").value("Plan segment 1"))
                .andExpect(jsonPath("$.root.children[1].children[0].actionName").value("planning-agent"))

                // MergeNode — always shown
                .andExpect(jsonPath("$.root.children[1].children[1].title").value("Merge: Planning results"))

                // Planning Collector
                .andExpect(jsonPath("$.root.children[1].children[2].title").value("Planning Collector"))
                .andExpect(jsonPath("$.root.children[1].children[2].actionName").value("planning-collector"))

                // Child 2: Ticket Orchestrator
                .andExpect(jsonPath("$.root.children[2].title").value("Ticket Orchestrator"))
                .andExpect(jsonPath("$.root.children[2].actionName").value("ticket-orchestrator"))
                .andExpect(jsonPath("$.root.children[2].children.length()").value(1))

                // Ticket Agent has pending permission + pending interrupt as children
                .andExpect(jsonPath("$.root.children[2].children[0].title").value("Ticket 1"))
                .andExpect(jsonPath("$.root.children[2].children[0].actionName").value("ticket-agent"))
                .andExpect(jsonPath("$.root.children[2].children[0].children.length()").value(2))

                // Pending items on ticket agent
                .andExpect(jsonPath("$.root.children[2].children[0].metrics.pendingItems.length()").value(2))
                .andExpect(jsonPath("$.root.children[2].children[0].metrics.pendingItems[0].type").value("PERMISSION"))
                .andExpect(jsonPath("$.root.children[2].children[0].metrics.pendingItems[0].id").value(permToolCallId))
                .andExpect(jsonPath("$.root.children[2].children[0].metrics.pendingItems[1].type").value("INTERRUPT"))

                // Metrics: thought tokens from discovery agent (190) + ticket agent (124) = 314
                .andExpect(jsonPath("$.stats.thoughtTokens").value(314))
                // Stream tokens from disc collector (533) + plan agent (525) + plan collector (833) = 1891
                .andExpect(jsonPath("$.stats.streamTokens").value(1891))
                // Chat sessions: 5 (one per agent node)
                .andExpect(jsonPath("$.stats.chatSessionEvents").value(5))
                // Tool events on discovery agent (1 read_file) + ticket agent (1 write_file)
                .andExpect(jsonPath("$.root.children[0].children[0].metrics.toolEvents").value(1))
                .andExpect(jsonPath("$.root.children[2].children[0].metrics.toolEvents").value(1));
    }

    /**
     * Verifies that resolved permissions and interrupts are excluded from children,
     * while resolved reviews/merges remain.
     */
    @Test
    void workflowGraph_excludes_resolved_permissions_and_interrupts() throws Exception {
        ArtifactKey rootKey = ArtifactKey.createRoot();
        ArtifactKey agentKey = rootKey.createChild();

        String rootId = rootKey.value();
        String agentId = agentKey.value();
        String resolvedPermId = uid();
        String resolvedPermToolCallId = "call_resolved";
        String resolvedInterruptId = uid();

        // Orchestrator with one agent child
        OrchestratorNode orch = OrchestratorNode.builder()
                .nodeId(rootId)
                .title("Orchestrator")
                .goal("Test exclusion")
                .status(Events.NodeStatus.RUNNING)
                .parentNodeId(null)
                .childNodeIds(new ArrayList<>(List.of(agentId)))
                .createdAt(NOW.minusSeconds(60))
                .lastUpdatedAt(NOW.minusSeconds(10))
                .worktreeContext(testWorktreeContext(rootId))
                .build();
        graphRepository.save(orch);

        // Agent with resolved permission + resolved interrupt as children
        SummaryNode agent = SummaryNode.builder()
                .nodeId(agentId)
                .title("Agent")
                .goal("Do work")
                .status(Events.NodeStatus.RUNNING)
                .parentNodeId(rootId)
                .childNodeIds(new ArrayList<>(List.of(resolvedPermId, resolvedInterruptId)))
                .createdAt(NOW.minusSeconds(55))
                .lastUpdatedAt(NOW.minusSeconds(10))
                .build();
        graphRepository.save(agent);

        // Resolved permission — COMPLETED status
        AskPermissionNode resolvedPerm = AskPermissionNode.builder()
                .nodeId(resolvedPermId)
                .title("Permission: resolved")
                .goal("Resolved perm")
                .status(Events.NodeStatus.COMPLETED)
                .parentNodeId(agentId)
                .childNodeIds(new ArrayList<>())
                .createdAt(NOW.minusSeconds(50))
                .lastUpdatedAt(NOW.minusSeconds(40))
                .toolCallId(resolvedPermToolCallId)
                .build();
        graphRepository.save(resolvedPerm);

        // Resolved interrupt — RESOLVED status
        InterruptContext resolvedCtx = new InterruptContext(
                Events.InterruptType.AGENT_REVIEW,
                InterruptContext.InterruptStatus.RESOLVED,
                "Was routed back",
                agentId, agentId, resolvedInterruptId, "done");
        InterruptNode resolvedInterrupt = InterruptNode.builder()
                .nodeId(resolvedInterruptId)
                .title("Interrupt: resolved")
                .goal("Resolved interrupt")
                .status(Events.NodeStatus.COMPLETED)
                .parentNodeId(agentId)
                .childNodeIds(new ArrayList<>())
                .createdAt(NOW.minusSeconds(48))
                .lastUpdatedAt(NOW.minusSeconds(35))
                .interruptContext(resolvedCtx)
                .build();
        graphRepository.save(resolvedInterrupt);

        mockMvc.perform(get("/api/llm-debug/ui/workflow-graph")
                        .param("nodeId", rootId)
                        .param("errorWindowSeconds", "180"))
                .andDo(print())
                .andExpect(status().isOk())
                // Agent should have NO children (both permission and interrupt are resolved)
                .andExpect(jsonPath("$.root.children[0].children.length()").value(0))
                // No pending items either
                .andExpect(jsonPath("$.root.children[0].metrics.pendingItems").isEmpty());
    }

    // --- Helpers ---

    private static String uid() {
        return UUID.randomUUID().toString();
    }

    private static MainWorktreeContext testWorktreeContext(String nodeId) {
        return MainWorktreeContext.builder()
                .worktreeId("wt-" + nodeId.substring(0, 8))
                .worktreePath(Path.of("/tmp/test-worktree"))
                .baseBranch("main")
                .derivedBranch("test-branch")
                .status(WorktreeContext.WorktreeStatus.ACTIVE)
                .associatedNodeId(nodeId)
                .createdAt(NOW.minusSeconds(300))
                .repositoryUrl("https://github.com/test/repo.git")
                .build();
    }
}
