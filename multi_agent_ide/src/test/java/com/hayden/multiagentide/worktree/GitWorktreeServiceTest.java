package com.hayden.multiagentide.worktree;

import static org.assertj.core.api.Assertions.assertThat;

import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentide.support.AgentTestBase;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "multiagentide.worktrees.base-path=${java.io.tmpdir}/multi-agent-ide-test-worktrees")
class GitWorktreeServiceTest extends AgentTestBase {

    private static final String WORKTREE_BASE =
        System.getProperty("java.io.tmpdir") + "/multi-agent-ide-test-worktrees";

    @Autowired
    private GitWorktreeService gitWorktreeService;

    @Autowired
    private WorktreeRepository worktreeRepository;

    @BeforeEach
    void setUp() throws Exception {
        worktreeRepository.clear();
        deleteRecursively(Path.of(WORKTREE_BASE));
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteRecursively(Path.of(WORKTREE_BASE));
    }

    @Test
    void createMainWorktreeClonesRepository() throws Exception {
        Path repoDir = Files.createTempDirectory("main-repo");
        initRepo(repoDir);
        commitFile(repoDir, "README.md", "hello", "init");

        MainWorktreeContext worktree = gitWorktreeService.createMainWorktree(
            repoDir.toString(),
            "main",
            "main-0",
            "node-main"
        );

        assertThat(Files.exists(worktree.worktreePath())).isTrue();
        assertThat(worktreeRepository.findById(worktree.worktreeId())).isPresent();

        String head = gitOutput(worktree.worktreePath(), "git", "rev-parse", "HEAD").trim();
        assertThat(worktree.lastCommitHash()).isEqualTo(head);
    }

    @Test
    void createSubmoduleWorktreeTracksSubmodule() throws Exception {
        Path subRepo = Files.createTempDirectory("submodule-repo");
        initRepo(subRepo);
        commitFile(subRepo, "lib.txt", "lib", "init submodule");

        Path mainRepo = Files.createTempDirectory("main-repo-with-sub");
        initRepo(mainRepo);
        commitFile(mainRepo, "README.md", "main", "init main");

        runGit(mainRepo, "git", "-c", "protocol.file.allow=always", "submodule", "add",
            subRepo.toString(), "libs/submodule-lib");
        commitAll(mainRepo, "add submodule");

        MainWorktreeContext mainWorktree = gitWorktreeService.createMainWorktree(
            mainRepo.toString(),
            "main",
            "main-0",
            "node-submodule"
        );

        List<SubmoduleWorktreeContext> submodules = gitWorktreeService.getSubmoduleWorktrees(mainWorktree.worktreeId());
        assertThat(submodules).isNotEmpty();

        SubmoduleWorktreeContext submoduleWorktree = submodules.getFirst();
        assertThat(Files.exists(submoduleWorktree.worktreePath())).isTrue();
        assertThat(submoduleWorktree.parentWorktreeId()).isEqualTo(mainWorktree.worktreeId());
    }

    private void initRepo(Path repoDir) throws Exception {
        runGit(repoDir, "git", "init", "-b", "main");
        runGit(repoDir, "git", "config", "user.email", "test@example.com");
        runGit(repoDir, "git", "config", "user.name", "Test User");
    }

    private void commitFile(Path repoDir, String fileName, String content, String message) throws Exception {
        Path file = repoDir.resolve(fileName);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        commitAll(repoDir, message);
    }

    private void commitAll(Path repoDir, String message) throws Exception {
        runGit(repoDir, "git", "add", "-A");
        runGit(repoDir, "git", "commit", "-m", message);
    }

    private void runGit(Path repoDir, String... command) throws Exception {
        gitOutput(repoDir, command);
    }

    private String gitOutput(Path repoDir, String... command) throws Exception {
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

    private void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.toList()) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
