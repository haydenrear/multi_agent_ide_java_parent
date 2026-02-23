package com.hayden.acp_cdc_ai.acp.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hayden.utilitymodule.schema.SpecialJsonSchemaGenerator;
import com.hayden.utilitymodule.stream.StreamUtil;
import lombok.Builder;
import lombok.With;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Base interface for all artifacts in the execution tree.
 * 
 * Artifacts are immutable nodes that capture execution state:
 * - prompts, templates, arguments
 * - tool I/O
 * - configuration
 * - outcomes
 * - captured events
 */
//TODO: maybe a polymorphic? However, the json will always be deserialized with a type in ArtifactEntity, matched over, so maybe not.
//@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "artifactType")
//@JsonSubTypes({
//    @JsonSubTypes.Type(value = Artifact.ExecutionArtifact.class, name = "Execution"),
//    @JsonSubTypes.Type(value = Artifact.ExecutionConfigArtifact.class, name = "ExecutionConfig"),
//    @JsonSubTypes.Type(value = Artifact.RenderedPromptArtifact.class, name = "RenderedPrompt"),
//    @JsonSubTypes.Type(value = Artifact.PromptArgsArtifact.class, name = "PromptArgs"),
//    @JsonSubTypes.Type(value = Artifact.PromptContributionArtifact.class, name = "PromptContribution"),
//    @JsonSubTypes.Type(value = Artifact.ToolCallArtifact.class, name = "ToolCall"),
//    @JsonSubTypes.Type(value = Artifact.OutcomeEvidenceArtifact.class, name = "OutcomeEvidence"),
//    @JsonSubTypes.Type(value = Artifact.EventArtifact.class, name = "EventArtifact"),
//    @JsonSubTypes.Type(value = Artifact.AgentRequestArtifact.class, name = "AgentRequest"),
//    @JsonSubTypes.Type(value = Artifact.AgentResultArtifact.class, name = "AgentResult"),
//    @JsonSubTypes.Type(value = Artifact.GroupArtifact.class, name = "Group"),
//    @JsonSubTypes.Type(value = RefArtifact.class, name = "RefArtifact"),
//    @JsonSubTypes.Type(value = MessageStreamArtifact.class, name = "MessageStream")
//})
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface Artifact
        permits
            Artifact.AgentModelArtifact,
            Artifact.EventArtifact,
            Artifact.ExecutionArtifact,
            Artifact.ExecutionConfigArtifact,
            Artifact.OutcomeEvidenceArtifact,
            Artifact.PromptArgsArtifact,
            Artifact.RenderedPromptArtifact,
            Artifact.ToolCallArtifact,
            MessageStreamArtifact,
            Artifact.ArtifactDbRef,
            Templated {

    String SCHEMA = "schema";

    @JsonIgnore
    default String artifactType() {
        return this.getClass().getSimpleName();
    }

    Artifact withArtifactKey(ArtifactKey key);

    default List<Artifact> collectRecursiveChildren() {
        var l = StreamUtil.toStream(this.children())
                .collect(Collectors.toCollection(ArrayList::new));
        StreamUtil.toStream(this.children())
                .flatMap(a -> StreamUtil.toStream(a.collectRecursiveChildren()))
                .forEach(l::add);
        return l;
    }

    Artifact withChildren(List<Artifact> children);

    Artifact withHash(String hash);

    /**
     * Hierarchical, time-sortable identifier.
     */
    ArtifactKey artifactKey();

    /**
     * SHA-256 hash of content bytes (if applicable).
     */
    @JsonIgnore
    Optional<String> contentHash();

    /**
     * Optional metadata map.
     */
    Map<String, String> metadata();

    /**
     * Child artifacts (tree structure).
     */
    List<Artifact> children();

    @Builder
    @With
    record AgentModelArtifact(List<Artifact> children,
                              AgentModel agentModel,
                              Map<String, String> metadata,
                              String hash) implements Artifact {

        @Override
        public ArtifactKey artifactKey() {
            return agentModel.key();
        }

        @Override
        @JsonIgnore
        public String artifactType() {
            return agentModel().artifactType();
        }

        @Override
        public Artifact withArtifactKey(ArtifactKey key) {
            return this.withAgentModel(agentModel.withContextId(key));
        }

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    interface HashContext {
        String hash(String in);

        default String hashMap(Map<String, Object> doHash) {
            return ArtifactHashing.hashMap(doHash);
        }

        static HashContext defaultHashContext() {
            return ArtifactHashing::hashText;
        }
    }

    interface AgentModel {

        String computeHash(HashContext hashContext);

        @JsonIgnore
        List<AgentModel> children();

        @JsonIgnore
        ArtifactKey key();

        @JsonIgnore
        default String artifactType() {
            return this.getClass().getSimpleName();
        }

        AgentModel withContextId(ArtifactKey key);

        @JsonIgnore
        <T extends AgentModel> T withChildren(List<AgentModel> c);

        @JsonIgnore
        default Map<String, String> metadata() {
            return new HashMap<>();
        }

        default Artifact toArtifact(HashContext hashContext) {
            var childArtifacts = children().stream()
                    .map(a -> a.toArtifact(hashContext))
                    .collect(Collectors.toCollection(ArrayList::new));

            if (childArtifacts.stream().noneMatch(a -> Objects.equals(a.artifactType(), SCHEMA))) {
                var schema = SpecialJsonSchemaGenerator.generateForType(this.getClass());
                childArtifacts.add(
                        Artifact.SchemaArtifact.builder()
                                .schema(schema)
                                .hash(hashContext.hash(schema))
                                .templateArtifactKey(key().createChild())
                                .metadata(new HashMap<>())
                                .build());
            }

            return new Artifact.AgentModelArtifact(childArtifacts, this, metadata(), computeHash(hashContext));
        }

    }

    @Builder(toBuilder = true)
    @With
    @Slf4j
    record TemplateDbRef(
            ArtifactKey templateArtifactKey,
            String templateStaticId,
            String hash,
            @JsonIgnore
            Templated ref,
            List<Artifact> children,
            Map<String, String> metadata,
            String artifactType
    ) implements Templated {

        //      This is the part that's deduped.
        @Override
        @JsonIgnore
        public String templateText() {
            return Optional.ofNullable(ref).map(Templated::templateText)
                    .orElseGet(() -> null);
        }

        @Override
        public Templated withArtifactKey(ArtifactKey key) {
            return withTemplateArtifactKey(key);
        }

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash) ;
        }

    }

    @Builder(toBuilder = true)
    @With
    record ArtifactDbRef(
            ArtifactKey artifactKey,
            String hash,
            @JsonIgnore
            Artifact ref,
            List<Artifact> children,
            Map<String, String> metadata,
            String artifactType
    ) implements Artifact {

        @JsonIgnore
        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }

    }

    @Builder(toBuilder = true)
    @With
    record SchemaArtifact(
            ArtifactKey templateArtifactKey,
            String templateStaticId,
            String hash,
            String templateText,
            Map<String, String> metadata,
            String schema
    ) implements Templated {

        @Override
        public Templated withArtifactKey(ArtifactKey key) {
            return withTemplateArtifactKey(key);
        }

        @Override
        public Artifact withChildren(List<Artifact> children) {
            return this;
        }

        @Override
        @JsonIgnore
        public ArtifactKey artifactKey() {
            return templateArtifactKey;
        }

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }

        @Override
        public List<Artifact> children() {
            return List.of();
        }
    }

    // ========== Execution Root ==========

    /**
     * Root artifact for an execution tree.
     */
    @Builder(toBuilder = true)
    @With
    record ExecutionArtifact(
            ArtifactKey artifactKey,
            String workflowRunId,
            Instant startedAt,
            Instant finishedAt,
            ExecutionStatus status,
            Map<String, String> metadata,
            List<Artifact> children,
            String hash
    ) implements Artifact {


        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    enum ExecutionStatus {
        RUNNING, COMPLETED, FAILED, STOPPED
    }

    // ========== Execution Config ==========

    /**
     * Configuration snapshot for reconstructability.
     */
    @Builder(toBuilder = true)
    @With
    record ExecutionConfigArtifact(
            ArtifactKey artifactKey,
            String repositorySnapshotId,
            Map<String, Object> modelRefs,
            Map<String, Object> toolPolicy,
            Map<String, Object> routingPolicy,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {


        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    // ========== Prompts ==========

    /**
     * Fully rendered prompt text with references to template and args.
     */
    @Builder(toBuilder = true)
    @With
    record RenderedPromptArtifact(
            ArtifactKey artifactKey,
            String renderedText,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children,
            String promptName
    ) implements Artifact {


        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    /**
     * Dynamic inputs bound into a template.
     */
    @Builder(toBuilder = true)
    @With
    record PromptArgsArtifact(
            ArtifactKey artifactKey,
            Map<String, Object> args,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {


        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    /**
     * Single prompt contributor output.
     */
    @Builder(toBuilder = true)
    @With
    record PromptContributionTemplate(
            ArtifactKey templateArtifactKey,
            String contributorName,
            int priority,
            List<String> agentTypes,
            String templateText,
            int orderIndex,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Templated {

        @Override
        @JsonIgnore
        public String templateStaticId() {
            return contributorName;
        }

        @Override
        public Artifact withArtifactKey(ArtifactKey key) {
            return this.withTemplateArtifactKey(templateArtifactKey);
        }

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }

    }

    @Builder(toBuilder = true)
    @With
    record ToolPrompt(
            ArtifactKey templateArtifactKey,
            Map<String, String> metadata,
            List<Artifact> children,
            String toolCallName,
            String toolDescription,
            String hash
    ) implements Templated {

        @Override
        public String templateStaticId() {
            return toolCallName;
        }

        @Override
        public String templateText() {
            return toolDescription;
        }

        @Override
        public Templated withArtifactKey(ArtifactKey key) {
            return withTemplateArtifactKey(key);
        }

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }

        @Override
        public ArtifactKey templateArtifactKey() {
            return templateArtifactKey;
        }
    }

    @Builder(toBuilder = true)
    @With
    record SkillPrompt(
            ArtifactKey templateArtifactKey,
            Map<String, String> metadata,
            List<Artifact> children,
            String skillName,
            String skillDescription,
            String hash,
            String activationText
    ) implements Templated {

        @Override
        public String templateStaticId() {
            return skillName;
        }

        @Override
        public String templateText() {
            return skillDescription;
        }


        @Override
        public Artifact withArtifactKey(ArtifactKey key) {
            return withTemplateArtifactKey(key);
        }

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }

        @Override
        public ArtifactKey templateArtifactKey() {
            return templateArtifactKey;
        }
    }

    // ========== Tools ==========

    /**
     * Tool invocation with input/output.
     */
    @Builder(toBuilder = true)
    @With
    record ToolCallArtifact(
            ArtifactKey artifactKey,
            String toolCallId,
            String toolName,
            String inputJson,
            String inputHash,
            String outputJson,
            String outputHash,
            String error,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {

        @Override
        public Artifact withHash(String hash) {
            return this.toBuilder()
                    .inputHash(hash)
                    .build();
        }


        @Override
        public Optional<String> contentHash() {
            // Could hash combined input+output
            return Optional.ofNullable(inputHash);
        }
    }

    // ========== Outcomes ==========

    /**
     * Objective evidence for outcomes.
     */
    @Builder(toBuilder = true)
    @With
    record OutcomeEvidenceArtifact(
            ArtifactKey artifactKey,
            String evidenceType,
            String payload,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {


        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    // ========== Events ==========

    /**
     * Captured GraphEvent as source artifact.
     */
    @Builder(toBuilder = true)
    @With
    record EventArtifact(
            ArtifactKey artifactKey,
            String eventId,
            Instant eventTimestamp,
            String eventType,
            Map<String, Object> payloadJson,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {


        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }


}
