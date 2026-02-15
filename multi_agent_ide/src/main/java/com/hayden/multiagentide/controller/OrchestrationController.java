package com.hayden.multiagentide.controller;

import com.hayden.commitdiffcontext.git.parser.support.episodic.model.OnboardingRunMetadata;
import com.hayden.commitdiffcontext.git.parser.support.episodic.service.OnboardingOrchestrationService;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for orchestration operations.
 * Provides endpoints for initializing goals, executing nodes, and checking status.
 */
@Slf4j
@RestController
@RequestMapping("/api/orchestrator")
@RequiredArgsConstructor
public class OrchestrationController {

    private final GoalExecutor goalExecutor;
    private final ObjectProvider<OnboardingOrchestrationService> onboardingOrchestrationServiceProvider;



//    private static final ExecutorService exec =

    @PostMapping("/start")
    public StartGoalResponse startGoal(@RequestBody StartGoalRequest request) {
        return startGoalAsync(request);
    }

    public StartGoalResponse startGoalAsync(StartGoalRequest request) {
        if (request == null || request.goal() == null || request.goal().isBlank()) {
            throw new IllegalArgumentException("goal is required");
        }
        if (request.repositoryUrl() == null || request.repositoryUrl().isBlank()) {
            throw new IllegalArgumentException("repositoryUrl is required");
        }

        ArtifactKey root = ArtifactKey.createRoot();
        goalExecutor.executeGoal(request, root);
        return new StartGoalResponse(root.value());
    }

    @GetMapping("/onboarding/runs")
    public java.util.List<OnboardingRunMetadata> onboardingRuns() {
        return onboardingService().findRuns();
    }

    @GetMapping("/onboarding/runs/{runId}")
    public OnboardingRunMetadata onboardingRun(@PathVariable String runId) {
        return onboardingService().findRun(runId)
                .orElseThrow(() -> new IllegalArgumentException("Onboarding run not found: " + runId));
    }

    private OnboardingOrchestrationService onboardingService() {
        var service = onboardingOrchestrationServiceProvider.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Onboarding orchestration is not configured in this application context.");
        }
        return service;
    }

    public record StartGoalRequest(
            String goal,
            String repositoryUrl,
            String baseBranch,
            String title
    ) {}

    public record StartGoalResponse(String nodeId) {}
}
