package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.model.merge.AgentMergeStatus;
import com.hayden.multiagentidelib.model.merge.MergeAggregation;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Emits merge context by walking marker interfaces instead of request-specific deconstruction.
 */
@Component
public class MergeAggregationPromptContributorFactory implements PromptContributorFactory {

    private static final String DISPATCH_NAME = "dispatch-merge-validation-v2";
    private static final String COLLECTOR_NAME = "collector-merge-validation-v2";

    private static final String DISPATCH_TEMPLATE = """
            ## Dispatch Merge Context (Simplified)

            Merge descriptors (serialized, one per line or `none`):
            %s

            Merge aggregations (serialized, one per line or `none`):
            %s

            Merge conflict agent outcomes (one per line or `none`):
            %s
            """;

    private static final String COLLECTOR_TEMPLATE = """
            ## Collector Merge Context (Simplified)

            Merge descriptors (serialized, one per line or `none`):
            %s

            Merge aggregations (serialized, one per line or `none`):
            %s

            Merge conflict agent outcomes (one per line or `none`):
            %s

            Final collector merge descriptors (one per line or `none`):
            %s
            """;

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        AgentModels.AgentRequest request = context.currentRequest();
        if (request instanceof AgentModels.ResultsRequest) {
            MergePromptSnapshot snapshot = collectSnapshot(context);
            if (!snapshot.hasAnyData()) {
                return List.of();
            }
            return List.of(new DispatchMergeValidationPromptContributor(snapshot));
        }

        if (request instanceof AgentModels.DiscoveryCollectorRequest
                || request instanceof AgentModels.PlanningCollectorRequest
                || request instanceof AgentModels.TicketCollectorRequest
                || request instanceof AgentModels.OrchestratorCollectorRequest) {
            MergePromptSnapshot snapshot = collectSnapshot(context);
            if (!snapshot.hasAnyData()) {
                return List.of();
            }
            return List.of(new CollectorMergeValidationPromptContributor(snapshot));
        }

