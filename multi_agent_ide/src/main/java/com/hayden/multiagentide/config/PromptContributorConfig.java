package com.hayden.multiagentide.config;

import com.hayden.multiagentidelib.prompt.contributor.EpisodicMemoryPromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorRegistry;
import com.hayden.multiagentidelib.prompt.contributor.WeAreHerePromptContributor;
import com.hayden.multiagentide.prompt.TicketOrchestratorWorktreePromptContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PromptContributorConfig {

    @Bean
    public EpisodicMemoryPromptContributor episodicMemoryPromptContributor() {
        return new EpisodicMemoryPromptContributor();
    }

    @Bean
    public WeAreHerePromptContributor weAreHerePromptContributor() {
        return new WeAreHerePromptContributor();
    }

    @Bean
    public TicketOrchestratorWorktreePromptContributor ticketOrchestratorWorktreePromptContributor() {
        return new TicketOrchestratorWorktreePromptContributor();
    }

    @Bean
    public PromptContributorRegistry promptContributorRegistry(List<PromptContributor> contributors) {
        return new PromptContributorRegistry(contributors);
    }

}
