package com.hayden.multiagentidelib.prompt;

import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import org.springframework.stereotype.Service;

@Service
public class ContextIdService {

    public ArtifactKey generate(String workflowRunId, AgentType agentType, Artifact.AgentModel parent) {
        if (parent != null && parent.key() != null) {
            return parent.key().createChild();
        }

        if (workflowRunId != null && ArtifactKey.isValid(workflowRunId)) {
            return new ArtifactKey(workflowRunId).createChild();
        }

        return ArtifactKey.createRoot();
    }

    public ArtifactKey generate(String workflowRunId, AgentType agentType) {
        return ArtifactKey.createRoot();
    }

}
