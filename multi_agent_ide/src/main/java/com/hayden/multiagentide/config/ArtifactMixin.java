package com.hayden.multiagentide.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hayden.multiagentidelib.artifact.PromptTemplateVersion;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.MessageStreamArtifact;
import com.hayden.acp_cdc_ai.acp.events.RefArtifact;
import com.hayden.acp_cdc_ai.acp.events.Templated;

/**
 * Jackson mix-in for Artifact interface to enable polymorphic serialization/deserialization.
 */
public interface ArtifactMixin {
    
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "artifactType"
    )
    @JsonSubTypes({
        // Core Artifact types
        @JsonSubTypes.Type(value = Artifact.ExecutionArtifact.class, name = "Execution"),
        @JsonSubTypes.Type(value = Artifact.ExecutionConfigArtifact.class, name = "ExecutionConfig"),
        @JsonSubTypes.Type(value = Artifact.RenderedPromptArtifact.class, name = "RenderedPrompt"),
        @JsonSubTypes.Type(value = Artifact.PromptArgsArtifact.class, name = "PromptArgs"),
        @JsonSubTypes.Type(value = Artifact.ToolCallArtifact.class, name = "ToolCall"),
        @JsonSubTypes.Type(value = Artifact.ToolPrompt.class, name = "ToolPrompt"),
        @JsonSubTypes.Type(value = Artifact.OutcomeEvidenceArtifact.class, name = "OutcomeEvidence"),
        @JsonSubTypes.Type(value = Artifact.EventArtifact.class, name = "EventArtifact"),
        @JsonSubTypes.Type(value = Artifact.AgentModelArtifact.class, name = "AgentModel"),
        @JsonSubTypes.Type(value = Artifact.SchemaArtifact.class, name = "schema"),
        @JsonSubTypes.Type(value = RefArtifact.class, name = "RefArtifact"),
        @JsonSubTypes.Type(value = MessageStreamArtifact.class, name = "MessageStream"),
        // Templated types (extends Artifact)
        @JsonSubTypes.Type(value = Artifact.PromptContributionTemplate.class, name = "PromptContribution"),
        @JsonSubTypes.Type(value = PromptTemplateVersion.class, name = "PromptTemplateVersion"),
        @JsonSubTypes.Type(value = Artifact.ArtifactDbRef.class, name = "ArtifactDbRef")
    })
    interface WithTypeInfo {}
}
