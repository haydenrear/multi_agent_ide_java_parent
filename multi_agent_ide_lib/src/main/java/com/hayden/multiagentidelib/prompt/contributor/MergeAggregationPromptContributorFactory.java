package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.model.merge.AgentMergeStatus;
import com.hayden.multiagentidelib.model.merge.MergeAggregation;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.worktree.MainWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Adds merge/worktree verification context for dispatch and collector routing prompts.
 *
 * The contributor is selected by current request type, not AgentType:
 * - Dispatch prompts: current request is ResultsRequest
 * - Collector prompts: current request is Discovery/Planning/Ticket/Orchestrator collector request
 */
@Component
public class MergeAggregationPromptContributorFactory implements PromptContributorFactory {

    private static final String MERGE_AGGREGATION_METADATA_KEY = "mergeAggregation";
    private static final String MERGE_DESCRIPTOR_METADATA_KEY = "mergeDescriptor";
    private static final String FINAL_MERGE_DESCRIPTOR_METADATA_KEY = "finalMergeDescriptor";

    private static final String DISPATCH_TEMPLATE = """
            ## Dispatch Merge Validation Context

            Dispatch family: %s
            Dispatch request envelopes (one per line, or `none`):
            %s

            Dispatched agent requests (one per line, or `none`):
            %s

            Dispatched agent results (one per line, or `none`):
            %s

            Merge aggregation rounds (one per line, or `none`):
            %s

            Aggregated merge status entries
            Format: `<source> | <agentResultId> | <status> | <error> | <conflictFiles>`
            %s

            Worktrees for verification
            Main worktrees:
            %s
            Submodule worktrees:
            %s

            Merge commit trail (one per line, or `none`):
            %s

            Reported commits from dispatched agents (one per line, or `none`):
            %s

            Reported files modified from dispatched agents (one per line, or `none`):
            %s

            ---
            Required dispatch validation workflow:
            1. Use bash+git to verify merges before routing:
               - `bash -lc 'git -C <worktreePath> log --oneline --decorate --graph -n 40'`
               - `bash -lc 'git -C <worktreePath> show --name-status <mergeCommitHash>'`
               - `bash -lc 'git -C <worktreePath> diff <expected_base_or_target>...HEAD'`
            2. Validate that expected commits are present and that worktree diffs match the merge request intent.
            3. Do a preliminary quality pass (buildability + integration sanity + obvious regressions).
            4. If merges are incomplete/failed/conflicted, route to `MergerAgent` first.
            5. If code quality or correctness is questionable, route to `ReviewAgent` for deeper review.
            6. Include concrete worktree paths, commit hashes, and merge failure details in the next routing request.
            """;

