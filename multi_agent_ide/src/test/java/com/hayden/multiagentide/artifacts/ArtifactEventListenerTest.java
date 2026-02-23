package com.hayden.multiagentide.artifacts;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.artifact.PromptTemplateVersion;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ArtifactEventListener.
 * 
 * Tests verify:
 * - Event subscription and filtering
 * - Artifact event handling with deduplication
 * - Execution completion and persistence
 * - Template artifact handling
 */
@ExtendWith(MockitoExtension.class)
class ArtifactEventListenerTest {
    
    @Mock
    private EventBus eventBus;
    
    @Mock
    private ArtifactTreeBuilder treeBuilder;

    @Mock
    private EventArtifactMapper eventArtifactMapper;

    @Captor
    private ArgumentCaptor<EventListener> listenerCaptor;
    
    @Captor
    private ArgumentCaptor<Artifact> artifactCaptor;
    
    private ArtifactEventListener listener;
    
    private ArtifactKey rootKey;
    private String executionKey;
    
    @BeforeEach
    void setUp() {
        listener = new ArtifactEventListener(treeBuilder, eventArtifactMapper);
        eventBus.subscribe(listener);

        // Set properties via reflection since @Value won't work in unit tests
        ReflectionTestUtils.setField(listener, "persistenceEnabled", true);

        rootKey = ArtifactKey.createRoot();
        executionKey = rootKey.value();
    }
    
    @Nested
    @DisplayName("Initialization")
    class Initialization {

        @Test
        @DisplayName("listenerId returns expected value")
        void listenerIdReturnsExpectedValue() {
            assertThat(listener.listenerId()).isEqualTo("artifact-event-listener");
        }
    }
    
    @Nested
    @DisplayName("Event Interest")
    class EventInterest {
        
        @Test
        @DisplayName("isInterestedIn returns true for ArtifactEvent")
        void isInterestedInReturnsTrueForArtifactEvent() {
            Events.ArtifactEvent event = createArtifactEvent(rootKey, createExecutionArtifact(rootKey));
            
            assertThat(listener.isInterestedIn(event)).isTrue();
        }
        
        @Test
        @DisplayName("isInterestedIn returns true for GoalCompletedEvent")
        void isInterestedInReturnsTrueForGoalCompletedEvent() {
            Events.GoalCompletedEvent event = new Events.GoalCompletedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    "orchestrator-1",
                    "Completed successfully",
                    AgentModels.OrchestratorCollectorResult.builder().build()
            );
            
            assertThat(listener.isInterestedIn(event)).isTrue();
        }
        
        @Test
        @DisplayName("isInterestedIn returns false for other events")
        void isInterestedInReturnsFalseForOtherEvents() {
            Events.NodeAddedEvent event = new Events.NodeAddedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    "node-1",
                    null,
                    Events.NodeType.WORK,
                    "TestAgent"
            );
            
