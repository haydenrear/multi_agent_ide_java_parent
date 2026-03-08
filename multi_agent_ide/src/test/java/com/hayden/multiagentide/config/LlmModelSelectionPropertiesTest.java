package com.hayden.multiagentide.config;

import com.hayden.multiagentidelib.agent.AgentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmModelSelectionPropertiesTest {

    private LlmModelSelectionProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LlmModelSelectionProperties();
        properties.setDefaultModel("sonnet");
        properties.setByAgentType(new java.util.HashMap<>(Map.of(
                "commit-agent", "haiku",
                "merge-conflict-agent", "haiku",
                "ai-filter", "haiku"
        )));
        properties.setByTemplate(new java.util.HashMap<>(Map.of(
                "filter/ai_filter", "haiku-special"
        )));
    }

    @Test
    @DisplayName("resolves by template name with highest priority")
    void resolvesByTemplate() {
        String model = properties.resolve(AgentType.AI_FILTER, "filter/ai_filter");
        assertThat(model).isEqualTo("haiku-special");
    }

    @Test
    @DisplayName("resolves by agent type when no template match")
    void resolvesByAgentType() {
        String model = properties.resolve(AgentType.COMMIT_AGENT, "workflow/worktree_commit_agent");
        assertThat(model).isEqualTo("haiku");
    }

    @Test
    @DisplayName("resolves merge conflict agent to haiku")
    void resolvesMergeConflictAgent() {
        String model = properties.resolve(AgentType.MERGE_CONFLICT_AGENT, "workflow/worktree_merge_conflict_agent");
        assertThat(model).isEqualTo("haiku");
    }

    @Test
    @DisplayName("falls back to default model when no specific match")
    void fallsBackToDefault() {
        String model = properties.resolve(AgentType.ORCHESTRATOR, "workflow/orchestrator");
        assertThat(model).isEqualTo("sonnet");
    }

    @Test
    @DisplayName("returns null when no default and no match")
    void returnsNullWhenNoDefault() {
        properties.setDefaultModel(null);
        properties.setByAgentType(new java.util.HashMap<>());
        properties.setByTemplate(new java.util.HashMap<>());

        String model = properties.resolve(AgentType.ORCHESTRATOR, "workflow/orchestrator");
        assertThat(model).isNull();
    }

    @Test
    @DisplayName("handles null agent type gracefully")
    void handlesNullAgentType() {
        String model = properties.resolve(null, "workflow/orchestrator");
        assertThat(model).isEqualTo("sonnet");
    }

    @Test
    @DisplayName("handles null template name gracefully")
    void handlesNullTemplateName() {
        String model = properties.resolve(AgentType.COMMIT_AGENT, null);
        assertThat(model).isEqualTo("haiku");
    }

    @Test
    @DisplayName("handles both null gracefully")
    void handlesBothNull() {
        String model = properties.resolve(null, null);
        assertThat(model).isEqualTo("sonnet");
    }

    @Test
    @DisplayName("template match takes priority over agent type match")
    void templatePriorityOverAgentType() {
        properties.getByTemplate().put("workflow/special", "opus");
        String model = properties.resolve(AgentType.COMMIT_AGENT, "workflow/special");
        assertThat(model).isEqualTo("opus");
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
            assertThat(properties.resolve(type, null))
                    .as("AgentType %s should resolve to sonnet", type)
                    .isEqualTo("sonnet");
        }
    }
}
