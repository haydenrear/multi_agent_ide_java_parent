package com.hayden.multiagentide.controller;

import com.hayden.multiagentide.controller.debug.RunDebugResponseMapper;
import com.hayden.multiagentide.controller.model.RunIdRequest;
import com.hayden.multiagentide.model.DebugRun;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.hayden.multiagentide.service.DebugRunPersistenceValidationService;
import com.hayden.multiagentide.service.DebugRunQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/runs")
@RequiredArgsConstructor
@Tag(name = "Debug Runs", description = "Manage LLM debug run lifecycle and persistence validation")
public class LlmDebugRunsController {

    private final DebugRunQueryService queryService;
    private final DebugRunPersistenceValidationService persistenceValidationService;
    private final RunDebugResponseMapper responseMapper;

    @PostMapping("/start")
    @Operation(summary = "Start a new debug run")
    public StartRunResponse start(@RequestBody @Valid StartRunRequest request) {
        DebugRun run = queryService.startRun(new OrchestrationController.StartGoalRequest(
                request.goal(),
                request.repositoryUrl(),
                request.baseBranch(),
                request.title(),
                request.tags()
        ));
        return new StartRunResponse(run.runId(), run.runId(), run.status().name());
    }

    @GetMapping
    @Operation(summary = "List debug runs with cursor-based pagination")
    public DebugRunQueryService.DebugRunPage list(
            @Parameter(description = "Maximum number of runs to return (default 50)") @RequestParam(defaultValue = "50") int limit,
            @Parameter(description = "Pagination cursor from a previous response (optional)") @RequestParam(required = false) String cursor
    ) {
        return responseMapper.mapRunPage(queryService.listRuns(limit, cursor));
    }

    @PostMapping("/get")
    @Operation(summary = "Get a debug run by ID")
    public DebugRun get(@RequestBody @Valid RunIdRequest request) {
        return queryService.findRun(request.runId())
                .map(responseMapper::mapRun)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Run not found: " + request.runId()));
    }

    @PostMapping("/timeline")
    @Operation(summary = "Get timeline events for a debug run")
    public DebugRunQueryService.RunTimelinePage timeline(
            @RequestBody @Valid RunTimelineRequest request
    ) {
        int limit = request.limit() <= 0 ? 200 : request.limit();
        return responseMapper.mapTimelinePage(queryService.timeline(request.runId(), limit, request.cursor()));
    }

    @PostMapping("/actions")
    @Operation(summary = "Apply an action to a debug run node")
    public DebugRunQueryService.ActionResponse action(
            @RequestBody @Valid RunActionRequestWrapper request
    ) {
        return responseMapper.mapAction(queryService.applyAction(
                request.runId(),
                new DebugRunQueryService.RunActionRequest(request.actionType(), request.nodeId(), request.message())
        ));
    }

    @PostMapping("/persistence-validation")
    @Operation(summary = "Trigger persistence validation for a debug run")
    public DebugRunPersistenceValidationService.PersistenceValidationSummary validatePersistence(
            @RequestBody @Valid RunIdRequest request) {
        return responseMapper.mapValidation(persistenceValidationService.validate(request.runId()));
    }

    @PostMapping("/persistence-validation/get")
    @Operation(summary = "Get latest persistence validation result for a debug run")
    public DebugRunPersistenceValidationService.PersistenceValidationSummary getPersistenceValidation(
            @RequestBody @Valid RunIdRequest request) {
        return responseMapper.mapValidation(persistenceValidationService.getLatest(request.runId()));
    }

    public record StartRunRequest(@NotBlank String goal, @NotBlank String repositoryUrl, String baseBranch, String title, List<String> tags) {
        public StartRunRequest(String goal, String repositoryUrl, String baseBranch, String title) {
            this(goal, repositoryUrl, baseBranch, title, List.of());
        }

        public StartRunRequest {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    public record StartRunResponse(
            @Schema(description = "The run identifier") String runId,
            @Schema(description = "The root node identifier") String nodeId,
            @Schema(description = "Initial run status") String status
    ) {
    }

    public record RunTimelineRequest(
            @Schema(description = "The debug run identifier") String runId,
            @Schema(description = "Maximum number of events to return (0 = 200)") int limit,
            @Schema(description = "Pagination cursor") String cursor
    ) {
    }

    public record RunActionRequestWrapper(
            @Schema(description = "The debug run identifier") String runId,
            @Schema(description = "Action type to apply") String actionType,
            @Schema(description = "Target node identifier") String nodeId,
            @Schema(description = "Optional message for the action") String message
    ) {
    }
}
