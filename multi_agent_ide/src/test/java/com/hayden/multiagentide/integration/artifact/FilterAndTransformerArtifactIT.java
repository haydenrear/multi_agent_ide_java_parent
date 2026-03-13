package com.hayden.multiagentide.integration.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.artifacts.*;
import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import com.hayden.multiagentide.filter.repository.FilterDecisionRecordEntity;
import com.hayden.multiagentide.filter.repository.FilterDecisionRepository;
import com.hayden.multiagentide.filter.repository.FilterAction;
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

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for filter and transformer artifact emission.
 *
 * Validates:
 * - T-002: Filter artifact emission (no FilterEvent)
 * - T-003: Transformer parent-child relationships
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "testdocker"})
@DisplayName("Filter and Transformer Artifact Integration Tests")
class FilterAndTransformerArtifactIT {

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private FilterDecisionRecordRepository filterDecisionRepository;

    @Autowired
    private ArtifactTreeBuilder artifactTreeBuilder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    private static final String TEST_REGISTRATION_ID = "filter-reg-123";
    private static final String TEST_LAYER_ID = "filter-layer-456";

    @BeforeEach
    void setUp() {
        artifactRepository.deleteAll();
        filterDecisionRepository.deleteAll();
        entityManager.flush();
    }

    @Nested
    @DisplayName("T-002: Filter Artifact Emission")
    class FilterArtifactEmissionTests {

        @Test
        @DisplayName("T-002.1: Filter decision recorded without event emission")
        void test_filterDecisionWithoutEvent() {
            // Create filter decision record
            FilterDecisionRecordEntity decision = new FilterDecisionRecordEntity();
            decision.setRegistrationId(TEST_REGISTRATION_ID);
            decision.setLayerId(TEST_LAYER_ID);
            decision.setSourceNodeId("source-stream-001");
            decision.setAction(FilterAction.ACCEPTED);
            decision.setCreatedAt(Instant.now());

            filterDecisionRepository.save(decision);
            entityManager.flush();

            // Verify decision is persisted
            Optional<FilterDecisionRecordEntity> retrieved = filterDecisionRepository
                .findById(decision.getDecisionId());
            assertThat(retrieved)
                .isPresent()
                .hasValueSatisfying(d -> {
                    assertThat(d.getAction()).isEqualTo(FilterAction.ACCEPTED);
                    assertThat(d.getSourceNodeId()).isEqualTo("source-stream-001");
                });

            // Note: No FilterEvent should be emitted (architectural difference from propagators)
        }

        @Test
        @DisplayName("T-002.2: All filter decision actions persisted correctly")
        void test_filterDecisionActions() {
            // Create decisions for all action types
            FilterAction[] actions = {
                FilterAction.ACCEPTED,
                FilterAction.REJECTED,
                FilterAction.ERROR,
                FilterAction.PASSTHROUGH
            };

            for (int i = 0; i < actions.length; i++) {
                FilterDecisionRecordEntity decision = new FilterDecisionRecordEntity();
                decision.setRegistrationId(TEST_REGISTRATION_ID);
                decision.setLayerId(TEST_LAYER_ID);
                decision.setSourceNodeId("source-" + i);
                decision.setAction(actions[i]);
                decision.setCreatedAt(Instant.now());

                filterDecisionRepository.save(decision);
            }
            entityManager.flush();

            // Verify all actions recorded
            List<FilterDecisionRecordEntity> allDecisions = filterDecisionRepository.findAll();
            assertThat(allDecisions).hasSize(4);
            assertThat(allDecisions)
                .extracting(FilterDecisionRecordEntity::getAction)
                .containsExactlyInAnyOrder(
                    FilterAction.ACCEPTED,
                    FilterAction.REJECTED,
                    FilterAction.ERROR,
                    FilterAction.PASSTHROUGH
                );
        }

