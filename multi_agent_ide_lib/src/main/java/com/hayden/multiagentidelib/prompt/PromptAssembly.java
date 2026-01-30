package com.hayden.multiagentidelib.prompt;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles prompts from a base prompt and contributions from registered contributors.
 * 
 * Supports listeners for observability and artifact emission.
 */
@Component
@RequiredArgsConstructor
public class PromptAssembly {

    private final PromptContributorRegistry registry;
    private final List<PromptContributionListener> listeners;


    /**
     * Adds a listener for prompt contribution events.
     */
    public void addListener(PromptContributionListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    private void notifyContribution(
            PromptContext context,
            PromptContributor promptContributor
    ) {
        for (PromptContributionListener listener : listeners) {
            try {
                listener.onContribution(
                        context,
                        promptContributor);
            } catch (Exception e) {
                // Don't let listener exceptions break assembly
            }
        }
    }
    
}
