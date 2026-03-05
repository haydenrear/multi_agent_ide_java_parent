package com.hayden.multiagentide.filter.service;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.filter.model.executor.AiFilterTool;
import com.hayden.multiagentidelib.model.nodes.OrchestratorNode;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiFilterSessionResolverTest {

    @Mock
    private GraphRepository graphRepository;
    @Mock
    private EventBus eventBus;

    private AiFilterSessionResolver resolver;

    @BeforeEach
    void setUp() {
        lenient().when(graphRepository.findById(anyString())).thenReturn(Optional.empty());
        resolver = new AiFilterSessionResolver(graphRepository);
        resolver.setEventBus(eventBus);
    }

    @Test
    void perInvocation_returnsUniqueChildEachTime() {
        ArtifactKey current = ArtifactKey.createRoot();
        PromptContext promptContext = PromptContext.builder()
                .currentContextId(current)
                .build();

        ArtifactKey first = resolver.resolveSessionKey(
                "policy-a",
                AiFilterTool.SessionMode.PER_INVOCATION,
                promptContext
        );
        ArtifactKey second = resolver.resolveSessionKey(
                "policy-a",
                AiFilterTool.SessionMode.PER_INVOCATION,
                promptContext
        );

        assertThat(first).isNotEqualTo(second);
        assertThat(first.parent()).contains(current);
        assertThat(second.parent()).contains(current);
    }

    @Test
    void sameSessionForAll_reusesCachedKeyWithinExecutionRoot() {
        ArtifactKey root = ArtifactKey.createRoot();
        registerExecutionNode(root);

        PromptContext firstContext = PromptContext.builder()
                .currentContextId(root.createChild().createChild())
                .build();
        PromptContext secondContext = PromptContext.builder()
                .currentContextId(root.createChild().createChild())
                .build();

        ArtifactKey first = resolver.resolveSessionKey(
                "policy-a",
                AiFilterTool.SessionMode.SAME_SESSION_FOR_ALL,
                firstContext
        );
        ArtifactKey second = resolver.resolveSessionKey(
                "policy-a",
                AiFilterTool.SessionMode.SAME_SESSION_FOR_ALL,
                secondContext
        );

        assertThat(first).isEqualTo(second);
        assertThat(first.parent()).contains(root);
    }

    @Test
    void sameSessionForAction_scopesByAgentTypeQualifier() {
        ArtifactKey root = ArtifactKey.createRoot();
        registerExecutionNode(root);

        PromptContext orchestratorContext = PromptContext.builder()
                .currentContextId(root.createChild())
                .agentType(AgentType.ORCHESTRATOR)
                .build();
        PromptContext discoveryContext = PromptContext.builder()
                .currentContextId(root.createChild())
                .agentType(AgentType.DISCOVERY_AGENT)
                .build();

        ArtifactKey orchestratorFirst = resolver.resolveSessionKey(
                "policy-a",
                AiFilterTool.SessionMode.SAME_SESSION_FOR_ACTION,
                orchestratorContext
        );
        ArtifactKey orchestratorSecond = resolver.resolveSessionKey(
                "policy-a",
                AiFilterTool.SessionMode.SAME_SESSION_FOR_ACTION,
                orchestratorContext
        );
        ArtifactKey discovery = resolver.resolveSessionKey(
                "policy-a",
                AiFilterTool.SessionMode.SAME_SESSION_FOR_ACTION,
                discoveryContext
        );

        assertThat(orchestratorFirst).isEqualTo(orchestratorSecond);
        assertThat(discovery).isNotEqualTo(orchestratorFirst);
    }

    @Test
    void goalCompletedEvent_evictsRootScopedSessions() {
        ArtifactKey root = ArtifactKey.createRoot();
        registerExecutionNode(root);

        PromptContext promptContext = PromptContext.builder()
                .currentContextId(root)
                .build();

        ArtifactKey first = resolver.resolveSessionKey(
                "policy-a",
                AiFilterTool.SessionMode.SAME_SESSION_FOR_ALL,
                promptContext
        );

        resolver.onEvent(new Events.GoalCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                root.value(),
                "wf-1",
                null
        ));

        ArtifactKey second = resolver.resolveSessionKey(
                "policy-a",
                AiFilterTool.SessionMode.SAME_SESSION_FOR_ALL,
                promptContext
        );

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void actionCompletedEvent_evictsActionScopedSessions_only() {
        ArtifactKey root = ArtifactKey.createRoot();
        registerExecutionNode(root);

        PromptContext promptContext = PromptContext.builder()
                .currentContextId(root)
                .agentType(AgentType.ORCHESTRATOR)
                .build();

        ArtifactKey actionFirst = resolver.resolveSessionKey(
                "policy-a",
                AiFilterTool.SessionMode.SAME_SESSION_FOR_ACTION,
                promptContext
        );
        ArtifactKey agentFirst = resolver.resolveSessionKey(
                "policy-a",
                AiFilterTool.SessionMode.SAME_SESSION_FOR_AGENT,
                promptContext
        );

        resolver.onEvent(new Events.ActionCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                root.value(),
                "workflow-agent",
                "coordinateWorkflow",
                "SUCCESS",
                null
        ));

        ArtifactKey actionSecond = resolver.resolveSessionKey(
                "policy-a",
                AiFilterTool.SessionMode.SAME_SESSION_FOR_ACTION,
                promptContext
        );
        ArtifactKey agentSecond = resolver.resolveSessionKey(
                "policy-a",
                AiFilterTool.SessionMode.SAME_SESSION_FOR_AGENT,
                promptContext
        );

        assertThat(actionSecond).isNotEqualTo(actionFirst);
        assertThat(agentSecond).isEqualTo(agentFirst);
    }

    @Test
    void sameSessionForAction_publishesCreationEvent_onlyOnFirstResolve() {
        ArtifactKey root = ArtifactKey.createRoot();
        registerExecutionNode(root);

        PromptContext promptContext = PromptContext.builder()
                .currentContextId(root)
                .agentType(AgentType.ORCHESTRATOR)
                .build();

        ArtifactKey first = resolver.resolveSessionKey(
                "policy-x",
                AiFilterTool.SessionMode.SAME_SESSION_FOR_ACTION,
                promptContext
        );
        ArtifactKey second = resolver.resolveSessionKey(
                "policy-x",
                AiFilterTool.SessionMode.SAME_SESSION_FOR_ACTION,
                promptContext
        );

        assertThat(second).isEqualTo(first);

        ArgumentCaptor<Events.GraphEvent> events = ArgumentCaptor.forClass(Events.GraphEvent.class);
        verify(eventBus, times(1)).publish(events.capture());
        assertThat(events.getValue()).isInstanceOf(Events.AiFilterSessionEvent.class);

        Events.AiFilterSessionEvent created = (Events.AiFilterSessionEvent) events.getValue();
        assertThat(created.policyId()).isEqualTo("policy-x");
        assertThat(created.sessionMode()).isEqualTo(AiFilterTool.SessionMode.SAME_SESSION_FOR_ACTION.name());
        assertThat(created.scopeNodeId()).isEqualTo(root.value());
        assertThat(created.sessionContextId()).isEqualTo(first);
    }

    private void registerExecutionNode(ArtifactKey key) {
        Instant now = Instant.now();

        MainWorktreeContext worktreeContext = MainWorktreeContext.builder()
                .worktreeId("wt-" + UUID.randomUUID())
                .worktreePath(Path.of("."))
                .baseBranch("main")
                .derivedBranch("main")
                .status(WorktreeContext.WorktreeStatus.ACTIVE)
                .associatedNodeId(key.value())
                .createdAt(now)
                .lastCommitHash("HEAD")
                .repositoryUrl("https://example.com/repo.git")
                .hasSubmodules(false)
                .submoduleWorktrees(new ArrayList<>())
                .metadata(new HashMap<>())
                .build();

        OrchestratorNode graphNode = OrchestratorNode.builder()
                .nodeId(key.value())
                .title("Workflow")
                .goal("Test goal")
                .status(Events.NodeStatus.RUNNING)
                .parentNodeId(null)
                .childNodeIds(new ArrayList<>())
                .metadata(new HashMap<>())
                .createdAt(now)
                .lastUpdatedAt(now)
                .worktreeContext(worktreeContext)
                .orchestratorOutput("")
                .build();

        when(graphRepository.findById(key.value())).thenReturn(Optional.of(graphNode));
    }
}
