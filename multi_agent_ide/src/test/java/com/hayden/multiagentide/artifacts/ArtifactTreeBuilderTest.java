package com.hayden.multiagentide.artifacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import com.hayden.multiagentide.config.SerdesConfiguration;
import com.hayden.multiagentidelib.artifact.PromptTemplateVersion;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.Builder;
import lombok.With;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.jackson.JsonMixinModule;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ArtifactTreeBuilder.
 * 
 * Tests verify:
 * - Trie-based artifact addition with deduplication
 * - Batch persistence to repository
 * - Tree loading and reconstruction
 */
@ExtendWith(MockitoExtension.class)
class ArtifactTreeBuilderTest {
    
    @Mock
    private ArtifactRepository artifactRepository;
    
    private ObjectMapper objectMapper;
    private ArtifactTreeBuilder treeBuilder;
    
    @Captor
    private ArgumentCaptor<ArtifactEntity> entityListCaptor;
    
    private String executionKey;
    private ArtifactKey rootKey;
    private Artifact.ExecutionArtifact rootArtifact;
    
    @BeforeEach
    void setUp() {
        SerdesConfiguration serdesConfiguration = new SerdesConfiguration();
        Jackson2ObjectMapperBuilder jacksonObjectMapperBuilder = new Jackson2ObjectMapperBuilder();
        serdesConfiguration.artifactSerdesCustomizer().customize(jacksonObjectMapperBuilder);
        objectMapper = jacksonObjectMapperBuilder.build();
        objectMapper.findAndRegisterModules();

        ArtifactService artifactService = new ArtifactService(artifactRepository);
        artifactService.configure();
        treeBuilder = new ArtifactTreeBuilder(artifactRepository, objectMapper, artifactService);

        rootKey = ArtifactKey.createRoot();
        executionKey = rootKey.value();
        rootArtifact = createExecutionArtifact(rootKey);
    }
    
    @Nested
    @DisplayName("Adding Artifacts")
    class AddingArtifacts {
        
        @Test
        @DisplayName("addArtifact creates execution tree with root")
        void addArtifactCreatesExecutionTreeWithRoot() {
            boolean added = treeBuilder.addArtifact(executionKey, rootArtifact);
            
            assertThat(added).isTrue();
            assertThat(treeBuilder.getExecutionTree(executionKey)).isPresent();
        }
        
        @Test
        @DisplayName("addArtifact adds child to existing tree")
        void addArtifactAddsChildToExistingTree() {
            treeBuilder.addArtifact(executionKey, rootArtifact);

            ArtifactKey childKey = rootKey.createChild();
            Artifact.AgentModelArtifact child = createGroupArtifact(childKey, "InputArtifacts");

            boolean added = treeBuilder.addArtifact(executionKey, child);

            assertThat(added).isTrue();
            Collection<Artifact> artifacts = treeBuilder.getExecutionArtifacts(executionKey);
            assertThat(artifacts).hasSize(2);
        }

        @Test
        @DisplayName("addArtifact rejects duplicate key")
        void addArtifactRejectsDuplicateKeyDuplicateHash() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            Artifact.ExecutionArtifact duplicate = createExecutionArtifact(rootKey);
            duplicate = duplicate.toBuilder().hash(rootArtifact.hash()).build();
            boolean added = treeBuilder.addArtifact(executionKey, duplicate);
            
            assertThat(added).isFalse();
        }
        
        @Test
        @DisplayName("addArtifact allows duplicate hash among siblings when key differs")
        void addArtifactAllowsDuplicateHashAmongSiblingsWhenKeyDiffers() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            String sharedHash = "shared-content-hash";
            
            ArtifactKey firstKey = rootKey.createChild();
            Artifact.RenderedPromptArtifact first = createRenderedPromptArtifact(
                    firstKey, "Content", sharedHash);
            
            ArtifactKey secondKey = rootKey.createChild();
            Artifact.RenderedPromptArtifact second = createRenderedPromptArtifact(
                    secondKey, "Content", sharedHash);
            
            boolean firstAdded = treeBuilder.addArtifact(executionKey, first);
            boolean secondAdded = treeBuilder.addArtifact(executionKey, second);
            
