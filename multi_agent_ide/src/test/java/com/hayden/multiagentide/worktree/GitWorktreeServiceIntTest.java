package com.hayden.multiagentide.worktree;

import static org.assertj.core.api.Assertions.assertThat;

import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentide.support.AgentTestBase;
import com.hayden.multiagentidelib.model.MergeResult;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.merge.MergeDirection;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@SpringBootTest(properties = "multiagentide.worktrees.base-path=${java.io.tmpdir}/multi-agent-ide-test-worktrees")
@ActiveProfiles({"testdocker", "test"})
class GitWorktreeServiceIntTest extends AgentTestBase {

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
    @DisplayName("mergeWorktrees merges submodules and main without conflicts")
    void mergeWorktreesWithSubmodulesNoConflicts() throws Exception {
        Path subRepo = createRepoWithFile("submodule-repo", "lib.txt", "base", "init submodule");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subRepo, "libs/submodule-lib");
        initSubmodules(mainRepo);

        MainWorktreeContext trunk = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-trunk");
        SubmoduleWorktreeContext trunkSub = createSubmoduleWorktreeFromMain(trunk);
        assertClean(trunk.worktreePath());
        assertClean(trunkSub.worktreePath());

        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main","main-1", "node-child");
        SubmoduleWorktreeContext childSub = createSubmoduleWorktreeFromMain(child);
        assertClean(child.worktreePath());
        assertClean(childSub.worktreePath());

        commitFile(childSub.worktreePath(), "lib.txt", "child change", "update submodule");
        commitFile(child.worktreePath(), "README.md", "child change", "update main");
        assertClean(childSub.worktreePath());
        assertClean(child.worktreePath());

        MergeResult subResult = gitWorktreeService.mergeWorktrees(childSub.worktreeId(), trunkSub.worktreeId());
        assertThat(subResult.successful()).isTrue();
        gitWorktreeService.updateSubmodulePointer(trunk.worktreeId(), childSub.submoduleName());
        assertClean(trunkSub.worktreePath());
        assertClean(trunk.worktreePath());