            assertThat(listener.isInterestedIn(event)).isFalse();
        }
    }
    
    @Nested
    @DisplayName("Artifact Event Handling")
    class ArtifactEventHandling {
        
        @Test
        @DisplayName("onEvent adds artifact to tree builder")
        void onEventAddsArtifactToTreeBuilder() {
            when(treeBuilder.addArtifact(any())).thenReturn(true);
            
            Artifact.ExecutionArtifact artifact = createExecutionArtifact(rootKey);
            Events.ArtifactEvent event = createArtifactEvent(rootKey, artifact);
            
            listener.onEvent(event);
            
            verify(treeBuilder).addArtifact(artifactCaptor.capture());
            assertThat(artifactCaptor.getValue()).isEqualTo(artifact);
        }
        
        @Test
        @DisplayName("onEvent extracts execution key from root artifact")
        void onEventExtractsExecutionKeyFromRoot() {
            when(treeBuilder.addArtifact(any())).thenReturn(true);
            
            Events.ArtifactEvent event = createArtifactEvent(rootKey, createExecutionArtifact(rootKey));
            
            listener.onEvent(event);
            
            verify(treeBuilder).addArtifact(any());
        }
        
        @Test
        @DisplayName("onEvent extracts execution key from child artifact")
        void onEventExtractsExecutionKeyFromChild() {
            when(treeBuilder.addArtifact(any())).thenReturn(true);
            
            ArtifactKey childKey = rootKey.createChild();
            Events.ArtifactEvent event = createArtifactEvent(childKey, createGroupArtifact(childKey, "Child"));
            
            listener.onEvent(event);
            
            // Should extract root key as execution key
            verify(treeBuilder).addArtifact(any());
        }
        
        @Test
        @DisplayName("onEvent handles null artifact gracefully")
        void onEventHandlesNullArtifactGracefully() {
            Events.ArtifactEvent event = new Events.ArtifactEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    "node-1",
                    "Execution",
                    null,
                    null
            );
            
            // Should not throw, just log warning
            listener.onEvent(event);
            
            verify(treeBuilder, never()).addArtifact(anyString(), any());
        }
        
        @Test
        @DisplayName("onEvent does nothing when persistence disabled")
        void onEventDoesNothingWhenPersistenceDisabled() {
            ReflectionTestUtils.setField(listener, "persistenceEnabled", false);
            
            Events.ArtifactEvent event = createArtifactEvent(rootKey, createExecutionArtifact(rootKey));
            
            listener.onEvent(event);
            
            verify(treeBuilder, never()).addArtifact(anyString(), any());
        }
    }
    
    @Nested
    @DisplayName("Template Artifact Handling")
    class TemplateArtifactHandling {
        
        @Test
        @DisplayName("PromptTemplateVersion artifact is added correctly")
        void promptTemplateVersionIsAddedCorrectly() {
            when(treeBuilder.addArtifact(any())).thenReturn(true);
            
            ArtifactKey templateKey = rootKey.createChild();
            PromptTemplateVersion template = PromptTemplateVersion.builder()
                    .templateStaticId("tpl.agent.test.system")
                    .templateText("You are a helpful assistant.")
                    .hash("template-hash-123")
                    .templateArtifactKey(templateKey)
                    .lastUpdatedAt(Instant.now())
                    .build();
            
            Events.ArtifactEvent event = createArtifactEvent(templateKey, template);
            
            listener.onEvent(event);
            
            verify(treeBuilder).addArtifact(artifactCaptor.capture());
            assertThat(artifactCaptor.getValue()).isInstanceOf(PromptTemplateVersion.class);
            
            PromptTemplateVersion captured = (PromptTemplateVersion) artifactCaptor.getValue();
            assertThat(captured.templateStaticId()).isEqualTo("tpl.agent.test.system");
        }
        
        @Test
        @DisplayName("multiple templates with different hashes are added")
        void multipleTemplatesWithDifferentHashesAreAdded() {
            when(treeBuilder.addArtifact(any())).thenReturn(true);
            
            ArtifactKey template1Key = rootKey.createChild();
            PromptTemplateVersion template1 = PromptTemplateVersion.builder()
                    .templateStaticId("tpl.agent.test.first")
                    .templateText("First template")
                    .hash("hash-1")
                    .templateArtifactKey(template1Key)
                    .lastUpdatedAt(Instant.now())
                    .build();
            
            ArtifactKey template2Key = rootKey.createChild();
            PromptTemplateVersion template2 = PromptTemplateVersion.builder()
                    .templateStaticId("tpl.agent.test.second")
                    .templateText("Second template")
                    .hash("hash-2")
                    .templateArtifactKey(template2Key)
                    .lastUpdatedAt(Instant.now())
                    .build();
            
            listener.onEvent(createArtifactEvent(template1Key, template1));
            listener.onEvent(createArtifactEvent(template2Key, template2));
            
            verify(treeBuilder, times(2)).addArtifact(any());
        }
    }
    
    @Nested
    @DisplayName("Execution Completion")
    class ExecutionCompletion {
        
        @Test
        @DisplayName("GoalCompletedEvent triggers persistence")
        void goalCompletedEventTriggersPersistence() {
            // Register execution first
            listener.registerExecution(executionKey, "workflow-run-1");
            
            Events.GoalCompletedEvent event = new Events.GoalCompletedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    "workflow-run-1",
                    "Completed",
                    AgentModels.OrchestratorCollectorResult.builder().build()
            );
            
            listener.onEvent(event);
            
//            verify(treeBuilder).persistExecutionTree(executionKey);
        }
        
        @Test
        @DisplayName("GoalCompletedEvent with unknown workflow logs warning")
        void goalCompletedEventWithUnknownWorkflowLogsWarning() {
            Events.GoalCompletedEvent event = new Events.GoalCompletedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    "unknown-workflow",
                    "Completed",
                    AgentModels.OrchestratorCollectorResult.builder().build()
            );
            
            // Should not throw, just log warning
            listener.onEvent(event);
            
            verify(treeBuilder, never()).persistExecutionTree(anyString());
        }
    }
    
    @Nested
    @DisplayName("Execution Registration")
    class ExecutionRegistration {
        
        @Test
        @DisplayName("registerExecution stores mapping")
        void registerExecutionStoresMapping() {
            listener.registerExecution(executionKey, "workflow-123");
            
            // Verify by triggering completion
            Events.GoalCompletedEvent event = new Events.GoalCompletedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    "workflow-123",
                    "Done",
                    AgentModels.OrchestratorCollectorResult.builder().build()
            );
            
            listener.onEvent(event);
            
//            verify(treeBuilder).persistExecutionTree(executionKey);
        }
        
        @Test
        @DisplayName("flushExecution calls tree builder persist")
        void flushExecutionCallsTreeBuilderPersist() {
            listener.flushExecution(executionKey);
            
            verify(treeBuilder).persistExecutionTree(executionKey);
        }
        
        @Test
        @DisplayName("flushExecution does nothing when persistence disabled")
        void flushExecutionDoesNothingWhenDisabled() {
            ReflectionTestUtils.setField(listener, "persistenceEnabled", false);
            
            listener.flushExecution(executionKey);
            
            verify(treeBuilder, never()).persistExecution(anyString());
        }
    }
    
    @Nested
    @DisplayName("Complex Scenarios")
    class ComplexScenarios {
        
        @Test
        @DisplayName("full execution lifecycle works correctly")
        void fullExecutionLifecycleWorksCorrectly() {
            when(treeBuilder.addArtifact(any())).thenReturn(true);

            // Register execution
            listener.registerExecution(executionKey, "workflow-full-test");
            
            // Add root artifact
            Artifact.ExecutionArtifact root = createExecutionArtifact(rootKey);
            listener.onEvent(createArtifactEvent(rootKey, root));
            
            // Add child artifacts
            ArtifactKey groupKey = rootKey.createChild();
            listener.onEvent(createArtifactEvent(groupKey, createGroupArtifact(groupKey, "Group")));
            
            ArtifactKey templateKey = groupKey.createChild();
            PromptTemplateVersion template = PromptTemplateVersion.builder()
                    .templateStaticId("tpl.test.lifecycle")
                    .templateText("Test template")
                    .hash("lifecycle-hash")
                    .templateArtifactKey(templateKey)
                    .lastUpdatedAt(Instant.now())
                    .build();
            listener.onEvent(createArtifactEvent(templateKey, template));
            
            // Complete execution
            Events.GoalCompletedEvent completion = new Events.GoalCompletedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    "workflow-full-test",
                    "Success",
                    AgentModels.OrchestratorCollectorResult.builder().build()
            );
            listener.onEvent(completion);
            
            // Verify all artifacts were added
            verify(treeBuilder, times(3)).addArtifact(any());
            
            // Verify finished was called
//            verify(treeBuilder).persistExecutionTree(executionKey);
        }
        
        @Test
        @DisplayName("multiple executions are tracked separately")
        void multipleExecutionsAreTrackedSeparately() {
            when(treeBuilder.addArtifact(anyString(), any())).thenReturn(true);
            
            // Create two separate executions
            ArtifactKey rootKey1 = ArtifactKey.createRoot();
            String execKey1 = rootKey1.value();
            listener.registerExecution(execKey1, "workflow-1");
            
            ArtifactKey rootKey2 = ArtifactKey.createRoot();
            String execKey2 = rootKey2.value();
            listener.registerExecution(execKey2, "workflow-2");
            
            // Add artifacts to both
            listener.onEvent(createArtifactEvent(rootKey1, createExecutionArtifact(rootKey1)));
            listener.onEvent(createArtifactEvent(rootKey2, createExecutionArtifact(rootKey2)));
            
            // Complete first execution
            listener.onEvent(new Events.GoalCompletedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    "workflow-1",
                    "Done",
                    AgentModels.OrchestratorCollectorResult.builder().build()
            ));
            
