package com.hayden.multiagentide.artifacts.entity;

import com.hayden.persistence.models.JpaHibernateAuditedIded;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity for persisted artifacts.
 * 
 * Stores the artifact tree in a relational format:
 * - artifactKey is the hierarchical identifier (indexed for prefix queries)
 * - parentKey enables tree traversal
 * - contentJson stores the full artifact payload
 * - contentHash enables deduplication and integrity checks
 */
@Entity
@Table(name = "artifact", indexes = {
    @Index(name = "idx_artifact_key", columnList = "artifactKey"),
    @Index(name = "idx_parent_key", columnList = "parentKey"),
    @Index(name = "idx_execution_key", columnList = "executionKey"),
    @Index(name = "idx_artifact_type", columnList = "artifactType"),
    @Index(name = "idx_content_hash", columnList = "contentHash")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ArtifactEntity extends JpaHibernateAuditedIded {
    
    /**
     * Hierarchical artifact key (ak:ulid/ulid/...).
     */
    @Column(nullable = false, unique = true, length = 30_000)
    private String artifactKey;
    
    /**
     * Parent artifact key (null for root).
     */
    @Column(length = 30_000)
    private String parentKey;
    
    /**
     * Root execution key for this artifact tree.
     */
    @Column(nullable = false, length = 30_000)
    private String executionKey;
    
    /**
     * Artifact type discriminator.
     */
    @Column(nullable = false, length = 30_000)
    private String artifactType;

    @Column(length = 30_000)
    private String referencedArtifactKey;

    /**
     * SHA-256 content hash (lowercase hex, 64 chars).
     */
    @Column(length = 30_000, unique=true)
    private String contentHash;
    
    /**
     * Full artifact payload as canonical JSON.
     */
    @Column(columnDefinition = "TEXT")
    private String contentJson;

    /**
     * Depth in the artifact tree (1 = root).
     */
    @Column(nullable = false)
    private Integer depth;
    
    /**
     * For template artifacts: the stable template static ID.
     */
    @Column(length = 30_000)
    private String templateStaticId;
    
    /**
     * Whether this is a shared artifact (e.g., template version).
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean shared = false;

    @Column(nullable = false)
    @Builder.Default
    private String schemaVersion = "0.0.1";

    @ElementCollection
    @CollectionTable(
            name = "artifact_child_ids",
            joinColumns = @JoinColumn(name = "artifact_id", nullable = false)
    )
    @Column(name = "child_id", nullable = false, length = 30_000)
    @OrderColumn(name = "child_order") // only if order matters
    @Builder.Default
    private List<String> childIds = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "artifact_refs",
            joinColumns = @JoinColumn(name = "artifact_id", nullable = false)
    )
    @Column(name = "ref", nullable = false, length = 512)
    @OrderColumn(name = "ref_order") // optional
    @Builder.Default
    private List<String> artifactKeyRefs = new ArrayList<>();


    public void addRef(String ref) {
        if (artifactKeyRefs == null)
            artifactKeyRefs = new ArrayList<>();

        artifactKeyRefs.add(ref);
    }

    public void addRef(ArtifactKey ref) {
        addRef(ref.value());
    }

}
