package com.hayden.multiagentide.agent.episodic;

import com.hayden.commitdiffcontext.git.parser.support.episodic.EpisodicMemoryAgent;
import com.hayden.commitdiffcontext.git.parser.support.episodic.model.SegmentEpisodicRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MultiAgentIdeEpisodicMemoryAgent implements EpisodicMemoryAgent {

    private final HindsightOnboardingClient hindsightOnboardingClient;

    @Override
    public AgentRunResult runAgent(SegmentEpisodicRequest request) {
        log.debug("Episodic run-agent run={} segment={}", request.runId(), request.segmentIndex());
        return hindsightOnboardingClient.runAgent(request);
    }
}
