package com.hayden.multiagentide.artifacts.repository;

import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Repository for artifact persistence operations.
 */
@Repository
public interface ArtifactRepository extends JpaRepository<ArtifactEntity, Long> {
    
    /**
     * Finds an artifact by its unique key.
     */
    Optional<ArtifactEntity> findByArtifactKey(String artifactKey);
    
    /**
     * Checks if an artifact exists with the given key.
     */
    boolean existsByArtifactKey(String artifactKey);
    
    /**
     * Finds all artifacts in an execution tree.
     */
    List<ArtifactEntity> findByExecutionKeyOrderByArtifactKey(String executionKey);

    /**
     * Finds all artifacts under a key prefix (subtree).
     */
    @Query("SELECT a FROM ArtifactEntity a WHERE a.artifactKey LIKE :prefix% ORDER BY a.artifactKey")
    List<ArtifactEntity> findByKeyPrefix(@Param("prefix") String keyPrefix);
    
    /**
     * Finds artifacts by type within an execution.
     */
    List<ArtifactEntity> findByExecutionKeyAndArtifactTypeOrderByArtifactKey(
            String executionKey, String artifactType);
    
    /**
     * Finds artifacts by content hash (for deduplication).
     */
    Optional<ArtifactEntity> findByContentHash(String contentHash);
    
    /**
     * Finds shared template artifacts by static ID and content hash.
     */
    Optional<ArtifactEntity> findByTemplateStaticIdAndContentHashAndSharedTrue(
            String templateStaticId, String contentHash);
    
    /**
     * Finds all versions of a template family.
     */
    List<ArtifactEntity> findByTemplateStaticIdAndSharedTrueOrderByCreatedTimeDesc(
            String templateStaticId);
    
    /**
     * Finds all templates under a static ID prefix (template family).
     */
    @Query("SELECT a FROM ArtifactEntity a WHERE a.templateStaticId LIKE :prefix% AND a.shared = true ORDER BY a.templateStaticId, a.createdTime DESC")
    List<ArtifactEntity> findTemplatesByStaticIdPrefix(@Param("prefix") String staticIdPrefix);
    
    /**
     * Finds executions within a time range.
     */
    @Query("SELECT a.executionKey FROM ArtifactEntity a WHERE a.depth = 1 AND a.createdTime BETWEEN :start AND :end ORDER BY a.createdTime DESC")
    List<String> findExecutionKeysBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    default List<String> findExecutionKeysBetween(@Param("start") Instant start, @Param("end") Instant end) {
        return findExecutionKeysBetween(LocalDateTime.ofInstant(start, ZoneId.systemDefault()), LocalDateTime.ofInstant(end, ZoneId.systemDefault())) ;
    }

    /**
     * Finds root execution artifacts.
     */
    @Query("SELECT a FROM ArtifactEntity a WHERE a.depth = 1 AND a.artifactType = 'Execution' ORDER BY a.createdTime DESC")
    List<ArtifactEntity> findAllExecutions();

    /**
     * Finds all distinct shared template static IDs.
     */
    @Query("SELECT DISTINCT a.templateStaticId FROM ArtifactEntity a WHERE a.shared = true AND a.templateStaticId IS NOT NULL")
    List<String> findAllTemplateStaticIds();
    
    /**
     * Deletes all artifacts in an execution tree.
     */
    void deleteByExecutionKey(String executionKey);
}
