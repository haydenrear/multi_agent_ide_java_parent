package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Factory that provides merge status context to dispatch and collector routing LLMs.
 */
@Component
public class MergeAggregationPromptContributorFactory implements PromptContributorFactory {

    private static final Set<AgentType> DISPATCH_AGENT_TYPES = Set.of(
            AgentType.TICKET_AGENT_DISPATCH,
            AgentType.PLANNING_AGENT_DISPATCH,
            AgentType.DISCOVERY_AGENT_DISPATCH
    );

    private static final Set<AgentType> COLLECTOR_AGENT_TYPES = Set.of(
            AgentType.DISCOVERY_COLLECTOR,
            AgentType.PLANNING_COLLECTOR,
            AgentType.TICKET_COLLECTOR,
            AgentType.ORCHESTRATOR_COLLECTOR
    );

    private static final String MERGE_AGGREGATION_METADATA_KEY = "mergeAggregation";
    private static final String MERGE_DESCRIPTOR_METADATA_KEY = "mergeDescriptor";
    private static final String FINAL_MERGE_DESCRIPTOR_METADATA_KEY = "finalMergeDescriptor";

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        AgentType agentType = context.agentType();
        if (!isSupportedAgentType(agentType)) {
            return List.of();
        }

        List<MergeAggregationSource> aggregationSources = extractMergeAggregationSources(context, agentType);
        MergeDescriptor finalMergeDescriptor = extractFinalMergeDescriptor(context);
        if (aggregationSources.isEmpty() && finalMergeDescriptor == null) {
            return List.of();
        }

