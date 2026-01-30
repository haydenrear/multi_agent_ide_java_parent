package com.hayden.multiagentide.artifacts.semantic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentidelib.artifact.SemanticRepresentation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;

/**
 * Service for attaching and retrieving semantic representations.
 * 
 * Semantic representations are post-hoc interpretations that reference
 * source artifacts without mutating them. This service ensures:
 * - Source artifacts are never modified
 * - Representations are properly persisted
 * - Duplicate representations are handled appropriately
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SemanticRepresentationService {
    
    private final SemanticRepresentationRepository repository;
    private final ObjectMapper objectMapper;
    
    /**
     * Attaches a semantic representation to an artifact.
     * 
     * @param representation The semantic representation to attach
     * @return The persisted representation with its key
     */
    @Transactional
    public SemanticRepresentation attach(SemanticRepresentation representation) {
        representation.validate();
        
        // Check if already exists
        if (repository.findBySemanticKey(representation.semanticKey()).isPresent()) {
            log.debug("SemanticRepresentation already exists: {}", representation.semanticKey());
            return representation;
        }
        
        SemanticRepresentationEntity entity = toEntity(representation);
        repository.save(entity);
        
        log.info("Attached SemanticRepresentation {} to artifact {}", 
                representation.semanticKey(), 
                representation.targetArtifactKey());
        
        return representation;
    }
    
    /**
     * Attaches an embedding to an artifact.
     */
    @Transactional
    public SemanticRepresentation attachEmbedding(
            ArtifactKey targetArtifactKey,
            String modelRef,
            float[] embedding
    ) {
        String semanticKey = generateSemanticKey("emb");
        SemanticRepresentation representation = SemanticRepresentation.createEmbedding(
                semanticKey,
                targetArtifactKey,
                modelRef,
                embedding
        );
        return attach(representation);
    }
    
    /**
     * Attaches a summary to an artifact.
     */
    @Transactional
    public SemanticRepresentation attachSummary(
            ArtifactKey targetArtifactKey,
            String modelRef,
            String summaryText
    ) {
        String semanticKey = generateSemanticKey("sum");
        SemanticRepresentation representation = SemanticRepresentation.createSummary(
                semanticKey,
                targetArtifactKey,
                modelRef,
                summaryText
        );
        return attach(representation);
    }
    
    /**
     * Gets a semantic representation by its key.
     */
    public Optional<SemanticRepresentation> get(String semanticKey) {
        return repository.findBySemanticKey(semanticKey)
                .map(this::fromEntity);
    }
    
    /**
     * Gets all semantic representations attached to an artifact.
     */
    public List<SemanticRepresentation> getForArtifact(ArtifactKey artifactKey) {
        return repository.findByTargetArtifactKey(artifactKey.value())
                .stream()
                .map(this::fromEntity)
                .toList();
    }
    
    /**
     * Gets all representations of a specific type for an artifact.
     */
    public List<SemanticRepresentation> getForArtifact(
            ArtifactKey artifactKey,
            SemanticRepresentation.PayloadType payloadType
    ) {
        SemanticRepresentationEntity.PayloadType entityType = toEntityPayloadType(payloadType);
        return repository.findByTargetArtifactKeyAndPayloadType(artifactKey.value(), entityType)
                .stream()
                .map(this::fromEntity)
                .toList();
    }
    
    /**
     * Gets the most recent embedding for an artifact.
     */
    public Optional<SemanticRepresentation> getLatestEmbedding(ArtifactKey artifactKey) {
        return repository.findLatestByTargetAndType(
                        artifactKey.value(),
                        SemanticRepresentationEntity.PayloadType.EMBEDDING
                )
                .map(this::fromEntity);
    }
    
    /**
     * Gets the most recent summary for an artifact.
     */
    public Optional<SemanticRepresentation> getLatestSummary(ArtifactKey artifactKey) {
        return repository.findLatestByTargetAndType(
                        artifactKey.value(),
                        SemanticRepresentationEntity.PayloadType.SUMMARY
                )
                .map(this::fromEntity);
    }
    
    /**
     * Gets all representations within an execution tree (by key prefix).
     */
    public List<SemanticRepresentation> getForExecution(ArtifactKey executionKey) {
        return repository.findByTargetArtifactKeyPrefix(executionKey.value())
                .stream()
                .map(this::fromEntity)
                .toList();
    }
    
    /**
     * Checks if a representation already exists for an artifact with a specific recipe.
     */
    public boolean exists(
            ArtifactKey artifactKey,
            String derivationRecipeId,
            String derivationRecipeVersion
    ) {
        return repository.existsByTargetArtifactKeyAndDerivationRecipeIdAndDerivationRecipeVersion(
                artifactKey.value(),
                derivationRecipeId,
                derivationRecipeVersion
        );
    }
    
    /**
     * Deletes all representations attached to an artifact.
     */
    @Transactional
    public void deleteForArtifact(ArtifactKey artifactKey) {
        repository.deleteByTargetArtifactKey(artifactKey.value());
        log.info("Deleted all SemanticRepresentations for artifact {}", artifactKey);
    }
    
    // ========== Private Helpers ==========
    
    private String generateSemanticKey(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
    
    private SemanticRepresentationEntity toEntity(SemanticRepresentation rep) {
        SemanticRepresentationEntity.SemanticRepresentationEntityBuilder builder = 
                SemanticRepresentationEntity.builder()
                        .semanticKey(rep.semanticKey())
                        .targetArtifactKey(rep.targetArtifactKey().value())
                        .derivationRecipeId(rep.derivationRecipeId())
                        .derivationRecipeVersion(rep.derivationRecipeVersion())
                        .modelRef(rep.modelRef())
                        .payloadType(toEntityPayloadType(rep.payloadType()));
        
        // Quality metadata as JSON
        if (rep.qualityMetadata() != null && !rep.qualityMetadata().isEmpty()) {
            try {
                builder.qualityMetadataJson(objectMapper.writeValueAsString(rep.qualityMetadata()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize quality metadata: {}", e.getMessage());
            }
        }
        
        // Handle payload based on type
        if (rep.payload() != null) {
            if (rep.payloadType() == SemanticRepresentation.PayloadType.EMBEDDING 
                    && rep.payload() instanceof float[] embedding) {
                builder.payloadBinary(floatArrayToBytes(embedding));
            } else {
                try {
                    builder.payloadJson(objectMapper.writeValueAsString(rep.payload()));
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize payload: {}", e.getMessage());
                }
            }
        }
        
        return builder.build();
    }
    
    @SuppressWarnings("unchecked")
    private SemanticRepresentation fromEntity(SemanticRepresentationEntity entity) {
        Map<String, Object> qualityMetadata = Map.of();
        if (entity.getQualityMetadataJson() != null) {
            try {
                qualityMetadata = objectMapper.readValue(entity.getQualityMetadataJson(), Map.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse quality metadata: {}", e.getMessage());
            }
        }
        
        Object payload = null;
        SemanticRepresentation.PayloadType payloadType = fromEntityPayloadType(entity.getPayloadType());
        
        if (payloadType == SemanticRepresentation.PayloadType.EMBEDDING 
                && entity.getPayloadBinary() != null) {
            payload = bytesToFloatArray(entity.getPayloadBinary());
        } else if (entity.getPayloadJson() != null) {
            try {
                payload = objectMapper.readValue(entity.getPayloadJson(), Object.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse payload: {}", e.getMessage());
            }
        }
        
        return SemanticRepresentation.builder()
                .semanticKey(entity.getSemanticKey())
                .targetArtifactKey(new ArtifactKey(entity.getTargetArtifactKey()))
                .derivationRecipeId(entity.getDerivationRecipeId())
                .derivationRecipeVersion(entity.getDerivationRecipeVersion())
                .modelRef(entity.getModelRef())
                .qualityMetadata(qualityMetadata)
                .payloadType(payloadType)
                .payload(payload)
                .build();
    }
    
    private SemanticRepresentationEntity.PayloadType toEntityPayloadType(
            SemanticRepresentation.PayloadType type
    ) {
        if (type == null) {
            return SemanticRepresentationEntity.PayloadType.CUSTOM;
        }
        return switch (type) {
            case EMBEDDING -> SemanticRepresentationEntity.PayloadType.EMBEDDING;
            case SUMMARY -> SemanticRepresentationEntity.PayloadType.SUMMARY;
            case SEMANTIC_INDEX_REF -> SemanticRepresentationEntity.PayloadType.SEMANTIC_INDEX_REF;
            case CLASSIFICATION -> SemanticRepresentationEntity.PayloadType.CLASSIFICATION;
            case NAMED_ENTITIES -> SemanticRepresentationEntity.PayloadType.NAMED_ENTITIES;
            case CUSTOM -> SemanticRepresentationEntity.PayloadType.CUSTOM;
        };
    }
    
    private SemanticRepresentation.PayloadType fromEntityPayloadType(
            SemanticRepresentationEntity.PayloadType type
    ) {
        if (type == null) {
            return SemanticRepresentation.PayloadType.CUSTOM;
        }
        return switch (type) {
            case EMBEDDING -> SemanticRepresentation.PayloadType.EMBEDDING;
            case SUMMARY -> SemanticRepresentation.PayloadType.SUMMARY;
            case SEMANTIC_INDEX_REF -> SemanticRepresentation.PayloadType.SEMANTIC_INDEX_REF;
            case CLASSIFICATION -> SemanticRepresentation.PayloadType.CLASSIFICATION;
            case NAMED_ENTITIES -> SemanticRepresentation.PayloadType.NAMED_ENTITIES;
            case CUSTOM -> SemanticRepresentation.PayloadType.CUSTOM;
        };
    }
    
    private byte[] floatArrayToBytes(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }
    
    private float[] bytesToFloatArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }
}
