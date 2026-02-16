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

class GooseSandboxStrategyTest {

    private GooseSandboxStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new GooseSandboxStrategy();
    }

    @Test
    @DisplayName("providerKey should return goose")
    void providerKeyShouldReturnGoose() {
        assertThat(strategy.providerKey()).isEqualTo("goose");
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

        @TempDir
        Path tempDir;

        private RequestContext contextWithExistingPath;

        @BeforeEach
        void setUp() throws IOException {
            // Create a real directory for tests that check file existence
            Path projectDir = tempDir.resolve("project");
            Files.createDirectories(projectDir);

            contextWithExistingPath = RequestContext.builder()
                    .sessionId("test-session")
                    .sandboxContext(SandboxContext.builder()
                            .mainWorktreePath(projectDir)
                            .build())
                    .build();
        }

        @Test
        @DisplayName("should set GOOSE_MODE env to smart_approve")
        void shouldSetGooseModeEnv() {
            SandboxTranslation result = strategy.translate(contextWithExistingPath, Collections.emptyList());
            
            assertThat(result.env()).containsEntry("GOOSE_MODE", "smart_approve");
        }

        @Test
        @DisplayName("should set workingDirectory in translation")
        void shouldSetWorkingDirectory() {
            SandboxTranslation result = strategy.translate(contextWithExistingPath, Collections.emptyList());
            
            assertThat(result.workingDirectory()).isEqualTo(tempDir.resolve("project").toString());
        }


        @Test
        @DisplayName("should not add -w for non-existing directory")
        void shouldNotAddWorkingDirForNonExistingPath() {
            RequestContext context = RequestContext.builder()
                    .sessionId("test-session")
                    .sandboxContext(SandboxContext.builder()
                            .mainWorktreePath(Path.of("/non/existing/path"))
                            .build())
                    .build();
            
            SandboxTranslation result = strategy.translate(context, Collections.emptyList());
            
            // Should still set workingDirectory but not add -w flag
            assertThat(result.workingDirectory()).isEqualTo("/non/existing/path");
        }

        @Test
        @DisplayName("should always set GOOSE_MODE regardless of acpArgs")
        void shouldAlwaysSetGooseMode() {
            List<String> acpArgs = Arrays.asList("-w", "/custom/path");

            SandboxTranslation result = strategy.translate(contextWithExistingPath, acpArgs);
            
            // GOOSE_MODE should always be set
            assertThat(result.env()).containsEntry("GOOSE_MODE", "smart_approve");
        }

        @Test
        @DisplayName("should not have sandbox or add-dir args (Goose doesn't support them)")
        void shouldNotHaveSandboxArgs() {
            SandboxTranslation result = strategy.translate(contextWithExistingPath, Collections.emptyList());
            
            assertThat(result.args()).doesNotContain("--sandbox");
            assertThat(result.args()).doesNotContain("--add-dir");
            assertThat(result.args()).doesNotContain("-s");
        }
    }
}
