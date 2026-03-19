package com.hayden.acp_cdc_ai.acp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.config.AcpChatOptionsString;
import com.hayden.acp_cdc_ai.acp.config.AcpModelProperties;
import com.hayden.acp_cdc_ai.acp.config.AcpProvider;
import com.hayden.acp_cdc_ai.acp.config.AcpProviderDefinition;
import com.hayden.acp_cdc_ai.acp.config.McpProperties;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.acp_cdc_ai.repository.RequestContext;
import com.hayden.acp_cdc_ai.repository.RequestContextRepository;
import com.hayden.acp_cdc_ai.sandbox.SandboxContext;
import com.hayden.acp_cdc_ai.sandbox.SandboxTranslation;
import com.hayden.acp_cdc_ai.sandbox.SandboxTranslationRegistry;
import com.hayden.acp_cdc_ai.sandbox.SandboxTranslationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcpChatModelArgsParsingTest {

    @Mock
    private ChatMemoryContext chatMemoryContext;

    @Mock
    private AcpSessionManager sessionManager;

    @Mock
    private McpProperties mcpProperties;

    @Mock
    private IPermissionGate permissionGate;

    @Mock
    private RequestContextRepository requestContextRepository;

    @Mock
    private SandboxTranslationRegistry sandboxTranslationRegistry;

    private AcpChatModel acpChatModel;
    private AcpModelProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AcpModelProperties(
                AcpProvider.CODEX,
                Map.of(
                        AcpProvider.CODEX, new AcpProviderDefinition(
                                "codex",
                                "stdio",
                                "codex-acp",
                                "--sandbox workspace-write",
                                "/tmp/worktree",
                                null,
                                null,
                                "chatgpt",
                                Map.of("CODEX_HOME", "/tmp/codex"),
                                null
                        ),
                        AcpProvider.CLAUDE_OPENROUTER, new AcpProviderDefinition(
                                "claude-openrouter",
                                "stdio",
                                "claude-agent-acp",
                                "--model openrouter/free",
                                "/tmp/claude",
                                null,
                                null,
                                null,
                                Map.of(),
                                "openrouter/free"
                        )
                )
        );
        acpChatModel = new AcpChatModel(
                properties,
                chatMemoryContext,
                sessionManager,
                mcpProperties,
                permissionGate,
                requestContextRepository,
                sandboxTranslationRegistry,
                new ObjectMapper()
        );
    }

    @Nested
    @DisplayName("AcpChatOptionsString")
    class AcpChatOptionsStringTests {

        @Test
        @DisplayName("encodes and decodes session, model, provider, and options")
        void encodesAndDecodesPayload() {
            ObjectMapper mapper = new ObjectMapper();
            AcpChatOptionsString payload = AcpChatOptionsString.create(
                    "ak:test-session",
                    "gpt-oss:120b-cloud",
                    "claude-openrouter",
                    Map.of("mode", "strict")
            );

            String encoded = payload.encodeModel(mapper);
            AcpChatOptionsString decoded = AcpChatOptionsString.fromEncodedModel(encoded, mapper);

            assertThat(decoded.sessionArtifactKey()).isEqualTo("ak:test-session");
            assertThat(decoded.requestedModel()).isEqualTo("gpt-oss:120b-cloud");
            assertThat(decoded.requestedProvider()).isEqualTo("claude-openrouter");
            assertThat(decoded.options()).containsEntry("mode", "strict");
        }

        @Test
        @DisplayName("treats a raw legacy model string as a session-only payload")
        void decodesLegacyPayload() {
            AcpChatOptionsString decoded = AcpChatOptionsString.fromEncodedModel("ak:legacy-session", new ObjectMapper());

            assertThat(decoded.sessionArtifactKey()).isEqualTo("ak:legacy-session");
            assertThat(decoded.requestedModel()).isNull();
            assertThat(decoded.requestedProvider()).isNull();
        }
    }

    @Nested
    @DisplayName("AcpModelProperties")
    class ProviderCatalogTests {

        @Test
        @DisplayName("resolves explicit provider by name")
        void resolvesExplicitProvider() {
            assertThat(properties.resolveProviderName("claude-openrouter")).isEqualTo(AcpProvider.CLAUDE_OPENROUTER);
            assertThat(properties.resolveProvider("claude-openrouter").command()).isEqualTo("claude-agent-acp");
        }

        @Test
        @DisplayName("uses configured default provider when provider is omitted")
        void usesConfiguredDefaultProvider() {
            assertThat(properties.resolveProviderName(null)).isEqualTo(AcpProvider.CODEX);
            assertThat(properties.resolveProvider(null).authMethod()).isEqualTo("chatgpt");
        }

        @Test
        @DisplayName("fails for unknown provider names")
        void failsForUnknownProvider() {
            assertThatThrownBy(() -> properties.resolveProviderName("missing-provider"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown ACP provider");
        }
    }

    @Nested
    @DisplayName("parseArgs")
    class ParseArgsTests {

        @Test
        @DisplayName("parses simple space-separated args")
        void parsesSimpleArgs() {
            assertThat(acpChatModel.parseArgs("--add-dir /path/a --sandbox workspace-write"))
                    .containsExactly("--add-dir", "/path/a", "--sandbox", "workspace-write");
        }

        @Test
        @DisplayName("returns empty list for blank args")
        void returnsEmptyForBlankArgs() {
            assertThat(acpChatModel.parseArgs("   ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("resolveSandboxTranslation")
    class ResolveSandboxTranslationTests {

        @Test
        @DisplayName("returns empty translation when session is unknown")
        void returnsEmptyTranslationWhenSessionUnknown() {
            when(requestContextRepository.findBySessionId("session-123")).thenReturn(Optional.empty());

            SandboxTranslation result = acpChatModel.resolveSandboxTranslation("session-123", AcpProvider.CODEX, "--existing-arg value");

            assertThat(result.env()).isEmpty();
            assertThat(result.args()).isEmpty();
        }

        @Test
        @DisplayName("uses direct strategy when provider strategy exists")
        void usesDirectStrategyWhenFound() {
            RequestContext context = RequestContext.builder()
                    .sessionId("session-123")
                    .sandboxContext(SandboxContext.builder().mainWorktreePath(Path.of("/project")).build())
                    .build();
            SandboxTranslationStrategy directStrategy = mock(SandboxTranslationStrategy.class);
            SandboxTranslation expected = new SandboxTranslation(Map.of("ENV_VAR", "value"), List.of("--translated"), "/project");

            when(requestContextRepository.findBySessionId("session-123")).thenReturn(Optional.of(context));
            // AcpProvider.CLAUDE_OPENROUTER.wireValue() -> "claudeopenrouter"
            when(sandboxTranslationRegistry.find("claude-agent-acp")).thenReturn(Optional.of(directStrategy));
            when(directStrategy.translate(eq(context), any())).thenReturn(expected);

            SandboxTranslation result = acpChatModel.resolveSandboxTranslation("session-123", AcpProvider.CLAUDE_OPENROUTER, "--model openrouter/free");

            assertThat(result).isEqualTo(expected);
            verify(directStrategy).translate(eq(context), eq(List.of("--model", "openrouter/free")));
        }

    }
}
