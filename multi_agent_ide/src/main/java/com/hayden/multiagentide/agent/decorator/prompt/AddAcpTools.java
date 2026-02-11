package com.hayden.multiagentide.agent.decorator.prompt;

import com.hayden.multiagentide.agent.AcpTooling;
import com.hayden.multiagentide.tool.ToolAbstraction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("goose")
public class AddAcpTools implements LlmCallDecorator {

    @Autowired(required = false)
    private AcpTooling tools;

    @Override
    public int order() {
        return -10_000;
    }

    @Override
    public LlmCallContext decorate(LlmCallContext promptContext) {
        var t = new ArrayList<>(promptContext.tcc().tools());

        t.add(ToolAbstraction.fromToolCarrier(tools));

        return promptContext.toBuilder()
                .tcc(
                        promptContext.tcc().toBuilder()
                                .tools(t)
                                .build())
                .build();
    }

}
