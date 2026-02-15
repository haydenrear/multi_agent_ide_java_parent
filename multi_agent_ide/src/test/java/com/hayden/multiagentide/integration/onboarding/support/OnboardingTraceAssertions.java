package com.hayden.multiagentide.integration.onboarding.support;

import com.hayden.commitdiffcontext.git.parser.support.episodic.model.OnboardingRunMetadata;
import org.assertj.core.api.Assertions;

public final class OnboardingTraceAssertions {

    private OnboardingTraceAssertions() {
    }

    public static void assertHasSegmentPlan(OnboardingRunMetadata run) {
        Assertions.assertThat(run.getSegmentPlan()).isNotNull();
        Assertions.assertThat(run.getSegmentPlan().ranges()).isNotEmpty();
    }

    public static void assertCompleted(OnboardingRunMetadata run) {
        Assertions.assertThat(run.getStatus())
                .isIn(OnboardingRunMetadata.RunStatus.COMPLETED, OnboardingRunMetadata.RunStatus.COMPLETED_WITH_WARNINGS);
    }

    public static void assertFailed(OnboardingRunMetadata run) {
        Assertions.assertThat(run.getStatus()).isEqualTo(OnboardingRunMetadata.RunStatus.FAILED);
    }
}
