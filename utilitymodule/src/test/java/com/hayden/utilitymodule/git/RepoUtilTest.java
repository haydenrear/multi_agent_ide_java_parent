package com.hayden.utilitymodule.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class RepoUtilTest {

    @Test
    void doTest() {
        var f = RepoUtil.updateSubmodulesRecursively(Path.of("/Users/hayde/.multi-agent-ide/worktrees/f"));
        var unwrapped = f.unwrap();
        assertThat(unwrapped.size()).isNotZero();

    }

    @Test
    void initGit() throws IOException {
        var newTemp = Files.createTempDirectory("repo-util");
        RepoUtil.initGit(newTemp.resolve(".git"))
                .doOnClosable(git -> {
                    try {
                        commitFile(git, newTemp, "txt.txt");
                        commitFile(git, newTemp, "txt1.txt");
                        commitFile(git, newTemp, "txt2.txt");
                        commitFile(git, newTemp, "txt3.txt");


                        stageFile(git, newTemp, "another.txt");

                        var statusCall = git.status().call();

                        assertThat(statusCall.getUncommittedChanges().stream().anyMatch(s -> s.contains("another.txt"))).isTrue();

                        var o = RepoUtil.doInsideReset(git, "HEAD~1", () -> "ok");

                        assertThat(o.isOk()).isTrue();
                        var statusCall2 = git.status().call();
                        assertThat(statusCall2.getUncommittedChanges().size()).isEqualTo(1);
                        assertThat(statusCall2.getUncommittedChanges().stream().anyMatch(s -> s.contains("another.txt"))).isTrue();

                        git.add().addFilepattern(".").call();
                        git.commit().setMessage("commit").call();



                        stageFile(git, newTemp, "another-1.txt");

                        AtomicBoolean b = new AtomicBoolean(false);

                        o = RepoUtil.doInsideStash(git, () -> {

                            b.set(true);

                            assertThat(newTemp.resolve("another-1.txt").toFile().exists()).isFalse();

                            return "ok";

                        });

                        assertThat(newTemp.resolve("another-1.txt").toFile().exists()).isTrue();

                        assertThat(b.get()).isTrue();
                        assertThat(o.isOk()).isTrue();

                    } catch (IOException |
                             GitAPIException e) {
                        throw new AssertionError(e);
                    }
                });
    }

    private static void commitFile(Git git, Path newTemp, String other) throws IOException, GitAPIException {
        stageFile(git, newTemp, other);
        git.commit().setMessage("ok").call();
    }

    private static void stageFile(Git git, Path newTemp, String other) throws IOException, GitAPIException {
        File file = newTemp.resolve(other).toFile();
        var found = file.createNewFile();
        assertTrue(file.exists());
        git.add().addFilepattern(".").call();
        var statusCall = git.status().call();
        var foundChanges = statusCall.getUncommittedChanges();
        assertEquals(1, foundChanges.size());
    }

    @Test
    void getGitRepo() {
        var repo = RepoUtil.getGitRepo();
        assertThat(repo.toFile().exists()).isTrue();
        Assertions.assertThrows(RuntimeException.class, () -> RepoUtil.getGitRepo(Files.createTempDirectory("no-repo").toFile()));

    }

    @Test
    void updateSubmodulesRecursivelyHandlesNestedSubmodules() throws Exception {
        Path submoduleB = Files.createTempDirectory("submodule-b");
        initRepo(submoduleB);
        commitFile(submoduleB, "b.txt");

        Path submoduleA = Files.createTempDirectory("submodule-a");
        initRepo(submoduleA);
        commitFile(submoduleA, "a.txt");
        addSubmodule(submoduleA, submoduleB, "libs/sub-b");

        Path mainRepo = Files.createTempDirectory("main-repo");
        initRepo(mainRepo);
        commitFile(mainRepo, "README.md");
        addSubmodule(mainRepo, submoduleA, "libs/sub-a");

        var result = RepoUtil.updateSubmodulesRecursively(mainRepo);
        assertThat(result.isOk()).isTrue();
        List<String> updated = result.r().get();
        assertThat(updated).contains("libs/sub-a", "libs/sub-a/libs/sub-b");
        assertThat(mainRepo.resolve("libs/sub-a")).exists();
        assertThat(mainRepo.resolve("libs/sub-a/libs/sub-b")).exists();

        Path cloneRepo = Files.createTempDirectory("main-repo-clone");
        runGit(cloneRepo.getParent(), "git", "clone", mainRepo.toString(), cloneRepo.toString());

        var cloneResult = RepoUtil.updateSubmodulesRecursively(cloneRepo);
        assertThat(cloneResult.isOk()).isTrue();
        List<String> cloneUpdated = cloneResult.r().get();
        assertThat(cloneUpdated).contains("libs/sub-a", "libs/sub-a/libs/sub-b");
        assertThat(cloneRepo.resolve("libs/sub-a")).exists();
        assertThat(cloneRepo.resolve("libs/sub-a/libs/sub-b")).exists();
    }

    private static void initRepo(Path repoDir) throws Exception {
        runGit(repoDir, "git", "init", "-b", "main");
        runGit(repoDir, "git", "config", "user.email", "test@example.com");
        runGit(repoDir, "git", "config", "user.name", "Test User");
    }

    private static void commitFile(Path repoDir, String fileName) throws Exception {
        Path file = repoDir.resolve(fileName);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "content");
        commitAll(repoDir, "commit " + fileName);
    }

    private static void commitAll(Path repoDir, String message) throws Exception {
        runGit(repoDir, "git", "add", "-A");
        runGit(repoDir, "git", "commit", "-m", message);
    }

    private static void addSubmodule(Path mainRepo, Path subRepo, String submodulePath) throws Exception {
        runGit(mainRepo, "git", "-c", "protocol.file.allow=always",
                "submodule", "add", subRepo.toString(), submodulePath);
        commitAll(mainRepo, "add submodule");
    }

    private static void runGit(Path repoDir, String... command) throws Exception {
        gitOutput(repoDir, command);
    }

    private static String gitOutput(Path repoDir, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(repoDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("Git command failed: " + String.join(" ", command) + "\n" + output);
        }
        return output.toString();
    }
}
