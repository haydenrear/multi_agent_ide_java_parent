package com.hayden.multiagentide.service;

import com.embabel.agent.api.common.ActionContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import com.hayden.multiagentidelib.model.MergeResult;
import com.hayden.multiagentidelib.model.worktree.WorktreeContext;
import com.hayden.multiagentide.repository.WorktreeRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Git-based implementation of WorktreeService using git worktree commands.
 * Handles both main repository and git submodule worktrees.
 */
@Service
public class GitWorktreeService implements WorktreeService {

    private final WorktreeRepository worktreeRepository;
    private final String baseWorktreesPath;

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
    public MainWorktreeContext createMainWorktree(String repositoryUrl, String baseBranch, String nodeId) {
        String worktreeId = UUID.randomUUID().toString();
        Path worktreePath = Paths.get(baseWorktreesPath, worktreeId);

        try {
            Files.createDirectories(worktreePath);
            try {
                executeGitCommand(worktreePath.getParent().toString(), "git", "clone", repositoryUrl, worktreePath.toString());
                executeGitCommand(worktreePath.toString(), "git", "checkout", baseBranch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Git command interrupted", e);
            }

            String commitHash = getCurrentCommitHashInternal(worktreePath);

            MainWorktreeContext context = new MainWorktreeContext(
                    worktreeId,
                    worktreePath,
                    baseBranch,
                    WorktreeContext.WorktreeStatus.ACTIVE,
                    null,
                    nodeId,
                    Instant.now(),
                    commitHash,
                    repositoryUrl,
                    hasSubmodulesInternal(worktreePath),
                    new ArrayList<>(),
                    new HashMap<>()
            );

            worktreeRepository.save(context);
            return context;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create main worktree: " + e.getMessage(), e);
        }
    }

    @Override
    public SubmoduleWorktreeContext createSubmoduleWorktree(String submoduleName, String submodulePath,
                                                             String parentWorktreeId, Path parentWorktreePath, String nodeId) {
        String worktreeId = UUID.randomUUID().toString();
        Path submoduleFullPath = parentWorktreePath.resolve(submodulePath);

        try {
            // Initialize submodule if not already done
            executeGitCommand(
                    parentWorktreePath.toString(),
                    "git",
                    "-c",
                    "protocol.file.allow=always",
                    "submodule",
                    "update",
                    "--init",
                    "--recursive",
                    submodulePath
            );

            String commitHash = getCurrentCommitHashInternal(submoduleFullPath);

            SubmoduleWorktreeContext context = new SubmoduleWorktreeContext(
                    worktreeId,
                    submoduleFullPath,
                    "main", // submodules typically use main or master
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

            worktreeRepository.save(context);
            return context;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create submodule worktree: " + e.getMessage(), e);
        }
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

    @Override
    public boolean hasSubmodules(Path repositoryPath) {
        return hasSubmodulesInternal(repositoryPath);
    }

    @Override
    public List<String> getSubmoduleNames(Path repositoryPath) {
        Path gitmodulesPath = repositoryPath.resolve(".gitmodules");
        if (!Files.exists(gitmodulesPath)) {
            return new ArrayList<>();
        }

        List<String> submodules = new ArrayList<>();
        try {
            String output = executeGitCommand(repositoryPath.toString(), "git", "config", "--file", ".gitmodules", "--name-only", "--get-regexp", "path");
            for (String line : output.split("\n")) {
                if (line.startsWith("submodule.") && line.endsWith(".path")) {
                    String name = line.substring("submodule.".length(), line.length() - ".path".length());
                    submodules.add(name);
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not get submodule names: " + e.getMessage());
        }

        return submodules;
    }

    @Override
    public Path getSubmodulePath(Path repositoryPath, String submoduleName) {
        try {
            String output = executeGitCommand(repositoryPath.toString(), "git", "config", "--file", ".gitmodules", "--get", "submodule." + submoduleName + ".path");
            return repositoryPath.resolve(output.trim());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get submodule path: " + e.getMessage(), e);
        }
    }

    @Override
    public MergeResult mergeWorktrees(String childWorktreeId, String parentWorktreeId) {
        Optional<MainWorktreeContext> childWt = getMainWorktree(childWorktreeId);
        Optional<MainWorktreeContext> parentWt = getMainWorktree(parentWorktreeId);

        if (childWt.isEmpty() || parentWt.isEmpty()) {
            return new MergeResult(
                    UUID.randomUUID().toString(),
                    childWorktreeId,
                    parentWorktreeId,
                    false,
                    null,
                    List.of(),
                    List.of(),
                    "Worktree not found",
                    Instant.now()
            );
        }

        try {
            Path childPath = childWt.get().worktreePath();
            Path parentPath = parentWt.get().worktreePath();

            // Get the branch to merge from
            String childBranch = childWt.get().baseBranch();
            
            // Switch parent to merge from
            String mergeOutput = executeGitCommand(parentPath.toString(), 
                    "git", "merge", "--no-edit", childBranch);

            List<String> conflictFileNames = detectMergeConflicts(childWorktreeId, parentWorktreeId);
            
            List<MergeResult.MergeConflict> conflicts = conflictFileNames.stream()
                    .map(file -> new MergeResult.MergeConflict(file, "content", "", "", ""))
                    .collect(Collectors.toList());
            
            if (conflicts.isEmpty()) {
                String mergeCommit = getCurrentCommitHashInternal(parentPath);
                MainWorktreeContext updated = parentWt.get().withLastCommit(mergeCommit);
                worktreeRepository.save(updated);
                return new MergeResult(
                        UUID.randomUUID().toString(),
                        childWorktreeId,
                        parentWorktreeId,
                        true,
                        mergeCommit,
                        List.of(),
                        List.of(),
                        "Merge successful",
                        Instant.now()
                );
            } else {
                return new MergeResult(
                        UUID.randomUUID().toString(),
                        childWorktreeId,
                        parentWorktreeId,
                        false,
                        null,
                        conflicts,
                        List.of(),
                        "Merge conflicts detected",
                        Instant.now()
                );
            }
        } catch (Exception e) {
            return new MergeResult(
                    UUID.randomUUID().toString(),
                    childWorktreeId,
                    parentWorktreeId,
                    false,
                    null,
                    List.of(),
                    List.of(),
                    "Merge failed: " + e.getMessage(),
                    Instant.now()
            );
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

            // Clone from source
            executeGitCommand(Paths.get(baseWorktreesPath).toString(),
                    "git", "clone", source.get().repositoryUrl(), newWorktreePath.toString());
            
            // Create and checkout new branch
            executeGitCommand(newWorktreePath.toString(),
                    "git", "checkout", "-b", newBranchName, source.get().baseBranch());

            String commitHash = getCurrentCommitHashInternal(newWorktreePath);

            MainWorktreeContext context = new MainWorktreeContext(
                    worktreeId,
                    newWorktreePath,
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
            executeGitCommand(source.get().worktreePath().toString(),
                    "git", "checkout", "-b", newBranchName);

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

    public String resolveNodeId(ActionContext context) {
        if (context == null || context.getProcessContext() == null) {
            return "unknown";
        }
        var options = context.getProcessContext().getProcessOptions();
        if (options == null) {
            return "unknown";
        }
        String contextId = options.getContextIdString();
        return contextId != null ? contextId : "unknown";
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
            System.err.println("Warning: Could not fully discard worktree: " + e.getMessage());
        }
    }

    @Override
    public String getCurrentCommitHash(String worktreeId) {
        Optional<WorktreeContext> wt = worktreeRepository.findById(worktreeId);
        if (wt.isEmpty()) {
            throw new RuntimeException("Worktree not found: " + worktreeId);
        }
        return getCurrentCommitHashInternal(wt.get().worktreePath());
    }

    @Override
    public String commitChanges(String worktreeId, String message) {
        Optional<WorktreeContext> wt = worktreeRepository.findById(worktreeId);
        if (wt.isEmpty()) {
            throw new RuntimeException("Worktree not found: " + worktreeId);
        }

        try {
            Path wtPath = wt.get().worktreePath();
            executeGitCommand(wtPath.toString(), "git", "add", "-A");
            executeGitCommand(wtPath.toString(), "git", "commit", "-m", message);
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
            executeGitCommand(mainPath.toString(), "git", "add", submoduleName);
            executeGitCommand(mainPath.toString(), "git", "commit", "-m", "Update " + submoduleName + " pointer");
        } catch (Exception e) {
            System.err.println("Warning: Could not update submodule pointer: " + e.getMessage());
        }
    }

    @Override
    public List<String> detectMergeConflicts(String childWorktreeId, String parentWorktreeId) {
        Optional<MainWorktreeContext> parentWt = getMainWorktree(parentWorktreeId);
        if (parentWt.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            String output = executeGitCommand(parentWt.get().worktreePath().toString(), 
                    "git", "diff", "--name-only", "--diff-filter=U");
            return Arrays.stream(output.split("\n"))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ======== PRIVATE HELPERS ========

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

    private static String shortId(String id) {
        if (id == null) {
            return "";
        }
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private boolean hasSubmodulesInternal(Path repositoryPath) {
        return Files.exists(repositoryPath.resolve(".gitmodules"));
    }

    private String getCurrentCommitHashInternal(Path worktreePath) {
        try {
            return executeGitCommand(worktreePath.toString(), "git", "rev-parse", "HEAD").trim();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String executeGitCommand(String workingDir, String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(workingDir));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 && !output.toString().contains("fatal")) {
            throw new RuntimeException("Git command failed with exit code " + exitCode + ": " + output);
        }

        return output.toString();
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.list(path).forEach(child -> {
                try {
                    deleteRecursively(child);
                } catch (IOException e) {
                    System.err.println("Warning: Could not delete " + child + ": " + e.getMessage());
                }
            });
        }
        Files.delete(path);
    }
}
