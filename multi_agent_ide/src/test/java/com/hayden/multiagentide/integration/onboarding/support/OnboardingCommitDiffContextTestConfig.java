package com.hayden.multiagentide.integration.onboarding.support;

import com.hayden.commitdiffcontext.cdc_config.*;
import com.hayden.commitdiffcontext.convert.CommitDiffContextMapper;
import com.hayden.commitdiffmodel.config.CommitDiffContextProperties;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@TestConfiguration
@ComponentScan(basePackages = {
        "com.hayden.commitdiffcontext.repo_ref",
        "com.hayden.commitdiffcontext.git",
        "com.hayden.commitdiffcontext.context",
        "com.hayden.commitdiffcontext.message"
})
@Import({CommitDiffContextConfig.class, CommitDiffContextDisableLoggingConfig.class,
        EpisodicMemoryConfigProps.class, EpisodicModelConfigProps.class, ModelServerRequestConfigProps.class,
        OnboardingPipelineConfigProps.class, RequestSizeProvider.class, CommitDiffContextProperties.class,
        CommitDiffContextMapper.class})
@EntityScan(basePackages = {
        "com.hayden.multiagentide",
        "com.hayden.commitdiffcontext.git",
        "com.hayden.commitdiffcontext.repo_ref"
})
@EnableJpaRepositories(basePackages = {
        "com.hayden.multiagentide",
        "com.hayden.commitdiffcontext.git",
        "com.hayden.commitdiffcontext.repo_ref"
})
public class OnboardingCommitDiffContextTestConfig {
}
