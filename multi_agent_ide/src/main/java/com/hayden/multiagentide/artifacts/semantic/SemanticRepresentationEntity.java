package com.hayden.multiagentide.artifacts.semantic;

import com.hayden.persistence.models.JpaHibernateAuditedIded;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for persisting SemanticRepresentation.
 * 
 * Semantic representations are post-hoc interpretations attached to source artifacts.
 * They reference but never mutate the source artifacts.
 */
@Entity
@Table(name = "semantic_representations",
        indexes = {
                @Index(name = "idx_semantic_target", columnList = "target_artifact_key"),
                @Index(name = "idx_semantic_recipe", columnList = "derivation_recipe_id"),
                @Index(name = "idx_semantic_type", columnList = "payload_type"),
                @Index(name = "idx_semantic_created", columnList = "created_time")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_semantic_key", columnNames = "semantic_key")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SemanticRepresentationEntity extends JpaHibernateAuditedIded {
    
    /**
     * Unique identifier for this semantic representation.
     */
    @Column(name = "semantic_key", nullable = false, length = 128)
    private String semanticKey;
    
    /**
     * The artifact key this representation is attached to.
     */
    @Column(name = "target_artifact_key", nullable = false, length = 30_000)
    private String targetArtifactKey;
    
    /**
     * Identifier for the derivation recipe/algorithm used.
     */
    @Column(name = "derivation_recipe_id", nullable = false, length = 128)
    private String derivationRecipeId;
    
    /**
     * Version of the derivation recipe.
     */
    @Column(name = "derivation_recipe_version", length = 64)
    private String derivationRecipeVersion;
    
    /**
     * Model reference used for generation (if applicable).
     */
    @Column(name = "model_ref", length = 256)
    private String modelRef;
    
    /**
     * Quality metadata as JSON.
     */
    @Column(name = "quality_metadata", columnDefinition = "TEXT")
    private String qualityMetadataJson;
    
    /**
     * Type of payload (Embedding, Summary, SemanticIndexRef, etc.).
     */
    @Column(name = "payload_type", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    private PayloadType payloadType;
    
    /**
     * The semantic payload as JSON or binary.
     * For embeddings, stored as JSON array of floats.
     * For summaries, stored as text.
     */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payloadJson;
    
    /**
     * Binary payload for embeddings (more efficient storage).
     */
    @Column(name = "payload_binary", columnDefinition = "BYTEA")
    private byte[] payloadBinary;
    
    /**
     * Payload types supported.
     */
    public enum PayloadType {
        EMBEDDING,
        SUMMARY,
        SEMANTIC_INDEX_REF,
        CLASSIFICATION,
        NAMED_ENTITIES,
        CUSTOM
    }
}
