package com.hayden.multiagentide.artifacts.repository;

import com.hayden.multiagentide.artifacts.ArtifactService;
import com.hayden.multiagentide.artifacts.ArtifactTreeBuilder;
import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ArtifactRepository.
 * 
 * Tests verify:
 * - All repository query methods work correctly
 * - Custom queries return expected results
 * - Entity fields are persisted and retrieved correctly
 * - Indexes are being used effectively
 */
@Slf4j
@SpringBootTest
@ActiveProfiles({"test", "testdocker"})
@Transactional
class ArtifactRepositoryTest {

    @Autowired
    private ArtifactRepository artifactRepository;
    
    @Autowired
    private ArtifactService artifactService;
    
    @Autowired
    private ArtifactTreeBuilder artifactTreeBuilder;
    
    @BeforeEach
    void setUp() {
        artifactRepository.deleteAll();
    }
    
    @AfterEach
    void tearDown() {
        artifactRepository.deleteAll();
    }
    
    // ========== Basic CRUD Operations ==========
    
    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudOperations {
        
        @Test
        @DisplayName("save and findById works correctly")
        void saveAndFindById() {
            ArtifactEntity entity = createTestEntity("ak:01HX5ZRXQM", null, "test-exec", "TestType");
            
            ArtifactEntity saved = artifactRepository.save(entity);
            assertThat(saved.getUuid()).isNotNull();
            
            Optional<ArtifactEntity> found = artifactRepository.findById(saved.getUuid());
            assertThat(found).isPresent();
            assertThat(found.get().getArtifactKey()).isEqualTo("ak:01HX5ZRXQM");
        }
        
        @Test
        @DisplayName("findByArtifactKey returns correct entity")
        void findByArtifactKey() {
            ArtifactEntity entity = createTestEntity("ak:01HX5ZRXQM", null, "test-exec", "TestType");
            artifactRepository.save(entity);
            
            Optional<ArtifactEntity> found = artifactRepository.findByArtifactKey("ak:01HX5ZRXQM");
            
            assertThat(found).isPresent();
            assertThat(found.get().getArtifactKey()).isEqualTo("ak:01HX5ZRXQM");
            assertThat(found.get().getExecutionKey()).isEqualTo("test-exec");
        }
        
        @Test
        @DisplayName("existsByArtifactKey returns true for existing key")
        void existsByArtifactKey() {
            ArtifactEntity entity = createTestEntity("ak:01HX5ZRXQM", null, "test-exec", "TestType");
            artifactRepository.save(entity);
            
            boolean exists = artifactRepository.existsByArtifactKey("ak:01HX5ZRXQM");
            assertThat(exists).isTrue();
            
            boolean notExists = artifactRepository.existsByArtifactKey("ak:NOTEXIST");
            assertThat(notExists).isFalse();
        }
        
        @Test
        @DisplayName("deleteAll removes all entities")
        void deleteAllWorks() {
            artifactRepository.save(createTestEntity("ak:01HX5ZRXQM", null, "exec1", "Type1"));
            artifactRepository.save(createTestEntity("ak:01HX5ZRXQN", null, "exec1", "Type2"));
            artifactRepository.save(createTestEntity("ak:01HX5ZRXQO", null, "exec2", "Type1"));
            
            assertThat(artifactRepository.count()).isEqualTo(3);
            
            artifactRepository.deleteAll();
            
            assertThat(artifactRepository.count()).isEqualTo(0);
        }
    }
    
    // ========== Entity Field Tests ==========
    
    @Nested
    @DisplayName("Entity Field Persistence")
    class EntityFieldPersistence {
        
