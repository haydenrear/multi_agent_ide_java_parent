package com.hayden.multiagentide.service;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.prompt.ContextIdService;
import com.hayden.multiagentidelib.service.RequestEnrichment;
import com.hayden.multiagentidelib.template.DiscoveryReport;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.Blackboard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestEnrichmentTest {

    @Test
    void enrichResultsRequest_assignsContextIdsToNestedAgentModels() {
        RequestEnrichment enrichment = new RequestEnrichment(new ContextIdService());

        ArtifactKey parentKey = ArtifactKey.createRoot();
        AgentModels.DiscoveryAgentRequests parent = AgentModels.DiscoveryAgentRequests.builder()
                .contextId(parentKey)
                .requests(List.of())
                .build();

        DiscoveryReport reportWithoutContext = new DiscoveryReport(
                null,
                "v1",
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "overview",
                List.of(),
                List.of(),
                java.util.Map.of(),
                List.of()
        );

        AgentModels.DiscoveryAgentResult childResultWithoutContext = AgentModels.DiscoveryAgentResult.builder()
                .contextId(null)
                .report(reportWithoutContext)
                .output("ok")
                .build();

        AgentModels.DiscoveryAgentResults request = AgentModels.DiscoveryAgentResults.builder()
                .contextId(null)
                .result(List.of(childResultWithoutContext))
                .build();

        OperationContext operationContext = mock(OperationContext.class);
        AgentProcess agentProcess = mock(AgentProcess.class);
        Blackboard blackboard = mock(Blackboard.class);
        when(operationContext.getAgentProcess()).thenReturn(agentProcess);
        when(agentProcess.getBlackboard()).thenReturn(blackboard);
        when(blackboard.last(com.hayden.multiagentidelib.agent.BlackboardHistory.class)).thenReturn(null);

        AgentModels.DiscoveryAgentResults enriched = enrichment.enrich(request, operationContext, parent);

        assertThat(enriched.contextId()).isNotNull();
        assertThat(enriched.result()).hasSize(1);
        assertThat(enriched.result().getFirst().contextId()).isNotNull();
        assertThat(enriched.result().getFirst().report()).isNotNull();
        assertThat(enriched.result().getFirst().report().contextId()).isNotNull();
    }
}
