package com.hayden.multiagentidelib.model.worktree;

import com.hayden.acp_cdc_ai.repository.RequestContext;
import com.hayden.acp_cdc_ai.sandbox.SandboxContext;
import com.hayden.utilitymodule.io.FileUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WorktreeSandbox")
class WorktreeSandboxTest {

    Path tempDir;

    private Path mainWorktree;
    private Path submodule1;
    private Path submodule2;

    @BeforeEach
    void setUp() throws IOException {

        tempDir = new File("").toPath().resolve("tmp");

        tempDir.toFile().mkdirs();

        mainWorktree = tempDir.resolve("main-project");
        submodule1 = tempDir.resolve("submodule1");
        submodule2 = tempDir.resolve("submodule2");
        
        Files.createDirectories(mainWorktree);
        Files.createDirectories(submodule1);
        Files.createDirectories(submodule2);
        
        // Create some subdirectories and files
        Files.createDirectories(mainWorktree.resolve("src/main/java"));
        Files.createDirectories(mainWorktree.resolve("src/test/java"));
        Files.writeString(mainWorktree.resolve("README.md"), "# Test");
        Files.writeString(mainWorktree.resolve("src/main/java/App.java"), "class App {}");
    }

    @AfterEach
    public void deleteAll() {
        FileUtils.deleteFilesRecursive(tempDir);
    }

    @Nested
    @DisplayName("validatePath")
    class ValidatePathTests {

        @Test
        @DisplayName("should allow the root directory itself")
        void shouldAllowRootDirectoryItself() {
            WorktreeSandbox sandbox = createSandbox(mainWorktree);

            SandboxValidationResult result = sandbox.validatePath(mainWorktree.toString());

            assertThat(result.allowed()).isTrue();
            assertThat(result.normalizedPath()).isEqualTo(mainWorktree.toAbsolutePath().normalize().toString());
        }

        @Test
        @DisplayName("should allow existing file in root")
        void shouldAllowExistingFileInRoot() {
            WorktreeSandbox sandbox = createSandbox(mainWorktree);

            SandboxValidationResult result = sandbox.validatePath(mainWorktree.resolve("README.md").toString());

            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("should allow existing subdirectory")
        void shouldAllowExistingSubdirectory() {
            WorktreeSandbox sandbox = createSandbox(mainWorktree);

            SandboxValidationResult result = sandbox.validatePath(mainWorktree.resolve("src/main/java").toString());

            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("should allow existing file in subdirectory")
        void shouldAllowExistingFileInSubdirectory() {
            WorktreeSandbox sandbox = createSandbox(mainWorktree);

            SandboxValidationResult result = sandbox.validatePath(mainWorktree.resolve("src/main/java/App.java").toString());

            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("should allow non-existent file in root")
        void shouldAllowNonExistentFileInRoot() {
            WorktreeSandbox sandbox = createSandbox(mainWorktree);

            SandboxValidationResult result = sandbox.validatePath(mainWorktree.resolve("new-file.txt").toString());

            assertThat(result.allowed()).isTrue();
            assertThat(result.normalizedPath()).contains("new-file.txt");
        }

        @Test
        @DisplayName("should allow non-existent file in existing subdirectory")
        void shouldAllowNonExistentFileInExistingSubdirectory() {
            WorktreeSandbox sandbox = createSandbox(mainWorktree);

            SandboxValidationResult result = sandbox.validatePath(mainWorktree.resolve("src/main/java/NewClass.java").toString());

            assertThat(result.allowed()).isTrue();
            assertThat(result.normalizedPath()).contains("NewClass.java");
        }

        @Test
        @DisplayName("should allow non-existent file in non-existent subdirectory")
        void shouldAllowNonExistentFileInNonExistentSubdirectory() {
            WorktreeSandbox sandbox = createSandbox(mainWorktree);

            // This path doesn't exist at all - neither the directory nor the file
            SandboxValidationResult result = sandbox.validatePath(
                    mainWorktree.resolve("src/new/package/structure/NewFile.java").toString());

            assertThat(result.allowed()).isTrue();
            assertThat(result.normalizedPath()).contains("NewFile.java");
        }

        @Test
        @DisplayName("should allow deeply nested non-existent path")
        void shouldAllowDeeplyNestedNonExistentPath() {
            WorktreeSandbox sandbox = createSandbox(mainWorktree);

            SandboxValidationResult result = sandbox.validatePath(
                    mainWorktree.resolve("a/b/c/d/e/f/g/file.txt").toString());

            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("should deny path outside of sandbox")
        void shouldDenyPathOutsideOfSandbox() {
            WorktreeSandbox sandbox = createSandbox(mainWorktree);

            SandboxValidationResult result = sandbox.validatePath("/etc/passwd");

            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).contains("outside");
        }

        @Test
        @DisplayName("should deny path in sibling directory")
        void shouldDenyPathInSiblingDirectory() {
            WorktreeSandbox sandbox = createSandbox(mainWorktree);

            // submodule1 is a sibling, not a child
            SandboxValidationResult result = sandbox.validatePath(submodule1.resolve("file.txt").toString());

            assertThat(result.allowed()).isFalse();
        }

        @Test
        @DisplayName("should deny path traversal attack")
        void shouldDenyPathTraversalAttack() {
            WorktreeSandbox sandbox = createSandbox(mainWorktree);

            // Attempt to escape sandbox with ..
            SandboxValidationResult result = sandbox.validatePath(
                    mainWorktree.resolve("../../../etc/passwd").toString());

            assertThat(result.allowed()).isFalse();
        }

        @Test
        @DisplayName("should deny null path")
        void shouldDenyNullPath() {
            WorktreeSandbox sandbox = createSandbox(mainWorktree);

            SandboxValidationResult result = sandbox.validatePath(null);

            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).contains("required");
        }

        @Test
        @DisplayName("should deny blank path")
        void shouldDenyBlankPath() {
            WorktreeSandbox sandbox = createSandbox(mainWorktree);

            SandboxValidationResult result = sandbox.validatePath("   ");

            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).contains("required");
        }

        @Test
        @DisplayName("should deny when no roots configured")
        void shouldDenyWhenNoRootsConfigured() {
            WorktreeSandbox sandbox = WorktreeSandbox.fromRequestContext(null);

            SandboxValidationResult result = sandbox.validatePath(mainWorktree.resolve("file.txt").toString());

            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).contains("no worktree sandbox configured");
        }
    }

