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

    @PostMapping("/get")
    public DebugRun get(@RequestBody RunIdRequest request) {
        return queryService.findRun(request.runId())
                .map(responseMapper::mapRun)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Run not found: " + request.runId()));
    }

    @PostMapping("/timeline")
    public DebugRunQueryService.RunTimelinePage timeline(
            @RequestBody RunTimelineRequest request
    ) {
        int limit = request.limit() <= 0 ? 200 : request.limit();
        return responseMapper.mapTimelinePage(queryService.timeline(request.runId(), limit, request.cursor()));
    }

    @PostMapping("/actions")
    public DebugRunQueryService.ActionResponse action(
            @RequestBody RunActionRequestWrapper request
    ) {
        return responseMapper.mapAction(queryService.applyAction(
                request.runId(),
                new DebugRunQueryService.RunActionRequest(request.actionType(), request.nodeId(), request.message())
        ));
    }

    @PostMapping("/persistence-validation")
    public DebugRunPersistenceValidationService.PersistenceValidationSummary validatePersistence(
            @RequestBody RunIdRequest request) {
        return responseMapper.mapValidation(persistenceValidationService.validate(request.runId()));
    }

    @PostMapping("/persistence-validation/get")
    public DebugRunPersistenceValidationService.PersistenceValidationSummary getPersistenceValidation(
            @RequestBody RunIdRequest request) {
        return responseMapper.mapValidation(persistenceValidationService.getLatest(request.runId()));
    }

    public record StartRunRequest(String goal, String repositoryUrl, String baseBranch, String title) {
    }

    public record StartRunResponse(String runId, String nodeId, String status) {
    }

    public record RunIdRequest(String runId) {
    }

    public record RunTimelineRequest(String runId, int limit, String cursor) {
    }

    public record RunActionRequestWrapper(String runId, String actionType, String nodeId, String message) {
    }
}
