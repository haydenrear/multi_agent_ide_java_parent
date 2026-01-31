package com.hayden.multiagentide.embabel;

import com.embabel.agent.api.common.OperationContext;

public interface EmbabelUtil {


    /**
     * Extracts the workflow run ID from the operation context.
     */
    static String extractWorkflowRunId(OperationContext context) {
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
