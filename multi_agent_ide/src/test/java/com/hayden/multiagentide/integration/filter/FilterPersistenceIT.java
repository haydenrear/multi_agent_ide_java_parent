package com.hayden.multiagentide.integration.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.acp_cdc_ai.acp.filter.Instruction;
import com.hayden.acp_cdc_ai.acp.filter.InstructionMatcher;
import com.hayden.acp_cdc_ai.acp.filter.path.JsonPath;
import com.hayden.acp_cdc_ai.acp.filter.path.MarkdownPath;
import com.hayden.acp_cdc_ai.acp.filter.path.Path;
import com.hayden.multiagentide.filter.repository.*;
import com.hayden.multiagentide.filter.service.LayerHierarchyBootstrap;
import com.hayden.multiagentide.filter.service.LayerService;
import com.hayden.multiagentidelib.filter.model.policy.PolicyLayerBinding;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive persistence-layer integration tests for the data-layer policy filter system.
 * Uses real PostgreSQL via docker-compose (testdocker profile).
 * <p>
 * Covers:
 * - Layer hierarchy bootstrap (idempotency, parent-child, depths)
 * - Policy registration with every executor type (PYTHON, JAVA_FUNCTION, BINARY, AI)
 * - Policy registration with every filter kind (JSON_PATH, MARKDOWN_PATH, REGEX_PATH, AI_PATH)
 * - PolicyLayerBinding serialization with every MatcherKey × MatcherType × MatchOn combination
 * - FilterDecisionRecord persistence with every InstructionOp and Path type
 * - Layer binding propagation (inheritable, propagatedToParent)
 * - Policy lifecycle (activate, deactivate, toggle)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "testdocker"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FilterPersistenceIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private LayerRepository layerRepository;
    @Autowired
    private PolicyRegistrationRepository policyRegistrationRepository;
    @Autowired
    private FilterDecisionRecordRepository filterDecisionRecordRepository;
    @Autowired
    private LayerHierarchyBootstrap layerHierarchyBootstrap;
    @Autowired
    private LayerService layerService;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        filterDecisionRecordRepository.deleteAll();
        policyRegistrationRepository.deleteAll();
        layerRepository.deleteAll();
    }

    // ==================== Layer Hierarchy Bootstrap ====================

    @Test
    @Order(1)
    @Transactional
    void bootstrap_createsFullHierarchy() {
        layerHierarchyBootstrap.seedLayersIfAbsent();

        List<LayerEntity> all = layerRepository.findAll();
        // controller(1) + controller-ui-event-poll(1) + workflow-agent(1) + 22 actions
        // + 3 sub-agents + 3×3 sub-agent actions = 38
        assertThat(all).hasSize(37);

        // Root
        LayerEntity controller = layerRepository.findByLayerId("controller").orElseThrow();
        assertThat(controller.getLayerType()).isEqualTo("CONTROLLER");
        assertThat(controller.getDepth()).isEqualTo(0);
        assertThat(controller.getParentLayerId()).isNull();
        assertThat(controller.getChildLayerIds()).contains("controller-ui-event-poll", "workflow-agent");

        // UI event poll
        LayerEntity uiPoll = layerRepository.findByLayerId("controller-ui-event-poll").orElseThrow();
        assertThat(uiPoll.getLayerType()).isEqualTo("CONTROLLER_UI_EVENT_POLL");
        assertThat(uiPoll.getDepth()).isEqualTo(1);
        assertThat(uiPoll.getParentLayerId()).isEqualTo("controller");

        // Workflow agent
        LayerEntity wa = layerRepository.findByLayerId("workflow-agent").orElseThrow();
        assertThat(wa.getLayerType()).isEqualTo("WORKFLOW_AGENT");
        assertThat(wa.getDepth()).isEqualTo(1);
        assertThat(wa.getParentLayerId()).isEqualTo("controller");
        assertThat(wa.getChildLayerIds()).contains(
                "workflow-agent/coordinateWorkflow",
                "workflow-agent/performReview",
                "discovery-dispatch-subagent",
                "planning-dispatch-subagent",
                "ticket-dispatch-subagent"
        );

        // A specific action layer
        LayerEntity coordAction = layerRepository.findByLayerId("workflow-agent/coordinateWorkflow").orElseThrow();
        assertThat(coordAction.getLayerType()).isEqualTo("WORKFLOW_AGENT_ACTION");
        assertThat(coordAction.getLayerKey()).isEqualTo("coordinateWorkflow");
        assertThat(coordAction.getDepth()).isEqualTo(2);
        assertThat(coordAction.getParentLayerId()).isEqualTo("workflow-agent");

        // Sub-agent
        LayerEntity disc = layerRepository.findByLayerId("discovery-dispatch-subagent").orElseThrow();
        assertThat(disc.getLayerType()).isEqualTo("WORKFLOW_AGENT");
        assertThat(disc.getDepth()).isEqualTo(2);
        assertThat(disc.getParentLayerId()).isEqualTo("workflow-agent");
        assertThat(disc.getChildLayerIds()).containsExactlyInAnyOrder(
                "discovery-dispatch-subagent/ranDiscoveryAgent",
                "discovery-dispatch-subagent/transitionToInterruptState",
                "discovery-dispatch-subagent/runDiscoveryAgent"
        );

        // Sub-agent action
        LayerEntity discAction = layerRepository.findByLayerId("discovery-dispatch-subagent/runDiscoveryAgent").orElseThrow();
        assertThat(discAction.getLayerType()).isEqualTo("WORKFLOW_AGENT_ACTION");
        assertThat(discAction.getDepth()).isEqualTo(3);
        assertThat(discAction.getParentLayerId()).isEqualTo("discovery-dispatch-subagent");
    }

    @Test
    @Order(2)
    void bootstrap_isIdempotent() {
        layerHierarchyBootstrap.seedLayersIfAbsent();
        long countAfterFirst = layerRepository.count();

        layerHierarchyBootstrap.seedLayersIfAbsent();
        long countAfterSecond = layerRepository.count();

        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }

    @Test
    @Order(3)
    void bootstrap_layerServiceTraversal_worksWithBootstrappedHierarchy() {
        layerHierarchyBootstrap.seedLayersIfAbsent();

        // Children of workflow-agent should include actions + sub-agents
        List<LayerEntity> waChildren = layerService.getChildLayers("workflow-agent", false);
        assertThat(waChildren).hasSizeGreaterThanOrEqualTo(25); // 22 actions + 3 sub-agents

        // Descendants of controller should be everything else
        Set<String> descendants = layerService.getDescendantLayerIds("controller");
        assertThat(descendants).hasSize(36); // all except controller itself

        // Effective layers for a sub-agent action should include itself + ancestors
        List<LayerEntity> effective = layerService.getEffectiveLayers(
                "discovery-dispatch-subagent/runDiscoveryAgent");
        List<String> effectiveIds = effective.stream().map(LayerEntity::getLayerId).toList();
        assertThat(effectiveIds).contains(
                "discovery-dispatch-subagent/runDiscoveryAgent",
                "discovery-dispatch-subagent",
                "workflow-agent",
                "controller"
        );
    }

    @Test
    @Order(4)
    void bootstrap_repositoryQueries_workCorrectly() {
        layerHierarchyBootstrap.seedLayersIfAbsent();

        // findByLayerType
        List<LayerEntity> agents = layerRepository.findByLayerType("WORKFLOW_AGENT");
        assertThat(agents).hasSize(4); // workflow-agent + 3 sub-agents

        List<LayerEntity> actions = layerRepository.findByLayerType("WORKFLOW_AGENT_ACTION");
        assertThat(actions).hasSize(31); // 22 WA actions + 3×3 sub-agent actions

        // findByParentLayerId
        List<LayerEntity> controllerChildren = layerRepository.findByParentLayerId("controller");
        assertThat(controllerChildren).hasSize(2); // ui-event-poll + workflow-agent

        // findByMaxDepth
        List<LayerEntity> shallow = layerRepository.findByMaxDepth(1);
        assertThat(shallow).hasSize(3); // controller(0) + ui-event-poll(1) + workflow-agent(1)

        // findByLayerKeyPrefix
        List<LayerEntity> discoveryLayers = layerRepository.findByLayerKeyPrefix("discovery");
        assertThat(discoveryLayers).isNotEmpty();
    }

    // ==================== Policy Registration with Every Executor Type ====================

    @Test
    @Order(10)
    void policyRegistration_pythonExecutor_persistsAndDeserializes() throws Exception {
        saveLayer("test-layer", "CONTROLLER", "test", null, 0);

        Map<String, Object> executor = Map.of(
                "executorType", "PYTHON",
                "scriptPath", "/tmp/test_filter.py",
                "entryFunction", "filter",
                "runtimeArgsSchema", Map.of("verbose", "boolean"),
                "timeoutMs", 5000,
                "configVersion", "1.0"
        );

        String policyId = registerPolicyViaApi(
                "/api/filters/json-path-filters/policies",
                "python-executor-test", "test-layer", executor);

        PolicyRegistrationEntity entity = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
        assertThat(entity.getFilterKind()).isEqualTo("JSON_PATH");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");

        JsonNode filterJson = objectMapper.readTree(entity.getFilterJson());
        assertThat(filterJson.path("filterType").asText()).isEqualTo("PATH");
        assertThat(filterJson.path("name").asText()).isEqualTo("python-executor-test");

        JsonNode executorNode = filterJson.path("executor");
        assertThat(executorNode.path("executorType").asText()).isEqualTo("PYTHON");
        assertThat(executorNode.path("scriptPath").asText()).isEqualTo("/tmp/test_filter.py");
        assertThat(executorNode.path("entryFunction").asText()).isEqualTo("filter");
        assertThat(executorNode.path("timeoutMs").asInt()).isEqualTo(5000);
        assertThat(executorNode.path("runtimeArgsSchema").path("verbose").asText()).isEqualTo("boolean");
    }

    @Test
    @Order(11)
    void policyRegistration_javaFunctionExecutor_persistsAndDeserializes() throws Exception {
        saveLayer("test-layer", "CONTROLLER", "test", null, 0);

        Map<String, Object> executor = Map.of(
                "executorType", "JAVA_FUNCTION",
                "functionRef", "com.example.MyFilter",
                "className", "com.example.MyFilter",
                "methodName", "apply",
                "timeoutMs", 1000,
                "configVersion", "1.0"
        );

        String policyId = registerPolicyViaApi(
                "/api/filters/json-path-filters/policies",
                "java-function-test", "test-layer", executor);

        PolicyRegistrationEntity entity = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
        JsonNode executorNode = objectMapper.readTree(entity.getFilterJson()).path("executor");
        assertThat(executorNode.path("executorType").asText()).isEqualTo("JAVA_FUNCTION");
        assertThat(executorNode.path("functionRef").asText()).isEqualTo("com.example.MyFilter");
        assertThat(executorNode.path("className").asText()).isEqualTo("com.example.MyFilter");
        assertThat(executorNode.path("methodName").asText()).isEqualTo("apply");
    }

    @Test
    @Order(12)
    void policyRegistration_binaryExecutor_persistsAndDeserializes() throws Exception {
        saveLayer("test-layer", "CONTROLLER", "test", null, 0);

        Map<String, Object> executor = Map.of(
                "executorType", "BINARY",
                "command", List.of("/usr/bin/python3", "-m", "mymodule"),
                "workingDirectory", "/tmp",
                "env", Map.of("PYTHONPATH", "/opt/lib"),
                "outputParserRef", "json-stdout",
                "timeoutMs", 10000,
                "configVersion", "1.0"
        );

        String policyId = registerPolicyViaApi(
                "/api/filters/json-path-filters/policies",
                "binary-executor-test", "test-layer", executor);

        PolicyRegistrationEntity entity = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
        JsonNode executorNode = objectMapper.readTree(entity.getFilterJson()).path("executor");
        assertThat(executorNode.path("executorType").asText()).isEqualTo("BINARY");
        assertThat(executorNode.path("command").isArray()).isTrue();
        assertThat(executorNode.path("command").get(0).asText()).isEqualTo("/usr/bin/python3");
        assertThat(executorNode.path("workingDirectory").asText()).isEqualTo("/tmp");
        assertThat(executorNode.path("env").path("PYTHONPATH").asText()).isEqualTo("/opt/lib");
    }

    @Test
    @Order(13)
    void policyRegistration_aiExecutor_persistsAndDeserializes() throws Exception {
        saveLayer("test-layer", "CONTROLLER", "test", null, 0);

        Map<String, Object> executor = Map.of(
                "executorType", "AI",
                "modelRef", "claude-sonnet-4-5-20250929",
                "promptTemplate", "Filter the following event: {{input}}",
                "registrarPrompt", "Persisted registrar guidance",
                "sessionMode", "SAME_SESSION_FOR_AGENT",
                "maxTokens", 4096,
                "outputSchema", Map.of("type", "array"),
                "timeoutMs", 30000,
                "configVersion", "1.0"
        );

        String policyId = registerPolicyViaApi(
                "/api/filters/json-path-filters/policies",
                "ai-executor-test", "test-layer", executor);

        PolicyRegistrationEntity entity = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
        JsonNode executorNode = objectMapper.readTree(entity.getFilterJson()).path("executor");
        assertThat(executorNode.path("executorType").asText()).isEqualTo("AI");
        assertThat(executorNode.path("modelRef").asText()).isEqualTo("claude-sonnet-4-5-20250929");
        assertThat(executorNode.path("promptTemplate").asText()).contains("{{input}}");
        assertThat(executorNode.path("registrarPrompt").asText()).isEqualTo("Persisted registrar guidance");
        assertThat(executorNode.path("sessionMode").asText()).isEqualTo("SAME_SESSION_FOR_AGENT");
        assertThat(executorNode.path("maxTokens").asInt()).isEqualTo(4096);
    }

    // ==================== Policy Registration with Every Filter Kind ====================

    @Test
    @Order(20)
    void policyRegistration_jsonPathFilter_persistsCorrectFilterKind() throws Exception {
        saveLayer("test-layer", "CONTROLLER", "test", null, 0);
        String policyId = registerPolicyViaApi(
                "/api/filters/json-path-filters/policies",
                "json-path-filter", "test-layer", defaultPythonExecutor());

        PolicyRegistrationEntity entity = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
        assertThat(entity.getFilterKind()).isEqualTo("JSON_PATH");
        assertThat(objectMapper.readTree(entity.getFilterJson()).path("filterType").asText()).isEqualTo("PATH");
    }

    @Test
    @Order(21)
    void policyRegistration_markdownPathFilter_persistsCorrectFilterKind() throws Exception {
        saveLayer("test-layer", "CONTROLLER", "test", null, 0);
        String policyId = registerPolicyViaApi(
                "/api/filters/markdown-path-filters/policies",
                "markdown-path-filter", "test-layer", defaultPythonExecutor());

        PolicyRegistrationEntity entity = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
        assertThat(entity.getFilterKind()).isEqualTo("MARKDOWN_PATH");
        assertThat(objectMapper.readTree(entity.getFilterJson()).path("filterType").asText()).isEqualTo("PATH");
    }

    @Test
    @Order(22)
    void policyRegistration_regexPathFilter_persistsCorrectFilterKind() throws Exception {
        saveLayer("test-layer", "CONTROLLER", "test", null, 0);
        String policyId = registerPolicyViaApi(
                "/api/filters/regex-path-filters/policies",
                "regex-path-filter", "test-layer", defaultPythonExecutor());

        PolicyRegistrationEntity entity = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
        assertThat(entity.getFilterKind()).isEqualTo("REGEX_PATH");
        assertThat(objectMapper.readTree(entity.getFilterJson()).path("filterType").asText()).isEqualTo("PATH");
    }

    @Test
    @Order(23)
    void policyRegistration_aiPathFilter_persistsCorrectFilterKind() throws Exception {
        saveLayer("test-layer", "CONTROLLER", "test", null, 0);
        String policyId = registerPolicyViaApi(
                "/api/filters/ai-path-filters/policies",
                "ai-path-filter", "test-layer", defaultAiExecutor());

        PolicyRegistrationEntity entity = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
        assertThat(entity.getFilterKind()).isEqualTo("AI_PATH");
        assertThat(objectMapper.readTree(entity.getFilterJson()).path("filterType").asText()).isEqualTo("AI");
    }

    // ==================== PolicyLayerBinding Serialization ====================

    @Test
    @Order(30)
    void layerBinding_allMatcherCombinations_persistAndDeserialize() throws Exception {
        saveLayer("test-layer", "CONTROLLER", "test", null, 0);

        // Test every valid combination of MatcherKey × MatcherType × MatchOn
        record MatcherCombo(String matcherKey,
                            String matcherType,
                            String matchOn,
                            String matcherText) {
        }

        List<MatcherCombo> combos = List.of(
                new MatcherCombo("NAME", "REGEX", "GRAPH_EVENT", "NodeStatusChanged.*"),
                new MatcherCombo("NAME", "EQUALS", "GRAPH_EVENT", "AddMessageEvent"),
                new MatcherCombo("TEXT", "REGEX", "GRAPH_EVENT", ".*error.*"),
                new MatcherCombo("TEXT", "EQUALS", "GRAPH_EVENT", "exact payload match"),
                new MatcherCombo("NAME", "REGEX", "PROMPT_CONTRIBUTOR", "debug-.*"),
                new MatcherCombo("NAME", "EQUALS", "PROMPT_CONTRIBUTOR", "we-are-here"),
                new MatcherCombo("TEXT", "REGEX", "PROMPT_CONTRIBUTOR", ".*TODO.*"),
                new MatcherCombo("TEXT", "EQUALS", "PROMPT_CONTRIBUTOR", "exact contributor text")
        );

        for (int i = 0; i < combos.size(); i++) {
            MatcherCombo combo = combos.get(i);

            Map<String, Object> body = buildRegistrationBody(
                    "matcher-combo-" + i, "test-layer", defaultPythonExecutor());

            // Override the layer binding with this specific combo
            List<Map<String, Object>> bindings = List.of(bindingMap(
                    "test-layer", "CONTROLLER", "test",
                    combo.matcherKey(), combo.matcherType(), combo.matcherText(), combo.matchOn(),
                    false, false));
            body.put("layerBindings", bindings);

            MvcResult result = mockMvc.perform(post("/api/filters/json-path-filters/policies")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andReturn();

            String policyId = objectMapper.readTree(result.getResponse().getContentAsString())
                    .path("policyId").asText();

            // Read back and verify the binding was serialized correctly
            PolicyRegistrationEntity entity = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
            List<PolicyLayerBinding> deserializedBindings = objectMapper.readValue(
                    entity.getLayerBindingsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, PolicyLayerBinding.class));

            assertThat(deserializedBindings).isNotEmpty();
            PolicyLayerBinding binding = deserializedBindings.getFirst();
            assertThat(binding.matcherKey().name()).isEqualTo(combo.matcherKey());
            assertThat(binding.matcherType().name()).isEqualTo(combo.matcherType());
            assertThat(binding.matchOn().name()).isEqualTo(combo.matchOn());
            assertThat(binding.matcherText()).isEqualTo(combo.matcherText());
            assertThat(binding.layerId()).isEqualTo("test-layer");
            assertThat(binding.updatedBy()).isEqualTo("system");
            assertThat(binding.updatedAt()).isNotNull();
        }
    }

    @Test
    @Order(31)
    void layerBinding_inheritableFlag_propagatesToDescendants() throws Exception {
        // Build a 2-level hierarchy: parent -> child
        saveLayer("parent-layer", "CONTROLLER", "parent", null, 0);
        saveLayer("child-layer", "WORKFLOW_AGENT", "child", "parent-layer", 1);

        Map<String, Object> body = buildRegistrationBody(
                "inheritable-test", "parent-layer", defaultPythonExecutor());

        List<Map<String, Object>> bindings = List.of(bindingMap(
                "parent-layer", "CONTROLLER", "parent",
                "NAME", "REGEX", ".*", "GRAPH_EVENT",
                true, false));
        body.put("layerBindings", bindings);
        body.put("isInheritable", true);

        MvcResult result = mockMvc.perform(post("/api/filters/json-path-filters/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn();

        String policyId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("policyId").asText();

        PolicyRegistrationEntity entity = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
        List<PolicyLayerBinding> allBindings = objectMapper.readValue(
                entity.getLayerBindingsJson(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, PolicyLayerBinding.class));

        // Should have at least 2: original + propagated to child
        assertThat(allBindings).hasSizeGreaterThanOrEqualTo(2);
        Set<String> boundLayerIds = allBindings.stream()
                .map(PolicyLayerBinding::layerId)
                .collect(Collectors.toSet());
        assertThat(boundLayerIds).contains("parent-layer", "child-layer");
    }

    @Test
    @Order(32)
    void layerBinding_propagatedToParent_createsParentBinding() throws Exception {
        saveLayer("parent-layer", "CONTROLLER", "parent", null, 0);
        saveLayer("child-layer", "WORKFLOW_AGENT", "child", "parent-layer", 1);

        Map<String, Object> body = buildRegistrationBody(
                "propagate-parent-test", "child-layer", defaultPythonExecutor());

        Map<String, Object> childBinding = bindingMap(
                "child-layer", "WORKFLOW_AGENT", "child",
                "NAME", "EQUALS", "TestEvent", "GRAPH_EVENT",
                false, true);
        List<Map<String, Object>> bindings = List.of(childBinding);
        body.put("layerBindings", bindings);

        MvcResult result = mockMvc.perform(post("/api/filters/json-path-filters/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn();

        String policyId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("policyId").asText();

        PolicyRegistrationEntity entity = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
        List<PolicyLayerBinding> allBindings = objectMapper.readValue(
                entity.getLayerBindingsJson(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, PolicyLayerBinding.class));

        Set<String> boundLayerIds = allBindings.stream()
                .map(PolicyLayerBinding::layerId)
                .collect(Collectors.toSet());
        assertThat(boundLayerIds).contains("child-layer", "parent-layer");

        // Propagated binding should have propagation flags turned off
        PolicyLayerBinding parentBinding = allBindings.stream()
                .filter(b -> "parent-layer".equals(b.layerId()))
                .findFirst().orElseThrow();
        assertThat(parentBinding.isInheritable()).isFalse();
        assertThat(parentBinding.isPropagatedToParent()).isFalse();
        assertThat(parentBinding.updatedBy()).isEqualTo("propagation");
    }

    // ==================== FilterDecisionRecord with Every Instruction Type ====================

    @Test
    @Order(40)
    void filterDecisionRecord_allInstructionOps_withJsonPath_persistAndDeserialize() throws Exception {
        List<Instruction> instructions = List.of(
                new Instruction.Replace(new JsonPath("$.field"), "new-value", 1),
                new Instruction.Set(new JsonPath("$.newField"), "set-value", 2),
                new Instruction.Remove(new JsonPath("$.removeMe"), 3),
                new Instruction.ReplaceIfMatch(new JsonPath("$.regexField"), new InstructionMatcher(FilterEnums.MatcherType.REGEX, "token-\\d+"), "masked", 4),
                new Instruction.RemoveIfMatch(new JsonPath("$.secretField"), new InstructionMatcher(FilterEnums.MatcherType.REGEX, "SECRET"), 5),
                new Instruction.ReplaceIfMatch(new JsonPath("$.matchField"),
                        new InstructionMatcher(FilterEnums.MatcherType.EQUALS, "MATCH_THIS"), "replaced", 6),
                new Instruction.RemoveIfMatch(new JsonPath("$.deleteField"),
                        new InstructionMatcher(FilterEnums.MatcherType.REGEX, "DELETE_.*"), 7)
        );

        // Must use writerFor with TypeReference so Jackson includes the @JsonTypeInfo discriminator
        String instructionsJson = objectMapper.writerFor(new TypeReference<List<Instruction>>() {
                })
                .writeValueAsString(instructions);

        FilterDecisionRecordEntity entity = FilterDecisionRecordEntity.builder()
                .decisionId("dec-" + UUID.randomUUID())
                .policyId("policy-test")
                .filterType("JSON_PATH")
                .layerId("test-layer")
                .action(FilterEnums.FilterAction.TRANSFORMED.name())
                .inputJson("{\"field\": \"old\"}")
                .outputJson("{\"field\": \"new-value\", \"newField\": \"set-value\"}")
                .appliedInstructionsJson(instructionsJson)
                .createdAt(Instant.now())
                .build();

        filterDecisionRecordRepository.save(entity);

        // Read back
        FilterDecisionRecordEntity saved = filterDecisionRecordRepository
                .findByDecisionId(entity.getDecisionId()).getFirst();

        assertThat(saved.getAction()).isEqualTo("TRANSFORMED");
        assertThat(saved.getErrorMessage()).isNull();

        // Deserialize instructions back
        List<Instruction> deserialized = objectMapper.readValue(
                saved.getAppliedInstructionsJson(),
                new TypeReference<List<Instruction>>() {
                });

        assertThat(deserialized).hasSize(7);

        // Verify each instruction type round-trips
        assertThat(deserialized.get(0)).isInstanceOf(Instruction.Replace.class);
        Instruction.Replace replace = (Instruction.Replace) deserialized.get(0);
        assertThat(replace.targetPath()).isInstanceOf(JsonPath.class);
        assertThat(replace.targetPath().expression()).isEqualTo("$.field");
        assertThat(replace.value().toString()).isEqualTo("new-value");
        assertThat(replace.order()).isEqualTo(1);

        assertThat(deserialized.get(1)).isInstanceOf(Instruction.Set.class);
        assertThat(deserialized.get(2)).isInstanceOf(Instruction.Remove.class);

        assertThat(deserialized.get(5)).isInstanceOf(Instruction.ReplaceIfMatch.class);
        Instruction.ReplaceIfMatch rim = (Instruction.ReplaceIfMatch) deserialized.get(5);
        assertThat(rim.matcher().matcherType()).isEqualTo(FilterEnums.MatcherType.EQUALS);
        assertThat(rim.matcher().value()).isEqualTo("MATCH_THIS");

        assertThat(deserialized.get(6)).isInstanceOf(Instruction.RemoveIfMatch.class);
        Instruction.RemoveIfMatch rmim = (Instruction.RemoveIfMatch) deserialized.get(6);
        assertThat(rmim.matcher().matcherType()).isEqualTo(FilterEnums.MatcherType.REGEX);
    }

    @Test
    @Order(41)
    void filterDecisionRecord_allInstructionOps_withMarkdownPath_persistAndDeserialize() throws Exception {
        List<Instruction> instructions = List.of(
                new Instruction.Replace(new MarkdownPath("## Section"), "new content", 1),
                new Instruction.Set(new MarkdownPath("## New Section"), "set content", 2),
                new Instruction.Remove(new MarkdownPath("#"), 3),
                new Instruction.ReplaceIfMatch(new MarkdownPath("## Regex Section"), new InstructionMatcher(FilterEnums.MatcherType.REGEX, "abc-\\d+"), "masked", 4),
                new Instruction.ReplaceIfMatch(new MarkdownPath("## Secret Section"), new InstructionMatcher(FilterEnums.MatcherType.REGEX, "SECRET"), "replaced", 5),
                new Instruction.ReplaceIfMatch(new MarkdownPath("## Match Section"),
                        new InstructionMatcher(FilterEnums.MatcherType.REGEX, "MATCH.*"), "replaced", 6),
                new Instruction.RemoveIfMatch(new MarkdownPath("## Delete Section"),
                        new InstructionMatcher(FilterEnums.MatcherType.EQUALS, "DELETE_ME"), 7)
        );

        String instructionsJson = objectMapper.writerFor(new TypeReference<List<Instruction>>() {
                })
                .writeValueAsString(instructions);

        FilterDecisionRecordEntity entity = FilterDecisionRecordEntity.builder()
                .decisionId("dec-md-" + UUID.randomUUID())
                .policyId("policy-md-test")
                .filterType("MARKDOWN_PATH")
                .layerId("test-layer")
                .action(FilterEnums.FilterAction.TRANSFORMED.name())
                .inputJson("# Report\n## Section\nold content")
                .outputJson("# Report\n## Section\nnew content")
                .appliedInstructionsJson(instructionsJson)
                .createdAt(Instant.now())
                .build();

        filterDecisionRecordRepository.save(entity);

        FilterDecisionRecordEntity saved = filterDecisionRecordRepository
                .findByDecisionId(entity.getDecisionId()).getFirst();

        List<Instruction> deserialized = objectMapper.readValue(
                saved.getAppliedInstructionsJson(),
                new TypeReference<List<Instruction>>() {
                });

        assertThat(deserialized).hasSize(7);

        // All paths should be MarkdownPath
        for (Instruction instr : deserialized) {
            assertThat(instr.targetPath()).isInstanceOf(MarkdownPath.class);
            assertThat(instr.targetPath().pathType()).isEqualTo(FilterEnums.PathType.MARKDOWN_PATH);
        }

        assertThat(deserialized.get(0).targetPath().expression()).isEqualTo("## Section");
        assertThat(deserialized.get(2).targetPath().expression()).isEqualTo("#"); // root
    }

    @Test
    @Order(42)
    void filterDecisionRecord_allFilterActions_persist() {
        for (FilterEnums.FilterAction action : FilterEnums.FilterAction.values()) {
            FilterDecisionRecordEntity entity = FilterDecisionRecordEntity.builder()
                    .decisionId("dec-action-" + action.name() + "-" + UUID.randomUUID())
                    .policyId("policy-action-test")
                    .filterType("JSON_PATH")
                    .layerId("test-layer")
                    .action(action.name())
                    .inputJson("{}")
                    .outputJson(action == FilterEnums.FilterAction.DROPPED ? null : "{}")
                    .appliedInstructionsJson("[]")
                    .errorMessage(action == FilterEnums.FilterAction.ERROR ? "Test error message" : null)
                    .createdAt(Instant.now())
                    .build();

            filterDecisionRecordRepository.save(entity);

            FilterDecisionRecordEntity saved = filterDecisionRecordRepository
                    .findByDecisionId(entity.getDecisionId()).getFirst();
            assertThat(saved.getAction()).isEqualTo(action.name());
        }
    }

    @Test
    @Order(43)
    void filterDecisionRecord_largePayloads_persist() {
        // Test that TEXT columns can handle large JSON
        String largeInput = "{\"data\": \"" + "x".repeat(50_000) + "\"}";
        String largeOutput = "{\"data\": \"" + "y".repeat(50_000) + "\"}";

        FilterDecisionRecordEntity entity = FilterDecisionRecordEntity.builder()
                .decisionId("dec-large-" + UUID.randomUUID())
                .policyId("policy-large-test")
                .filterType("JSON_PATH")
                .layerId("test-layer")
                .action("PASSTHROUGH")
                .inputJson(largeInput)
                .outputJson(largeOutput)
                .appliedInstructionsJson("[]")
                .createdAt(Instant.now())
                .build();

        filterDecisionRecordRepository.save(entity);

        FilterDecisionRecordEntity saved = filterDecisionRecordRepository
                .findByDecisionId(entity.getDecisionId()).getFirst();
        assertThat(saved.getInputJson()).hasSize(largeInput.length());
        assertThat(saved.getOutputJson()).hasSize(largeOutput.length());
    }

    // ==================== Policy Lifecycle ====================

    @Test
    @Order(50)
    void policyLifecycle_activate_deactivate_toggle() throws Exception {
        saveLayer("lifecycle-layer", "CONTROLLER", "lifecycle", null, 0);

        String policyId = registerPolicyViaApi(
                "/api/filters/json-path-filters/policies",
                "lifecycle-test", "lifecycle-layer", defaultPythonExecutor());

        // Should be ACTIVE after registration
        PolicyRegistrationEntity active = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
        assertThat(active.getStatus()).isEqualTo("ACTIVE");
        assertThat(active.getActivatedAt()).isNotNull();
        assertThat(active.getDeactivatedAt()).isNull();

        // Deactivate
        mockMvc.perform(post("/api/filters/policies/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "policyId", policyId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        PolicyRegistrationEntity deactivated = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
        assertThat(deactivated.getStatus()).isEqualTo("INACTIVE");
        assertThat(deactivated.getDeactivatedAt()).isNotNull();

        // Toggle: disable on layer
        mockMvc.perform(post("/api/filters/policies/layers/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "policyId", policyId,
                                "layerId", "lifecycle-layer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        PolicyRegistrationEntity toggled = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
        List<PolicyLayerBinding> bindings = objectMapper.readValue(
                toggled.getLayerBindingsJson(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, PolicyLayerBinding.class));
        PolicyLayerBinding layerBinding = bindings.stream()
                .filter(b -> "lifecycle-layer".equals(b.layerId()))
                .findFirst().orElseThrow();
        assertThat(layerBinding.enabled()).isFalse();

        // Toggle: re-enable on layer
        mockMvc.perform(post("/api/filters/policies/layers/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "policyId", policyId,
                                "layerId", "lifecycle-layer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        PolicyRegistrationEntity reEnabled = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
        List<PolicyLayerBinding> reBindings = objectMapper.readValue(
                reEnabled.getLayerBindingsJson(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, PolicyLayerBinding.class));
        PolicyLayerBinding reLayerBinding = reBindings.stream()
                .filter(b -> "lifecycle-layer".equals(b.layerId()))
                .findFirst().orElseThrow();
        assertThat(reLayerBinding.enabled()).isTrue();
    }

    @Test
    @Order(51)
    void policyRegistration_inactiveOnCreation_whenActivateFalse() throws Exception {
        saveLayer("test-layer", "CONTROLLER", "test", null, 0);

        Map<String, Object> body = buildRegistrationBody(
                "inactive-test", "test-layer", defaultPythonExecutor());
        body.put("activate", false);

        MvcResult result = mockMvc.perform(post("/api/filters/json-path-filters/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn();

        String policyId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("policyId").asText();

        PolicyRegistrationEntity entity = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo("INACTIVE");
        assertThat(entity.getActivatedAt()).isNull();
    }

    // ==================== Repository Query Methods ====================

    @Test
    @Order(60)
    void policyRepositoryQueries_findByStatusAndFilterKind() throws Exception {
        saveLayer("test-layer", "CONTROLLER", "test", null, 0);

        registerPolicyViaApi("/api/filters/json-path-filters/policies",
                "active-json", "test-layer", defaultPythonExecutor());
        registerPolicyViaApi("/api/filters/markdown-path-filters/policies",
                "active-md", "test-layer", defaultPythonExecutor());

        // Register one inactive
        Map<String, Object> inactiveBody = buildRegistrationBody(
                "inactive-json", "test-layer", defaultPythonExecutor());
        inactiveBody.put("activate", false);
        mockMvc.perform(post("/api/filters/json-path-filters/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inactiveBody)))
                .andExpect(status().isOk());

        assertThat(policyRegistrationRepository.findByStatus("ACTIVE")).hasSize(2);
        assertThat(policyRegistrationRepository.findByStatus("INACTIVE")).hasSize(1);
        assertThat(policyRegistrationRepository.findByFilterKind("JSON_PATH")).hasSize(2);
        assertThat(policyRegistrationRepository.findByFilterKind("MARKDOWN_PATH")).hasSize(1);
        assertThat(policyRegistrationRepository.findByStatusAndFilterKind("ACTIVE", "JSON_PATH")).hasSize(1);
        assertThat(policyRegistrationRepository.findActiveByLayerId("ACTIVE", "test-layer")).hasSize(2);
    }

    @Test
    @Order(61)
    void decisionRepositoryQueries_findByPolicyAndLayer() {
        Instant now = Instant.now();
        String policyId = "policy-query-test";
        String layerId = "layer-query-test";

        for (int i = 0; i < 5; i++) {
            filterDecisionRecordRepository.save(FilterDecisionRecordEntity.builder()
                    .decisionId("dec-q-" + i + "-" + UUID.randomUUID())
                    .policyId(policyId)
                    .filterType("JSON_PATH")
                    .layerId(layerId)
                    .action("PASSTHROUGH")
                    .inputJson("{}")
                    .outputJson("{}")
                    .appliedInstructionsJson("[]")
                    .createdAt(now.minusSeconds(i))
                    .build());
        }

        // Add one for a different layer
        filterDecisionRecordRepository.save(FilterDecisionRecordEntity.builder()
                .decisionId("dec-q-other-" + UUID.randomUUID())
                .policyId(policyId)
                .filterType("JSON_PATH")
                .layerId("other-layer")
                .action("DROPPED")
                .inputJson("{}")
                .appliedInstructionsJson("[]")
                .createdAt(now)
                .build());

        var byPolicyAndLayer = filterDecisionRecordRepository
                .findByPolicyIdAndLayerIdOrderByCreatedAtDesc(policyId, layerId,
                        org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(byPolicyAndLayer).hasSize(5);
        // Should be ordered by createdAt desc
        assertThat(byPolicyAndLayer.getFirst().getCreatedAt())
                .isAfterOrEqualTo(byPolicyAndLayer.getLast().getCreatedAt());

        var byPolicyOnly = filterDecisionRecordRepository
                .findByPolicyIdOrderByCreatedAtDesc(policyId,
                        org.springframework.data.domain.PageRequest.of(0, 100));
        assertThat(byPolicyOnly).hasSize(6);
    }

    // ==================== Layer Entity Edge Cases ====================

    @Test
    @Order(70)
    void layerEntity_metadataJson_persistsAndDeserializes() {
        Map<String, Object> metadata = Map.of(
                "agentClass", "com.hayden.multiagentide.agent.AgentInterfaces$WorkflowAgent",
                "registeredAt", Instant.now().toString(),
                "tags", List.of("production", "v2")
        );

        LayerEntity entity = LayerEntity.builder()
                .layerId("metadata-test-layer")
                .layerType("WORKFLOW_AGENT")
                .layerKey("metadata-test")
                .depth(0)
                .metadataJson(assertDoesNotThrow(() -> objectMapper.writeValueAsString(metadata)))
                .build();
        layerRepository.save(entity);

        LayerEntity saved = layerRepository.findByLayerId("metadata-test-layer").orElseThrow();
        assertThat(saved.getMetadataJson()).isNotBlank();

        Map<String, Object> deserializedMeta = assertDoesNotThrow(() ->
                objectMapper.readValue(saved.getMetadataJson(), new TypeReference<>() {
                }));
        assertThat(deserializedMeta).containsKey("agentClass");
        assertThat(deserializedMeta).containsKey("tags");
    }

    @Test
    @Order(71)
    @Transactional
    void layerEntity_childLayerIds_elementCollection_persistsAndRetrieves() {
        LayerEntity parent = LayerEntity.builder()
                .layerId("parent-ec-test")
                .layerType("CONTROLLER")
                .layerKey("parent-ec")
                .depth(0)
                .build();
        parent.getChildLayerIds().addAll(List.of("child-1", "child-2", "child-3"));
        layerRepository.saveAndFlush(parent);
        entityManager.clear(); // force re-read from DB

        LayerEntity saved = layerRepository.findByLayerId("parent-ec-test").orElseThrow();
        assertThat(saved.getChildLayerIds()).containsExactly("child-1", "child-2", "child-3");
    }

    @Test
    @Order(72)
    void layerEntity_uniqueConstraint_layerId() {
        saveLayer("unique-test", "CONTROLLER", "unique", null, 0);

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            saveLayer("unique-test", "CONTROLLER", "unique-dup", null, 0);
            layerRepository.flush();
        });
    }

    // ==================== Helpers ====================

    private static <T> T assertDoesNotThrow(java.util.concurrent.Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            throw new AssertionError("Expected no exception but got: " + e.getMessage(), e);
        }
    }

    private void saveLayer(String layerId, String layerType, String layerKey, String parentLayerId, int depth) {
        layerRepository.save(LayerEntity.builder()
                .layerId(layerId)
                .layerType(layerType)
                .layerKey(layerKey)
                .parentLayerId(parentLayerId)
                .depth(depth)
                .build());
    }

    private Map<String, Object> defaultPythonExecutor() {
        return Map.of(
                "executorType", "PYTHON",
                "scriptPath", "/tmp/test_filter.py",
                "entryFunction", "filter",
                "runtimeArgsSchema", Map.of(),
                "timeoutMs", 5000,
                "configVersion", "1.0"
        );
    }

    private Map<String, Object> defaultAiExecutor() {
        return Map.of(
                "executorType", "AI",
                "modelRef", "claude-sonnet-4-5-20250929",
                "promptTemplate", "Filter input: {{input}}",
                "registrarPrompt", "Default registrar guidance",
                "sessionMode", "PER_INVOCATION",
                "timeoutMs", 30000,
                "maxTokens", 2048,
                "configVersion", "1.0"
        );
    }

    private Map<String, Object> bindingMap(String layerId, String layerType, String layerKey,
                                           String matcherKey, String matcherType,
                                           String matcherText, String matchOn,
                                           boolean isInheritable, boolean isPropagatedToParent) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("layerId", layerId);
        binding.put("layerType", layerType);
        binding.put("layerKey", layerKey);
        binding.put("enabled", true);
        binding.put("includeDescendants", isInheritable);
        binding.put("isInheritable", isInheritable);
        binding.put("isPropagatedToParent", isPropagatedToParent);
        binding.put("matcherKey", matcherKey);
        binding.put("matcherType", matcherType);
        binding.put("matcherText", matcherText);
        binding.put("matchOn", matchOn);
        return binding;
    }

    private Map<String, Object> buildRegistrationBody(String name, String layerId,
                                                      Map<String, Object> executor) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", "Integration test policy " + name);
        body.put("sourcePath", "test/" + name + ".json");
        body.put("priority", 1);
        body.put("isInheritable", false);
        body.put("isPropagatedToParent", false);
        body.put("activate", true);
        body.put("executor", executor);
        body.put("layerBindings", List.of(bindingMap(
                layerId, "CONTROLLER", "test",
                "NAME", "EQUALS", "TestEvent", "GRAPH_EVENT",
                false, false)));
        return body;
    }

    private String registerPolicyViaApi(String endpoint, String name, String layerId,
                                        Map<String, Object> executor) throws Exception {
        Map<String, Object> body = buildRegistrationBody(name, layerId, executor);

        MvcResult result = mockMvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("policyId").asText();
    }
}
