package com.hayden.multiagentide.prompt.contributor;

import com.google.common.collect.Lists;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Contributes worktree sandbox information to the prompt whenever a
 * WorktreeSandboxContext is available on the current or previous request.
 * This ensures all agents are aware of the sandbox boundaries and
 * worktree paths they should operate within.
 */
@Component
public class WorktreeSandboxPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null) {
            return List.of();
        }

        WorktreeSandboxContext worktreeContext = resolveWorktreeContext(context);
        if (worktreeContext == null || worktreeContext.mainWorktree() == null) {
            return List.of();
        }

        return Lists.newArrayList(new WorktreeSandboxPromptContributor(worktreeContext));
    }

    private WorktreeSandboxContext resolveWorktreeContext(PromptContext context) {
        if (context.currentRequest() != null && context.currentRequest().worktreeContext() != null) {
            return context.currentRequest().worktreeContext();
        }
        if (context.previousRequest() != null && context.previousRequest().worktreeContext() != null) {
            return context.previousRequest().worktreeContext();
        }
        return null;
    }

    public record WorktreeSandboxPromptContributor(
            WorktreeSandboxContext worktreeContext
    ) implements PromptContributor {

        @Override
        public String name() {
            return "worktree-sandbox-context";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            MainWorktreeContext main = worktreeContext.mainWorktree();

            String mainPath = main.worktreePath() != null ? main.worktreePath().toString() : "(unknown)";
            String baseBranch = main.baseBranch() != null ? main.baseBranch() : "(unknown)";
            String derivedBranch = main.derivedBranch() != null ? main.derivedBranch() : "(unknown)";
            String repoUrl = main.repositoryUrl() != null ? main.repositoryUrl() : "(unknown)";

            String submoduleInfo = buildSubmoduleInfo(worktreeContext.submoduleWorktrees());

            return template()
                    .replace("{{main_worktree_path}}", mainPath)
                    .replace("{{base_branch}}", baseBranch)
                    .replace("{{derived_branch}}", derivedBranch)
                    .replace("{{repository_url}}", repoUrl)
                    .replace("{{submodule_info}}", submoduleInfo);
        }

        private String buildSubmoduleInfo(List<SubmoduleWorktreeContext> submodules) {
            if (submodules == null || submodules.isEmpty()) {
                return "No submodule worktrees.";
            }
            return submodules.stream()
                    .map(sub -> "  - **%s**: %s (branch: %s)".formatted(
                            sub.submoduleName() != null ? sub.submoduleName() : "(unknown)",
                            sub.worktreePath() != null ? sub.worktreePath().toString() : "(unknown)",
                            sub.baseBranch() != null ? sub.baseBranch() : "(unknown)"
                    ))
                    .collect(Collectors.joining("\n"));
        }

        @Override
        public String template() {
            return """
                    ## Worktree Sandbox Context
                    
                    All file operations must be performed within the sandbox worktree boundaries.
                    Do not read or write files outside of these paths.
                    
                    ### Main Worktree
                    - **Path**: {{main_worktree_path}}
                    - **Repository**: {{repository_url}}
                    - **Base Branch**: {{base_branch}}
                    - **Derived Branch**: {{derived_branch}}
                    
                    ### Submodule Worktrees
                    {{submodule_info}}
                    """;
        }

        @Override
        public int priority() {
            return 80;
        }
    }
}
