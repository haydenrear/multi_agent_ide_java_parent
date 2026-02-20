package com.hayden.acp_cdc_ai.sandbox;

import com.hayden.acp_cdc_ai.repository.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that verify all sandbox strategies work correctly together
 * with the SandboxTranslationRegistry and process real-world argument scenarios.
 */
class SandboxTranslationIntegrationTest {

    private SandboxTranslationRegistry registry;
    private ClaudeCodeSandboxStrategy claudeStrategy;
    private CodexSandboxStrategy codexStrategy;

    @TempDir
    Path tempDir;

    private Path mainWorktree;
    private Path submodule1;
    private Path submodule2;

    @BeforeEach
    void setUp() throws IOException {
        claudeStrategy = new ClaudeCodeSandboxStrategy();
        codexStrategy = new CodexSandboxStrategy();

        registry = new SandboxTranslationRegistry(
                Arrays.asList(claudeStrategy, codexStrategy)
        );

        // Create temp directories for testing
        mainWorktree = tempDir.resolve("main-project");
        submodule1 = tempDir.resolve("submodule1");
        submodule2 = tempDir.resolve("submodule2");
        Files.createDirectories(mainWorktree);
        Files.createDirectories(submodule1);
        Files.createDirectories(submodule2);
    }

    @Nested
    @DisplayName("Registry lookup")
    class RegistryLookupTests {

        @Test
        @DisplayName("should find claude-code-acp strategy")
        void shouldFindClaudeStrategy() {
            assertThat(registry.find("claude-code-acp")).isPresent();
            assertThat(registry.find("claude-code-acp").get()).isInstanceOf(ClaudeCodeSandboxStrategy.class);
        }

        @Test
        @DisplayName("should find codex-acp strategy")
        void shouldFindCodexStrategy() {
            assertThat(registry.find("codex-acp")).isPresent();
            assertThat(registry.find("codex-acp").get()).isInstanceOf(CodexSandboxStrategy.class);
        }

        @Test
        @DisplayName("should be case insensitive")
        void shouldBeCaseInsensitive() {
            assertThat(registry.find("CLAUDE-CODE-ACP")).isPresent();
            assertThat(registry.find("Claude-Code-Acp")).isPresent();
            assertThat(registry.find("GOOSE")).isPresent();
        }

