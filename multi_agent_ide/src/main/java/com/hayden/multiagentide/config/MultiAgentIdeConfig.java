package com.hayden.multiagentide.config;

import com.hayden.acp_cdc_ai.acp.AcpChatModel;
import com.hayden.acp_cdc_ai.acp.events.AgUiSerdes;
import com.hayden.acp_cdc_ai.acp.config.McpProperties;
import com.hayden.commitdiffcontext.git.parser.support.episodic.EpisodicMemoryAgent;
import com.hayden.multiagentide.agent.episodic.HindsightOnboardingClient;
import com.hayden.multiagentide.agent.episodic.MultiAgentIdeEpisodicMemoryAgent;
import com.hayden.utilitymodule.config.EnvConfigProps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(
        basePackageClasses = {
                AgUiSerdes.class, McpProperties.class, AcpChatModel.class, EnvConfigProps.class
        },
        basePackages = {
                "com.hayden.multiagentidelib"
        })
public class MultiAgentIdeConfig {

    @Bean
    public EpisodicMemoryAgent episodicMemoryAgent(HindsightOnboardingClient hindsightOnboardingClient) {
        return new MultiAgentIdeEpisodicMemoryAgent(hindsightOnboardingClient);
    }
}