            assertThat(firstAdded).isTrue();
            assertThat(secondAdded).isTrue();
            assertThat(treeBuilder.getExecutionArtifacts(executionKey)).hasSize(3);
        }
        
        @Test
        @DisplayName("addArtifact allows different hashes")
        void addArtifactAllowsDifferentHashes() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            ArtifactKey firstKey = rootKey.createChild();
            Artifact.RenderedPromptArtifact first = createRenderedPromptArtifact(
                    firstKey, "Content A", "hash-a");
            
            ArtifactKey secondKey = rootKey.createChild();
            Artifact.RenderedPromptArtifact second = createRenderedPromptArtifact(
                    secondKey, "Content B", "hash-b");
            
            boolean firstAdded = treeBuilder.addArtifact(executionKey, first);
            boolean secondAdded = treeBuilder.addArtifact(executionKey, second);
            
            assertThat(firstAdded).isTrue();
            assertThat(secondAdded).isTrue();
            assertThat(treeBuilder.getExecutionArtifacts(executionKey)).hasSize(3);
        }
    }
    
    @Nested
    @DisplayName("Getting Artifacts")
    class GettingArtifacts {
        
        @Test
        @DisplayName("getArtifact returns artifact by key")
        void getArtifactReturnsByKey() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            ArtifactKey childKey = rootKey.createChild();
            Artifact.AgentModelArtifact child = createGroupArtifact(childKey, "TestGroup");
            treeBuilder.addArtifact(executionKey, child);
            
            Optional<Artifact> found = treeBuilder.getArtifact(executionKey, childKey.value());
            
            assertThat(found).isPresent();
            assertThat(found.get()).isEqualTo(child);
        }
        
        @Test
        @DisplayName("getArtifact returns empty for non-existent key")
        void getArtifactReturnsEmptyForNonExistent() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            Optional<Artifact> found = treeBuilder.getArtifact(executionKey, "ak:NONEXISTENT00000000000000");
            
            assertThat(found).isEmpty();
        }
        
        @Test
        @DisplayName("getExecutionArtifacts returns all artifacts")
        void getExecutionArtifactsReturnsAll() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            ArtifactKey child1Key = rootKey.createChild();
            ArtifactKey child2Key = rootKey.createChild();
            ArtifactKey grandchildKey = child1Key.createChild();
            
            treeBuilder.addArtifact(executionKey, createGroupArtifact(child1Key, "Child1"));
            treeBuilder.addArtifact(executionKey, createGroupArtifact(child2Key, "Child2"));
            treeBuilder.addArtifact(executionKey, createGroupArtifact(grandchildKey, "Grandchild"));
            
            Collection<Artifact> artifacts = treeBuilder.getExecutionArtifacts(executionKey);
            
            assertThat(artifacts).hasSize(4);
        }
        
        @Test
        @DisplayName("getExecutionArtifacts returns empty for unknown execution")
        void getExecutionArtifactsReturnsEmptyForUnknown() {
            Collection<Artifact> artifacts = treeBuilder.getExecutionArtifacts("unknown-execution");
            
            assertThat(artifacts).isEmpty();
        }
        
        @Test
        @DisplayName("hasSiblingWithHash checks parent node")
        void hasSiblingWithHashChecksParentNode() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            String hash = "test-hash";
            ArtifactKey childKey = rootKey.createChild();
            treeBuilder.addArtifact(executionKey, createRenderedPromptArtifact(childKey, "Text", hash));
            
            boolean hasSibling = treeBuilder.hasSiblingWithHash(executionKey, rootKey, hash);
            
            assertThat(hasSibling).isTrue();
        }
    }
    
    @Nested
    @DisplayName("Persistence")
    class Persistence {
        
        @Test
        @DisplayName("persistExecution saves all artifacts to repository")
        void persistExecutionSavesAllArtifacts() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            ArtifactKey childKey = rootKey.createChild();
            treeBuilder.addArtifact(executionKey, createGroupArtifact(childKey, "Child"));
            
            treeBuilder.persistExecution(executionKey);
            
            verify(artifactRepository, times(2)).save(entityListCaptor.capture());
            List<ArtifactEntity> saved = entityListCaptor.getAllValues();
            
            assertThat(saved).hasSize(2);
        }
        
        @Test
        @DisplayName("artifact children list is populated when building tree")
        void artifactChildrenListIsPopulatedWhenBuildingTree() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            ArtifactKey child1Key = rootKey.createChild();
            Artifact.AgentModelArtifact child1 = createGroupArtifact(child1Key, "Child1");
            treeBuilder.addArtifact(executionKey, child1);
            
            ArtifactKey child2Key = rootKey.createChild();
            Artifact.AgentModelArtifact child2 = createGroupArtifact(child2Key, "Child2");
            treeBuilder.addArtifact(executionKey, child2);
            
            // Build the tree
            Optional<Artifact> tree = treeBuilder.buildArtifactTree(executionKey);
            
            assertThat(tree).isPresent();
            Artifact root = tree.get();
            
            // Verify root has children populated
            assertThat(root.children()).hasSize(2);
            assertThat(root.children()).contains(child1, child2);
        }
        
        @Test
        @DisplayName("persistExecution does NOT clear in-memory tree")
        void persistExecutionDoesNotClearInMemoryTree() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            treeBuilder.persistExecution(executionKey);
            
            // After persist, tree should still be available
            assertThat(treeBuilder.getExecutionTree(executionKey)).isPresent();
            assertThat(treeBuilder.getExecutionArtifacts(executionKey)).isNotEmpty();
        }
        
        @Test
        @DisplayName("clearExecution removes in-memory tree")
        void clearExecutionRemovesInMemoryTree() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            treeBuilder.clearExecution(executionKey);
            
            assertThat(treeBuilder.getExecutionTree(executionKey)).isEmpty();
            assertThat(treeBuilder.getExecutionArtifacts(executionKey)).isEmpty();
        }
        
        @Test
        @DisplayName("persistExecution does nothing for unknown execution")
        void persistExecutionDoesNothingForUnknown() {
            treeBuilder.persistExecution("unknown-execution");
            
            verify(artifactRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("entity contains correct fields")
        void entityContainsCorrectFields() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            ArtifactKey childKey = rootKey.createChild();
            Artifact.RenderedPromptArtifact child = createRenderedPromptArtifact(
                    childKey, "Hello world", "content-hash-123");
            treeBuilder.addArtifact(executionKey, child);
            
            treeBuilder.persistExecution(executionKey);
            
            verify(artifactRepository, times(2)).save(entityListCaptor.capture());
            List<ArtifactEntity> saved = entityListCaptor.getAllValues();
            
            ArtifactEntity childEntity = saved.stream()
                    .filter(e -> e.getArtifactKey().equals(childKey.value()))
                    .findFirst()
                    .orElseThrow();
            
            assertThat(childEntity.getParentKey()).isEqualTo(rootKey.value());
            assertThat(childEntity.getExecutionKey()).isEqualTo(executionKey);
            assertThat(childEntity.getArtifactType()).isEqualTo(Artifact.RenderedPromptArtifact.class.getSimpleName());
            assertThat(childEntity.getContentHash()).isEqualTo("content-hash-123");
            assertThat(childEntity.getDepth()).isEqualTo(2);
            assertThat(childEntity.getShared()).isFalse();
        }
    }
    
    @Nested
    @DisplayName("Template Artifacts")
    class TemplateArtifacts {
        
        @Test
        @DisplayName("PromptTemplateVersion can be added and retrieved")
        void promptTemplateVersionCanBeAddedAndRetrieved() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            ArtifactKey templateKey = rootKey.createChild();
            PromptTemplateVersion template = PromptTemplateVersion.builder()
                    .templateStaticId("tpl.agent.test.system")
                    .templateText("You are a helpful assistant.")
                    .hash("template-hash-abc")
                    .templateArtifactKey(templateKey)
                    .lastUpdatedAt(Instant.now())
                    .build();
            
            boolean added = treeBuilder.addArtifact(executionKey, template);
            
            assertThat(added).isTrue();
            
            Optional<Artifact> found = treeBuilder.getArtifact(executionKey, templateKey.value());
            assertThat(found).isPresent();
            assertThat(found.get()).isInstanceOf(PromptTemplateVersion.class);
            
            PromptTemplateVersion retrieved = (PromptTemplateVersion) found.get();
            assertThat(retrieved.templateStaticId()).isEqualTo("tpl.agent.test.system");
            assertThat(retrieved.templateText()).isEqualTo("You are a helpful assistant.");
        }
        
        @Test
        @DisplayName("duplicate template hash is accepted for distinct key")
        void duplicateTemplateHashIsAcceptedForDistinctKey() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            String sharedHash = "same-template-hash";
            
            ArtifactKey firstKey = rootKey.createChild();
            PromptTemplateVersion first = PromptTemplateVersion.builder()
                    .templateStaticId("tpl.agent.test.first")
                    .templateText("Template text")
                    .hash(sharedHash)
                    .templateArtifactKey(firstKey)
                    .lastUpdatedAt(Instant.now())
                    .build();
            
            ArtifactKey secondKey = rootKey.createChild();
            PromptTemplateVersion second = PromptTemplateVersion.builder()
                    .templateStaticId("tpl.agent.test.second")
                    .templateText("Template text")
                    .hash(sharedHash)
                    .templateArtifactKey(secondKey)
                    .lastUpdatedAt(Instant.now())
                    .build();
            
            boolean firstAdded = treeBuilder.addArtifact(executionKey, first);
            boolean secondAdded = treeBuilder.addArtifact(executionKey, second);
            
            assertThat(firstAdded).isTrue();
            assertThat(secondAdded).isTrue();
        }
        
        @Test
        @DisplayName("template entity is persisted correctly")
        void templateEntityIsPersistedCorrectly() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            ArtifactKey templateKey = rootKey.createChild();
            PromptTemplateVersion template = PromptTemplateVersion.builder()
                    .templateStaticId("tpl.workflow.planning.initial")
                    .templateText("Plan the following task: {{ task }}")
                    .hash("planning-template-hash")
                    .templateArtifactKey(templateKey)
                    .lastUpdatedAt(Instant.now())
                    .build();
            
            treeBuilder.addArtifact(executionKey, template);
            treeBuilder.persistExecution(executionKey);
            
            verify(artifactRepository, times(2)).save(entityListCaptor.capture());
            List<ArtifactEntity> saved = entityListCaptor.getAllValues();
            
            ArtifactEntity templateEntity = saved.stream()
                    .filter(e -> e.getArtifactKey().equals(templateKey.value()))
                    .findFirst()
                    .orElseThrow();
            
            assertThat(templateEntity.getArtifactType()).isEqualTo("PromptTemplateVersion");
            assertThat(templateEntity.getContentHash()).isEqualTo("planning-template-hash");
            assertThat(templateEntity.getContentJson()).contains("tpl.workflow.planning.initial");
        }
        
        @Test
        @DisplayName("template with nested children works correctly")
        void templateWithNestedChildrenWorksCorrectly() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            // Add a group for templates
            ArtifactKey templatesGroupKey = rootKey.createChild();
            treeBuilder.addArtifact(executionKey, createGroupArtifact(templatesGroupKey, "Templates"));
            
            // Add template under the group
            ArtifactKey templateKey = templatesGroupKey.createChild();
            PromptTemplateVersion template = PromptTemplateVersion.builder()
                    .templateStaticId("tpl.agent.discovery.system")
                    .templateText("Discover relevant information.")
                    .hash("discovery-hash")
                    .templateArtifactKey(templateKey)
                    .lastUpdatedAt(Instant.now())
                    .build();
            
            boolean added = treeBuilder.addArtifact(executionKey, template);
            
            assertThat(added).isTrue();
            
            // Verify it's under the correct parent
            Optional<Artifact> found = treeBuilder.getArtifact(executionKey, templateKey.value());
            assertThat(found).isPresent();
            assertThat(templateKey.isChildOf(templatesGroupKey)).isTrue();
        }
    }
    
    @Nested
    @DisplayName("Complex Tree Scenarios")
    class ComplexTreeScenarios {
        
        @Test
        @DisplayName("deep tree hierarchy works correctly")
        void deepTreeHierarchyWorksCorrectly() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            // Build 5 levels deep
            ArtifactKey currentKey = rootKey;
            for (int i = 0; i < 5; i++) {
                ArtifactKey childKey = currentKey.createChild();
                treeBuilder.addArtifact(executionKey, createGroupArtifact(childKey, "Level" + i));
                currentKey = childKey;
            }
            
            Collection<Artifact> artifacts = treeBuilder.getExecutionArtifacts(executionKey);
            assertThat(artifacts).hasSize(6); // root + 5 levels
        }
        
        @Test
        @DisplayName("wide tree with many siblings works correctly")
        void wideTreeWithManySiblingsWorksCorrectly() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            // Add 20 siblings under root
            for (int i = 0; i < 20; i++) {
                ArtifactKey childKey = rootKey.createChild();
                treeBuilder.addArtifact(executionKey, createGroupArtifact(childKey, "Sibling" + i));
            }
            
            Collection<Artifact> artifacts = treeBuilder.getExecutionArtifacts(executionKey);
            assertThat(artifacts).hasSize(21); // root + 20 siblings
        }
        
        @Test
        @DisplayName("mixed artifact types in tree works correctly")
        void mixedArtifactTypesWorksCorrectly() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            // Add various artifact types
            ArtifactKey groupKey = rootKey.createChild();
            treeBuilder.addArtifact(executionKey, createGroupArtifact(groupKey, "AgentExecution"));
            
            ArtifactKey promptKey = groupKey.createChild();
            treeBuilder.addArtifact(executionKey, createRenderedPromptArtifact(promptKey, "Hello", "hash1"));
            
            ArtifactKey templateKey = groupKey.createChild();
            treeBuilder.addArtifact(executionKey, PromptTemplateVersion.builder()
                    .templateStaticId("tpl.test.mixed")
                    .templateText("Template")
                    .hash("hash2")
                    .templateArtifactKey(templateKey)
                    .lastUpdatedAt(Instant.now())
                    .build());
            
            ArtifactKey toolCallKey = groupKey.createChild();
            treeBuilder.addArtifact(executionKey, Artifact.ToolCallArtifact.builder()
                    .artifactKey(toolCallKey)
                    .toolCallId("call-123")
                    .toolName("readFile")
                    .inputJson("{\"path\": \"/test.txt\"}")
                    .inputHash("hash3")
                    .outputJson("{\"content\": \"test\"}")
                    .outputHash("hash4")
                    .metadata(Map.of())
                    .children(new ArrayList<>())
                    .build());
            
            Collection<Artifact> artifacts = treeBuilder.getExecutionArtifacts(executionKey);
            assertThat(artifacts).hasSize(5);
            
            // Verify types
            assertThat(artifacts.stream()
                    .map(Artifact::artifactType)
                    .toList())
                    .containsExactlyInAnyOrder(Artifact.ExecutionArtifact.class.getSimpleName(), Artifact.AgentModelArtifact.class.getSimpleName(), Artifact.RenderedPromptArtifact.class.getSimpleName(),
                            PromptTemplateVersion.class.getSimpleName(), Artifact.ToolCallArtifact.class.getSimpleName());
        }
    }
    
    @Nested
    @DisplayName("Hierarchical Insertion with AgentModelArtifact")
    class HierarchicalInsertionWithAgentModel {
        
        /**
         * Test AgentModel implementation for testing purposes.
         */
        @Builder(toBuilder = true)
        @With
        record TestAgentModel(
                ArtifactKey contextId,
                String name,
                String content,
                List<Artifact.AgentModel> children
        ) implements Artifact.AgentModel {

            @Override
            public String computeHash(Artifact.HashContext hashContext) {
                return hashContext.hash(name + ":" + content);
            }

            @Override
            public ArtifactKey key() {
                return contextId;
            }

            @Override
            public String artifactType() {
                return "TestAgentModel";
            }

            @Override
            public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> c) {
                return (T) this.toBuilder()
                        .children(c)
                        .build();
            }

            @Override
            public Map<String, String> metadata() {
                return Map.of("name", name);
            }
        }
        
        @Test
        @DisplayName("AgentModelArtifact is inserted at correct position using createChild")
        void agentModelArtifactIsInsertedAtCorrectPosition() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            // Create child key using createChild
            ArtifactKey childKey = rootKey.createChild();
            TestAgentModel model = new TestAgentModel(childKey, "TestModel", "content1", new ArrayList<>());
            Artifact.AgentModelArtifact artifact = new Artifact.AgentModelArtifact(
                    new ArrayList<>(), model, Map.of("name", model.name()), model.computeHash(Artifact.HashContext.defaultHashContext()));
            
            boolean added = treeBuilder.addArtifact(executionKey, artifact);
            
            assertThat(added).isTrue();
            
            // Verify artifact is at correct position
            Optional<Artifact> found = treeBuilder.getArtifact(executionKey, childKey.value());
            assertThat(found).isPresent();
            assertThat(found.get()).isInstanceOf(Artifact.AgentModelArtifact.class);
            
            // Verify key hierarchy
            assertThat(childKey.isChildOf(rootKey)).isTrue();
            assertThat(childKey.parent()).isPresent();
            assertThat(childKey.parent().get()).isEqualTo(rootKey);
        }
        
        @Test
        @DisplayName("multiple AgentModelArtifacts at same level with different hashes")
        void multipleAgentModelArtifactsAtSameLevelWithDifferentHashes() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            // Add multiple siblings under root
            for (int i = 0; i < 5; i++) {
                ArtifactKey childKey = rootKey.createChild();
                TestAgentModel model = new TestAgentModel(childKey, "Model" + i, "content" + i, new ArrayList<>());
                Artifact.AgentModelArtifact artifact = new Artifact.AgentModelArtifact(
                        new ArrayList<>(), model, Map.of("name", model.name()), model.computeHash(Artifact.HashContext.defaultHashContext()));
                
                boolean added = treeBuilder.addArtifact(executionKey, artifact);
                assertThat(added).isTrue();
            }
            
            Collection<Artifact> artifacts = treeBuilder.getExecutionArtifacts(executionKey);
            assertThat(artifacts).hasSize(6); // root + 5 models
            
            // Verify all are AgentModelArtifact (except root)
            long agentModelCount = artifacts.stream()
                    .filter(a -> a instanceof Artifact.AgentModelArtifact)
                    .count();
            assertThat(agentModelCount).isEqualTo(5);
        }
        
        @Test
        @DisplayName("AgentModelArtifact with duplicate hash is accepted for distinct key")
        void agentModelArtifactWithDuplicateHashIsAcceptedForDistinctKey() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            // Create two models with same content (same hash)
            ArtifactKey firstKey = rootKey.createChild();
            TestAgentModel firstModel = new TestAgentModel(firstKey, "Model", "same-content", new ArrayList<>());
            Artifact.AgentModelArtifact firstArtifact = new Artifact.AgentModelArtifact(
                    new ArrayList<>(), firstModel, Map.of("name", firstModel.name()), firstModel.computeHash(Artifact.HashContext.defaultHashContext()));
            
            ArtifactKey secondKey = rootKey.createChild();
            TestAgentModel secondModel = new TestAgentModel(secondKey, "Model", "same-content", new ArrayList<>());
            Artifact.AgentModelArtifact secondArtifact = new Artifact.AgentModelArtifact(
                    new ArrayList<>(), secondModel, Map.of("name", secondModel.name()), secondModel.computeHash(Artifact.HashContext.defaultHashContext()));
            
            boolean firstAdded = treeBuilder.addArtifact(executionKey, firstArtifact);
            boolean secondAdded = treeBuilder.addArtifact(executionKey, secondArtifact);
            
            assertThat(firstAdded).isTrue();
            assertThat(secondAdded).isTrue();
            
            Collection<Artifact> artifacts = treeBuilder.getExecutionArtifacts(executionKey);
            assertThat(artifacts).hasSize(3); // root + both models
        }
        
        @Test
        @DisplayName("nested AgentModelArtifact hierarchy with createChild")
        void nestedAgentModelArtifactHierarchyWithCreateChild() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            // Level 1: Group under root
            ArtifactKey level1Key = rootKey.createChild();
            treeBuilder.addArtifact(executionKey, createGroupArtifact(level1Key, "AgentExecutions"));
            
            // Level 2: AgentModelArtifact under group
            ArtifactKey level2Key = level1Key.createChild();
            TestAgentModel level2Model = new TestAgentModel(level2Key, "Level2Model", "level2", new ArrayList<>());
            Artifact.AgentModelArtifact level2Artifact = new Artifact.AgentModelArtifact(
                    new ArrayList<>(), level2Model, Map.of("name", level2Model.name()), level2Model.computeHash(Artifact.HashContext.defaultHashContext()));
            
            boolean level2Added = treeBuilder.addArtifact(executionKey, level2Artifact);
            assertThat(level2Added).isTrue();
            
            // Level 3: AgentModelArtifact under level 2
            ArtifactKey level3Key = level2Key.createChild();
            TestAgentModel level3Model = new TestAgentModel(level3Key, "Level3Model", "level3", new ArrayList<>());
            Artifact.AgentModelArtifact level3Artifact = new Artifact.AgentModelArtifact(
                    new ArrayList<>(), level3Model, Map.of("name", level3Model.name()), level3Model.computeHash(Artifact.HashContext.defaultHashContext()));
            
            boolean level3Added = treeBuilder.addArtifact(executionKey, level3Artifact);
            assertThat(level3Added).isTrue();
            
            // Verify hierarchy
            assertThat(level2Key.isChildOf(level1Key)).isTrue();
            assertThat(level3Key.isChildOf(level2Key)).isTrue();
            assertThat(level3Key.depth()).isEqualTo(4); // root(1) + level1(2) + level2(3) + level3(4)
            
            // Verify all artifacts are retrievable
            assertThat(treeBuilder.getArtifact(executionKey, level1Key.value())).isPresent();
            assertThat(treeBuilder.getArtifact(executionKey, level2Key.value())).isPresent();
            assertThat(treeBuilder.getArtifact(executionKey, level3Key.value())).isPresent();
        }
        
        @Test
        @DisplayName("same hash at different levels is NOT deduplicated (different parents)")
        void sameHashAtDifferentLevelsIsNotDeduplicated() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            // Level 1: First artifact
            ArtifactKey level1Key = rootKey.createChild();
            TestAgentModel level1Model = new TestAgentModel(level1Key, "SameModel", "same-content", new ArrayList<>());
            Artifact.AgentModelArtifact level1Artifact = new Artifact.AgentModelArtifact(
                    new ArrayList<>(), level1Model, Map.of("name", level1Model.name()), level1Model.computeHash(Artifact.HashContext.defaultHashContext()));
            
            boolean level1Added = treeBuilder.addArtifact(executionKey, level1Artifact);
            assertThat(level1Added).isTrue();
            
            // Level 2: Same content/hash but under different parent
            ArtifactKey level2Key = level1Key.createChild();
            TestAgentModel level2Model = new TestAgentModel(level2Key, "SameModel", "same-content", new ArrayList<>());
            Artifact.AgentModelArtifact level2Artifact = new Artifact.AgentModelArtifact(
                    new ArrayList<>(), level2Model, Map.of("name", level2Model.name()), level2Model.computeHash(Artifact.HashContext.defaultHashContext()));
            
            boolean level2Added = treeBuilder.addArtifact(executionKey, level2Artifact);
            assertThat(level2Added).isTrue(); // Different parent, so NOT deduplicated
            
            Collection<Artifact> artifacts = treeBuilder.getExecutionArtifacts(executionKey);
            assertThat(artifacts).hasSize(3); // root + level1 + level2
        }
        
        @Test
        @DisplayName("complex multi-level tree with many inserts builds correctly")
        void complexMultiLevelTreeWithManyInsertsBuildsCorrectly() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            // Build a tree structure:
            // root
            // ├── InputArtifacts
            // │   ├── Request1
            // │   └── Request2
            // ├── AgentExecution
            // │   ├── Phase1
            // │   │   ├── Prompt1
            // │   │   ├── ToolCall1
            // │   │   └── Result1
            // │   └── Phase2
            // │       ├── Prompt2
            // │       └── Result2
            // └── Outputs
            //     └── FinalResult
            
            // InputArtifacts branch
            ArtifactKey inputsKey = rootKey.createChild();
            treeBuilder.addArtifact(executionKey, createGroupArtifact(inputsKey, "InputArtifacts"));
            
            ArtifactKey request1Key = inputsKey.createChild();
            treeBuilder.addArtifact(executionKey, createAgentModelArtifact(request1Key, "Request1", "req1-content"));
            
            ArtifactKey request2Key = inputsKey.createChild();
            treeBuilder.addArtifact(executionKey, createAgentModelArtifact(request2Key, "Request2", "req2-content"));
            
            // AgentExecution branch
            ArtifactKey executionBranchKey = rootKey.createChild();
            treeBuilder.addArtifact(executionKey, createGroupArtifact(executionBranchKey, "AgentExecution"));
            
            // Phase1
            ArtifactKey phase1Key = executionBranchKey.createChild();
            treeBuilder.addArtifact(executionKey, createGroupArtifact(phase1Key, "Phase1"));
            
            ArtifactKey prompt1Key = phase1Key.createChild();
            treeBuilder.addArtifact(executionKey, createRenderedPromptArtifact(prompt1Key, "Prompt 1", "prompt1-hash"));
            
            ArtifactKey toolCall1Key = phase1Key.createChild();
            treeBuilder.addArtifact(executionKey, createAgentModelArtifact(toolCall1Key, "ToolCall1", "tool1-content"));
            
            ArtifactKey result1Key = phase1Key.createChild();
            treeBuilder.addArtifact(executionKey, createAgentModelArtifact(result1Key, "Result1", "result1-content"));
            
            // Phase2
            ArtifactKey phase2Key = executionBranchKey.createChild();
            treeBuilder.addArtifact(executionKey, createGroupArtifact(phase2Key, "Phase2"));
            
            ArtifactKey prompt2Key = phase2Key.createChild();
            treeBuilder.addArtifact(executionKey, createRenderedPromptArtifact(prompt2Key, "Prompt 2", "prompt2-hash"));
            
            ArtifactKey result2Key = phase2Key.createChild();
            treeBuilder.addArtifact(executionKey, createAgentModelArtifact(result2Key, "Result2", "result2-content"));
            
            // Outputs branch
            ArtifactKey outputsKey = rootKey.createChild();
            treeBuilder.addArtifact(executionKey, createGroupArtifact(outputsKey, "Outputs"));
            
            ArtifactKey finalResultKey = outputsKey.createChild();
            treeBuilder.addArtifact(executionKey, createAgentModelArtifact(finalResultKey, "FinalResult", "final-content"));
            
            // Verify total count: 1 root + 13 nodes = 14
            Collection<Artifact> artifacts = treeBuilder.getExecutionArtifacts(executionKey);
            assertThat(artifacts).hasSize(14);
            
            // Verify all artifacts are retrievable at correct positions
            assertThat(treeBuilder.getArtifact(executionKey, inputsKey.value())).isPresent();
            assertThat(treeBuilder.getArtifact(executionKey, request1Key.value())).isPresent();
            assertThat(treeBuilder.getArtifact(executionKey, phase1Key.value())).isPresent();
            assertThat(treeBuilder.getArtifact(executionKey, toolCall1Key.value())).isPresent();
            assertThat(treeBuilder.getArtifact(executionKey, phase2Key.value())).isPresent();
            assertThat(treeBuilder.getArtifact(executionKey, finalResultKey.value())).isPresent();
            
            // Verify parent-child relationships via key structure
            assertThat(request1Key.isChildOf(inputsKey)).isTrue();
            assertThat(request2Key.isChildOf(inputsKey)).isTrue();
            assertThat(phase1Key.isChildOf(executionBranchKey)).isTrue();
            assertThat(phase2Key.isChildOf(executionBranchKey)).isTrue();
            assertThat(prompt1Key.isChildOf(phase1Key)).isTrue();
            assertThat(toolCall1Key.isChildOf(phase1Key)).isTrue();
            assertThat(result1Key.isChildOf(phase1Key)).isTrue();
            assertThat(finalResultKey.isChildOf(outputsKey)).isTrue();
            
            // Verify depths
            assertThat(rootKey.depth()).isEqualTo(1);
            assertThat(inputsKey.depth()).isEqualTo(2);
            assertThat(request1Key.depth()).isEqualTo(3);
            assertThat(phase1Key.depth()).isEqualTo(3);
            assertThat(prompt1Key.depth()).isEqualTo(4);
        }
        
        @Test
        @DisplayName("tree structure is preserved after persistence")
        void treeStructureIsPreservedAfterPersistence() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            // Build a 3-level tree
            ArtifactKey level1Key = rootKey.createChild();
            treeBuilder.addArtifact(executionKey, createGroupArtifact(level1Key, "Level1"));
            
            ArtifactKey level2aKey = level1Key.createChild();
            treeBuilder.addArtifact(executionKey, createAgentModelArtifact(level2aKey, "Level2a", "content-2a"));
            
            ArtifactKey level2bKey = level1Key.createChild();
            treeBuilder.addArtifact(executionKey, createAgentModelArtifact(level2bKey, "Level2b", "content-2b"));
            
            ArtifactKey level3Key = level2aKey.createChild();
            treeBuilder.addArtifact(executionKey, createAgentModelArtifact(level3Key, "Level3", "content-3"));
            
            // Persist and capture entities
            treeBuilder.persistExecution(executionKey);
            
            verify(artifactRepository, times(5)).save(entityListCaptor.capture());
            List<ArtifactEntity> saved = entityListCaptor.getAllValues();
            
            // Verify entities have correct parent keys
            assertThat(saved).hasSize(5);
            
            ArtifactEntity rootEntity = findEntityByKey(saved, rootKey.value());
            ArtifactEntity level1Entity = findEntityByKey(saved, level1Key.value());
            ArtifactEntity level2aEntity = findEntityByKey(saved, level2aKey.value());
            ArtifactEntity level2bEntity = findEntityByKey(saved, level2bKey.value());
            ArtifactEntity level3Entity = findEntityByKey(saved, level3Key.value());
            
            assertThat(rootEntity.getParentKey()).isNull();
            assertThat(level1Entity.getParentKey()).isEqualTo(rootKey.value());
            assertThat(level2aEntity.getParentKey()).isEqualTo(level1Key.value());
            assertThat(level2bEntity.getParentKey()).isEqualTo(level1Key.value());
            assertThat(level3Entity.getParentKey()).isEqualTo(level2aKey.value());
            
            // Verify depths
            assertThat(rootEntity.getDepth()).isEqualTo(1);
            assertThat(level1Entity.getDepth()).isEqualTo(2);
            assertThat(level2aEntity.getDepth()).isEqualTo(3);
            assertThat(level2bEntity.getDepth()).isEqualTo(3);
            assertThat(level3Entity.getDepth()).isEqualTo(4);
            
        }
        
        @Test
        @DisplayName("createChild generates unique keys with correct parent prefix")
        void createChildGeneratesUniqueKeysWithCorrectParentPrefix() {
            ArtifactKey parent = ArtifactKey.createRoot();
            
            // Create multiple children
            List<ArtifactKey> children = new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) {
                children.add(parent.createChild());
            }
            
            // All children should have unique keys
            long uniqueCount = children.stream().map(ArtifactKey::value).distinct().count();
            assertThat(uniqueCount).isEqualTo(10);
            
            // All children should have parent as prefix
            for (ArtifactKey child : children) {
                assertThat(child.value()).startsWith(parent.value() + "/");
                assertThat(child.isChildOf(parent)).isTrue();
                assertThat(child.parent()).isPresent();
                assertThat(child.parent().get()).isEqualTo(parent);
            }
        }
        
        @Test
        @DisplayName("getExecutionTree returns correct node structure")
        void getExecutionTreeReturnsCorrectNodeStructure() {
            treeBuilder.addArtifact(executionKey, rootArtifact);
            
            // Add children
            ArtifactKey child1Key = rootKey.createChild();
            treeBuilder.addArtifact(executionKey, createAgentModelArtifact(child1Key, "Child1", "c1"));
            
            ArtifactKey child2Key = rootKey.createChild();
            treeBuilder.addArtifact(executionKey, createAgentModelArtifact(child2Key, "Child2", "c2"));
            
            ArtifactKey grandchildKey = child1Key.createChild();
            treeBuilder.addArtifact(executionKey, createAgentModelArtifact(grandchildKey, "Grandchild", "gc"));
            
            // Get the tree and verify structure
            Optional<ArtifactNode> tree = treeBuilder.getExecutionTree(executionKey);
            assertThat(tree).isPresent();
            
            ArtifactNode root = tree.get();
            assertThat(root.getArtifact()).isEqualTo(rootArtifact);
            assertThat(root.getChildren()).hasSize(2);
            
            // Find child1 node
            ArtifactNode child1Node = root.findNode(child1Key);
            assertThat(child1Node).isNotNull();
            assertThat(child1Node.getChildren()).hasSize(1);
            
            // Find grandchild
            ArtifactNode grandchildNode = child1Node.findNode(grandchildKey);
            assertThat(grandchildNode).isNotNull();
            assertThat(grandchildNode.getChildren()).isEmpty();
            
            // Total size should be 4
            assertThat(root.size()).isEqualTo(4);
        }
        
        private Artifact.AgentModelArtifact createAgentModelArtifact(ArtifactKey key, String name, String content) {
            TestAgentModel model = new TestAgentModel(key, name, content, new ArrayList<>());
            return new Artifact.AgentModelArtifact(
                    new ArrayList<>(), model, Map.of("name", name), model.computeHash(Artifact.HashContext.defaultHashContext()));
        }
        
        private ArtifactEntity findEntityByKey(List<ArtifactEntity> entities, String key) {
            return entities.stream()
                    .filter(e -> e.getArtifactKey().equals(key))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Entity not found: " + key));
        }
    }
    
    // ========== Helper Methods ==========
    
    private Artifact.ExecutionArtifact createExecutionArtifact(ArtifactKey key) {
        return Artifact.ExecutionArtifact.builder()
                .hash(UUID.randomUUID().toString())
                .artifactKey(key)
                .workflowRunId("test-run-" + System.nanoTime())
                .startedAt(Instant.now())
                .status(Artifact.ExecutionStatus.RUNNING)
                .metadata(new java.util.HashMap<>())
                .children(new ArrayList<>())
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
                    public String artifactType() {
                        return "AgentModelArtifact";
                    }

                    @Override
                    public Artifact.AgentModel withContextId(ArtifactKey key) {
                        return this;
                    }

                    @Override
                    public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> c) {
                        return (T) this;
                    }
                })
                .hash(UUID.randomUUID().toString())
                .metadata(new java.util.HashMap<>())
                .children(new ArrayList<>())
                .build();
    }

    private Artifact.RenderedPromptArtifact createRenderedPromptArtifact(
            ArtifactKey key, String text, String hash) {
        return Artifact.RenderedPromptArtifact.builder()
                .artifactKey(key)
                .renderedText(text)
                .hash(hash)
                .metadata(new java.util.HashMap<>())
                .children(new ArrayList<>())
                .build();
    }
}
