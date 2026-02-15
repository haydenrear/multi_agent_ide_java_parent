package com.hayden.multiagentide.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.config.McpProperties;
import com.hayden.acp_cdc_ai.mcp.RequiredProtocolProperties;
import com.hayden.multiagentide.agent.decorator.prompt.AddIntellij;
import com.hayden.multiagentide.config.SerdesConfiguration;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentide.service.WorktreeService;
import com.hayden.multiagentide.tool.EmbabelToolObjectRegistry;
import com.hayden.multiagentide.tool.McpToolObjectRegistrar;
import com.hayden.utilitymodule.config.EnvConfigProps;
import com.hayden.utilitymodule.git.RepoUtil;
import com.hayden.utilitymodule.io.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;

@Slf4j
//@SpringBootTest(classes = {EnvConfigProps.class, McpServerProperties.class, McpProperties.class, RequiredProtocolProperties.class, SerdesConfiguration.class, McpToolObjectRegistrar.class,
//        EmbabelToolObjectRegistry.class})
//@EnableConfigurationProperties({EnvConfigProps.class, McpServerProperties.class, McpProperties.class, RequiredProtocolProperties.class})
class AddIntellijTest {

    @Autowired
    EnvConfigProps envConfigProps;

    @Autowired
    McpToolObjectRegistrar toolObjectRegistry;

//    @Test
    void testAddIntellij() throws GitAPIException, IOException, InterruptedException {


        AddIntellij a = new AddIntellij(null);

        var u = envConfigProps.getProjectDir();
        Path worktreePath = Paths.get("/tmp/worktree/test-again-ok");
        FileUtils.deleteFilesRecursive(worktreePath);
        worktreePath.getParent().toFile().mkdirs();
        GitWorktreeService.cloneRepository(envConfigProps.getProjectDir().getParent().getParent().toAbsolutePath().resolve(".git").toString(), worktreePath, "001-cli-mode-events");
        a.openInIntellijIfNeeded(worktreePath);

        var t = toolObjectRegistry.tool("intellij").get();

        var r = RetryTemplate.builder()
                .maxAttempts(5)
                .fixedBackoff(Duration.ofSeconds(3_000))
                .build()
                .execute(retryCtx -> {
                    return ((SyncMcpToolCallback) t.get(2).getObjects().getFirst()).call(new ObjectMapper().writeValueAsString(Map.of("projectPath", "/private/tmp/worktree/test-again-ok")));
                });

        log.info("");
    }

}
