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

class CodexSandboxStrategyTest {

    private CodexSandboxStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new CodexSandboxStrategy();
    }

    @Test
    @DisplayName("providerKey should return codex-acp")
    void providerKeyShouldReturnCodexAcp() {
        assertThat(strategy.providerKey()).isEqualTo("codex-acp");
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
        @DisplayName("should add -c cd=<path> for main worktree path")
        void shouldAddCdConfigForMainPath() {
            SandboxTranslation result = strategy.translate(contextWithMainPath, Collections.emptyList());
            
            assertThat(result.args()).containsSequence("-c", "cd=/project/main");
            assertThat(result.workingDirectory()).isEqualTo("/project/main");
        }

        @Test
        @DisplayName("should add -c sandbox=workspace-write by default")
        void shouldAddSandboxConfigByDefault() {
            SandboxTranslation result = strategy.translate(contextWithMainPath, Collections.emptyList());
            
            assertThat(result.args()).containsSequence("-c", "sandbox=workspace-write");
        }

        @Test
        @DisplayName("should add -c add-dir=<path> for each submodule")
        void shouldAddSubmodulePathsAsConfig() {
            SandboxTranslation result = strategy.translate(contextWithSubmodules, Collections.emptyList());
            
            assertThat(result.args()).containsSequence("-c", "add-dir=/project/submodule1");
            assertThat(result.args()).containsSequence("-c", "add-dir=/project/submodule2");
        }

        @Test
        @DisplayName("should not add cd config if already specified")
        void shouldNotAddCdIfAlreadySpecified() {
            List<String> acpArgs = Arrays.asList("-c", "cd=/custom/path");
            
            SandboxTranslation result = strategy.translate(contextWithMainPath, acpArgs);
            
            // Should not contain our generated cd config
            long cdConfigCount = result.args().stream()
                    .filter(arg -> arg.startsWith("cd="))
                    .count();
            assertThat(cdConfigCount).isZero();
        }

        @Test
        @DisplayName("should not add sandbox config if already specified")
        void shouldNotAddSandboxIfAlreadySpecified() {
            List<String> acpArgs = Arrays.asList("-c", "sandbox=read-only");
            
            SandboxTranslation result = strategy.translate(contextWithMainPath, acpArgs);
            
            // Should not contain our generated sandbox config
            long sandboxConfigCount = result.args().stream()
                    .filter(arg -> arg.startsWith("sandbox="))
                    .count();
            assertThat(sandboxConfigCount).isZero();
        }

        @Test
        @DisplayName("should not add add-dir config for submodule path already specified")
        void shouldNotAddDuplicateSubmodulePath() {
            List<String> acpArgs = Arrays.asList("-c", "add-dir=/project/submodule1");
            
            SandboxTranslation result = strategy.translate(contextWithSubmodules, acpArgs);
            
            // Should add submodule2 but not submodule1
            assertThat(result.args()).containsSequence("-c", "add-dir=/project/submodule2");
            
            // Count how many times submodule1 appears in our generated args
            long submodule1Count = result.args().stream()
                    .filter(arg -> arg.equals("add-dir=/project/submodule1"))
                    .count();
            assertThat(submodule1Count).isZero();
        }

        @Test
        @DisplayName("should return empty env map")
        void shouldReturnEmptyEnvMap() {
            SandboxTranslation result = strategy.translate(contextWithMainPath, Collections.emptyList());
            
            assertThat(result.env()).isEmpty();
        }

        @Test
        @DisplayName("should handle context with empty submodule list")
        void shouldHandleEmptySubmoduleList() {
            RequestContext context = RequestContext.builder()
                    .sessionId("test-session")
                    .sandboxContext(SandboxContext.builder()
                            .mainWorktreePath(Path.of("/project/main"))
                            .submoduleWorktreePaths(Collections.emptyList())
                            .build())
                    .build();
            
            SandboxTranslation result = strategy.translate(context, Collections.emptyList());
            
            // Should have -c cd=... and -c sandbox=... but no add-dir
            assertThat(result.args()).containsSequence("-c", "cd=/project/main");
            assertThat(result.args()).containsSequence("-c", "sandbox=workspace-write");
            
            boolean hasAddDir = result.args().stream().anyMatch(arg -> arg.startsWith("add-dir="));
            assertThat(hasAddDir).isFalse();
        }

        @Test
        @DisplayName("should generate correct argument structure")
        void shouldGenerateCorrectArgumentStructure() {
            SandboxTranslation result = strategy.translate(contextWithMainPath, Collections.emptyList());
            
            // Args should be: -c, cd=..., -c, sandbox=...
            assertThat(result.args()).hasSize(4);
            assertThat(result.args().get(0)).isEqualTo("-c");
            assertThat(result.args().get(1)).isEqualTo("cd=/project/main");
            assertThat(result.args().get(2)).isEqualTo("-c");
            assertThat(result.args().get(3)).isEqualTo("sandbox=workspace-write");
        }

        @Test
        @DisplayName("should generate correct argument structure with submodules")
        void shouldGenerateCorrectArgumentStructureWithSubmodules() {
            SandboxTranslation result = strategy.translate(contextWithSubmodules, Collections.emptyList());
            
            // Args should be: -c, cd=..., -c, sandbox=..., -c, add-dir=..., -c, add-dir=...
            assertThat(result.args()).hasSize(8);
            
            // Verify pattern: every even index is "-c", every odd index is "key=value"
            for (int i = 0; i < result.args().size(); i++) {
                if (i % 2 == 0) {
                    assertThat(result.args().get(i)).isEqualTo("-c");
                } else {
                    assertThat(result.args().get(i)).contains("=");
                }
            }
        }
    }
}
