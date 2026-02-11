package com.hayden.multiagentidelib.prompt;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.PreviousContext;
import com.hayden.multiagentidelib.prompt.contributor.PreviousContextPromptContributorFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;


@Slf4j
@ExtendWith(MockitoExtension.class)
class PreviousContextPromptContributorFactoryTest {

    @Mock
    PromptContext context;

    @Test
    public void testReplaceAll() {
        PreviousContextPromptContributorFactory p = new PreviousContextPromptContributorFactory();

        Mockito.when(context.previousContext())
                .thenReturn(PreviousContext.PlanningCollectorPreviousContext.builder().contextId(ArtifactKey.createRoot()).build());
        Mockito.when(context.currentRequest())
                .thenReturn(AgentModels.DiscoveryOrchestratorRequest.builder().build());

        var created = p.create(context);
        var f = created.getFirst().contribute(context);
        log.info("{}", f);
    }

}
