package com.hayden.multiagentide.perf.onboarding;

import com.hayden.multiagentide.integration.onboarding.support.OnboardingTraceAssertions;
import com.hayden.multiagentide.perf.onboarding.support.OnboardingPerfTestConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class RepoOnboardingRealWorldPerfIT extends OnboardingPerfTestConfig {

    @TempDir
    Path tempDir;

    @Test
    void runsRealWorldOnboardingPipelineWithinConfiguredBudget() throws Exception {
        PerfSettings settings = perfSettings();
        Instant started = Instant.now();

        try (var cloned = openSourceRepositoryFactory.cloneRepository(
                settings.repoUrl(),
                settings.branch(),
                settings.cloneDepth(),
                tempDir
        )) {
            try (var ctx = onboardingContext(
                    cloned.rootPath(),
                    cloned.branch(),
                    settings.maxCommitDepth(),
                    settings.maxCommitDiffs()
            )) {
                var onboardingResult = runOnboarding(ctx);
                assertThat(onboardingResult.e().isPresent()).isFalse();

                var run = latestRun();
                OnboardingTraceAssertions.assertCompleted(run);
                assertThat(run.getSegmentExecutions()).isNotEmpty();

                var embeddingSummary = validateEmbeddingPersistence(ctx.operationArgs().repositoryArgs())
                        .r()
                        .get();
                assertThat(embeddingSummary.commitDiffCount()).isGreaterThan(0);
                assertThat(embeddingSummary.embeddedCommitDiffCount())
                        .isGreaterThanOrEqualTo(settings.minEmbeddedCommitDiffs());
                assertThat(embeddingSummary.commitDiffsWithOnboardingMetadata())
                        .isEqualTo(embeddingSummary.commitDiffCount());

                Duration elapsed = Duration.between(started, Instant.now());
                log.info("Perf onboarding run completed: repo={} branch={} commitDiffs={} embedded={} runtime={}s",
                        settings.repoUrl(),
                        cloned.branch(),
                        embeddingSummary.commitDiffCount(),
                        embeddingSummary.embeddedCommitDiffCount(),
                        elapsed.toSeconds());
                assertThat(elapsed)
                        .isLessThanOrEqualTo(Duration.ofSeconds(settings.maxRuntimeSeconds()));
            }
        }
    }
}
