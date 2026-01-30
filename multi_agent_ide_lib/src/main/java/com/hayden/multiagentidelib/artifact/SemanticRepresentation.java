package com.hayden.multiagentidelib.artifact;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.Builder;
import lombok.With;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a post-hoc semantic interpretation attached to a source artifact.
 * 
 * Semantic representations are computed after the fact (e.g., embeddings, summaries,
 * semantic indices) and reference source artifacts by key. They MUST NOT mutate
 * the source artifacts they reference.
 * 
 * Used for:
 * - Embeddings for semantic search
 * - LLM-generated summaries
 * - Semantic index references
 * - Classification labels
 * - Other derived interpretations
 */
@Builder(toBuilder = true)
@With
@JsonIgnoreProperties(ignoreUnknown = true)
public record SemanticRepresentation(
        /**
         * Unique identifier for this semantic representation.
         */
        String semanticKey,
        
        /**
         * The artifact this representation is attached to.
         */
        ArtifactKey targetArtifactKey,
        
        /**
         * Identifier for the derivation recipe/algorithm used.
         */
        String derivationRecipeId,
        
        /**
         * Version of the derivation recipe.
         */
        String derivationRecipeVersion,
        
        /**
         * Model reference used for generation (if applicable).
         */
        String modelRef,
        
        /**
         * When this representation was created.
         */
        Instant createdAt,
        
        /**
         * Optional quality metadata (confidence scores, validation results, etc.).
         */
        Map<String, Object> qualityMetadata,
        
        /**
         * Type of payload (Embedding, Summary, SemanticIndexRef, etc.).
         */
        PayloadType payloadType,
        
        /**
         * The semantic payload (embeddings, summary text, index reference, etc.).
         */
        Object payload
) {
    
    /**
     * Types of semantic payloads supported.
     */
    public enum PayloadType {
        /**
         * Vector embedding (typically float[])
         */
        EMBEDDING,
        
        /**
         * Text summary of the artifact
         */
        SUMMARY,
        
        /**
         * Reference to an external semantic index entry
         */
        SEMANTIC_INDEX_REF,
        
        /**
         * Classification labels/categories
         */
        CLASSIFICATION,
        
        /**
         * Named entity extraction results
         */
        NAMED_ENTITIES,
        
        /**
         * Custom/other payload type
         */
        CUSTOM
    }
    
    /**
     * Creates a new SemanticRepresentation for an embedding.
     */
    public static SemanticRepresentation createEmbedding(
            String semanticKey,
            ArtifactKey targetArtifactKey,
            String modelRef,
            float[] embedding
    ) {
        return SemanticRepresentation.builder()
                .semanticKey(semanticKey)
                .targetArtifactKey(targetArtifactKey)
                .derivationRecipeId("embedding")
                .derivationRecipeVersion("1.0")
                .modelRef(modelRef)
                .createdAt(Instant.now())
                .qualityMetadata(Map.of())
                .payloadType(PayloadType.EMBEDDING)
                .payload(embedding)
                .build();
    }
    
    /**
     * Creates a new SemanticRepresentation for a summary.
     */
    public static SemanticRepresentation createSummary(
            String semanticKey,
            ArtifactKey targetArtifactKey,
            String modelRef,
            String summaryText
    ) {
        return SemanticRepresentation.builder()
                .semanticKey(semanticKey)
                .targetArtifactKey(targetArtifactKey)
                .derivationRecipeId("summary")
                .derivationRecipeVersion("1.0")
                .modelRef(modelRef)
                .createdAt(Instant.now())
                .qualityMetadata(Map.of())
                .payloadType(PayloadType.SUMMARY)
                .payload(summaryText)
                .build();
    }
    
    /**
     * Creates a new SemanticRepresentation for a semantic index reference.
     */
    public static SemanticRepresentation createIndexRef(
            String semanticKey,
            ArtifactKey targetArtifactKey,
            String indexId,
            String indexEntryId
    ) {
        return SemanticRepresentation.builder()
                .semanticKey(semanticKey)
                .targetArtifactKey(targetArtifactKey)
                .derivationRecipeId("semantic-index")
                .derivationRecipeVersion("1.0")
                .modelRef(null)
                .createdAt(Instant.now())
                .qualityMetadata(Map.of())
                .payloadType(PayloadType.SEMANTIC_INDEX_REF)
                .payload(Map.of("indexId", indexId, "entryId", indexEntryId))
                .build();
    }
    
    /**
     * Returns the embedding payload if this is an embedding representation.
     */
    public float[] getEmbedding() {
        if (payloadType != PayloadType.EMBEDDING || payload == null) {
            return null;
        }
        if (payload instanceof float[] floatArray) {
            return floatArray;
        }
        return null;
    }
    
    /**
     * Returns the summary text if this is a summary representation.
     */
    public String getSummaryText() {
        if (payloadType != PayloadType.SUMMARY || payload == null) {
            return null;
        }
        if (payload instanceof String text) {
            return text;
        }
        return null;
    }
    
    /**
     * Validates that this representation does not reference a null target.
     */
    public void validate() {
        if (semanticKey == null || semanticKey.isBlank()) {
            throw new IllegalStateException("SemanticRepresentation must have a semanticKey");
        }
        if (targetArtifactKey == null) {
            throw new IllegalStateException("SemanticRepresentation must reference a target artifact");
        }
        if (payloadType == null) {
            throw new IllegalStateException("SemanticRepresentation must have a payloadType");
        }
    }
}
