package com.hayden.multiagentide.prompt;

import com.hayden.multiagentide.agent.decorator.prompt.AddIntellij;
import com.hayden.multiagentide.tool.McpToolObjectRegistrar;
import com.hayden.utilitymodule.config.EnvConfigProps;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Paths;

@Slf4j
@SpringBootTest(classes = EnvConfigProps.class)
@EnableConfigurationProperties(EnvConfigProps.class)
class AddIntellijTest {

    @Autowired
    EnvConfigProps envConfigProps;

    @Test
    void testAddIntellij() {
        AddIntellij a = new AddIntellij(null);

        var u = envConfigProps.getProjectDir();
        a.openInIntellijIfNeeded(Paths.get("/Users/hayde/.multi-agent-ide/worktrees/m-e"));
        log.info("");
    }

}