        return List.of();
    }

    private MergePromptSnapshot collectSnapshot(PromptContext context) {
        LinkedHashSet<String> descriptorLines = new LinkedHashSet<>();
        LinkedHashSet<String> aggregationLines = new LinkedHashSet<>();
        LinkedHashSet<String> conflictOutcomeLines = new LinkedHashSet<>();
        LinkedHashSet<String> worktreeLines = new LinkedHashSet<>();
        LinkedHashSet<String> finalCollectorLines = new LinkedHashSet<>();

        collectFromObject("current", context.currentRequest(), descriptorLines, aggregationLines, conflictOutcomeLines, worktreeLines, finalCollectorLines);
        collectFromObject("previous", context.previousRequest(), descriptorLines, aggregationLines, conflictOutcomeLines, worktreeLines, finalCollectorLines);

        BlackboardHistory history = context.blackboardHistory();
        if (history != null) {
            int sequence = 1;
            for (BlackboardHistory.Entry entry : history.copyOfEntries()) {
                if (!(entry instanceof BlackboardHistory.DefaultEntry defaultEntry)) {
                    continue;
                }
                Object input = defaultEntry.input();
                if (input == null) {
                    continue;
                }
                collectFromObject(
                        "history:%02d:%s".formatted(sequence++, input.getClass().getSimpleName()),
                        input,
                        descriptorLines,
                        aggregationLines,
                        conflictOutcomeLines,
                        worktreeLines,
                        finalCollectorLines
                );
            }
        }

        return new MergePromptSnapshot(
                List.copyOf(descriptorLines),
                List.copyOf(aggregationLines),
                List.copyOf(conflictOutcomeLines),
                List.copyOf(worktreeLines),
                List.copyOf(finalCollectorLines)
        );
    }

    private void collectFromObject(
            String source,
            Object value,
            LinkedHashSet<String> descriptorLines,
            LinkedHashSet<String> aggregationLines,
            LinkedHashSet<String> conflictOutcomeLines,
            LinkedHashSet<String> worktreeLines,
            LinkedHashSet<String> finalCollectorLines
    ) {
        if (value == null) {
            return;
        }

        if (value instanceof AgentModels.HasMergeDescriptor withDescriptor && withDescriptor.mergeDescriptor() != null) {
            descriptorLines.add(source + " | " + serializeDescriptor(withDescriptor.mergeDescriptor()));
        }

        if (value instanceof AgentModels.HasMergeAggregation withAggregation && withAggregation.mergeAggregation() != null) {
            aggregationLines.add(source + " | " + serializeAggregation(withAggregation.mergeAggregation()));
        }

        if (value instanceof AgentModels.MergeConflictResult conflictResult) {
            conflictOutcomeLines.add(source + " | " + serializeConflictOutcome(conflictResult));
        }

        if (value instanceof AgentModels.OrchestratorCollectorRequest collectorRequest && collectorRequest.mergeDescriptor() != null) {
            finalCollectorLines.add(source + " | " + serializeDescriptor(collectorRequest.mergeDescriptor()));
        }

        if (value instanceof AgentModels.AgentRequest request) {
            addWorktreeLine(source, request.worktreeContext(), worktreeLines);
        }

        if (value instanceof AgentModels.AgentResult result) {
            addWorktreeLine(source, result.worktreeContext(), worktreeLines);
        }

        if (value instanceof AgentModels.ResultsRequest resultsRequest) {
            addWorktreeLine(source + ":results", resultsRequest.worktreeContext(), worktreeLines);
            List<? extends AgentModels.AgentResult> children = resultsRequest.childResults();
            if (children != null) {
                for (AgentModels.AgentResult child : children) {
                    if (child == null) {
                        continue;
                    }
                    String childId = child.contextId() != null && child.contextId().value() != null
                            ? child.contextId().value()
                            : "unknown";
                    collectFromObject(
                            source + ":child:" + childId,
                            child,
                            descriptorLines,
                            aggregationLines,
                            conflictOutcomeLines,
                            worktreeLines,
                            finalCollectorLines
                    );
                }
            }
        }
    }

    private void addWorktreeLine(String source, WorktreeSandboxContext context, LinkedHashSet<String> worktreeLines) {
        if (context == null || context.mainWorktree() == null) {
            return;
        }
        List<String> submodulePaths = new ArrayList<>();
        if (context.submoduleWorktrees() != null) {
            for (SubmoduleWorktreeContext submodule : context.submoduleWorktrees()) {
                if (submodule == null || submodule.worktreePath() == null) {
                    continue;
                }
                submodulePaths.add(submodule.worktreePath().toString());
            }
        }

        worktreeLines.add(source
                + " | mainId=" + orNone(context.mainWorktree().worktreeId())
                + " | mainPath=" + orNone(pathValue(context.mainWorktree().worktreePath()))
                + " | submodules=" + commaOrNone(submodulePaths));
    }

    private String serializeDescriptor(MergeDescriptor descriptor) {
        if (descriptor == null) {
            return "descriptor=none";
        }

        int commitCount = descriptor.commitMetadata() == null ? 0 : descriptor.commitMetadata().size();
        return "direction=" + descriptor.mergeDirection()
                + " | successful=" + descriptor.successful()
                + " | errorType=" + (descriptor.errorType() != null ? descriptor.errorType() : "UNKNOWN")
                + " | conflicts=" + commaOrNone(descriptor.conflictFiles())
                + " | error=" + orNone(singleLine(descriptor.errorMessage()))
                + " | commits=" + commitCount;
    }

    private String serializeAggregation(MergeAggregation aggregation) {
        if (aggregation == null) {
            return "aggregation=none";
        }

        int mergedCount = aggregation.merged() == null ? 0 : aggregation.merged().size();
        int pendingCount = aggregation.pending() == null ? 0 : aggregation.pending().size();
        AgentMergeStatus conflicted = aggregation.conflicted();
        String conflictedId = conflicted == null ? "none" : orNone(conflicted.agentResultId());
        String conflictedError = conflicted == null || conflicted.mergeDescriptor() == null
                ? "none"
                : orNone(singleLine(conflicted.mergeDescriptor().errorMessage()));

        return "merged=" + mergedCount
                + " | pending=" + pendingCount
                + " | conflicted=" + conflictedId
                + " | conflictedError=" + conflictedError;
    }

    private String serializeConflictOutcome(AgentModels.MergeConflictResult conflictResult) {
        return "successful=" + conflictResult.successful()
                + " | error=" + orNone(singleLine(conflictResult.errorMessage()))
                + " | resolved=" + commaOrNone(conflictResult.resolvedConflictFiles())
                + " | notes=" + commaOrNone(conflictResult.notes());
    }

    private String pathValue(java.nio.file.Path path) {
        return path == null ? "none" : path.toAbsolutePath().normalize().toString();
    }

    private String singleLine(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\n", " ").replace("\r", " ").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String orNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private String commaOrNone(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "none";
        }
        List<String> normalized = values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(this::singleLine)
                .filter(v -> v != null && !v.isBlank())
                .toList();
        return normalized.isEmpty() ? "none" : String.join(", ", normalized);
    }

    private String linesOrNone(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "none";
        }
        return String.join("\n", lines);
    }

    public record DispatchMergeValidationPromptContributor(MergePromptSnapshot snapshot) implements PromptContributor {

        @Override
        public String name() {
            return DISPATCH_NAME;
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return promptContext != null && promptContext.currentRequest() instanceof AgentModels.ResultsRequest;
        }

        @Override
        public String contribute(PromptContext context) {
            return DISPATCH_TEMPLATE.formatted(
                    linesOrNone(snapshot.descriptorLines()),
                    linesOrNone(snapshot.aggregationLines()),
                    linesOrNone(snapshot.conflictOutcomeLines())
            );
        }

        @Override
        public String template() {
            return DISPATCH_TEMPLATE;
        }

        @Override
        public int priority() {
            return 190;
        }

        private String linesOrNone(List<String> lines) {
            if (lines == null || lines.isEmpty()) {
                return "none";
            }
            return String.join("\n", lines);
        }
    }

    public record CollectorMergeValidationPromptContributor(MergePromptSnapshot snapshot) implements PromptContributor {

        @Override
        public String name() {
            return COLLECTOR_NAME;
        }

        @Override
        public boolean include(PromptContext promptContext) {
            if (promptContext == null || promptContext.currentRequest() == null) {
                return false;
            }
            return promptContext.currentRequest() instanceof AgentModels.DiscoveryCollectorRequest
                    || promptContext.currentRequest() instanceof AgentModels.PlanningCollectorRequest
                    || promptContext.currentRequest() instanceof AgentModels.TicketCollectorRequest
                    || promptContext.currentRequest() instanceof AgentModels.OrchestratorCollectorRequest;
        }

        @Override
        public String contribute(PromptContext context) {
            return COLLECTOR_TEMPLATE.formatted(
                    linesOrNone(snapshot.descriptorLines()),
                    linesOrNone(snapshot.aggregationLines()),
                    linesOrNone(snapshot.conflictOutcomeLines()),
                    linesOrNone(snapshot.finalCollectorLines())
            );
        }

        @Override
        public String template() {
            return COLLECTOR_TEMPLATE;
        }

        @Override
        public int priority() {
            return 190;
        }

        private String linesOrNone(List<String> lines) {
            if (lines == null || lines.isEmpty()) {
                return "none";
            }
            return String.join("\n", lines);
        }
    }

    private record MergePromptSnapshot(
            List<String> descriptorLines,
            List<String> aggregationLines,
            List<String> conflictOutcomeLines,
            List<String> worktreeLines,
            List<String> finalCollectorLines
    ) {
        private boolean hasAnyData() {
            return !(descriptorLines == null || descriptorLines.isEmpty())
                    || !(aggregationLines == null || aggregationLines.isEmpty())
                    || !(conflictOutcomeLines == null || conflictOutcomeLines.isEmpty())
                    || !(worktreeLines == null || worktreeLines.isEmpty())
                    || !(finalCollectorLines == null || finalCollectorLines.isEmpty());
        }
    }
}
