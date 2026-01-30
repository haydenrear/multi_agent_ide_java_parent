package com.hayden.multiagentide.config;

import com.hayden.acp_cdc_ai.acp.AcpChatModel;
import com.hayden.acp_cdc_ai.acp.events.AgUiSerdes;
import com.hayden.acp_cdc_ai.acp.config.McpProperties;
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
