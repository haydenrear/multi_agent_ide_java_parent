package com.hayden.multiagentide.service;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.repository.EventStreamRepository;
import com.hayden.multiagentide.repository.GraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for SessionKeyResolutionService.
 *
 * <h3>Interfaces under test</h3>
 * <ul>
 *   <li>{@link SessionKeyResolutionService#filterSelfCalls(ArtifactKey, Set)}</li>
 *   <li>{@link SessionKeyResolutionService#resolveSessionForMessage}</li>
 *   <li>{@link SessionKeyResolutionService#onEvent(Events.GraphEvent)} — lifecycle eviction</li>
 * </ul>
 *
 * <h3>Input domains for filterSelfCalls</h3>
 * <ul>
 *   <li>callingKey: {null, root, child, grandchild}</li>
 *   <li>candidateKeys: {null, empty, contains-self, contains-ancestor, contains-descendant, contains-unrelated, mixed}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SessionKeyResolutionServiceTest {

    @Mock
    private GraphRepository graphRepository;
    @Mock
    private EventStreamRepository eventStreamRepository;
    @Mock
    private EventBus eventBus;

    private SessionKeyResolutionService service;

    @BeforeEach
    void setUp() {
        lenient().when(graphRepository.findById(anyString())).thenReturn(Optional.empty());
        service = new SessionKeyResolutionService(graphRepository, eventStreamRepository);
        service.setEventBus(eventBus);
    }

    // ── filterSelfCalls ───────────────────────────────────────────────────

    @Nested
    class FilterSelfCalls {

        @Test
        void nullCallingKey_returnsCandidatesUnchanged() {
            ArtifactKey a = ArtifactKey.createRoot();
            Set<ArtifactKey> candidates = Set.of(a);
            assertThat(service.filterSelfCalls(null, candidates)).isEqualTo(candidates);
        }

        @Test
        void nullCandidates_returnsEmptySet() {
            ArtifactKey caller = ArtifactKey.createRoot();
            assertThat(service.filterSelfCalls(caller, null)).isEmpty();
        }

        @Test
        void bothNull_returnsEmptySet() {
            assertThat(service.filterSelfCalls(null, null)).isEmpty();
        }

        @Test
        void emptyCandidates_returnsEmptySet() {
            ArtifactKey caller = ArtifactKey.createRoot();
            assertThat(service.filterSelfCalls(caller, Set.of())).isEmpty();
        }

        @Test
        void exactMatch_excluded() {
            ArtifactKey caller = ArtifactKey.createRoot();
            Set<ArtifactKey> candidates = Set.of(caller);
            assertThat(service.filterSelfCalls(caller, candidates)).isEmpty();
        }

        @Test
        void descendantOfCaller_preserved() {
            // Descendants are NOT filtered — hierarchy does not imply self.
            // Cycle detection via active call chain handles this instead.
            ArtifactKey caller = ArtifactKey.createRoot();
            ArtifactKey child = caller.createChild();
            ArtifactKey grandchild = child.createChild();
            Set<ArtifactKey> candidates = new LinkedHashSet<>();
            candidates.add(child);
            candidates.add(grandchild);
            assertThat(service.filterSelfCalls(caller, candidates))
                    .containsExactlyInAnyOrder(child, grandchild);
        }

        @Test
        void ancestorOfCaller_preserved() {
            // Ancestors are NOT filtered — hierarchy does not imply self.
            ArtifactKey root = ArtifactKey.createRoot();
            ArtifactKey child = root.createChild();
            ArtifactKey grandchild = child.createChild();
            Set<ArtifactKey> candidates = new LinkedHashSet<>();
            candidates.add(root);
            candidates.add(child);
            assertThat(service.filterSelfCalls(grandchild, candidates))
                    .containsExactlyInAnyOrder(root, child);
        }

        @Test
        void unrelatedKeys_preserved() {
            ArtifactKey callerRoot = ArtifactKey.createRoot();
            ArtifactKey caller = callerRoot.createChild();
            ArtifactKey otherRoot = ArtifactKey.createRoot();
            ArtifactKey other = otherRoot.createChild();
            Set<ArtifactKey> candidates = Set.of(other);
            assertThat(service.filterSelfCalls(caller, candidates)).containsExactly(other);
        }

        @Test
        void mixedCandidates_onlySelfExcluded() {
            ArtifactKey root = ArtifactKey.createRoot();
            ArtifactKey caller = root.createChild();
            ArtifactKey callerChild = caller.createChild();
            ArtifactKey unrelated1 = ArtifactKey.createRoot().createChild();
            ArtifactKey unrelated2 = ArtifactKey.createRoot().createChild();

            Set<ArtifactKey> candidates = new LinkedHashSet<>();
            candidates.add(caller);          // exact match — excluded (self)
            candidates.add(callerChild);     // descendant — kept (not self)
            candidates.add(root);            // ancestor — kept (not self)
            candidates.add(unrelated1);      // unrelated — kept
            candidates.add(unrelated2);      // unrelated — kept

            Set<ArtifactKey> result = service.filterSelfCalls(caller, candidates);
            assertThat(result).containsExactlyInAnyOrder(callerChild, root, unrelated1, unrelated2);
        }

        @Test
        void siblingKeys_bothPreserved() {
            ArtifactKey root = ArtifactKey.createRoot();
            ArtifactKey sibling1 = root.createChild();
            ArtifactKey sibling2 = root.createChild();
            Set<ArtifactKey> candidates = Set.of(sibling2);
            assertThat(service.filterSelfCalls(sibling1, candidates)).containsExactly(sibling2);
        }
    }

    // ── Active call chain cycle detection ─────────────────────────────────

    @Nested
    class CallChainCycleDetection {

        @Test
        void noActiveChain_noCycleDetected() {
            assertThat(service.isInActiveCallChain("nodeA", "nodeB")).isFalse();
        }

        @Test
        void directCycle_detected() {
            // B called A, now A wants to call B — cycle
            service.registerCall("nodeB", "nodeA", "call-1");
            assertThat(service.isInActiveCallChain("nodeA", "nodeB")).isTrue();
        }

        @Test
        void indirectCycle_detected() {
            // C called B, B called A, now A wants to call C — cycle
            service.registerCall("nodeC", "nodeB", "call-1");
            service.registerCall("nodeB", "nodeA", "call-2");
            assertThat(service.isInActiveCallChain("nodeA", "nodeC")).isTrue();
        }

        @Test
        void noCycle_whenChainDoesNotReachCaller() {
            // B called C, now A wants to call B — no cycle (B→C, not B→A)
            service.registerCall("nodeB", "nodeC", "call-1");
            assertThat(service.isInActiveCallChain("nodeA", "nodeB")).isFalse();
        }

        @Test
        void unregisterCall_breaksCycle() {
            service.registerCall("nodeB", "nodeA", "call-1");
            assertThat(service.isInActiveCallChain("nodeA", "nodeB")).isTrue();

            service.unregisterCall("nodeB", "nodeA");
            assertThat(service.isInActiveCallChain("nodeA", "nodeB")).isFalse();
        }

        @Test
        void filterSelfCalls_excludesCycleCandidates() {
            ArtifactKey callerKey = ArtifactKey.createRoot();
            ArtifactKey targetKey = ArtifactKey.createRoot();

            // Target has an active call to caller — calling target would create a cycle
            service.registerCall(targetKey.value(), callerKey.value(), "call-1");

            Set<ArtifactKey> candidates = Set.of(targetKey);
            assertThat(service.filterSelfCalls(callerKey, candidates)).isEmpty();
        }

        @Test
        void filterSelfCalls_preservesNonCycleCandidates() {
            ArtifactKey callerKey = ArtifactKey.createRoot();
            ArtifactKey targetKey = ArtifactKey.createRoot();
            ArtifactKey otherKey = ArtifactKey.createRoot();

            // Target called other, not caller — no cycle
            service.registerCall(targetKey.value(), otherKey.value(), "call-1");

            Set<ArtifactKey> candidates = Set.of(targetKey);
            assertThat(service.filterSelfCalls(callerKey, candidates)).containsExactly(targetKey);
        }
    }

    // ── resolveOwningNodeId ──────────────────────────────────────────────

    @Nested
    class ResolveOwningNodeId {

        @Test
        void noSessionEvent_returnsKeyValue() {
            ArtifactKey key = ArtifactKey.createRoot();
            lenient().when(eventStreamRepository.getLastMatching(any(), any()))
                    .thenReturn(Optional.empty());
            assertThat(service.resolveOwningNodeId(key)).isEqualTo(key.value());
        }

        @Test
        @SuppressWarnings("unchecked")
        void sessionEventExists_returnsOwningNodeId() {
            ArtifactKey chatKey = ArtifactKey.createRoot();
            String owningNodeId = "ak:OWNER";
            var sessionEvent = new Events.ChatSessionCreatedEvent(
                    "e1", java.time.Instant.now(), owningNodeId, chatKey, "opts"
            );
            when(eventStreamRepository.getLastMatching(any(), any()))
                    .thenAnswer(inv -> {
                        java.util.function.Predicate<Events.ChatSessionCreatedEvent> pred = inv.getArgument(1);
                        if (pred.test(sessionEvent)) {
                            return Optional.of(sessionEvent);
                        }
                        return Optional.empty();
                    });
            assertThat(service.resolveOwningNodeId(chatKey)).isEqualTo(owningNodeId);
        }
    }

    // ── resolveSessionForMessage ──────────────────────────────────────────

    @Nested
    class ResolveSessionForMessage {

        @Test
        @SuppressWarnings("unchecked")
        void noMatchingSessions_returnsEmpty() {
            when(eventStreamRepository.getAllMatching(eq(Events.ChatSessionCreatedEvent.class), any()))
                    .thenReturn(Stream.empty());

            var evt = mock(com.embabel.agent.api.channel.MessageOutputChannelEvent.class);
            lenient().when(evt.getProcessId()).thenReturn("ak:01TEST");

            assertThat(service.resolveSessionForMessage(evt)).isEmpty();
        }

        @Test
        @SuppressWarnings("unchecked")
        void exactMatch_returnsSessionKey() {
            ArtifactKey sessionKey = ArtifactKey.createRoot();
            var sessionEvent = new Events.ChatSessionCreatedEvent(
                    "e1", Instant.now(), "node1", sessionKey, "opts"
            );
            when(eventStreamRepository.getAllMatching(eq(Events.ChatSessionCreatedEvent.class), any()))
                    .thenAnswer(inv -> {
                        java.util.function.Predicate<Events.ChatSessionCreatedEvent> pred = inv.getArgument(1);
                        return Stream.of(sessionEvent).filter(pred);
                    });

            var evt = mock(com.embabel.agent.api.channel.MessageOutputChannelEvent.class);
            when(evt.getProcessId()).thenReturn(sessionKey.value());

            assertThat(service.resolveSessionForMessage(evt)).contains(sessionKey);
        }
    }

    // ── Lifecycle eviction ────────────────────────────────────────────────

    @Nested
    class LifecycleEviction {

        @Test
        void isInterestedIn_goalCompletedEvent() {
            assertThat(service.isInterestedIn(new Events.GoalCompletedEvent(
                    "e1", Instant.now(), "node1", "wf1", null
            ))).isTrue();
        }

        @Test
        void isInterestedIn_actionCompletedEvent() {
            assertThat(service.isInterestedIn(new Events.ActionCompletedEvent(
                    "e1", Instant.now(), "node1", "agent", "action", "SUCCESS", null
            ))).isTrue();
        }

        @Test
        void notInterestedIn_otherEvents() {
            assertThat(service.isInterestedIn(new Events.NodeAddedEvent(
                    "e1", Instant.now(), "node1", "title", Events.NodeType.ORCHESTRATOR, null, null
            ))).isFalse();
        }

        @Test
        void onEvent_nullNodeId_noException() {
            // Should not throw
            service.onEvent(new Events.GoalCompletedEvent("e1", Instant.now(), null, "wf1", null));
            service.onEvent(new Events.ActionCompletedEvent("e1", Instant.now(), null, "agent", "action", "SUCCESS", null));
        }

        @Test
        void onEvent_blankNodeId_noException() {
            service.onEvent(new Events.GoalCompletedEvent("e1", Instant.now(), "", "wf1", null));
            service.onEvent(new Events.ActionCompletedEvent("e1", Instant.now(), "  ", "agent", "action", "SUCCESS", null));
        }
    }
}
