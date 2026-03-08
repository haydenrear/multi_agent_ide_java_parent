package com.hayden.multiagentide.embabel;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;

public interface EmbabelUtil {


    /**
     * Extracts the workflow run ID from the operation context.
     */
    static String extractWorkflowRunId(OperationContext context) {
        try {
            return new ArtifactKey(context.getAgentProcess().getId())
                    .value();
        } catch (Exception e) {
            if (context == null || context.getProcessContext() == null) {
                return null;
            }
            var options = context.getProcessContext().getProcessOptions();
            if (options == null) {
                return null;
            }
            return options.getContextIdString();
        }
    }
}
