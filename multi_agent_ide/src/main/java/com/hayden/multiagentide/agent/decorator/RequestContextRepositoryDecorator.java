package com.hayden.multiagentide.agent.decorator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.model.worktree.WorktreeSandboxContext;
import com.hayden.acp_cdc_ai.repository.RequestContext;
import com.hayden.acp_cdc_ai.repository.RequestContextRepository;
import com.hayden.utilitymodule.stream.StreamUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class RequestContextRepositoryDecorator implements RequestDecorator {

    private final RequestContextRepository requestContextRepository;

    @Override
    public int order() {
        return 30_000;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        if (request == null || request.contextId() == null) {
            return request;
        }

        String sessionId = request.contextId().value();

        RequestContext updated = requestContextRepository.findBySessionId(sessionId)
                .orElse(RequestContext.builder().sessionId(sessionId).build());

        WorktreeSandboxContext worktree = request.worktreeContext();

        if (worktree != null) {
            updated = updated.withSandboxContext(worktree.sandboxContext());
        }

        requestContextRepository.save(updated);
        return request;
    }

}
