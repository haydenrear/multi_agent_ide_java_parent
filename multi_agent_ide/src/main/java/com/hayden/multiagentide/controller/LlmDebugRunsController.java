package com.hayden.multiagentide.controller;

import com.hayden.multiagentide.controller.debug.RunDebugResponseMapper;
import com.hayden.multiagentide.model.DebugRun;
import com.hayden.multiagentide.service.DebugRunPersistenceValidationService;
import com.hayden.multiagentide.service.DebugRunQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/llm-debug/runs")
@RequiredArgsConstructor
public class LlmDebugRunsController {

    private final DebugRunQueryService queryService;
    private final DebugRunPersistenceValidationService persistenceValidationService;
    private final RunDebugResponseMapper responseMapper;

    @PostMapping("/start")
    public StartRunResponse start(@RequestBody StartRunRequest request) {
        DebugRun run = queryService.startRun(new OrchestrationController.StartGoalRequest(
                request.goal(),
                request.repositoryUrl(),
                request.baseBranch(),
                request.title()
        ));
        return new StartRunResponse(run.runId(), run.runId(), run.status().name());
    }

    @GetMapping
    public DebugRunQueryService.DebugRunPage list(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor
    ) {
        return responseMapper.mapRunPage(queryService.listRuns(limit, cursor));
    }

    @GetMapping("/{runId}")
    public DebugRun get(@PathVariable String runId) {
        return queryService.findRun(runId)
                .map(responseMapper::mapRun)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Run not found: " + runId));
    }

    @GetMapping("/{runId}/timeline")
    public DebugRunQueryService.RunTimelinePage timeline(
            @PathVariable String runId,
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(required = false) String cursor
    ) {
        return responseMapper.mapTimelinePage(queryService.timeline(runId, limit, cursor));
    }

    @PostMapping("/{runId}/actions")
    public DebugRunQueryService.ActionResponse action(
            @PathVariable String runId,
            @RequestBody RunActionRequest request
    ) {
        return responseMapper.mapAction(queryService.applyAction(
                runId,
                new DebugRunQueryService.RunActionRequest(request.actionType(), request.nodeId(), request.message())
        ));
    }

    @PostMapping("/{runId}/persistence-validation")
    public DebugRunPersistenceValidationService.PersistenceValidationSummary validatePersistence(@PathVariable String runId) {
        return responseMapper.mapValidation(persistenceValidationService.validate(runId));
    }

    @GetMapping("/{runId}/persistence-validation")
    public DebugRunPersistenceValidationService.PersistenceValidationSummary getPersistenceValidation(@PathVariable String runId) {
        return responseMapper.mapValidation(persistenceValidationService.getLatest(runId));
    }

    public record StartRunRequest(String goal, String repositoryUrl, String baseBranch, String title) {
    }

    public record StartRunResponse(String runId, String nodeId, String status) {
    }

    public record RunActionRequest(String actionType, String nodeId, String message) {
    }
}