        @Test
        @DisplayName("all entity fields persist correctly")
        void allFieldsPersist() {
            ArtifactEntity ref1 = ArtifactEntity.builder()
                    .artifactKey("ref1")
                    .parentKey("ak:01HX5ZRXQL")
                    .executionKey("exec-123")
                    .artifactType("RenderedPrompt")
                    .contentHash("hash-abc1234")
                    .contentJson("{\"text\": \"test\"}")
                    .depth(4)
                    .templateStaticId("tpl.test.template")
                    .shared(true)
                    .schemaVersion("1.0.0")
                    .build();
            ArtifactEntity ref2 = ArtifactEntity.builder()
                    .artifactKey("ref2")
                    .parentKey("ak:01HX5ZRXQL")
                    .executionKey("exec-123")
                    .artifactType("RenderedPrompt")
                    .contentHash("hash-abc12345")
                    .contentJson("{\"text\": \"test\"}")
                    .depth(4)
                    .templateStaticId("tpl.test.template")
                    .shared(true)
                    .schemaVersion("1.0.0")
                    .build();

            artifactRepository.saveAll(List.of(ref1, ref2));
            artifactRepository.flush();

            ArtifactEntity entity = ArtifactEntity.builder()
                    .artifactKey("ak:01HX5ZRXQM")
                    .parentKey("ak:01HX5ZRXQL")
                    .executionKey("exec-123")
                    .artifactType("RenderedPrompt")
                    .contentHash("hash-abc123")
                    .contentJson("{\"text\": \"test\"}")
                    .depth(3)
                    .templateStaticId("tpl.test.template")
                    .shared(true)
                    .schemaVersion("1.0.0")
                    .childIds(List.of("child1", "child2"))
                    .artifactKeyRefs(List.of("ref1", "ref2"))
                    .build();
            
            ArtifactEntity saved = artifactRepository.save(entity);
            artifactRepository.flush();
            
            Optional<ArtifactEntity> found = artifactRepository.findById(saved.getUuid());
            assertThat(found).isPresent();
            
            ArtifactEntity retrieved = found.get();
            assertThat(retrieved.getArtifactKey()).isEqualTo("ak:01HX5ZRXQM");
            assertThat(retrieved.getParentKey()).isEqualTo("ak:01HX5ZRXQL");
            assertThat(retrieved.getExecutionKey()).isEqualTo("exec-123");
            assertThat(retrieved.getArtifactType()).isEqualTo("RenderedPrompt");
            assertThat(retrieved.getContentHash()).isEqualTo("hash-abc123");
            assertThat(retrieved.getContentJson()).isEqualTo("{\"text\": \"test\"}");
            assertThat(retrieved.getDepth()).isEqualTo(3);
            assertThat(retrieved.getTemplateStaticId()).isEqualTo("tpl.test.template");
            assertThat(retrieved.getShared()).isTrue();
            assertThat(retrieved.getSchemaVersion()).isEqualTo("1.0.0");
            assertThat(retrieved.getChildIds()).containsExactly("child1", "child2");
            assertThat(retrieved.getArtifactKeyRefs()).containsExactly("ref1", "ref2");
        }
        
        @Test
        @DisplayName("nullable fields can be null")
        void nullableFieldsCanBeNull() {
            ArtifactEntity entity = ArtifactEntity.builder()
                    .artifactKey("ak:01HX5ZRXQM")
                    .parentKey(null) // Root has no parent
                    .executionKey("exec-123")
                    .artifactType("Execution")
                    .contentHash(null) // Execution may not have hash
                    .contentJson("{}")
                    .depth(1)
                    .templateStaticId(null) // Not a template
                    .shared(false)
                    .schemaVersion("1.0.0")
                    .childIds(new ArrayList<>())
                    .artifactKeyRefs(new ArrayList<>())
                    .build();
            
            ArtifactEntity saved = artifactRepository.save(entity);
            Optional<ArtifactEntity> found = artifactRepository.findById(saved.getUuid());
            
            assertThat(found).isPresent();
            assertThat(found.get().getParentKey()).isNull();
            assertThat(found.get().getContentHash()).isNull();
            assertThat(found.get().getTemplateStaticId()).isNull();
        }
        
        @Test
        @DisplayName("childIds collection persists and retrieves correctly")
        void childIdsPersist() {
            List<String> childIds = List.of("child-1", "child-2", "child-3", "child-4");
            
            ArtifactEntity entity = createTestEntity("ak:01HX5ZRXQM", null, "exec-123", "Group");
            entity.setChildIds(childIds);
            
            ArtifactEntity saved = artifactRepository.save(entity);
            artifactRepository.flush();
            
            Optional<ArtifactEntity> found = artifactRepository.findById(saved.getUuid());
            assertThat(found).isPresent();
            assertThat(found.get().getChildIds()).hasSize(4);
            assertThat(found.get().getChildIds()).containsExactlyElementsOf(childIds);
        }
        