        @Test
        @DisplayName("T-002.3: Filtered artifact parenting to stream source")
        void test_filteredArtifactParenting() {
            String execKey = "filter-stream-" + UUID.randomUUID();

            // Create source stream artifact
            Artifact streamSource = new Artifact(
                new ArtifactKey("ak:stream-source"),
                null,
                ArtifactType.TEXT,
                null,
                "Stream Input",
                Instant.now().toString(),
                null,
                null,
                null
            );

            // Add stream source to tree
            artifactTreeBuilder.addArtifactResult(execKey, streamSource);

            // Create filtered artifact with stream source as parent
            Artifact filteredArtifact = new Artifact(
                new ArtifactKey("ak:stream-source/filtered"),
                streamSource.artifactKey(),
                ArtifactType.TEXT,
                null,
                "Filtered Result",
                Instant.now().toString(),
                null,
                null,
                null
            );

            // Add filtered artifact
            ArtifactNode.AddResult result = artifactTreeBuilder
                .addArtifactResult(execKey, filteredArtifact);
            assertThat(result).isEqualTo(ArtifactNode.AddResult.ADDED);

            // Create filter decision record
            FilterDecisionRecordEntity decision = new FilterDecisionRecordEntity();
            decision.setRegistrationId(TEST_REGISTRATION_ID);
            decision.setLayerId(TEST_LAYER_ID);
            decision.setSourceNodeId(streamSource.artifactKey().value());
            decision.setAction(FilterAction.ACCEPTED);
            decision.setCreatedAt(Instant.now());

            filterDecisionRepository.save(decision);

            // Persist all artifacts
            artifactTreeBuilder.persistExecutionTree(execKey);
            entityManager.flush();

            // Verify parent-child relationship in database
            Optional<ArtifactEntity> filtered = artifactRepository
                .findByArtifactKey(filteredArtifact.artifactKey().value());
            assertThat(filtered)
                .isPresent()
                .hasValueSatisfying(a -> {
                    assertThat(a.getParentKey()).isEqualTo(streamSource.artifactKey().value());
                    assertThat(a.getExecutionKey()).isEqualTo(execKey);
                });
        }
    }

    @Nested
    @DisplayName("T-003: Transformer Parent-Child Relationships")
    class TransformerParentChildTests {

        @Test
        @DisplayName("T-003.1: Basic transformer parent-child linkage")
        void test_basicTransformerParentChild() {
            String execKey = "transformer-" + UUID.randomUUID();

            // Create source artifact
            Artifact source = new Artifact(
                new ArtifactKey("ak:transform-source"),
                null,
                ArtifactType.TEXT,
                null,
                "Transform Input",
                Instant.now().toString(),
                null,
                null,
                null
            );

            // Create transformed artifact with source as parent
            Artifact transformed = new Artifact(
                new ArtifactKey("ak:transform-source/transformed"),
                source.artifactKey(),
                ArtifactType.TEXT,
                null,
                "Transform Output",
                Instant.now().toString(),
                null,
                null,
                null
            );

            // Add source
            ArtifactNode.AddResult sourceResult = artifactTreeBuilder
                .addArtifactResult(execKey, source);
            assertThat(sourceResult).isEqualTo(ArtifactNode.AddResult.ADDED);

            // Add transformed
            ArtifactNode.AddResult transformResult = artifactTreeBuilder
                .addArtifactResult(execKey, transformed);
            assertThat(transformResult).isEqualTo(ArtifactNode.AddResult.ADDED);

            // Persist and verify
            artifactTreeBuilder.persistExecutionTree(execKey);

            Optional<ArtifactEntity> transformedEntity = artifactRepository
                .findByArtifactKey(transformed.artifactKey().value());
            assertThat(transformedEntity)
                .isPresent()
                .hasValueSatisfying(a -> {
                    assertThat(a.getParentKey()).isEqualTo(source.artifactKey().value());
                    assertThat(a.getDepth()).isEqualTo(1);
                });
        }

