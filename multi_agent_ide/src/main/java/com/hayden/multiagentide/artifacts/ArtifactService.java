package com.hayden.multiagentide.artifacts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Templated;
import com.hayden.utilitymodule.stream.StreamUtil;
import io.micrometer.common.util.StringUtils;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for managing artifact persistence and retrieval.
 * Provides methods for:
 * - Hash-based artifact deduplication
 * - JSON serialization/deserialization of artifacts
 * - Artifact retrieval from the repository
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArtifactService {

    private final ArtifactRepository artifactRepository;
    private final ObjectMapper objectMapper;


    @Transactional
    public void doPersist(String executionKey, ArtifactNode root) {
        var allArtifacts = root.collectAll();
        
        // Group by content hash to find duplicates
        var groupedByHash = allArtifacts.stream()
                .map(a -> {
                    if (a.contentHash().isPresent() && StringUtils.isNotBlank(a.contentHash().get()))
                        return a;

                    return a.withHash(UUID.randomUUID().toString());
                })
                .collect(Collectors.groupingBy(a -> a.contentHash().orElseThrow()));

        var refsToSave = new ArrayList<Artifact>();
        
        for (var entry : groupedByHash.entrySet()) {
            var contentHash = entry.getKey();
            var artifacts = entry.getValue();
            if (artifacts.isEmpty()) continue;

            
            // Check if this hash already exists in the DB
            var existingOpt = artifactRepository.findByContentHash(contentHash);
            
            if (existingOpt.isPresent()) {
                // Original already in DB - decorateDuplicate all of them
                for (var artifact : artifacts) {
                    decorateDuplicate(contentHash, artifact.artifactKey())
                            .ifPresent(refsToSave::add);
                }
            } else {
                // Save the first as the original (no refs yet)
                var original = artifacts.getFirst();
                artifactRepository.save(toEntity(executionKey, original));
                
                // decorateDuplicate the rest
                for (int i = 1; i < artifacts.size(); i++) {
                    var duplicate = artifacts.get(i);
                    decorateDuplicate(contentHash, duplicate.artifactKey())
                            .ifPresent(refsToSave::add);
                }
            }
        }
        
        // Save all refs
        var savedRefs = artifactRepository.saveAll(
                refsToSave.stream()
                        .map(a -> toEntity(executionKey, a))
                        .toList());
        
        // Update the original artifacts with their ref keys
        for (var refEntity : savedRefs) {
            if (refEntity.getReferencedArtifactKey() != null) {
                artifactRepository.findByArtifactKey(refEntity.getReferencedArtifactKey())
                        .ifPresentOrElse(ae -> {
                            try {
                                ae.addRef(refEntity.getArtifactKey());
                                artifactRepository.save(ae);
                            } catch (PersistenceException p) {
                                log.error("Error adding referenced artifact key {}.", ae.getArtifactKey(), p);
                            }
                        }, () -> {
                            log.error("Could not find referenced artifact key {}.", refEntity.getReferencedArtifactKey());
                        });
            }
        }
    }


    @Transactional(readOnly = true)
    public Optional<Artifact> decorateDuplicate(String contentHash, @NotNull ArtifactKey artifact) {
        if (contentHash == null || contentHash.isEmpty()) {
            return Optional.empty();
        }

        return artifactRepository.findByContentHash(contentHash)
                .flatMap(this::deserializeArtifact)
                .map( a -> switch(a) {
                    case Templated t ->
//                          use a random hash for this one as it's a ref
                            new Artifact.TemplateDbRef(artifact, t.templateStaticId(), UUID.randomUUID().toString(), t, new ArrayList<>(t.children()), new HashMap<>(t.metadata()), t.artifactType());
                    case Artifact t ->
//                          use a random hash for this one as it's a ref
                            new Artifact.ArtifactDbRef(artifact, UUID.randomUUID().toString(), t, new ArrayList<>(t.children()), new HashMap<>(t.metadata()), t.artifactType());
                });
    }

    /**
     * Deserializes an ArtifactEntity's JSON content back to an Artifact instance.
     *
     * @param entity The ArtifactEntity containing JSON content
     * @return Optional containing the deserialized Artifact, or empty if deserialization fails
     */
    public Optional<Artifact> deserializeArtifact(ArtifactEntity entity) {
        if (entity == null || entity.getContentJson() == null) {
            return Optional.empty();
        }

        try {
            // Deserialize using the artifact type as a hint
            Artifact artifact = objectMapper.readValue(entity.getContentJson(), Artifact.class);

            artifact = switch(artifact) {
                case Artifact.TemplateDbRef t ->
                        this.artifactRepository.findByContentHash(t.hash())
                                .flatMap(this::deserializeArtifact)
                                .map(ae -> {
                                    if (ae instanceof Templated templated)
                                        return t.toBuilder()
                                                .ref(templated)
                                                .build();

                                    log.error("Found artifact incompateible with templated {}.", t);

                                    return t;
                                })
                                .orElseGet(() -> {
                                    log.error("Could not find referenced artifact in repository!");
                                    return t;
                                });
                case Artifact.ArtifactDbRef t ->
                        this.artifactRepository.findByContentHash(t.hash())
                                .flatMap(this::deserializeArtifact)
                                .map(ae -> t.toBuilder()
                                        .ref(ae)
                                        .build())
                                .orElseGet(() -> {
                                    log.error("Could not find referenced artifact in repository!");
                                    return t;
                                });
                default -> artifact;
            };
            log.debug("Successfully deserialized artifact: {} of type: {}",
                    entity.getArtifactKey(), entity.getArtifactType());
            return Optional.of(artifact);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize artifact: {} of type: {}", 
                    entity.getArtifactKey(), entity.getArtifactType(), e);
            return Optional.empty();
        }
    }


    /**
     * Saves an artifact entity to the repository.
     * This is a pass-through method that can be used for consistency.
     *
     * @param entity The artifact entity to save
     * @return The saved entity
     */
    @Transactional
    public ArtifactEntity save(ArtifactEntity entity) {
        return artifactRepository.save(entity);
    }

    ArtifactEntity toEntity(String executionKey, Artifact artifact) {
        ArtifactKey key = artifact.artifactKey();

        String contentJson;
        try {
            contentJson = objectMapper.writeValueAsString(artifact);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize artifact: {}", key, e);
            contentJson = "{}";
        }

        String parentKey = key.parent().map(ArtifactKey::value).orElse(null);

        var t = artifact instanceof Templated temp ? temp : null;

        var tDb = artifact instanceof Artifact.TemplateDbRef temp ? temp : null;
        var aDb = artifact instanceof Artifact.ArtifactDbRef temp ? temp : null;

        return ArtifactEntity.builder()
                .artifactKey(key.value())
                .referencedArtifactKey(
                        Optional.ofNullable(tDb)
                                .map(td -> td.ref().templateArtifactKey().value())
                                .or(() -> Optional.ofNullable(aDb).map(td -> td.ref().artifactKey().value()))
                                .orElse(null))
                .templateStaticId(Optional.ofNullable(t).map(Templated::templateStaticId).orElse(null))
                .parentKey(parentKey)
                .executionKey(executionKey)
                .artifactType(artifact.artifactType())
                .contentHash(artifact.contentHash().orElse(null))
                .contentJson(contentJson)
                .depth(key.depth())
                .shared(false)
                .childIds(
                        StreamUtil.toStream(artifact.children()).flatMap(a -> Stream.ofNullable(a.artifactKey()))
                                .flatMap(ak -> StreamUtil.toStream(ak.value()))
                                .collect(Collectors.toCollection(ArrayList::new)))
                .build();
    }

}