        @Test
        @DisplayName("artifactKeyRefs collection persists correctly")
        void artifactKeyRefsPersist() {
            // Create valid ULID format artifact keys
            ArtifactKey key1 = ArtifactKey.createRoot();
            ArtifactKey key2 = ArtifactKey.createRoot();
            ArtifactKey mainKey = ArtifactKey.createRoot();
            
            ArtifactEntity entity = createTestEntity(mainKey.value(), null, "exec-123", "Ref");

            ArtifactEntity ref1 = createTestEntity(key1.value(), null, "exec-123", "Ref");
            ArtifactEntity ref2 = createTestEntity(key2.value(), null, "exec-123", "Ref");

            artifactRepository.saveAll(List.of(ref1, ref2));
            artifactRepository.flush();

            entity.setArtifactKeyRefs(new ArrayList<>());
            
            // Add refs using the addRef method
            entity.addRef(key1);
            entity.addRef(key2);
            
            ArtifactEntity saved = artifactRepository.save(entity);
            artifactRepository.flush();
            
            Optional<ArtifactEntity> found = artifactRepository.findById(saved.getUuid());
            assertThat(found).isPresent();
            assertThat(found.get().getArtifactKeyRefs()).hasSize(2);
            assertThat(found.get().getArtifactKeyRefs()).contains(key1.value(), key2.value());
        }
        
        @Test
        @DisplayName("empty collections persist as empty")
        void emptyCollectionsPersist() {
            ArtifactEntity entity = createTestEntity("ak:01HX5ZRXQM", null, "exec-123", "Type");
            entity.setChildIds(new ArrayList<>());
            entity.setArtifactKeyRefs(new ArrayList<>());
            
            ArtifactEntity saved = artifactRepository.save(entity);
            artifactRepository.flush();
            
            Optional<ArtifactEntity> found = artifactRepository.findById(saved.getUuid());
            assertThat(found).isPresent();
            assertThat(found.get().getChildIds()).isEmpty();
            assertThat(found.get().getArtifactKeyRefs()).isEmpty();
        }
        
        @Test
        @DisplayName("large JSON content persists correctly")
        void largeJsonContentPersists() {
            // Create a large JSON payload (approximately 100KB)
            StringBuilder largeJson = new StringBuilder("{\"data\": [");
            for (int i = 0; i < 1000; i++) {
                if (i > 0) largeJson.append(",");
                largeJson.append("{\"id\": ").append(i)
                        .append(", \"text\": \"Lorem ipsum dolor sit amet, consectetur adipiscing elit.\"}")
                        ;
            }
            largeJson.append("]}");
            
            ArtifactEntity entity = createTestEntity("ak:01HX5ZRXQM", null, "exec-123", "LargeData");
            entity.setContentJson(largeJson.toString());
            
            ArtifactEntity saved = artifactRepository.save(entity);
            artifactRepository.flush();
            
            Optional<ArtifactEntity> found = artifactRepository.findById(saved.getUuid());
            assertThat(found).isPresent();
            assertThat(found.get().getContentJson()).hasSize(largeJson.length());
            assertThat(found.get().getContentJson()).startsWith("{\"data\": [");
        }
    }
    
    // ========== Query Method Tests ==========
    
    @Nested
    @DisplayName("Repository Query Methods")
    class RepositoryQueryMethods {
        
        @Test
        @DisplayName("findByExecutionKeyOrderByArtifactKey returns all artifacts in order")
        void findByExecutionKeyOrdered() {
            // Create artifacts with different keys in the same execution
            artifactRepository.save(createTestEntity("ak:01HX5ZRXQO", null, "exec-123", "Type3"));
            artifactRepository.save(createTestEntity("ak:01HX5ZRXQM", null, "exec-123", "Type1"));
            artifactRepository.save(createTestEntity("ak:01HX5ZRXQN", null, "exec-123", "Type2"));
            artifactRepository.save(createTestEntity("ak:01HX5ZRXQP", null, "exec-456", "Type4"));
            
            List<ArtifactEntity> results = artifactRepository.findByExecutionKeyOrderByArtifactKey("exec-123");
            
            assertThat(results).hasSize(3);
            assertThat(results.get(0).getArtifactKey()).isEqualTo("ak:01HX5ZRXQM");
            assertThat(results.get(1).getArtifactKey()).isEqualTo("ak:01HX5ZRXQN");
            assertThat(results.get(2).getArtifactKey()).isEqualTo("ak:01HX5ZRXQO");
        }
        
