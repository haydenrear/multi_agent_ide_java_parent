package com.hayden.multiagentidelib.model.nodes;

import java.util.List;
import java.util.Optional;

public interface HasWorktree {

    default String mainWorktreeId() {
        return worktree().worktreeId();
    }

    record WorkTree(String worktreeId, String parentWorkTreeId, List<WorkTree> submoduleWorktrees) {}

    WorkTree worktree();

}
