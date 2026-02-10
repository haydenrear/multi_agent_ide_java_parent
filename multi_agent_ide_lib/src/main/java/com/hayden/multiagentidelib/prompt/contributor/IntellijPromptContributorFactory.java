package com.hayden.multiagentidelib.prompt.contributor;

import com.google.common.collect.Lists;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class IntellijPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        return Optional.of(context.currentRequest())
                .flatMap(ar -> Optional.ofNullable(ar.worktreeContext()))
                .flatMap(w -> Optional.ofNullable(w.mainWorktree()))
                .map(IntellijPromptContributor::new)
                .map(Lists::<PromptContributor>newArrayList)
                .orElse(new ArrayList<>());
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
                    .replace("{{project_path}}", repoUrl)
                    .replace("{{worktree_path}}", mainPath);
        }

        @Override
        public String template() {
            return """
                    When you are using Intellij tool calls, you must reference the project from which this worktree was created.
                   
                    Worktree Path (the git worktree path you make changes to): {{worktree_path}}
                    Original Project Path (project path for Intellij worktree created from): {{project_path}}
                    
                    This worktree was created from this project, so when you request information, you request it
                    from the associated project repository.
                    """;
        }

        @Override
        public int priority() {
            return 10_000;
        }
    }

}
