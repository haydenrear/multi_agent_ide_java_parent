package com.hayden.multiagentide.perf.onboarding.support;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class OpenSourceRepositoryFactory {

    public ClonedRepository cloneRepository(String repoUrl,
                                            String branch,
                                            int cloneDepth,
                                            Path workspaceRoot) throws Exception {
        Path target = workspaceRoot.resolve(sanitizeRepoName(repoUrl) + "-" + System.currentTimeMillis());
        CloneCommand clone = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(target.toFile())
                .setCloneSubmodules(true);
        if (cloneDepth > 0) {
            clone.setDepth(cloneDepth);
        }
        if (branch != null && !branch.isBlank()) {
            clone.setBranch(branch.startsWith("refs/heads/") ? branch : "refs/heads/" + branch);
        }

        String actualBranch;
        try (Git git = clone.call()) {
            git.submoduleInit().call();
            git.submoduleUpdate().call();
            actualBranch = git.getRepository().getBranch();
        }

        return new ClonedRepository(target, actualBranch);
    }

    private static String sanitizeRepoName(String repoUrl) {
        String candidate = repoUrl;
        int slash = candidate.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < candidate.length()) {
            candidate = candidate.substring(slash + 1);
        }
        if (candidate.endsWith(".git")) {
            candidate = candidate.substring(0, candidate.length() - 4);
        }
        return candidate.replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    public record ClonedRepository(Path rootPath, String branch) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            if (!Files.exists(rootPath)) {
                return;
            }
            try (var walk = Files.walk(rootPath)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
        }
    }
}