        @Test
        @DisplayName("findByKeyPrefix returns all artifacts under prefix")
        void findByKeyPrefix() {
            // Create a hierarchical structure
            String rootKey = "ak:01HX5ZRXQM";
            String child1Key = rootKey + "/01HX5ZRXQN";
            String child2Key = rootKey + "/01HX5ZRXQO";
            String grandchildKey = child1Key + "/01HX5ZRXQP";
            String otherRootKey = "ak:01HX5ZRXQQ";
            
            artifactRepository.save(createTestEntity(rootKey, null, "exec-123", "Root"));
            artifactRepository.save(createTestEntity(child1Key, rootKey, "exec-123", "Child1"));
            artifactRepository.save(createTestEntity(child2Key, rootKey, "exec-123", "Child2"));
            artifactRepository.save(createTestEntity(grandchildKey, child1Key, "exec-123", "Grandchild"));
            artifactRepository.save(createTestEntity(otherRootKey, null, "exec-123", "OtherRoot"));
            
            List<ArtifactEntity> results = artifactRepository.findByKeyPrefix(rootKey);
            
            assertThat(results).hasSize(4); // root + 2 children + 1 grandchild
            assertThat(results.stream().map(ArtifactEntity::getArtifactKey))
                    .containsExactlyInAnyOrder(rootKey, child1Key, child2Key, grandchildKey);
        }
        
        @Test
        @DisplayName("findByExecutionKeyAndArtifactTypeOrderByArtifactKey filters by type")
        void findByExecutionKeyAndArtifactType() {
            artifactRepository.save(createTestEntity("ak:01HX5ZRXQM", null, "exec-123", "RenderedPrompt"));
            artifactRepository.save(createTestEntity("ak:01HX5ZRXQN", null, "exec-123", "ToolCall"));
            artifactRepository.save(createTestEntity("ak:01HX5ZRXQO", null, "exec-123", "RenderedPrompt"));
            artifactRepository.save(createTestEntity("ak:01HX5ZRXQP", null, "exec-123", "AgentResult"));
            
            List<ArtifactEntity> prompts = artifactRepository.findByExecutionKeyAndArtifactTypeOrderByArtifactKey(
                    "exec-123", "RenderedPrompt");
            
            assertThat(prompts).hasSize(2);
            assertThat(prompts.stream().allMatch(e -> e.getArtifactType().equals("RenderedPrompt"))).isTrue();
        }
        
        @Test
        @DisplayName("findByContentHash returns entities with matching hash")
        void findByContentHash() {
            String hash = "abc123hash";
            
            // Create valid ULID format artifact keys
            ArtifactKey key1 = ArtifactKey.createRoot();
            ArtifactKey key2 = ArtifactKey.createRoot();
            
            artifactRepository.save(createTestEntityWithHash(key1.value(), "exec-123", hash));
            artifactRepository.save(createTestEntityWithHash(key2.value(), "exec-123", "different-hash"));
            
            Optional<ArtifactEntity> found = artifactRepository.findByContentHash(hash);
            
            assertThat(found).isPresent();
            assertThat(found.get().getContentHash()).isEqualTo(hash);
        }

        @Test
        @DisplayName("findByTemplateStaticIdAndSharedTrueOrderByCreatedTimeDesc returns versions ordered")
        void findTemplateVersionsOrdered() throws InterruptedException {
            String staticId = "tpl.agent.system";
            
            ArtifactEntity v1 = createTestEntity("ak:01HX5ZRXQM", null, "exec-123", "PromptTemplateVersion");
            v1.setTemplateStaticId(staticId);
            v1.setShared(true);
            v1.setContentHash("hash-v1");
            Thread.sleep(10); // Ensure different timestamps
            artifactRepository.save(v1);
            
            ArtifactEntity v2 = createTestEntity("ak:01HX5ZRXQN", null, "exec-123", "PromptTemplateVersion");
            v2.setTemplateStaticId(staticId);
            v2.setShared(true);
            v2.setContentHash("hash-v2");
            Thread.sleep(10);
            artifactRepository.save(v2);
            
            ArtifactEntity v3 = createTestEntity("ak:01HX5ZRXQO", null, "exec-123", "PromptTemplateVersion");
            v3.setTemplateStaticId(staticId);
            v3.setShared(true);
            v3.setContentHash("hash-v3");
            artifactRepository.save(v3);
            
            List<ArtifactEntity> versions = artifactRepository.findByTemplateStaticIdAndSharedTrueOrderByCreatedTimeDesc(staticId);
            
            assertThat(versions).hasSize(3);
            // Most recent should be first (v3)
            assertThat(versions.getFirst().getContentHash()).isEqualTo("hash-v3");
        }
        