        @Test
        @DisplayName("should return empty for unknown provider")
        void shouldReturnEmptyForUnknownProvider() {
            assertThat(registry.find("unknown-provider")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Claude Code integration")
    class ClaudeCodeIntegrationTests {

        @Test
        @DisplayName("should generate correct args with no existing acpArgs")
        void shouldGenerateCorrectArgsWithNoExistingArgs() {
            RequestContext context = createContext(mainWorktree, submodule1, submodule2);

            SandboxTranslation result = claudeStrategy.translate(context, Collections.emptyList());

            // Verify all expected args are present
            assertThat(result.args()).contains("--add-dir", mainWorktree.toString());
            assertThat(result.args()).contains("--add-dir", submodule1.toString());
            assertThat(result.args()).contains("--add-dir", submodule2.toString());
            assertThat(result.args()).contains("--permission-mode", "acceptEdits");
            assertThat(result.workingDirectory()).isEqualTo(mainWorktree.toString());
        }

        @Test
        @DisplayName("should merge with existing acpArgs without duplicates")
        void shouldMergeWithExistingArgsWithoutDuplicates() {
            RequestContext context = createContext(mainWorktree, submodule1);
            List<String> existingArgs = Arrays.asList(
                    "--add-dir", mainWorktree.toString(),
                    "--permission-mode", "bypassPermissions"
            );

            SandboxTranslation result = claudeStrategy.translate(context, existingArgs);

            // Should NOT duplicate --add-dir for main worktree
            long mainPathCount = result.args().stream()
                    .filter(arg -> arg.equals(mainWorktree.toString()))
                    .count();
            assertThat(mainPathCount).isZero();

            // Should NOT add --permission-mode since it's already set
            long permModeCount = result.args().stream()
                    .filter(arg -> arg.equals("--permission-mode"))
                    .count();
            assertThat(permModeCount).isZero();

            // Should still add submodule1
            assertThat(result.args()).contains("--add-dir", submodule1.toString());
        }

        @Test
        @DisplayName("should handle complex path with spaces when properly tokenized")
        void shouldHandlePathsCorrectly() {
            // Note: Paths with spaces would need quoting at the shell level
            // but within our List<String>, they're handled correctly
            RequestContext context = createContext(mainWorktree);
            List<String> existingArgs = Arrays.asList("--some-flag", "value");

            SandboxTranslation result = claudeStrategy.translate(context, existingArgs);

            // Path should be added as a single element, not split
            assertThat(result.args()).contains(mainWorktree.toString());
        }
    }

    @Nested
    @DisplayName("Codex integration")
    class CodexIntegrationTests {

        @Test
        @DisplayName("should generate correct args with no existing acpArgs")
        void shouldGenerateCorrectArgsWithNoExistingArgs() {
            RequestContext context = createContext(mainWorktree, submodule1, submodule2);

            SandboxTranslation result = codexStrategy.translate(context, Collections.emptyList());

            // Codex uses -c 'key=value' format
            assertThat(result.args()).containsSequence("-c", "cd=" + mainWorktree.toString());
            assertThat(result.args()).containsSequence("-c", "sandbox=workspace-write");
            assertThat(result.args()).containsSequence("-c", "add-dir=" + submodule1.toString());
            assertThat(result.args()).containsSequence("-c", "add-dir=" + submodule2.toString());
            assertThat(result.workingDirectory()).isEqualTo(mainWorktree.toString());
        }

        @Test
        @DisplayName("should respect existing -c cd=... config")
        void shouldRespectExistingCdConfig() {
            RequestContext context = createContext(mainWorktree);
            List<String> existingArgs = Arrays.asList("-c", "cd=/custom/path");

            SandboxTranslation result = codexStrategy.translate(context, existingArgs);

            // Should NOT add cd config since it's already specified
            long cdConfigCount = result.args().stream()
                    .filter(arg -> arg.startsWith("cd="))
                    .count();
            assertThat(cdConfigCount).isZero();
        }

        @Test
        @DisplayName("should respect existing -c sandbox=... config")
        void shouldRespectExistingSandboxConfig() {
            RequestContext context = createContext(mainWorktree);
            List<String> existingArgs = Arrays.asList("-c", "sandbox=read-only");

            SandboxTranslation result = codexStrategy.translate(context, existingArgs);

            // Should NOT add sandbox config since it's already specified
            long sandboxConfigCount = result.args().stream()
                    .filter(arg -> arg.startsWith("sandbox="))
                    .count();
            assertThat(sandboxConfigCount).isZero();
        }

        @Test
        @DisplayName("should generate correct command line when combined")
        void shouldGenerateCorrectCommandLine() {
            RequestContext context = createContext(mainWorktree, submodule1);

            SandboxTranslation result = codexStrategy.translate(context, Collections.emptyList());

            // Build a command line string to verify ordering makes sense
            String commandLine = String.join(" ", result.args());
            
            // Verify the structure looks correct for Codex CLI with -c format
            assertThat(commandLine).contains("-c cd=" + mainWorktree);
            assertThat(commandLine).contains("-c sandbox=workspace-write");
            assertThat(commandLine).contains("-c add-dir=" + submodule1);
        }
    }

    @Nested
    @DisplayName("Cross-provider comparison")
    class CrossProviderComparisonTests {

        @Test
        @DisplayName("all providers should set workingDirectory for same context")
        void allProvidersShouldSetWorkingDirectory() {
            RequestContext context = createContext(mainWorktree);

            SandboxTranslation claudeResult = claudeStrategy.translate(context, Collections.emptyList());
            SandboxTranslation codexResult = codexStrategy.translate(context, Collections.emptyList());

            assertThat(claudeResult.workingDirectory()).isEqualTo(mainWorktree.toString());
            assertThat(codexResult.workingDirectory()).isEqualTo(mainWorktree.toString());
        }

        @Test
        @DisplayName("providers use different mechanisms for working directory")
        void providersUseDifferentMechanismsForWorkingDir() {
            RequestContext context = createContext(mainWorktree);

            SandboxTranslation claudeResult = claudeStrategy.translate(context, Collections.emptyList());
            SandboxTranslation codexResult = codexStrategy.translate(context, Collections.emptyList());

            // Claude uses --add-dir (cwd set via session param)
            assertThat(claudeResult.args()).contains("--add-dir");

            // Codex uses -c cd=... format
            assertThat(codexResult.args()).contains("-c");
            boolean hasCdConfig = codexResult.args().stream().anyMatch(arg -> arg.startsWith("cd="));
            assertThat(hasCdConfig).isTrue();

        }

        @Test
        @DisplayName("none sets environment variables by default")
        void setsEnvVarsByDefault() {
            RequestContext context = createContext(mainWorktree);

            SandboxTranslation claudeResult = claudeStrategy.translate(context, Collections.emptyList());
            SandboxTranslation codexResult = codexStrategy.translate(context, Collections.emptyList());

            assertThat(claudeResult.env()).isEmpty();
            assertThat(codexResult.env()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Real-world scenarios")
    class RealWorldScenarioTests {

        @Test
        @DisplayName("monorepo with multiple submodules using Claude")
        void monorepoWithMultipleSubmodulesUsingClaude() {
            RequestContext context = createContext(mainWorktree, submodule1, submodule2);
            List<String> existingArgs = Collections.emptyList();

            SandboxTranslation result = claudeStrategy.translate(context, existingArgs);

            // Should have 3 --add-dir entries + 1 --permission-mode
            // That's 8 args total: (--add-dir, path) x 3 + (--permission-mode, acceptEdits)
            assertThat(result.args()).hasSize(8);
        }

        @Test
        @DisplayName("user overrides permission mode in Claude")
        void userOverridesPermissionModeInClaude() {
            RequestContext context = createContext(mainWorktree);
            List<String> existingArgs = Arrays.asList("--permission-mode", "plan");

            SandboxTranslation result = claudeStrategy.translate(context, existingArgs);

            // Should not contain acceptEdits since user specified plan
            assertThat(result.args()).doesNotContain("acceptEdits");
            // Should only have --add-dir for main path
            assertThat(result.args()).containsExactly("--add-dir", mainWorktree.toString());
        }

        @Test
        @DisplayName("user specifies full-auto in Codex")
        void userSpecifiesFullAutoInCodex() {
            RequestContext context = createContext(mainWorktree);
            // --full-auto is a convenience flag that implies sandbox behavior
            List<String> existingArgs = Arrays.asList("--full-auto");

            SandboxTranslation result = codexStrategy.translate(context, existingArgs);

            // Should still add -c cd=... and -c sandbox=... since --full-auto doesn't match -c config format
            // In reality, the CLI might handle this, but our strategy doesn't know about --full-auto
            assertThat(result.args()).containsSequence("-c", "cd=" + mainWorktree.toString());
            assertThat(result.args()).containsSequence("-c", "sandbox=workspace-write");
        }

        @Test
        @DisplayName("incremental args building simulation")
        void incrementalArgsBuildingSimulation() {
            // Simulate how AcpChatModel builds the final command
            // command = ["codex-acp"]
            // + sandbox translation args
            // = full command array

            RequestContext context = createContext(mainWorktree, submodule1);
            SandboxTranslation translation = codexStrategy.translate(context, Collections.emptyList());

            String[] baseCommand = {"codex-acp"};
            String[] sandboxArgs = translation.args().toArray(new String[0]);

            // Combine like AcpChatModel does
            String[] fullCommand = new String[baseCommand.length + sandboxArgs.length];
            System.arraycopy(baseCommand, 0, fullCommand, 0, baseCommand.length);
            System.arraycopy(sandboxArgs, 0, fullCommand, baseCommand.length, sandboxArgs.length);

            assertThat(fullCommand).startsWith("codex-acp");
            // Codex uses -c 'key=value' format
            assertThat(fullCommand).contains("-c");
            // Check the config values are present
            assertThat(translation.args()).anyMatch(arg -> arg.startsWith("cd="));
            assertThat(translation.args()).anyMatch(arg -> arg.startsWith("sandbox="));
            assertThat(translation.args()).anyMatch(arg -> arg.startsWith("add-dir="));
        }
    }

    private RequestContext createContext(Path mainPath, Path... submodules) {
        return RequestContext.builder()
                .sessionId("test-session")
                .sandboxContext(SandboxContext.builder()
                        .mainWorktreePath(mainPath)
                        .submoduleWorktreePaths(submodules.length > 0 ? Arrays.asList(submodules) : null)
                        .build())
                .build();
    }
}
