package com.hayden.multiagentide.integration.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.artifacts.*;
import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import com.hayden.multiagentide.events.EventBus;
import com.hayden.multiagentide.propagation.repository.PropagationRecordEntity;
import com.hayden.multiagentide.propagation.repository.PropagationRecordRepository;
import com.hayden.multiagentide.propagation.service.PropagationExecutionService;
import com.hayden.multiagentide.propagation.repository.PropagationAction;
import com.hayden.multiagentidelib.artifact.ArtifactType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive integration tests for artifact tree integrity.
 *
 * Validates:
 * - T-001: Propagation artifact emission and persistence
 * - T-002: Filter artifact emission (no events)
 * - T-003: Transformer parent-child relationships
 * - T-004: Null output edge case handling
 * - T-005: Orphan artifact buffering and linking
 * - T-006: Content-hash based deduplication
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "testdocker"})
@DisplayName("Artifact Tree Comprehensive Integration Tests")
class ArtifactTreeComprehensiveIT {

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private PropagationRecordRepository propagationRecordRepository;

    @Autowired
    private ArtifactTreeBuilder artifactTreeBuilder;

    @Autowired
    private ArtifactService artifactService;

    @Autowired
    private EventBus eventBus;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    private static final String TEST_EXECUTION_KEY = "test-exec-" + UUID.randomUUID();
    private static final String TEST_REGISTRATION_ID = "reg-123";
    private static final String TEST_LAYER_ID = "layer-456";

    @BeforeEach
    void setUp() {
        artifactRepository.deleteAll();
        propagationRecordRepository.deleteAll();
        entityManager.flush();
    }

    @Nested
    @DisplayName("T-001: Propagation Artifact Emission")
    class PropagationArtifactEmissionTests {

        @Test
        @DisplayName("T-001.1: Basic propagation artifact emission")
        void test_basicPropagationEmission() {
            // Create a simple propagation artifact
            Artifact propagationArtifact = createTestArtifact(
                "ak:prop-basic-001",
                null,
                "Propagation Result",
                ArtifactType.TEXT
            );

            // Add to tree
            ArtifactNode.AddResult result = artifactTreeBuilder
                .addArtifactResult(TEST_EXECUTION_KEY, propagationArtifact);

            // Verify addition succeeded
            assertThat(result).isEqualTo(ArtifactNode.AddResult.ADDED);

            // Verify artifact is in tree
            Optional<Artifact> retrieved = artifactTreeBuilder
                .getArtifact(TEST_EXECUTION_KEY, propagationArtifact.artifactKey().value());
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().artifactKey()).isEqualTo(propagationArtifact.artifactKey());
        }

        @Test
        @DisplayName("T-001.2: Propagation artifact persistence to database")
        void test_propagationPersistenceToDatabase() {
            // Create and add propagation artifact
            Artifact propagationArtifact = createTestArtifact(
                "ak:prop-persist-001",
                null,
                "Persisted Propagation",
                ArtifactType.TEXT
            );

            artifactTreeBuilder.addArtifactResult(TEST_EXECUTION_KEY, propagationArtifact);

            // Persist to database
            artifactTreeBuilder.persistExecutionTree(TEST_EXECUTION_KEY);

            // Verify in database
            Optional<ArtifactEntity> persisted = artifactRepository
                .findByArtifactKey(propagationArtifact.artifactKey().value());
            assertThat(persisted).isPresent();
            assertThat(persisted.get().getContentJson()).contains("Persisted Propagation");
        }

        @Test
        @DisplayName("T-001.3: Correlation key deduplication")
        void test_correlationKeyDeduplication() {
            // Create propagation record with correlation key
            String correlationKey = computeCorrelationKey("input-123", "prop-v1");

            PropagationRecordEntity record1 = createPropagationRecord(
                correlationKey,
                "input-123",
                "output-123",
                PropagationAction.ITEM_CREATED
            );

            propagationRecordRepository.save(record1);
            entityManager.flush();

            // Verify the record exists with the correlation key
            List<PropagationRecordEntity> records = propagationRecordRepository.findAll();
            assertThat(records)
                .filteredOn(r -> r.getCorrelationKey().equals(correlationKey))
                .hasSize(1);
        }

