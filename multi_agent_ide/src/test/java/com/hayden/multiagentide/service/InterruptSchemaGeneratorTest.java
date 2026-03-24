package com.hayden.multiagentide.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.prompt.contributor.NodeMappings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that victools schema generation produces correct filtered schemas
 * for all agent routing types. These tests run every build to catch victools
 * version changes, model field changes, or annotation issues early.
 */
class InterruptSchemaGeneratorTest {

    private InterruptSchemaGenerator generator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        generator = new InterruptSchemaGenerator();
    }

    /**
     * Non-dispatch agents have exactly one interrupt field: interruptRequest.
     */
    @ParameterizedTest(name = "interrupt schema for {0} contains only interruptRequest field")
    @EnumSource(value = AgentType.class, names = {
            "ORCHESTRATOR",
            "ORCHESTRATOR_COLLECTOR",
            "DISCOVERY_ORCHESTRATOR",
            "DISCOVERY_AGENT",
            "DISCOVERY_COLLECTOR",
            "PLANNING_ORCHESTRATOR",
            "PLANNING_AGENT",
            "PLANNING_COLLECTOR",
            "TICKET_ORCHESTRATOR",
            "TICKET_AGENT",
            "TICKET_COLLECTOR",
            "CONTEXT_MANAGER"
    })
    void interruptSchemaContainsOnlyInterruptRequestField(AgentType agentType) throws Exception {
        String schema = generator.generateInterruptSchema(agentType);

        assertThat(schema).isNotNull().isNotBlank();

        JsonNode root = objectMapper.readTree(schema);
        JsonNode properties = root.get("properties");
        assertThat(properties)
                .as("Schema for %s should have a properties object", agentType)
                .isNotNull();

        assertThat(properties.size())
                .as("Schema for %s should have exactly one property (interruptRequest), but found: %s",
                        agentType, iteratorToList(properties.fieldNames()))
                .isEqualTo(1);
        assertThat(properties.has("interruptRequest"))
                .as("Schema for %s should have 'interruptRequest' property", agentType)
                .isTrue();
    }

    /**
     * Dispatch agents have two interrupt fields: interruptRequest (own) and
     * agentInterruptRequest (bubble-up from subagent, annotated @SkipPropertyFilter).
     * Both are InterruptRequest subtypes, so both appear in the filtered schema.
     */
    @ParameterizedTest(name = "interrupt schema for dispatch {0} contains interruptRequest and agentInterruptRequest")
    @EnumSource(value = AgentType.class, names = {
            "DISCOVERY_AGENT_DISPATCH",
            "PLANNING_AGENT_DISPATCH",
            "TICKET_AGENT_DISPATCH"
    })
    void interruptSchemaForDispatchContainsBothInterruptFields(AgentType agentType) throws Exception {
        String schema = generator.generateInterruptSchema(agentType);

        assertThat(schema).isNotNull().isNotBlank();

        JsonNode root = objectMapper.readTree(schema);
        JsonNode properties = root.get("properties");
        assertThat(properties)
                .as("Schema for %s should have a properties object", agentType)
                .isNotNull();

        assertThat(properties.size())
                .as("Schema for dispatch %s should have exactly two properties, but found: %s",
                        agentType, iteratorToList(properties.fieldNames()))
                .isEqualTo(2);
        assertThat(properties.has("interruptRequest"))
                .as("Schema for %s should have 'interruptRequest' property", agentType)
                .isTrue();
        assertThat(properties.has("agentInterruptRequest"))
                .as("Schema for %s should have 'agentInterruptRequest' property", agentType)
                .isTrue();
    }

    private static <T> java.util.List<T> iteratorToList(java.util.Iterator<T> iter) {
        java.util.List<T> list = new java.util.ArrayList<>();
        iter.forEachRemaining(list::add);
        return list;
    }

    @Test
    void interruptSchemaReturnsNullForUnmappedAgentType() {
        // ALL, COMMIT_AGENT, etc. don't have routing in AGENT_TYPE_TO_ROUTING
        assertThat(generator.generateInterruptSchema(AgentType.ALL)).isNull();
        assertThat(generator.generateInterruptSchema(AgentType.COMMIT_AGENT)).isNull();
    }

    @Test
    void interruptSchemaByWireValueWorks() {
        String schema = generator.generateInterruptSchema("orchestrator");
        assertThat(schema).isNotNull().contains("interruptRequest");
    }

    @Test
    void interruptSchemaByWireValueReturnsNullForUnknown() {
        assertThat(generator.generateInterruptSchema("nonexistent-agent")).isNull();
    }

    @Test
    void interruptSchemaIsCached() {
        String first = generator.generateInterruptSchema(AgentType.ORCHESTRATOR);
        String second = generator.generateInterruptSchema(AgentType.ORCHESTRATOR);
        assertThat(first).isSameAs(second);
    }

    @Test
    void interruptSchemaIsValidJson() throws Exception {
        for (AgentType agentType : NodeMappings.AGENT_TYPE_TO_ROUTING.keySet()) {
            String schema = generator.generateInterruptSchema(agentType);
            assertThat(schema).as("Schema for %s", agentType).isNotNull();
            // Parse to verify valid JSON
            JsonNode root = objectMapper.readTree(schema);
            assertThat(root.has("properties"))
                    .as("Schema for %s should be an object schema with properties", agentType)
                    .isTrue();
        }
    }

    @Test
    void routeBackSchemaForCollectorContainsOnlyRouteBackField() throws Exception {
        // OrchestratorCollectorRouting has an OrchestratorRequest route-back field
        String schema = generator.generateRouteBackSchema(
                AgentModels.OrchestratorCollectorRouting.class,
                AgentModels.OrchestratorRequest.class
        );

        assertThat(schema).isNotNull().isNotBlank();

        JsonNode root = objectMapper.readTree(schema);
        JsonNode properties = root.get("properties");
        assertThat(properties).isNotNull();
        assertThat(properties.size())
                .as("Route-back schema should have exactly one property (orchestratorRequest)")
                .isEqualTo(1);
        assertThat(properties.has("orchestratorRequest")).isTrue();
    }

    @Test
    void routeBackSchemaForDiscoveryCollector() throws Exception {
        // DiscoveryCollectorRouting has a DiscoveryOrchestratorRequest route-back field
        String schema = generator.generateRouteBackSchema(
                AgentModels.DiscoveryCollectorRouting.class,
                AgentModels.DiscoveryOrchestratorRequest.class
        );

        assertThat(schema).isNotNull().isNotBlank();

        JsonNode root = objectMapper.readTree(schema);
        JsonNode properties = root.get("properties");
        assertThat(properties).isNotNull();
        assertThat(properties.size())
                .as("Route-back schema should have exactly one property (discoveryRequest)")
                .isEqualTo(1);
        assertThat(properties.has("discoveryRequest")).isTrue();
    }

    @Test
    void routeBackSchemaIsCached() {
        String first = generator.generateRouteBackSchema(
                AgentModels.OrchestratorCollectorRouting.class,
                AgentModels.OrchestratorRequest.class
        );
        String second = generator.generateRouteBackSchema(
                AgentModels.OrchestratorCollectorRouting.class,
                AgentModels.OrchestratorRequest.class
        );
        assertThat(first).isSameAs(second);
    }
}
