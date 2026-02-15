package com.hayden.multiagentide.integration.onboarding.support;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class RepositoryFixtureFactory {

    public Path createRepositoryWithSubmoduleFixture(Path root) throws IOException, GitAPIException {
        Path submoduleRepo = root.resolve("submodule-lib");
        Path mainRepo = root.resolve("main-repo");
        Files.createDirectories(submoduleRepo);
        Files.createDirectories(mainRepo);

        createCommittedRepo(submoduleRepo, "main", "lib.txt", "library-v1", "initial submodule");
        createCommittedRepo(mainRepo, "main", "README.md", "root", "initial root");

        try (Git main = Git.open(mainRepo.toFile())) {
            main.submoduleAdd()
                    .setPath("modules/submodule-lib")
                    .setURI(submoduleRepo.toUri().toString())
                    .call();
            main.add().addFilepattern(".gitmodules").addFilepattern("modules/submodule-lib").call();
            main.commit().setMessage("add submodule").setAuthor("test", "test@example.com")
                    .setCommitter("test", "test@example.com").call();
            Files.writeString(mainRepo.resolve("README.md"), "root-updated");
            main.add().addFilepattern("README.md").call();
            main.commit().setMessage("update root").setAuthor("test", "test@example.com")
                    .setCommitter("test", "test@example.com").call();
        }
        return mainRepo;
    }

    private static void createCommittedRepo(Path repoPath,
                                            String branch,
                                            String fileName,
                                            String contents,
                                            String message) throws GitAPIException, IOException {
        try (Git git = Git.init().setInitialBranch(branch).setDirectory(repoPath.toFile()).call()) {
            Files.writeString(repoPath.resolve(fileName), contents);
            git.add().addFilepattern(fileName).call();
            git.commit().setMessage(message).setAuthor("test", "test@example.com")
                    .setCommitter("test", "test@example.com").call();
        }
    }
}
