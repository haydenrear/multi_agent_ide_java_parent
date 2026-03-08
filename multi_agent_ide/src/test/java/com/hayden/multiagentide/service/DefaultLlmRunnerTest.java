package com.hayden.multiagentide.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.config.AcpChatOptionsString;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.prompt.PromptContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"test", "testdocker"})
class DefaultLlmRunnerTest {

    @Autowired
    private DefaultLlmRunner defaultLlmRunner;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("encodes session and requested model into ACP chat options")
    void encodesSessionAndRequestedModel() {
        ArtifactKey sessionKey = ArtifactKey.createRoot();
        PromptContext promptContext = PromptContext.builder()
                .agentType(AgentType.ORCHESTRATOR)
                .currentContextId(sessionKey)
                .currentRequest(new AgentModels.OrchestratorRequest(sessionKey, "Goal", "DISCOVERY"))
                .promptContributors(java.util.List.of())
                .metadata(Map.of())
                .modelName("gpt-oss:120b-cloud")
                .build();

        String encoded = defaultLlmRunner.resolveEncodedAcpOptions(promptContext);
        AcpChatOptionsString decoded = AcpChatOptionsString.fromEncodedModel(encoded, objectMapper);

        assertThat(decoded.sessionArtifactKey()).isEqualTo(promptContext.chatId().value());
        assertThat(decoded.requestedModel()).isEqualTo("gpt-oss:120b-cloud");
        assertThat(decoded.requestedProvider()).isNull();
    }

    @Test
    @DisplayName("includes provider metadata and runtime options in ACP chat options")
    void includesProviderMetadataAndRuntimeOptions() {
        ArtifactKey sessionKey = ArtifactKey.createRoot();
        PromptContext promptContext = PromptContext.builder()
                .agentType(AgentType.ORCHESTRATOR)
                .currentContextId(sessionKey)
                .currentRequest(new AgentModels.OrchestratorRequest(sessionKey, "Goal", "DISCOVERY"))
                .promptContributors(java.util.List.of())
                .metadata(Map.of(
                        "acpProvider", "claude-openrouter",
                        "acpOptions", Map.of("mode", "strict")
                ))
                .modelName("openrouter/free")
                .build();

        String encoded = defaultLlmRunner.resolveEncodedAcpOptions(promptContext);
        AcpChatOptionsString decoded = AcpChatOptionsString.fromEncodedModel(encoded, objectMapper);

        assertThat(decoded.requestedProvider()).isEqualTo("claude-openrouter");
        assertThat(decoded.options()).containsEntry("mode", "strict");
    }

    @Test
    @DisplayName("DEFAULT modelName resolves to config default model from model-selection properties")
    void defaultModelNameResolvesFromConfig() {
        ArtifactKey sessionKey = ArtifactKey.createRoot();
        PromptContext promptContext = PromptContext.builder()
                .agentType(AgentType.ORCHESTRATOR)
                .currentContextId(sessionKey)
                .currentRequest(new AgentModels.OrchestratorRequest(sessionKey, "Goal", "DISCOVERY"))
                .promptContributors(java.util.List.of())
                .metadata(Map.of())
                .modelName(AcpChatOptionsString.DEFAULT_MODEL_NAME)
                .build();

        String encoded = defaultLlmRunner.resolveEncodedAcpOptions(promptContext);
        AcpChatOptionsString decoded = AcpChatOptionsString.fromEncodedModel(encoded, objectMapper);

        // application.yml sets default-model: sonnet
        assertThat(decoded.requestedModel()).isEqualTo("sonnet");
    }

    @Test
    @DisplayName("commit agent type resolves to haiku from config")
    void commitAgentResolvesToHaikuFromConfig() {
        ArtifactKey sessionKey = ArtifactKey.createRoot();
        ArtifactKey childKey = sessionKey.createChild();
        PromptContext promptContext = PromptContext.builder()
                .agentType(AgentType.COMMIT_AGENT)
                .currentContextId(childKey)
                .currentRequest(AgentModels.CommitAgentRequest.builder().contextId(childKey).build())
                .promptContributors(java.util.List.of())
                .metadata(Map.of())
                .modelName(AcpChatOptionsString.DEFAULT_MODEL_NAME)
                .build();

        String encoded = defaultLlmRunner.resolveEncodedAcpOptions(promptContext);
        AcpChatOptionsString decoded = AcpChatOptionsString.fromEncodedModel(encoded, objectMapper);

        assertThat(decoded.requestedModel()).isEqualTo("haiku");
    }

