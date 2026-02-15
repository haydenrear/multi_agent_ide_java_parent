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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepoOnboardingHappyPathIT extends OnboardingIntegrationTestConfig {

    @TempDir
    Path tempDir;

    @BeforeEach
    void resetMocks() {
        reset(episodicMemoryAgent, hindsightOnboardingClient);
    }

    @Test
    void onboardingRunsEndToEndWithRealRepositoryFixture() throws Exception {
        when(episodicMemoryAgent.runAgent(any()))
                .thenReturn(new EpisodicMemoryAgent.AgentRunResult(2, "next segment focus", Map.of()));

        Path repo = repositoryFixtureFactory.createRepositoryWithSubmoduleFixture(tempDir);
        try (var ctx = onboardingContext(repo)) {
            var onboardingResult = runOnboarding(ctx);
            assertThat(onboardingResult.e().isPresent()).isFalse();
        }

        var run = latestRun();
        OnboardingTraceAssertions.assertHasSegmentPlan(run);
        OnboardingTraceAssertions.assertCompleted(run);
        assertThat(run.getSegmentExecutions()).isNotEmpty();
        verify(episodicMemoryAgent).runAgent(any());
    }
}
