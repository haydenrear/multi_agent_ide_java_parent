package com.hayden.multiagentidelib.model.worktree;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hayden.acp_cdc_ai.sandbox.SandboxContext;
import com.hayden.utilitymodule.stream.StreamUtil;
import lombok.Builder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Builder
public record WorktreeSandboxContext(
        MainWorktreeContext mainWorktree,
        List<SubmoduleWorktreeContext> submoduleWorktrees
) {
    public WorktreeSandboxContext {
        if (submoduleWorktrees == null) {
            submoduleWorktrees = new ArrayList<>();
        }
    }

    public SandboxContext sandboxContext() {
        return SandboxContext.builder()
                .submoduleWorktreePaths(submoduleWorktreePaths(this))
                .mainWorktreePath(mainWorktreePath(this))
                .build();
    }

    @JsonIgnore
    public Path mainWorktreePath(WorktreeSandboxContext sandboxContext) {
        return Optional.ofNullable(sandboxContext)
                .flatMap(ws -> Optional.ofNullable(ws.mainWorktree()))
                .flatMap(ws -> Optional.ofNullable(ws.worktreePath()))
                .orElse(null);
    }

    @JsonIgnore
    public List<Path> submoduleWorktreePaths(WorktreeSandboxContext sandboxContext) {
        return Optional.ofNullable(sandboxContext)
                .stream()
                .flatMap(ws -> StreamUtil.toStream(ws.submoduleWorktrees()))
                .flatMap(ws -> Stream.ofNullable(ws.worktreePath()))
                .toList();
    }
}