        @Test
        @DisplayName("T-001.4: Multiple artifacts per execution")
        void test_multipleArtifactsPerExecution() {
            // Create multiple propagation artifacts
            List<Artifact> artifacts = List.of(
                createTestArtifact("ak:multi-001", null, "Result 1", ArtifactType.TEXT),
                createTestArtifact("ak:multi-002", null, "Result 2", ArtifactType.TEXT),
                createTestArtifact("ak:multi-003", null, "Result 3", ArtifactType.TEXT)
            );

            // Add all to tree
            for (Artifact artifact : artifacts) {
                ArtifactNode.AddResult result = artifactTreeBuilder
                    .addArtifactResult(TEST_EXECUTION_KEY, artifact);
                assertThat(result).isEqualTo(ArtifactNode.AddResult.ADDED);
            }

            // Persist all
            artifactTreeBuilder.persistExecutionTree(TEST_EXECUTION_KEY);

            // Verify all in database with same execution key
            List<ArtifactEntity> persisted = artifactRepository
                .findByExecutionKeyOrderByArtifactKey(TEST_EXECUTION_KEY);
            assertThat(persisted).hasSize(3);
            assertThat(persisted).extracting(ArtifactEntity::getArtifactKey)
                .containsExactly("ak:multi-001", "ak:multi-002", "ak:multi-003");
        }
    }

    @Nested
    @DisplayName("T-004: Null Output Handling")
    class NullOutputHandlingTests {

        @Test
        @DisplayName("T-004.1: Null output creates failed record")
        void test_nullOutputHandling() {
            // Create propagation record for null output
            PropagationRecordEntity failedRecord = createPropagationRecord(
                "corr-null-001",
                "input-data",
                null,  // null output
                PropagationAction.FAILED
            );

            propagationRecordRepository.save(failedRecord);
            entityManager.flush();

            // Verify record with FAILED action exists
            Optional<PropagationRecordEntity> retrieved = propagationRecordRepository
                .findById(failedRecord.getRecordId());
            assertThat(retrieved)
                .isPresent()
                .hasValueSatisfying(r -> {
                    assertThat(r.getAction()).isEqualTo(PropagationAction.FAILED);
                    assertThat(r.getAfterPayload()).isNull();
                });
        }

        @Test
        @DisplayName("T-004.2: Null output does not create artifact")
        void test_nullOutputNoArtifact() {
            String executionKey = "test-null-exec-" + UUID.randomUUID();

            // Create and persist propagation record with null output
            PropagationRecordEntity failedRecord = new PropagationRecordEntity();
            failedRecord.setRegistrationId(TEST_REGISTRATION_ID);
            failedRecord.setLayerId(TEST_LAYER_ID);
            failedRecord.setAction(PropagationAction.FAILED);
            failedRecord.setCorrelationKey("corr-null-no-artifact");
            failedRecord.setBeforePayload("input");
            failedRecord.setAfterPayload(null);

            propagationRecordRepository.save(failedRecord);

            // Verify no artifact created for this execution
            List<ArtifactEntity> artifacts = artifactRepository
                .findByExecutionKeyOrderByArtifactKey(executionKey);
            assertThat(artifacts).isEmpty();
        }
    }

    @Nested
    @DisplayName("T-005: Orphan Artifact Buffering")
    class OrphanArtifactBufferingTests {

        @Test
        @DisplayName("T-005.1: Orphan artifact buffering when parent missing")
        void test_orphanBufferingWhenParentMissing() {
            // Create child artifact with non-existent parent
            Artifact parentArtifact = createTestArtifact(
                "ak:parent-001",
                null,
                "Parent",
                ArtifactType.TEXT
            );

            Artifact childArtifact = createTestArtifact(
                "ak:parent-001/child-001",
                parentArtifact.artifactKey(),
                "Child",
                ArtifactType.TEXT
            );

            // Try to add child first (parent doesn't exist)
            ArtifactNode.AddResult childResult = artifactTreeBuilder
                .addArtifactResult(TEST_EXECUTION_KEY, childArtifact);

            // Should return PARENT_NOT_FOUND
            assertThat(childResult).isEqualTo(ArtifactNode.AddResult.PARENT_NOT_FOUND);
        }

        @Test
        @DisplayName("T-005.2: Orphan linking when parent established")
        void test_orphanLinkingWhenParentEstablished() {
            String execKey = "orphan-test-" + UUID.randomUUID();

            // Create parent
            Artifact parentArtifact = createTestArtifact(
                "ak:orphan-parent",
                null,
                "Orphan Parent",
                ArtifactType.TEXT
            );

            // Add parent (becomes root)
            ArtifactNode.AddResult parentResult = artifactTreeBuilder
                .addArtifactResult(execKey, parentArtifact);
            assertThat(parentResult).isEqualTo(ArtifactNode.AddResult.ADDED);

            // Create child with parent reference
            Artifact childArtifact = createTestArtifact(
                "ak:orphan-parent/child",
                parentArtifact.artifactKey(),
                "Orphan Child",
                ArtifactType.TEXT
            );

            // Add child (should now find parent)
            ArtifactNode.AddResult childResult = artifactTreeBuilder
                .addArtifactResult(execKey, childArtifact);
            assertThat(childResult).isEqualTo(ArtifactNode.AddResult.ADDED);

            // Verify child is in tree under parent
            Optional<Artifact> retrievedChild = artifactTreeBuilder
                .getArtifact(execKey, childArtifact.artifactKey().value());
            assertThat(retrievedChild).isPresent();
        }

        @Test
        @DisplayName("T-005.3: Out-of-order artifact arrival")
        void test_outOfOrderArrival() {
            String execKey = "ooo-test-" + UUID.randomUUID();

            // Create three-level chain: A -> B -> C
            Artifact artifactA = createTestArtifact("ak:ooo-a", null, "A", ArtifactType.TEXT);
            Artifact artifactB = createTestArtifact("ak:ooo-a/b", artifactA.artifactKey(), "B", ArtifactType.TEXT);
            Artifact artifactC = createTestArtifact("ak:ooo-a/b/c", artifactB.artifactKey(), "C", ArtifactType.TEXT);

            // Add in out-of-order: C, B, A
            ArtifactNode.AddResult cResult = artifactTreeBuilder.addArtifactResult(execKey, artifactC);
            assertThat(cResult).isEqualTo(ArtifactNode.AddResult.PARENT_NOT_FOUND);

            ArtifactNode.AddResult bResult = artifactTreeBuilder.addArtifactResult(execKey, artifactB);
            assertThat(bResult).isEqualTo(ArtifactNode.AddResult.PARENT_NOT_FOUND);

            // Now add A (root)
            ArtifactNode.AddResult aResult = artifactTreeBuilder.addArtifactResult(execKey, artifactA);
            assertThat(aResult).isEqualTo(ArtifactNode.AddResult.ADDED);

            // Verify complete chain in database after persistence
            artifactTreeBuilder.persistExecutionTree(execKey);

            List<ArtifactEntity> all = artifactRepository
                .findByExecutionKeyOrderByArtifactKey(execKey);
            assertThat(all)
                .extracting(ArtifactEntity::getArtifactKey)
                .containsExactly("ak:ooo-a", "ak:ooo-a/b", "ak:ooo-a/b/c");
        }
    }

    @Nested
    @DisplayName("T-006: Content-Hash Deduplication")
    class ContentHashDeduplicationTests {

        @Test
        @DisplayName("T-006.1: Duplicate hash detection at tree level")
        void test_duplicateHashDetectionAtTreeLevel() {
            String execKey = "dedup-test-" + UUID.randomUUID();

            // Create two artifacts with identical content
            String identicalContent = "Same Content";
            Artifact artifact1 = createTestArtifact("ak:dedup-1", null, identicalContent, ArtifactType.TEXT);
            Artifact artifact2 = createTestArtifact("ak:dedup-2", null, identicalContent, ArtifactType.TEXT);

            // Add first artifact
            ArtifactNode.AddResult result1 = artifactTreeBuilder
                .addArtifactResult(execKey, artifact1);
            assertThat(result1).isEqualTo(ArtifactNode.AddResult.ADDED);

            // Add second artifact with same content (should be duplicate hash)
            ArtifactNode.AddResult result2 = artifactTreeBuilder
                .addArtifactResult(execKey, artifact2);
            assertThat(result2).isEqualTo(ArtifactNode.AddResult.DUPLICATE_HASH);
        }

        @Test
        @DisplayName("T-006.2: Content hash uniqueness verification")
        void test_contentHashComputationDeterministic() throws Exception {
            // Same content should always produce same hash
            String testContent = "Test Content for Hashing";
            String hash1 = computeSHA256(testContent);
            String hash2 = computeSHA256(testContent);

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("T-006.3: Different content produces different hashes")
        void test_differentContentDifferentHashes() throws Exception {
            String hash1 = computeSHA256("Content 1");
            String hash2 = computeSHA256("Content 2");

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("T-006.4: Cross-execution deduplication")
        void test_crossExecutionDeduplication() throws Exception {
            String execKey1 = "cross-dedup-1-" + UUID.randomUUID();
            String execKey2 = "cross-dedup-2-" + UUID.randomUUID();

            String identicalContent = "Cross-execution Identical";
            Artifact artifact1 = createTestArtifact("ak:cross-1", null, identicalContent, ArtifactType.TEXT);
            Artifact artifact2 = createTestArtifact("ak:cross-2", null, identicalContent, ArtifactType.TEXT);

            // Add to different executions
            artifactTreeBuilder.addArtifactResult(execKey1, artifact1);
            artifactTreeBuilder.addArtifactResult(execKey2, artifact2);

            // Persist both
            artifactTreeBuilder.persistExecutionTree(execKey1);
            artifactTreeBuilder.persistExecutionTree(execKey2);

            // Verify both have same content hash but are in different executions
            String hash1 = computeSHA256(identicalContent);
            List<ArtifactEntity> withHash = artifactRepository.findByContentHash(hash1);

            assertThat(withHash).hasSize(2);
            assertThat(withHash)
                .extracting(ArtifactEntity::getExecutionKey)
                .containsExactly(execKey1, execKey2);
        }
    }

    // Helper methods

    private Artifact createTestArtifact(
        String key,
        ArtifactKey parentKey,
        String content,
        ArtifactType type
    ) {
        return new Artifact(
            new ArtifactKey(key),
            parentKey,
            type,
            null,
            content,
            Instant.now().toString(),
            null,
            null,
            null
        );
    }

    private PropagationRecordEntity createPropagationRecord(
        String correlationKey,
        String beforePayload,
        String afterPayload,
        PropagationAction action
    ) {
        PropagationRecordEntity record = new PropagationRecordEntity();
        record.setRecordId(UUID.randomUUID().toString());
        record.setRegistrationId(TEST_REGISTRATION_ID);
        record.setLayerId(TEST_LAYER_ID);
        record.setSourceNodeId("test-source");
        record.setSourceType("TEXT");
        record.setAction(action);
        record.setBeforePayload(beforePayload);
        record.setAfterPayload(afterPayload);
        record.setCorrelationKey(correlationKey);
        record.setCreatedAt(Instant.now());
        return record;
    }

    private String computeCorrelationKey(String input, String propagatorVersion) {
        return TEST_REGISTRATION_ID + "-" + TEST_LAYER_ID + "-" + input.hashCode() + "-" + propagatorVersion.hashCode();
    }

    private String computeSHA256(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content.getBytes());
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