    @Nested
    @DisplayName("with multiple roots (submodules)")
    class MultipleRootsTests {

        @Test
        @DisplayName("should allow path in main worktree")
        void shouldAllowPathInMainWorktree() {
            WorktreeSandbox sandbox = createSandboxWithSubmodules(mainWorktree, submodule1, submodule2);

            SandboxValidationResult result = sandbox.validatePath(mainWorktree.resolve("file.txt").toString());

            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("should allow path in first submodule")
        void shouldAllowPathInFirstSubmodule() {
            WorktreeSandbox sandbox = createSandboxWithSubmodules(mainWorktree, submodule1, submodule2);

            SandboxValidationResult result = sandbox.validatePath(submodule1.resolve("src/file.txt").toString());

            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("should allow path in second submodule")
        void shouldAllowPathInSecondSubmodule() {
            WorktreeSandbox sandbox = createSandboxWithSubmodules(mainWorktree, submodule1, submodule2);

            SandboxValidationResult result = sandbox.validatePath(submodule2.resolve("deep/nested/file.txt").toString());

            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("should allow non-existent paths in any root")
        void shouldAllowNonExistentPathsInAnyRoot() {
            WorktreeSandbox sandbox = createSandboxWithSubmodules(mainWorktree, submodule1, submodule2);

            assertThat(sandbox.validatePath(mainWorktree.resolve("new/path/file.txt").toString()).allowed()).isTrue();
            assertThat(sandbox.validatePath(submodule1.resolve("new/path/file.txt").toString()).allowed()).isTrue();
            assertThat(sandbox.validatePath(submodule2.resolve("new/path/file.txt").toString()).allowed()).isTrue();
        }

        @Test
        @DisplayName("should deny path outside all roots")
        void shouldDenyPathOutsideAllRoots() {
            WorktreeSandbox sandbox = createSandboxWithSubmodules(mainWorktree, submodule1, submodule2);

            SandboxValidationResult result = sandbox.validatePath("/tmp/outside/file.txt");

            assertThat(result.allowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("fromRequestContext")
    class FromRequestContextTests {

        @Test
        @DisplayName("should create sandbox from RequestContext with main path only")
        void shouldCreateSandboxFromRequestContextWithMainOnly() {
            RequestContext context = RequestContext.builder()
                    .sessionId("test-session")
                    .sandboxContext(SandboxContext.builder()
                            .mainWorktreePath(mainWorktree)
                            .build())
                    .build();

            WorktreeSandbox sandbox = WorktreeSandbox.fromRequestContext(context);

            assertThat(sandbox.allowedRoots()).hasSize(1);
            assertThat(sandbox.validatePath(mainWorktree.resolve("file.txt").toString()).allowed()).isTrue();
        }

        @Test
        @DisplayName("should create sandbox from RequestContext with submodules")
        void shouldCreateSandboxFromRequestContextWithSubmodules() {
            RequestContext context = RequestContext.builder()
                    .sessionId("test-session")
                    .sandboxContext(SandboxContext.builder()
                            .mainWorktreePath(mainWorktree)
                            .submoduleWorktreePaths(List.of(submodule1, submodule2))
                            .build())
                    .build();

            WorktreeSandbox sandbox = WorktreeSandbox.fromRequestContext(context);

            assertThat(sandbox.allowedRoots()).hasSize(3);
            assertThat(sandbox.validatePath(mainWorktree.resolve("file.txt").toString()).allowed()).isTrue();
            assertThat(sandbox.validatePath(submodule1.resolve("file.txt").toString()).allowed()).isTrue();
            assertThat(sandbox.validatePath(submodule2.resolve("file.txt").toString()).allowed()).isTrue();
        }

        @Test
        @DisplayName("should handle null RequestContext")
        void shouldHandleNullRequestContext() {
            WorktreeSandbox sandbox = WorktreeSandbox.fromRequestContext(null);

            assertThat(sandbox.allowedRoots()).isEmpty();
        }

        @Test
        @DisplayName("should handle RequestContext with null sandboxContext")
        void shouldHandleRequestContextWithNullSandboxContext() {
            RequestContext context = RequestContext.builder()
                    .sessionId("test-session")
                    .sandboxContext(null)
                    .build();

            WorktreeSandbox sandbox = WorktreeSandbox.fromRequestContext(context);

            assertThat(sandbox.allowedRoots()).isEmpty();
        }
    }

    @Nested
    @DisplayName("path normalization")
    class PathNormalizationTests {

        @Test
        @DisplayName("should normalize relative paths")
        void shouldNormalizeRelativePaths() {
            WorktreeSandbox sandbox = createSandbox(mainWorktree);

            // Path with ./ should be normalized
            SandboxValidationResult result = sandbox.validatePath(mainWorktree.resolve("./src/../src/main/java").toString());

            assertThat(result.allowed()).isTrue();
            assertThat(result.normalizedPath()).doesNotContain("./");
            assertThat(result.normalizedPath()).doesNotContain("../");
        }

        @Test
        @DisplayName("should handle paths with trailing slashes")
        void shouldHandlePathsWithTrailingSlashes() {
            WorktreeSandbox sandbox = createSandbox(mainWorktree);

            SandboxValidationResult result = sandbox.validatePath(mainWorktree.toString() + "/src/");

            assertThat(result.allowed()).isTrue();
        }
    }

    private WorktreeSandbox createSandbox(Path mainPath) {
        RequestContext context = RequestContext.builder()
                .sessionId("test")
                .sandboxContext(SandboxContext.builder()
                        .mainWorktreePath(mainPath)
                        .build())
                .build();
        return WorktreeSandbox.fromRequestContext(context);
    }

    private WorktreeSandbox createSandboxWithSubmodules(Path mainPath, Path... submodules) {
        RequestContext context = RequestContext.builder()
                .sessionId("test")
                .sandboxContext(SandboxContext.builder()
                        .mainWorktreePath(mainPath)
                        .submoduleWorktreePaths(List.of(submodules))
                        .build())
                .build();
        return WorktreeSandbox.fromRequestContext(context);
    }
}
