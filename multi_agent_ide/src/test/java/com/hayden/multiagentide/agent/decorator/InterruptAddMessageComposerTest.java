package com.hayden.multiagentide.agent.decorator;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.service.InterruptSchemaGenerator;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.model.nodes.GraphNode;
import com.hayden.multiagentidelib.model.nodes.OrchestratorNode;
import com.hayden.multiagentidelib.model.nodes.DiscoveryNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for InterruptAddMessageComposer.
 *
 * <h3>Interfaces under test</h3>
 * <ul>
 *   <li>{@link InterruptAddMessageComposer#composeInterruptMessage(Events.InterruptRequestEvent, GraphNode)}</li>
 *   <li>{@link InterruptAddMessageComposer#composeRouteBackMessage(String, String, String)}</li>
 *   <li>{@link InterruptAddMessageComposer#resolveAgentType(String, GraphNode)}</li>
 * </ul>
 *
 * <h3>Input domains for composeInterruptMessage</h3>
 * <ul>
 *   <li>event.reason: {null, blank, non-blank}</li>
 *   <li>event.contextForDecision: {null, blank, non-blank}</li>
 *   <li>event.rerouteToAgentType: {null, blank, non-blank}</li>
 *   <li>event.sourceAgentType: {null, blank, valid-wire-value, invalid-wire-value}</li>
 *   <li>node: {OrchestratorNode, DiscoveryNode}</li>
 *   <li>interruptSchema: {null (generator returns null), non-null}</li>
 * </ul>
 *
 * <h3>Input domains for composeRouteBackMessage</h3>
 * <ul>
 *   <li>routeBackSchemaJson: {null, non-null}</li>
 *   <li>targetAgentType: {null, blank, non-blank}</li>
 *   <li>reason: {null, blank, non-blank}</li>
 * </ul>
 *
 * <h3>Input domains for resolveAgentType</h3>
 * <ul>
 *   <li>sourceAgentTypeStr: {null, blank, valid-wire-value, invalid-wire-value}</li>
 *   <li>node: {OrchestratorNode (→ ORCHESTRATOR), DiscoveryNode (→ DISCOVERY_AGENT)}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class InterruptAddMessageComposerTest {

    @Mock
    private InterruptSchemaGenerator schemaGenerator;

    private InterruptAddMessageComposer composer;

    @BeforeEach
    void setUp() {
        composer = new InterruptAddMessageComposer(schemaGenerator);
        lenient().when(schemaGenerator.generateInterruptSchema(any(AgentType.class))).thenReturn("{\"type\":\"object\"}");
    }

    // ── resolveAgentType ──────────────────────────────────────────────────

    @Nested
    class ResolveAgentType {

        private final GraphNode orchestratorNode = buildOrchestratorNode();
        private final GraphNode discoveryNode = buildDiscoveryNode();

        @Test
        void validWireValue_returnsFromWireValue() {
            assertThat(InterruptAddMessageComposer.resolveAgentType("orchestrator", orchestratorNode))
                    .isEqualTo(AgentType.ORCHESTRATOR);
        }

        @Test
        void validWireValue_overridesNodeType() {
            // Wire value says PLANNING_AGENT, node is OrchestratorNode — wire value wins
            assertThat(InterruptAddMessageComposer.resolveAgentType("planning-agent", orchestratorNode))
                    .isEqualTo(AgentType.PLANNING_AGENT);
        }

        @Test
        void invalidWireValue_fallsBackToNode() {
            assertThat(InterruptAddMessageComposer.resolveAgentType("not-a-real-type", orchestratorNode))
                    .isEqualTo(AgentType.ORCHESTRATOR);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void nullOrBlankWireValue_fallsBackToNode(String wireValue) {
            assertThat(InterruptAddMessageComposer.resolveAgentType(wireValue, discoveryNode))
                    .isEqualTo(AgentType.DISCOVERY_AGENT);
        }
    }

    // ── composeInterruptMessage ───────────────────────────────────────────

    @Nested
    class ComposeInterruptMessage {

        @Test
        void alwaysContainsHeader() {
            String msg = composer.composeInterruptMessage(
                    buildEvent(null, null, null, null), buildOrchestratorNode());
            assertThat(msg).contains("## INTERRUPT: Your structured response type has changed");
            assertThat(msg).contains("You MUST now respond with an InterruptRequest");
        }

        @Test
        void nullReason_usesDefaultText() {
            String msg = composer.composeInterruptMessage(
                    buildEvent(null, null, null, null), buildOrchestratorNode());
            assertThat(msg).contains("Interrupt requested by external actor.");
        }

        @Test
        void blankReason_usesDefaultText() {
            String msg = composer.composeInterruptMessage(
                    buildEvent("  ", null, null, null), buildOrchestratorNode());
            assertThat(msg).contains("Interrupt requested by external actor.");
        }

        @Test
        void nonBlankReason_usedVerbatim() {
            String msg = composer.composeInterruptMessage(
                    buildEvent("Agent is stuck in a loop", null, null, null), buildOrchestratorNode());
            assertThat(msg).contains("Agent is stuck in a loop");
            assertThat(msg).doesNotContain("Interrupt requested by external actor.");
        }

        @Test
        void nullContext_contextSectionOmitted() {
            String msg = composer.composeInterruptMessage(
                    buildEvent("reason", null, null, null), buildOrchestratorNode());
            assertThat(msg).doesNotContain("### Context");
        }

        @Test
        void blankContext_contextSectionOmitted() {
            String msg = composer.composeInterruptMessage(
                    buildEvent("reason", "  ", null, null), buildOrchestratorNode());
            assertThat(msg).doesNotContain("### Context");
        }

        @Test
        void nonBlankContext_contextSectionPresent() {
            String msg = composer.composeInterruptMessage(
                    buildEvent("reason", "Discovery found 3 requirements", null, null), buildOrchestratorNode());
            assertThat(msg).contains("### Context");
            assertThat(msg).contains("Discovery found 3 requirements");
        }

        @Test
        void nullRerouteTarget_routingSectionOmitted() {
            String msg = composer.composeInterruptMessage(
                    buildEvent("reason", null, null, null), buildOrchestratorNode());
            assertThat(msg).doesNotContain("### Routing Target");
        }

        @Test
        void blankRerouteTarget_routingSectionOmitted() {
            String msg = composer.composeInterruptMessage(
                    buildEvent("reason", null, "  ", null), buildOrchestratorNode());
            assertThat(msg).doesNotContain("### Routing Target");
        }

        @Test
        void nonBlankRerouteTarget_routingSectionPresent() {
            String msg = composer.composeInterruptMessage(
                    buildEvent("reason", null, "planning-orchestrator", null), buildOrchestratorNode());
            assertThat(msg).contains("### Routing Target");
            assertThat(msg).contains("**planning-orchestrator**");
        }

        @Test
        void schemaGenerated_schemaSectionPresent() {
            when(schemaGenerator.generateInterruptSchema(AgentType.ORCHESTRATOR)).thenReturn("{\"schema\":true}");
            String msg = composer.composeInterruptMessage(
                    buildEvent("reason", null, null, "orchestrator"), buildOrchestratorNode());
            assertThat(msg).contains("### Override Schema (InterruptRequest)");
            assertThat(msg).contains("{\"schema\":true}");
        }

        @Test
        void schemaGeneratorReturnsNull_schemaSectionOmitted() {
            when(schemaGenerator.generateInterruptSchema(any(AgentType.class))).thenReturn(null);
            String msg = composer.composeInterruptMessage(
                    buildEvent("reason", null, null, "orchestrator"), buildOrchestratorNode());
            assertThat(msg).doesNotContain("### Override Schema");
        }

        @Test
        void allFieldsPopulated_allSectionsPresent() {
            when(schemaGenerator.generateInterruptSchema(AgentType.ORCHESTRATOR)).thenReturn("{\"full\":true}");
            String msg = composer.composeInterruptMessage(
                    buildEvent("Loop detected", "Context info", "planning-orchestrator", "orchestrator"),
                    buildOrchestratorNode());
            assertThat(msg).contains("## INTERRUPT:");
            assertThat(msg).contains("### Reason");
            assertThat(msg).contains("Loop detected");
            assertThat(msg).contains("### Context");
            assertThat(msg).contains("Context info");
            assertThat(msg).contains("### Routing Target");
            assertThat(msg).contains("**planning-orchestrator**");
            assertThat(msg).contains("### Override Schema");
            assertThat(msg).contains("{\"full\":true}");
        }

        @Test
        void allFieldsNull_minimalOutput() {
            when(schemaGenerator.generateInterruptSchema(any(AgentType.class))).thenReturn(null);
            String msg = composer.composeInterruptMessage(
                    buildEvent(null, null, null, null), buildOrchestratorNode());
            assertThat(msg).contains("## INTERRUPT:");
            assertThat(msg).contains("Interrupt requested by external actor.");
            assertThat(msg).doesNotContain("### Context");
            assertThat(msg).doesNotContain("### Routing Target");
            assertThat(msg).doesNotContain("### Override Schema");
        }
    }

    // ── composeRouteBackMessage ───────────────────────────────────────────

    @Nested
    class ComposeRouteBackMessage {

        @Test
        void alwaysContainsHeader() {
            String msg = composer.composeRouteBackMessage(null, null, null);
            assertThat(msg).contains("## ROUTE-BACK: Your structured response type has changed");
        }

        @Test
        void nullReason_reasonSectionOmitted() {
            String msg = composer.composeRouteBackMessage(null, null, null);
            assertThat(msg).doesNotContain("### Reason");
        }

        @Test
        void blankReason_reasonSectionOmitted() {
            String msg = composer.composeRouteBackMessage(null, null, "  ");
            assertThat(msg).doesNotContain("### Reason");
        }

        @Test
        void nonBlankReason_reasonSectionPresent() {
            String msg = composer.composeRouteBackMessage(null, null, "Controller approved route-back");
            assertThat(msg).contains("### Reason");
            assertThat(msg).contains("Controller approved route-back");
        }

        @Test
        void nullTargetAgentType_routingSectionOmitted() {
            String msg = composer.composeRouteBackMessage(null, null, "reason");
            assertThat(msg).doesNotContain("### Routing Target");
        }

        @Test
        void nonBlankTargetAgentType_routingSectionPresent() {
            String msg = composer.composeRouteBackMessage(null, "discovery-agent", "reason");
            assertThat(msg).contains("### Routing Target");
            assertThat(msg).contains("**discovery-agent**");
        }

        @Test
        void nullSchema_schemaSectionOmitted() {
            String msg = composer.composeRouteBackMessage(null, "agent", "reason");
            assertThat(msg).doesNotContain("### Override Schema");
        }

        @Test
        void nonNullSchema_schemaSectionPresent() {
            String msg = composer.composeRouteBackMessage("{\"route\":true}", "agent", "reason");
            assertThat(msg).contains("### Override Schema (Route-Back Request)");
            assertThat(msg).contains("{\"route\":true}");
        }

        @Test
        void allFieldsPopulated_allSectionsPresent() {
            String msg = composer.composeRouteBackMessage("{\"s\":1}", "discovery-agent", "Go back to discovery");
            assertThat(msg).contains("## ROUTE-BACK:");
            assertThat(msg).contains("### Reason");
            assertThat(msg).contains("Go back to discovery");
            assertThat(msg).contains("### Routing Target");
            assertThat(msg).contains("**discovery-agent**");
            assertThat(msg).contains("### Override Schema");
            assertThat(msg).contains("{\"s\":1}");
        }

        @Test
        void allFieldsNull_minimalOutput() {
            String msg = composer.composeRouteBackMessage(null, null, null);
            assertThat(msg).contains("## ROUTE-BACK:");
            assertThat(msg).doesNotContain("### Reason");
            assertThat(msg).doesNotContain("### Routing Target");
            assertThat(msg).doesNotContain("### Override Schema");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Events.InterruptRequestEvent buildEvent(String reason, String context,
                                                     String rerouteToAgentType, String sourceAgentType) {
        return new Events.InterruptRequestEvent(
                "evt-1", Instant.now(), "node-1",
                sourceAgentType, rerouteToAgentType,
                Events.InterruptType.HUMAN_REVIEW,
                reason, List.of(), List.of(), context, "req-1"
        );
    }

    private static GraphNode buildOrchestratorNode() {
        return OrchestratorNode.builder()
                .nodeId("ak:TEST")
                .title("Orchestrator")
                .goal("Test")
                .status(Events.NodeStatus.RUNNING)
                .childNodeIds(List.of())
                .metadata(Map.of())
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .orchestratorOutput("")
                .worktreeContext(com.hayden.multiagentidelib.model.worktree.MainWorktreeContext.builder()
                        .worktreeId("wt-test")
                        .repositoryUrl("https://test.example.com/repo.git")
                        .worktreePath(java.nio.file.Path.of("/tmp/test"))
                        .build())
                .build();
    }

    private static GraphNode buildDiscoveryNode() {
        return DiscoveryNode.builder()
                .nodeId("ak:DISC")
                .title("Discovery")
                .goal("Test")
                .status(Events.NodeStatus.RUNNING)
                .childNodeIds(List.of())
                .metadata(Map.of())
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .build();
    }
}
