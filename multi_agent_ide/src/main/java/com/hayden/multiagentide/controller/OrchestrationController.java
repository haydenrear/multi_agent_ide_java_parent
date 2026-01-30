package com.hayden.multiagentide.controller;

import com.hayden.multiagentide.agent.AgentLifecycleHandler;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for orchestration operations.
 * Provides endpoints for initializing goals, executing nodes, and checking status.
 */
@RestController
@RequestMapping("/api/orchestrator")
@RequiredArgsConstructor
public class OrchestrationController {

    private final AgentLifecycleHandler agentLifecycleHandler;

    @PostMapping("/start")
    public StartGoalResponse startGoal(@RequestBody StartGoalRequest request) {
        if (request == null || request.goal() == null || request.goal().isBlank()) {
            throw new IllegalArgumentException("goal is required");
        }
        if (request.repositoryUrl() == null || request.repositoryUrl().isBlank()) {
            throw new IllegalArgumentException("repositoryUrl is required");
        }

        String nodeId = (request.nodeId() == null || request.nodeId().isBlank())
                ? UUID.randomUUID().toString()
                : request.nodeId();
        String baseBranch = (request.baseBranch() == null || request.baseBranch().isBlank())
                ? "main"
                : request.baseBranch();

        agentLifecycleHandler.initializeOrchestrator(
                request.repositoryUrl(),
                baseBranch,
                request.goal(),
                request.title(),
                nodeId
        );

        return new StartGoalResponse(nodeId);
    }

    public record StartGoalRequest(
            String goal,
            String repositoryUrl,
            String baseBranch,
            String title,
            String nodeId
    ) {}

    public record StartGoalResponse(String nodeId) {}
}