        return List.of(new MergeAggregationPromptContributor(agentType, aggregationSources, finalMergeDescriptor));
    }

    private static boolean isDispatchAgentType(AgentType agentType) {
        return agentType != null && DISPATCH_AGENT_TYPES.contains(agentType);
    }

    private static boolean isCollectorAgentType(AgentType agentType) {
        return agentType != null && COLLECTOR_AGENT_TYPES.contains(agentType);
    }

    private static boolean isSupportedAgentType(AgentType agentType) {
        return isDispatchAgentType(agentType) || isCollectorAgentType(agentType);
    }

    private List<MergeAggregationSource> extractMergeAggregationSources(PromptContext context, AgentType agentType) {
        List<MergeAggregationSource> sources = new ArrayList<>();

        MergeAggregation currentRequestAggregation = extractMergeAggregationFromCurrentRequest(context);
        if (currentRequestAggregation != null) {
            String source = "current:" + context.currentRequest().getClass().getSimpleName();
            sources.add(new MergeAggregationSource(source, currentRequestAggregation));
        }

        MergeAggregation metadataAggregation = extractMergeAggregationFromMetadata(context);
        if (metadataAggregation != null) {
            sources.add(new MergeAggregationSource("metadata:" + MERGE_AGGREGATION_METADATA_KEY, metadataAggregation));
        }

        if (isCollectorAgentType(agentType)) {
            sources.addAll(extractHistoryMergeAggregations(context.blackboardHistory()));
        }

        return sources;
    }

    private MergeAggregation extractMergeAggregationFromCurrentRequest(PromptContext context) {
        AgentModels.AgentRequest request = context.currentRequest();
        if (request instanceof AgentModels.ResultsRequest resultsRequest) {
            return resultsRequest.mergeAggregation();
        }
        return null;
    }

    private MergeAggregation extractMergeAggregationFromMetadata(PromptContext context) {
        Object fromMetadata = context.metadata().get(MERGE_AGGREGATION_METADATA_KEY);
        if (fromMetadata instanceof MergeAggregation agg) {
            return agg;
        }
        return null;
    }

    private List<MergeAggregationSource> extractHistoryMergeAggregations(BlackboardHistory blackboardHistory) {
        if (blackboardHistory == null) {
            return List.of();
        }
        List<BlackboardHistory.Entry> entries = blackboardHistory.copyOfEntries();
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        List<MergeAggregationSource> sources = new ArrayList<>();
        int sequence = 1;
        for (BlackboardHistory.Entry entry : entries) {
            if (!(entry instanceof BlackboardHistory.DefaultEntry defaultEntry)) {
                continue;
            }
            if (!(defaultEntry.input() instanceof AgentModels.ResultsRequest resultsRequest)) {
                continue;
            }
            MergeAggregation aggregation = resultsRequest.mergeAggregation();
            if (aggregation == null) {
                continue;
            }
            String requestType = resultsRequest.getClass().getSimpleName();
            String contextId = safeContextId(resultsRequest.contextId());
            String source = String.format("history:%02d:%s:%s", sequence++, requestType, contextId);
            sources.add(new MergeAggregationSource(source, aggregation));
        }
        return sources;
    }

    private MergeDescriptor extractFinalMergeDescriptor(PromptContext context) {
        AgentModels.AgentRequest request = context.currentRequest();
        if (request instanceof AgentModels.OrchestratorCollectorRequest orchestratorCollectorRequest
                && orchestratorCollectorRequest.mergeDescriptor() != null) {
            return orchestratorCollectorRequest.mergeDescriptor();
        }

        Object metadataMergeDescriptor = context.metadata().get(MERGE_DESCRIPTOR_METADATA_KEY);
        if (metadataMergeDescriptor instanceof MergeDescriptor descriptor) {
            return descriptor;
        }

        Object finalMetadataMergeDescriptor = context.metadata().get(FINAL_MERGE_DESCRIPTOR_METADATA_KEY);
        if (finalMetadataMergeDescriptor instanceof MergeDescriptor descriptor) {
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
            if (!(defaultEntry.input() instanceof AgentModels.OrchestratorCollectorRequest collectorRequest)) {
                continue;
            }
            if (collectorRequest.mergeDescriptor() != null) {
                return collectorRequest.mergeDescriptor();
            }
        }
        return null;
    }

    private static String safeContextId(Object contextId) {
        if (contextId == null) {
            return "unknown";
        }
        if (contextId instanceof com.hayden.acp_cdc_ai.acp.events.ArtifactKey artifactKey) {
            String value = artifactKey.value();
            return value == null || value.isBlank() ? "unknown" : value;
        }
        String value = contextId.toString();
        return value == null || value.isBlank() ? "unknown" : value;
    }

    /**
     * Prompt contributor that provides merge aggregation and worktree validation guidance.
     */
    public record MergeAggregationPromptContributor(
            AgentType agentType,
            List<MergeAggregationSource> aggregationSources,
            MergeDescriptor finalMergeDescriptor
    ) implements PromptContributor {

        private static final String TEMPLATE = """
                ## Merge Validation Context

                Role: %s
                Dispatch merge rounds available: %s
                Merge aggregation sources (one per line, or `none`):
                %s

                Overall merge status: %s

                ### Aggregated merge outcomes
                Merged entries: %s
                Format: `<source> | <agentResultId> | <status>`
                %s

                Conflicted entries: %s
                Format: `<source> | <agentResultId> | <status> | <error> | <conflictFiles>`
                %s

                Pending entries: %s
                Format: `<source> | <agentResultId> | <status>`
                %s

                ### Worktree inventory for git validation
                Main worktrees
                Format: `<source> | <agentResultId> | <worktreeId> | <path> | <baseBranch> | <derivedBranch> | <lastCommitHash>`
                %s

                Submodule worktrees
                Format: `<source> | <agentResultId> | <submoduleName> | <worktreeId> | <path> | <baseBranch> | <lastCommitHash>`
                %s

                Merge commit trail
                Format: `<source> | <agentResultId> | <mergeCommitHash> | <childWorktreeId> | <parentWorktreeId> | <successful>`
                %s

                ### Final merge-to-source status
                Status: %s
                Conflict files (one per line, or `none`):
                %s
                Error message (single line, or `none`):
                %s
                Merge commit hash (single line, or `none`):
                %s

                ---
                ### Required validation process

                1. Use bash + git to validate merge outcomes before routing:
                   - `bash -lc 'git -C <worktreePath> log --oneline --decorate --graph -n 30'`
                   - `bash -lc 'git -C <worktreePath> diff <expected_base_or_merge_target>...HEAD'`
                   - `bash -lc 'git -C <worktreePath> show --name-status <mergeCommitHash>'`
                2. Compare each worktree against the intended merge request and confirm expected commits were merged.
                3. Run a preliminary review for buildability, integration risk, and semantic correctness.
                4. If conflicts/pending merges/final merge failures exist, route to `MergerAgent` first. Merger requirement: %s.
                5. If the code does not meet acceptance criteria, route to `ReviewAgent` for deeper review. Review routing guidance: %s.
                6. Include worktree IDs, worktree paths, merge commits, and conflict/error details when creating Merger/Review requests.
                7. Collector scope requirement: %s
                """;

        @Override
        public String name() {
            return MergeAggregationPromptContributor.class.getSimpleName();
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return hasMergeData();
        }

        @Override
        public String contribute(PromptContext context) {
            if (!hasMergeData()) {
                return "";
            }
            TemplateArgs args = buildTemplateArgs();
            return String.format(
                    template(),
                    args.role(),
                    args.sourceCount(),
                    args.sourceLines(),
                    args.overallStatus(),
                    args.mergedCount(),
                    args.mergedLines(),
                    args.conflictedCount(),
                    args.conflictedLines(),
                    args.pendingCount(),
                    args.pendingLines(),
                    args.mainWorktreeLines(),
                    args.submoduleWorktreeLines(),
                    args.mergeCommitLines(),
                    args.finalMergeStatus(),
                    args.finalMergeConflictFiles(),
                    args.finalMergeError(),
                    args.finalMergeCommit(),
                    args.mergerApplicability(),
                    args.reviewApplicability(),
                    args.collectorScopeRequirement()
            );
        }

        @Override
        public String template() {
            return TEMPLATE;
        }

        @Override
        public int priority() {
            return 500;
        }

        private boolean hasMergeData() {
            return (aggregationSources != null && !aggregationSources.isEmpty()) || finalMergeDescriptor != null;
        }

        private TemplateArgs buildTemplateArgs() {
            List<MergeAggregationSource> sources = aggregationSources == null ? List.of() : aggregationSources;
            List<SourcedStatus> merged = collectMergedStatuses(sources);
            List<SourcedStatus> pending = collectPendingStatuses(sources);
            List<SourcedStatus> conflicted = collectConflictedStatuses(sources);
            List<SourcedStatus> allStatuses = new ArrayList<>();
            allStatuses.addAll(merged);
            allStatuses.addAll(conflicted);
            allStatuses.addAll(pending);

            boolean hasDispatchMergeIssues = !pending.isEmpty() || !conflicted.isEmpty();
            boolean hasFinalMergeFailure = finalMergeDescriptor != null && !finalMergeDescriptor.successful();
            boolean hasAnyMergeFailure = hasDispatchMergeIssues || hasFinalMergeFailure;

            String role = isCollectorAgentType(agentType)
                    ? "collector-routing (reviews current + previous dispatch worktrees)"
                    : "dispatch-routing (reviews this dispatch round across multiple worktrees)";
            String sourceCount = Integer.toString(sources.size());
            String sourceLines = toLineListOrNone(buildSourceLines(sources));
            String overallStatus = hasAnyMergeFailure ? "MERGE ISSUES DETECTED" : "NO MERGE FAILURES DETECTED";

            String mergedCount = Integer.toString(merged.size());
            String mergedLines = toLineListOrNone(buildMergedLines(merged));
            String conflictedCount = Integer.toString(conflicted.size());
            String conflictedLines = toLineListOrNone(buildConflictedLines(conflicted));
            String pendingCount = Integer.toString(pending.size());
            String pendingLines = toLineListOrNone(buildPendingLines(pending));

            String mainWorktreeLines = toLineListOrNone(buildMainWorktreeLines(allStatuses));
            String submoduleWorktreeLines = toLineListOrNone(buildSubmoduleWorktreeLines(allStatuses));
            String mergeCommitLines = toLineListOrNone(buildMergeCommitLines(allStatuses));

            String finalMergeStatus = resolveFinalMergeStatus(finalMergeDescriptor);
            String finalMergeConflictFiles = toLineListOrNone(resolveFinalMergeConflictFiles(finalMergeDescriptor));
            String finalMergeError = resolveFinalMergeError(finalMergeDescriptor);
            String finalMergeCommit = resolveFinalMergeCommit(finalMergeDescriptor);

            String mergerApplicability = hasAnyMergeFailure ? "REQUIRED" : "OPTIONAL";
            String reviewApplicability = hasAnyMergeFailure
                    ? "REQUIRED after merger/failure remediation; route immediately if acceptance criteria fail"
                    : "REQUIRED for acceptance verification; route if preliminary review detects risk";
            String collectorScopeRequirement = isCollectorAgentType(agentType)
                    ? "Validate all listed history/current worktrees; do not scope review to only one dispatch result."
                    : "Validate all dispatched child worktrees in this round before routing.";

            return new TemplateArgs(
                    role,
                    sourceCount,
                    sourceLines,
                    overallStatus,
                    mergedCount,
                    mergedLines,
                    conflictedCount,
                    conflictedLines,
                    pendingCount,
                    pendingLines,
                    mainWorktreeLines,
                    submoduleWorktreeLines,
                    mergeCommitLines,
                    finalMergeStatus,
                    finalMergeConflictFiles,
                    finalMergeError,
                    finalMergeCommit,
                    mergerApplicability,
                    reviewApplicability,
                    collectorScopeRequirement
            );
        }

        private List<SourcedStatus> collectMergedStatuses(List<MergeAggregationSource> sources) {
            if (sources == null || sources.isEmpty()) {
                return List.of();
            }
            List<SourcedStatus> collected = new ArrayList<>();
            for (MergeAggregationSource source : sources) {
                if (source == null || source.aggregation() == null || source.aggregation().merged() == null) {
                    continue;
                }
                for (AgentMergeStatus status : source.aggregation().merged()) {
                    if (status == null) {
                        continue;
                    }
                    collected.add(new SourcedStatus(source.source(), status));
                }
            }
            return collected;
        }

        private List<SourcedStatus> collectPendingStatuses(List<MergeAggregationSource> sources) {
            if (sources == null || sources.isEmpty()) {
                return List.of();
            }
            List<SourcedStatus> collected = new ArrayList<>();
            for (MergeAggregationSource source : sources) {
                if (source == null || source.aggregation() == null || source.aggregation().pending() == null) {
                    continue;
                }
                for (AgentMergeStatus status : source.aggregation().pending()) {
                    if (status == null) {
                        continue;
                    }
                    collected.add(new SourcedStatus(source.source(), status));
                }
            }
            return collected;
        }

        private List<SourcedStatus> collectConflictedStatuses(List<MergeAggregationSource> sources) {
            if (sources == null || sources.isEmpty()) {
                return List.of();
            }
            List<SourcedStatus> collected = new ArrayList<>();
            for (MergeAggregationSource source : sources) {
                if (source == null || source.aggregation() == null || source.aggregation().conflicted() == null) {
                    continue;
                }
                collected.add(new SourcedStatus(source.source(), source.aggregation().conflicted()));
            }
            return collected;
        }

        private List<String> buildSourceLines(List<MergeAggregationSource> sources) {
            if (sources == null || sources.isEmpty()) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            for (MergeAggregationSource source : sources) {
                if (source == null || source.aggregation() == null) {
                    continue;
                }
                lines.add(String.format(
                        "%s | merged=%d | conflicted=%s | pending=%d",
                        safeValue(source.source(), "unknown"),
                        source.aggregation().merged() != null ? source.aggregation().merged().size() : 0,
                        source.aggregation().conflicted() != null ? "yes" : "no",
                        source.aggregation().pending() != null ? source.aggregation().pending().size() : 0
                ));
            }
            return lines;
        }

        private List<String> buildMergedLines(List<SourcedStatus> merged) {
            if (merged == null || merged.isEmpty()) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            for (SourcedStatus status : merged) {
                if (status == null || status.status() == null) {
                    continue;
                }
                lines.add(String.format(
                        "%s | %s | merged",
                        safeValue(status.source(), "unknown"),
                        safeValue(status.status().agentResultId(), "unknown")
                ));
            }
            return lines;
        }

        private List<String> buildConflictedLines(List<SourcedStatus> conflicted) {
            if (conflicted == null || conflicted.isEmpty()) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            for (SourcedStatus status : conflicted) {
                if (status == null || status.status() == null) {
                    continue;
                }
                MergeDescriptor descriptor = status.status().mergeDescriptor();
                String error = descriptor != null ? safeValue(descriptor.errorMessage(), "none") : "none";
                String conflictFiles = descriptor != null
                        ? toCommaListOrNone(nonBlankValues(descriptor.conflictFiles()))
                        : "none";
                lines.add(String.format(
                        "%s | %s | conflicted | %s | %s",
                        safeValue(status.source(), "unknown"),
                        safeValue(status.status().agentResultId(), "unknown"),
                        error,
                        conflictFiles
                ));
            }
            return lines;
        }

        private List<String> buildPendingLines(List<SourcedStatus> pending) {
            if (pending == null || pending.isEmpty()) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            for (SourcedStatus status : pending) {
                if (status == null || status.status() == null) {
                    continue;
                }
                lines.add(String.format(
                        "%s | %s | pending",
                        safeValue(status.source(), "unknown"),
                        safeValue(status.status().agentResultId(), "unknown")
                ));
            }
            return lines;
        }

        private List<String> buildMainWorktreeLines(List<SourcedStatus> statuses) {
            if (statuses == null || statuses.isEmpty()) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            for (SourcedStatus status : statuses) {
                if (status == null || status.status() == null) {
                    continue;
                }
                WorktreeSandboxContext worktreeContext = status.status().worktreeContext();
                if (worktreeContext == null || worktreeContext.mainWorktree() == null) {
                    continue;
                }
                MainWorktreeContext main = worktreeContext.mainWorktree();
                lines.add(String.format(
                        "%s | %s | %s | %s | %s | %s | %s",
                        safeValue(status.source(), "unknown"),
                        safeValue(status.status().agentResultId(), "unknown"),
                        safeValue(main.worktreeId(), "unknown"),
                        safeValue(pathOrNull(main.worktreePath()), "unknown"),
                        safeValue(main.baseBranch(), "unknown"),
                        safeValue(main.derivedBranch(), "unknown"),
                        safeValue(main.lastCommitHash(), "unknown")
                ));
            }
            return lines;
        }

        private List<String> buildSubmoduleWorktreeLines(List<SourcedStatus> statuses) {
            if (statuses == null || statuses.isEmpty()) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            for (SourcedStatus status : statuses) {
                if (status == null || status.status() == null) {
                    continue;
                }
                WorktreeSandboxContext worktreeContext = status.status().worktreeContext();
                if (worktreeContext == null || worktreeContext.submoduleWorktrees() == null) {
                    continue;
                }
                for (SubmoduleWorktreeContext submodule : worktreeContext.submoduleWorktrees()) {
                    if (submodule == null) {
                        continue;
                    }
                    lines.add(String.format(
                            "%s | %s | %s | %s | %s | %s | %s",
                            safeValue(status.source(), "unknown"),
                            safeValue(status.status().agentResultId(), "unknown"),
                            safeValue(submodule.submoduleName(), "unknown"),
                            safeValue(submodule.worktreeId(), "unknown"),
                            safeValue(pathOrNull(submodule.worktreePath()), "unknown"),
                            safeValue(submodule.baseBranch(), "unknown"),
                            safeValue(submodule.lastCommitHash(), "unknown")
                    ));
                }
            }
            return lines;
        }

        private List<String> buildMergeCommitLines(List<SourcedStatus> statuses) {
            if (statuses == null || statuses.isEmpty()) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            for (SourcedStatus status : statuses) {
                if (status == null || status.status() == null || status.status().mergeDescriptor() == null) {
                    continue;
                }
                MergeDescriptor descriptor = status.status().mergeDescriptor();
                String mergeCommit = descriptor.mainWorktreeMergeResult() != null
                        ? safeValue(descriptor.mainWorktreeMergeResult().mergeCommitHash(), "none")
                        : "none";
                String childId = descriptor.mainWorktreeMergeResult() != null
                        ? safeValue(descriptor.mainWorktreeMergeResult().childWorktreeId(), "unknown")
                        : "unknown";
                String parentId = descriptor.mainWorktreeMergeResult() != null
                        ? safeValue(descriptor.mainWorktreeMergeResult().parentWorktreeId(), "unknown")
                        : "unknown";
                lines.add(String.format(
                        "%s | %s | %s | %s | %s | %s",
                        safeValue(status.source(), "unknown"),
                        safeValue(status.status().agentResultId(), "unknown"),
                        mergeCommit,
                        childId,
                        parentId,
                        Boolean.toString(descriptor.successful())
                ));
            }
            return lines;
        }

        private List<String> resolveFinalMergeConflictFiles(MergeDescriptor descriptor) {
            if (descriptor == null) {
                return List.of();
            }
            return nonBlankValues(descriptor.conflictFiles());
        }

        private String resolveFinalMergeStatus(MergeDescriptor descriptor) {
            if (descriptor == null) {
                return "not available";
            }
            return descriptor.successful() ? "successful" : "failed";
        }

        private String resolveFinalMergeError(MergeDescriptor descriptor) {
            if (descriptor == null) {
                return "none";
            }
            return safeValue(descriptor.errorMessage(), "none");
        }

        private String resolveFinalMergeCommit(MergeDescriptor descriptor) {
            if (descriptor == null || descriptor.mainWorktreeMergeResult() == null) {
                return "none";
            }
            return safeValue(descriptor.mainWorktreeMergeResult().mergeCommitHash(), "none");
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
                filtered.add(value);
            }
            return filtered;
        }

        private String pathOrNull(java.nio.file.Path path) {
            return path == null ? null : path.toString();
        }

        private String toLineListOrNone(List<String> lines) {
            if (lines == null || lines.isEmpty()) {
                return "none";
            }
            return String.join("\n", lines);
        }

        private String toCommaListOrNone(List<String> values) {
            if (values == null || values.isEmpty()) {
                return "none";
            }
            return String.join(", ", values);
        }

        private String safeValue(String value, String fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value;
        }

        private record TemplateArgs(
                String role,
                String sourceCount,
                String sourceLines,
                String overallStatus,
                String mergedCount,
                String mergedLines,
                String conflictedCount,
                String conflictedLines,
                String pendingCount,
                String pendingLines,
                String mainWorktreeLines,
                String submoduleWorktreeLines,
                String mergeCommitLines,
                String finalMergeStatus,
                String finalMergeConflictFiles,
                String finalMergeError,
                String finalMergeCommit,
                String mergerApplicability,
                String reviewApplicability,
                String collectorScopeRequirement
        ) {
        }

        private record SourcedStatus(
                String source,
                AgentMergeStatus status
        ) {
        }
    }

    private record MergeAggregationSource(
            String source,
            MergeAggregation aggregation
    ) {
    }
}
