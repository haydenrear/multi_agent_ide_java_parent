package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.model.merge.AgentMergeStatus;
import com.hayden.multiagentidelib.model.merge.MergeAggregation;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

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
     */
    public record MergeAggregationPromptContributor(
            MergeAggregation aggregation
    ) implements PromptContributor {

        private static final String TEMPLATE = """
                ## Worktree Merge Status
                
                The child agent worktrees have been merged back to the parent worktree.
                Review the merge status below to determine next steps.
                
                %s
                
                If there are conflicts, you must decide how to handle them.
                Options include:
                - Emit a `MergerInterruptRequest` to pause and request human intervention
                - Emit a `MergerRequest` to trigger conflict resolution
                - Skip this agent's changes and proceed with others
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
            String mergeStatusDetails = buildMergeStatusDetails(aggregation);
            return String.format(template(), mergeStatusDetails);
        }

        @Override
        public String template() {
            return TEMPLATE;
        }

        @Override
        public int priority() {
            return 500;
        }

        /**
         * Builds the dynamic merge status details to insert into the template.
         */
        private String buildMergeStatusDetails(MergeAggregation agg) {
            StringBuilder sb = new StringBuilder();

            sb.append("**Overall Status**: ");
            sb.append(agg.allSuccessful() ? "ALL MERGED SUCCESSFULLY" : "MERGE CONFLICTS DETECTED");
            sb.append("\n\n");

            if (agg.merged() != null && !agg.merged().isEmpty()) {
                sb.append("### Successfully Merged (").append(agg.merged().size()).append(")\n");
                for (AgentMergeStatus status : agg.merged()) {
                    sb.append("- `").append(status.agentResultId()).append("`\n");
                }
                sb.append("\n");
            }

            if (agg.conflicted() != null) {
                sb.append("### Conflicted\n");
                AgentMergeStatus conflicted = agg.conflicted();
                sb.append("- `").append(conflicted.agentResultId()).append("`\n");
                if (conflicted.mergeDescriptor() != null &&
                        conflicted.mergeDescriptor().conflictFiles() != null &&
                        !conflicted.mergeDescriptor().conflictFiles().isEmpty()) {
                    sb.append("  - Conflict files:\n");
                    for (String file : conflicted.mergeDescriptor().conflictFiles()) {
                        sb.append("    - `").append(file).append("`\n");
                    }
                }
                sb.append("\n");
            }

            if (agg.pending() != null && !agg.pending().isEmpty()) {
                sb.append("### Pending (").append(agg.pending().size()).append(")\n");
                sb.append("These agents have not been merged yet (merge stopped at first conflict):\n");
                for (AgentMergeStatus status : agg.pending()) {
                    sb.append("- `").append(status.agentResultId()).append("`\n");
                }
            }

            return sb.toString();
        }
    }
}
