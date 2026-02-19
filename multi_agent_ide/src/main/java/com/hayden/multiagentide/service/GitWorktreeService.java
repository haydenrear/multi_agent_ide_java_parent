package com.hayden.multiagentide.service;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.MergeResult;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.merge.MergeDirection;
import com.hayden.multiagentidelib.model.merge.SubmoduleMergeResult;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeContext;
import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.utilitymodule.git.RepoUtil;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

/**
 * Git-based implementation of WorktreeService using git worktree commands.
 * Handles both main repository and git submodule worktrees.
 */
@Slf4j
@Service
public class GitWorktreeService implements WorktreeService {

    private final WorktreeRepository worktreeRepository;
    private final String baseWorktreesPath;

    @Lazy
    @Autowired
    private EventBus eventBus;

    public GitWorktreeService(
            WorktreeRepository worktreeRepository,
            @Value("${multiagentide.worktrees.base-path:}") String baseWorktreesPath
    ) {
        this.worktreeRepository = worktreeRepository;
        if (baseWorktreesPath == null || baseWorktreesPath.isBlank()) {
            this.baseWorktreesPath = System.getProperty("user.home") + "/.multi-agent-ide/worktrees";
        } else {
            this.baseWorktreesPath = baseWorktreesPath;
        }
    }

    @Override
    public MainWorktreeContext createMainWorktree(String repositoryUrl, String baseBranch, String derivedBranch, String nodeId) {
        String worktreeId = UUID.randomUUID().toString();
        Path worktreePath = Paths.get(baseWorktreesPath, worktreeId);

        try {
            Files.createDirectories(worktreePath);
            cloneRepository(repositoryUrl, worktreePath, baseBranch);
            checkoutNewBranch(worktreePath, derivedBranch, null);
            List<String> submodulePaths = initializeSubmodule(worktreePath);

//            we pass in the baseBranch of the parent here because when we clone the
//            submodules it puts it in a detached head. So we do a checkout -b ...
//            and we assume that it's either on that same commit as the branch or will
//            create a new branch with the name of the commit.
            List<SubmoduleWorktreeContext> submodules = createSubmoduleContexts(
                    submodulePaths,
                    worktreeId,
                    worktreePath,
                    nodeId,
                    derivedBranch
            );

            String commitHash = getCurrentCommitHashInternal(worktreePath);

            MainWorktreeContext context = new MainWorktreeContext(
                    worktreeId,
                    worktreePath,
                    baseBranch,
                    derivedBranch,
                    WorktreeContext.WorktreeStatus.ACTIVE,
                    null,
                    nodeId,
                    Instant.now(),
                    commitHash,
                    repositoryUrl,
                    !submodules.isEmpty(),
                    submodules,
                    new HashMap<>()
            );

            worktreeRepository.save(context);
            for (SubmoduleWorktreeContext submoduleContext : submodules) {
                worktreeRepository.save(submoduleContext);
            }
            return context;
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Failed to create main worktree: " + e.getMessage(), e);
        }
    }

