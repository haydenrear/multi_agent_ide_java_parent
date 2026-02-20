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
}
