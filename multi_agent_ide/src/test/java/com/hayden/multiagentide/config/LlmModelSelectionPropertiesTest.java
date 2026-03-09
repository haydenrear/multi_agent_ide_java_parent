package com.hayden.multiagentide.config;

import com.hayden.acp_cdc_ai.acp.config.AcpProvider;
import com.hayden.multiagentidelib.agent.AgentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

class LlmModelSelectionPropertiesTest {

    private LlmModelSelectionProperties properties;

    private static LlmModelSelectionProperties.ModelEntry entry(String model, AcpProvider provider) {
        var e = new LlmModelSelectionProperties.ModelEntry();
        e.setModel(model);
        e.setProvider(provider);
        return e;
    }

    @BeforeEach
    void setUp() {
        properties = new LlmModelSelectionProperties();
        properties.setDefaultModel(entry("sonnet", AcpProvider.CLAUDE));

        var byAgentType = new HashMap<String, LlmModelSelectionProperties.ModelEntry>();
        byAgentType.put("commit-agent", entry("haiku", AcpProvider.CLAUDE));
        byAgentType.put("merge-conflict-agent", entry("haiku", AcpProvider.CLAUDE));
        byAgentType.put("ai-filter", entry("haiku", AcpProvider.CLAUDE));
        properties.setByAgentType(byAgentType);

        var byTemplate = new HashMap<String, LlmModelSelectionProperties.ModelEntry>();
        byTemplate.put("filter/ai_filter", entry("haiku-special", AcpProvider.CLAUDE));
        properties.setByTemplate(byTemplate);
    }

    @Test
    @DisplayName("resolves by template name with highest priority")
    void resolvesByTemplate() {
        var resolved = properties.resolve(AgentType.AI_FILTER, "filter/ai_filter");
        assertThat(resolved.model()).isEqualTo("haiku-special");
        assertThat(resolved.provider()).isEqualTo(AcpProvider.CLAUDE);
    }

    @Test
    @DisplayName("resolves by agent type when no template match")
    void resolvesByAgentType() {
        var resolved = properties.resolve(AgentType.COMMIT_AGENT, "workflow/worktree_commit_agent");
        assertThat(resolved.model()).isEqualTo("haiku");
        assertThat(resolved.provider()).isEqualTo(AcpProvider.CLAUDE);
    }

    @Test
    @DisplayName("resolves merge conflict agent to haiku")
    void resolvesMergeConflictAgent() {
        var resolved = properties.resolve(AgentType.MERGE_CONFLICT_AGENT, "workflow/worktree_merge_conflict_agent");
        assertThat(resolved.model()).isEqualTo("haiku");
    }

    @Test
    @DisplayName("falls back to default model when no specific match")
    void fallsBackToDefault() {
        var resolved = properties.resolve(AgentType.ORCHESTRATOR, "workflow/orchestrator");
        assertThat(resolved.model()).isEqualTo("sonnet");
        assertThat(resolved.provider()).isEqualTo(AcpProvider.CLAUDE);
    }

    @Test
    @DisplayName("returns nulls when no default and no match")
    void returnsNullWhenNoDefault() {
        properties.setDefaultModel(null);
        properties.setByAgentType(new HashMap<>());
        properties.setByTemplate(new HashMap<>());

        var resolved = properties.resolve(AgentType.ORCHESTRATOR, "workflow/orchestrator");
        assertThat(resolved.model()).isNull();
        assertThat(resolved.provider()).isNull();
    }

    @Test
    @DisplayName("handles null agent type gracefully")
    void handlesNullAgentType() {
        var resolved = properties.resolve(null, "workflow/orchestrator");
        assertThat(resolved.model()).isEqualTo("sonnet");
    }

    @Test
    @DisplayName("handles null template name gracefully")
    void handlesNullTemplateName() {
        var resolved = properties.resolve(AgentType.COMMIT_AGENT, null);
        assertThat(resolved.model()).isEqualTo("haiku");
    }

    @Test
    @DisplayName("handles both null gracefully")
    void handlesBothNull() {
        var resolved = properties.resolve(null, null);
        assertThat(resolved.model()).isEqualTo("sonnet");
    }

    @Test
    @DisplayName("template match takes priority over agent type match")
    void templatePriorityOverAgentType() {
        properties.getByTemplate().put("workflow/special", entry("opus", AcpProvider.CLAUDE));
        var resolved = properties.resolve(AgentType.COMMIT_AGENT, "workflow/special");
        assertThat(resolved.model()).isEqualTo("opus");
    }

    @Test
    @DisplayName("all workflow agent types fall through to default sonnet")
    void workflowAgentsFallToDefault() {
        for (AgentType type : new AgentType[]{
                AgentType.ORCHESTRATOR,
                AgentType.DISCOVERY_AGENT,
                AgentType.PLANNING_AGENT,
                AgentType.TICKET_AGENT,
                AgentType.CONTEXT_MANAGER
        }) {
            var resolved = properties.resolve(type, null);
            assertThat(resolved.model())
                    .as("AgentType %s should resolve to sonnet", type)
                    .isEqualTo("sonnet");
        }
    }

    @Test
    @DisplayName("providerWireValue returns correct wire value")
    void providerWireValue() {
        var resolved = properties.resolve(AgentType.ORCHESTRATOR, null);
        assertThat(resolved.providerWireValue()).isEqualTo("claude");
    }

    @Test
    @DisplayName("providerWireValue returns null when provider is null")
    void providerWireValueNull() {
        properties.setDefaultModel(entry("sonnet", null));
        var resolved = properties.resolve(AgentType.ORCHESTRATOR, null);
        assertThat(resolved.providerWireValue()).isNull();
    }
}
