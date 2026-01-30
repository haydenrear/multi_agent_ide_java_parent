package com.hayden.multiagentide.config;

import com.hayden.multiagentidelib.prompt.EpisodicMemoryPromptContributor;
import com.hayden.multiagentidelib.prompt.PromptAssembly;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorRegistry;
import com.hayden.multiagentidelib.prompt.WeAreHerePromptContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
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
    public PromptContributorRegistry promptContributorRegistry(List<PromptContributor> contributors) {
        return new PromptContributorRegistry(contributors);
    }

    @Bean
    public PromptAssembly promptAssembly(PromptContributorRegistry registry) {
        return new PromptAssembly(registry);
    }

}
