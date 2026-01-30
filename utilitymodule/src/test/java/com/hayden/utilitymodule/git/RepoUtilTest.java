package com.hayden.utilitymodule.git;

import org.assertj.core.util.Files;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class RepoUtilTest {

    @Test
    void initGit() {
        var newTemp = Files.newTemporaryFolder().toPath();
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
        Assertions.assertThrows(RuntimeException.class, () -> RepoUtil.getGitRepo(Files.newTemporaryFolder()));

    }
}