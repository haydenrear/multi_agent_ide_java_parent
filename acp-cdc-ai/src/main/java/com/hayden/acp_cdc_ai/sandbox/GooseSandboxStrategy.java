package com.hayden.acp_cdc_ai.sandbox;

import com.hayden.acp_cdc_ai.repository.RequestContext;
import com.hayden.utilitymodule.config.EnvConfigProps;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hayden.acp_cdc_ai.sandbox.SandboxArgUtils.*;

/**
 * Sandbox translation strategy for Goose (via goose acp).
 *
 * <p>Goose handles sandboxing differently from Claude Code and Codex:</p>
 * <ul>
 *   <li>Working directory is set via session {@code cwd} parameter (handled by ACP protocol)</li>
 *   <li>Working directory can also be set via {@code -w} or {@code --working_dir} CLI arg</li>
 *   <li>No CLI arguments for sandbox configuration - uses environment variables instead</li>
 *   <li>Permission modes controlled via {@code GOOSE_MODE} environment variable</li>
 * </ul>
 *
 * <p>Environment variables:</p>
 * <ul>
 *   <li>{@code GOOSE_MODE} - Permission mode: auto, approve, smart_approve, chat</li>
 *   <li>{@code GOOSE_PROVIDER} - Override the provider</li>
 *   <li>{@code GOOSE_MODEL} - Override the model</li>
 *   <li>{@code GOOSE_ALLOWLIST} - URL to YAML file specifying allowed MCP commands</li>
 * </ul>
 *
 * <p>Note: Goose does not support CLI arguments like {@code --sandbox} or {@code --add-dir}.
 * Directory sandboxing is handled via {@code .gooseignore} files or Filesystem MCP extension.</p>
 *
 * @see <a href="https://block.github.io/goose/docs/guides/acp-clients/">Goose ACP Clients</a>
 * @see <a href="https://block.github.io/goose/docs/guides/goose-cli-commands">Goose CLI Commands</a>
 */
@Component
public class GooseSandboxStrategy implements SandboxTranslationStrategy {

    @Override
    public String providerKey() {
        return "goose";
    }

    @Override
    public SandboxTranslation translate(RequestContext context, List<String> acpArgs) {
        if (context == null || context.mainWorktreePath() == null) {
            return SandboxTranslation.empty();
        }

        String mainPath = context.mainWorktreePath().toString();

        Map<String, String> env = new HashMap<>();
        List<String> args = new ArrayList<>();

        // Set permission mode to auto for automated workflows
        // Options: auto (no approval), approve (ask for all), smart_approve (ask for risky), chat (no tools)
        env.put("GOOSE_MODE", "smart_approve");

        // Set working directory if not already specified and path exists
        Path mainPathObj = Paths.get(mainPath);

        return new SandboxTranslation(env, args, mainPath);
    }
}
