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
import org.springframework.test.util.ReflectionTestUtils;

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

        String encoded = ReflectionTestUtils.invokeMethod(defaultLlmRunner, "resolveEncodedAcpOptions", promptContext);
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

        String encoded = ReflectionTestUtils.invokeMethod(defaultLlmRunner, "resolveEncodedAcpOptions", promptContext);
        AcpChatOptionsString decoded = AcpChatOptionsString.fromEncodedModel(encoded, objectMapper);

        assertThat(decoded.requestedProvider()).isEqualTo("claude-openrouter");
        assertThat(decoded.options()).containsEntry("mode", "strict");
    }
}