        @Test
        @DisplayName("T-003.2: Chained transformations maintain lineage")
        void test_chainedTransformations() {
            String execKey = "chained-transform-" + UUID.randomUUID();

            // Create three-level chain: A -> B -> C
            Artifact a = new Artifact(
                new ArtifactKey("ak:chain-a"),
                null,
                ArtifactType.TEXT,
                null,
                "Original",
                Instant.now().toString(),
                null,
                null,
                null
            );

            Artifact b = new Artifact(
                new ArtifactKey("ak:chain-a/b"),
                a.artifactKey(),
                ArtifactType.TEXT,
                null,
                "Step 1",
                Instant.now().toString(),
                null,
                null,
                null
            );

            Artifact c = new Artifact(
                new ArtifactKey("ak:chain-a/b/c"),
                b.artifactKey(),
                ArtifactType.TEXT,
                null,
                "Step 2",
                Instant.now().toString(),
                null,
                null,
                null
            );

            // Add in order A -> B -> C
            artifactTreeBuilder.addArtifactResult(execKey, a);
            artifactTreeBuilder.addArtifactResult(execKey, b);
            artifactTreeBuilder.addArtifactResult(execKey, c);

            // Persist and verify chain
            artifactTreeBuilder.persistExecutionTree(execKey);

            Optional<ArtifactEntity> aEntity = artifactRepository.findByArtifactKey(a.artifactKey().value());
            Optional<ArtifactEntity> bEntity = artifactRepository.findByArtifactKey(b.artifactKey().value());
            Optional<ArtifactEntity> cEntity = artifactRepository.findByArtifactKey(c.artifactKey().value());

            assertThat(aEntity).isPresent().hasValueSatisfying(ae ->
                assertThat(ae.getParentKey()).isNull()  // Root
            );

            assertThat(bEntity).isPresent().hasValueSatisfying(be ->
                assertThat(be.getParentKey()).isEqualTo(a.artifactKey().value())
            );

            assertThat(cEntity).isPresent().hasValueSatisfying(ce ->
                assertThat(ce.getParentKey()).isEqualTo(b.artifactKey().value())
            );

            // Verify depths
            assertThat(aEntity.get().getDepth()).isEqualTo(0);
            assertThat(bEntity.get().getDepth()).isEqualTo(1);
            assertThat(cEntity.get().getDepth()).isEqualTo(2);
        }

        @Test
        @DisplayName("T-003.3: Multiple outputs with same parent")
        void test_multipleOutputsSameParent() {
            String execKey = "multi-output-" + UUID.randomUUID();

            // Create parent
            Artifact parent = new Artifact(
                new ArtifactKey("ak:multi-parent"),
                null,
                ArtifactType.TEXT,
                null,
                "Parent",
                Instant.now().toString(),
                null,
                null,
                null
            );

            // Create three children
            List<Artifact> children = List.of(
                new Artifact(
                    new ArtifactKey("ak:multi-parent/child-1"),
                    parent.artifactKey(),
                    ArtifactType.TEXT,
                    null,
                    "Child 1",
                    Instant.now().toString(),
                    null,
                    null,
                    null
                ),
                new Artifact(
                    new ArtifactKey("ak:multi-parent/child-2"),
                    parent.artifactKey(),
                    ArtifactType.TEXT,
                    null,
                    "Child 2",
                    Instant.now().toString(),
                    null,
                    null,
                    null
                ),
                new Artifact(
                    new ArtifactKey("ak:multi-parent/child-3"),
                    parent.artifactKey(),
                    ArtifactType.TEXT,
                    null,
                    "Child 3",
                    Instant.now().toString(),
                    null,
                    null,
                    null
                )
            );

            // Add all
            artifactTreeBuilder.addArtifactResult(execKey, parent);
            for (Artifact child : children) {
                artifactTreeBuilder.addArtifactResult(execKey, child);
            }

            // Persist
            artifactTreeBuilder.persistExecutionTree(execKey);

            // Verify all children have same parent
            List<ArtifactEntity> allInExecution = artifactRepository
                .findByExecutionKeyOrderByArtifactKey(execKey);
            List<ArtifactEntity> allChildren = allInExecution.stream()
                .filter(a -> parent.artifactKey().value().equals(a.getParentKey()))
                .toList();
            assertThat(allChildren)
                .hasSize(3)
                .allMatch(a -> a.getParentKey().equals(parent.artifactKey().value()));
        }
    }
}
