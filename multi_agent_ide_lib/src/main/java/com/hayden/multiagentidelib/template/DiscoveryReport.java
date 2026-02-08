package com.hayden.multiagentidelib.template;

import com.hayden.multiagentidelib.agent.AgentContext;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.With;

import java.util.List;
import java.util.Map;

//TODO: add report type, specific to a particular thing, such as architecture, etc.
@With
public record DiscoveryReport(
        ArtifactKey contextId,
        String schemaVersion,
        ArtifactKey resultId,
        ArtifactKey upstreamContextId,
        List<FileReference> fileReferences,
        List<CrossLink> crossLinks,
        List<SemanticTag> semanticTags,
        List<GeneratedQuery> generatedQueries,
        List<DiagramRepresentation> diagrams,
        String architectureOverview,
        List<String> keyPatterns,
        List<String> integrationPoints,
        Map<String, Double> relevanceScores,
        List<MemoryReference> memoryReferences
) implements AgentContext {

    @Override
    public String computeHash(Artifact.HashContext hashContext) {
        return hashContext.hash(prettyPrint());
    }

    @Override
    public String prettyPrint() {
        StringBuilder builder = new StringBuilder();
        if (architectureOverview != null && !architectureOverview.isBlank()) {
            builder.append("Architecture Overview:\n").append(architectureOverview.trim()).append("\n");
        }
        if (keyPatterns != null && !keyPatterns.isEmpty()) {
            builder.append("Key Patterns:\n");
            for (String pattern : keyPatterns) {
                if (pattern == null || pattern.isBlank()) {
                    continue;
                }
                builder.append("- ").append(pattern.trim()).append("\n");
            }
        }
        if (integrationPoints != null && !integrationPoints.isEmpty()) {
            builder.append("Integration Points:\n");
            for (String point : integrationPoints) {
                if (point == null || point.isBlank()) {
                    continue;
                }
                builder.append("- ").append(point.trim()).append("\n");
            }
        }
        return builder.toString().trim();
    }

    public record FileReference(
            String filePath,
            int startLine,
            int endLine,
            double relevanceScore,
            String snippet,
            List<String> tags
    ) {
    }

    public record CrossLink(
            String linkId,
            String sourceReferenceId,
            String targetReferenceId,
            LinkType linkType,
            String description
    ) {
    }

    public enum LinkType {
        CALLS,
        DEPENDS_ON,
        IMPLEMENTS,
        EXTENDS,
        RELATED_TO,
        DATA_FLOW
    }

    public record SemanticTag(
            String tagId,
            String tagName,
            TagCategory tagCategory,
            double confidence,
            List<String> appliedTo
    ) {
    }

    public enum TagCategory {
        ARCHITECTURE,
        PATTERN,
        DATA_FLOW,
        ENTRY_POINT,
        TEST,
        INTEGRATION,
        SECURITY,
        PERFORMANCE
    }

    public record GeneratedQuery(
            String queryId,
            String queryText,
            List<String> expectedResultPaths,
            QueryType queryType,
            Map<String, Double> groundTruthRelevance
    ) {
    }

    public enum QueryType {
        CODE_SEARCH,
        PATTERN_FIND,
        DEPENDENCY_TRACE,
        API_LOOKUP,
        TEST_FIND
    }

    public record DiagramRepresentation(
            String diagramId,
            DiagramType diagramType,
            String title,
            List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges,
            String mermaidSource
    ) {
    }

    public enum DiagramType {
        CLASS,
        SEQUENCE,
        COMPONENT,
        DATA_FLOW,
        DEPENDENCY
    }
}
