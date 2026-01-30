package com.hayden.multiagentide.artifacts;

import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentidelib.prompt.PromptContributionListener;
import com.hayden.multiagentidelib.prompt.PromptContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Listener that emits PromptContributionArtifact for each prompt contribution.
 * 
 * Captures individual contributions during prompt assembly for artifact tracking.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromptContributionArtifactListener implements PromptContributionListener {
    
    private final ExecutionScopeService executionScopeService;
    
    public void onContribution(
            PromptContext context,
            PromptContributor promptContributor
    ) {
        var promptContributorName = promptContributor.name();
        String workflowRunId = context.currentRequest().key().value();
    }

}
