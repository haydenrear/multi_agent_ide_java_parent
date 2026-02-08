package com.hayden.multiagentide.agent;

import com.hayden.acp_cdc_ai.acp.AcpSessionManager;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.agent.AgentModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcpSessionCleanupServiceTest {

    @Mock
    private EventBus eventBus;

    private AcpSessionManager sessionManager;
    private AcpSessionCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        sessionManager = mock(AcpSessionManager.class, RETURNS_DEEP_STUBS);
        when(sessionManager.getSessionContexts()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());
        cleanupService = new AcpSessionCleanupService(sessionManager);
        cleanupService.setEventBus(eventBus);
    }

    private AcpSessionManager.AcpSessionContext mockSessionContext() {
        return mock(AcpSessionManager.AcpSessionContext.class, RETURNS_DEEP_STUBS);
    }

    private void putSession(String keyValue, AcpSessionManager.AcpSessionContext session) {
        sessionManager.getSessionContexts().put(keyValue, session);
    }

    @Test
    @DisplayName("should close dispatched agent session on ActionCompletedEvent with DiscoveryAgentResult")
    void shouldCloseDiscoveryAgentSession() {
        var rootKey = ArtifactKey.createRoot();
        var childKey = rootKey.createChild();

        var session = mockSessionContext();
        putSession(childKey.value(), session);

        var result = AgentModels.DiscoveryAgentResult.builder()
                .contextId(childKey)
                .build();

        var event = new Events.ActionCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                childKey.value(),
                "discovery-agent",
                "discover",
                "DiscoveryAgentResult",
                result
        );

        cleanupService.onEvent(event);

        assertThat(sessionManager.getSessionContexts()).isEmpty();
        verify(session.getClient().getProtocol()).close();
        verify(eventBus).publish(any(Events.ChatSessionClosedEvent.class));
    }

    @Test
    @DisplayName("should close dispatched agent session on ActionCompletedEvent with PlanningAgentResult")
    void shouldClosePlanningAgentSession() {
        var rootKey = ArtifactKey.createRoot();
        var childKey = rootKey.createChild();

        var session = mockSessionContext();
        putSession(childKey.value(), session);

        var result = AgentModels.PlanningAgentResult.builder()
                .contextId(childKey)
                .build();

        var event = new Events.ActionCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                childKey.value(),
                "planning-agent",
                "plan",
                "PlanningAgentResult",
                result
        );

        cleanupService.onEvent(event);

        assertThat(sessionManager.getSessionContexts()).isEmpty();
        verify(session.getClient().getProtocol()).close();
    }

    @Test
    @DisplayName("should close dispatched agent session on ActionCompletedEvent with TicketAgentResult")
    void shouldCloseTicketAgentSession() {
        var rootKey = ArtifactKey.createRoot();
        var childKey = rootKey.createChild();

        var session = mockSessionContext();
        putSession(childKey.value(), session);

        var result = AgentModels.TicketAgentResult.builder()
                .contextId(childKey)
                .build();

        var event = new Events.ActionCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                childKey.value(),
                "ticket-agent",
                "ticket",
                "TicketAgentResult",
                result
        );

        cleanupService.onEvent(event);

        assertThat(sessionManager.getSessionContexts()).isEmpty();
        verify(session.getClient().getProtocol()).close();
    }

    @Test
    @DisplayName("should NOT close session on ActionCompletedEvent with non-dispatched result type")
    void shouldNotCloseNonDispatchedAgentSession() {
        var rootKey = ArtifactKey.createRoot();

        var session = mockSessionContext();
        putSession(rootKey.value(), session);

        var result = AgentModels.OrchestratorAgentResult.builder()
                .contextId(rootKey)
                .build();

        var event = new Events.ActionCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                rootKey.value(),
                "orchestrator",
                "orchestrate",
                "OrchestratorAgentResult",
                result
        );

        cleanupService.onEvent(event);

        assertThat(sessionManager.getSessionContexts()).hasSize(1);
        verify(session.getClient().getProtocol(), never()).close();
    }

    @Test
    @DisplayName("should close all descendant sessions on GoalCompletedEvent with OrchestratorCollectorResult")
    void shouldCloseAllDescendantSessionsOnGoalCompleted() {
        var rootKey = ArtifactKey.createRoot();
        var child1 = rootKey.createChild();
        var child2 = rootKey.createChild();
        var grandchild = child1.createChild();

        var rootSession = mockSessionContext();
        var child1Session = mockSessionContext();
        var child2Session = mockSessionContext();
        var grandchildSession = mockSessionContext();

        putSession(rootKey.value(), rootSession);
        putSession(child1.value(), child1Session);
        putSession(child2.value(), child2Session);
        putSession(grandchild.value(), grandchildSession);

        var result = AgentModels.OrchestratorCollectorResult.builder()
                .contextId(rootKey)
                .build();

        var event = new Events.GoalCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                rootKey.value(),
                "workflow-1",
                result
        );

        cleanupService.onEvent(event);

        assertThat(sessionManager.getSessionContexts()).isEmpty();
        verify(rootSession.getClient().getProtocol()).close();
        verify(child1Session.getClient().getProtocol()).close();
        verify(child2Session.getClient().getProtocol()).close();
        verify(grandchildSession.getClient().getProtocol()).close();
        verify(eventBus, times(4)).publish(any(Events.ChatSessionClosedEvent.class));
    }

    @Test
    @DisplayName("should only close descendants of the root key, not unrelated sessions")
    void shouldOnlyCloseDescendantsNotUnrelatedSessions() {
        var rootKey1 = ArtifactKey.createRoot();
        var child1 = rootKey1.createChild();

        var rootKey2 = ArtifactKey.createRoot();
        var child2 = rootKey2.createChild();

        var child1Session = mockSessionContext();
        var rootKey1Session = mockSessionContext();
        var child2Session = mockSessionContext();
        var rootKey2Session = mockSessionContext();

        putSession(rootKey1.value(), rootKey1Session);
        putSession(child1.value(), child1Session);
        putSession(rootKey2.value(), rootKey2Session);
        putSession(child2.value(), child2Session);

        var result = AgentModels.OrchestratorCollectorResult.builder()
                .contextId(rootKey1)
                .build();

        var event = new Events.GoalCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                rootKey1.value(),
                "workflow-1",
                result
        );

        cleanupService.onEvent(event);

        // rootKey1 and child1 should be closed
        assertThat(sessionManager.getSessionContexts()).hasSize(2);
        assertThat(sessionManager.getSessionContexts()).containsKey(rootKey2.value());
        assertThat(sessionManager.getSessionContexts()).containsKey(child2.value());

        verify(rootKey1Session.getClient().getProtocol()).close();
        verify(child1Session.getClient().getProtocol()).close();
        verify(rootKey2Session.getClient().getProtocol(), never()).close();
        verify(child2Session.getClient().getProtocol(), never()).close();
    }

    @Test
    @DisplayName("should close deeply nested descendants")
    void shouldCloseDeeplyNestedDescendants() {
        var root = ArtifactKey.createRoot();
        var level1 = root.createChild();
        var level2 = level1.createChild();
        var level3 = level2.createChild();

        var rootSession = mockSessionContext();
        var level1Session = mockSessionContext();
        var level2Session = mockSessionContext();
        var level3Session = mockSessionContext();

        putSession(root.value(), rootSession);
        putSession(level1.value(), level1Session);
        putSession(level2.value(), level2Session);
        putSession(level3.value(), level3Session);

        var result = AgentModels.OrchestratorCollectorResult.builder()
                .contextId(root)
                .build();

        var event = new Events.GoalCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                root.value(),
                "workflow-1",
                result
        );

        cleanupService.onEvent(event);

        assertThat(sessionManager.getSessionContexts()).isEmpty();
        verify(rootSession.getClient().getProtocol()).close();
        verify(level1Session.getClient().getProtocol()).close();
        verify(level2Session.getClient().getProtocol()).close();
        verify(level3Session.getClient().getProtocol()).close();
    }

    @Test
    @DisplayName("should handle GoalCompletedEvent with non-OrchestratorCollectorResult gracefully")
    void shouldIgnoreNonOrchestratorCollectorGoalCompleted() {
        var rootKey = ArtifactKey.createRoot();

        var session = mockSessionContext();
        putSession(rootKey.value(), session);

        var result = AgentModels.DiscoveryAgentResult.builder()
                .contextId(rootKey)
                .build();

        var event = new Events.GoalCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                rootKey.value(),
                "workflow-1",
                result
        );

        cleanupService.onEvent(event);

        assertThat(sessionManager.getSessionContexts()).hasSize(1);
        verify(session.getClient().getProtocol(), never()).close();
    }

    @Test
    @DisplayName("should handle session with no matching key gracefully")
    void shouldHandleNoMatchingSession() {
        var childKey = ArtifactKey.createRoot().createChild();

        var result = AgentModels.DiscoveryAgentResult.builder()
                .contextId(childKey)
                .build();

        var event = new Events.ActionCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                childKey.value(),
                "discovery-agent",
                "discover",
                "DiscoveryAgentResult",
                result
        );

        // Should not throw
        cleanupService.onEvent(event);

        verify(eventBus, never()).publish(any(Events.ChatSessionClosedEvent.class));
    }

    @Test
    @DisplayName("isInterestedIn should return true for ActionCompletedEvent and GoalCompletedEvent")
    void shouldBeInterestedInCorrectEvents() {
        var actionEvent = new Events.ActionCompletedEvent(
                "id", Instant.now(), "node", "agent", "action", "type", null);
        var goalEvent = new Events.GoalCompletedEvent(
                "id", Instant.now(), "node", "workflow", null);
        var otherEvent = new Events.NodeAddedEvent(
                "id", Instant.now(), "node", "title", Events.NodeType.ORCHESTRATOR, "parent");

        assertThat(cleanupService.isInterestedIn(actionEvent)).isTrue();
        assertThat(cleanupService.isInterestedIn(goalEvent)).isTrue();
        assertThat(cleanupService.isInterestedIn(otherEvent)).isFalse();
    }
}
