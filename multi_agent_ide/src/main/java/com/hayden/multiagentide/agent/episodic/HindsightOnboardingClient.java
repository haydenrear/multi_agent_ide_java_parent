package com.hayden.multiagentide.agent.episodic;

import com.hayden.commitdiffcontext.git.parser.support.episodic.EpisodicMemoryAgent;
import com.hayden.commitdiffcontext.git.parser.support.episodic.model.SegmentEpisodicRequest;

public interface HindsightOnboardingClient {

    EpisodicMemoryAgent.AgentRunResult runAgent(SegmentEpisodicRequest request);
}