    private static final String COLLECTOR_TEMPLATE = """
            ## Collector Merge Validation Context

            Collector type: %s
            Coverage scope: %s

            Dispatch request envelopes from history/current scope (one per line, or `none`):
            %s

            Dispatched agent requests from history/current scope (one per line, or `none`):
            %s

            Dispatched agent results from history/current scope (one per line, or `none`):
            %s

            Merge aggregation rounds from history/current scope (one per line, or `none`):
            %s

            Aggregated merge status entries
            Format: `<source> | <agentResultId> | <status> | <error> | <conflictFiles>`
            %s

            Worktrees for collector-level validation
            Main worktrees:
            %s
            Submodule worktrees:
            %s

            Merge commit trail (one per line, or `none`):
            %s

            Reported commits from dispatched agents (one per line, or `none`):
            %s

            Reported files modified from dispatched agents (one per line, or `none`):
            %s

            Final merge-to-source status: %s
            Final merge conflict files (one per line, or `none`):
            %s
            Final merge error (single line, or `none`): %s
            Final merge commit hash (single line, or `none`): %s

            ---
            Required collector validation workflow:
            1. Use bash+git on every listed worktree to verify that dispatched-agent commits were actually merged.
            2. Compare worktrees against intended merge requests and confirm merged commit history is coherent.
            3. Perform a preliminary acceptance review for completeness and integration quality.
            4. If any merge failed/conflicted/pending, route to `MergerAgent` first.
            5. If merged code does not meet acceptance criteria, route to `ReviewAgent`.
            6. In Merger/Review routing, include concrete merge failures, worktree paths, commit hashes, and impacted files.
            """;

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        AgentModels.AgentRequest currentRequest = context.currentRequest();
        switch (currentRequest) {
            case AgentModels.ResultsRequest resultsRequest -> {
                DispatchFamily family = resolveDispatchFamily(resultsRequest);
                if (family == null) {
                    return List.of();
                }

                MergePromptData data = collectMergePromptData(context, Set.of(family), true);
                if (!data.hasData()) {
                    return List.of();
                }
                return List.of(new DispatchMergeValidationPromptContributor(family, data));
            }
            case AgentModels.DiscoveryCollectorRequest ignored -> {
                return createCollectorContributor(context, CollectorKind.DISCOVERY, Set.of(DispatchFamily.DISCOVERY));
            }
            case AgentModels.PlanningCollectorRequest ignored -> {
                return createCollectorContributor(context, CollectorKind.PLANNING, Set.of(DispatchFamily.PLANNING));
            }
            case AgentModels.TicketCollectorRequest ignored -> {
                return createCollectorContributor(context, CollectorKind.TICKET, Set.of(DispatchFamily.TICKET));
            }
            case AgentModels.OrchestratorCollectorRequest ignored -> {
                return createCollectorContributor(context, CollectorKind.ORCHESTRATOR,
                        Set.of(DispatchFamily.DISCOVERY, DispatchFamily.PLANNING, DispatchFamily.TICKET));
            }
            default -> {
                return List.of();
            }
        }
    }

    private List<PromptContributor> createCollectorContributor(
            PromptContext context,
            CollectorKind collectorKind,
            Set<DispatchFamily> families
    ) {
        MergePromptData data = collectMergePromptData(context, families, false);
        MergeDescriptor finalMergeDescriptor = extractFinalMergeDescriptor(context);

        if (!data.hasData() && finalMergeDescriptor == null) {
            return List.of();
        }

        return List.of(new CollectorMergeValidationPromptContributor(collectorKind, data, finalMergeDescriptor));
    }

    private MergePromptData collectMergePromptData(
            PromptContext context,
            Set<DispatchFamily> families,
            boolean includeCurrentResultsRequest
    ) {
        MergePromptData data = MergePromptData.empty();

        if (includeCurrentResultsRequest && context.currentRequest() instanceof AgentModels.ResultsRequest resultsRequest) {
            MergeAggregation currentAggregation = resultsRequest.mergeAggregation();
            if (currentAggregation != null) {
                data.aggregationSources().add(new MergeAggregationSource(
                        "current:" + resultsRequest.getClass().getSimpleName(),
                        currentAggregation
                ));
            }
            data.dispatchedResults().addAll(extractResultSummariesFromResultsRequest(
                    "current:" + resultsRequest.getClass().getSimpleName(),
                    resultsRequest
            ));
            data.worktreeContexts().addAll(extractWorktreeContextsFromResultsRequest(resultsRequest));
        }

        MergeAggregation metadataAggregation = extractMergeAggregationFromMetadata(context);
        if (metadataAggregation != null) {
            data.aggregationSources().add(new MergeAggregationSource("metadata:" + MERGE_AGGREGATION_METADATA_KEY, metadataAggregation));
        }

        BlackboardHistory history = context.blackboardHistory();
        if (history == null) {
            return data;
        }

        List<BlackboardHistory.Entry> entries = history.copyOfEntries();
        int sequence = 1;
        for (BlackboardHistory.Entry entry : entries) {
            if (!(entry instanceof BlackboardHistory.DefaultEntry defaultEntry)) {
                continue;
            }

            Object input = defaultEntry.input();
            if (input == null) {
                continue;
            }

            String source = String.format("history:%02d:%s", sequence++, input.getClass().getSimpleName());

            for (DispatchFamily family : families) {
                collectFamilyData(source, input, family, data);
            }
        }

        return data;
    }

    private void collectFamilyData(String source, Object input, DispatchFamily family, MergePromptData data) {
        switch (family) {
            case DISCOVERY -> collectDiscoveryData(source, input, data);
            case PLANNING -> collectPlanningData(source, input, data);
            case TICKET -> collectTicketData(source, input, data);
        }
    }

    private void collectDiscoveryData(String source, Object input, MergePromptData data) {
        switch (input) {
            case AgentModels.DiscoveryAgentRequests requests -> {
                data.dispatchEnvelopes().add(new DispatchEnvelope(
                        source,
                        safeContextId(requests.contextId()),
                        requests.worktreeContext(),
                        requests.requests() != null ? requests.requests().size() : 0,
                        summarizeDiscoveryRequestList(requests.requests())
                ));
                data.worktreeContexts().addIfPresent(requests.worktreeContext());
            }
            case AgentModels.DiscoveryAgentRequest request -> {
                data.dispatchedRequests().add(new DispatchedRequestSummary(
                        source,
                        safeContextId(request.contextId()),
                        request.subdomainFocus(),
                        request.worktreeContext()
                ));
                data.worktreeContexts().addIfPresent(request.worktreeContext());
            }
            case AgentModels.DiscoveryAgentResult result -> data.dispatchedResults().add(toResultSummary(source, result));
            case AgentModels.DiscoveryAgentResults resultsRequest -> {
                addAggregationSource(source, resultsRequest, data);
                data.dispatchedResults().addAll(extractResultSummariesFromResultsRequest(source, resultsRequest));
                data.worktreeContexts().addAll(extractWorktreeContextsFromResultsRequest(resultsRequest));
            }
            default -> {
            }
        }
    }

    private void collectPlanningData(String source, Object input, MergePromptData data) {
        switch (input) {
            case AgentModels.PlanningAgentRequests requests -> {
                data.dispatchEnvelopes().add(new DispatchEnvelope(
                        source,
                        safeContextId(requests.contextId()),
                        requests.worktreeContext(),
                        requests.requests() != null ? requests.requests().size() : 0,
                        summarizePlanningRequestList(requests.requests())
                ));
                data.worktreeContexts().addIfPresent(requests.worktreeContext());
            }
            case AgentModels.PlanningAgentRequest request -> {
                data.dispatchedRequests().add(new DispatchedRequestSummary(
                        source,
                        safeContextId(request.contextId()),
                        request.goal(),
                        request.worktreeContext()
                ));
                data.worktreeContexts().addIfPresent(request.worktreeContext());
            }
            case AgentModels.PlanningAgentResult result -> data.dispatchedResults().add(toResultSummary(source, result));
            case AgentModels.PlanningAgentResults resultsRequest -> {
                addAggregationSource(source, resultsRequest, data);
                data.dispatchedResults().addAll(extractResultSummariesFromResultsRequest(source, resultsRequest));
                data.worktreeContexts().addAll(extractWorktreeContextsFromResultsRequest(resultsRequest));
            }
            default -> {
            }
        }
    }

    private void collectTicketData(String source, Object input, MergePromptData data) {
        switch (input) {
            case AgentModels.TicketAgentRequests requests -> {
                data.dispatchEnvelopes().add(new DispatchEnvelope(
                        source,
                        safeContextId(requests.contextId()),
                        requests.worktreeContext(),
                        requests.requests() != null ? requests.requests().size() : 0,
                        summarizeTicketRequestList(requests.requests())
                ));
                data.worktreeContexts().addIfPresent(requests.worktreeContext());
            }
            case AgentModels.TicketAgentRequest request -> {
                String requestSummary = nonBlankOrFallback(request.ticketDetails(), "no-ticket-details")
                        + " | "
                        + nonBlankOrFallback(request.ticketDetailsFilePath(), "no-ticket-file");
                data.dispatchedRequests().add(new DispatchedRequestSummary(
                        source,
                        safeContextId(request.contextId()),
                        requestSummary,
                        request.worktreeContext()
                ));
                data.worktreeContexts().addIfPresent(request.worktreeContext());
            }
            case AgentModels.TicketAgentResult result -> data.dispatchedResults().add(toResultSummary(source, result));
            case AgentModels.TicketAgentResults resultsRequest -> {
                addAggregationSource(source, resultsRequest, data);
                data.dispatchedResults().addAll(extractResultSummariesFromResultsRequest(source, resultsRequest));
                data.worktreeContexts().addAll(extractWorktreeContextsFromResultsRequest(resultsRequest));
            }
            default -> {
            }
        }
    }

    private void addAggregationSource(String source, AgentModels.ResultsRequest resultsRequest, MergePromptData data) {
        if (resultsRequest.mergeAggregation() != null) {
            data.aggregationSources().add(new MergeAggregationSource(source, resultsRequest.mergeAggregation()));
        }
    }

    private List<DispatchedResultSummary> extractResultSummariesFromResultsRequest(String source, AgentModels.ResultsRequest resultsRequest) {
        if (resultsRequest == null || resultsRequest.childResults() == null) {
            return List.of();
        }
        List<DispatchedResultSummary> summaries = new ArrayList<>();
        for (AgentModels.AgentResult result : resultsRequest.childResults()) {
            summaries.add(toResultSummary(source, result));
        }
        return summaries;
    }

    private List<WorktreeSandboxContext> extractWorktreeContextsFromResultsRequest(AgentModels.ResultsRequest resultsRequest) {
        List<WorktreeSandboxContext> contexts = new ArrayList<>();
        if (resultsRequest == null) {
            return contexts;
        }
        if (resultsRequest.worktreeContext() != null) {
            contexts.add(resultsRequest.worktreeContext());
        }
        if (resultsRequest.mergeAggregation() == null) {
            return contexts;
        }

        List<AgentMergeStatus> merged = resultsRequest.mergeAggregation().merged() != null
                ? resultsRequest.mergeAggregation().merged()
                : List.of();
        for (AgentMergeStatus status : merged) {
            if (status != null && status.worktreeContext() != null) {
                contexts.add(status.worktreeContext());
            }
        }

        List<AgentMergeStatus> pending = resultsRequest.mergeAggregation().pending() != null
                ? resultsRequest.mergeAggregation().pending()
                : List.of();
        for (AgentMergeStatus status : pending) {
            if (status != null && status.worktreeContext() != null) {
                contexts.add(status.worktreeContext());
            }
        }

        AgentMergeStatus conflicted = resultsRequest.mergeAggregation().conflicted();
        if (conflicted != null && conflicted.worktreeContext() != null) {
            contexts.add(conflicted.worktreeContext());
        }

        return contexts;
    }

    private DispatchedResultSummary toResultSummary(String source, AgentModels.AgentResult result) {
        if (result == null) {
            return new DispatchedResultSummary(source, "unknown", "unknown", null, List.of(), List.of(), List.of(), "none");
        }

        return switch (result) {
            case AgentModels.DiscoveryAgentResult discovery -> new DispatchedResultSummary(
                    source,
                    safeContextId(discovery.contextId()),
                    "DiscoveryAgentResult",
                    discovery.mergeDescriptor(),
                    List.of(),
                    List.of(),
                    List.of(),
                    summarizeOneLine(discovery.output())
            );
            case AgentModels.PlanningAgentResult planning -> new DispatchedResultSummary(
                    source,
                    safeContextId(planning.contextId()),
                    "PlanningAgentResult",
                    planning.mergeDescriptor(),
                    List.of(),
                    List.of(),
                    List.of(),
                    summarizeOneLine(planning.output())
            );
            case AgentModels.TicketAgentResult ticket -> new DispatchedResultSummary(
                    source,
                    safeContextId(ticket.contextId()),
                    "TicketAgentResult",
                    ticket.mergeDescriptor(),
                    nonBlankValues(ticket.filesModified()),
                    nonBlankValues(ticket.commits()),
                    nonBlankValues(ticket.testResults()),
                    summarizeOneLine(ticket.output())
            );
            default -> new DispatchedResultSummary(
                    source,
                    safeContextId(result.contextId()),
                    result.getClass().getSimpleName(),
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    summarizeOneLine(result.prettyPrint())
            );
        };
    }

    private MergeAggregation extractMergeAggregationFromMetadata(PromptContext context) {
        Object metadataValue = context.metadata().get(MERGE_AGGREGATION_METADATA_KEY);
        if (metadataValue instanceof MergeAggregation aggregation) {
            return aggregation;
        }
        return null;
    }

    private MergeDescriptor extractFinalMergeDescriptor(PromptContext context) {
        AgentModels.AgentRequest currentRequest = context.currentRequest();
        if (currentRequest instanceof AgentModels.OrchestratorCollectorRequest orchestratorCollectorRequest
                && orchestratorCollectorRequest.mergeDescriptor() != null) {
            return orchestratorCollectorRequest.mergeDescriptor();
        }

        Object descriptorFromMetadata = context.metadata().get(MERGE_DESCRIPTOR_METADATA_KEY);
        if (descriptorFromMetadata instanceof MergeDescriptor descriptor) {
            return descriptor;
        }

        Object finalDescriptorFromMetadata = context.metadata().get(FINAL_MERGE_DESCRIPTOR_METADATA_KEY);
        if (finalDescriptorFromMetadata instanceof MergeDescriptor descriptor) {
            return descriptor;
        }

        BlackboardHistory history = context.blackboardHistory();
        if (history == null) {
            return null;
        }

        List<BlackboardHistory.Entry> entries = history.copyOfEntries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            BlackboardHistory.Entry entry = entries.get(i);
            if (!(entry instanceof BlackboardHistory.DefaultEntry defaultEntry)) {
                continue;
            }
            if (!(defaultEntry.input() instanceof AgentModels.OrchestratorCollectorRequest request)) {
                continue;
            }
            if (request.mergeDescriptor() != null) {
                return request.mergeDescriptor();
            }
        }

        return null;
    }

    private DispatchFamily resolveDispatchFamily(AgentModels.ResultsRequest request) {
        return switch (request) {
            case AgentModels.DiscoveryAgentResults ignored -> DispatchFamily.DISCOVERY;
            case AgentModels.PlanningAgentResults ignored -> DispatchFamily.PLANNING;
            case AgentModels.TicketAgentResults ignored -> DispatchFamily.TICKET;
        };
    }

    private List<String> summarizeDiscoveryRequestList(List<AgentModels.DiscoveryAgentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (AgentModels.DiscoveryAgentRequest request : requests) {
            if (request == null) {
                continue;
            }
            lines.add(nonBlankOrFallback(request.subdomainFocus(), "no-subdomain-focus"));
        }
        return lines;
    }

    private List<String> summarizePlanningRequestList(List<AgentModels.PlanningAgentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (AgentModels.PlanningAgentRequest request : requests) {
            if (request == null) {
                continue;
            }
            lines.add(nonBlankOrFallback(request.goal(), "no-goal"));
        }
        return lines;
    }

    private List<String> summarizeTicketRequestList(List<AgentModels.TicketAgentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (AgentModels.TicketAgentRequest request : requests) {
            if (request == null) {
                continue;
            }
            String summary = nonBlankOrFallback(request.ticketDetails(), "no-ticket-details")
                    + " | "
                    + nonBlankOrFallback(request.ticketDetailsFilePath(), "no-ticket-file");
            lines.add(summary);
        }
        return lines;
    }

    private List<AgentMergeStatusSnapshot> flattenAggregationStatuses(List<MergeAggregationSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }

        List<AgentMergeStatusSnapshot> snapshots = new ArrayList<>();
        for (MergeAggregationSource source : sources) {
            if (source == null || source.aggregation() == null) {
                continue;
            }

            if (source.aggregation().merged() != null) {
                for (AgentMergeStatus status : source.aggregation().merged()) {
                    if (status != null) {
                        snapshots.add(new AgentMergeStatusSnapshot(source.source(), "merged", status));
                    }
                }
            }

            if (source.aggregation().pending() != null) {
                for (AgentMergeStatus status : source.aggregation().pending()) {
                    if (status != null) {
                        snapshots.add(new AgentMergeStatusSnapshot(source.source(), "pending", status));
                    }
                }
            }

            if (source.aggregation().conflicted() != null) {
                snapshots.add(new AgentMergeStatusSnapshot(source.source(), "conflicted", source.aggregation().conflicted()));
            }
        }

        return snapshots;
    }

    private List<String> toAggregationRoundLines(List<MergeAggregationSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        for (MergeAggregationSource source : sources) {
            if (source == null || source.aggregation() == null) {
                continue;
            }
            lines.add(String.format(
                    "%s | merged=%d | pending=%d | conflicted=%s",
                    nonBlankOrFallback(source.source(), "unknown-source"),
                    source.aggregation().merged() != null ? source.aggregation().merged().size() : 0,
                    source.aggregation().pending() != null ? source.aggregation().pending().size() : 0,
                    source.aggregation().conflicted() != null ? "yes" : "no"
            ));
        }
        return lines;
    }

    private List<String> toMergeStatusLines(List<AgentMergeStatusSnapshot> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        for (AgentMergeStatusSnapshot snapshot : statuses) {
            if (snapshot == null || snapshot.status() == null) {
                continue;
            }
            MergeDescriptor descriptor = snapshot.status().mergeDescriptor();
            String error = descriptor != null ? nonBlankOrFallback(descriptor.errorMessage(), "none") : "none";
            String conflictFiles = descriptor != null
                    ? commaOrNone(nonBlankValues(descriptor.conflictFiles()))
                    : "none";
            lines.add(String.format(
                    "%s | %s | %s | %s | %s",
                    nonBlankOrFallback(snapshot.source(), "unknown-source"),
                    nonBlankOrFallback(snapshot.status().agentResultId(), "unknown-agent-result"),
                    snapshot.statusLabel(),
                    error,
                    conflictFiles
            ));
        }
        return lines;
    }

    private List<String> toDispatchEnvelopeLines(List<DispatchEnvelope> envelopes) {
        if (envelopes == null || envelopes.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (DispatchEnvelope envelope : envelopes) {
            if (envelope == null) {
                continue;
            }
            lines.add(String.format(
                    "%s | %s | requestCount=%d | items=%s",
                    nonBlankOrFallback(envelope.source(), "unknown-source"),
                    nonBlankOrFallback(envelope.contextId(), "unknown-context"),
                    envelope.requestCount(),
                    commaOrNone(envelope.requestSummaries())
            ));
        }
        return lines;
    }

    private List<String> toDispatchedRequestLines(List<DispatchedRequestSummary> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (DispatchedRequestSummary request : requests) {
            if (request == null) {
                continue;
            }
            lines.add(String.format(
                    "%s | %s | %s",
                    nonBlankOrFallback(request.source(), "unknown-source"),
                    nonBlankOrFallback(request.contextId(), "unknown-context"),
                    nonBlankOrFallback(request.requestSummary(), "none")
            ));
        }
        return lines;
    }

    private List<String> toDispatchedResultLines(List<DispatchedResultSummary> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (DispatchedResultSummary result : results) {
            if (result == null) {
                continue;
            }
            MergeDescriptor descriptor = result.mergeDescriptor();
            String mergeStatus = descriptor == null ? "merge=unknown" : "merge=" + (descriptor.successful() ? "success" : "failed");
            String mergeError = descriptor == null ? "mergeError=none" : "mergeError=" + nonBlankOrFallback(descriptor.errorMessage(), "none");
            lines.add(String.format(
                    "%s | %s | %s | %s | %s | output=%s",
                    nonBlankOrFallback(result.source(), "unknown-source"),
                    nonBlankOrFallback(result.contextId(), "unknown-context"),
                    nonBlankOrFallback(result.resultType(), "unknown-result"),
                    mergeStatus,
                    mergeError,
                    nonBlankOrFallback(result.outputSummary(), "none")
            ));
        }
        return lines;
    }

    private List<String> toMainWorktreeLines(List<WorktreeSandboxContext> contexts, List<AgentMergeStatusSnapshot> statusSnapshots) {
        LinkedHashSet<String> lines = new LinkedHashSet<>();

        if (contexts != null) {
            for (WorktreeSandboxContext context : contexts) {
                if (context == null || context.mainWorktree() == null) {
                    continue;
                }
                MainWorktreeContext main = context.mainWorktree();
                lines.add(String.format(
                        "%s | %s | %s | %s | %s | %s",
                        nonBlankOrFallback(main.worktreeId(), "unknown-worktree-id"),
                        nonBlankOrFallback(pathValue(main.worktreePath()), "unknown-path"),
                        nonBlankOrFallback(main.baseBranch(), "unknown-base-branch"),
                        nonBlankOrFallback(main.derivedBranch(), "unknown-derived-branch"),
                        nonBlankOrFallback(main.lastCommitHash(), "unknown-commit"),
                        nonBlankOrFallback(main.associatedNodeId(), "unknown-node")
                ));
            }
        }

        if (statusSnapshots != null) {
            for (AgentMergeStatusSnapshot snapshot : statusSnapshots) {
                if (snapshot == null || snapshot.status() == null) {
                    continue;
                }
                WorktreeSandboxContext context = snapshot.status().worktreeContext();
                if (context == null || context.mainWorktree() == null) {
                    continue;
                }
                MainWorktreeContext main = context.mainWorktree();
                lines.add(String.format(
                        "%s | %s | %s | %s | %s | %s | source=%s | agentResult=%s",
                        nonBlankOrFallback(main.worktreeId(), "unknown-worktree-id"),
                        nonBlankOrFallback(pathValue(main.worktreePath()), "unknown-path"),
                        nonBlankOrFallback(main.baseBranch(), "unknown-base-branch"),
                        nonBlankOrFallback(main.derivedBranch(), "unknown-derived-branch"),
                        nonBlankOrFallback(main.lastCommitHash(), "unknown-commit"),
                        nonBlankOrFallback(main.associatedNodeId(), "unknown-node"),
                        nonBlankOrFallback(snapshot.source(), "unknown-source"),
                        nonBlankOrFallback(snapshot.status().agentResultId(), "unknown-agent-result")
                ));
            }
        }

        return new ArrayList<>(lines);
    }

    private List<String> toSubmoduleWorktreeLines(List<WorktreeSandboxContext> contexts, List<AgentMergeStatusSnapshot> statusSnapshots) {
        LinkedHashSet<String> lines = new LinkedHashSet<>();

        if (contexts != null) {
            for (WorktreeSandboxContext context : contexts) {
                addSubmoduleLines(lines, context, null, null);
            }
        }

        if (statusSnapshots != null) {
            for (AgentMergeStatusSnapshot snapshot : statusSnapshots) {
                if (snapshot == null || snapshot.status() == null) {
                    continue;
                }
                addSubmoduleLines(lines, snapshot.status().worktreeContext(), snapshot.source(), snapshot.status().agentResultId());
            }
        }

        return new ArrayList<>(lines);
    }

    private void addSubmoduleLines(
            Set<String> lines,
            WorktreeSandboxContext context,
            String source,
            String agentResultId
    ) {
        if (context == null || context.submoduleWorktrees() == null) {
            return;
        }
        for (SubmoduleWorktreeContext submodule : context.submoduleWorktrees()) {
            if (submodule == null) {
                continue;
            }
            String suffix = source == null ? "" : String.format(
                    " | source=%s | agentResult=%s",
                    nonBlankOrFallback(source, "unknown-source"),
                    nonBlankOrFallback(agentResultId, "unknown-agent-result")
            );
            lines.add(String.format(
                    "%s | %s | %s | %s | %s | %s%s",
                    nonBlankOrFallback(submodule.submoduleName(), "unknown-submodule"),
                    nonBlankOrFallback(submodule.worktreeId(), "unknown-worktree-id"),
                    nonBlankOrFallback(pathValue(submodule.worktreePath()), "unknown-path"),
                    nonBlankOrFallback(submodule.baseBranch(), "unknown-base-branch"),
                    nonBlankOrFallback(submodule.lastCommitHash(), "unknown-commit"),
                    nonBlankOrFallback(submodule.mainWorktreeId(), "unknown-main-worktree-id"),
                    suffix
            ));
        }
    }

    private List<String> toMergeCommitLines(List<DispatchedResultSummary> results, List<AgentMergeStatusSnapshot> statuses, MergeDescriptor finalMergeDescriptor) {
        LinkedHashSet<String> lines = new LinkedHashSet<>();

        if (results != null) {
            for (DispatchedResultSummary result : results) {
                if (result == null || result.mergeDescriptor() == null) {
                    continue;
                }
                lines.add(toMergeCommitLine(result.source(), result.contextId(), result.mergeDescriptor()));
            }
        }

        if (statuses != null) {
            for (AgentMergeStatusSnapshot status : statuses) {
                if (status == null || status.status() == null || status.status().mergeDescriptor() == null) {
                    continue;
                }
                lines.add(toMergeCommitLine(status.source(), status.status().agentResultId(), status.status().mergeDescriptor()));
            }
        }

        if (finalMergeDescriptor != null) {
            lines.add(toMergeCommitLine("final-merge", "orchestrator-collector", finalMergeDescriptor));
        }

        return new ArrayList<>(lines);
    }

    private String toMergeCommitLine(String source, String entityId, MergeDescriptor descriptor) {
        String mergeCommitHash = descriptor.mainWorktreeMergeResult() != null
                ? nonBlankOrFallback(descriptor.mainWorktreeMergeResult().mergeCommitHash(), "none")
                : "none";
        String childWorktreeId = descriptor.mainWorktreeMergeResult() != null
                ? nonBlankOrFallback(descriptor.mainWorktreeMergeResult().childWorktreeId(), "unknown-child")
                : "unknown-child";
        String parentWorktreeId = descriptor.mainWorktreeMergeResult() != null
                ? nonBlankOrFallback(descriptor.mainWorktreeMergeResult().parentWorktreeId(), "unknown-parent")
                : "unknown-parent";
        String error = nonBlankOrFallback(descriptor.errorMessage(), "none");
        return String.format(
                "%s | %s | mergeCommit=%s | child=%s | parent=%s | successful=%s | error=%s",
                nonBlankOrFallback(source, "unknown-source"),
                nonBlankOrFallback(entityId, "unknown-entity"),
                mergeCommitHash,
                childWorktreeId,
                parentWorktreeId,
                Boolean.toString(descriptor.successful()),
                error
        );
    }

    private List<String> toReportedCommitLines(List<DispatchedResultSummary> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (DispatchedResultSummary result : results) {
            if (result == null || result.reportedCommits() == null || result.reportedCommits().isEmpty()) {
                continue;
            }
            for (String commit : result.reportedCommits()) {
                if (commit == null || commit.isBlank()) {
                    continue;
                }
                lines.add(String.format(
                        "%s | %s | %s",
                        nonBlankOrFallback(result.source(), "unknown-source"),
                        nonBlankOrFallback(result.contextId(), "unknown-context"),
                        commit
                ));
            }
        }
        return lines;
    }

    private List<String> toReportedFilesLines(List<DispatchedResultSummary> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (DispatchedResultSummary result : results) {
            if (result == null || result.filesModified() == null || result.filesModified().isEmpty()) {
                continue;
            }
            for (String file : result.filesModified()) {
                if (file == null || file.isBlank()) {
                    continue;
                }
                lines.add(String.format(
                        "%s | %s | %s",
                        nonBlankOrFallback(result.source(), "unknown-source"),
                        nonBlankOrFallback(result.contextId(), "unknown-context"),
                        file
                ));
            }
        }
        return lines;
    }

    private String safeContextId(ArtifactKey contextId) {
        if (contextId == null || contextId.value() == null || contextId.value().isBlank()) {
            return "unknown-context";
        }
        return contextId.value();
    }

    private String pathValue(Path path) {
        return path == null ? null : path.toString();
    }

    private String summarizeOneLine(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        String oneLine = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() <= 180) {
            return oneLine;
        }
        return oneLine.substring(0, 180) + "...";
    }

    private List<String> nonBlankValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> filtered = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            filtered.add(value.trim());
        }
        return filtered;
    }

    private String linesOrNone(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "none";
        }
        return String.join("\n", lines);
    }

    private String commaOrNone(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "none";
        }
        return String.join(", ", values);
    }

    private String nonBlankOrFallback(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    public record DispatchMergeValidationPromptContributor(
            DispatchFamily family,
            MergePromptData data
    ) implements PromptContributor {

        @Override
        public String name() {
            return "DispatchMergeValidationPromptContributor";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return data != null && data.hasData();
        }

        @Override
        public String contribute(PromptContext context) {
            if (data == null || !data.hasData()) {
                return "";
            }

            MergeAggregationPromptContributorFactory helper = new MergeAggregationPromptContributorFactory();
            List<AgentMergeStatusSnapshot> statuses = helper.flattenAggregationStatuses(data.aggregationSources());

            return String.format(
                    DISPATCH_TEMPLATE,
                    family.label(),
                    helper.linesOrNone(helper.toDispatchEnvelopeLines(data.dispatchEnvelopes())),
                    helper.linesOrNone(helper.toDispatchedRequestLines(data.dispatchedRequests())),
                    helper.linesOrNone(helper.toDispatchedResultLines(data.dispatchedResults())),
                    helper.linesOrNone(helper.toAggregationRoundLines(data.aggregationSources())),
                    helper.linesOrNone(helper.toMergeStatusLines(statuses)),
                    helper.linesOrNone(helper.toMainWorktreeLines(data.worktreeContexts().values(), statuses)),
                    helper.linesOrNone(helper.toSubmoduleWorktreeLines(data.worktreeContexts().values(), statuses)),
                    helper.linesOrNone(helper.toMergeCommitLines(data.dispatchedResults(), statuses, null)),
                    helper.linesOrNone(helper.toReportedCommitLines(data.dispatchedResults())),
                    helper.linesOrNone(helper.toReportedFilesLines(data.dispatchedResults()))
            );
        }

        @Override
        public String template() {
            return DISPATCH_TEMPLATE;
        }

        @Override
        public int priority() {
            return 500;
        }
    }

    public record CollectorMergeValidationPromptContributor(
            CollectorKind collectorKind,
            MergePromptData data,
            MergeDescriptor finalMergeDescriptor
    ) implements PromptContributor {

        @Override
        public String name() {
            return "CollectorMergeValidationPromptContributor";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return (data != null && data.hasData()) || finalMergeDescriptor != null;
        }

        @Override
        public String contribute(PromptContext context) {
            if (!include(context)) {
                return "";
            }

            MergeAggregationPromptContributorFactory helper = new MergeAggregationPromptContributorFactory();
            List<AgentMergeStatusSnapshot> statuses = helper.flattenAggregationStatuses(data.aggregationSources());

            String finalStatus = finalMergeDescriptor == null ? "not-available" : (finalMergeDescriptor.successful() ? "successful" : "failed");
            String finalConflictFiles = finalMergeDescriptor == null
                    ? "none"
                    : helper.linesOrNone(helper.nonBlankValues(finalMergeDescriptor.conflictFiles()));
            String finalError = finalMergeDescriptor == null
                    ? "none"
                    : helper.nonBlankOrFallback(finalMergeDescriptor.errorMessage(), "none");
            String finalMergeCommit = finalMergeDescriptor == null || finalMergeDescriptor.mainWorktreeMergeResult() == null
                    ? "none"
                    : helper.nonBlankOrFallback(finalMergeDescriptor.mainWorktreeMergeResult().mergeCommitHash(), "none");

            return String.format(
                    COLLECTOR_TEMPLATE,
                    collectorKind.label(),
                    collectorKind.scopeDescription(),
                    helper.linesOrNone(helper.toDispatchEnvelopeLines(data.dispatchEnvelopes())),
                    helper.linesOrNone(helper.toDispatchedRequestLines(data.dispatchedRequests())),
                    helper.linesOrNone(helper.toDispatchedResultLines(data.dispatchedResults())),
                    helper.linesOrNone(helper.toAggregationRoundLines(data.aggregationSources())),
                    helper.linesOrNone(helper.toMergeStatusLines(statuses)),
                    helper.linesOrNone(helper.toMainWorktreeLines(data.worktreeContexts().values(), statuses)),
                    helper.linesOrNone(helper.toSubmoduleWorktreeLines(data.worktreeContexts().values(), statuses)),
                    helper.linesOrNone(helper.toMergeCommitLines(data.dispatchedResults(), statuses, finalMergeDescriptor)),
                    helper.linesOrNone(helper.toReportedCommitLines(data.dispatchedResults())),
                    helper.linesOrNone(helper.toReportedFilesLines(data.dispatchedResults())),
                    finalStatus,
                    finalConflictFiles,
                    finalError,
                    finalMergeCommit
            );
        }

        @Override
        public String template() {
            return COLLECTOR_TEMPLATE;
        }

        @Override
        public int priority() {
            return 510;
        }
    }

    private enum DispatchFamily {
        DISCOVERY("discovery-dispatch"),
        PLANNING("planning-dispatch"),
        TICKET("ticket-dispatch");

        private final String label;

        DispatchFamily(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }

    private enum CollectorKind {
        DISCOVERY("discovery-collector", "Only discovery dispatch history + current round"),
        PLANNING("planning-collector", "Only planning dispatch history + current round"),
        TICKET("ticket-collector", "Only ticket dispatch history + current round"),
        ORCHESTRATOR("orchestrator-collector", "Discovery + planning + ticket dispatch history across previous rounds");

        private final String label;
        private final String scopeDescription;

        CollectorKind(String label, String scopeDescription) {
            this.label = label;
            this.scopeDescription = scopeDescription;
        }

        private String label() {
            return label;
        }

        private String scopeDescription() {
            return scopeDescription;
        }
    }

    private record MergeAggregationSource(
            String source,
            MergeAggregation aggregation
    ) {
    }

    private record AgentMergeStatusSnapshot(
            String source,
            String statusLabel,
            AgentMergeStatus status
    ) {
    }

    private record DispatchEnvelope(
            String source,
            String contextId,
            WorktreeSandboxContext worktreeContext,
            int requestCount,
            List<String> requestSummaries
    ) {
    }

    private record DispatchedRequestSummary(
            String source,
            String contextId,
            String requestSummary,
            WorktreeSandboxContext worktreeContext
    ) {
    }

    private record DispatchedResultSummary(
            String source,
            String contextId,
            String resultType,
            MergeDescriptor mergeDescriptor,
            List<String> filesModified,
            List<String> reportedCommits,
            List<String> testResults,
            String outputSummary
    ) {
    }

    private record MergePromptData(
            List<MergeAggregationSource> aggregationSources,
            List<DispatchEnvelope> dispatchEnvelopes,
            List<DispatchedRequestSummary> dispatchedRequests,
            List<DispatchedResultSummary> dispatchedResults,
            WorktreeContextBag worktreeContexts
    ) {
        static MergePromptData empty() {
            return new MergePromptData(
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new WorktreeContextBag()
            );
        }

        boolean hasData() {
            return !aggregationSources.isEmpty()
                    || !dispatchEnvelopes.isEmpty()
                    || !dispatchedRequests.isEmpty()
                    || !dispatchedResults.isEmpty()
                    || !worktreeContexts.values().isEmpty();
        }
    }

    private static final class WorktreeContextBag {
        private final LinkedHashSet<WorktreeSandboxContext> contexts = new LinkedHashSet<>();

        private void addIfPresent(WorktreeSandboxContext context) {
            if (context != null) {
                contexts.add(context);
            }
        }

        private void addAll(List<WorktreeSandboxContext> contextList) {
            if (contextList == null || contextList.isEmpty()) {
                return;
            }
            for (WorktreeSandboxContext context : contextList) {
                addIfPresent(context);
            }
        }

        private List<WorktreeSandboxContext> values() {
            return new ArrayList<>(contexts);
        }
    }
}
