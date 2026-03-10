package com.hayden.multiagentide.prompt.contributor;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.prompt.PromptContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InterruptPromptContributorFactoryTest {

    private final InterruptPromptContributorFactory factory = new InterruptPromptContributorFactory();

    @Test
    void create_returnsResolutionContributor_whenModelIsNull() {
        PromptContext context = PromptContext.builder()
                .agentType(AgentType.ORCHESTRATOR)
                .currentContextId(ArtifactKey.createRoot())
                .currentRequest(interruptRequest())
                .build();

        assertThat(factory.create(context))
                .extracting(contributor -> contributor.name())
                .containsExactly("interrupt-review-resolution");
    }

    @Test
    void create_returnsReviewContributor_whenInterruptFeedbackIsPresent() {
        PromptContext context = PromptContext.builder()
                .agentType(AgentType.ORCHESTRATOR)
                .currentContextId(ArtifactKey.createRoot())
                .currentRequest(interruptRequest())
                .model(Map.of("interruptFeedback", "approved"))
                .build();

        assertThat(factory.create(context))
                .extracting(contributor -> contributor.name())
                .containsExactly("interrupt-review-guidance");
    }

    private AgentModels.InterruptRequest.OrchestratorInterruptRequest interruptRequest() {
        return AgentModels.InterruptRequest.OrchestratorInterruptRequest.builder()
                .contextId(ArtifactKey.createRoot())
                .type(Events.InterruptType.AGENT_REVIEW)
                .reason("Need review")
                .choices(List.of())
                .confirmationItems(List.of())
                .contextForDecision("details")
                .goal("goal")
                .build();
    }
}
