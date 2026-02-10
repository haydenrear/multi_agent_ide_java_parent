package com.hayden.multiagentide.config;

import com.hayden.multiagentide.cli.ArtifactKeyFormatter;
import com.hayden.multiagentide.cli.CliEventFormatter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("cli")
public class CliModeConfig {

    @Bean
    public ArtifactKeyFormatter artifactKeyFormatter() {
        return new ArtifactKeyFormatter();
    }

    @Bean
    public CliEventFormatter cliEventFormatter(ArtifactKeyFormatter artifactKeyFormatter) {
        return new CliEventFormatter(artifactKeyFormatter);
    }

}
