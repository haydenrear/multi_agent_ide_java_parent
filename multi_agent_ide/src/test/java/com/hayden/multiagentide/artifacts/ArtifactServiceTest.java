package com.hayden.multiagentide.artifacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import com.hayden.multiagentide.config.SerdesConfiguration;
import com.hayden.multiagentidelib.artifact.PromptTemplateVersion;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

import static com.hayden.multiagentide.artifacts.ArtifactSerializationTest.createExecutionArtifact;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ArtifactService.
 * <p>
 * Tests verify:
 * - Entity conversion (toEntity)
 * - Serialization/deserialization of artifacts
 * - Duplicate decoration
 * - Persistence with deduplication
 */
@Slf4j
@SpringBootTest
@ActiveProfiles({"test", "testdocker"})
@Transactional
class ArtifactServiceTest {

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private ArtifactService artifactService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        artifactRepository.deleteAll();

        SerdesConfiguration s = new SerdesConfiguration();

        var b = new Jackson2ObjectMapperBuilder();
        s.artifactAndAgentModelMixIn().customize(b);

        this.objectMapper = b.build();
    }

    @AfterEach
    void tearDown() {
        artifactRepository.deleteAll();
    }

    // ========== toEntity Tests ==========

    @Test
    @DisplayName("toEntity converts ExecutionArtifact with all fields correctly")
    void toEntity_executionArtifact_allFieldsPopulated() {
        // Given
        String executionKey = "exec-123";
        ArtifactKey artifactKey = ArtifactKey.createRoot();
        String hash = "abc123hash";
        
        Artifact.ExecutionArtifact artifact = Artifact.ExecutionArtifact.builder()
                .artifactKey(artifactKey)
                .workflowRunId("workflow-run-1")
                .startedAt(Instant.now())
                .status(Artifact.ExecutionStatus.RUNNING)
                .metadata(Map.of("key1", "value1"))
                .children(new ArrayList<>())
                .hash(hash)
                .build();

        // When
        ArtifactEntity entity = artifactService.toEntity(executionKey, artifact);

        // Then
        assertThat(entity.getArtifactKey()).isEqualTo(artifactKey.value());
        assertThat(entity.getExecutionKey()).isEqualTo(executionKey);
        assertThat(entity.getArtifactType()).isEqualTo(Artifact.ExecutionArtifact.class.getSimpleName());
        assertThat(entity.getContentHash()).isEqualTo(hash);
        assertThat(entity.getDepth()).isEqualTo(artifactKey.depth());
        assertThat(entity.getShared()).isFalse();
        assertThat(entity.getParentKey()).isNull();
        assertThat(entity.getContentJson()).isNotNull();
        assertThat(entity.getContentJson()).contains("workflow-run-1");
    }

    @Test
    @DisplayName("toEntity converts child artifact with parent key")
    void toEntity_childArtifact_hasParentKey() {
        // Given
        String executionKey = "exec-456";
        ArtifactKey parentKey = ArtifactKey.createRoot();
        ArtifactKey childKey = parentKey.createChild();
        
        Artifact.ToolCallArtifact artifact = Artifact.ToolCallArtifact.builder()
                .artifactKey(childKey)
                .toolCallId("tool-call-1")
                .toolName("testTool")
                .inputJson("{}")
                .inputHash("inputhash")
                .metadata(new HashMap<>())
                .children(new ArrayList<>())
                .build();

        // When
        ArtifactEntity entity = artifactService.toEntity(executionKey, artifact);

        // Then
        assertThat(entity.getArtifactKey()).isEqualTo(childKey.value());
        assertThat(entity.getParentKey()).isEqualTo(parentKey.value());
        assertThat(entity.getDepth()).isEqualTo(2);
    }

    @Test
    @DisplayName("toEntity converts PromptContributionTemplate (Templated) with templateStaticId")
    void toEntity_templatedArtifact_hasTemplateStaticId() {
        // Given
        String executionKey = "exec-789";
        ArtifactKey artifactKey = ArtifactKey.createRoot();
        
        Artifact.PromptContributionTemplate artifact = Artifact.PromptContributionTemplate.builder()
                .templateArtifactKey(artifactKey)
                .contributorName("test-contributor")
                .priority(10)
                .agentTypes(List.of("agent1"))
                .templateText("Hello {{name}}")
                .orderIndex(1)
                .hash("templatehash")
                .metadata(new HashMap<>())
                .children(new ArrayList<>())
                .build();

        // When
        ArtifactEntity entity = artifactService.toEntity(executionKey, artifact);

        // Then
        assertThat(entity.getTemplateStaticId()).isEqualTo("test-contributor");
        assertThat(entity.getArtifactType()).isEqualTo(Artifact.PromptContributionTemplate.class.getSimpleName());
    }

    @Test
    @DisplayName("toEntity includes child IDs in entity")
    void toEntity_withChildren_includesChildIds() {
        // Given
        String executionKey = "exec-children";
        ArtifactKey parentKey = ArtifactKey.createRoot();
        ArtifactKey childKey1 = parentKey.createChild();
        ArtifactKey childKey2 = parentKey.createChild();
        
        Artifact.ToolCallArtifact child1 = Artifact.ToolCallArtifact.builder()
                .artifactKey(childKey1)
                .toolCallId("child-1")
                .toolName("tool1")
                .metadata(new HashMap<>())
                .children(new ArrayList<>())
                .build();
        
        Artifact.ToolCallArtifact child2 = Artifact.ToolCallArtifact.builder()
                .artifactKey(childKey2)
                .toolCallId("child-2")
                .toolName("tool2")
                .metadata(new HashMap<>())
                .children(new ArrayList<>())
                .build();
        
        Artifact.ExecutionArtifact parent = Artifact.ExecutionArtifact.builder()
                .artifactKey(parentKey)
                .workflowRunId("workflow-1")
                .status(Artifact.ExecutionStatus.RUNNING)
                .metadata(new HashMap<>())
                .children(new ArrayList<>(List.of(child1, child2)))
                .build();

        // When
        ArtifactEntity entity = artifactService.toEntity(executionKey, parent);

        // Then
        assertThat(entity.getChildIds()).hasSize(2);
        assertThat(entity.getChildIds()).contains(childKey1.value(), childKey2.value());
    }

    @Test
    @DisplayName("toEntity handles artifact with no content hash")
    void toEntity_noContentHash_setsNullHash() {
        // Given
        String executionKey = "exec-nohash";
        ArtifactKey artifactKey = ArtifactKey.createRoot();
        
        Artifact.ExecutionArtifact artifact = Artifact.ExecutionArtifact.builder()
                .artifactKey(artifactKey)
                .workflowRunId("workflow-1")
                .status(Artifact.ExecutionStatus.RUNNING)
                .metadata(new HashMap<>())
                .children(new ArrayList<>())
                .hash(null)
                .build();

        // When
        ArtifactEntity entity = artifactService.toEntity(executionKey, artifact);

        // Then
        assertThat(entity.getContentHash()).isNull();
    }

    // ========== deserializeArtifact Tests ==========

    @Test
    @DisplayName("deserializeArtifact returns empty for null entity")
    void deserializeArtifact_nullEntity_returnsEmpty() {
        // When
        Optional<Artifact> result = artifactService.deserializeArtifact(null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deserializeArtifact returns empty for entity with null contentJson")
    void deserializeArtifact_nullContentJson_returnsEmpty() {
        // Given
        ArtifactEntity entity = ArtifactEntity.builder()
                .artifactKey("ak:TEST123456789012345678901")
                .executionKey("exec-1")
                .artifactType("Execution")
                .depth(1)
                .contentJson(null)
                .build();

        // When
        Optional<Artifact> result = artifactService.deserializeArtifact(entity);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deserializeArtifact successfully deserializes ExecutionArtifact")
    void deserializeArtifact_validExecutionArtifact_succeeds() throws Exception {
        // Given
        ArtifactKey artifactKey = ArtifactKey.createRoot();
        Artifact.ExecutionArtifact original = Artifact.ExecutionArtifact.builder()
                .artifactKey(artifactKey)
                .workflowRunId("workflow-deserialize-test")
                .startedAt(Instant.now())
                .status(Artifact.ExecutionStatus.COMPLETED)
                .metadata(Map.of("test", "value"))
                .children(new ArrayList<>())
                .hash("deserializehash")
                .build();
        
        String json = objectMapper.writeValueAsString(original);
        
        ArtifactEntity entity = ArtifactEntity.builder()
                .artifactKey(artifactKey.value())
                .executionKey("exec-deserialize")
                .artifactType("Execution")
                .depth(1)
                .contentJson(json)
                .build();

        // When
        Optional<Artifact> result = artifactService.deserializeArtifact(entity);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(Artifact.ExecutionArtifact.class);
        Artifact.ExecutionArtifact deserialized = (Artifact.ExecutionArtifact) result.get();
        assertThat(deserialized.workflowRunId()).isEqualTo("workflow-deserialize-test");
        assertThat(deserialized.status()).isEqualTo(Artifact.ExecutionStatus.COMPLETED);
    }

    @Test
    @DisplayName("deserializeArtifact returns empty for invalid JSON")
    void deserializeArtifact_invalidJson_returnsEmpty() {
        // Given
        ArtifactEntity entity = ArtifactEntity.builder()
                .artifactKey("ak:TEST123456789012345678901")
                .executionKey("exec-1")
                .artifactType("Execution")
                .depth(1)
                .contentJson("{ invalid json }")
                .build();

        // When
        Optional<Artifact> result = artifactService.deserializeArtifact(entity);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deserializeArtifact deserializes ToolCallArtifact")
    void deserializeArtifact_toolCallArtifact_succeeds() throws Exception {
        // Given
        ArtifactKey artifactKey = ArtifactKey.createRoot();
        Artifact.ToolCallArtifact original = Artifact.ToolCallArtifact.builder()
                .artifactKey(artifactKey)
                .toolCallId("tool-123")
                .toolName("myTool")
                .inputJson("{\"arg\": \"value\"}")
                .inputHash("inputhash123")
                .outputJson("{\"result\": \"success\"}")
                .outputHash("outputhash123")
                .metadata(new HashMap<>())
                .children(new ArrayList<>())
                .build();
        
        String json = objectMapper.writeValueAsString(original);
        
        ArtifactEntity entity = ArtifactEntity.builder()
                .artifactKey(artifactKey.value())
                .executionKey("exec-tool")
                .artifactType("ToolCall")
                .depth(1)
                .contentJson(json)
                .build();

        // When
        Optional<Artifact> result = artifactService.deserializeArtifact(entity);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(Artifact.ToolCallArtifact.class);
        Artifact.ToolCallArtifact deserialized = (Artifact.ToolCallArtifact) result.get();
        assertThat(deserialized.toolName()).isEqualTo("myTool");
        assertThat(deserialized.toolCallId()).isEqualTo("tool-123");
    }

    // ========== decorateDuplicate Tests ==========

    @Test
    @DisplayName("decorateDuplicate returns empty for null contentHash")
    void decorateDuplicate_nullHash_returnsEmpty() {
        // Given
        ArtifactKey artifactKey = ArtifactKey.createRoot();

        // When
        Optional<Artifact> result = artifactService.decorateDuplicate(null, artifactKey);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("decorateDuplicate returns empty for empty contentHash")
    void decorateDuplicate_emptyHash_returnsEmpty() {
        // Given
        ArtifactKey artifactKey = ArtifactKey.createRoot();

        // When
        Optional<Artifact> result = artifactService.decorateDuplicate("", artifactKey);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("decorateDuplicate returns empty when no matching hash in repository")
    void decorateDuplicate_noMatchingHash_returnsEmpty() {
        // Given
        ArtifactKey artifactKey = ArtifactKey.createRoot();

        // When
        Optional<Artifact> result = artifactService.decorateDuplicate("nonexistenthash", artifactKey);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("decorateDuplicate returns ArtifactDbRef for existing artifact")
    void decorateDuplicate_existingArtifact_returnsDbRef() throws Exception {
        // Given
        ArtifactKey originalKey = ArtifactKey.createRoot();
        String contentHash = "uniquehash123";
        
        Artifact.ExecutionArtifact original = Artifact.ExecutionArtifact.builder()
                .artifactKey(originalKey)
                .workflowRunId("workflow-original")
                .status(Artifact.ExecutionStatus.RUNNING)
                .metadata(new HashMap<>())
                .children(new ArrayList<>())
                .hash(contentHash)
                .build();
        
        // Save the original to repository
        ArtifactEntity originalEntity = artifactService.toEntity("exec-original", original);
        artifactRepository.save(originalEntity);
        
        ArtifactKey duplicateKey = ArtifactKey.createRoot();

        // When
        Optional<Artifact> result = artifactService.decorateDuplicate(contentHash, duplicateKey);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(Artifact.ArtifactDbRef.class);
        Artifact.ArtifactDbRef dbRef = (Artifact.ArtifactDbRef) result.get();
        assertThat(dbRef.artifactKey()).isEqualTo(duplicateKey);
        assertThat(dbRef.ref()).isNotNull();
    }

    @Test
    @DisplayName("decorateDuplicate returns TemplateDbRef for existing Templated artifact")
    void decorateDuplicate_existingTemplated_returnsTemplateDbRef() throws Exception {
        // Given
        ArtifactKey originalKey = ArtifactKey.createRoot();
        String contentHash = "templatehash456";
        
        Artifact.PromptContributionTemplate original = Artifact.PromptContributionTemplate.builder()
                .templateArtifactKey(originalKey)
                .contributorName("test-template")
                .priority(5)
                .agentTypes(List.of("agent"))
                .templateText("Template content")
                .orderIndex(0)
                .hash(contentHash)
                .metadata(new HashMap<>())
                .children(new ArrayList<>())
                .build();
        
        // Save the original to repository
        ArtifactEntity originalEntity = artifactService.toEntity("exec-template", original);
        artifactRepository.save(originalEntity);
        
        ArtifactKey duplicateKey = ArtifactKey.createRoot();

        // When
        Optional<Artifact> result = artifactService.decorateDuplicate(contentHash, duplicateKey);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(Artifact.TemplateDbRef.class);
        Artifact.TemplateDbRef dbRef = (Artifact.TemplateDbRef) result.get();
        assertThat(dbRef.templateArtifactKey()).isEqualTo(duplicateKey);
    }

    // ========== save Tests ==========

    @Test
    @DisplayName("save persists entity to repository")
    void save_validEntity_persistsSuccessfully() {
        // Given
        ArtifactKey artifactKey = ArtifactKey.createRoot();
        ArtifactEntity entity = ArtifactEntity.builder()
                .artifactKey(artifactKey.value())
                .executionKey("exec-save-test")
                .artifactType("Execution")
                .depth(1)
                .contentJson("{}")
                .contentHash("savehash")
                .build();

        // When
        ArtifactEntity saved = artifactService.save(entity);

        // Then
        assertThat(saved.getUuid()).isNotNull();
        assertThat(artifactRepository.findByArtifactKey(artifactKey.value())).isPresent();
    }

    // ========== doPersist Tests ==========

    @Test
    @DisplayName("doPersist saves single artifact without duplicates")
    void doPersist_singleArtifact_savesSuccessfully() {
        // Given
        String executionKey = "exec-persist-single";
        ArtifactKey rootKey = ArtifactKey.createRoot();
        String contentHash = "singlehash";
        
        Artifact.ExecutionArtifact artifact = Artifact.ExecutionArtifact.builder()
                .artifactKey(rootKey)
                .workflowRunId("workflow-persist")
                .status(Artifact.ExecutionStatus.RUNNING)
                .metadata(new HashMap<>())
                .children(new ArrayList<>())
                .hash(contentHash)
                .build();
        
        ArtifactNode root = ArtifactNode.createRoot(artifact);

        // When
        artifactService.doPersist(executionKey, root);

        // Then
        Optional<ArtifactEntity> saved = artifactRepository.findByContentHash(contentHash);
        assertThat(saved).isPresent();
        assertThat(saved.get().getExecutionKey()).isEqualTo(executionKey);
    }

    @Test
    @DisplayName("doPersist deduplicates artifacts with same content hash")
    void doPersist_duplicateHashes_savesOnlyOneOriginal() {
        // Given
        String executionKey = "exec-persist-dedup";
        ArtifactKey rootKey = ArtifactKey.createRoot();
        String sharedHash = "sharedhash123";
        
        // Create root with a child that has the same hash
        ArtifactKey childKey = rootKey.createChild();
        
        Artifact.ToolCallArtifact child = Artifact.ToolCallArtifact.builder()
                .artifactKey(childKey)
                .toolCallId("tool-1")
                .toolName("sharedTool")
                .inputHash(sharedHash)
                .metadata(new HashMap<>())
                .children(new ArrayList<>())
                .build();
        
        Artifact.ExecutionArtifact rootArtifact = Artifact.ExecutionArtifact.builder()
                .artifactKey(rootKey)
                .workflowRunId("workflow-dedup")
                .status(Artifact.ExecutionStatus.RUNNING)
                .metadata(new HashMap<>())
                .children(new ArrayList<>(List.of(child)))
                .hash("roothash")
                .build();
        
        ArtifactNode root = ArtifactNode.createRoot(rootArtifact);

        // When
        artifactService.doPersist(executionKey, root);

        // Then
        // Root and child should both be saved (different hashes)
        assertThat(artifactRepository.findByContentHash("roothash")).isPresent();
    }

    @Test
    @DisplayName("doPersist handles artifacts with no content hash")
    void doPersist_noContentHash_stillProcesses() {
        // Given
        String executionKey = "exec-no-hash";
        ArtifactKey rootKey = ArtifactKey.createRoot();
        
        Artifact.ExecutionArtifact artifact = Artifact.ExecutionArtifact.builder()
                .artifactKey(rootKey)
                .workflowRunId("workflow-no-hash")
                .status(Artifact.ExecutionStatus.RUNNING)
                .metadata(new HashMap<>())
                .children(new ArrayList<>())
                .hash(null) // No hash
                .build();
        
        ArtifactNode root = ArtifactNode.createRoot(artifact);

        // When/Then - should not throw
        artifactService.doPersist(executionKey, root);
        
        // Artifacts without hash are filtered out in the grouping phase
        // so nothing gets persisted in the hash-based flow
    }

    @Test
    @DisplayName("doPersist creates refs when hash already exists in DB")
    void doPersist_existingHash_createsRefs() {
        // Given
        String contentHash = "existinghash789";
        ArtifactKey existingKey = ArtifactKey.createRoot();
        
        // First, save an artifact with this hash
        Artifact.ExecutionArtifact existingArtifact = Artifact.ExecutionArtifact.builder()
                .artifactKey(existingKey)
                .workflowRunId("workflow-existing")
                .status(Artifact.ExecutionStatus.COMPLETED)
                .metadata(new HashMap<>())
                .children(new ArrayList<>())
                .hash(contentHash)
                .build();
        
        ArtifactEntity existingEntity = artifactService.toEntity("exec-existing", existingArtifact);
        artifactRepository.save(existingEntity);
        
        // Now create a new artifact with the same hash
        String newExecutionKey = "exec-new";
        ArtifactKey newKey = ArtifactKey.createRoot();
        
        Artifact.ExecutionArtifact newArtifact = Artifact.ExecutionArtifact.builder()
                .artifactKey(newKey)
                .workflowRunId("workflow-new")
                .status(Artifact.ExecutionStatus.RUNNING)
                .metadata(new HashMap<>())
                .children(new ArrayList<>())
                .hash(contentHash)
                .build();
        
        ArtifactNode root = ArtifactNode.createRoot(newArtifact);

        // When
        artifactService.doPersist(newExecutionKey, root);

        // Then
        // The original should still exist
        Optional<ArtifactEntity> original = artifactRepository.findByContentHash(contentHash);
        assertThat(original).isPresent();
        assertThat(original.get().getArtifactKey()).isEqualTo(existingKey.value());
    }

    // ========== Round-trip Tests ==========

    @Test
    @DisplayName("toEntity and deserializeArtifact round-trip preserves data")
    void roundTrip_toEntityAndDeserialize_preservesData() {
        // Given
        String executionKey = "exec-roundtrip";
        ArtifactKey artifactKey = ArtifactKey.createRoot();
        
        Artifact.ExecutionArtifact original = Artifact.ExecutionArtifact.builder()
                .artifactKey(artifactKey)
                .workflowRunId("workflow-roundtrip")
                .startedAt(Instant.parse("2024-01-15T10:30:00Z"))
                .status(Artifact.ExecutionStatus.COMPLETED)
                .metadata(Map.of("env", "test", "version", "1.0"))
                .children(new ArrayList<>())
                .hash("roundtriphash")
                .build();

        // When
        ArtifactEntity entity = artifactService.toEntity(executionKey, original);
        Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity);

        // Then
        assertThat(deserialized).isPresent();
        assertThat(deserialized.get()).isInstanceOf(Artifact.ExecutionArtifact.class);
        Artifact.ExecutionArtifact result = (Artifact.ExecutionArtifact) deserialized.get();
        
        assertThat(result.artifactKey()).isEqualTo(original.artifactKey());
        assertThat(result.workflowRunId()).isEqualTo(original.workflowRunId());
        assertThat(result.status()).isEqualTo(original.status());
    }

    @Test
    @DisplayName("PromptContributionTemplate round-trip preserves template data")
    void roundTrip_promptContribution_preservesTemplateData() {
        // Given
        String executionKey = "exec-template-roundtrip";
        ArtifactKey artifactKey = ArtifactKey.createRoot();
        
        Artifact.PromptContributionTemplate original = Artifact.PromptContributionTemplate.builder()
                .templateArtifactKey(artifactKey)
                .contributorName("system-prompt")
                .priority(100)
                .agentTypes(List.of("planning", "coding"))
                .templateText("You are a helpful assistant.")
                .orderIndex(0)
                .hash("prompthash")
                .metadata(Map.of("category", "system"))
                .children(new ArrayList<>())
                .build();

        // When
        ArtifactEntity entity = artifactService.toEntity(executionKey, original);
        Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity);

        // Then
        assertThat(deserialized).isPresent();
        assertThat(deserialized.get()).isInstanceOf(Artifact.PromptContributionTemplate.class);
        Artifact.PromptContributionTemplate result = (Artifact.PromptContributionTemplate) deserialized.get();
        
        assertThat(result.contributorName()).isEqualTo("system-prompt");
        assertThat(result.templateText()).isEqualTo("You are a helpful assistant.");
        assertThat(result.priority()).isEqualTo(100);
    }

    @Nested
    @DisplayName("ArtifactService doPersist")
    class ArtifactServiceDoPersistTests {

        private ArtifactKey rootKey;
        private String executionKey;

        @BeforeEach
        void setUpDoPersistTests() {
            rootKey = ArtifactKey.createRoot();
            executionKey = rootKey.value();
        }

        @Test
        @DisplayName("doPersist saves artifact with unique hash")
        void doPersistSavesArtifactWithUniqueHash() {
            String uniqueHash = "unique-hash-for-test";

            // Create root artifact node
            ArtifactNode rootNode = ArtifactNode.createRoot(createExecutionArtifact(rootKey));

            // Create an artifact with a unique hash
            ArtifactKey key1 = rootKey.createChild();
            Artifact.RenderedPromptArtifact artifact1 = Artifact.RenderedPromptArtifact.builder()
                    .artifactKey(key1)
                    .renderedText("Unique content")
                    .promptName("prompt1")
                    .hash(uniqueHash)
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();

            rootNode.addArtifact(artifact1);

            // Persist
            artifactService.doPersist(executionKey, rootNode);

            // The artifact should be saved
            Optional<ArtifactEntity> entity1 = artifactRepository.findByArtifactKey(key1.value());
            assertThat(entity1).isPresent();

            // The original has the uniqueHash as contentHash
            Optional<ArtifactEntity> originalEntity = artifactRepository.findByContentHash(uniqueHash);
            assertThat(originalEntity).isPresent();

            // Check that we have exactly 2 artifacts (root + artifact)
            assertThat(artifactRepository.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("doPersist generates hash for artifacts without hash")
        void doPersistGeneratesHashForArtifactsWithoutHash() {
            // Create root artifact node
            ArtifactNode rootNode = ArtifactNode.createRoot(createExecutionArtifact(rootKey));

            // Create artifact without hash (null or blank)
            ArtifactKey key1 = rootKey.createChild();
            Artifact.RenderedPromptArtifact artifactWithoutHash = Artifact.RenderedPromptArtifact.builder()
                    .artifactKey(key1)
                    .renderedText("Content without hash")
                    .promptName("no-hash-prompt")
                    .hash(null) // No hash
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();

            ArtifactKey key2 = rootKey.createChild();
            Artifact.RenderedPromptArtifact artifactWithBlankHash = Artifact.RenderedPromptArtifact.builder()
                    .artifactKey(key2)
                    .renderedText("Content with blank hash")
                    .promptName("blank-hash-prompt")
                    .hash("") // Blank hash
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();

            rootNode.addArtifact(artifactWithoutHash);
            rootNode.addArtifact(artifactWithBlankHash);

            // Persist
            artifactService.doPersist(executionKey, rootNode);

            // Both artifacts should be saved with generated hashes
            Optional<ArtifactEntity> entity1 = artifactRepository.findByArtifactKey(key1.value());
            Optional<ArtifactEntity> entity2 = artifactRepository.findByArtifactKey(key2.value());

            assertThat(entity1).isPresent();
            assertThat(entity2).isPresent();

            // Both should have non-null, non-blank content hashes (UUID format)
            assertThat(entity1.get().getContentHash()).isNotNull().isNotBlank();
            assertThat(entity2.get().getContentHash()).isNotNull().isNotBlank();

            // The generated hashes should be different (UUID uniqueness)
            assertThat(entity1.get().getContentHash()).isNotEqualTo(entity2.get().getContentHash());
        }

        @Test
        @DisplayName("doPersist saves unique artifacts without creating refs")
        void doPersistSavesUniqueArtifactsWithoutRefs() {
            // Create root artifact node
            ArtifactNode rootNode = ArtifactNode.createRoot(createExecutionArtifact(rootKey));

            // Create artifacts with different hashes
            ArtifactKey key1 = rootKey.createChild();
            Artifact.RenderedPromptArtifact artifact1 = Artifact.RenderedPromptArtifact.builder()
                    .artifactKey(key1)
                    .renderedText("Unique content 1")
                    .promptName("unique-prompt-1")
                    .hash("unique-hash-1")
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();

            ArtifactKey key2 = rootKey.createChild();
            Artifact.ToolCallArtifact artifact2 = Artifact.ToolCallArtifact.builder()
                    .artifactKey(key2)
                    .toolCallId("call-1")
                    .toolName("tool1")
                    .inputJson("{}")
                    .inputHash("unique-hash-2") // Different hash
                    .outputJson("{}")
                    .outputHash("output-hash")
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();

            ArtifactKey key3 = rootKey.createChild();
            Artifact.EventArtifact artifact3 = Artifact.EventArtifact.builder()
                    .artifactKey(key3)
                    .eventId("event-1")
                    .eventTimestamp(Instant.now())
                    .eventType("TestEvent")
                    .payloadJson(new HashMap<>())
                    .hash("unique-hash-3") // Different hash
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();

            rootNode.addArtifact(artifact1);
            rootNode.addArtifact(artifact2);
            rootNode.addArtifact(artifact3);

            // Persist
            artifactService.doPersist(executionKey, rootNode);

            // All artifacts should be saved as originals (not refs)
            Optional<ArtifactEntity> entity1 = artifactRepository.findByArtifactKey(key1.value());
            Optional<ArtifactEntity> entity2 = artifactRepository.findByArtifactKey(key2.value());
            Optional<ArtifactEntity> entity3 = artifactRepository.findByArtifactKey(key3.value());

            assertThat(entity1).isPresent();
            assertThat(entity2).isPresent();
            assertThat(entity3).isPresent();

            // Check artifact types are preserved (not converted to refs)
            assertThat(entity1.get().getArtifactType()).isEqualTo(Artifact.RenderedPromptArtifact.class.getSimpleName());
            assertThat(entity2.get().getArtifactType()).isEqualTo(Artifact.ToolCallArtifact.class.getSimpleName());
            assertThat(entity3.get().getArtifactType()).isEqualTo(Artifact.EventArtifact.class.getSimpleName());

            // Total count should be 4 (root + 3 unique artifacts)
            assertThat(artifactRepository.count()).isEqualTo(4);
        }

        @Test
        @DisplayName("doPersist creates TemplateDbRef for duplicate Templated artifacts")
        void doPersistCreatesTemplateDbRefForDuplicateTemplates() {
            String sharedHash = "shared-template-hash";

            // Create root artifact node
            ArtifactNode rootNode = ArtifactNode.createRoot(createExecutionArtifact(rootKey));

            // Create two template artifacts with the same hash
            ArtifactKey key1 = rootKey.createChild();
            PromptTemplateVersion template1 = new PromptTemplateVersion(
                    "tpl.shared.template",
                    "Shared template content",
                    sharedHash,
                    key1,
                    Instant.now(),
                    new ArrayList<>()
            );

            ArtifactKey key2 = key1.createChild();
            PromptTemplateVersion template2 = new PromptTemplateVersion(
                    "tpl.shared.template",
                    "Shared template content",
                    sharedHash, // Same hash
                    key2,
                    Instant.now(),
                    new ArrayList<>()
            );

            var f = rootNode.addArtifact(template1);
            var s = rootNode.addArtifact(template2);

            // Persist
            artifactService.doPersist(executionKey, rootNode);

            // Verify original template was saved
            Optional<ArtifactEntity> originalEntity = artifactRepository.findByContentHash(sharedHash);
            assertThat(originalEntity).isPresent();
            assertThat(originalEntity.get().getArtifactType()).isEqualTo("PromptTemplateVersion");

            // The second should be saved as TemplateDbRef
            // Find the entity that is NOT the original
            List<ArtifactEntity> allEntities = artifactRepository.findAll();
            Optional<ArtifactEntity> refEntity = allEntities.stream()
                    .filter(e -> artifactService.deserializeArtifact(e)
                            .get().artifactType().equals("TemplateDbRef"))
                    .findFirst();

            assertThat(allEntities.size()).isEqualTo(3);

            assertThat(refEntity).isPresent();
            assertThat(refEntity.get().getTemplateStaticId()).isEqualTo("tpl.shared.template");
        }

        @Test
        @DisplayName("doPersist handles existing duplicate in database")
        void doPersistHandlesExistingDuplicateInDatabase() {
            String sharedHash = "pre-existing-hash";

            // First, persist an artifact with a specific hash
            ArtifactKey existingKey = rootKey.createChild();
            Artifact.RenderedPromptArtifact existingArtifact = Artifact.RenderedPromptArtifact.builder()
                    .artifactKey(existingKey)
                    .renderedText("Pre-existing content")
                    .promptName("existing-prompt")
                    .hash(sharedHash)
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();

            ArtifactNode firstRootNode = ArtifactNode.createRoot(createExecutionArtifact(rootKey));
            firstRootNode.addArtifact(existingArtifact);
            artifactService.doPersist(executionKey, firstRootNode);

            // Verify it was saved
            assertThat(artifactRepository.findByContentHash(sharedHash)).isPresent();
            long countAfterFirst = artifactRepository.count();

            // Now create a new execution with an artifact that has the same hash
            ArtifactKey newRootKey = ArtifactKey.createRoot();
            String newExecutionKey = newRootKey.value();

            ArtifactKey newKey = newRootKey.createChild();
            Artifact.RenderedPromptArtifact newArtifact = Artifact.RenderedPromptArtifact.builder()
                    .artifactKey(newKey)
                    .renderedText("Pre-existing content") // Same content
                    .promptName("new-prompt")
                    .hash(sharedHash) // Same hash
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();

            ArtifactNode secondRootNode = ArtifactNode.createRoot(createExecutionArtifact(newRootKey));
            secondRootNode.addArtifact(newArtifact);
            artifactService.doPersist(newExecutionKey, secondRootNode);

            // The new artifact should be saved as a ref since the hash already exists
            Optional<ArtifactEntity> newEntity = artifactRepository.findByArtifactKey(newKey.value());
            assertThat(newEntity).isPresent();
            var d = artifactService.deserializeArtifact(newEntity.get());
            assertThat(d.get().artifactType()).isEqualTo(Artifact.ArtifactDbRef.class.getSimpleName());
            assertThat(newEntity.get().getArtifactType()).isEqualTo(Artifact.RenderedPromptArtifact.class.getSimpleName());

            // The original should still exist unchanged
            Optional<ArtifactEntity> originalEntity = artifactRepository.findByContentHash(sharedHash);
            assertThat(originalEntity).isPresent();
            assertThat(originalEntity.get().getArtifactType()).isEqualTo(Artifact.RenderedPromptArtifact.class.getSimpleName());
        }
    }
}
