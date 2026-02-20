package com.hayden.acp_cdc_ai.acp;

import com.hayden.acp_cdc_ai.acp.config.AcpModelProperties;
import com.hayden.acp_cdc_ai.acp.config.McpProperties;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.acp_cdc_ai.repository.RequestContext;
import com.hayden.acp_cdc_ai.repository.RequestContextRepository;
import com.hayden.acp_cdc_ai.sandbox.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for AcpChatModel args parsing and sandbox translation resolution.
 */
@ExtendWith(MockitoExtension.class)
class AcpChatModelArgsParsingTest {

    @Mock
    private AcpModelProperties properties;

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

    @BeforeEach
    void setUp() {
        acpChatModel = new AcpChatModel(
                properties,
                chatMemoryContext,
                sessionManager,
                mcpProperties,
                permissionGate,
                requestContextRepository,
                sandboxTranslationRegistry
        );
    }

    @Nested
    @DisplayName("parseArgs")
    class ParseArgsTests {

        @Test
        @DisplayName("should parse simple space-separated args")
        void shouldParseSimpleArgs() throws Exception {
            List<String> result = invokeParseArgs("--add-dir /path/a --sandbox workspace-write");

            assertThat(result).containsExactly("--add-dir", "/path/a", "--sandbox", "workspace-write");
        }

        @Test
        @DisplayName("should parse args with multiple spaces")
        void shouldParseArgsWithMultipleSpaces() throws Exception {
            List<String> result = invokeParseArgs("--flag1   value1    --flag2   value2");

            assertThat(result).containsExactly("--flag1", "value1", "--flag2", "value2");
        }

