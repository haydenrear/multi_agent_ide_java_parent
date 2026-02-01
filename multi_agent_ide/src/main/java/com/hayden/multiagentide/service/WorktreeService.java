package com.hayden.multiagentide.service;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentidelib.model.MergeResult;
import org.springframework.util.CollectionUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing git worktrees including main repo and submodules.
 */
public interface WorktreeService {

    /**
     * Create a worktree for the main repository.
     * @param repositoryUrl the repository URL
     * @param baseBranch the base branch (e.g., "main", "develop")
     * @param nodeId the associated node ID
     * @return the created main worktree context
     */
    MainWorktreeContext createMainWorktree(String repositoryUrl, String baseBranch, String nodeId);

    AgentModels.DiscoveryAgentRequests attachWorktreesToDiscoveryRequests(
            AgentModels.DiscoveryAgentRequests input,
            String nodeId
    );
    AgentModels.PlanningAgentRequests attachWorktreesToPlanningRequests(
            AgentModels.PlanningAgentRequests input,
            String nodeId
    );
    AgentModels.TicketAgentRequests attachWorktreesToTicketRequests(
            AgentModels.TicketAgentRequests input,
            String nodeId
    );

    /**
     * Create a worktree for a git submodule.
     * @param submoduleName the submodule name
     * @param submodulePath the path to the submodule in the repo
     * @param parentWorktreeId the parent worktree ID
     * @param parentWorktreePath the parent worktree path
     * @param nodeId the associated node ID
     * @return the created submodule worktree context
     */
    SubmoduleWorktreeContext createSubmoduleWorktree(String submoduleName, String submodulePath,
                                                     String parentWorktreeId, Path parentWorktreePath, String nodeId);

    /**
     * Get a worktree context by ID.
     * @param worktreeId the worktree ID
     * @return the worktree context if found
     */
    Optional<MainWorktreeContext> getMainWorktree(String worktreeId);

    /**
     * Get a submodule worktree context by ID.
     * @param worktreeId the worktree ID
     * @return the submodule worktree context if found
     */
    Optional<SubmoduleWorktreeContext> getSubmoduleWorktree(String worktreeId);

    /**
     * Get all submodule worktrees for a main worktree.
     * @param mainWorktreeId the main worktree ID
     * @return list of submodule worktree contexts
     */
    List<SubmoduleWorktreeContext> getSubmoduleWorktrees(String mainWorktreeId);

    /**
     * Detect if a repository has git submodules.
     * @param repositoryPath the path to the repository
     * @return true if submodules exist
     */
    boolean hasSubmodules(Path repositoryPath);

    /**
     * Get list of submodule names in a repository.
     * @param repositoryPath the path to the repository
     * @return list of submodule names
     */
    List<String> getSubmoduleNames(Path repositoryPath);

    /**
     * Get the path of a submodule.
     * @param repositoryPath the repository path
     * @param submoduleName the submodule name
     * @return the path to the submodule
     */
    Path getSubmodulePath(Path repositoryPath, String submoduleName);

    /**
     * Merge a child worktree into a parent worktree.
     * @param childWorktreeId the child worktree ID
     * @param parentWorktreeId the parent worktree ID
     * @return merge result with conflict information
     */
    MergeResult mergeWorktrees(String childWorktreeId, String parentWorktreeId);

    /**
     * Branch a worktree (create a new independent worktree from existing).
     * @param sourceWorktreeId the source worktree ID
     * @param newBranchName the new branch name
     * @param nodeId the associated node ID for the branch
     * @return the created branched worktree context
     */
    MainWorktreeContext branchWorktree(String sourceWorktreeId, String newBranchName, String nodeId);

    /**
     * Branch a submodule worktree.
     * @param sourceWorktreeId the source submodule worktree ID
     * @param newBranchName the new branch name
     * @param nodeId the associated node ID for the branch
     * @return the created branched submodule worktree context
     */
    SubmoduleWorktreeContext branchSubmoduleWorktree(String sourceWorktreeId, String newBranchName, String nodeId);

    /**
     * Discard/remove a worktree and its children.
     * @param worktreeId the worktree ID
     */
    void discardWorktree(String worktreeId);

    /**
     * Get the current commit hash in a worktree.
     * @param worktreeId the worktree ID
     * @return the commit hash
     */
    String getCurrentCommitHash(String worktreeId);

    /**
     * Commit changes in a worktree.
     * @param worktreeId the worktree ID
     * @param message the commit message
     * @return the commit hash
     */
    String commitChanges(String worktreeId, String message);

    /**
     * Update submodule pointer in main worktree after submodule changes.
     * @param mainWorktreeId the main worktree ID
     * @param submoduleName the submodule name
     */
    void updateSubmodulePointer(String mainWorktreeId, String submoduleName);

    /**
     * Detect merge conflicts between worktrees.
     * @param childWorktreeId the child worktree ID
     * @param parentWorktreeId the parent worktree ID
     * @return list of files with conflicts
     */
    List<String> detectMergeConflicts(String childWorktreeId, String parentWorktreeId);

    default boolean containsMergeConflicts(String childWorktreeId, String parentWorktreeId) {
        return !CollectionUtils.isEmpty(detectMergeConflicts(childWorktreeId, parentWorktreeId));
    }
}