        @Test
        @DisplayName("findTemplatesByStaticIdPrefix finds template family")
        void findTemplatesByPrefix() {
            // Create templates with hierarchical static IDs
            createSharedTemplate("tpl.agent.orchestrator.system", "hash1");
            createSharedTemplate("tpl.agent.orchestrator.user", "hash2");
            createSharedTemplate("tpl.agent.discovery.system", "hash3");
            createSharedTemplate("tpl.workflow.planning", "hash4");
            
            List<ArtifactEntity> orchestratorTemplates = 
                    artifactRepository.findTemplatesByStaticIdPrefix("tpl.agent.orchestrator");
            
            assertThat(orchestratorTemplates).hasSize(2);
            assertThat(orchestratorTemplates.stream()
                    .allMatch(e -> e.getTemplateStaticId().startsWith("tpl.agent.orchestrator")))
                    .isTrue();
        }
        
        @Test
        @DisplayName("findExecutionKeysBetween returns executions in time range")
        void findExecutionKeysBetween() throws InterruptedException {
            Instant start = Instant.now();
            Thread.sleep(50);
            
            createExecutionArtifact("exec-1");
            Thread.sleep(50);
            
            Instant middle = Instant.now();
            Thread.sleep(50);
            
            createExecutionArtifact("exec-2");
            Thread.sleep(50);
            
            Instant end = Instant.now();
            
            createExecutionArtifact("exec-3");
            
            List<String> executions = artifactRepository.findExecutionKeysBetween(middle, end);
            
            // Should find exec-2 (created between middle and end)
            assertThat(executions).contains("exec-2");
            assertThat(executions).doesNotContain("exec-1"); // Too early
        }
        
        @Test
        @DisplayName("findAllExecutions returns all execution roots")
        void findAllExecutions() {
            createExecutionArtifact("exec-1");
            createExecutionArtifact("exec-2");
            createExecutionArtifact("exec-3");
            
            // Create non-execution artifacts
            artifactRepository.save(createTestEntity("ak:01HX5ZRXQP", null, "exec-1", "ToolCall"));
            
            List<ArtifactEntity> executions = artifactRepository.findAllExecutions();
            
            assertThat(executions).hasSize(3);
            assertThat(executions.stream().allMatch(e -> e.getArtifactType().equals("Execution"))).isTrue();
            assertThat(executions.stream().allMatch(e -> e.getDepth() == 1)).isTrue();
        }
        
        @Test
        @DisplayName("findAllTemplateStaticIds returns distinct template IDs")
        void findAllTemplateStaticIds() {
            createSharedTemplate("tpl.agent.system", "hash1");
            createSharedTemplate("tpl.agent.system", "hash2"); // Same ID, different hash
            createSharedTemplate("tpl.agent.user", "hash3");
            createSharedTemplate("tpl.workflow.planning", "hash4");
            
            // Create non-shared template (should not be included)
            ArtifactEntity nonShared = createTestEntity("ak:01HX5ZRXQS", null, "exec-123", "Template");
            nonShared.setTemplateStaticId("tpl.nonshared");
            nonShared.setShared(false);
            artifactRepository.save(nonShared);
            
            List<String> staticIds = artifactRepository.findAllTemplateStaticIds();
            
            assertThat(staticIds).hasSize(3);
            assertThat(staticIds).containsExactlyInAnyOrder(
                    "tpl.agent.system",
                    "tpl.agent.user",
                    "tpl.workflow.planning"
            );
        }
        
        @Test
        @DisplayName("deleteByExecutionKey removes all artifacts in execution")
        void deleteByExecutionKey() {
            // Create artifacts in two executions
            artifactRepository.save(createTestEntity("ak:01HX5ZRXQM", null, "exec-123", "Type1"));
            artifactRepository.save(createTestEntity("ak:01HX5ZRXQN", null, "exec-123", "Type2"));
            artifactRepository.save(createTestEntity("ak:01HX5ZRXQO", null, "exec-456", "Type3"));
            
            assertThat(artifactRepository.count()).isEqualTo(3);
            
            artifactRepository.deleteByExecutionKey("exec-123");
            artifactRepository.flush();
            
            assertThat(artifactRepository.count()).isEqualTo(1);
            assertThat(artifactRepository.findByExecutionKeyOrderByArtifactKey("exec-123")).isEmpty();
            assertThat(artifactRepository.findByExecutionKeyOrderByArtifactKey("exec-456")).hasSize(1);
        }
    }
    