        MergeResult mainResult = gitWorktreeService.mergeWorktrees(child.worktreeId(), trunk.worktreeId());
        assertThat(mainResult.successful()).isTrue();
        assertThat(mainResult.conflicts()).isEmpty();
        assertThat(Files.readString(trunk.worktreePath().resolve("README.md"))).contains("child change");
        assertClean(trunk.worktreePath());
    }

    @Test
    @DisplayName("mergeWorktrees reports conflicts for main and submodule")
    void mergeWorktreesWithConflicts() throws Exception {
        Path subRepo = createRepoWithFile("submodule-repo", "lib.txt", "base", "init submodule");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subRepo, "libs/submodule-lib");
        initSubmodules(mainRepo);

        MainWorktreeContext trunk = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-trunk");
        SubmoduleWorktreeContext trunkSub = createSubmoduleWorktreeFromMain(trunk);
        assertClean(trunk.worktreePath());
        assertClean(trunkSub.worktreePath());

        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");
        SubmoduleWorktreeContext childSub = createSubmoduleWorktreeFromMain(child);
        assertClean(child.worktreePath());
        assertClean(childSub.worktreePath());

        commitFile(trunkSub.worktreePath(), "lib.txt", "trunk change", "trunk sub edit");
        commitFile(childSub.worktreePath(), "lib.txt", "child change", "child sub edit");
        assertClean(trunkSub.worktreePath());
        assertClean(childSub.worktreePath());

        MergeResult subResult = gitWorktreeService.mergeWorktrees(childSub.worktreeId(), trunkSub.worktreeId());
        assertThat(subResult.successful()).isFalse();
        assertThat(subResult.conflicts()).isNotEmpty();
        assertConflicts(trunkSub.worktreePath(), "lib.txt");

        runGit(trunkSub.worktreePath(), "git", "add", "lib.txt");
        runGit(trunkSub.worktreePath(), "git", "commit", "-m", "updated");

        commitFile(trunk.worktreePath(), "README.md", "trunk change", "trunk main edit");
        commitFile(child.worktreePath(), "README.md", "child change", "child main edit");

        assertClean(trunk.worktreePath());
        assertClean(child.worktreePath());

        MergeResult mainResult = gitWorktreeService.mergeWorktrees(child.worktreeId(), trunk.worktreeId());
        assertThat(mainResult.successful()).isFalse();
        assertThat(mainResult.conflicts()).isNotEmpty();
        assertConflicts(trunk.worktreePath(), "README.md");
    }

    @Test
    @DisplayName("createSubmoduleWorktree initializes nested submodules and merges nested updates")
    void mergeWorktreesWithNestedSubmodules() throws Exception {
        Path submoduleB = createRepoWithFile("submodule-b", "b.txt", "base", "init submodule b");
        Path submoduleA = createRepoWithFile("submodule-a", "a.txt", "base", "init submodule a");
        addSubmodule(submoduleA, submoduleB, "libs/sub-b");
        initSubmodules(submoduleA);

        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, submoduleA, "libs/sub-a");
        initSubmodules(mainRepo);

        MainWorktreeContext trunk = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-trunk");
        SubmoduleWorktreeContext trunkSub = createSubmoduleWorktreeFromMain(trunk);
        Path trunkSubB = trunkSub.worktreePath().resolve("libs/sub-b");
        assertThat(trunkSubB).exists();

        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");
        SubmoduleWorktreeContext childSub = createSubmoduleWorktreeFromMain(child);
        Path childSubB = childSub.worktreePath().resolve("libs/sub-b");
        assertThat(childSubB).exists();

        configureUser(childSubB);
        commitFile(childSubB, "b.txt", "nested change", "nested commit");

        configureUser(childSub.worktreePath());
        runGit(childSub.worktreePath(), "git", "add", "libs/sub-b");
        runGit(childSub.worktreePath(), "git", "commit", "-m", "update nested submodule");

        MergeResult subResult = gitWorktreeService.mergeWorktrees(childSub.worktreeId(), trunkSub.worktreeId());
        assertThat(subResult.successful()).isTrue();
        assertThat(subResult.conflicts()).isEmpty();
        assertThat(trunkSubB).exists();
        assertClean(trunkSub.worktreePath());
    }

    @Test
    @DisplayName("mergeWorktrees merges parent to child for main and nested submodules")
    void mergeWorktreesParentToChildWithNestedSubmodules() throws Exception {
        Path submoduleB = createRepoWithFile("submodule-b", "b.txt", "base", "init submodule b");
        Path submoduleA = createRepoWithFile("submodule-a", "a.txt", "base", "init submodule a");
        addSubmodule(submoduleA, submoduleB, "libs/sub-b");
        initSubmodules(submoduleA);

        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, submoduleA, "libs/sub-a");
        initSubmodules(mainRepo);

        MainWorktreeContext parent = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-parent");
        SubmoduleWorktreeContext parentSub = createSubmoduleWorktreeFromMain(parent);
        Path parentSubB = parentSub.worktreePath().resolve("libs/sub-b");

        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");
        SubmoduleWorktreeContext childSub = createSubmoduleWorktreeFromMain(child);
        Path childSubB = childSub.worktreePath().resolve("libs/sub-b");

        configureUser(parentSubB);
        commitFile(parentSubB, "b.txt", "parent nested change", "parent nested commit");
        configureUser(parentSub.worktreePath());
        runGit(parentSub.worktreePath(), "git", "add", "libs/sub-b");
        runGit(parentSub.worktreePath(), "git", "commit", "-m", "update nested submodule");

        MergeResult subResult = gitWorktreeService.mergeWorktrees(parentSub.worktreeId(), childSub.worktreeId());
        assertThat(subResult.successful()).isTrue();
        assertThat(subResult.conflicts()).isEmpty();
        assertThat(childSubB).exists();

        configureUser(parent.worktreePath());
        commitFile(parent.worktreePath(), "README.md", "parent main change", "parent main commit");

        MergeResult mainResult = gitWorktreeService.mergeWorktrees(parent.worktreeId(), child.worktreeId());
        assertThat(mainResult.successful()).isTrue();
        assertThat(mainResult.conflicts()).isEmpty();
        assertThat(Files.readString(child.worktreePath().resolve("README.md"))).contains("parent main change");
        assertClean(child.worktreePath());
    }

    @Test
    @DisplayName("mergeWorktrees reports conflicts on round-trip with nested submodules")
    void mergeWorktreesRoundTripWithNestedSubmodules() throws Exception {
        Path submoduleB = createRepoWithFile("submodule-b", "b.txt", "base", "init submodule b");
        Path submoduleA = createRepoWithFile("submodule-a", "a.txt", "base", "init submodule a");
        addSubmodule(submoduleA, submoduleB, "libs/sub-b");
        initSubmodules(submoduleA);

        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, submoduleA, "libs/sub-a");
        initSubmodules(mainRepo);

        MainWorktreeContext parent = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-parent");
        SubmoduleWorktreeContext parentSub = createSubmoduleWorktreeFromMain(parent);
        Path parentSubB = parentSub.worktreePath().resolve("libs/sub-b");

        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");
        SubmoduleWorktreeContext childSub = createSubmoduleWorktreeFromMain(child);
        Path childSubB = childSub.worktreePath().resolve("libs/sub-b");

        configureUser(parentSubB);
        commitFile(parentSubB, "b.txt", "parent nested change", "parent nested commit");
        configureUser(parentSub.worktreePath());
        runGit(parentSub.worktreePath(), "git", "add", "libs/sub-b");
        runGit(parentSub.worktreePath(), "git", "commit", "-m", "update nested submodule");

        configureUser(parent.worktreePath());
        commitFile(parent.worktreePath(), "README.md", "parent main change", "parent main commit");

        configureUser(childSubB);
        commitFile(childSubB, "b.txt", "child nested change", "child nested commit");
        configureUser(childSub.worktreePath());
        runGit(childSub.worktreePath(), "git", "add", "libs/sub-b");
        runGit(childSub.worktreePath(), "git", "commit", "-m", "update nested submodule (child)");

        configureUser(child.worktreePath());
        commitFile(child.worktreePath(), "README.md", "child main change", "child main commit");

        MergeResult parentToChildSub = gitWorktreeService.mergeWorktrees(parentSub.worktreeId(), childSub.worktreeId());
        assertThat(parentToChildSub.successful()).isFalse();
        assertThat(parentToChildSub.conflicts()).isNotEmpty();
        assertThat(parentToChildSub.conflicts().stream()
                .map(MergeResult.MergeConflict::filePath)
                .toList()).contains("b.txt");
        assertThat(parentToChildSub.conflicts().stream()
                .map(MergeResult.MergeConflict::submodulePath)
                .toList()).contains("libs_sub-b");

    }

    @Test
    @DisplayName("mergeWorktrees continues merging sibling submodules when one conflicts")
    void mergeWorktreesSiblingSubmodulesOneConflict() throws Exception {
        Path subA = createRepoWithFile("sub-a", "a.txt", "base", "init sub-a");
        Path subB = createRepoWithFile("sub-b", "b.txt", "base", "init sub-b");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subA, "libs/sub-a");
        addSubmodule(mainRepo, subB, "libs/sub-b");
        initSubmodules(mainRepo);

        MainWorktreeContext parent = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-parent");
        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");

        SubmoduleWorktreeContext parentSubA = findSubmodule(parent, "libs/sub-a");
        SubmoduleWorktreeContext parentSubB = findSubmodule(parent, "libs/sub-b");
        SubmoduleWorktreeContext childSubA = findSubmodule(child, "libs/sub-a");
        SubmoduleWorktreeContext childSubB = findSubmodule(child, "libs/sub-b");

        configureUser(parentSubA.worktreePath());
        commitFile(parentSubA.worktreePath(), "a.txt", "parent change", "parent sub-a");
        configureUser(childSubA.worktreePath());
        commitFile(childSubA.worktreePath(), "a.txt", "child change", "child sub-a");

        configureUser(parentSubB.worktreePath());
        commitFile(parentSubB.worktreePath(), "b.txt", "parent change", "parent sub-b");

        MergeResult result = gitWorktreeService.mergeWorktrees(child.worktreeId(), parent.worktreeId());
        assertThat(result.successful()).isFalse();
        assertThat(result.conflicts()).isNotEmpty();
        assertThat(result.conflicts().stream()
                .map(MergeResult.MergeConflict::submodulePath)
                .toList()).contains("libs_sub-a");

        assertThat(Files.readString(parentSubB.worktreePath().resolve("b.txt")))
                .contains("parent change");
        assertClean(parentSubB.worktreePath());
    }

    @Test
    @DisplayName("mergeWorktrees merges sibling submodules and parent when no conflicts")
    void mergeWorktreesSiblingSubmodulesAndParentNoConflicts() throws Exception {
        Path subA = createRepoWithFile("sub-a", "a.txt", "base", "init sub-a");
        Path subB = createRepoWithFile("sub-b", "b.txt", "base", "init sub-b");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subA, "libs/sub-a");
        addSubmodule(mainRepo, subB, "libs/sub-b");
        initSubmodules(mainRepo);

        MainWorktreeContext parent = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-parent");
        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");

        SubmoduleWorktreeContext parentSubA = findSubmodule(parent, "libs/sub-a");
        SubmoduleWorktreeContext parentSubB = findSubmodule(parent, "libs/sub-b");
        SubmoduleWorktreeContext childSubA = findSubmodule(child, "libs/sub-a");
        SubmoduleWorktreeContext childSubB = findSubmodule(child, "libs/sub-b");

        configureUser(parentSubA.worktreePath());
        commitFile(parentSubA.worktreePath(), "a.txt", "parent change", "parent sub-a");
        configureUser(parentSubB.worktreePath());
        commitFile(parentSubB.worktreePath(), "b.txt", "parent change", "parent sub-b");

        configureUser(parent.worktreePath());
        commitFile(parent.worktreePath(), "README.md", "parent change", "parent main");

        MergeResult result = gitWorktreeService.mergeWorktrees(parent.worktreeId(), child.worktreeId());
        assertThat(result.successful()).isTrue();
        assertThat(result.conflicts()).isEmpty();

        String actual = Files.readString(childSubA.worktreePath().resolve("a.txt"));
        assertThat(actual).contains("parent change");
        assertThat(Files.readString(childSubB.worktreePath().resolve("b.txt"))).contains("parent change");
        assertThat(Files.readString(child.worktreePath().resolve("README.md"))).contains("parent change");
        assertClean(child.worktreePath());
        assertClean(childSubA.worktreePath());
        assertClean(childSubB.worktreePath());
    }

    @Test
    @DisplayName("finalMergeToSource merges derived branches back into source repo")
    void finalMergeToSourceWithSubmodulesNoConflicts() throws Exception {
        Path subRepo = createRepoWithFile("submodule-repo", "lib.txt", "base", "init submodule");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subRepo, "libs/submodule-lib");
        initSubmodules(mainRepo);

        // Verify source repo starts on "main"
        assertThat(gitOutput(mainRepo, "git", "branch", "--show-current").trim()).isEqualTo("main");

        // Create worktree with a derived branch (simulates orchestrator initialization)
        String derivedBranch = "main-derived-test";
        MainWorktreeContext worktree = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", derivedBranch, "node-final-merge");
        SubmoduleWorktreeContext worktreeSub = createSubmoduleWorktreeFromMain(worktree);

        // Make changes in the worktree on the derived branch
        configureUser(worktreeSub.worktreePath());
        commitFile(worktreeSub.worktreePath(), "lib.txt", "agent submodule change", "agent sub commit");

        configureUser(worktree.worktreePath());
        // Stage the submodule pointer change
        runGit(worktree.worktreePath(), "git", "add", worktreeSub.submoduleName());
        runGit(worktree.worktreePath(), "git", "commit", "-m", "update submodule pointer");
        commitFile(worktree.worktreePath(), "README.md", "agent main change", "agent main commit");

        assertClean(worktree.worktreePath());
        assertClean(worktreeSub.worktreePath());

        // Final merge back to source
        MergeResult result = gitWorktreeService.finalMergeToSource(worktree.worktreeId());
        assertThat(result.successful()).isTrue();
        assertThat(result.conflicts()).isEmpty();

        // Verify source repo (on "main") now contains the agent's changes
        assertThat(gitOutput(mainRepo, "git", "branch", "--show-current").trim()).isEqualTo("main");
        assertThat(Files.readString(mainRepo.resolve("README.md"))).contains("agent main change");

        // Verify source submodule has the change
        Path sourceSubPath = mainRepo.resolve("libs/submodule-lib");
        assertThat(Files.readString(sourceSubPath.resolve("lib.txt"))).contains("agent submodule change");
        assertClean(mainRepo);
    }

    @Test
    @DisplayName("finalMergeToSource reports conflicts when source diverged")
    void finalMergeToSourceWithConflicts() throws Exception {
        Path subRepo = createRepoWithFile("submodule-repo", "lib.txt", "base", "init submodule");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subRepo, "libs/submodule-lib");
        initSubmodules(mainRepo);

        // Create worktree with a derived branch
        String derivedBranch = "main-derived-conflict";
        MainWorktreeContext worktree = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", derivedBranch, "node-conflict");

        // Make a conflicting change directly in the source repo on "main"
        configureUser(mainRepo);
        commitFile(mainRepo, "README.md", "source diverged change", "source commit");

        // Make a conflicting change in the worktree
        configureUser(worktree.worktreePath());
        commitFile(worktree.worktreePath(), "README.md", "agent conflicting change", "agent commit");

        assertClean(worktree.worktreePath());
        assertClean(mainRepo);

        // Final merge should report conflicts
        MergeResult result = gitWorktreeService.finalMergeToSource(worktree.worktreeId());
        assertThat(result.successful()).isFalse();
        assertThat(result.conflicts()).isNotEmpty();
    }

    @Test
    @DisplayName("finalMergeToSource reports conflicts in submodules")
    void finalMergeToSourceWithSubmoduleConflicts() throws Exception {
        Path subRepo = createRepoWithFile("submodule-repo", "lib.txt", "base", "init submodule");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subRepo, "libs/submodule-lib");
        initSubmodules(mainRepo);

        String derivedBranch = "main-derived-sub-conflict";
        MainWorktreeContext worktree = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", derivedBranch, "node-sub-conflict");
        SubmoduleWorktreeContext worktreeSub = createSubmoduleWorktreeFromMain(worktree);

        Path sourceSubPath = mainRepo.resolve("libs/submodule-lib");
        configureUser(sourceSubPath);
        commitFile(sourceSubPath, "lib.txt", "source change", "source sub edit");
        configureUser(mainRepo);
        runGit(mainRepo, "git", "add", "libs/submodule-lib");
        runGit(mainRepo, "git", "commit", "-m", "update submodule pointer (source)");

        configureUser(worktreeSub.worktreePath());
        commitFile(worktreeSub.worktreePath(), "lib.txt", "worktree change", "worktree sub edit");
        configureUser(worktree.worktreePath());
        runGit(worktree.worktreePath(), "git", "add", worktreeSub.submoduleName());
        runGit(worktree.worktreePath(), "git", "commit", "-m", "update submodule pointer (worktree)");

        MergeResult result = gitWorktreeService.finalMergeToSource(worktree.worktreeId());
        assertThat(result.successful()).isFalse();
        assertThat(result.conflicts()).isNotEmpty();
        assertThat(result.conflicts().stream()
                .map(MergeResult.MergeConflict::submodulePath)
                .toList()).contains("libs_submodule-lib");
        assertConflicts(sourceSubPath, "lib.txt");
    }

    @Test
    @DisplayName("finalMergeToSource merges nested submodules without conflicts")
    void finalMergeToSourceWithNestedSubmodulesNoConflicts() throws Exception {
        Path submoduleB = createRepoWithFile("submodule-b", "b.txt", "base", "init submodule b");
        Path submoduleA = createRepoWithFile("submodule-a", "a.txt", "base", "init submodule a");
        addSubmodule(submoduleA, submoduleB, "libs/sub-b");
        initSubmodules(submoduleA);

        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, submoduleA, "libs/sub-a");
        initSubmodules(mainRepo);

        String derivedBranch = "main-derived-nested";
        MainWorktreeContext worktree = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", derivedBranch, "node-final-nested");
        SubmoduleWorktreeContext worktreeSubA = findSubmodule(worktree, "libs/sub-a");
        Path worktreeSubB = worktreeSubA.worktreePath().resolve("libs/sub-b");

        configureUser(worktreeSubB);
        commitFile(worktreeSubB, "b.txt", "nested change", "nested commit");
        configureUser(worktreeSubA.worktreePath());
        runGit(worktreeSubA.worktreePath(), "git", "add", "libs/sub-b");
        runGit(worktreeSubA.worktreePath(), "git", "commit", "-m", "update nested submodule");

        configureUser(worktree.worktreePath());
        runGit(worktree.worktreePath(), "git", "add", "libs/sub-a");
        runGit(worktree.worktreePath(), "git", "commit", "-m", "update submodule pointer");

        MergeResult result = gitWorktreeService.finalMergeToSource(worktree.worktreeId());
        assertThat(result.successful()).isTrue();
        assertThat(result.conflicts()).isEmpty();

        Path sourceSubB = mainRepo.resolve("libs/sub-a").resolve("libs/sub-b");
        assertThat(Files.readString(sourceSubB.resolve("b.txt"))).contains("nested change");
        assertClean(mainRepo);
        assertClean(mainRepo.resolve("libs/sub-a"));
        assertClean(sourceSubB);
    }

    @Test
    @DisplayName("finalMergeToSource merges when source is ahead with nested submodules")
    void finalMergeToSourceParentToChildWithNestedSubmodules() throws Exception {
        Path submoduleB = createRepoWithFile("submodule-b", "b.txt", "base", "init submodule b");
        Path submoduleA = createRepoWithFile("submodule-a", "a.txt", "base", "init submodule a");
        addSubmodule(submoduleA, submoduleB, "libs/sub-b");
        initSubmodules(submoduleA);

        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, submoduleA, "libs/sub-a");
        initSubmodules(mainRepo);

        String derivedBranch = "main-derived-parent-ahead";
        MainWorktreeContext worktree = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", derivedBranch, "node-final-parent");

        Path sourceSubB = mainRepo.resolve("libs/sub-a").resolve("libs/sub-b");
        configureUser(sourceSubB);
        commitFile(sourceSubB, "b.txt", "source nested change", "source nested commit");
        Path sourceSubA = mainRepo.resolve("libs/sub-a");
        configureUser(sourceSubA);
        runGit(sourceSubA, "git", "add", "libs/sub-b");
        runGit(sourceSubA, "git", "commit", "-m", "update nested submodule (source)");
        configureUser(mainRepo);
        runGit(mainRepo, "git", "add", "libs/sub-a");
        runGit(mainRepo, "git", "commit", "-m", "update submodule pointer (source)");

        configureUser(worktree.worktreePath());
        commitFile(worktree.worktreePath(), "README.md", "worktree main change", "worktree main commit");

        MergeResult result = gitWorktreeService.finalMergeToSource(worktree.worktreeId());
        assertThat(result.successful()).isTrue();
        assertThat(result.conflicts()).isEmpty();
        assertThat(Files.readString(mainRepo.resolve("README.md"))).contains("worktree main change");
        assertThat(Files.readString(sourceSubB.resolve("b.txt"))).contains("source nested change");
        assertClean(mainRepo);
        assertClean(sourceSubA);
        assertClean(sourceSubB);
    }

    @Test
    @DisplayName("finalMergeToSource reports conflicts on round-trip with nested submodules")
    void finalMergeToSourceRoundTripWithNestedSubmodules() throws Exception {
        Path submoduleB = createRepoWithFile("submodule-b", "b.txt", "base", "init submodule b");
        Path submoduleA = createRepoWithFile("submodule-a", "a.txt", "base", "init submodule a");
        addSubmodule(submoduleA, submoduleB, "libs/sub-b");
        initSubmodules(submoduleA);

        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, submoduleA, "libs/sub-a");
        initSubmodules(mainRepo);

        String derivedBranch = "main-derived-roundtrip";
        MainWorktreeContext worktree = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", derivedBranch, "node-final-roundtrip");
        SubmoduleWorktreeContext worktreeSubA = findSubmodule(worktree, "libs/sub-a");
        Path worktreeSubB = worktreeSubA.worktreePath().resolve("libs/sub-b");

        Path sourceSubB = mainRepo.resolve("libs/sub-a").resolve("libs/sub-b");
        configureUser(sourceSubB);
        commitFile(sourceSubB, "b.txt", "source nested change", "source nested commit");
        Path sourceSubA = mainRepo.resolve("libs/sub-a");
        configureUser(sourceSubA);
        runGit(sourceSubA, "git", "add", "libs/sub-b");
        runGit(sourceSubA, "git", "commit", "-m", "update nested submodule (source)");
        configureUser(mainRepo);
        runGit(mainRepo, "git", "add", "libs/sub-a");
        runGit(mainRepo, "git", "commit", "-m", "update submodule pointer (source)");

        configureUser(worktreeSubB);
        commitFile(worktreeSubB, "b.txt", "worktree nested change", "worktree nested commit");
        configureUser(worktreeSubA.worktreePath());
        runGit(worktreeSubA.worktreePath(), "git", "add", "libs/sub-b");
        runGit(worktreeSubA.worktreePath(), "git", "commit", "-m", "update nested submodule (worktree)");
        configureUser(worktree.worktreePath());
        runGit(worktree.worktreePath(), "git", "add", "libs/sub-a");
        runGit(worktree.worktreePath(), "git", "commit", "-m", "update submodule pointer (worktree)");

        MergeResult result = gitWorktreeService.finalMergeToSource(worktree.worktreeId());
        assertThat(result.successful()).isFalse();
        assertThat(result.conflicts()).isNotEmpty();
        assertThat(result.conflicts().stream()
                .map(MergeResult.MergeConflict::submodulePath)
                .toList()).contains("libs_sub-a_libs_sub-b");
        assertConflicts(sourceSubB, "b.txt");
    }

    @Test
    @DisplayName("finalMergeToSource continues merging sibling submodules when one conflicts")
    void finalMergeToSourceSiblingSubmodulesOneConflict() throws Exception {
        Path subA = createRepoWithFile("sub-a", "a.txt", "base", "init sub-a");
        Path subB = createRepoWithFile("sub-b", "b.txt", "base", "init sub-b");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subA, "libs/sub-a");
        addSubmodule(mainRepo, subB, "libs/sub-b");
        initSubmodules(mainRepo);

        String derivedBranch = "main-derived-sibling-conflict";
        MainWorktreeContext worktree = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", derivedBranch, "node-final-siblings");

        SubmoduleWorktreeContext worktreeSubA = findSubmodule(worktree, "libs/sub-a");
        SubmoduleWorktreeContext worktreeSubB = findSubmodule(worktree, "libs/sub-b");

        Path sourceSubA = mainRepo.resolve("libs/sub-a");
        configureUser(sourceSubA);
        commitFile(sourceSubA, "a.txt", "source change", "source sub-a");
        configureUser(mainRepo);
        runGit(mainRepo, "git", "add", "libs/sub-a");
        runGit(mainRepo, "git", "commit", "-m", "update submodule pointer (source sub-a)");

        configureUser(worktreeSubA.worktreePath());
        commitFile(worktreeSubA.worktreePath(), "a.txt", "worktree change", "worktree sub-a");
        configureUser(worktreeSubB.worktreePath());
        commitFile(worktreeSubB.worktreePath(), "b.txt", "worktree change", "worktree sub-b");

        configureUser(worktree.worktreePath());
        runGit(worktree.worktreePath(), "git", "add", "libs/sub-a");
        runGit(worktree.worktreePath(), "git", "add", "libs/sub-b");
        runGit(worktree.worktreePath(), "git", "commit", "-m", "update submodule pointers (worktree)");

        MergeResult result = gitWorktreeService.finalMergeToSource(worktree.worktreeId());
        assertThat(result.successful()).isFalse();
        assertThat(result.conflicts()).isNotEmpty();
        assertThat(result.conflicts().stream()
                .map(MergeResult.MergeConflict::submodulePath)
                .toList()).contains("libs_sub-a");

        Path sourceSubB = mainRepo.resolve("libs/sub-b");
        assertThat(Files.readString(sourceSubB.resolve("b.txt"))).contains("worktree change");
        assertClean(sourceSubB);
    }

    @Test
    @DisplayName("finalMergeToSource merges sibling submodules and parent when no conflicts")
    void finalMergeToSourceSiblingSubmodulesAndParentNoConflicts() throws Exception {
        Path subA = createRepoWithFile("sub-a", "a.txt", "base", "init sub-a");
        Path subB = createRepoWithFile("sub-b", "b.txt", "base", "init sub-b");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subA, "libs/sub-a");
        addSubmodule(mainRepo, subB, "libs/sub-b");
        initSubmodules(mainRepo);

        String derivedBranch = "main-derived-sibling-ok";
        MainWorktreeContext worktree = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", derivedBranch, "node-final-siblings-ok");

        SubmoduleWorktreeContext worktreeSubA = findSubmodule(worktree, "libs/sub-a");
        SubmoduleWorktreeContext worktreeSubB = findSubmodule(worktree, "libs/sub-b");

        configureUser(worktreeSubA.worktreePath());
        commitFile(worktreeSubA.worktreePath(), "a.txt", "worktree change", "worktree sub-a");
        configureUser(worktreeSubB.worktreePath());
        commitFile(worktreeSubB.worktreePath(), "b.txt", "worktree change", "worktree sub-b");

        configureUser(worktree.worktreePath());
        runGit(worktree.worktreePath(), "git", "add", "libs/sub-a");
        runGit(worktree.worktreePath(), "git", "add", "libs/sub-b");
        runGit(worktree.worktreePath(), "git", "commit", "-m", "update submodule pointers (worktree)");
        commitFile(worktree.worktreePath(), "README.md", "worktree main change", "worktree main");

        MergeResult result = gitWorktreeService.finalMergeToSource(worktree.worktreeId());
        assertThat(result.successful()).isTrue();
        assertThat(result.conflicts()).isEmpty();

        Path sourceSubA = mainRepo.resolve("libs/sub-a");
        Path sourceSubB = mainRepo.resolve("libs/sub-b");
        assertThat(Files.readString(sourceSubA.resolve("a.txt"))).contains("worktree change");
        assertThat(Files.readString(sourceSubB.resolve("b.txt"))).contains("worktree change");
        assertThat(Files.readString(mainRepo.resolve("README.md"))).contains("worktree main change");
        assertClean(mainRepo);
        assertClean(sourceSubA);
        assertClean(sourceSubB);
    }

    @Test
    @DisplayName("ensureMergeConflictsCaptured returns unchanged result when merge is clean")
    void ensureMergeConflictsCapturedNoConflicts() throws Exception {
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");

        MainWorktreeContext parent = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-parent");
        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");

        commitFile(child.worktreePath(), "README.md", "child change", "child commit");

        MergeResult result = gitWorktreeService.mergeWorktrees(child.worktreeId(), parent.worktreeId());
        assertThat(result.successful()).isTrue();

        MergeResult validated = gitWorktreeService.ensureMergeConflictsCaptured(result);
        assertThat(validated.successful()).isTrue();
        assertThat(validated.conflicts()).isEmpty();
    }

    @Test
    @DisplayName("ensureMergeConflictsCaptured detects unresolved conflicts")
    void ensureMergeConflictsCapturedDetectsConflicts() throws Exception {
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");

        MainWorktreeContext parent = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-parent");
        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");

        commitFile(parent.worktreePath(), "README.md", "parent change", "parent commit");
        commitFile(child.worktreePath(), "README.md", "child change", "child commit");

        gitWorktreeService.mergeWorktrees(child.worktreeId(), parent.worktreeId());

        MergeResult optimistic = new MergeResult(
                "merge-id",
                child.worktreeId(),
                parent.worktreeId(),
                child.worktreePath().toString(),
                parent.worktreePath().toString(),
                true,
                null,
                List.of(),
                List.of(),
                "Merge successful",
                Instant.now()
        );

        MergeResult validated = gitWorktreeService.ensureMergeConflictsCaptured(optimistic);
        assertThat(validated.successful()).isFalse();
        assertThat(validated.conflicts().stream()
                .map(MergeResult.MergeConflict::filePath)
                .toList()).contains("README.md");
    }

    @Test
    @DisplayName("ensureMergeConflictsCaptured detects missing child commit")
    void ensureMergeConflictsCapturedDetectsMissingCommit() throws Exception {
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");

        MainWorktreeContext parent = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-parent");
        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");

        commitFile(child.worktreePath(), "README.md", "child change", "child commit");

        MergeResult optimistic = new MergeResult(
                "merge-id",
                child.worktreeId(),
                parent.worktreeId(),
                child.worktreePath().toString(),
                parent.worktreePath().toString(),
                true,
                null,
                List.of(),
                List.of(),
                "Merge successful",
                Instant.now()
        );

        MergeResult validated = gitWorktreeService.ensureMergeConflictsCaptured(optimistic);
        assertThat(validated.successful()).isFalse();
        assertThat(validated.conflicts().stream()
                .map(MergeResult.MergeConflict::filePath)
                .toList()).contains(parent.worktreePath().toString());
    }

    @Test
    @DisplayName("ensureMergeConflictsCaptured detects missing submodule commits on final merge")
    void ensureMergeConflictsCapturedDetectsMissingSubmoduleCommit() throws Exception {
        Path subRepo = createRepoWithFile("submodule-repo", "lib.txt", "base", "init submodule");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subRepo, "libs/submodule-lib");
        initSubmodules(mainRepo);

        MainWorktreeContext worktree = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-derived", "node-final");
        SubmoduleWorktreeContext worktreeSub = createSubmoduleWorktreeFromMain(worktree);

        commitFile(worktreeSub.worktreePath(), "lib.txt", "worktree change", "worktree sub commit");

        MergeResult optimistic = new MergeResult(
                "merge-id",
                worktree.worktreeId(),
                "source",
                worktree.worktreePath().toString(),
                mainRepo.toString(),
                true,
                null,
                List.of(),
                List.of(),
                "Merge successful",
                Instant.now()
        );

        MergeResult validated = gitWorktreeService.ensureMergeConflictsCaptured(optimistic, worktree);
        assertThat(validated.successful()).isFalse();
        assertThat(validated.conflicts().stream()
                .map(MergeResult.MergeConflict::filePath)
                .toList()).contains(worktreeSub.submoduleName());
    }

    // ========================================================================
    // MergeDescriptor integration tests for mergeTrunkToChild
    // ========================================================================

    @Test
    @DisplayName("mergeTrunkToChild succeeds with multiple submodules including pointer updates")
    void mergeTrunkToChildMultipleSubmodulesSuccess() throws Exception {
        Path subA = createRepoWithFile("sub-a", "a.txt", "base", "init sub-a");
        Path subB = createRepoWithFile("sub-b", "b.txt", "base", "init sub-b");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subA, "libs/sub-a");
        addSubmodule(mainRepo, subB, "libs/sub-b");
        initSubmodules(mainRepo);

        MainWorktreeContext trunk = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-trunk");
        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");

        SubmoduleWorktreeContext trunkSubA = findSubmodule(trunk, "libs/sub-a");
        SubmoduleWorktreeContext trunkSubB = findSubmodule(trunk, "libs/sub-b");

        configureUser(trunkSubA.worktreePath());
        commitFile(trunkSubA.worktreePath(), "a.txt", "trunk change a", "trunk sub-a");
        configureUser(trunkSubB.worktreePath());
        commitFile(trunkSubB.worktreePath(), "b.txt", "trunk change b", "trunk sub-b");
        configureUser(trunk.worktreePath());
        commitFile(trunk.worktreePath(), "README.md", "trunk main change", "trunk main");

        WorktreeSandboxContext trunkCtx = buildSandboxContext(trunk);
        WorktreeSandboxContext childCtx = buildSandboxContext(child);

        MergeDescriptor descriptor = gitWorktreeService.mergeTrunkToChild(trunkCtx, childCtx);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.mergeDirection()).isEqualTo(MergeDirection.TRUNK_TO_CHILD);
        assertThat(descriptor.successful()).isTrue();
        assertThat(descriptor.conflictFiles()).isEmpty();
        assertThat(descriptor.errorMessage()).isNull();
        assertThat(descriptor.mainWorktreeMergeResult()).isNotNull();
        assertThat(descriptor.mainWorktreeMergeResult().successful()).isTrue();

        SubmoduleWorktreeContext childSubA = findSubmodule(child, "libs/sub-a");
        SubmoduleWorktreeContext childSubB = findSubmodule(child, "libs/sub-b");
        assertThat(Files.readString(childSubA.worktreePath().resolve("a.txt"))).contains("trunk change a");
        assertThat(Files.readString(childSubB.worktreePath().resolve("b.txt"))).contains("trunk change b");
        assertThat(Files.readString(child.worktreePath().resolve("README.md"))).contains("trunk main change");
    }

    @Test
    @DisplayName("mergeTrunkToChild succeeds with nested submodules")
    void mergeTrunkToChildNestedSubmodulesSuccess() throws Exception {
        Path submoduleB = createRepoWithFile("submodule-b", "b.txt", "base", "init submodule b");
        Path submoduleA = createRepoWithFile("submodule-a", "a.txt", "base", "init submodule a");
        addSubmodule(submoduleA, submoduleB, "libs/sub-b");
        initSubmodules(submoduleA);

        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, submoduleA, "libs/sub-a");
        initSubmodules(mainRepo);

        MainWorktreeContext trunk = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-trunk");
        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");

        SubmoduleWorktreeContext trunkSubA = findSubmodule(trunk, "libs/sub-a");
        Path trunkSubB = trunkSubA.worktreePath().resolve("libs/sub-b");

        configureUser(trunkSubB);
        commitFile(trunkSubB, "b.txt", "trunk nested change", "trunk nested commit");
        configureUser(trunkSubA.worktreePath());
        runGit(trunkSubA.worktreePath(), "git", "add", "libs/sub-b");
        runGit(trunkSubA.worktreePath(), "git", "commit", "-m", "update nested submodule");
        configureUser(trunk.worktreePath());
        commitFile(trunk.worktreePath(), "README.md", "trunk main change", "trunk main commit");

        WorktreeSandboxContext trunkCtx = buildSandboxContext(trunk);
        WorktreeSandboxContext childCtx = buildSandboxContext(child);

        MergeDescriptor descriptor = gitWorktreeService.mergeTrunkToChild(trunkCtx, childCtx);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.mergeDirection()).isEqualTo(MergeDirection.TRUNK_TO_CHILD);
        assertThat(descriptor.successful()).isTrue();
        assertThat(descriptor.conflictFiles()).isEmpty();

        SubmoduleWorktreeContext childSubA = findSubmodule(child, "libs/sub-a");
        Path childSubB = childSubA.worktreePath().resolve("libs/sub-b");
        assertThat(Files.readString(childSubB.resolve("b.txt"))).contains("trunk nested change");
        assertThat(Files.readString(child.worktreePath().resolve("README.md"))).contains("trunk main change");
    }

    // ========================================================================
    // MergeDescriptor integration tests for mergeChildToTrunk
    // ========================================================================

    @Test
    @DisplayName("mergeChildToTrunk succeeds with multiple submodules including pointer updates")
    void mergeChildToTrunkMultipleSubmodulesSuccess() throws Exception {
        Path subA = createRepoWithFile("sub-a", "a.txt", "base", "init sub-a");
        Path subB = createRepoWithFile("sub-b", "b.txt", "base", "init sub-b");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subA, "libs/sub-a");
        addSubmodule(mainRepo, subB, "libs/sub-b");
        initSubmodules(mainRepo);

        MainWorktreeContext trunk = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-trunk");
        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");

        SubmoduleWorktreeContext childSubA = findSubmodule(child, "libs/sub-a");
        SubmoduleWorktreeContext childSubB = findSubmodule(child, "libs/sub-b");

        configureUser(childSubA.worktreePath());
        commitFile(childSubA.worktreePath(), "a.txt", "child change a", "child sub-a");
        configureUser(childSubB.worktreePath());
        commitFile(childSubB.worktreePath(), "b.txt", "child change b", "child sub-b");
        configureUser(child.worktreePath());
        commitFile(child.worktreePath(), "README.md", "child main change", "child main");

        WorktreeSandboxContext childCtx = buildSandboxContext(child);
        WorktreeSandboxContext trunkCtx = buildSandboxContext(trunk);

        MergeDescriptor descriptor = gitWorktreeService.mergeChildToTrunk(childCtx, trunkCtx);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.mergeDirection()).isEqualTo(MergeDirection.CHILD_TO_TRUNK);
        assertThat(descriptor.successful()).isTrue();
        assertThat(descriptor.conflictFiles()).isEmpty();
        assertThat(descriptor.errorMessage()).isNull();
        assertThat(descriptor.mainWorktreeMergeResult()).isNotNull();
        assertThat(descriptor.mainWorktreeMergeResult().successful()).isTrue();

        SubmoduleWorktreeContext trunkSubA = findSubmodule(trunk, "libs/sub-a");
        SubmoduleWorktreeContext trunkSubB = findSubmodule(trunk, "libs/sub-b");
        assertThat(Files.readString(trunkSubA.worktreePath().resolve("a.txt"))).contains("child change a");
        assertThat(Files.readString(trunkSubB.worktreePath().resolve("b.txt"))).contains("child change b");
        assertThat(Files.readString(trunk.worktreePath().resolve("README.md"))).contains("child main change");
    }

    @Test
    @DisplayName("mergeChildToTrunk succeeds with nested submodules")
    void mergeChildToTrunkNestedSubmodulesSuccess() throws Exception {
        Path submoduleB = createRepoWithFile("submodule-b", "b.txt", "base", "init submodule b");
        Path submoduleA = createRepoWithFile("submodule-a", "a.txt", "base", "init submodule a");
        addSubmodule(submoduleA, submoduleB, "libs/sub-b");
        initSubmodules(submoduleA);

        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, submoduleA, "libs/sub-a");
        initSubmodules(mainRepo);

        MainWorktreeContext trunk = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-trunk");
        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");

        SubmoduleWorktreeContext childSubA = findSubmodule(child, "libs/sub-a");
        Path childSubB = childSubA.worktreePath().resolve("libs/sub-b");

        configureUser(childSubB);
        commitFile(childSubB, "b.txt", "child nested change", "child nested commit");
        configureUser(childSubA.worktreePath());
        runGit(childSubA.worktreePath(), "git", "add", "libs/sub-b");
        runGit(childSubA.worktreePath(), "git", "commit", "-m", "update nested submodule");
        configureUser(child.worktreePath());
        commitFile(child.worktreePath(), "README.md", "child main change", "child main commit");

        WorktreeSandboxContext childCtx = buildSandboxContext(child);
        WorktreeSandboxContext trunkCtx = buildSandboxContext(trunk);

        MergeDescriptor descriptor = gitWorktreeService.mergeChildToTrunk(childCtx, trunkCtx);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.mergeDirection()).isEqualTo(MergeDirection.CHILD_TO_TRUNK);
        assertThat(descriptor.successful()).isTrue();
        assertThat(descriptor.conflictFiles()).isEmpty();

        SubmoduleWorktreeContext trunkSubA = findSubmodule(trunk, "libs/sub-a");
        Path trunkSubB = trunkSubA.worktreePath().resolve("libs/sub-b");
        assertThat(Files.readString(trunkSubB.resolve("b.txt"))).contains("child nested change");
        assertThat(Files.readString(trunk.worktreePath().resolve("README.md"))).contains("child main change");
    }

    @Test
    @DisplayName("mergeChildToTrunk reports conflict for one sibling, other sibling still merged - flat case")
    void mergeChildToTrunkSiblingConflictFlat() throws Exception {
        Path subA = createRepoWithFile("sub-a", "a.txt", "base", "init sub-a");
        Path subB = createRepoWithFile("sub-b", "b.txt", "base", "init sub-b");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subA, "libs/sub-a");
        addSubmodule(mainRepo, subB, "libs/sub-b");
        initSubmodules(mainRepo);

        MainWorktreeContext trunk = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-trunk");
        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");

        SubmoduleWorktreeContext trunkSubA = findSubmodule(trunk, "libs/sub-a");
        SubmoduleWorktreeContext childSubA = findSubmodule(child, "libs/sub-a");
        SubmoduleWorktreeContext childSubB = findSubmodule(child, "libs/sub-b");
        SubmoduleWorktreeContext trunkSubB = findSubmodule(trunk, "libs/sub-b");

        // Create conflict on sub-a
        configureUser(trunkSubA.worktreePath());
        commitFile(trunkSubA.worktreePath(), "a.txt", "trunk change", "trunk sub-a");
        configureUser(childSubA.worktreePath());
        commitFile(childSubA.worktreePath(), "a.txt", "child change", "child sub-a");

        // Non-conflicting change on sub-b
        configureUser(childSubB.worktreePath());
        commitFile(childSubB.worktreePath(), "b.txt", "child change b", "child sub-b");

        WorktreeSandboxContext childCtx = buildSandboxContext(child);
        WorktreeSandboxContext trunkCtx = buildSandboxContext(trunk);

        MergeDescriptor descriptor = gitWorktreeService.mergeChildToTrunk(childCtx, trunkCtx);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.mergeDirection()).isEqualTo(MergeDirection.CHILD_TO_TRUNK);
        assertThat(descriptor.successful()).isFalse();
        assertThat(descriptor.conflictFiles()).isNotEmpty();
        // sub-a should be in conflict identifiers
        assertThat(descriptor.mainWorktreeMergeResult().conflicts().stream()
                .map(MergeResult.MergeConflict::submodulePath)
                .toList()).contains("libs_sub-a");

        // sub-b should still have been merged (sibling continuation)
        assertThat(Files.readString(trunkSubB.worktreePath().resolve("b.txt"))).contains("child change b");
        assertClean(trunkSubB.worktreePath());
    }

    @Test
    @DisplayName("mergeChildToTrunk reports conflict for one sibling in nested case - deep failure blocks parent")
    void mergeChildToTrunkNestedDeepConflictBlocksParent() throws Exception {
        Path submoduleB = createRepoWithFile("submodule-b", "b.txt", "base", "init submodule b");
        Path submoduleA = createRepoWithFile("submodule-a", "a.txt", "base", "init submodule a");
        addSubmodule(submoduleA, submoduleB, "libs/sub-b");
        initSubmodules(submoduleA);

        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, submoduleA, "libs/sub-a");
        initSubmodules(mainRepo);

        MainWorktreeContext trunk = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-trunk");
        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");

        SubmoduleWorktreeContext trunkSubA = findSubmodule(trunk, "libs/sub-a");
        SubmoduleWorktreeContext childSubA = findSubmodule(child, "libs/sub-a");
        Path trunkSubB = trunkSubA.worktreePath().resolve("libs/sub-b");
        Path childSubB = childSubA.worktreePath().resolve("libs/sub-b");

        // Create deep conflict in sub-b (nested inside sub-a)
        configureUser(trunkSubB);
        commitFile(trunkSubB, "b.txt", "trunk nested change", "trunk nested commit");
        configureUser(trunkSubA.worktreePath());
        runGit(trunkSubA.worktreePath(), "git", "add", "libs/sub-b");
        runGit(trunkSubA.worktreePath(), "git", "commit", "-m", "update nested pointer trunk");

        configureUser(childSubB);
        commitFile(childSubB, "b.txt", "child nested change", "child nested commit");
        configureUser(childSubA.worktreePath());
        runGit(childSubA.worktreePath(), "git", "add", "libs/sub-b");
        runGit(childSubA.worktreePath(), "git", "commit", "-m", "update nested pointer child");

        WorktreeSandboxContext childCtx = buildSandboxContext(child);
        WorktreeSandboxContext trunkCtx = buildSandboxContext(trunk);

        MergeDescriptor descriptor = gitWorktreeService.mergeChildToTrunk(childCtx, trunkCtx);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.mergeDirection()).isEqualTo(MergeDirection.CHILD_TO_TRUNK);
        assertThat(descriptor.successful()).isFalse();
        assertThat(descriptor.conflictFiles()).isNotEmpty();
        // Deep conflict should be identified with full nested path from root
        assertThat(descriptor.mainWorktreeMergeResult().conflicts().stream()
                .map(MergeResult.MergeConflict::submodulePath)
                .toList()).contains("libs_sub-a_libs_sub-b");
    }

    @Test
    @DisplayName("mergeChildToTrunk nested shallow conflict - sibling submodule with own submodule commits pointer")
    void mergeChildToTrunkNestedShallowConflictSiblingCommitsPointer() throws Exception {
        // Structure: main -> libs/sub-a (has nested libs/sub-b), libs/sub-c (flat sibling)
        Path submoduleB = createRepoWithFile("submodule-b", "b.txt", "base", "init submodule b");
        Path submoduleA = createRepoWithFile("submodule-a", "a.txt", "base", "init submodule a");
        addSubmodule(submoduleA, submoduleB, "libs/sub-b");
        initSubmodules(submoduleA);
        Path submoduleC = createRepoWithFile("submodule-c", "c.txt", "base", "init submodule c");

        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, submoduleA, "libs/sub-a");
        addSubmodule(mainRepo, submoduleC, "libs/sub-c");
        initSubmodules(mainRepo);

        MainWorktreeContext trunk = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-trunk");
        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");

        SubmoduleWorktreeContext trunkSubA = findSubmodule(trunk, "libs/sub-a");
        SubmoduleWorktreeContext childSubA = findSubmodule(child, "libs/sub-a");
        SubmoduleWorktreeContext childSubC = findSubmodule(child, "libs/sub-c");
        SubmoduleWorktreeContext trunkSubC = findSubmodule(trunk, "libs/sub-c");

        // Create conflict at sub-a level (shallow)
        configureUser(trunkSubA.worktreePath());
        commitFile(trunkSubA.worktreePath(), "a.txt", "trunk change a", "trunk sub-a");
        configureUser(childSubA.worktreePath());
        commitFile(childSubA.worktreePath(), "a.txt", "child change a", "child sub-a");

        // Non-conflicting change on sub-c
        configureUser(childSubC.worktreePath());
        commitFile(childSubC.worktreePath(), "c.txt", "child change c", "child sub-c");

        WorktreeSandboxContext childCtx = buildSandboxContext(child);
        WorktreeSandboxContext trunkCtx = buildSandboxContext(trunk);

        MergeDescriptor descriptor = gitWorktreeService.mergeChildToTrunk(childCtx, trunkCtx);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.successful()).isFalse();
        assertThat(descriptor.conflictFiles()).isNotEmpty();

        // sub-c (the sibling) should still have been merged
        assertThat(Files.readString(trunkSubC.worktreePath().resolve("c.txt"))).contains("child change c");
        assertClean(trunkSubC.worktreePath());
    }

    @Test
    @DisplayName("mergeChildToTrunk reports all conflicts when all submodules fail")
    void mergeChildToTrunkAllSubmodulesFail() throws Exception {
        Path subA = createRepoWithFile("sub-a", "a.txt", "base", "init sub-a");
        Path subB = createRepoWithFile("sub-b", "b.txt", "base", "init sub-b");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subA, "libs/sub-a");
        addSubmodule(mainRepo, subB, "libs/sub-b");
        initSubmodules(mainRepo);

        MainWorktreeContext trunk = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-trunk");
        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");

        SubmoduleWorktreeContext trunkSubA = findSubmodule(trunk, "libs/sub-a");
        SubmoduleWorktreeContext trunkSubB = findSubmodule(trunk, "libs/sub-b");
        SubmoduleWorktreeContext childSubA = findSubmodule(child, "libs/sub-a");
        SubmoduleWorktreeContext childSubB = findSubmodule(child, "libs/sub-b");

        // Create conflicts on both submodules
        configureUser(trunkSubA.worktreePath());
        commitFile(trunkSubA.worktreePath(), "a.txt", "trunk change a", "trunk sub-a");
        configureUser(childSubA.worktreePath());
        commitFile(childSubA.worktreePath(), "a.txt", "child change a", "child sub-a");

        configureUser(trunkSubB.worktreePath());
        commitFile(trunkSubB.worktreePath(), "b.txt", "trunk change b", "trunk sub-b");
        configureUser(childSubB.worktreePath());
        commitFile(childSubB.worktreePath(), "b.txt", "child change b", "child sub-b");

        WorktreeSandboxContext childCtx = buildSandboxContext(child);
        WorktreeSandboxContext trunkCtx = buildSandboxContext(trunk);

        MergeDescriptor descriptor = gitWorktreeService.mergeChildToTrunk(childCtx, trunkCtx);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.mergeDirection()).isEqualTo(MergeDirection.CHILD_TO_TRUNK);
        assertThat(descriptor.successful()).isFalse();
        assertThat(descriptor.conflictFiles()).isNotEmpty();
        // Both submodules should have conflicts recorded
        List<String> conflictSubmodules = descriptor.mainWorktreeMergeResult().conflicts().stream()
                .map(MergeResult.MergeConflict::submodulePath)
                .filter(s -> s != null)
                .toList();
        assertThat(conflictSubmodules).contains("libs_sub-a");
        assertThat(conflictSubmodules).contains("libs_sub-b");
    }

    @Test
    @DisplayName("mergeChildToTrunk returns success when no changes (same commit)")
    void mergeChildToTrunkNoChangesSameCommit() throws Exception {
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");

        MainWorktreeContext trunk = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-trunk");
        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");

        // No changes made — both at same commit

        WorktreeSandboxContext childCtx = buildSandboxContext(child);
        WorktreeSandboxContext trunkCtx = buildSandboxContext(trunk);

        MergeDescriptor descriptor = gitWorktreeService.mergeChildToTrunk(childCtx, trunkCtx);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.mergeDirection()).isEqualTo(MergeDirection.CHILD_TO_TRUNK);
        assertThat(descriptor.successful()).isTrue();
        assertThat(descriptor.conflictFiles()).isEmpty();
    }

    @Test
    @DisplayName("mergeChildToTrunk returns success when no changes with submodules (same commits)")
    void mergeChildToTrunkNoChangesWithSubmodulesSameCommit() throws Exception {
        Path subA = createRepoWithFile("sub-a", "a.txt", "base", "init sub-a");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subA, "libs/sub-a");
        initSubmodules(mainRepo);

        MainWorktreeContext trunk = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-trunk");
        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");

        // No changes made — both at same commit including submodule pointers

        WorktreeSandboxContext childCtx = buildSandboxContext(child);
        WorktreeSandboxContext trunkCtx = buildSandboxContext(trunk);

        MergeDescriptor descriptor = gitWorktreeService.mergeChildToTrunk(childCtx, trunkCtx);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.mergeDirection()).isEqualTo(MergeDirection.CHILD_TO_TRUNK);
        assertThat(descriptor.successful()).isTrue();
        assertThat(descriptor.conflictFiles()).isEmpty();
    }

    @Test
    @DisplayName("mergeTrunkToChild returns success when no changes (same commit)")
    void mergeTrunkToChildNoChangesSameCommit() throws Exception {
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");

        MainWorktreeContext trunk = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-trunk");
        MainWorktreeContext child = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", "main-1", "node-child");

        WorktreeSandboxContext trunkCtx = buildSandboxContext(trunk);
        WorktreeSandboxContext childCtx = buildSandboxContext(child);

        MergeDescriptor descriptor = gitWorktreeService.mergeTrunkToChild(trunkCtx, childCtx);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.mergeDirection()).isEqualTo(MergeDirection.TRUNK_TO_CHILD);
        assertThat(descriptor.successful()).isTrue();
        assertThat(descriptor.conflictFiles()).isEmpty();
    }

    // ========================================================================
    // MergeDescriptor integration tests for finalMergeToSourceDescriptor
    // ========================================================================

    @Test
    @DisplayName("finalMergeToSourceDescriptor succeeds with multiple submodules")
    void finalMergeToSourceDescriptorMultipleSubmodulesSuccess() throws Exception {
        Path subA = createRepoWithFile("sub-a", "a.txt", "base", "init sub-a");
        Path subB = createRepoWithFile("sub-b", "b.txt", "base", "init sub-b");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subA, "libs/sub-a");
        addSubmodule(mainRepo, subB, "libs/sub-b");
        initSubmodules(mainRepo);

        String derivedBranch = "main-derived-descriptor";
        MainWorktreeContext worktree = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", derivedBranch, "node-final");

        SubmoduleWorktreeContext worktreeSubA = findSubmodule(worktree, "libs/sub-a");
        SubmoduleWorktreeContext worktreeSubB = findSubmodule(worktree, "libs/sub-b");

        configureUser(worktreeSubA.worktreePath());
        commitFile(worktreeSubA.worktreePath(), "a.txt", "agent change a", "agent sub-a");
        configureUser(worktreeSubB.worktreePath());
        commitFile(worktreeSubB.worktreePath(), "b.txt", "agent change b", "agent sub-b");
        configureUser(worktree.worktreePath());
        runGit(worktree.worktreePath(), "git", "add", "libs/sub-a");
        runGit(worktree.worktreePath(), "git", "add", "libs/sub-b");
        runGit(worktree.worktreePath(), "git", "commit", "-m", "update submodule pointers");
        commitFile(worktree.worktreePath(), "README.md", "agent main change", "agent main");

        MergeDescriptor descriptor = gitWorktreeService.finalMergeToSourceDescriptor(worktree.worktreeId());

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.mergeDirection()).isEqualTo(MergeDirection.WORKTREE_TO_SOURCE);
        assertThat(descriptor.successful()).isTrue();
        assertThat(descriptor.conflictFiles()).isEmpty();
        assertThat(descriptor.errorMessage()).isNull();
        assertThat(descriptor.mainWorktreeMergeResult()).isNotNull();

        Path sourceSubA = mainRepo.resolve("libs/sub-a");
        Path sourceSubB = mainRepo.resolve("libs/sub-b");
        assertThat(Files.readString(sourceSubA.resolve("a.txt"))).contains("agent change a");
        assertThat(Files.readString(sourceSubB.resolve("b.txt"))).contains("agent change b");
        assertThat(Files.readString(mainRepo.resolve("README.md"))).contains("agent main change");
    }

    @Test
    @DisplayName("finalMergeToSourceDescriptor succeeds with nested submodules")
    void finalMergeToSourceDescriptorNestedSuccess() throws Exception {
        Path submoduleB = createRepoWithFile("submodule-b", "b.txt", "base", "init submodule b");
        Path submoduleA = createRepoWithFile("submodule-a", "a.txt", "base", "init submodule a");
        addSubmodule(submoduleA, submoduleB, "libs/sub-b");
        initSubmodules(submoduleA);

        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, submoduleA, "libs/sub-a");
        initSubmodules(mainRepo);

        String derivedBranch = "main-derived-nested-desc";
        MainWorktreeContext worktree = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", derivedBranch, "node-final-nested");
        SubmoduleWorktreeContext worktreeSubA = findSubmodule(worktree, "libs/sub-a");
        Path worktreeSubB = worktreeSubA.worktreePath().resolve("libs/sub-b");

        configureUser(worktreeSubB);
        commitFile(worktreeSubB, "b.txt", "nested change", "nested commit");
        configureUser(worktreeSubA.worktreePath());
        runGit(worktreeSubA.worktreePath(), "git", "add", "libs/sub-b");
        runGit(worktreeSubA.worktreePath(), "git", "commit", "-m", "update nested submodule");
        configureUser(worktree.worktreePath());
        runGit(worktree.worktreePath(), "git", "add", "libs/sub-a");
        runGit(worktree.worktreePath(), "git", "commit", "-m", "update submodule pointer");

        MergeDescriptor descriptor = gitWorktreeService.finalMergeToSourceDescriptor(worktree.worktreeId());

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.mergeDirection()).isEqualTo(MergeDirection.WORKTREE_TO_SOURCE);
        assertThat(descriptor.successful()).isTrue();
        assertThat(descriptor.conflictFiles()).isEmpty();

        Path sourceSubB = mainRepo.resolve("libs/sub-a").resolve("libs/sub-b");
        assertThat(Files.readString(sourceSubB.resolve("b.txt"))).contains("nested change");
    }

    @Test
    @DisplayName("finalMergeToSourceDescriptor reports conflict with correct submodule path in descriptor")
    void finalMergeToSourceDescriptorConflictSubmodulePath() throws Exception {
        Path subRepo = createRepoWithFile("submodule-repo", "lib.txt", "base", "init submodule");
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");
        addSubmodule(mainRepo, subRepo, "libs/submodule-lib");
        initSubmodules(mainRepo);

        String derivedBranch = "main-derived-conflict-desc";
        MainWorktreeContext worktree = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", derivedBranch, "node-conflict");
        SubmoduleWorktreeContext worktreeSub = createSubmoduleWorktreeFromMain(worktree);

        // Diverge source
        Path sourceSubPath = mainRepo.resolve("libs/submodule-lib");
        configureUser(sourceSubPath);
        commitFile(sourceSubPath, "lib.txt", "source change", "source sub edit");
        configureUser(mainRepo);
        runGit(mainRepo, "git", "add", "libs/submodule-lib");
        runGit(mainRepo, "git", "commit", "-m", "update submodule pointer (source)");

        // Diverge worktree
        configureUser(worktreeSub.worktreePath());
        commitFile(worktreeSub.worktreePath(), "lib.txt", "worktree change", "worktree sub edit");
        configureUser(worktree.worktreePath());
        runGit(worktree.worktreePath(), "git", "add", worktreeSub.submoduleName());
        runGit(worktree.worktreePath(), "git", "commit", "-m", "update submodule pointer (worktree)");

        MergeDescriptor descriptor = gitWorktreeService.finalMergeToSourceDescriptor(worktree.worktreeId());

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.mergeDirection()).isEqualTo(MergeDirection.WORKTREE_TO_SOURCE);
        assertThat(descriptor.successful()).isFalse();
        assertThat(descriptor.conflictFiles()).isNotEmpty();
        assertThat(descriptor.mainWorktreeMergeResult()).isNotNull();
        assertThat(descriptor.mainWorktreeMergeResult().conflicts().stream()
                .map(MergeResult.MergeConflict::submodulePath)
                .toList()).contains("libs_submodule-lib");
    }

    @Test
    @DisplayName("finalMergeToSourceDescriptor returns success when no changes")
    void finalMergeToSourceDescriptorNoChanges() throws Exception {
        Path mainRepo = createRepoWithFile("main-repo", "README.md", "base", "init main");

        String derivedBranch = "main-derived-noop";
        MainWorktreeContext worktree = gitWorktreeService.createMainWorktree(
                mainRepo.toString(), "main", derivedBranch, "node-noop");

        // No changes made

        MergeDescriptor descriptor = gitWorktreeService.finalMergeToSourceDescriptor(worktree.worktreeId());

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.mergeDirection()).isEqualTo(MergeDirection.WORKTREE_TO_SOURCE);
        assertThat(descriptor.successful()).isTrue();
        assertThat(descriptor.conflictFiles()).isEmpty();
    }

    // ========================================================================
    // Helper to build WorktreeSandboxContext from MainWorktreeContext
    // ========================================================================

    private WorktreeSandboxContext buildSandboxContext(MainWorktreeContext main) {
        List<SubmoduleWorktreeContext> submodules = gitWorktreeService.getSubmoduleWorktrees(main.worktreeId());
        return new WorktreeSandboxContext(main, submodules);
    }

    private SubmoduleWorktreeContext createSubmoduleWorktreeFromMain(MainWorktreeContext mainWorktree) {
        List<SubmoduleWorktreeContext> submodules =
                gitWorktreeService.getSubmoduleWorktrees(mainWorktree.worktreeId());
        assertThat(submodules).isNotEmpty();
        return submodules.stream()
                .min((a, b) -> Integer.compare(pathDepth(a.submoduleName()), pathDepth(b.submoduleName())))
                .orElseThrow();
    }

    private SubmoduleWorktreeContext findSubmodule(MainWorktreeContext mainWorktree, String submoduleName) {
        List<SubmoduleWorktreeContext> submoduleWorktrees = gitWorktreeService.getSubmoduleWorktrees(mainWorktree.worktreeId());
        return submoduleWorktrees.stream()
                .filter(sub -> submoduleName.equals(sub.submoduleName()))
                .findFirst()
                .orElseThrow();
    }

    private int pathDepth(String path) {
        if (path == null || path.isBlank()) {
            return Integer.MAX_VALUE;
        }
        return (int) path.chars().filter(ch -> ch == '/').count();
    }

    private Path createRepoWithFile(String prefix, String fileName, String content, String message) throws Exception {
        Path repoDir = Files.createTempDirectory(prefix);
        initRepo(repoDir);
        commitFile(repoDir, fileName, content, message);
        return repoDir;
    }

    private void addSubmodule(Path mainRepo, Path subRepo, String submodulePath) throws Exception {
        runGit(mainRepo, "git", "-c", "protocol.file.allow=always",
                "submodule", "add", subRepo.toString(), submodulePath);
        commitAll(mainRepo, "add submodule");
    }
    
    private void initSubmodules(Path repoDir) throws Exception {
        runGit(repoDir, "git", "-c", "protocol.file.allow=always",
                "submodule", "update", "--init", "--recursive");
        runGit(repoDir, "git", "submodule", "foreach", "--recursive", "git", "switch", "main");
    }

    private void initRepo(Path repoDir) throws Exception {
        runGit(repoDir, "git", "init", "-b", "main");
        runGit(repoDir, "git", "config", "user.email", "test@example.com");
        runGit(repoDir, "git", "config", "user.name", "Test User");
    }

    private void configureUser(Path repoDir) throws Exception {
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
        runGit(repoDir, "git", "add", ".");
        runGit(repoDir, "git", "commit", "-m", message);
    }

    private void runGit(Path repoDir, String... command) throws Exception {
        var o = gitOutput(repoDir, command);
        log.info("{}", o);
    }

    private void assertClean(Path repoDir) throws Exception {
        String status = gitOutput(repoDir, "git", "status", "--porcelain").trim();
        assertThat(status).isEmpty();
    }

    private void assertConflicts(Path repoDir, String expectedFile) throws Exception {
        String conflicts = gitOutput(repoDir, "git", "diff", "--name-only", "--diff-filter=U");
        assertThat(conflicts).contains(expectedFile);
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
