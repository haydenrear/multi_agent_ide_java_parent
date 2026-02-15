package com.hayden.multiagentide.integration.onboarding;

import com.hayden.commitdiffcontext.git.parser.support.episodic.EpisodicMemoryAgent;
import com.hayden.multiagentide.integration.onboarding.support.OnboardingIntegrationTestConfig;
import com.hayden.multiagentide.integration.onboarding.support.OnboardingTraceAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepoOnboardingAgentSwapIT extends OnboardingIntegrationTestConfig {

    @TempDir
    Path tempDir;

    @BeforeEach
    void resetMocks() {
        reset(episodicMemoryAgent, hindsightOnboardingClient);
    }

    @Test
    void onboardingUsesMockitoBeanEpisodicAgentOverride() throws Exception {
        when(episodicMemoryAgent.runAgent(any()))
                .thenReturn(new EpisodicMemoryAgent.AgentRunResult(1, "swap-handoff", Map.of("impl", "mock")));

        Path repo = repositoryFixtureFactory.createRepositoryWithSubmoduleFixture(tempDir);
        try (var ctx = onboardingContext(repo)) {
            var onboardingResult = runOnboarding(ctx);
            assertThat(onboardingResult.e().isPresent()).isFalse();
        }

        var run = latestRun();
        OnboardingTraceAssertions.assertCompleted(run);
        verify(episodicMemoryAgent).runAgent(any());
        verify(hindsightOnboardingClient, never()).runAgent(any());
    }
}
