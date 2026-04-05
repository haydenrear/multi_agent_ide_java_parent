package com.hayden.multiagentide.agent;

import com.embabel.common.ai.converters.FilteringJacksonOutputConverter;
import com.embabel.common.ai.converters.JacksonPropertyFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class JacksonSchemaTest {

    @Test
    void doTest() {
        var f = new FilteringJacksonOutputConverter<>(
                AgentModels.ContextManagerResultRouting.class,
                new ObjectMapper(),
                new JacksonPropertyFilter.SkipAnnotation(SkipPropertyFilter.class));

        var schema = f.getJsonSchema();

        log.info("{}", schema);

        var o = new FilteringJacksonOutputConverter<>(
                AgentModels.OrchestratorRouting.class,
                new ObjectMapper(),
                new JacksonPropertyFilter.SkipAnnotation(SkipPropertyFilter.class));

        schema = o.getJsonSchema();

        log.info("{}", schema);
    }

}
