package com.hayden.multiagentidelib.model.worktree;

import com.hayden.acp_cdc_ai.repository.RequestContext;
import com.hayden.utilitymodule.stream.StreamUtil;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class WorktreeSandbox {

    private final List<Path> allowedRoots;

    private WorktreeSandbox(List<Path> allowedRoots) {
        for (var a : allowedRoots) {
            if (Files.isSymbolicLink(a.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("Cannot accept symbolic links as roots: %s.".formatted(a));
            }
        }

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

    /**
     * Dummy path validator for local use only. Really not very important, catching quick easy.
     * NOT worked on very much or hard, or analyzed really at all.
     * @param rawPath
     * @return
     */
    public SandboxValidationResult validatePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return SandboxValidationResult.denied("path is required", null);
        }

        Path normalized = Paths.get(rawPath).toAbsolutePath().normalize();
        String normalizedString = normalized.toString();

        if (allowedRoots.isEmpty()) {
            return SandboxValidationResult.denied("no worktree sandbox configured", normalizedString);
        }

        // 1) Lexical root containment check
        boolean inRoot = false;
        for (Path root : allowedRoots) {
            if (root == null)
                continue;
            Path normalizedRoot = root.toAbsolutePath().normalize();
            if (normalized.startsWith(normalizedRoot)) {
                inRoot = true;
                break;
            }
        }

        if (!inRoot) {
            return SandboxValidationResult.denied("path outside of worktree sandbox", normalizedString);
        }

        // 2) Deny symlinks in any EXISTING component of the path chain
        //    (allows non-existent leaf for create flows)
        Path existing = normalized;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }

        for (Path cur = existing; cur != null; cur = cur.getParent()) {
            if (Files.isSymbolicLink(cur)) {
                return SandboxValidationResult.denied("Do not allow symlinks!", normalizedString);
            }
        }

        return SandboxValidationResult.allowed(normalizedString);
    }


    public List<Path> allowedRoots() {
        return allowedRoots.stream().filter(Objects::nonNull).map(p -> p.toAbsolutePath().normalize()).toList();
    }
}