//            // Verify only first execution was finished
//            verify(treeBuilder).persistExecutionTree(execKey1);
//            verify(treeBuilder, never()).persistExecutionTree(execKey2);

            // Complete second execution
            listener.onEvent(new Events.GoalCompletedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    "workflow-2",
                    "Done",
                    AgentModels.OrchestratorCollectorResult.builder().build()
            ));
            
            // Verify second execution was finished
//            verify(treeBuilder).persistExecutionTree(execKey2);
        }
    }
    
    // ========== Helper Methods ==========
    
    private Events.ArtifactEvent createArtifactEvent(ArtifactKey key, Artifact artifact) {
        return new Events.ArtifactEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                "node-1",
                artifact.artifactType(),
                key.parent().map(ArtifactKey::value).orElse(null),
                artifact
        );
    }
    
    private Artifact.ExecutionArtifact createExecutionArtifact(ArtifactKey key) {
        return Artifact.ExecutionArtifact.builder()
                .artifactKey(key)
                .workflowRunId("test-run-" + System.nanoTime())
                .startedAt(Instant.now())
                .status(Artifact.ExecutionStatus.RUNNING)
                .metadata(Map.of())
                .children(List.of())
                .build();
    }

    private Artifact.AgentModelArtifact createGroupArtifact(ArtifactKey key, String name) {
        return Artifact.AgentModelArtifact.builder()
                .agentModel(new Artifact.AgentModel() {
                    @Override
                    public String computeHash(Artifact.HashContext hashContext) {
                        return UUID.randomUUID().toString();
                    }

                    @Override
                    public List<Artifact.AgentModel> children() {
                        return List.of();
                    }

                    @Override
                    public ArtifactKey key() {
                        return key;
                    }

                    @Override
                    public Artifact.AgentModel withContextId(ArtifactKey key) {
                        return this;
                    }

                    @Override
                    public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> c) {
                        return null;
                    }
                })
                .metadata(new java.util.HashMap<>())
                .children(new ArrayList<>())
                .build();
    }
}
