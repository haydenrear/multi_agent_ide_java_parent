package com.hayden.acp_cdc_ai.sandbox;

import com.hayden.acp_cdc_ai.repository.RequestContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hayden.acp_cdc_ai.sandbox.SandboxArgUtils.*;

/**
 * Sandbox translation strategy for OpenAI Codex CLI (via codex-acp).
 * 
 * <p>Codex CLI options used:</p>
 * <ul>
 *   <li>{@code --cd, -C <path>} - Set the working directory for the agent</li>
 *   <li>{@code --sandbox, -s <policy>} - Set sandbox policy (read-only, workspace-write, danger-full-access)</li>
 *   <li>{@code --add-dir <path>} - Grant write permissions to additional directories</li>
 *   <li>{@code --full-auto} - Convenience preset for workspace-write sandbox with on-request approvals</li>
 * </ul>
 * 
 * <p>Environment variables:</p>
 * <ul>
 *   <li>{@code OPENAI_API_KEY} - OpenAI API key for authentication</li>
 *   <li>{@code CODEX_API_KEY} - Alternative Codex API key for authentication</li>
 * </ul>
 * 
 * <p>Working directory is set via session {@code cwd} parameter and {@code --cd} argument.</p>
 * 
 * @see <a href="https://developers.openai.com/codex/cli/reference/">Codex CLI Reference</a>
 * @see <a href="https://github.com/zed-industries/codex-acp">codex-acp</a>
 */
@Component
public class CodexSandboxStrategy implements SandboxTranslationStrategy {

    @Override
    public String providerKey() {
        return "codex-acp";
    }

    @Override
    public SandboxTranslation translate(RequestContext context, List<String> acpArgs) {
        if (context == null || context.mainWorktreePath() == null) {
            return SandboxTranslation.empty();
        }
        
        String mainPath = context.mainWorktreePath().toString();
        List<Path> submodulePaths = context.submoduleWorktreePaths();
        
        Map<String, String> env = new HashMap<>();
        List<String> args = new ArrayList<>();

        // Set the working directory for the agent if not already specified
        if (!hasFlag(acpArgs, "--cd", "-C")) {
            args.add("--cd");
            args.add(mainPath);
        }
        
        // Set sandbox policy to workspace-write if not already specified
        if (!hasFlag(acpArgs, "--sandbox", "-s")) {
            args.add("--sandbox");
            args.add("workspace-write");
        }
        
        // Add each submodule worktree as an additional writable directory
        if (submodulePaths != null && !submodulePaths.isEmpty()) {
            for (Path submodulePath : submodulePaths) {
                String submodulePathStr = submodulePath.toString();
                if (!hasFlagValuePair(acpArgs, submodulePathStr, "--add-dir")) {
                    args.add("--add-dir");
                    args.add(submodulePathStr);
                }
            }
        }
        
        return new SandboxTranslation(env, args, mainPath);
    }
}
