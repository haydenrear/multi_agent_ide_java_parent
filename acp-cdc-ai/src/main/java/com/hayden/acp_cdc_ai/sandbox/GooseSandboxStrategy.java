package com.hayden.acp_cdc_ai.sandbox;

import com.hayden.acp_cdc_ai.repository.RequestContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GooseSandboxStrategy implements SandboxTranslationStrategy {

    @Override
    public String providerKey() {
        return "goose";
    }

    @Override
    public SandboxTranslation translate(RequestContext context) {
        if (context == null || context.mainWorktreePath() == null) {
            return SandboxTranslation.empty();
        }
        String mainPath = context.mainWorktreePath().toString();
        String submodules = joinPaths(context.submoduleWorktreePaths());
        Map<String, String> env = submodules.isBlank()
                ? Map.of("GOOSE_WORKTREE_ROOT", mainPath)
                : Map.of(
                        "GOOSE_WORKTREE_ROOT", mainPath,
                        "GOOSE_SUBMODULE_ROOTS", submodules
                );
        List<String> args = submodules.isBlank()
                ? List.of("--worktree-root", mainPath)
                : List.of("--worktree-root", mainPath, "--submodule-roots", submodules);
        return new SandboxTranslation(env, args);
    }

    private String joinPaths(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return "";
        }
        return paths.stream()
                .map(Path::toString)
                .collect(Collectors.joining(","));
    }
}
