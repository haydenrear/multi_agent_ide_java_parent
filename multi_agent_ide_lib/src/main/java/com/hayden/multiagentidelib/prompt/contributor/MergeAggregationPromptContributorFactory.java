package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.model.merge.AgentMergeStatus;
import com.hayden.multiagentidelib.model.merge.MergeAggregation;
import com.hayden.multiagentidelib.model.merge.MergeDescriptor;
import com.hayden.multiagentidelib.model.merge.SubmoduleMergeResult;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Factory that provides merge status context to routing LLMs via the standard prompt contributor pattern.
 *
 * This is used to inform the dispatch agent's routing LLM about the status of child→trunk merges
 * so it can decide how to handle any conflicts (e.g., emit MergerInterruptRequest, MergerRequest, skip).
 */
@Component
public class MergeAggregationPromptContributorFactory implements PromptContributorFactory {

    private static final Set<AgentType> DISPATCH_AGENT_TYPES = Set.of(
            AgentType.TICKET_AGENT_DISPATCH,
            AgentType.PLANNING_AGENT_DISPATCH,
            AgentType.DISCOVERY_AGENT_DISPATCH
    );

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        // Only applicable for dispatch agent types that have ResultsRequest
        if (!isDispatchAgentType(context.agentType())) {
            return List.of();
        }

        // Extract MergeAggregation from the current request if it's a ResultsRequest
        MergeAggregation aggregation = extractMergeAggregation(context);
        if (aggregation == null) {
            return List.of();
        }