    @Test
    @DisplayName("merge conflict agent type resolves to haiku from config")
    void mergeConflictAgentResolvesToHaikuFromConfig() {
        ArtifactKey sessionKey = ArtifactKey.createRoot();
        ArtifactKey childKey = sessionKey.createChild();
        PromptContext promptContext = PromptContext.builder()
                .agentType(AgentType.MERGE_CONFLICT_AGENT)
                .currentContextId(childKey)
                .currentRequest(AgentModels.MergeConflictRequest.builder().contextId(childKey).build())
                .promptContributors(java.util.List.of())
                .metadata(Map.of())
                .modelName(AcpChatOptionsString.DEFAULT_MODEL_NAME)
                .build();

        String encoded = defaultLlmRunner.resolveEncodedAcpOptions(promptContext);
        AcpChatOptionsString decoded = AcpChatOptionsString.fromEncodedModel(encoded, objectMapper);

        assertThat(decoded.requestedModel()).isEqualTo("haiku");
    }

    @Test
    @DisplayName("AI filter agent type resolves to haiku from config")
    void aiFilterAgentResolvesToHaikuFromConfig() {
        ArtifactKey sessionKey = ArtifactKey.createRoot();
        PromptContext promptContext = PromptContext.builder()
                .agentType(AgentType.AI_FILTER)
                .currentContextId(sessionKey)
                .currentRequest(AgentModels.AiFilterRequest.builder().contextId(sessionKey).build())
                .promptContributors(java.util.List.of())
                .metadata(Map.of())
                .modelName(AcpChatOptionsString.DEFAULT_MODEL_NAME)
                .build();

        String encoded = defaultLlmRunner.resolveEncodedAcpOptions(promptContext);
        AcpChatOptionsString decoded = AcpChatOptionsString.fromEncodedModel(encoded, objectMapper);

        assertThat(decoded.requestedModel()).isEqualTo("haiku");
    }

    @Test
    @DisplayName("explicit modelName takes priority over config")
    void explicitModelNameOverridesConfig() {
        ArtifactKey sessionKey = ArtifactKey.createRoot();
        ArtifactKey childKey = sessionKey.createChild();
        PromptContext promptContext = PromptContext.builder()
                .agentType(AgentType.COMMIT_AGENT)
                .currentContextId(childKey)
                .currentRequest(AgentModels.CommitAgentRequest.builder().contextId(childKey).build())
                .promptContributors(java.util.List.of())
                .metadata(Map.of())
                .modelName("opus")
                .build();

        String encoded = defaultLlmRunner.resolveEncodedAcpOptions(promptContext);
        AcpChatOptionsString decoded = AcpChatOptionsString.fromEncodedModel(encoded, objectMapper);

        // explicit "opus" should override the config "haiku" for commit agents
        assertThat(decoded.requestedModel()).isEqualTo("opus");
    }

    @Test
    @DisplayName("workflow agent types resolve to sonnet (default) from config")
    void workflowAgentsResolveToSonnet() {
        for (AgentType agentType : new AgentType[]{
                AgentType.ORCHESTRATOR,
                AgentType.DISCOVERY_AGENT,
                AgentType.PLANNING_AGENT,
                AgentType.TICKET_AGENT
        }) {
            ArtifactKey sessionKey = ArtifactKey.createRoot();
            PromptContext promptContext = PromptContext.builder()
                    .agentType(agentType)
                    .currentContextId(sessionKey)
                    .currentRequest(new AgentModels.OrchestratorRequest(sessionKey, "Goal", "DISCOVERY"))
                    .promptContributors(java.util.List.of())
                    .metadata(Map.of())
                    .modelName(AcpChatOptionsString.DEFAULT_MODEL_NAME)
                    .build();

            String encoded = defaultLlmRunner.resolveEncodedAcpOptions(promptContext);
            AcpChatOptionsString decoded = AcpChatOptionsString.fromEncodedModel(encoded, objectMapper);

            assertThat(decoded.requestedModel())
                    .as("AgentType %s should resolve to sonnet", agentType)
                    .isEqualTo("sonnet");
        }
    }
}