    // ========== Complex Integration Tests ==========
    
    @Nested
    @DisplayName("Complex Integration Scenarios")
    class ComplexIntegrationScenarios {
        
        @Test
        @DisplayName("full artifact tree persists and queries correctly")
        void fullArtifactTreeIntegration() {
            ArtifactKey rootKey = ArtifactKey.createRoot();
            String executionKey = rootKey.value();
            
            // Create execution tree
            Artifact.ExecutionArtifact execution = Artifact.ExecutionArtifact.builder()
                    .artifactKey(rootKey)
                    .hash(UUID.randomUUID().toString())
                    .workflowRunId("workflow-123")
                    .startedAt(Instant.now())
                    .status(Artifact.ExecutionStatus.RUNNING)
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();
            
            artifactTreeBuilder.addArtifact(executionKey, execution);
            
            // Add children
            ArtifactKey child1Key = rootKey.createChild();
            Artifact.RenderedPromptArtifact prompt = Artifact.RenderedPromptArtifact.builder()
                    .artifactKey(child1Key)
                    .renderedText("Test prompt")
                    .promptName("test")
                    .hash("hash1")
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();
            artifactTreeBuilder.addArtifact(executionKey, prompt);
            
            ArtifactKey child2Key = rootKey.createChild();
            Artifact.ToolCallArtifact toolCall = Artifact.ToolCallArtifact.builder()
                    .artifactKey(child2Key)
                    .toolCallId("call-123")
                    .toolName("tool")
                    .inputJson("{}")
                    .inputHash("hash2")
                    .outputJson("{}")
                    .outputHash("hash3")
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();
            artifactTreeBuilder.addArtifact(executionKey, toolCall);
            
            // Persist
            artifactTreeBuilder.persistExecution(executionKey);
            
            // Query and verify
            List<ArtifactEntity> allInExecution = 
                    artifactRepository.findByExecutionKeyOrderByArtifactKey(executionKey);
            
            assertThat(allInExecution).hasSize(3);
            
            // Verify tree structure
            Optional<ArtifactEntity> rootEntity = artifactRepository.findByArtifactKey(rootKey.value());
            assertThat(rootEntity).isPresent();
            assertThat(rootEntity.get().getParentKey()).isNull();
            assertThat(rootEntity.get().getDepth()).isEqualTo(1);
            
            Optional<ArtifactEntity> childEntity = artifactRepository.findByArtifactKey(child1Key.value());
            assertThat(childEntity).isPresent();
            assertThat(childEntity.get().getParentKey()).isEqualTo(rootKey.value());
            assertThat(childEntity.get().getDepth()).isEqualTo(2);
        }
        

    }
    
    // ========== Helper Methods ==========
    
    private ArtifactEntity createTestEntity(String artifactKey, String parentKey, 
                                            String executionKey, String artifactType) {
        return ArtifactEntity.builder()
                .artifactKey(artifactKey)
                .parentKey(parentKey)
                .executionKey(executionKey)
                .artifactType(artifactType)
                .contentHash(UUID.randomUUID().toString())
                .contentJson("{}")
                .depth(parentKey == null ? 1 : 2)
                .shared(false)
                .schemaVersion("1.0.0")
                .childIds(new ArrayList<>())
                .artifactKeyRefs(new ArrayList<>())
                .build();
    }
    
    private ArtifactEntity createTestEntityWithHash(String artifactKey, String executionKey, String hash) {
        return ArtifactEntity.builder()
                .artifactKey(artifactKey)
                .executionKey(executionKey)
                .artifactType("TestType")
                .contentHash(hash)
                .contentJson("{}")
                .depth(1)
                .shared(false)
                .schemaVersion("1.0.0")
                .childIds(new ArrayList<>())
                .artifactKeyRefs(new ArrayList<>())
                .build();
    }
    
    private void createSharedTemplate(String staticId, String hash) {
        ArtifactEntity template = createTestEntity(
                "ak:template-" + UUID.randomUUID().toString().substring(0, 8),
                null,
                "exec-templates",
                "PromptTemplateVersion"
        );
        template.setTemplateStaticId(staticId);
        template.setContentHash(hash);
        template.setShared(true);
        artifactRepository.save(template);
    }
    
    private void createExecutionArtifact(String executionKey) {
        ArtifactEntity execution = createTestEntity(executionKey, null, executionKey, "Execution");
        execution.setDepth(1);
        artifactRepository.save(execution);
    }
}
