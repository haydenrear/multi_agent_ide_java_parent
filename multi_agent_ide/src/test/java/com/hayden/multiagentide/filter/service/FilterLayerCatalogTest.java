package com.hayden.multiagentide.filter.service;

import com.hayden.multiagentide.agent.AgentInterfaces;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilterLayerCatalogTest {

    @Test
    void canonicalActionNamePrefersWorkflowMethodName() {
        assertThat(FilterLayerCatalog.canonicalActionName(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_ORCHESTRATOR,
                AgentInterfaces.METHOD_COORDINATE_WORKFLOW
        )).isEqualTo(AgentInterfaces.METHOD_COORDINATE_WORKFLOW);
    }

    @Test
    void canonicalActionNameMapsServiceAliasesToCatalogMethodNames() {
        assertThat(FilterLayerCatalog.canonicalActionName(
                FilterLayerCatalog.WORKTREE_AUTO_COMMIT,
                "commit-agent",
                "runCommitAgent"
        )).isEqualTo("runCommitAgent");

        assertThat(FilterLayerCatalog.canonicalActionName(
                FilterLayerCatalog.WORKTREE_MERGE_CONFLICT,
                "merge-conflict-agent",
                "runMergeConflictAgent"
        )).isEqualTo("runMergeConflictAgent");

        assertThat(FilterLayerCatalog.canonicalActionName(
                FilterLayerCatalog.AI_FILTER,
                "path-filter",
                "runAiFilter"
        )).isEqualTo("runAiFilter");
    }

    @Test
    void resolveActionLayerSupportsLegacyAliasAndCanonicalMethod() {
        assertThat(FilterLayerCatalog.resolveActionLayer(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_ORCHESTRATOR,
                null
        )).contains(FilterLayerCatalog.WORKFLOW_AGENT + "/" + AgentInterfaces.METHOD_COORDINATE_WORKFLOW);

        assertThat(FilterLayerCatalog.resolveActionLayer(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                null,
                AgentInterfaces.METHOD_COORDINATE_WORKFLOW
        )).contains(FilterLayerCatalog.WORKFLOW_AGENT + "/" + AgentInterfaces.METHOD_COORDINATE_WORKFLOW);
    }
}
