package com.hayden.multiagentidelib.model.worktree;

import com.hayden.acp_cdc_ai.repository.RequestContext;
import com.hayden.utilitymodule.stream.StreamUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WorktreeSandbox {

    private final List<Path> allowedRoots;

    private WorktreeSandbox(List<Path> allowedRoots) {
        this.allowedRoots = allowedRoots;
    }

    public static WorktreeSandbox fromRequestContext(RequestContext r) {
        if (r == null) {
            return new WorktreeSandbox(List.of());
        }
        List<Path> roots = new ArrayList<>();
        var context = r.sandboxContext();

        if (context != null && context.mainWorktreePath() != null) {
            roots.add(context.mainWorktreePath());
        }

        StreamUtil.toStream(context)
                .flatMap(sc -> StreamUtil.toStream(sc.submoduleWorktreePaths()))
                .forEach(roots::add);

        return new WorktreeSandbox(roots);
    }

    public SandboxValidationResult validatePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return SandboxValidationResult.denied("path is required", null);
        }
        Path normalized = Path.of(rawPath).toAbsolutePath().normalize();
        String normalizedString = normalized.toString();
        if (allowedRoots.isEmpty()) {
            return SandboxValidationResult.denied("no worktree sandbox configured", normalizedString);
        }
        for (Path root : allowedRoots) {
            if (root == null) {
                continue;
            }
            Path normalizedRoot = root.toAbsolutePath().normalize();
            if (normalized.startsWith(normalizedRoot)) {
                return SandboxValidationResult.allowed(normalizedString);
            }
        }
        return SandboxValidationResult.denied("path outside of worktree sandbox", normalizedString);
    }

    public List<Path> allowedRoots() {
        return allowedRoots.stream().filter(Objects::nonNull).map(p -> p.toAbsolutePath().normalize()).toList();
    }
}
