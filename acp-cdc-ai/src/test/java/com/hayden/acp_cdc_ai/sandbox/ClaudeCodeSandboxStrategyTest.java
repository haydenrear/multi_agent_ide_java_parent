package com.hayden.acp_cdc_ai.sandbox;

import com.hayden.acp_cdc_ai.repository.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeCodeSandboxStrategyTest {

    private ClaudeCodeSandboxStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ClaudeCodeSandboxStrategy();
    }

    @Test
    @DisplayName("providerKey should return claude-code-acp")
    void providerKeyShouldReturnClaudeCodeAcp() {
        assertThat(strategy.providerKey()).isEqualTo("claude-code-acp");
    }

    @Nested
    @DisplayName("translate with null/empty context")
    class NullContextTests {

        @Test
        @DisplayName("should return empty translation for null context")
        void shouldReturnEmptyForNullContext() {
            SandboxTranslation result = strategy.translate(null, Collections.emptyList());
            
            assertThat(result.env()).isEmpty();
            assertThat(result.args()).isEmpty();
            assertThat(result.workingDirectory()).isNull();
        }

        @Test
        @DisplayName("should return empty translation when mainWorktreePath is null")
        void shouldReturnEmptyWhenMainPathIsNull() {
            RequestContext context = RequestContext.builder()
                    .sessionId("test-session")
                    .sandboxContext(SandboxContext.builder().mainWorktreePath(null).build())
                    .build();
            
            SandboxTranslation result = strategy.translate(context, Collections.emptyList());
            
            assertThat(result.env()).isEmpty();
            assertThat(result.args()).isEmpty();
        }
    }

    @Nested
    @DisplayName("translate with valid context")
    class ValidContextTests {

        private RequestContext contextWithMainPath;
        private RequestContext contextWithSubmodules;

        @BeforeEach
        void setUp() {
            contextWithMainPath = RequestContext.builder()
                    .sessionId("test-session")
                    .sandboxContext(SandboxContext.builder()
                            .mainWorktreePath(Path.of("/project/main"))
                            .build())
                    .build();

            contextWithSubmodules = RequestContext.builder()
                    .sessionId("test-session")
                    .sandboxContext(SandboxContext.builder()
                            .mainWorktreePath(Path.of("/project/main"))
                            .submoduleWorktreePaths(Arrays.asList(
                                    Path.of("/project/submodule1"),
                                    Path.of("/project/submodule2")
                            ))
                            .build())
                    .build();
        }

        @Test
        @DisplayName("should add --add-dir for main worktree path")
        void shouldAddMainWorktreePath() {
            SandboxTranslation result = strategy.translate(contextWithMainPath, Collections.emptyList());
            
            assertThat(result.args()).contains("--add-dir", "/project/main");
            assertThat(result.workingDirectory()).isEqualTo("/project/main");
        }

        @Test
        @DisplayName("should add --add-dir for each submodule")
        void shouldAddSubmodulePaths() {
            SandboxTranslation result = strategy.translate(contextWithSubmodules, Collections.emptyList());
            
            assertThat(result.args()).contains(
                    "--add-dir", "/project/main",
                    "--add-dir", "/project/submodule1",
                    "--add-dir", "/project/submodule2"
            );
        }

        @Test
        @DisplayName("should add --permission-mode acceptEdits by default")
        void shouldAddPermissionModeByDefault() {
            SandboxTranslation result = strategy.translate(contextWithMainPath, Collections.emptyList());
            
            assertThat(result.args()).contains("--permission-mode", "acceptEdits");
        }

        @Test
        @DisplayName("should not add --permission-mode if already specified in acpArgs")
        void shouldNotAddPermissionModeIfAlreadySpecified() {
            List<String> acpArgs = Arrays.asList("--permission-mode", "bypassPermissions");
            
            SandboxTranslation result = strategy.translate(contextWithMainPath, acpArgs);
            
            // Should not contain our default acceptEdits
            long permissionModeCount = result.args().stream()
                    .filter(arg -> arg.equals("--permission-mode"))
                    .count();
            assertThat(permissionModeCount).isZero();
        }

        @Test
        @DisplayName("should not add --add-dir for path already specified in acpArgs")
        void shouldNotAddDuplicateAddDir() {
            List<String> acpArgs = Arrays.asList("--add-dir", "/project/main");
            
            SandboxTranslation result = strategy.translate(contextWithMainPath, acpArgs);
            
            // Should not contain duplicate --add-dir for main path
            long addDirCount = result.args().stream()
                    .filter(arg -> arg.equals("--add-dir"))
                    .count();
            assertThat(addDirCount).isZero();
        }

        @Test
        @DisplayName("should add submodule paths not already in acpArgs")
        void shouldAddOnlyMissingSubmodulePaths() {
            List<String> acpArgs = Arrays.asList("--add-dir", "/project/submodule1");
            
            SandboxTranslation result = strategy.translate(contextWithSubmodules, acpArgs);
            
            // Should add main and submodule2, but not submodule1
            assertThat(result.args()).contains("--add-dir", "/project/main");
            assertThat(result.args()).contains("--add-dir", "/project/submodule2");
            
            // Count how many times submodule1 appears (should be 0)
            long submodule1Count = result.args().stream()
                    .filter(arg -> arg.equals("/project/submodule1"))
                    .count();
            assertThat(submodule1Count).isZero();
        }

        @Test
        @DisplayName("should return empty env map")
        void shouldReturnEmptyEnvMap() {
            SandboxTranslation result = strategy.translate(contextWithMainPath, Collections.emptyList());
            
            assertThat(result.env()).isEmpty();
        }
    }
}
