package com.hayden.multiagentide.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrchestratorCollectorCommitPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || !(context.currentRequest() instanceof AgentModels.CommitAgentRequest request)) {
            return List.of();
        }
        if (!isOrchestratorCollectorCommit(request)) {
            return List.of();
        }
        return List.of(new OrchestratorCollectorCommitPromptContributor());
    }

    private boolean isOrchestratorCollectorCommit(AgentModels.CommitAgentRequest request) {
        if (request.sourceAgentType() == AgentType.ORCHESTRATOR_COLLECTOR) {
            return true;
        }
        return request.routedFromRequest() instanceof AgentModels.OrchestratorCollectorRequest;
    }

    private record OrchestratorCollectorCommitPromptContributor() implements PromptContributor {

        @Override
        public String name() {
            return "commit-agent-orchestrator-collector-submodule-pointer-v1";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            return template();
        }

        @Override
        public String template() {
            return """
                    ## Orchestrator Collector Commit Requirements

                    This commit run is preparing final merge-to-source output.

                    Required order:
                    1) Commit all dirty submodule repositories first.
                    2) After submodule commits, stage and commit parent repository submodule pointers.
                    3) Repeat pointer commits up the tree until the main repository pointer state is committed.

                    Do not return until git status is clean for the full sandbox, including parent pointer updates.
                    """;
        }

        @Override
        public int priority() {
            return 75;
        }
    }
}
