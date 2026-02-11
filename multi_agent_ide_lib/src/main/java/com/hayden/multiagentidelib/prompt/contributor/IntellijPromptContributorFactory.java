package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class IntellijPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null) {
            return List.of();
        }

        return resolveMainWorktree(context)
                .map(IntellijPromptContributor::new)
                .<List<PromptContributor>>map(List::of)
                .orElseGet(List::of);
    }

    private Optional<MainWorktreeContext> resolveMainWorktree(PromptContext context) {
        return Optional.ofNullable(context.currentRequest())
                .map(ar -> ar.worktreeContext())
                .map(WorktreeSandboxContext::mainWorktree)
                .or(() -> Optional.ofNullable(context.previousRequest())
                        .map(ar -> ar.worktreeContext())
                        .map(WorktreeSandboxContext::mainWorktree));
    }

    public record IntellijPromptContributor(MainWorktreeContext main) implements PromptContributor {

        @Override
        public String name() {
            return this.getClass().getSimpleName();
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            String mainPath = main.worktreePath() != null ? main.worktreePath().toString() : "(unknown)";
            String repoUrl = main.repositoryUrl() != null ? main.repositoryUrl() : "(unknown)";
            return template()
                    .replace("{{intellij_project_path}}", mainPath)
                    .replace("{{source_repository_path}}", repoUrl);
        }

        @Override
        public String template() {
            return """
                    For IntelliJ MCP tool calls, always target the worktree project opened in IntelliJ.

                    We run `idea .` from the worktree root, which adds that worktree as a project IntelliJ MCP can query.

                    For every IntelliJ MCP request:
                    - Set `projectPath` to the worktree path below.
                    - Do not set `projectPath` to the original repository path.

                    Worktree path (use this as `projectPath`): {{intellij_project_path}}
                    Original repository path (reference only): {{source_repository_path}}
                    """;
        }

        @Override
        public int priority() {
            return 10_000;
        }
    }

}