        @Test
        @DisplayName("should return empty list for null args")
        void shouldReturnEmptyForNullArgs() throws Exception {
            List<String> result = invokeParseArgs(null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for blank args")
        void shouldReturnEmptyForBlankArgs() throws Exception {
            List<String> result = invokeParseArgs("   ");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for empty string")
        void shouldReturnEmptyForEmptyString() throws Exception {
            List<String> result = invokeParseArgs("");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should parse single arg")
        void shouldParseSingleArg() throws Exception {
            List<String> result = invokeParseArgs("--verbose");

            assertThat(result).containsExactly("--verbose");
        }

        @Test
        @DisplayName("should handle tabs as separators")
        void shouldHandleTabsAsSeparators() throws Exception {
            List<String> result = invokeParseArgs("--flag1\tvalue1\t--flag2\tvalue2");

            assertThat(result).containsExactly("--flag1", "value1", "--flag2", "value2");
        }

        @SuppressWarnings("unchecked")
        private List<String> invokeParseArgs(String args) throws Exception {
            return acpChatModel.parseArgs(args);
        }
    }

    @Nested
    @DisplayName("resolveProviderKey")
    class ResolveProviderKeyTests {

        @Test
        @DisplayName("should extract provider key from simple command")
        void shouldExtractProviderKeyFromSimpleCommand() throws Exception {
            when(properties.getCommand()).thenReturn("claude-acp");

            String result = invokeResolveProviderKey();

            assertThat(result).isEqualTo("claude-acp");
        }

        @Test
        @DisplayName("should extract provider key from command with args")
        void shouldExtractProviderKeyFromCommandWithArgs() throws Exception {
            when(properties.getCommand()).thenReturn("codex-acp --some-flag value");

            String result = invokeResolveProviderKey();

            assertThat(result).isEqualTo("codex-acp");
        }

        @Test
        @DisplayName("should return empty string for null command")
        void shouldReturnEmptyForNullCommand() throws Exception {
            when(properties.getCommand()).thenReturn(null);

            String result = invokeResolveProviderKey();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty string for blank command")
        void shouldReturnEmptyForBlankCommand() throws Exception {
            when(properties.getCommand()).thenReturn("   ");

            String result = invokeResolveProviderKey();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should lowercase the provider key")
        void shouldLowercaseProviderKey() throws Exception {
            when(properties.getCommand()).thenReturn("Claude-ACP");

            String result = invokeResolveProviderKey();

            assertThat(result).isEqualTo("claude-acp");
        }

        private String invokeResolveProviderKey() throws Exception {
            return acpChatModel.resolveProviderKey();
        }
    }

    @Nested
    @DisplayName("resolveSandboxTranslation")
    class ResolveSandboxTranslationTests {

        @Test
        @DisplayName("should return empty translation when memoryId is null")
        void shouldReturnEmptyWhenMemoryIdIsNull() throws Exception {
            SandboxTranslation result = invokeResolveSandboxTranslation(null, "--some-args");

            assertThat(result.env()).isEmpty();
            assertThat(result.args()).isEmpty();
        }

        @Test
        @DisplayName("should return empty translation when context not found")
        void shouldReturnEmptyWhenContextNotFound() throws Exception {
            when(requestContextRepository.findBySessionId("session-123")).thenReturn(Optional.empty());

            SandboxTranslation result = invokeResolveSandboxTranslation("session-123", "--some-args");

            assertThat(result.env()).isEmpty();
            assertThat(result.args()).isEmpty();
        }

        @Test
        @DisplayName("should use direct strategy when found")
        void shouldUseDirectStrategyWhenFound() throws Exception {
            RequestContext context = RequestContext.builder()
                    .sessionId("session-123")
                    .sandboxContext(SandboxContext.builder()
                            .mainWorktreePath(Path.of("/project"))
                            .build())
                    .build();
            
            SandboxTranslationStrategy mockStrategy = mock(SandboxTranslationStrategy.class);
            SandboxTranslation expectedTranslation = new SandboxTranslation(
                    java.util.Map.of("ENV_VAR", "value"),
                    List.of("--arg", "value"),
                    "/project"
            );
            
            when(requestContextRepository.findBySessionId("session-123")).thenReturn(Optional.of(context));
            when(properties.getCommand()).thenReturn("claude-acp");
            when(sandboxTranslationRegistry.find("claude-acp")).thenReturn(Optional.of(mockStrategy));
            when(mockStrategy.translate(eq(context), any())).thenReturn(expectedTranslation);

            SandboxTranslation result = invokeResolveSandboxTranslation("session-123", "--existing-arg value");

            assertThat(result).isEqualTo(expectedTranslation);
            verify(mockStrategy).translate(eq(context), eq(List.of("--existing-arg", "value")));
        }

        @Test
        @DisplayName("should use fallback strategy when direct not found")
        void shouldUseFallbackStrategyWhenDirectNotFound() throws Exception {
            RequestContext context = RequestContext.builder()
                    .sessionId("session-123")
                    .sandboxContext(SandboxContext.builder()
                            .mainWorktreePath(Path.of("/project"))
                            .build())
                    .build();
            
            SandboxTranslationStrategy fallbackStrategy = mock(SandboxTranslationStrategy.class);
            SandboxTranslation expectedTranslation = new SandboxTranslation(
                    java.util.Map.of(),
                    List.of("--fallback-arg"),
                    "/project"
            );
            
            when(requestContextRepository.findBySessionId("session-123")).thenReturn(Optional.of(context));
            when(properties.getCommand()).thenReturn("claude-acp-custom");
            when(sandboxTranslationRegistry.find("claude-acp-custom")).thenReturn(Optional.empty());
            when(sandboxTranslationRegistry.find("claude")).thenReturn(Optional.of(fallbackStrategy));
            when(fallbackStrategy.translate(eq(context), any())).thenReturn(expectedTranslation);

            SandboxTranslation result = invokeResolveSandboxTranslation("session-123", null);

            assertThat(result).isEqualTo(expectedTranslation);
        }

        @Test
        @DisplayName("should return empty translation when no strategy found")
        void shouldReturnEmptyWhenNoStrategyFound() throws Exception {
            RequestContext context = RequestContext.builder()
                    .sessionId("session-123")
                    .sandboxContext(SandboxContext.builder()
                            .mainWorktreePath(Path.of("/project"))
                            .build())
                    .build();
            
            when(requestContextRepository.findBySessionId("session-123")).thenReturn(Optional.of(context));
            when(properties.getCommand()).thenReturn("unknown-provider");
            when(sandboxTranslationRegistry.find("unknown-provider")).thenReturn(Optional.empty());
            when(sandboxTranslationRegistry.find("unknown")).thenReturn(Optional.empty());

            SandboxTranslation result = invokeResolveSandboxTranslation("session-123", null);

            assertThat(result.env()).isEmpty();
            assertThat(result.args()).isEmpty();
        }

        private SandboxTranslation invokeResolveSandboxTranslation(Object memoryId, String args) throws Exception {
            return acpChatModel.resolveSandboxTranslation(memoryId, args);
        }
    }
}
