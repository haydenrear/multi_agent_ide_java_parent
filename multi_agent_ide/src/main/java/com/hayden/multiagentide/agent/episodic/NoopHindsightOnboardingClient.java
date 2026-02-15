package com.hayden.multiagentide.agent.episodic;

import com.hayden.commitdiffcontext.git.parser.support.episodic.EpisodicMemoryAgent;
import com.hayden.commitdiffcontext.git.parser.support.episodic.model.SegmentEpisodicRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NoopHindsightOnboardingClient implements HindsightOnboardingClient {

    @Override
    public EpisodicMemoryAgent.AgentRunResult runAgent(SegmentEpisodicRequest request) {
        String summary = "Processed segment %d (%d commits)".formatted(
                request.segmentIndex(),
                request.commitHashes().size()
        );
        return new EpisodicMemoryAgent.AgentRunResult(
                Math.min(request.maxEpisodes(), request.commitHashes().size()),
                summary,
                Map.of("agent", "noop")
        );
    }
}
