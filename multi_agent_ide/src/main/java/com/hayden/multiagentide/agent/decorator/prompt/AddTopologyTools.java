package com.hayden.multiagentide.agent.decorator.prompt;

import com.hayden.multiagentide.agent.AgentTopologyTools;
import com.hayden.multiagentidelib.tool.ToolAbstraction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * Injects AgentTopologyTools (list_agents, call_agent, call_controller) into every
 * workflow agent's tool set. No profile restriction — all agents should be able
 * to discover peers and communicate via the topology.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AddTopologyTools implements LlmCallDecorator {

    private final AgentTopologyTools agentTopologyTools;

    @Override
    public int order() {
        return -9_000;
    }

    @Override
    public <T> LlmCallContext<T> decorate(LlmCallContext<T> promptContext) {
        var t = new ArrayList<>(promptContext.tcc().tools());
        t.add(ToolAbstraction.fromToolCarrier(agentTopologyTools));

        return promptContext.toBuilder()
                .tcc(
                        promptContext.tcc().toBuilder()
                                .tools(t)
                                .build())
                .build();
    }
}
