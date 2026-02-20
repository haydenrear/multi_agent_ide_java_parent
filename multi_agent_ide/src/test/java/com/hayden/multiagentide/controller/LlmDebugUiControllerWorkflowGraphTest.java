package com.hayden.multiagentide.controller;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.repository.EventStreamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
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

    @Test
    void workflowGraph_returns_hierarchy_and_metrics_for_root_scope() throws Exception {
        ArtifactKey root = ArtifactKey.createRoot();
        ArtifactKey child = root.createChild();
        ArtifactKey chatKey = child.createChild();
        Instant now = Instant.now();

        eventStreamRepository.save(new Events.NodeAddedEvent(
                UUID.randomUUID().toString(),
                now.minusSeconds(120),
                root.value(),
                "Orchestrator",
                Events.NodeType.ORCHESTRATOR,
                null
        ));
        eventStreamRepository.save(new Events.NodeAddedEvent(
                UUID.randomUUID().toString(),
                now.minusSeconds(110),
                child.value(),
                "Discovery Agent",
                Events.NodeType.SUMMARY,
                root.value()
        ));
        eventStreamRepository.save(new Events.ActionStartedEvent(
                UUID.randomUUID().toString(),
                now.minusSeconds(100),
                child.value(),
                "WorkflowDiscoveryAgent",
                "discovery"
        ));
        eventStreamRepository.save(new Events.ChatSessionCreatedEvent(
                UUID.randomUUID().toString(),
                now.minusSeconds(95),
                child.value(),
                chatKey
        ));
        eventStreamRepository.save(new Events.AddMessageEvent(
                UUID.randomUUID().toString(),
                now.minusSeconds(90),
                child.value(),
                "Working on it."
        ));
        eventStreamRepository.save(new Events.NodeThoughtDeltaEvent(
                UUID.randomUUID().toString(),
                now.minusSeconds(85),
                child.value(),
                chatKey,
                "Thinking",
                7,
                true
        ));
        eventStreamRepository.save(new Events.NodeStreamDeltaEvent(
                UUID.randomUUID().toString(),
                now.minusSeconds(80),
                child.value(),
                chatKey,
                "Output",
                11,
                true
        ));
        eventStreamRepository.save(new Events.NodeErrorEvent(
                UUID.randomUUID().toString(),
                now.minusSeconds(70),
                child.value(),
                "Discovery Agent",
                Events.NodeType.SUMMARY,
                "Recent failure"
        ));
        eventStreamRepository.save(new Events.NodeErrorEvent(
                UUID.randomUUID().toString(),
                now.minusSeconds(500),
                child.value(),
                "Discovery Agent",
                Events.NodeType.SUMMARY,
                "Old failure"
        ));

        mockMvc.perform(get("/api/llm-debug/ui/workflow-graph")
                        .param("nodeId", child.value())
                        .param("errorWindowSeconds", "180"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedNodeId").value(child.value()))
                .andExpect(jsonPath("$.rootNodeId").value(root.value()))
                .andExpect(jsonPath("$.stats.totalNodes").value(2))
                .andExpect(jsonPath("$.stats.eventTypeCounts.ACTION_STARTED").value(1))
                .andExpect(jsonPath("$.stats.recentErrorCount").value(1))
                .andExpect(jsonPath("$.stats.recentErrorsByNodeType.SUMMARY").value(1))
                .andExpect(jsonPath("$.stats.chatSessionEvents").value(1))
                .andExpect(jsonPath("$.stats.chatMessageEvents").value(1))
                .andExpect(jsonPath("$.stats.thoughtTokens").value(7))
                .andExpect(jsonPath("$.stats.streamTokens").value(11))
                .andExpect(jsonPath("$.root.nodeId").value(root.value()))
                .andExpect(jsonPath("$.root.children[0].nodeId").value(child.value()))
                .andExpect(jsonPath("$.root.children[0].metrics.nodeErrorCount").value(2))
                .andExpect(jsonPath("$.root.children[0].metrics.otherEvents").value(0))
                .andExpect(jsonPath("$.root.children[0].actionName").value("discovery"))
                .andExpect(jsonPath("$.root.children[0].routeBackCount").value(0))
                .andExpect(jsonPath("$.root.children[0].metrics.pendingItems").isEmpty());
    }

    /**
     * Full workflow test modeled on real production output.
     * ArtifactKey hierarchy determines the graph nesting:
     *   Orchestrator (root)
     *   ├── Discovery Orchestrator       (root/A)
     *   │   ├── Discovery Agent          (root/A/B/C)
     *   │   └── Discovery Collector      (root/A/D)
     *   ├── Planning Orchestrator        (root/E — direct child of root)
     *   │   ├── Plan segment 1           (root/E/F/G)
     *   │   └── Planning Collector       (root/E/H)
     *   └── Ticket Orchestrator          (root/I — direct child of root)
     *       └── Ticket 1                 (root/I/J/K, has pending permission)
     */
    @Test
    void workflowGraph_reparents_orchestrators_and_tracks_pending_items() throws Exception {
        Instant now = Instant.now();

        // Build artifact key hierarchy matching corrected production structure.
        // All phase orchestrators are direct children of root.
        ArtifactKey root = ArtifactKey.createRoot();

        // Discovery orchestrator — child of root
        ArtifactKey discoveryOrchScope = root.createChild();
        ArtifactKey discoveryAgentScope = discoveryOrchScope.createChild().createChild();
        ArtifactKey discoveryCollectorScope = discoveryOrchScope.createChild();

        // Planning orchestrator — direct child of root (not nested under discovery)
        ArtifactKey planningOrchScope = root.createChild();
        ArtifactKey planningAgentScope = planningOrchScope.createChild().createChild();
        ArtifactKey planningCollectorScope = planningOrchScope.createChild();

        // Ticket orchestrator — direct child of root (not nested under planning)
        ArtifactKey ticketOrchScope = root.createChild();
        ArtifactKey ticketAgentScope = ticketOrchScope.createChild().createChild();

        // --- Orchestrator ---
        eventStreamRepository.save(new Events.NodeAddedEvent(
                uid(), now.minusSeconds(300), root.value(),
                "Orchestrator", Events.NodeType.ORCHESTRATOR, null));
        eventStreamRepository.save(new Events.ActionStartedEvent(
                uid(), now.minusSeconds(299), root.value(),
                "WorkflowOrchestrator", "orchestrator"));
        eventStreamRepository.save(new Events.NodeStatusChangedEvent(
                uid(), now.minusSeconds(298), root.value(),
                Events.NodeStatus.PENDING, Events.NodeStatus.RUNNING, "Agent execution started"));

        // --- Discovery Orchestrator ---
        eventStreamRepository.save(new Events.NodeAddedEvent(
                uid(), now.minusSeconds(280), discoveryOrchScope.value(),
                "Discovery Orchestrator", Events.NodeType.SUMMARY, root.value()));
        eventStreamRepository.save(new Events.ActionStartedEvent(
                uid(), now.minusSeconds(279), discoveryOrchScope.value(),
                "DiscoveryOrchestratorAgent", "discovery-orchestrator"));
        eventStreamRepository.save(new Events.NodeStatusChangedEvent(
                uid(), now.minusSeconds(278), discoveryOrchScope.value(),
                Events.NodeStatus.PENDING, Events.NodeStatus.RUNNING, "Agent execution started"));

        // --- Discovery Agent ---
        eventStreamRepository.save(new Events.NodeAddedEvent(
                uid(), now.minusSeconds(270), discoveryAgentScope.value(),
                "Discover: Repository overview", Events.NodeType.SUMMARY, discoveryOrchScope.value()));
        eventStreamRepository.save(new Events.ActionStartedEvent(
                uid(), now.minusSeconds(269), discoveryAgentScope.value(),
                "WorkflowDiscoveryAgent", "discovery-agent"));
        eventStreamRepository.save(new Events.NodeStatusChangedEvent(
                uid(), now.minusSeconds(268), discoveryAgentScope.value(),
                Events.NodeStatus.PENDING, Events.NodeStatus.RUNNING, "Agent execution started"));
        eventStreamRepository.save(new Events.NodeThoughtDeltaEvent(
                uid(), now.minusSeconds(260), discoveryAgentScope.value(),
                discoveryAgentScope.createChild(), "thinking", 190, true));
        eventStreamRepository.save(new Events.NodeStatusChangedEvent(
                uid(), now.minusSeconds(250), discoveryAgentScope.value(),
                Events.NodeStatus.RUNNING, Events.NodeStatus.COMPLETED, "Agent execution completed successfully"));

        // --- Discovery Collector ---
        eventStreamRepository.save(new Events.NodeAddedEvent(
                uid(), now.minusSeconds(240), discoveryCollectorScope.value(),
                "Discovery Collector", Events.NodeType.SUMMARY, discoveryOrchScope.value()));
        eventStreamRepository.save(new Events.ActionStartedEvent(
                uid(), now.minusSeconds(239), discoveryCollectorScope.value(),
                "DiscoveryCollectorAgent", "discovery-collector"));
        eventStreamRepository.save(new Events.NodeStatusChangedEvent(
                uid(), now.minusSeconds(238), discoveryCollectorScope.value(),
                Events.NodeStatus.PENDING, Events.NodeStatus.RUNNING, "Agent execution started"));
        eventStreamRepository.save(new Events.NodeStreamDeltaEvent(
                uid(), now.minusSeconds(230), discoveryCollectorScope.value(),
                discoveryCollectorScope.createChild(), "collector output", 533, true));
        eventStreamRepository.save(new Events.NodeStatusChangedEvent(
                uid(), now.minusSeconds(220), discoveryCollectorScope.value(),
                Events.NodeStatus.RUNNING, Events.NodeStatus.COMPLETED, "Agent execution completed successfully"));

        // Discovery orchestrator completes
        eventStreamRepository.save(new Events.NodeStatusChangedEvent(
                uid(), now.minusSeconds(219), discoveryOrchScope.value(),
                Events.NodeStatus.RUNNING, Events.NodeStatus.COMPLETED, "Agent execution completed successfully"));

        // --- Planning Orchestrator --- (direct child of root orchestrator)
        eventStreamRepository.save(new Events.NodeAddedEvent(
                uid(), now.minusSeconds(200), planningOrchScope.value(),
                "Planning Orchestrator", Events.NodeType.PLANNING, root.value()));
        eventStreamRepository.save(new Events.ActionStartedEvent(
                uid(), now.minusSeconds(199), planningOrchScope.value(),
                "PlanningOrchestratorAgent", "planning-orchestrator"));
        eventStreamRepository.save(new Events.NodeStatusChangedEvent(
                uid(), now.minusSeconds(198), planningOrchScope.value(),
                Events.NodeStatus.PENDING, Events.NodeStatus.RUNNING, "Agent execution started"));

        // --- Planning Agent ---
        eventStreamRepository.save(new Events.NodeAddedEvent(
                uid(), now.minusSeconds(190), planningAgentScope.value(),
                "Plan segment 1", Events.NodeType.PLANNING, planningOrchScope.value()));
        eventStreamRepository.save(new Events.ActionStartedEvent(
                uid(), now.minusSeconds(189), planningAgentScope.value(),
                "PlanningAgent", "planning-agent"));
        eventStreamRepository.save(new Events.NodeStreamDeltaEvent(
                uid(), now.minusSeconds(180), planningAgentScope.value(),
                planningAgentScope.createChild(), "plan output", 525, true));
        eventStreamRepository.save(new Events.NodeStatusChangedEvent(
                uid(), now.minusSeconds(170), planningAgentScope.value(),
                Events.NodeStatus.RUNNING, Events.NodeStatus.COMPLETED, "Agent execution completed successfully"));

        // --- Planning Collector ---
        eventStreamRepository.save(new Events.NodeAddedEvent(
                uid(), now.minusSeconds(160), planningCollectorScope.value(),
                "Planning Collector", Events.NodeType.PLANNING, planningOrchScope.value()));
        eventStreamRepository.save(new Events.ActionStartedEvent(
                uid(), now.minusSeconds(159), planningCollectorScope.value(),
                "PlanningCollectorAgent", "planning-collector"));
        eventStreamRepository.save(new Events.NodeStreamDeltaEvent(
                uid(), now.minusSeconds(150), planningCollectorScope.value(),
                planningCollectorScope.createChild(), "collector output", 833, true));
        eventStreamRepository.save(new Events.NodeStatusChangedEvent(
                uid(), now.minusSeconds(140), planningCollectorScope.value(),
                Events.NodeStatus.RUNNING, Events.NodeStatus.COMPLETED, "Agent execution completed successfully"));

        // Planning orchestrator completes
        eventStreamRepository.save(new Events.NodeStatusChangedEvent(
                uid(), now.minusSeconds(139), planningOrchScope.value(),
                Events.NodeStatus.RUNNING, Events.NodeStatus.COMPLETED, "Agent execution completed successfully"));

        // --- Ticket Orchestrator --- (direct child of root orchestrator)
        eventStreamRepository.save(new Events.NodeAddedEvent(
                uid(), now.minusSeconds(120), ticketOrchScope.value(),
                "Ticket Orchestrator", Events.NodeType.WORK, root.value()));
        eventStreamRepository.save(new Events.ActionStartedEvent(
                uid(), now.minusSeconds(119), ticketOrchScope.value(),
                "TicketOrchestratorAgent", "ticket-orchestrator"));
        eventStreamRepository.save(new Events.NodeStatusChangedEvent(
                uid(), now.minusSeconds(118), ticketOrchScope.value(),
                Events.NodeStatus.PENDING, Events.NodeStatus.RUNNING, "Agent execution started"));

        // --- Ticket Agent ---
        eventStreamRepository.save(new Events.NodeAddedEvent(
                uid(), now.minusSeconds(100), ticketAgentScope.value(),
                "Ticket 1", Events.NodeType.WORK, ticketOrchScope.value()));
        eventStreamRepository.save(new Events.ActionStartedEvent(
                uid(), now.minusSeconds(99), ticketAgentScope.value(),
                "TicketAgent", "ticket-agent"));
        eventStreamRepository.save(new Events.NodeStatusChangedEvent(
                uid(), now.minusSeconds(98), ticketAgentScope.value(),
                Events.NodeStatus.PENDING, Events.NodeStatus.RUNNING, "Agent execution started"));
        eventStreamRepository.save(new Events.NodeThoughtDeltaEvent(
                uid(), now.minusSeconds(90), ticketAgentScope.value(),
                ticketAgentScope.createChild(), "working", 124, true));

        // Pending permission on ticket agent
        String permRequestId = "call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String permToolCallId = permRequestId;
        ArtifactKey permScope = ticketAgentScope.createChild();
        eventStreamRepository.save(new Events.PermissionRequestedEvent(
                uid(), now.minusSeconds(50), permScope.value(),
                ticketAgentScope.value(), permRequestId, permToolCallId, null));

        // --- Assertions ---
        mockMvc.perform(get("/api/llm-debug/ui/workflow-graph")
                        .param("nodeId", root.value())
                        .param("errorWindowSeconds", "180"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootNodeId").value(root.value()))
                .andExpect(jsonPath("$.stats.totalNodes").value(9))

                // Root has 3 direct children after reparenting:
                // Discovery Orchestrator, Planning Orchestrator, Ticket Orchestrator
                .andExpect(jsonPath("$.root.title").value("Orchestrator"))
                .andExpect(jsonPath("$.root.actionName").value("orchestrator"))
                .andExpect(jsonPath("$.root.children.length()").value(3))

                // Child 0: Discovery Orchestrator (sorted by nodeId)
                .andExpect(jsonPath("$.root.children[0].title").value("Discovery Orchestrator"))
                .andExpect(jsonPath("$.root.children[0].actionName").value("discovery-orchestrator"))
                .andExpect(jsonPath("$.root.children[0].children.length()").value(2))
                .andExpect(jsonPath("$.root.children[0].children[0].title").value("Discover: Repository overview"))
                .andExpect(jsonPath("$.root.children[0].children[0].actionName").value("discovery-agent"))
                .andExpect(jsonPath("$.root.children[0].children[1].title").value("Discovery Collector"))
                .andExpect(jsonPath("$.root.children[0].children[1].actionName").value("discovery-collector"))
                // Discovery Collector should have NO children (planning-orchestrator was reparented away)
                .andExpect(jsonPath("$.root.children[0].children[1].children.length()").value(0))

                // Child 1: Planning Orchestrator (reparented to root)
                .andExpect(jsonPath("$.root.children[1].title").value("Planning Orchestrator"))
                .andExpect(jsonPath("$.root.children[1].actionName").value("planning-orchestrator"))
                .andExpect(jsonPath("$.root.children[1].children.length()").value(2))
                .andExpect(jsonPath("$.root.children[1].children[0].title").value("Plan segment 1"))
                .andExpect(jsonPath("$.root.children[1].children[0].actionName").value("planning-agent"))
                .andExpect(jsonPath("$.root.children[1].children[1].title").value("Planning Collector"))
                .andExpect(jsonPath("$.root.children[1].children[1].actionName").value("planning-collector"))
                // Planning Collector should have NO children (ticket-orchestrator was reparented away)
                .andExpect(jsonPath("$.root.children[1].children[1].children.length()").value(0))

                // Child 2: Ticket Orchestrator (reparented to root)
                .andExpect(jsonPath("$.root.children[2].title").value("Ticket Orchestrator"))
                .andExpect(jsonPath("$.root.children[2].actionName").value("ticket-orchestrator"))
                .andExpect(jsonPath("$.root.children[2].children.length()").value(1))
                .andExpect(jsonPath("$.root.children[2].children[0].title").value("Ticket 1"))
                .andExpect(jsonPath("$.root.children[2].children[0].actionName").value("ticket-agent"))

                // Ticket 1 has a pending permission
                .andExpect(jsonPath("$.root.children[2].children[0].metrics.pendingItems.length()").value(1))
                .andExpect(jsonPath("$.root.children[2].children[0].metrics.pendingItems[0].type").value("PERMISSION"))
                .andExpect(jsonPath("$.root.children[2].children[0].metrics.pendingItems[0].id").value(permRequestId))
                .andExpect(jsonPath("$.root.children[2].children[0].metrics.pendingItems[0].description").exists())

                // Metrics aggregation: thought tokens from discovery agent + ticket agent
                .andExpect(jsonPath("$.stats.thoughtTokens").value(190 + 124));
    }

    private static String uid() {
        return UUID.randomUUID().toString();
    }
}