        return List.of(new MergeAggregationPromptContributor(aggregation));
    }

    private boolean isDispatchAgentType(AgentType agentType) {
        return agentType != null && DISPATCH_AGENT_TYPES.contains(agentType);
    }

    private MergeAggregation extractMergeAggregation(PromptContext context) {
        AgentModels.AgentRequest request = context.currentRequest();
        if (request instanceof AgentModels.ResultsRequest resultsRequest) {
            return resultsRequest.mergeAggregation();
        }
        // Also check metadata in case it was stored there
        Object fromMetadata = context.metadata().get("mergeAggregation");
        if (fromMetadata instanceof MergeAggregation agg) {
            return agg;
        }
        return null;
    }

    /**
     * Prompt contributor that provides merge aggregation status to routing LLMs.
     * Includes detailed per-submodule merge step information and specific instructions
     * for Review and Merger routing decisions.
     */
    public record MergeAggregationPromptContributor(
            MergeAggregation aggregation
    ) implements PromptContributor {

        private static final String TEMPLATE = """
                ## Worktree Merge Status

                The child agent worktrees have been merged back to the parent worktree.
                Multiple agents worked in isolated worktrees and their changes have been
                merged using a leaves-first submodule merge strategy. Review the merge
                status below to determine next steps.

                **Overall Status**: %s

                ### Completed Merge Steps
                Count: %s
                Agent result IDs (one per line, or `none`):
                %s

                Submodule results for completed steps
                Format: `<agentResultId> | <submoduleName> | <status> | <pointerUpdated>`
                %s

                ### Conflicted Merge Step
                Agent result ID (single line, or `none`):
                %s

                Submodule results for conflicted step
                Format: `<submoduleName> | <status> | <pointerUpdated>`
                %s

                Conflict files (one per line, or `none`):
                %s

                Error message (single line, or `none`):
                %s

                ### Pending Merge Steps
                Count: %s
                Agent result IDs (one per line, or `none`):
                %s

                ---
                ### Routing Instructions

                **Important context**: Because multiple agents worked in parallel on isolated
                worktrees, the merged repository may be in an inconsistent state. Each agent's
                changes were developed independently, so even when merges succeed without
                conflicts, the combined result may have:
                - Build or compilation errors from incompatible changes across agents
                - Duplicate or contradictory modifications to shared files
                - Submodule pointer mismatches between parent and child repositories
                - Incomplete integration of cross-cutting changes

                #### Collector Responsibilities (This Routing Step)

                You are the collector LLM deciding next routing after child worktree merges.
                Before routing, you should:
                - Review the merge status details above and identify conflicts or pending merges
                - Scan for overlapping changes across agents that may require consolidation
                - Choose the next step: route to MergerAgent, ReviewAgent, or the appropriate
                  collector agent for this result type (for example, TicketCollector)
                - If the decision depends on unclear intent or user preference, use the
                  question-answer tool to ask the user a brief, targeted question before routing

                #### If Routing to MergerAgent

                Applicability: %s
                - REQUIRED when conflicts or pending merges exist.
                - NOT REQUIRED when all merges are successful.

                The MergerAgent should resolve merge conflicts and complete any remaining
                merge steps. When populating the `mergerRequest`, include the following:

                1. **`mergeContext`**: Describe the completed merge steps so far and what
                   remains. Specifically:
                   - Already merged successfully: %s
                   - Currently conflicted: %s
                   - Still pending merge: %s

                2. **`conflictFiles`**: List all conflicting file paths from the merge
                   descriptor above. Use the conflict file list already provided.
                   Conflict files (one per line, or `none`):
                   %s

                3. **`mergeSummary`**: Provide instructions for the MergerAgent:
                   - Resolve the listed conflicts by examining both sides of each conflict
                   - After resolving conflicts, verify the build compiles across all submodules
                   - Stage and commit resolved files with a descriptive commit message that
                     includes metadata about which agents' work was merged
                   - Once conflicts are resolved, the remaining pending agents' merges can
                     proceed — instruct the MergerAgent to note this for the next routing step
                   - After completing merge work, the MergerAgent should route to ReviewAgent
                     (via the returnToXxxCollector fields) with instructions to validate the
                     combined result

                4. **`returnToXxxCollector`**: Set the appropriate return-to-collector field so
                   that after the MergerAgent completes, routing returns to this collector for
                   the next phase decision.

                #### If Routing to ReviewAgent

                Applicability: %s
                - APPLIES when all merges are successful or conflicts have been resolved.
                - DEFER when conflicts remain; route to MergerAgent first.

                When populating the `reviewRequest`, include the following:

                1. **`content`**: Package a summary of all changes that were merged. For each
                   agent that was merged, describe:
                   - The agent identifier and what work it performed
                   - Which submodules were affected and the merge direction
                   - Any files that were modified across multiple agents (potential integration issues)

                2. **`criteria`**: Provide review criteria that accounts for multi-agent merge:
                   - Verify the build compiles successfully across all submodules
                   - Check for semantic conflicts (changes that don't conflict at the text level
                     but are logically incompatible)
                   - Validate that submodule pointers are consistent with their actual content
                   - Review any files that were touched by multiple agents for coherence
                   - Confirm that cross-cutting concerns (shared interfaces, common utilities)
                     are consistent after the merge

                3. **Processing steps for ReviewAgent**: Instruct the reviewer to:
                   - First run the build/compilation to catch integration errors
                   - Create a unified commit per submodule that squashes the merge commits,
                     with a commit message containing metadata: which agents contributed,
                     what phase this merge belongs to, and a summary of combined changes
                   - If issues are found, describe them clearly so the next routing step can
                     address them (e.g., route back to MergerAgent or a specific agent)

                4. **`returnToXxxCollector`**: Set the appropriate return-to-collector field so
                   that after the ReviewAgent completes, routing returns to this collector for
                   the next phase decision.
                """;

        @Override
        public String name() {
            return MergeAggregationPromptContributor.class.getSimpleName();
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return aggregation != null;
        }

        @Override
        public String contribute(PromptContext context) {
            if (aggregation == null) {
                return "";
            }
            TemplateArgs args = buildTemplateArgs(aggregation);
            return String.format(
                    template(),
                    args.overallStatus(),
                    args.mergedCount(),
                    args.mergedAgentIds(),
                    args.mergedSubmoduleResults(),
                    args.conflictedAgentId(),
                    args.conflictedSubmoduleResults(),
                    args.conflictFiles(),
                    args.conflictError(),
                    args.pendingCount(),
                    args.pendingAgentIds(),
                    args.mergerApplicability(),
                    args.mergerMergedIds(),
                    args.mergerConflictedId(),
                    args.mergerPendingIds(),
                    args.mergerConflictFiles(),
                    args.reviewApplicability()
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

        private TemplateArgs buildTemplateArgs(MergeAggregation agg) {
            List<AgentMergeStatus> merged = agg.merged() != null ? agg.merged() : List.of();
            List<AgentMergeStatus> pending = agg.pending() != null ? agg.pending() : List.of();
            AgentMergeStatus conflicted = agg.conflicted();
            boolean hasConflictsOrPending = agg.hasConflict() || !pending.isEmpty();

            String overallStatus = agg.allSuccessful() ? "ALL MERGED SUCCESSFULLY" : "MERGE ISSUES DETECTED";
            String mergedCount = Integer.toString(merged.size());
            String mergedAgentIds = toLineListOrNone(extractAgentIds(merged));
            String mergedSubmoduleResults = toLineListOrNone(buildMergedSubmoduleLines(merged));
            String conflictedAgentId = conflicted != null
                    ? safeValue(conflicted.agentResultId(), "unknown")
                    : "none";
            String conflictedSubmoduleResults = toLineListOrNone(buildConflictedSubmoduleLines(conflicted));
            String conflictFiles = toLineListOrNone(buildConflictFileLines(conflicted));
            String conflictError = safeValue(
                    conflicted != null && conflicted.mergeDescriptor() != null
                            ? conflicted.mergeDescriptor().errorMessage()
                            : null,
                    "none"
            );
            String pendingCount = Integer.toString(pending.size());
            String pendingAgentIds = toLineListOrNone(extractAgentIds(pending));

            String mergerApplicability = hasConflictsOrPending ? "REQUIRED" : "NOT REQUIRED";
            String mergerMergedIds = toCommaListOrNone(extractAgentIds(merged));
            String mergerConflictedId = conflicted != null
                    ? safeValue(conflicted.agentResultId(), "unknown")
                    : "none";
            String mergerPendingIds = toCommaListOrNone(extractAgentIds(pending));
            String mergerConflictFiles = toLineListOrNone(buildConflictFileLines(conflicted));

            String reviewApplicability = hasConflictsOrPending ? "DEFER" : "APPLIES";

            return new TemplateArgs(
                    overallStatus,
                    mergedCount,
                    mergedAgentIds,
                    mergedSubmoduleResults,
                    conflictedAgentId,
                    conflictedSubmoduleResults,
                    conflictFiles,
                    conflictError,
                    pendingCount,
                    pendingAgentIds,
                    mergerApplicability,
                    mergerMergedIds,
                    mergerConflictedId,
                    mergerPendingIds,
                    mergerConflictFiles,
                    reviewApplicability
            );
        }

        private List<String> extractAgentIds(List<AgentMergeStatus> statuses) {
            if (statuses == null || statuses.isEmpty()) {
                return List.of();
            }
            List<String> ids = new ArrayList<>();
            for (AgentMergeStatus status : statuses) {
                if (status == null) {
                    continue;
                }
                ids.add(safeValue(status.agentResultId(), "unknown"));
            }
            return ids;
        }

        private List<String> buildMergedSubmoduleLines(List<AgentMergeStatus> merged) {
            if (merged == null || merged.isEmpty()) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            for (AgentMergeStatus status : merged) {
                if (status == null || status.mergeDescriptor() == null) {
                    continue;
                }
                MergeDescriptor descriptor = status.mergeDescriptor();
                List<SubmoduleMergeResult> subResults = descriptor.submoduleMergeResults();
                if (subResults == null || subResults.isEmpty()) {
                    continue;
                }
                String agentId = safeValue(status.agentResultId(), "unknown");
                for (SubmoduleMergeResult sub : subResults) {
                    if (sub == null) {
                        continue;
                    }
                    lines.add(String.format(
                            "%s | %s | %s | %s",
                            agentId,
                            safeValue(sub.submoduleName(), "unknown"),
                            sub.successful() ? "ok" : "conflict",
                            Boolean.toString(sub.pointerUpdated())
                    ));
                }
            }
            return lines;
        }

        private List<String> buildConflictedSubmoduleLines(AgentMergeStatus conflicted) {
            if (conflicted == null || conflicted.mergeDescriptor() == null) {
                return List.of();
            }
            List<SubmoduleMergeResult> subResults = conflicted.mergeDescriptor().submoduleMergeResults();
            if (subResults == null || subResults.isEmpty()) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            for (SubmoduleMergeResult sub : subResults) {
                if (sub == null) {
                    continue;
                }
                lines.add(String.format(
                        "%s | %s | %s",
                        safeValue(sub.submoduleName(), "unknown"),
                        sub.successful() ? "ok" : "conflict",
                        Boolean.toString(sub.pointerUpdated())
                ));
            }
            return lines;
        }

        private List<String> buildConflictFileLines(AgentMergeStatus conflicted) {
            if (conflicted == null || conflicted.mergeDescriptor() == null) {
                return List.of();
            }
            List<String> conflictFiles = conflicted.mergeDescriptor().conflictFiles();
            if (conflictFiles == null || conflictFiles.isEmpty()) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            for (String file : conflictFiles) {
                if (file == null || file.isBlank()) {
                    continue;
                }
                lines.add(file);
            }
            return lines;
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
                String overallStatus,
                String mergedCount,
                String mergedAgentIds,
                String mergedSubmoduleResults,
                String conflictedAgentId,
                String conflictedSubmoduleResults,
                String conflictFiles,
                String conflictError,
                String pendingCount,
                String pendingAgentIds,
                String mergerApplicability,
                String mergerMergedIds,
                String mergerConflictedId,
                String mergerPendingIds,
                String mergerConflictFiles,
                String reviewApplicability
        ) {
        }
    }
}
