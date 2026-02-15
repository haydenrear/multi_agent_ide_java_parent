package com.hayden.multiagentide.agent.episodic;

import com.hayden.commitdiffcontext.git.parser.support.episodic.EpisodicMemoryAgent;
import com.hayden.commitdiffcontext.git.parser.support.episodic.model.SegmentEpisodicRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class EpisodicMemoryAgentContractTest {

    @Test
    void multiAgentIdeImplementationDelegatesToHindsightClient() {
        HindsightOnboardingClient client = Mockito.mock(HindsightOnboardingClient.class);
        MultiAgentIdeEpisodicMemoryAgent agent = new MultiAgentIdeEpisodicMemoryAgent(client);
        var request = SegmentEpisodicRequest.builder()
                .runId("run-1")
                .repoRootPath("/tmp/repo")
                .repoPath("/tmp/repo")
                .branch("main")
                .segmentIndex(1)
                .commitHashes(List.of("c1", "c2"))
                .maxEpisodes(3)
                .dryRun(false)
                .provenance(Map.of())
                .build();

        var run = new EpisodicMemoryAgent.AgentRunResult(2, "handoff", Map.of("k", "v"));
        when(client.runAgent(request)).thenReturn(run);

        var response = agent.runAgent(request);

        assertThat(response).isEqualTo(run);
        Mockito.verify(client).runAgent(request);
    }
}
