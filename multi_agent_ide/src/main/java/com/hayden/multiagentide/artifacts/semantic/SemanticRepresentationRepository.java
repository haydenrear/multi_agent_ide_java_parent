package com.hayden.multiagentide.artifacts.semantic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for SemanticRepresentation persistence.
 */
@Repository
public interface SemanticRepresentationRepository extends JpaRepository<SemanticRepresentationEntity, Long> {
    
    /**
     * Find by unique semantic key.
     */
    Optional<SemanticRepresentationEntity> findBySemanticKey(String semanticKey);
    
    /**
     * Find all representations attached to a specific artifact.
     */
    List<SemanticRepresentationEntity> findByTargetArtifactKey(String targetArtifactKey);
    
    /**
     * Find all representations of a specific type attached to an artifact.
     */
    List<SemanticRepresentationEntity> findByTargetArtifactKeyAndPayloadType(
            String targetArtifactKey,
            SemanticRepresentationEntity.PayloadType payloadType
    );
    
    /**
     * Find representations for artifacts with a specific key prefix.
     * Useful for finding all semantics within an execution tree.
     */
    @Query("SELECT s FROM SemanticRepresentationEntity s WHERE s.targetArtifactKey LIKE :prefix%")
    List<SemanticRepresentationEntity> findByTargetArtifactKeyPrefix(@Param("prefix") String prefix);
    
    /**
     * Check if a semantic representation exists for an artifact with a specific recipe.
     */
    boolean existsByTargetArtifactKeyAndDerivationRecipeIdAndDerivationRecipeVersion(
            String targetArtifactKey,
            String derivationRecipeId,
            String derivationRecipeVersion
    );
    
    /**
     * Delete all representations attached to a specific artifact.
     */
    void deleteByTargetArtifactKey(String targetArtifactKey);
    
    /**
     * Find the most recent representation for a target artifact of a given type.
     */
    @Query("SELECT s FROM SemanticRepresentationEntity s " +
           "WHERE s.targetArtifactKey = :targetKey AND s.payloadType = :payloadType " +
           "ORDER BY s.createdTime DESC LIMIT 1")
    Optional<SemanticRepresentationEntity> findLatestByTargetAndType(
            @Param("targetKey") String targetArtifactKey,
            @Param("payloadType") SemanticRepresentationEntity.PayloadType payloadType
    );
}
