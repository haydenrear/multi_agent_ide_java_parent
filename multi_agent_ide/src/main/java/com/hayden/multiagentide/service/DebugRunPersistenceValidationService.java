package com.hayden.multiagentide.service;

import com.hayden.multiagentide.model.DebugRun;
import com.hayden.multiagentide.model.RunPersistenceCheck;
import com.hayden.multiagentide.repository.RunPersistenceCheckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DebugRunPersistenceValidationService {

    private final DebugRunQueryService debugRunQueryService;
    private final RunPersistenceCheckRepository persistenceCheckRepository;

    public PersistenceValidationSummary validate(String runId) {
        List<RunPersistenceCheck> checks = new ArrayList<>();
        var timeline = debugRunQueryService.collectTimeline(runId);

        boolean hasRun = debugRunQueryService.findRun(runId).isPresent();
        checks.add(check(RunPersistenceCheck.Category.RUN_METADATA, hasRun,
                hasRun ? "Run metadata found" : "Run metadata missing", runId));

        boolean hasTimeline = !timeline.isEmpty();
        checks.add(check(RunPersistenceCheck.Category.TIMELINE, hasTimeline,
                hasTimeline ? "Timeline events found" : "Timeline missing", runId));

        boolean hasStatusSummary = debugRunQueryService.findRun(runId)
                .map(DebugRun::status)
                .filter(status -> status != null)
                .isPresent();
        checks.add(check(RunPersistenceCheck.Category.STATUS_SUMMARY, hasStatusSummary,
                hasStatusSummary ? "Run status is available" : "Run status missing", runId));

        boolean hasArtifacts = timeline.stream()
                .anyMatch(event -> "ARTIFACT_EMITTED".equalsIgnoreCase(event.eventType()));
        checks.add(check(RunPersistenceCheck.Category.ARTIFACT_LINKS, hasArtifacts,
                hasArtifacts ? "Artifact events are present" : "No artifact events found", runId));

        persistenceCheckRepository.saveAll(runId, checks);
        boolean degraded = checks.stream().anyMatch(c -> c.status() == RunPersistenceCheck.Status.FAIL);
        return new PersistenceValidationSummary(runId, degraded, checks);
    }

    public PersistenceValidationSummary getLatest(String runId) {
        List<RunPersistenceCheck> checks = persistenceCheckRepository.findByRunId(runId);
        boolean degraded = checks.stream().anyMatch(c -> c.status() == RunPersistenceCheck.Status.FAIL);
        return new PersistenceValidationSummary(runId, degraded, checks);
    }

    private RunPersistenceCheck check(RunPersistenceCheck.Category category, boolean passed, String details, String runId) {
        return RunPersistenceCheck.builder()
                .checkId(UUID.randomUUID().toString())
                .runId(runId)
                .category(category)
                .status(passed ? RunPersistenceCheck.Status.PASS : RunPersistenceCheck.Status.FAIL)
                .details(details)
                .checkedAt(Instant.now())
                .build();
    }

    public record PersistenceValidationSummary(String runId, boolean degraded, List<RunPersistenceCheck> checks) {
    }
}
