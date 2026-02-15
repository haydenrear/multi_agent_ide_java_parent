package com.hayden.multiagentide.agent;

import com.embabel.common.ai.converters.FilteringJacksonOutputConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.SkipPropertyFilter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class JacksonSchemaTest {

    @Test
    void doTest() {
        var f = new FilteringJacksonOutputConverter<>(
                AgentModels.ContextManagerResultRouting.class,
                new ObjectMapper(),
                new FilteringJacksonOutputConverter.JacksonPropertyFilter.SkipAnnotation(SkipPropertyFilter.class));

        var schema = f.getJsonSchema();

        log.info("{}", schema);
    }

}