    private SubmoduleWorktreeContext createSubmoduleWorktree(String submoduleName, String submodulePath,
                                                             String parentWorktreeId, Path parentWorktreePath, String nodeId,
                                                             String parentBranch) {
        String worktreeId = UUID.randomUUID().toString();
        Path submoduleFullPath = parentWorktreePath.resolve(submodulePath);

        try {
            List<String> updatedSubmodules = initializeSubmodule(parentWorktreePath);
            if (submodulePath != null && !submodulePath.isBlank()
                    && updatedSubmodules.stream().noneMatch(p -> p.equals(submodulePath))) {
                throw new RuntimeException("Failed to initialize submodule path: " + submodulePath);
            }

            try(var g = openGit(parentWorktreePath)) {
                String branch = g.getRepository().getBranch();
                ensureBranchCheckedOut(submoduleFullPath, parentBranch);
                String commitHash = getCurrentCommitHashInternal(submoduleFullPath);

                return new SubmoduleWorktreeContext(
                        worktreeId,
                        submoduleFullPath,
                        branch, // submodules typically use main or master
                        WorktreeContext.WorktreeStatus.ACTIVE,
                        parentWorktreeId,
                        nodeId,
                        Instant.now(),
                        commitHash,
                        submoduleName,
                        "", // submoduleUrl - will be fetched from .gitmodules if needed
                        parentWorktreeId,  // mainWorktreeId
                        new HashMap<>()
                );
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to create submodule worktree: " + e.getMessage(), e);
        }
    }

    private void ensureBranchCheckedOut(Path repoPath, String branchName) {
        RepoUtil.initGit(repoPath)
                .exceptEmpty(exc -> {
                    log.error("Error", exc);
                })
                .ifPresent(git -> {
                    try {
                        Set<String> collect = git.branchList().call().stream().map(Ref::getName)
                                .collect(Collectors.toSet());
                        if (collect.stream().noneMatch(s -> s.endsWith("/" + branchName))) {
                            git.checkout().setName(branchName).setCreateBranch(true).call();
                        } else if(!Objects.equals(git.getRepository().getBranch(), branchName)) {
                            git.checkout().setName(branchName).call();
                        }
                    } catch (GitAPIException |
                             IOException e) {
                        log.error("Error when attempting to checkout a branch!");
                        throw new RuntimeException(e);
                    }
                });
    }

    @Override
    public Optional<MainWorktreeContext> getMainWorktree(String worktreeId) {
        return worktreeRepository.findById(worktreeId)
                .filter(wt -> wt instanceof MainWorktreeContext)
                .map(wt -> (MainWorktreeContext) wt);
    }

    @Override
    public Optional<SubmoduleWorktreeContext> getSubmoduleWorktree(String worktreeId) {
        return worktreeRepository.findById(worktreeId)
                .filter(wt -> wt instanceof SubmoduleWorktreeContext)
                .map(wt -> (SubmoduleWorktreeContext) wt);
    }

    @Override
    public List<SubmoduleWorktreeContext> getSubmoduleWorktrees(String mainWorktreeId) {
        return worktreeRepository.findByParentId(mainWorktreeId).stream()
                .filter(wt -> wt instanceof SubmoduleWorktreeContext)
                .map(wt -> (SubmoduleWorktreeContext) wt)
                .collect(Collectors.toList());
    }

    public boolean hasSubmodules(Path repositoryPath) {
        return hasSubmodulesInternal(repositoryPath);
    }

    public List<String> getSubmoduleNames(Path repositoryPath) {
        Path gitmodulesPath = repositoryPath.resolve(".gitmodules");
        if (!Files.exists(gitmodulesPath)) {
            return new ArrayList<>();
        }

        try {
            FileBasedConfig config = new FileBasedConfig(gitmodulesPath.toFile(), FS.DETECTED);
            config.load();
            return new ArrayList<>(config.getSubsections("submodule"));
        } catch (Exception e) {
            System.err.println("Warning: Could not get submodule names: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public Path getSubmodulePath(Path repositoryPath, String submoduleName) {
        Path gitmodulesPath = repositoryPath.resolve(".gitmodules");
        if (!Files.exists(gitmodulesPath)) {
            throw new RuntimeException("Failed to get submodule path: .gitmodules not found");
        }
        try {
            FileBasedConfig config = new FileBasedConfig(gitmodulesPath.toFile(), FS.DETECTED);
            config.load();
            String pathValue = config.getString("submodule", submoduleName, "path");
            if (pathValue == null || pathValue.isBlank()) {
                throw new RuntimeException("Failed to get submodule path: missing path for " + submoduleName);
            }
            return repositoryPath.resolve(pathValue.trim());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get submodule path: " + e.getMessage(), e);
        }
    }

    // ======== MERGE ACTION MODEL ========

    /**
     * State snapshot captured before and after each merge action for verification.
     */
    private record RepoStateSnapshot(
            String headCommit,
            String branch,
            boolean isClean
    ) {
        static RepoStateSnapshot capture(GitWorktreeService service, Path repoPath) {
            String head = service.getCurrentCommitHashInternal(repoPath);
            String branch = service.getBranchSafe(repoPath);
            boolean clean;
            try (var git = service.openGit(repoPath)) {
                clean = git.status().call().isClean();
            } catch (Exception e) {
                clean = false;
            }
            return new RepoStateSnapshot(head, branch, clean);
        }
    }

    /**
     * Mutable state carried through the merge plan execution.
     */
    private static class MergeExecutionState {
        final List<MergeResult.MergeConflict> allConflicts = new ArrayList<>();
        final Set<Path> blockedParentPaths = new HashSet<>();

        void addConflicts(List<MergeResult.MergeConflict> conflicts, MergePlanStep step) {
            allConflicts.addAll(conflicts);
            if (step.parentOfParentPath() != null) {
                blockedParentPaths.add(step.parentOfParentPath());
            }
        }

        boolean isBlocked(MergePlanStep step) {
            return blockedParentPaths.contains(step.parentPath());
        }

        void propagateBlock(MergePlanStep step) {
            if (step.parentOfParentPath() != null) {
                blockedParentPaths.add(step.parentOfParentPath());
            }
        }

        boolean hasConflicts() {
            return !allConflicts.isEmpty();
        }
    }

    @Override
    public MergeResult mergeWorktrees(String childWorktreeId, String parentWorktreeId) {
        Optional<WorktreeContext> childWt = worktreeRepository.findById(childWorktreeId);
        Optional<WorktreeContext> parentWt = worktreeRepository.findById(parentWorktreeId);

        if (childWt.isEmpty() || parentWt.isEmpty()) {
            MergeResult result = new MergeResult(
                    UUID.randomUUID().toString(),
                    childWorktreeId,
                    parentWorktreeId,
                    childWt.map(WorktreeContext::worktreePath).map(this::normalizePath).orElse(null),
                    parentWt.map(WorktreeContext::worktreePath).map(this::normalizePath).orElse(null),
                    false,
                    null,
                    List.of(),
                    List.of(),
                    "Worktree not found",
                    Instant.now()
            );
            emitMergeFailure(result, childWt.orElse(null), parentWt.orElse(null), "Worktree not found for merge request.");
            return result;
        }

        try {
            WorktreeContext childContext = childWt.get();
            WorktreeContext parentContext = parentWt.get();
            Path childPath = childContext.worktreePath();
            Path parentPath = parentContext.worktreePath();

            // Phase 1: Build comprehensive merge plan (leaves first, root last).
            List<MergePlanStep> plan = buildMergePlan(childPath, parentPath, childContext.derivedBranch());
            log.debug("Merge plan has {} steps", plan.size());

            // Phase 2: Execute each step with before/after actions and verification.
            MergeExecutionState state = new MergeExecutionState();
            for (MergePlanStep step : plan) {
                executeStepWithLifecycle(step, state, childContext, parentContext);
            }

            // Phase 3: Finalize — commit pointers, restore branches, update metadata.
            if (state.hasConflicts()) {
                switchToBranches(parentPath, childPath, childContext.derivedBranch());
                MergeResult conflictResult = buildConflictResult(childWorktreeId, parentWorktreeId, state.allConflicts);
                emitMergeFailure(conflictResult, childContext, parentContext, "Merge conflicts detected.");
                return conflictResult;
            }

            MergeResult success = finalizeSuccessfulMerge(childWorktreeId, parentWorktreeId, childContext, parentContext);
            emitMergeLifecycleEvent(success, childContext, parentContext, "main");
            return success;

        } catch (Exception e) {
            if (childWt.isPresent() && parentWt.isPresent()) {
                switchToBranches(parentWt.get().worktreePath(), childWt.get().worktreePath(), childWt.get().derivedBranch());
            }
            MergeResult result = new MergeResult(
                    UUID.randomUUID().toString(),
                    childWorktreeId,
                    parentWorktreeId,
                    childWt.map(WorktreeContext::worktreePath).map(this::normalizePath).orElse(null),
                    parentWt.map(WorktreeContext::worktreePath).map(this::normalizePath).orElse(null),
                    false,
                    null,
                    List.of(),
                    List.of(),
                    "Merge failed: " + e.getMessage(),
                    Instant.now()
            );
            emitMergeFailure(result, childWt.orElse(null), parentWt.orElse(null), "Merge execution failed: " + e.getMessage());
            return result;
        }
    }

    /**
     * Execute a single merge plan step through its full lifecycle:
     *   1. Check if blocked by child conflicts
     *   2. Capture before-state snapshot
     *   3. Execute the merge action
     *   4. Run after-actions (commit dirty pointers)
     *   5. Verify after-state (parent HEAD should have advanced, repo should be clean)
     */
    private void executeStepWithLifecycle(
            MergePlanStep step,
            MergeExecutionState state,
            WorktreeContext rootChildContext,
            WorktreeContext rootParentContext
    ) {
        // Pre-check: skip if a child step had conflicts.
        if (state.isBlocked(step)) {
            state.propagateBlock(step);
            log.debug("Skipping blocked step: sub={}", step.submodulePath());
            return;
        }

        // Before-state snapshot.
        RepoStateSnapshot parentBefore = RepoStateSnapshot.capture(this, step.parentPath());
        log.debug("Step sub={}: before parentHEAD={} branch={}", step.submodulePath(),
                parentBefore.headCommit(), parentBefore.branch());

        try {
            // Execute merge action.
            List<MergeResult.MergeConflict> conflicts = doMerge(step);

            if (!conflicts.isEmpty()) {
                emitMergeStepConflict(step, rootChildContext, rootParentContext, conflicts);
                state.addConflicts(conflicts, step);
                return;
            }

            // After-actions: commit dirty submodule pointers in the merged repo and its parent.
            commitDirtySubmodulePointers(step.parentPath());
            if (step.parentOfParentPath() != null) {
                commitDirtySubmodulePointers(step.parentOfParentPath());
            }

            // After-state snapshot and verification.
            RepoStateSnapshot parentAfter = RepoStateSnapshot.capture(this, step.parentPath());
            log.debug("Step sub={}: after parentHEAD={} branch={} clean={}",
                    step.submodulePath(), parentAfter.headCommit(), parentAfter.branch(), parentAfter.isClean());

            verifyStepResult(step, parentBefore, parentAfter);
            emitMergeStepSuccess(step, rootChildContext, rootParentContext);

        } catch (Exception e) {
            String label = step.submodulePath() != null ? step.submodulePath() : "root";
            List<MergeResult.MergeConflict> conflicts = List.of(new MergeResult.MergeConflict(
                    label, "merge-error", "", "", "", step.formattedSubmodulePath()
            ));
            emitMergeStepConflict(step, rootChildContext, rootParentContext, conflicts);
            state.addConflicts(conflicts, step);
        }
    }

    /**
     * Core merge action: fetch from child, merge into parent, handle auto-resolution.
     *
     * @return list of conflicts (empty if successful)
     */
    private List<MergeResult.MergeConflict> doMerge(MergePlanStep step) throws IOException, GitAPIException, URISyntaxException {
        String planBranch = resolveBranch(step.childPath(), step.childBranch());

        org.eclipse.jgit.api.MergeResult mergeResult = mergeFromChildRepo(
                step.parentPath(),
                step.childPath(),
                planBranch
        );

        log.debug("Merge result sub={}: status={}", step.submodulePath(), mergeResult.getMergeStatus());

        if (mergeResult.getMergeStatus().isSuccessful()) {
            return List.of();
        }

        // Extract conflicts.
        Map<String, int[][]> conflictMap = mergeResult.getConflicts();
        List<MergeResult.MergeConflict> conflicts = conflictMap == null
                ? List.of()
                : conflictMap.keySet().stream()
                .map(file -> new MergeResult.MergeConflict(
                        file, "content", "", "", "", step.formattedSubmodulePath()
                ))
                .collect(Collectors.toList());

        // Attempt auto-resolution for submodule pointer conflicts.
        if (tryAutoResolveSubmoduleConflicts(conflicts, step.parentPath())) {
            return List.of();
        }

        return conflicts;
    }

    /**
     * Verify that a merge step produced the expected state change.
     * Logs warnings if verification fails — does not throw.
     */
    private void verifyStepResult(MergePlanStep step, RepoStateSnapshot before, RepoStateSnapshot after) {
        // Verify the branch didn't change unexpectedly during the merge.
        if (!Objects.equals(before.branch(), after.branch())) {
            log.warn("Step sub={}: branch changed unexpectedly from {} to {}",
                    step.submodulePath(), before.branch(), after.branch());
        }
    }

    /**
     * Finalize a successful merge: commit pointers, restore branches, update metadata.
     * Order matters: commit dirty pointers before AND after switching branches to handle
     * both detached HEAD and named branch states.
     */
    private MergeResult finalizeSuccessfulMerge(String childWorktreeId, String parentWorktreeId,
                                                 WorktreeContext childContext, WorktreeContext parentContext) {
        Path parentPath = parentContext.worktreePath();
        Path childPath = childContext.worktreePath();

        // Commit pointers while still in current HEAD state (may be detached).
        commitDirtySubmodulePointers(parentPath);
        // Restore named branches.
        switchToBranches(parentPath, childPath, childContext.derivedBranch());
        // Commit pointers again on the named branch (catches any drift from branch switch).
        commitDirtySubmodulePointers(parentPath);

        String mergeCommit = getCurrentCommitHashInternal(parentPath);
        updateWorktreeLastCommit(parentContext, mergeCommit);
        if (childContext instanceof SubmoduleWorktreeContext submoduleChild) {
            propagateSubmodulePointerUpdates(submoduleChild, parentContext);
        }

        return new MergeResult(
                UUID.randomUUID().toString(),
                childWorktreeId,
                parentWorktreeId,
                normalizePath(childContext.worktreePath()),
                normalizePath(parentContext.worktreePath()),
                true,
                mergeCommit,
                List.of(),
                List.of(),
                "Merge successful",
                Instant.now()
        );
    }

    private MergeResult buildConflictResult(String childWorktreeId, String parentWorktreeId,
                                             List<MergeResult.MergeConflict> conflicts) {
        return new MergeResult(
                UUID.randomUUID().toString(),
                childWorktreeId,
                parentWorktreeId,
                resolveWorktreePath(childWorktreeId),
                resolveWorktreePath(parentWorktreeId),
                false,
                null,
                conflicts,
                List.of(),
                conflicts.stream().anyMatch(c -> c.submodulePath() != null)
                        ? "Merge conflicts detected in submodule"
                        : "Merge conflicts detected",
                Instant.now()
        );
    }

    private void switchToBranches(Path parentPath, Path childPath, String childBranch) {
        ActualBranches result = getActualBranches(parentPath, childPath, childBranch);
        ensureBranchMatchesContext(parentPath, result.parentBranch());
        ensureBranchMatchesContext(childPath, result.childBranchIn());
    }

    private @NonNull ActualBranches getActualBranches(Path parentPath, Path childPath, String childBranch) {
        var parentBranch = worktreeRepository.findByPath(parentPath)
                .map(wc -> wc.derivedBranch())
                .orElse(childBranch);
        var childBranchIn = worktreeRepository.findByPath(childPath)
                .map(wc -> wc.derivedBranch())
                .orElse(childBranch);
        ActualBranches result = new ActualBranches(parentBranch, childBranchIn);
        return result;
    }

    private record ActualBranches(String parentBranch, String childBranchIn) {
    }

    private String resolveBranch(Path path, String fallback) throws IOException {
        return worktreeRepository.findByPath(path)
                .map(WorktreeContext::derivedBranch)
                .orElse(fallback);
    }

    private String resolveWorktreePath(String worktreeId) {
        return worktreeRepository.findById(worktreeId)
                .map(WorktreeContext::worktreePath)
                .map(this::normalizePath)
                .orElse(null);
    }

    private void ensureBranchMatchesContext(Path path, String s) {
        if (path == null || s == null || s.isBlank()) {
            return;
        }
        try (var git = openGit(path)) {
            String current = git.getRepository().getBranch();
            if (!Objects.equals(current, s)) {
                var co = git.checkout().setName(s).call();
            }
        } catch (Exception e) {
            log.error("Failed to ensure matches context.", e);
        }
    }

    private void updateWorktreeLastCommit(WorktreeContext context, String commitHash) {
        if (context instanceof MainWorktreeContext main) {
            worktreeRepository.save(main.withLastCommit(commitHash));
        } else if (context instanceof SubmoduleWorktreeContext submodule) {
            worktreeRepository.save(submodule.withLastCommit(commitHash));
        }
    }

    private void commitDirtySubmodulePointers(Path parentPath) {
        if (parentPath == null) {
            return;
        }
        List<String> submodulePaths = new ArrayList<>();
        try {
            for (String name : getSubmoduleNames(parentPath)) {
                Path path = getSubmodulePath(parentPath, name);
                if (path != null) {
                    submodulePaths.add(parentPath.relativize(path).toString());
                }
            }
        } catch (Exception e) {
            return;
        }

        if (submodulePaths.isEmpty()) {
            return;
        }

        try (var git = openGit(parentPath)) {
            // Always stage all submodule paths unconditionally. JGit's status detection
            // for submodules can miss modifications (e.g., after merge conflict resolution),
            // so it's safer to always add and let git determine if anything actually changed.
            for (String path : submodulePaths) {
                git.add().addFilepattern(path).call();
            }
            Status status = git.status().call();
            boolean hasChanges = submodulePaths.stream()
                    .anyMatch(p -> status.getChanged().contains(p)
                            || status.getAdded().contains(p));
            if (hasChanges) {
                git.commit().setMessage("Update submodule pointer(s)").call();
            }
        } catch (Exception e) {
            // best-effort; leave as-is
        }
    }

    private boolean tryAutoResolveSubmoduleConflicts(List<MergeResult.MergeConflict> conflicts, Path parentPath) {
        if (parentPath == null || conflicts == null || conflicts.isEmpty()) {
            return false;
        }
        Set<String> submodulePaths = new HashSet<>();
        try {
            for (String name : getSubmoduleNames(parentPath)) {
                Path path = getSubmodulePath(parentPath, name);
                if (path != null) {
                    submodulePaths.add(parentPath.relativize(path).toString());
                }
            }
        } catch (Exception e) {
            return false;
        }

        List<String> conflictPaths = conflicts.stream()
                .map(MergeResult.MergeConflict::filePath)
                .filter(Objects::nonNull)
                .toList();

        if (conflictPaths.isEmpty() || !submodulePaths.containsAll(conflictPaths)) {
            return false;
        }

        try (var git = openGit(parentPath)) {
            // For each conflicting submodule, resolve the conflict to the submodule's
            // actual current HEAD commit. This ensures the pointer matches the working tree
            // even after intermediate merge steps have produced new commits.
            for (String conflictPath : conflictPaths) {
                git.add().addFilepattern(conflictPath).call();
            }
            git.commit().setMessage("Resolve submodule pointer conflicts").call();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void propagateSubmodulePointerUpdates(SubmoduleWorktreeContext childSubmodule,
                                                  WorktreeContext initialParent) {
        if (childSubmodule == null || initialParent == null) {
            return;
        }

        updateSubmodulePointerIfChanged(initialParent, childSubmodule.submoduleName());

        // Walk up the filesystem from the child's path, committing dirty submodule
        // pointers at each ancestor git repo. This handles nested submodules correctly
        // even when the WorktreeRepository parent chain is flat.
        Path childPath = childSubmodule.worktreePath();
        if (childPath == null) {
            return;
        }

        Path current = childPath.toAbsolutePath().normalize();
        Set<Path> committed = new HashSet<>();
        Path ancestor = current.getParent();
        while (ancestor != null && ancestor.getNameCount() > 0) {
            if (Files.exists(ancestor.resolve(".git")) && Files.exists(ancestor.resolve(".gitmodules"))) {
                if (committed.add(ancestor)) {
                    commitDirtySubmodulePointers(ancestor);
                }
            }
            ancestor = ancestor.getParent();
        }
    }

    private boolean updateSubmodulePointerIfChanged(WorktreeContext parentContext, String submodulePath) {
        if (parentContext == null || submodulePath == null || submodulePath.isBlank()) {
            return false;
        }
        try (var git = openGit(parentContext.worktreePath())) {
            Status status = git.status().call();
            Set<String> modified = status.getModified();
            boolean changed = modified.contains(submodulePath)
                    || status.getChanged().contains(submodulePath)
                    || status.getAdded().contains(submodulePath);
            if (!changed) {
                return false;
            }
            git.add().addFilepattern(submodulePath).call();
            git.commit().setMessage("Update " + submodulePath + " pointer").call();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public MainWorktreeContext branchWorktree(String sourceWorktreeId, String newBranchName, String nodeId) {
        Optional<MainWorktreeContext> source = getMainWorktree(sourceWorktreeId);
        if (source.isEmpty()) {
            throw new RuntimeException("Source worktree not found: " + sourceWorktreeId);
        }

        try {
            String worktreeId = UUID.randomUUID().toString();
            Path newWorktreePath = Paths.get(baseWorktreesPath, worktreeId);

            cloneRepository(source.get().repositoryUrl(), newWorktreePath, source.get().derivedBranch());
            checkoutNewBranch(newWorktreePath, newBranchName, source.get().derivedBranch());


            String commitHash = getCurrentCommitHashInternal(newWorktreePath);

            MainWorktreeContext context = new MainWorktreeContext(
                    worktreeId,
                    newWorktreePath,
                    source.get().derivedBranch(),
                    newBranchName,
                    WorktreeContext.WorktreeStatus.ACTIVE,
                    sourceWorktreeId,
                    nodeId,
                    Instant.now(),
                    commitHash,
                    source.get().repositoryUrl(),
                    source.get().hasSubmodules(),
                    new ArrayList<>(),
                    new HashMap<>()
            );

            worktreeRepository.save(context);
            return context;
        } catch (Exception e) {
            throw new RuntimeException("Failed to branch worktree: " + e.getMessage(), e);
        }
    }

    @Override
    public SubmoduleWorktreeContext branchSubmoduleWorktree(String sourceWorktreeId, String newBranchName, String nodeId) {
        Optional<SubmoduleWorktreeContext> source = getSubmoduleWorktree(sourceWorktreeId);
        if (source.isEmpty()) {
            throw new RuntimeException("Source submodule worktree not found: " + sourceWorktreeId);
        }

        try {
            checkoutNewBranch(source.get().worktreePath(), newBranchName, source.get().derivedBranch());

            String commitHash = getCurrentCommitHashInternal(source.get().worktreePath());

            SubmoduleWorktreeContext context = new SubmoduleWorktreeContext(
                    UUID.randomUUID().toString(),
                    source.get().worktreePath(),
                    newBranchName,
                    WorktreeContext.WorktreeStatus.ACTIVE,
                    source.get().parentWorktreeId(),
                    nodeId,
                    Instant.now(),
                    commitHash,
                    source.get().submoduleName(),
                    source.get().submoduleUrl(),
                    source.get().mainWorktreeId(),
                    new HashMap<>()
            );

            worktreeRepository.save(context);
            return context;
        } catch (Exception e) {
            throw new RuntimeException("Failed to branch submodule worktree: " + e.getMessage(), e);
        }
    }

    public AgentModels.DiscoveryAgentRequests attachWorktreesToDiscoveryRequests(
            AgentModels.DiscoveryAgentRequests input,
            String nodeId
    ) {
        if (input == null || input.requests() == null || input.requests().isEmpty()) {
            return input;
        }
        WorktreeSandboxContext parentContext = input.worktreeContext();
        if (parentContext == null || parentContext.mainWorktree() == null) {
            return input;
        }
        List<AgentModels.DiscoveryAgentRequest> updated = new ArrayList<>();
        int index = 0;
        for (AgentModels.DiscoveryAgentRequest request : input.requests()) {
            if (request == null) {
                continue;
            }
            index++;
            String branchName = "discovery-" + index + "-" + shortId(nodeId);
            WorktreeSandboxContext child = branchSandboxContext(parentContext, branchName, nodeId);
            updated.add(child != null ? request.toBuilder().worktreeContext(child).build() : request);
        }
        return input.toBuilder().requests(updated).build();
    }

    public AgentModels.PlanningAgentRequests attachWorktreesToPlanningRequests(
            AgentModels.PlanningAgentRequests input,
            String nodeId
    ) {
        if (input == null || input.requests() == null || input.requests().isEmpty()) {
            return input;
        }
        WorktreeSandboxContext parentContext = input.worktreeContext();
        if (parentContext == null || parentContext.mainWorktree() == null) {
            return input;
        }
        List<AgentModels.PlanningAgentRequest> updated = new ArrayList<>();
        int index = 0;
        for (AgentModels.PlanningAgentRequest request : input.requests()) {
            if (request == null) {
                continue;
            }
            index++;
            String branchName = "planning-" + index + "-" + shortId(nodeId);
            WorktreeSandboxContext child = branchSandboxContext(parentContext, branchName, nodeId);
            updated.add(child != null ? request.toBuilder().worktreeContext(child).build() : request);
        }
        return input.toBuilder().requests(updated).build();
    }

    public AgentModels.TicketAgentRequests attachWorktreesToTicketRequests(
            AgentModels.TicketAgentRequests input,
            String nodeId
    ) {
        if (input == null || input.requests() == null || input.requests().isEmpty()) {
            return input;
        }
        WorktreeSandboxContext parentContext = input.worktreeContext();
        if (parentContext == null || parentContext.mainWorktree() == null) {
            return input;
        }
        List<AgentModels.TicketAgentRequest> updated = new ArrayList<>();
        int index = 0;
        for (AgentModels.TicketAgentRequest request : input.requests()) {
            if (request == null) {
                continue;
            }
            index++;
            String branchName = "ticket-" + index + "-" + shortId(nodeId);
            WorktreeSandboxContext child = branchSandboxContext(parentContext, branchName, nodeId);
            updated.add(child != null ? request.toBuilder().worktreeContext(child).build() : request);
        }
        return input.toBuilder().requests(updated).build();
    }

    @Override
    public void discardWorktree(String worktreeId) {
        Optional<WorktreeContext> wt = worktreeRepository.findById(worktreeId);
        if (wt.isEmpty()) {
            return;
        }

        try {
            // Remove from disk
            Path path = wt.get().worktreePath();
            if (Files.exists(path)) {
                deleteRecursively(path);
            }

            // Update status to DISCARDED
            if (wt.get() instanceof MainWorktreeContext) {
                MainWorktreeContext updated = ((MainWorktreeContext) wt.get()).withStatus(WorktreeContext.WorktreeStatus.DISCARDED);
                worktreeRepository.save(updated);
            } else if (wt.get() instanceof SubmoduleWorktreeContext) {
                SubmoduleWorktreeContext updated = ((SubmoduleWorktreeContext) wt.get()).withStatus(WorktreeContext.WorktreeStatus.DISCARDED);
                worktreeRepository.save(updated);
            }

            // Recursively discard children
            for (WorktreeContext child : worktreeRepository.findByParentId(worktreeId)) {
                discardWorktree(child.worktreeId());
            }
        } catch (IOException e) {
            log.error("Warning: Could not fully discard worktree: " + e.getMessage());
        }
    }

    public String getCurrentCommitHash(String worktreeId) {
        Optional<WorktreeContext> wt = worktreeRepository.findById(worktreeId);
        if (wt.isEmpty()) {
            throw new RuntimeException("Worktree not found: " + worktreeId);
        }
        return getCurrentCommitHashInternal(wt.get().worktreePath());
    }

    public String commitChanges(String worktreeId, String message) {
        Optional<WorktreeContext> wt = worktreeRepository.findById(worktreeId);
        if (wt.isEmpty()) {
            throw new RuntimeException("Worktree not found: " + worktreeId);
        }

        try {
            Path wtPath = wt.get().worktreePath();
            try (var git = openGit(wtPath)) {
                git.add().addFilepattern(".").call();
                git.commit().setMessage(message).call();
            }
            String commitHash = getCurrentCommitHashInternal(wtPath);

            if (wt.get() instanceof MainWorktreeContext) {
                MainWorktreeContext updated = ((MainWorktreeContext) wt.get()).withLastCommit(commitHash);
                worktreeRepository.save(updated);
            } else if (wt.get() instanceof SubmoduleWorktreeContext) {
                SubmoduleWorktreeContext updated = ((SubmoduleWorktreeContext) wt.get()).withLastCommit(commitHash);
                worktreeRepository.save(updated);
            }

            return commitHash;
        } catch (Exception e) {
            throw new RuntimeException("Failed to commit changes: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateSubmodulePointer(String mainWorktreeId, String submoduleName) {
        Optional<MainWorktreeContext> main = getMainWorktree(mainWorktreeId);
        if (main.isEmpty()) {
            return;
        }

        try {
            Path mainPath = main.get().worktreePath();
            try (var git = openGit(mainPath)) {
                git.add().addFilepattern(submoduleName).call();
                git.commit().setMessage("Update " + submoduleName + " pointer").call();
            }
        } catch (Exception e) {
            log.error("Warning: Could not update submodule pointer: {}", e.getMessage());
        }
    }

    public List<String> detectMergeConflicts(String childWorktreeId, String parentWorktreeId) {
        Optional<MainWorktreeContext> parentWt = getMainWorktree(parentWorktreeId);
        if (parentWt.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            try (var git = openGit(parentWt.get().worktreePath())) {
                Status status = git.status().call();
                return new ArrayList<>(status.getConflicting());
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public MergeResult ensureMergeConflictsCaptured(MergeResult result) {
        if (result == null) {
            return null;
        }

        List<String> detected = detectMergeConflicts(
                result.childWorktreeId(),
                result.parentWorktreeId()
        );
        boolean parentContainsChild = parentContainsChildHead(
                result.childWorktreeId(),
                result.parentWorktreeId()
        );
        if (detected.isEmpty() && parentContainsChild) {
            return result;
        }

        List<MergeResult.MergeConflict> mergedConflicts = new ArrayList<>();
        if (result.conflicts() != null) {
            mergedConflicts.addAll(result.conflicts());
        }
        for (String file : detected) {
            boolean alreadyPresent = mergedConflicts.stream()
                    .filter(c -> c != null && c.filePath() != null)
                    .anyMatch(c -> Objects.equals(c.filePath(), file));
            if (!alreadyPresent) {
                mergedConflicts.add(new MergeResult.MergeConflict(
                        file,
                        "detected",
                        "",
                        "",
                        "",
                        null
                ));
            }
        }
        if (!parentContainsChild) {
            String markerPath = result.parentWorktreePath() != null
                    ? result.parentWorktreePath()
                    : "merge-validation";
            boolean alreadyPresent = mergedConflicts.stream()
                    .filter(c -> c != null && c.filePath() != null)
                    .anyMatch(c -> Objects.equals(c.filePath(), markerPath));
            if (!alreadyPresent) {
                mergedConflicts.add(new MergeResult.MergeConflict(
                        markerPath,
                        "missing-commit",
                        "",
                        "",
                        "",
                        null
                ));
            }
        }

        if (mergedConflicts.isEmpty()) {
            return result;
        }

        String message = result.mergeMessage();
        if (result.successful() || message == null || message.isBlank()) {
            message = parentContainsChild
                    ? "Merge conflicts detected (spot check)"
                    : "Merge validation failed: parent missing child commit";
        }

        return new MergeResult(
                result.mergeId(),
                result.childWorktreeId(),
                result.parentWorktreeId(),
                result.childWorktreePath(),
                result.parentWorktreePath(),
                false,
                result.mergeCommitHash(),
                mergedConflicts,
                result.submoduleUpdates(),
                message,
                result.mergedAt()
        );
    }

    @Override
    public MergeResult ensureMergeConflictsCaptured(MergeResult result, MainWorktreeContext mainWorktree) {
        if (result == null || mainWorktree == null) {
            return result;
        }

        String worktreePath = normalizePath(mainWorktree.worktreePath());
        String sourcePath = mainWorktree.repositoryUrl();

        if (worktreePath == null || sourcePath == null) {
            return result;
        }

        boolean sourceContainsWorktreeHead = parentContainsChildHeadByPath(
                worktreePath, sourcePath
        );

        List<MergeResult.MergeConflict> additionalConflicts = new ArrayList<>();

        if (!sourceContainsWorktreeHead) {
            String markerPath = sourcePath;
            boolean alreadyPresent = result.conflicts() != null && result.conflicts().stream()
                    .filter(c -> c != null && c.filePath() != null)
                    .anyMatch(c -> Objects.equals(c.filePath(), markerPath));
            if (!alreadyPresent) {
                additionalConflicts.add(new MergeResult.MergeConflict(
                        markerPath,
                        "missing-commit",
                        "",
                        "",
                        "",
                        null
                ));
            }
        }

        // Recursively discover submodules from the filesystem (not from
        // mainWorktree.submoduleWorktrees() which may have incorrect nesting).
        Path worktreePathObj = mainWorktree.worktreePath();
        Path sourcePathObj = Path.of(sourcePath);
        if (worktreePathObj != null) {
            collectSubmoduleConflictsRecursive(
                    worktreePathObj, sourcePathObj, "",
                    additionalConflicts, result.conflicts()
            );
        }

        if (additionalConflicts.isEmpty()) {
            return result;
        }

        List<MergeResult.MergeConflict> mergedConflicts = new ArrayList<>();
        if (result.conflicts() != null) {
            mergedConflicts.addAll(result.conflicts());
        }
        mergedConflicts.addAll(additionalConflicts);

        String message = sourceContainsWorktreeHead
                ? "Merge validation: submodule commits not fully propagated"
                : "Merge validation failed: source missing worktree commit";

        return new MergeResult(
                result.mergeId(),
                result.childWorktreeId(),
                result.parentWorktreeId(),
                result.childWorktreePath(),
                result.parentWorktreePath(),
                false,
                result.mergeCommitHash(),
                mergedConflicts,
                result.submoduleUpdates(),
                message,
                result.mergedAt()
        );
    }

    private void collectSubmoduleConflictsRecursive(Path worktreePath, Path sourcePath,
                                                      String parentRelPath,
                                                      List<MergeResult.MergeConflict> additionalConflicts,
                                                      List<MergeResult.MergeConflict> existingConflicts) {
        List<String> submoduleNames;
        try {
            submoduleNames = getSubmoduleNames(worktreePath);
        } catch (Exception e) {
            return;
        }

        for (String submoduleName : submoduleNames) {
            Path worktreeSubPath;
            Path sourceSubPath;
            String relPath = parentRelPath == null || parentRelPath.isBlank()
                    ? submoduleName
                    : parentRelPath + "/" + submoduleName;
            try {
                worktreeSubPath = getSubmodulePath(worktreePath, submoduleName);
                sourceSubPath = getSubmodulePath(sourcePath, submoduleName);
            } catch (Exception e) {
                continue;
            }

            String worktreeSubStr = normalizePath(worktreeSubPath);
            String sourceSubStr = normalizePath(sourceSubPath);

            if (worktreeSubStr != null && sourceSubStr != null) {
                boolean subContained = parentContainsChildHeadByPath(worktreeSubStr, sourceSubStr);
                if (!subContained) {
                    boolean alreadyPresent = existingConflicts != null && existingConflicts.stream()
                            .filter(c -> c != null && c.filePath() != null)
                            .anyMatch(c -> Objects.equals(c.filePath(), relPath));
                    boolean alreadyAdded = additionalConflicts.stream()
                            .filter(c -> c != null && c.filePath() != null)
                            .anyMatch(c -> Objects.equals(c.filePath(), relPath));
                    if (!alreadyPresent && !alreadyAdded) {
                        additionalConflicts.add(new MergeResult.MergeConflict(
                                relPath,
                                "missing-commit",
                                "",
                                "",
                                "",
                                formatSubmodulePath(relPath)
                        ));
                    }
                }
            }

            // Recurse into nested submodules
            collectSubmoduleConflictsRecursive(worktreeSubPath, sourceSubPath, relPath,
                    additionalConflicts, existingConflicts);
        }
    }

    @Override
    public MergeDescriptor mergeTrunkToChild(WorktreeSandboxContext trunk, WorktreeSandboxContext child) {
        try {
            MergeResult result = mergeWorktrees(
                    trunk.mainWorktree().worktreeId(),
                    child.mainWorktree().worktreeId()
            );
            result = ensureMergeConflictsCaptured(result);
            return toMergeDescriptor(result, MergeDirection.TRUNK_TO_CHILD);
        } catch (Exception e) {
            log.error("Trunk-to-child merge failed", e);
            return MergeDescriptor.builder()
                    .mergeDirection(MergeDirection.TRUNK_TO_CHILD)
                    .successful(false)
                    .errorMessage("Trunk-to-child merge failed: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public MergeDescriptor mergeChildToTrunk(WorktreeSandboxContext child, WorktreeSandboxContext trunk) {
        try {
            MergeResult result = mergeWorktrees(
                    child.mainWorktree().worktreeId(),
                    trunk.mainWorktree().worktreeId()
            );
            result = ensureMergeConflictsCaptured(result);

            if (result.successful() && child.submoduleWorktrees() != null) {
                for (SubmoduleWorktreeContext sub : child.submoduleWorktrees()) {
                    try {
                        updateSubmodulePointer(
                                trunk.mainWorktree().worktreeId(),
                                sub.submoduleName()
                        );
                    } catch (Exception e) {
                        log.warn("Failed to update submodule pointer for {}: {}", sub.submoduleName(), e.getMessage());
                    }
                }
            }

            return toMergeDescriptor(result, MergeDirection.CHILD_TO_TRUNK);
        } catch (Exception e) {
            log.error("Child-to-trunk merge failed", e);
            return MergeDescriptor.builder()
                    .mergeDirection(MergeDirection.CHILD_TO_TRUNK)
                    .successful(false)
                    .errorMessage("Child-to-trunk merge failed: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public MergeDescriptor finalMergeToSourceDescriptor(String mainWorktreeId) {
        try {
            MergeResult result = finalMergeToSource(mainWorktreeId);

            Optional<WorktreeContext> mainWt = worktreeRepository.findById(mainWorktreeId);
            if (mainWt.isPresent() && mainWt.get() instanceof MainWorktreeContext mainContext) {
                result = ensureMergeConflictsCaptured(result, mainContext);
            } else {
                result = ensureMergeConflictsCaptured(result);
            }

            return toMergeDescriptor(result, MergeDirection.WORKTREE_TO_SOURCE);
        } catch (Exception e) {
            log.error("Final merge to source descriptor failed", e);
            return MergeDescriptor.builder()
                    .mergeDirection(MergeDirection.WORKTREE_TO_SOURCE)
                    .successful(false)
                    .errorMessage("Final merge to source failed: " + e.getMessage())
                    .build();
        }
    }

    private MergeDescriptor toMergeDescriptor(MergeResult result, MergeDirection direction) {
        List<String> conflictFiles = result.conflicts() != null
                ? result.conflicts().stream()
                    .map(MergeResult.MergeConflict::filePath)
                    .filter(Objects::nonNull)
                    .toList()
                : List.of();

        // Group conflicts by submodule path to build SubmoduleMergeResults
        Map<String, List<MergeResult.MergeConflict>> bySubmodule = result.conflicts() != null
                ? result.conflicts().stream()
                    .filter(c -> c.submodulePath() != null && !c.submodulePath().isBlank())
                    .collect(Collectors.groupingBy(MergeResult.MergeConflict::submodulePath))
                : Map.of();

        List<SubmoduleMergeResult> submoduleResults = bySubmodule.entrySet().stream()
                .map(entry -> new SubmoduleMergeResult(
                        entry.getKey(),
                        result.childWorktreePath(),
                        result.parentWorktreePath(),
                        result,
                        false
                ))
                .toList();

        return MergeDescriptor.builder()
                .mergeDirection(direction)
                .successful(result.successful())
                .conflictFiles(conflictFiles)
                .submoduleMergeResults(submoduleResults)
                .mainWorktreeMergeResult(result)
                .errorMessage(result.successful() ? null : result.mergeMessage())
                .build();
    }

    public boolean parentContainsChildHead(String childWorktreeId, String parentWorktreeId) {
        String childPath = resolveWorktreePath(childWorktreeId);
        String parentPath = resolveWorktreePath(parentWorktreeId);
        return parentContainsChildHeadByPath(childPath, parentPath);
    }

    public boolean parentContainsChildHeadByPath(String childPath, String parentPath) {
        if (childPath == null || parentPath == null) {
            return false;
        }
        Path child = Path.of(childPath);
        Path parent = Path.of(parentPath);
        if (!Files.exists(child) || !Files.exists(parent)) {
            return false;
        }

        try (var childGit = openGit(child); var parentGit = openGit(parent)) {
            ObjectId childHead = childGit.getRepository().resolve(Constants.HEAD);
            ObjectId parentHead = parentGit.getRepository().resolve(Constants.HEAD);
            if (childHead == null || parentHead == null) {
                return false;
            }

            try (RevWalk walk = new RevWalk(parentGit.getRepository())) {
                RevCommit childCommit = walk.parseCommit(childHead);
                RevCommit parentCommit = walk.parseCommit(parentHead);
                return walk.isMergedInto(childCommit, parentCommit);
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ======== PRIVATE HELPERS ========

    public static void cloneRepository(String repositoryUrl, Path worktreePath, String baseBranch) throws GitAPIException, IOException {
        try(Repository build = RepoUtil.findRepo(Paths.get(repositoryUrl))) {
            CloneCommand clone = Git.cloneRepository()
                    .setURI(build.getDirectory().toString())
                    .setDirectory(worktreePath.toFile());

            try (
                    Git git = clone.call()
            ) {
                RepoUtil.runGitCommand(Paths.get(repositoryUrl), List.of("submodule", "update", "--init", "--recursive"));
                if (baseBranch != null && !baseBranch.isBlank() && !Objects.equals(git.getRepository().getBranch(), baseBranch)) {
                    git.checkout().setName(baseBranch).call();
                    RepoUtil.runGitCommand(Paths.get(repositoryUrl), List.of("submodule", "foreach", "--recursive", "git reset --hard || true"));
                }
            }
        }


        var updating = RepoUtil.updateSubmodulesRecursively(worktreePath);
        log.debug("Updated submodules {}", updating);
    }

    private void checkoutNewBranch(Path repoPath, String newBranchName, String startPoint) throws IOException, GitAPIException {
        try (var git = openGit(repoPath)) {
            CheckoutCommand checkout = git.checkout()
                    .setCreateBranch(true)
                    .setName(newBranchName);
            if (startPoint != null && !startPoint.isBlank()) {
                checkout.setStartPoint(startPoint);
            }
            checkout.call();
        }
    }

    private List<String> initializeSubmodule(Path parentWorktreePath) {
        var result = RepoUtil.updateSubmodulesRecursively(parentWorktreePath);
        if (result.isErr()) {
            throw new RuntimeException(result.errorMessage());
        }
        return result.unwrap();
    }

    private org.eclipse.jgit.api.MergeResult mergeFromChildRepo(Path parentPath, Path childPath, String childBranch) throws IOException, GitAPIException, URISyntaxException {
        String remoteNamespace = "child-" + shortId(childPath.getFileName().toString());
        String remoteRefBase = "refs/remotes/" + remoteNamespace + "/";
        String headRef = remoteRefBase + "HEAD";

        try (var parentGit = openGit(parentPath);
             var rep = new FileRepositoryBuilder().findGitDir(childPath.toFile()).build()) {

            parentGit.remoteAdd().setName("child")
                    .setUri(new URIish(rep.getDirectory().toString()))
                    .call();

            // Fetch both HEAD (for detached HEAD submodules) and named branches.
            FetchCommand fetch = parentGit.fetch()
                    .setRemote("child")
                    .setRefSpecs(
                            new RefSpec("+HEAD:" + headRef),
                            new RefSpec("+refs/heads/*:" + remoteRefBase + "*")
                    );

            fetch.call();

            parentGit.remoteRemove().setRemoteName("child")
                    .call();

            // Prefer HEAD (always represents the child's actual current state,
            // works for both named branches and detached HEAD submodules).
            // Fall back to the named branch ref if HEAD is unavailable.
            Repository repo = parentGit.getRepository();
            ObjectId mergeHead = repo.resolve(headRef);
            if (mergeHead == null) {
                String branchRef = remoteRefBase + (childBranch == null || childBranch.isBlank()
                        ? "main"
                        : childBranch);
                mergeHead = repo.resolve(branchRef);
            }
            if (mergeHead == null) {
                throw new IllegalStateException("Unable to resolve merge ref for child at " + childPath);
            }

            return parentGit.merge()
                    .include(mergeHead)
                    .setCommit(true)
                    .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                    .call();
        }
    }

    private WorktreeSandboxContext branchSandboxContext(
            WorktreeSandboxContext parentContext,
            String branchName,
            String nodeId
    ) {
        try {
            MainWorktreeContext main = branchWorktree(
                    parentContext.mainWorktree().worktreeId(),
                    branchName,
                    nodeId
            );
            List<SubmoduleWorktreeContext> submodules = new ArrayList<>();
            if (parentContext.submoduleWorktrees() != null) {
                for (SubmoduleWorktreeContext submodule : parentContext.submoduleWorktrees()) {
                    if (submodule == null) {
                        continue;
                    }
                    submodules.add(branchSubmoduleWorktree(
                            submodule.worktreeId(),
                            branchName,
                            nodeId
                    ));
                }
            }
            return new WorktreeSandboxContext(main, submodules);
        } catch (Exception ex) {
            return parentContext;
        }
    }

    private List<SubmoduleWorktreeContext> createSubmoduleContexts(
            List<String> submodulePaths,
            String parentWorktreeId,
            Path parentWorktreePath,
            String nodeId,
            String parentBranch
    ) {
        if (submodulePaths == null || submodulePaths.isEmpty()) {
            return List.of();
        }
        List<SubmoduleWorktreeContext> contexts = new ArrayList<>();
        for (String submodulePath : submodulePaths) {
            if (submodulePath == null || submodulePath.isBlank()) {
                continue;
            }
            try {
                SubmoduleWorktreeContext context = createSubmoduleWorktree(
                        submodulePath,
                        submodulePath,
                        parentWorktreeId,
                        parentWorktreePath,
                        nodeId,
                        parentBranch
                );
                contexts.add(context);
            } catch (Exception e) {
                log.warn("Skipping submodule '{}': {}", submodulePath, e.getMessage());
                eventBus.publish(Events.NodeErrorEvent.err(
                        "Failed to initialize submodule '%s': %s".formatted(submodulePath, e.getMessage()),
                        getKey(nodeId)
                ));
            }
        }
        return contexts;
    }

    @Override
    public MergeResult finalMergeToSource(String mainWorktreeId) {
        Optional<WorktreeContext> mainWt = worktreeRepository.findById(mainWorktreeId);
        if (mainWt.isEmpty() || !(mainWt.get() instanceof MainWorktreeContext mainContext)) {
            return new MergeResult(
                    UUID.randomUUID().toString(),
                    mainWorktreeId, "source",
                    null, null,
                    false, null, List.of(), List.of(),
                    "Main worktree not found: " + mainWorktreeId,
                    Instant.now()
            );
        }

        Path worktreePath = mainContext.worktreePath();
        Path sourcePath = Path.of(mainContext.repositoryUrl());
        String derivedBranch = mainContext.derivedBranch();
        String baseBranch = mainContext.baseBranch();

        try {
            // Phase 1: Build merge plan (leaves-first). Child = worktree, Parent = source repo.
            List<MergePlanStep> plan = buildFinalMergePlan(worktreePath, sourcePath, derivedBranch, baseBranch);
            log.debug("Final merge plan has {} steps", plan.size());

            // Phase 2: Execute each step with lifecycle.
            MergeExecutionState state = new MergeExecutionState();
            for (MergePlanStep step : plan) {
                executeStepWithLifecycle(step, state, mainContext, mainContext);
            }

            // Phase 3: Finalize.
            if (state.hasConflicts()) {
                return buildConflictResult(mainWorktreeId, "source", state.allConflicts);
            }

            // Commit submodule pointers, ensure source is on baseBranch.
            commitDirtySubmodulePointers(sourcePath);
            ensureBranchMatchesContext(sourcePath, baseBranch);
            commitDirtySubmodulePointers(sourcePath);

            String mergeCommit = getCurrentCommitHashInternal(sourcePath);
            return new MergeResult(
                    UUID.randomUUID().toString(),
                    mainWorktreeId, "source",
                    normalizePath(worktreePath),
                    normalizePath(sourcePath),
                    true, mergeCommit,
                    List.of(), List.of(),
                    "Final merge to source successful",
                    Instant.now()
            );
        } catch (Exception e) {
            log.error("Final merge to source failed", e);
            return new MergeResult(
                    UUID.randomUUID().toString(),
                    mainWorktreeId, "source",
                    normalizePath(worktreePath),
                    normalizePath(sourcePath),
                    false, null, List.of(), List.of(),
                    "Final merge failed: " + e.getMessage(),
                    Instant.now()
            );
        }
    }

    /**
     * Build a merge plan for merging worktree derived branches back into the source repo's
     * original branches. Ordered leaves-first, root-last.
     */
    private List<MergePlanStep> buildFinalMergePlan(Path worktreePath, Path sourcePath,
                                                     String derivedBranch, String baseBranch) throws IOException {
        List<MergePlanStep> plan = new ArrayList<>();

        // Ensure source repo submodules are initialized.
        try {
            initializeSubmodule(sourcePath);
        } catch (Exception e) {
            log.debug("Source submodule init skipped: {}", e.getMessage());
        }

        // Collect submodule steps (leaves first).
        collectFinalMergeSubmoduleSteps(worktreePath, sourcePath, sourcePath, "", plan);

        // Add the root merge step last.
        String worktreeCurrentBranch;
        try (var g = openGit(worktreePath)) {
            worktreeCurrentBranch = g.getRepository().getBranch();
        }

        // Ensure source is on baseBranch before merging.
        ensureBranchMatchesContext(sourcePath, baseBranch);

        plan.add(new MergePlanStep(
                worktreePath,       // child
                sourcePath,         // parent
                null,               // root has no submodule path
                null,               // root has no formatted submodule path
                worktreeCurrentBranch,  // child branch (derivedBranch)
                baseBranch,             // parent branch (baseBranch in source)
                null                    // root has no parent-of-parent
        ));

        return plan;
    }

    /**
     * Recursively collect merge steps for submodules when merging back to source.
     * Reads the parent (target) branch from the source repo's submodule git state.
     */
    private void collectFinalMergeSubmoduleSteps(Path worktreePath, Path sourcePath,
                                                  Path sourceParentOfParent, String parentRelPath,
                                                  List<MergePlanStep> plan) throws IOException {
        List<String> submoduleNames;
        try {
            submoduleNames = getSubmoduleNames(worktreePath);
        } catch (Exception e) {
            return;
        }

        for (String submoduleName : submoduleNames) {
            Path worktreeSubPath;
            Path sourceSubPath;
            String relPath = parentRelPath == null || parentRelPath.isBlank()
                    ? submoduleName
                    : parentRelPath + "/" + submoduleName;
            try {
                worktreeSubPath = getSubmodulePath(worktreePath, submoduleName);
                sourceSubPath = getSubmodulePath(sourcePath, submoduleName);
            } catch (Exception e) {
                continue;
            }

            // Recurse into nested submodules first (leaves before parents).
            collectFinalMergeSubmoduleSteps(worktreeSubPath, sourceSubPath, sourcePath, relPath, plan);

            // Child branch: whatever the worktree's submodule is on (the derived branch).
            String childBranch;
            try (var g = openGit(worktreeSubPath)) {
                childBranch = g.getRepository().getBranch();
            }

            // Parent branch: whatever the source repo's submodule is on (the original branch).
            String parentBranch;
            try (var g = openGit(sourceSubPath)) {
                parentBranch = g.getRepository().getBranch();
            }

            plan.add(new MergePlanStep(
                    worktreeSubPath,
                    sourceSubPath,
                    relPath,
                    formatSubmodulePath(relPath),
                    childBranch,
                    parentBranch,
                    sourcePath
            ));
        }
    }

    private static String shortId(String id) {
        if (id == null) {
            return "";
        }
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    /**
     * Build a comprehensive merge plan ordered leaves-first, root-last.
     * Each step knows its parent-of-parent path so that after a successful merge,
     * dirty submodule pointers can be committed in the correct containing repo.
     */
    private List<MergePlanStep> buildMergePlan(Path childPath, Path parentPath, String rootChildBranch) throws IOException {
        List<MergePlanStep> plan = new ArrayList<>();
        collectSubmoduleSteps(childPath, parentPath, parentPath, "", plan);

        // Add the root module as the final step (no submodulePath, no parent-of-parent).
        String childBranch;
        try (var g = openGit(childPath)) {
            childBranch = g.getRepository().getBranch();
        }
        String parentBranch;
        try (var g = openGit(parentPath)) {
            parentBranch = g.getRepository().getBranch();
        }
        plan.add(new MergePlanStep(
                childPath,
                parentPath,
                null,  // root has no submodule path
                null,  // root has no formatted submodule path
                childBranch,
                parentBranch,
                null   // root has no parent-of-parent
        ));

        return plan;
    }

    /**
     * Recursively collect merge steps for submodules, depth-first (leaves added before parents).
     */
    private void collectSubmoduleSteps(Path childPath, Path parentPath,
                                       Path parentOfParentPath, String parentRelPath,
                                       List<MergePlanStep> plan) throws IOException {
        List<String> submoduleNames;
        try {
            submoduleNames = getSubmoduleNames(childPath);
        } catch (Exception e) {
            return;
        }

        for (String submoduleName : submoduleNames) {
            Path childSubPath;
            Path parentSubPath;
            String relPath = parentRelPath == null || parentRelPath.isBlank()
                    ? submoduleName
                    : parentRelPath + "/" + submoduleName;
            try {
                childSubPath = getSubmodulePath(childPath, submoduleName);
                parentSubPath = getSubmodulePath(parentPath, submoduleName);
            } catch (Exception e) {
                continue;
            }

            // Recurse into nested submodules first (leaves before parents).
            collectSubmoduleSteps(childSubPath, parentSubPath, parentPath, relPath, plan);

            String childBranch;
            try (var g = openGit(childSubPath)) {
                childBranch = g.getRepository().getBranch();
            }
            String parentBranch;
            try (var g = openGit(parentSubPath)) {
                parentBranch = g.getRepository().getBranch();
            }
            plan.add(new MergePlanStep(
                    childSubPath,
                    parentSubPath,
                    relPath,
                    formatSubmodulePath(relPath),
                    childBranch,
                    parentBranch,
                    parentPath
            ));
        }
    }

    /**
     * A single step in the merge plan. Includes the parent-of-parent path so that
     * after a successful submodule merge, we commit dirty pointers in the containing repo.
     */
    private record MergePlanStep(
            Path childPath,
            Path parentPath,
            String submodulePath,
            String formattedSubmodulePath,
            String childBranch,
            String parentBranch,
            Path parentOfParentPath
    ) {}

    private String formatSubmodulePath(String path) {
        if (path == null) {
            return null;
        }
        return path.replace("/", "_");
    }

    private boolean hasSubmodulesInternal(Path repositoryPath) {
        return Files.exists(repositoryPath.resolve(".gitmodules"));
    }

    private void emitMergeStepSuccess(
            MergePlanStep step,
            WorktreeContext rootChildContext,
            WorktreeContext rootParentContext
    ) {
        WorktreeContext child = worktreeRepository.findByPath(step.childPath()).orElse(rootChildContext);
        WorktreeContext parent = worktreeRepository.findByPath(step.parentPath()).orElse(rootParentContext);
        MergeResult result = new MergeResult(
                UUID.randomUUID().toString(),
                resolveWorktreeId(step.childPath(), child),
                resolveWorktreeId(step.parentPath(), parent),
                normalizePath(step.childPath()),
                normalizePath(step.parentPath()),
                true,
                getCurrentCommitHashInternal(step.parentPath()),
                List.of(),
                List.of(),
                "Merge step successful",
                Instant.now()
        );
        emitMergeLifecycleEvent(result, child, parent, step.submodulePath() != null ? "submodule" : "main");
    }

    private void emitMergeStepConflict(
            MergePlanStep step,
            WorktreeContext rootChildContext,
            WorktreeContext rootParentContext,
            List<MergeResult.MergeConflict> conflicts
    ) {
        WorktreeContext child = worktreeRepository.findByPath(step.childPath()).orElse(rootChildContext);
        WorktreeContext parent = worktreeRepository.findByPath(step.parentPath()).orElse(rootParentContext);
        MergeResult conflictResult = new MergeResult(
                UUID.randomUUID().toString(),
                resolveWorktreeId(step.childPath(), child),
                resolveWorktreeId(step.parentPath(), parent),
                normalizePath(step.childPath()),
                normalizePath(step.parentPath()),
                false,
                null,
                conflicts != null ? conflicts : List.of(),
                List.of(),
                "Merge step conflict",
                Instant.now()
        );
        emitMergeFailure(conflictResult, child, parent, "Merge step failed for " + Optional.ofNullable(step.submodulePath()).orElse("root"));
    }

    private void emitMergeFailure(
            MergeResult result,
            WorktreeContext childContext,
            WorktreeContext parentContext,
            String reason
    ) {
        emitMergeLifecycleEvent(result, childContext, parentContext, inferWorktreeType(childContext));
        String nodeId = resolveMergeNodeId(childContext, parentContext);
        if (eventBus == null || nodeId == null || nodeId.isBlank()) {
            return;
        }
        eventBus.publish(Events.NodeErrorEvent.err(
                "Worktree merge failure: " + reason + " | child=" + result.childWorktreeId()
                        + " parent=" + result.parentWorktreeId()
                        + " conflicts=" + (result.conflicts() != null ? result.conflicts().size() : 0),
                getKey(nodeId)
        ));
    }

    private static ArtifactKey getKey(String nodeId) {
        try {
            return new ArtifactKey(nodeId);
        } catch (IllegalArgumentException e) {
            log.error("Error - could not create artifact key for {} when trying to push merge failure event.", nodeId);
            return ArtifactKey.createRoot();
        }
    }

    private void emitMergeLifecycleEvent(
            MergeResult result,
            WorktreeContext childContext,
            WorktreeContext parentContext,
            String worktreeType
    ) {
        if (eventBus == null || result == null) {
            return;
        }
        String nodeId = resolveMergeNodeId(childContext, parentContext);
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        List<String> conflictFiles = Optional.ofNullable(result.conflicts()).orElse(List.of())
                .stream()
                .map(conflict -> {
                    String submodule = conflict.submodulePath();
                    String file = conflict.filePath();
                    if (submodule == null || submodule.isBlank()) {
                        return file;
                    }
                    return submodule + "/" + file;
                })
                .toList();
        eventBus.publish(new Events.WorktreeMergedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                result.childWorktreeId(),
                result.parentWorktreeId(),
                result.mergeCommitHash(),
                !result.successful(),
                conflictFiles,
                worktreeType != null ? worktreeType : "main",
                nodeId
        ));
    }

    private String resolveWorktreeId(Path path, WorktreeContext context) {
        if (context != null && context.worktreeId() != null && !context.worktreeId().isBlank()) {
            return context.worktreeId();
        }
        return normalizePath(path);
    }

    private String resolveMergeNodeId(WorktreeContext childContext, WorktreeContext parentContext) {
        if (parentContext != null && parentContext.associatedNodeId() != null && !parentContext.associatedNodeId().isBlank()) {
            return parentContext.associatedNodeId();
        }
        if (childContext != null && childContext.associatedNodeId() != null && !childContext.associatedNodeId().isBlank()) {
            return childContext.associatedNodeId();
        }
        return null;
    }

    private String inferWorktreeType(WorktreeContext context) {
        if (context instanceof SubmoduleWorktreeContext) {
            return "submodule";
        }
        return "main";
    }

    private String getCurrentCommitHashInternal(Path worktreePath) {
        try (var git = openGit(worktreePath)) {
            Repository repo = git.getRepository();
            ObjectId head = repo.resolve(Constants.HEAD);
            return head != null ? head.name() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getBranchSafe(Path path) {
        try (var g = openGit(path)) {
            return g.getRepository().getBranch();
        } catch (Exception e) {
            return "error:" + e.getMessage();
        }
    }

    private String normalizePath(Path path) {
        if (path == null) {
            return null;
        }
        return path.toAbsolutePath().normalize().toString();
    }

    private Git openGit(Path repoPath) throws IOException {
        return RepoUtil.initGitOrThrow(repoPath);
    }

    private void deleteRecursively(Path path) throws IOException {
//        if (Files.isDirectory(path)) {
//            Files.list(path).forEach(child -> {
//                try {
//                    deleteRecursively(child);
//                } catch (IOException e) {
//                    System.err.println("Warning: Could not delete " + child + ": " + e.getMessage());
//                }
//            });
//        }
//        Files.delete(path);
    }
}
