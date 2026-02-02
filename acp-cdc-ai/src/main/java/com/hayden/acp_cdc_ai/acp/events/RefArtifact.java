package com.hayden.acp_cdc_ai.acp.events;

import lombok.Builder;
import lombok.With;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Explicit reference to another artifact for dependency modeling.
 * 
 * Used when the dependency is not represented by containment:
 * - References to shared artifacts (e.g., PromptTemplateVersion)
 * - Cross-tree references within an execution
 */
@Builder(toBuilder = true)
@With
public record RefArtifact(
        ArtifactKey artifactKey,
        ArtifactKey targetArtifactKey,
        String relationType,
        Map<String, String> metadata,
        List<Artifact> children
) implements Artifact {
    
    /**
     * Relation types for reference artifacts.
     */
    public static final String USES_TEMPLATE = "uses-template";
    public static final String DEPENDS_ON = "depends-on";
    public static final String REFERENCES = "references";
    public static final String DERIVED_FROM = "derived-from";

    @Override
    public Artifact withHash(String hash) {
        return this;
    }

    @Override
    public String artifactType() {
        return "RefArtifact";
    }
    
    @Override
    public Optional<String> contentHash() {
        return Optional.empty();
    }
    
    /**
     * Creates a template reference artifact.
     */
    public static RefArtifact templateRef(ArtifactKey key, ArtifactKey templateKey) {
        return RefArtifact.builder()
                .artifactKey(key)
                .targetArtifactKey(templateKey)
                .relationType(USES_TEMPLATE)
                .metadata(Map.of())
                .children(List.of())
                .build();
    }
    
    /**
     * Creates a dependency reference artifact.
     */
    public static RefArtifact dependsOn(ArtifactKey key, ArtifactKey targetKey) {
        return RefArtifact.builder()
                .artifactKey(key)
                .targetArtifactKey(targetKey)
                .relationType(DEPENDS_ON)
                .metadata(Map.of())
                .children(List.of())
                .build();
    }
}
