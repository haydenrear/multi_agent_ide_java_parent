package com.hayden.multiagentide.artifacts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Templated;
import com.hayden.multiagentide.config.SerdesConfiguration;
import com.hayden.utilitymodule.stream.StreamUtil;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
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

    private ObjectMapper objectMapper;

    @PostConstruct
    public void configure() {
        var j = new Jackson2ObjectMapperBuilder();
        new SerdesConfiguration().artifactAndAgentModelMixIn().customize(j);

        this.objectMapper = j.build();
    }


    @Transactional
    public void doPersist(String executionKey, ArtifactNode root) {
        var collected = new ArrayList<>(root.collectAll());

        var groupedByKey = collected.stream()
                .filter(a -> a.artifactKey() != null && StringUtils.isNotBlank(a.artifactKey().value()))
                .collect(Collectors.groupingBy(Artifact::artifactKey));

        // records are special
        Map<Artifact, Artifact> updates = new IdentityHashMap<>();

        for (var entry : groupedByKey.entrySet()) {
            var list = entry.getValue();
            updates.put(list.getFirst(), list.getFirst());

            if (list.size() <= 1)
                continue;

            // decide allSameHash using “present+nonblank” logic
            if (!entry.getValue().stream().allMatch(art -> art.contentHash().orElse("").equals(list.getFirst().contentHash().orElse("")))) {
                for (int i = 1; i < list.size(); i++) {
                    var e = list.get(i);
                    updates.put(e, e.withArtifactKey(e.artifactKey().createChild()));
                }
            }
        }

        var allArtifacts = new ArrayList<>(updates.values());

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
            if (artifacts.isEmpty())
                continue;


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
                ArtifactEntity entity = toEntity(executionKey, original);
                artifactRepository.save(entity);

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
                try {
                    artifactRepository.findByArtifactKey(refEntity.getReferencedArtifactKey())
                            .ifPresentOrElse(ae -> {
                                ae.addRef(refEntity.getArtifactKey());
                                artifactRepository.save(ae);
                            }, () -> {
                                log.error("Could not find referenced artifact key {}.", refEntity.getReferencedArtifactKey());
                            });
                } catch (
                        PersistenceException |
                        DataIntegrityViolationException p) {
                    log.error("Error adding referenced artifact key {}.", refEntity.getReferencedArtifactKey(), p);
                }
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
                .flatMap(a -> switch (a) {
                    case Templated t -> {
//                      use a random hash for this one as it's a ref
                        if (!a.artifactKey().equals(artifact)) {
                            yield Optional.of(new Artifact.TemplateDbRef(artifact, t.templateStaticId(), UUID.randomUUID().toString(), t, new ArrayList<>(StreamUtil.toStream(t.children()).toList()), new HashMap<>(t.metadata()), t.artifactType()));
                        }

                        yield Optional.of(new Artifact.TemplateDbRef(artifact.createChild(), t.templateStaticId(), UUID.randomUUID().toString(), t, new ArrayList<>(StreamUtil.toStream(t.children()).toList()), new HashMap<>(t.metadata()), t.artifactType()));
                    }
                    case Artifact t ->{
//                      use a random hash for this one as it's a ref
                        if (!t.artifactKey().equals(artifact)) {
                            yield Optional.of(new Artifact.ArtifactDbRef(artifact, UUID.randomUUID().toString(), t, new ArrayList<>(StreamUtil.toStream(t.children()).toList()), new HashMap<>(t.metadata()), t.artifactType()));
                        }

                        yield Optional.of(new Artifact.ArtifactDbRef(artifact.createChild(), UUID.randomUUID().toString(), t, new ArrayList<>(StreamUtil.toStream(t.children()).toList()), new HashMap<>(t.metadata()), t.artifactType()));
                    }
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

            artifact = switch (artifact) {
                case Artifact.TemplateDbRef t ->
                        this.artifactRepository.findByArtifactKey(entity.getReferencedArtifactKey())
                                .flatMap(this::deserializeArtifact)
                                .map(ae -> {
                                    if (ae instanceof Templated templated)
                                        return t.toBuilder()
                                                .ref(templated)
                                                .artifactType(Artifact.TemplateDbRef.class.getSimpleName())
                                                .build();

                                    log.error("Found artifact incompateible with templated {}.", t);

                                    return t;
                                })
                                .orElseGet(() -> {
                                    log.error("Could not find referenced artifact in repository!");
                                    return t;
                                });
                case Artifact.ArtifactDbRef t ->
                        this.artifactRepository.findByArtifactKey(entity.getReferencedArtifactKey())
                                .flatMap(this::deserializeArtifact)
                                .map(ae -> t.toBuilder()
                                        .ref(ae)
                                        .artifactType(Artifact.ArtifactDbRef.class.getSimpleName())
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

        if (tDb != null && tDb.ref() == null) {
            throw new IllegalArgumentException("Ref was null for TemplateDbRef provided.");
        }
        if (aDb != null && aDb.ref() == null) {
            throw new IllegalArgumentException("Ref was null for ArtifactDbRef provided.");
        }

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
