package com.hayden.multiagentide.integration.onboarding;

import com.hayden.multiagentide.integration.onboarding.support.OnboardingIntegrationTestConfig;
import com.hayden.multiagentide.integration.onboarding.support.OnboardingTraceAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

class RepoOnboardingMemoryFailureIT extends OnboardingIntegrationTestConfig {

    @TempDir
    Path tempDir;

    @BeforeEach
    void resetMocks() {
        reset(episodicMemoryAgent, hindsightOnboardingClient);
    }

//    @Test
    void hardMemoryFailureStopsRunAndRecordsFailedState() throws Exception {
        when(episodicMemoryAgent.runAgent(any()))
                .thenThrow(new RuntimeException("memory backend down"));

        Path repo = repositoryFixtureFactory.createRepositoryWithSubmoduleFixture(tempDir);
        try (var ctx = onboardingContext(repo)) {
            var onboardingResult = runOnboarding(ctx);
            assertThat(onboardingResult.e().isPresent()).isTrue();
        }

        var run = latestRun();
        OnboardingTraceAssertions.assertFailed(run);
        assertThat(run.getSegmentExecutions()).isNotEmpty();
        assertThat(run.getSegmentExecutions().getFirst().errorMessage()).contains("memory backend down");
    }
}
