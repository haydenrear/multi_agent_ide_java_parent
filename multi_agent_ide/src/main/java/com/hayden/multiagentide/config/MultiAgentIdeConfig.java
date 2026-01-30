package com.hayden.multiagentide.config;

import com.hayden.utilitymodule.acp.AcpChatModel;
import com.hayden.utilitymodule.acp.events.AgUiSerdes;
import com.hayden.utilitymodule.acp.config.McpProperties;
import com.hayden.multiagentidelib.model.acp.DefaultChatMemoryContext;
import com.hayden.multiagentidelib.service.UiStateService;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(
        basePackageClasses = {
                AgUiSerdes.class, McpProperties.class, AcpChatModel.class
        },
        basePackages = {
                "com.hayden.multiagentidelib"
        })
public class MultiAgentIdeConfig {
}
