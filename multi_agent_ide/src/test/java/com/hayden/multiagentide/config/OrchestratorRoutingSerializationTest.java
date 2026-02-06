package com.hayden.multiagentide.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrchestratorRouting serialization")
class OrchestratorRoutingSerializationTest {

    private final ObjectMapper objectMapper = buildObjectMapper();

    @Test
    @DisplayName("serializes and deserializes OrchestratorRouting with collector request")
    void orchestratorRoutingRoundTrip() throws Exception {
        AgentModels.OrchestratorCollectorRequest collectorRequest = AgentModels.OrchestratorCollectorRequest.builder()
                .contextId(ArtifactKey.createRoot())
                .goal("Finalize workflow")
                .phase("COMPLETE")
                .build();

        AgentModels.OrchestratorRouting routing = AgentModels.OrchestratorRouting.builder()
                .collectorRequest(collectorRequest)
                .build();

        String json = objectMapper.writeValueAsString(routing);

        assertThat(json).contains("\"collectorRequest\"");
        assertThat(json).contains("Finalize workflow");

        AgentModels.OrchestratorRouting roundTrip = objectMapper.readValue(
                json,
                AgentModels.OrchestratorRouting.class
        );

        assertThat(roundTrip.collectorRequest()).isNotNull();
        assertThat(roundTrip.collectorRequest().goal()).isEqualTo("Finalize workflow");
        assertThat(roundTrip.collectorRequest().phase()).isEqualTo("COMPLETE");
    }

    private static ObjectMapper buildObjectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new SerdesConfiguration().artifactSerdesCustomizer().customize(builder);
        var b = builder.build();

        var ptv = BasicPolymorphicTypeValidator.builder()
                // IMPORTANT: restrict this to your packages/types
                .allowIfSubType("com.hayden")
                .build();

        b.activateDefaultTypingAsProperty(
                ptv,
                ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE,
                "@class"
        );
        return b;
    }
}
