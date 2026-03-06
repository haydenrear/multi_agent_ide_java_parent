package com.hayden.multiagentide.integration.filter;

import com.embabel.agent.api.common.ContextualPromptElement;
import com.embabel.agent.api.common.OperationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.acp.filter.Instruction;
import com.hayden.multiagentide.filter.integration.PathFilterIntegration;
import com.hayden.multiagentide.filter.repository.FilterDecisionRecordEntity;
import com.hayden.multiagentide.filter.repository.FilterDecisionRecordRepository;
import com.hayden.multiagentide.filter.repository.LayerEntity;
import com.hayden.multiagentide.filter.repository.LayerRepository;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationRepository;
import com.hayden.multiagentide.repository.EventStreamRepository;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.filter.model.FilterSource;
import com.hayden.multiagentidelib.filter.model.layer.DefaultPathFilterContext;
import com.hayden.multiagentidelib.filter.model.layer.GraphEventObjectContext;
import com.hayden.multiagentidelib.filter.model.layer.PromptContributorContext;
import com.hayden.multiagentidelib.llm.LlmRunner;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorAdapterFactory;
import com.hayden.multiagentidelib.tool.ToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "testdocker"})
class FilterPolicyInfrastructureIT {

    private static final String CONTROLLER_ID = "LlmDebugUiController";

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
    private EventStreamRepository eventStreamRepository;

    @Autowired
    private PromptContributorAdapterFactory promptContributorAdapterFactory;

    @Autowired
    private PathFilterIntegration pathFilterIntegration;

    @MockitoBean
    private LlmRunner llmRunner;

    @BeforeEach
    void setUp() {
        filterDecisionRecordRepository.deleteAll();
        policyRegistrationRepository.deleteAll();
        layerRepository.deleteAll();
    }

    @Test
    void controllerLayer_registeredPolicies_areAppliedWhenViewingEvents() throws Exception {
        String nodeId = ArtifactKey.createRoot().value();
        String controllerLayerId = "layer-controller-" + UUID.randomUUID();

        saveLayer(controllerLayerId, "CONTROLLER", CONTROLLER_ID, null, 0);

        String eventPathPolicyIdA = registerPolicy(
                "/api/filters/json-path-filters/policies",
                "controller-event-json-path",
                controllerLayerId,
                "CONTROLLER",
                CONTROLLER_ID,
                "GRAPH_EVENT",
                "AddMessageEvent"
        );
        String eventPathPolicyId = registerPolicy(
                "/api/filters/regex-path-filters/policies",
                "controller-event-path",
                controllerLayerId,
                "CONTROLLER",
                CONTROLLER_ID,
                "GRAPH_EVENT",
                "AddMessageEvent"
        );

        Events.AddMessageEvent event = new Events.AddMessageEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                "Controller-layer filter verification"
        );
        eventStreamRepository.save(event);

        mockMvc.perform(post("/api/llm-debug/ui/nodes/events/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nodeId", nodeId,
                                "eventId", event.eventId(),
                                "pretty", false,
                                "maxFieldLength", 500))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.eventId()))
                .andExpect(jsonPath("$.formatted").isNotEmpty());

        assertThat(filterDecisionRecordRepository.findByPolicyIdAndLayerIdOrderByCreatedAtDesc(
                eventPathPolicyIdA, controllerLayerId, PageRequest.of(0, 20))).isNotEmpty();
        assertThat(filterDecisionRecordRepository.findByPolicyIdAndLayerIdOrderByCreatedAtDesc(
                eventPathPolicyId, controllerLayerId, PageRequest.of(0, 20))).isNotEmpty();

        mockMvc.perform(post("/api/filters/policies/records/recent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "policyId", eventPathPolicyIdA,
                                "layerId", controllerLayerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", greaterThanOrEqualTo(1)));
    }

    @Test
    void actionLayer_pythonMarkdownPolicy_appliesAllInstructionOps() throws Exception {
        ArtifactKey actionKey = ArtifactKey.createRoot().createChild();
        String actionNodeId = actionKey.value();
        String actionLayerId = "layer-action-python-" + UUID.randomUUID();
        String contributorName = "it-test-contributor-python";
        Path pythonScript = createPythonExecutorScript();

        saveLayer(actionLayerId, "WORKFLOW_AGENT_ACTION", actionNodeId, null, 1);

        String markdownPolicyId = registerPolicy(
                "/api/filters/markdown-path-filters/policies",
                "action-prompt-markdown-python",
                actionLayerId,
                "WORKFLOW_AGENT_ACTION",
                actionNodeId,
                "PROMPT_CONTRIBUTOR",
                contributorName,
                pythonExecutor(pythonScript, "markdown_instructions")
        );

        PromptContext promptContext = PromptContext.builder()
                .agentType(AgentType.ORCHESTRATOR)
                .currentContextId(actionKey)
                .build();

        PromptContributor contributor = new StaticPromptContributor(
                contributorName,
                "Static template text",
                """
                # Report
                ## Replace Section
                old replace content
                ## Set Section
                old set content
                ## Remove Section
                remove this section
                ## Replace Within Regex Section
                token: abc-123
                ## Remove Within Regex Section
                keep this but SECRET disappears
                ## Replace If Match Section
                contains MATCHME token
                ## Remove If Match Section
                contains DELETE_ME token
                """
        );
        ContextualPromptElement adapted = promptContributorAdapterFactory.create(contributor, promptContext);
        String contribution = adapted.contribution(Mockito.mock(OperationContext.class));

        assertThat(contribution).contains("REPLACED_MARKDOWN_CONTENT");
        assertThat(contribution).contains("SET_MARKDOWN_CONTENT");
        assertThat(contribution).doesNotContain("## Remove Section");
        assertThat(contribution).contains("token: masked");
        assertThat(contribution).doesNotContain("SECRET");
        assertThat(contribution).contains("MATCH_REPLACED_MARKDOWN");
        assertThat(contribution).doesNotContain("## Remove If Match Section");

        assertPolicyUsesPythonExecutor(markdownPolicyId);
        assertRecentDecisionWithoutError(markdownPolicyId, actionLayerId, "TRANSFORMED");
        assertRecentDecisionContainsExpectedInstructions(markdownPolicyId, actionLayerId, Set.of(
                "## Replace Section",
                "## Set Section",
                "## Remove Section",
                "## Replace Within Regex Section",
                "## Remove Within Regex Section",
                "## Replace If Match Section",
                "## Remove If Match Section"
        ));
    }

    @Test
    void controllerLayer_pythonJsonPolicy_appliesAllInstructionOpsForGraphEventPayload() throws Exception {
        String controllerLayerId = "layer-controller-json-python-" + UUID.randomUUID();
        Path pythonScript = createPythonExecutorScript();
        String nodeId = ArtifactKey.createRoot().value();

        saveLayer(controllerLayerId, "CONTROLLER", CONTROLLER_ID, null, 0);

        String jsonPolicyId = registerPolicy(
                "/api/filters/json-path-filters/policies",
                "controller-event-json-path-python",
                controllerLayerId,
                "CONTROLLER",
                CONTROLLER_ID,
                "GRAPH_EVENT",
                "AddMessageEvent",
                pythonExecutor(pythonScript, "json_instructions")
        );

        Events.AddMessageEvent event = new Events.AddMessageEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                "json payload source"
        );
        String payload = """
                {
                  "replaceField": "old replace",
                  "setField": "old set",
                  "removeField": "remove me",
                  "replaceWithin": "token-123",
                  "removeWithin": "keep SECRET hidden",
                  "replaceIf": "contains MATCH_THIS marker",
                  "removeIf": "contains DELETE_THIS marker"
                }
                """;

        String transformed = pathFilterIntegration.applyJsonPathFilters(
                        controllerLayerId,
                        FilterSource.graphEvent(event),
                        payload,
                        new DefaultPathFilterContext(controllerLayerId, new GraphEventObjectContext(controllerLayerId, event))
                )
                .t();
        JsonNode transformedJson = objectMapper.readTree(transformed);

        assertThat(transformedJson.path("replaceField").asText()).isEqualTo("REPLACED_JSON");
        assertThat(transformedJson.path("setField").asText()).isEqualTo("SET_JSON");
        assertThat(transformedJson.has("removeField")).isFalse();
        assertThat(transformedJson.path("replaceWithin").asText()).isEqualTo("token-masked");
        assertThat(transformedJson.path("removeWithin").asText()).doesNotContain("SECRET");
        assertThat(transformedJson.path("replaceIf").asText()).isEqualTo("MATCH_REPLACED_JSON");
        assertThat(transformedJson.has("removeIf")).isFalse();

        assertPolicyUsesPythonExecutor(jsonPolicyId);
        assertRecentDecisionWithoutError(jsonPolicyId, controllerLayerId, "TRANSFORMED");
        assertRecentDecisionContainsExpectedInstructions(jsonPolicyId, controllerLayerId, Set.of(
                "$.replaceField",
                "$.setField",
                "$.removeField",
                "$.replaceWithin",
                "$.removeWithin",
                "$.replaceIf",
                "$.removeIf"
        ));
    }

    @Test
    void aiPathFilter_registrationAndExecution_appliesLlmInstructionsThroughInterpreter() throws Exception {
        ArtifactKey actionKey = ArtifactKey.createRoot().createChild();
        String actionNodeId = actionKey.value();
        String actionLayerId = "layer-action-ai-" + UUID.randomUUID();
        String contributorName = "it-test-contributor-ai";

        saveLayer(actionLayerId, "WORKFLOW_AGENT_ACTION", actionNodeId, null, 1);

        // 1. Register AI filter policy via the dedicated endpoint
        String aiPolicyId = registerAiPolicy(
                "ai-markdown-filter",
                actionLayerId,
                "WORKFLOW_AGENT_ACTION",
                actionNodeId,
                "PROMPT_CONTRIBUTOR",
                contributorName
        );

        // 2. Verify policy was persisted with AI executor type
        var policyEntity = policyRegistrationRepository.findByRegistrationId(aiPolicyId).orElseThrow();
        assertThat(policyEntity.getFilterKind()).isEqualTo("AI_PATH");
        assertThat(policyEntity.getStatus()).isEqualTo("ACTIVE");
        String filterJson = policyEntity.getFilterJson();
        assertThat(filterJson).contains("\"filterType\":\"AI\"");
        assertThat(filterJson).contains("\"executorType\":\"AI\"");
        assertThat(filterJson).contains("\"modelRef\":\"test-model\"");
        assertThat(filterJson).contains("\"promptTemplate\":\"filter/test_ai_filter\"");
        assertThat(filterJson).contains("\"registrarPrompt\":\"integration-registrar-prompt\"");

        // 3. Verify policy can be discovered via the shared endpoint
        mockMvc.perform(post("/api/filters/layers/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "layerId", actionLayerId,
                                "status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policies").isArray())
                .andExpect(jsonPath("$.policies[0].policyId").value(aiPolicyId))
                .andExpect(jsonPath("$.policies[0].filterType").value("AI_PATH"));

        // 4. Configure LlmRunner mock to return AiFilterResult with REPLACE instructions
        List<Instruction> mockInstructions = List.of(
                new Instruction.Replace(
                        new com.hayden.acp_cdc_ai.acp.filter.path.MarkdownPath("## Target Section"),
                        "AI_REPLACED_CONTENT",
                        1
                ),
                new Instruction.Remove(
                        new com.hayden.acp_cdc_ai.acp.filter.path.MarkdownPath("## Remove Section"),
                        2
                )
        );
        AgentModels.AiFilterResult mockResult = AgentModels.AiFilterResult.builder()
                .contextId(actionKey.createChild())
                .successful(true)
                .output(mockInstructions)
                .build();
        Mockito.when(llmRunner.runWithTemplate(
                Mockito.anyString(),
                Mockito.any(PromptContext.class),
                Mockito.anyMap(),
                Mockito.any(ToolContext.class),
                Mockito.eq(AgentModels.AiFilterResult.class),
                Mockito.any(OperationContext.class)
        )).thenReturn(mockResult);

        // 5. Build a PromptContext with OperationContext so AI filter can execute
        var mockAgentProcess = Mockito.mock(com.embabel.agent.core.AgentProcess.class);
        OperationContext mockOperationContext = Mockito.mock(OperationContext.class);
        Mockito.when(mockOperationContext.getAgentProcess()).thenReturn(mockAgentProcess);
        PromptContext promptContext = PromptContext.builder()
                .agentType(AgentType.ORCHESTRATOR)
                .currentContextId(actionKey)
                .operationContext(mockOperationContext)
                .build();

        // 6. Execute via the prompt contributor path (which goes through FilteredPromptContributorAdapter)
        PromptContributor contributor = new StaticPromptContributor(
                contributorName,
                "Static template text",
                """
                # Report
                ## Target Section
                original content here
                ## Remove Section
                this should be removed
                ## Unaffected Section
                this should remain
                """
        );

        PromptContributorContext promptContributorContext = new PromptContributorContext(actionLayerId, promptContext);
        String rawContribution = contributor.contribute(promptContext);

        var filterResult = pathFilterIntegration.applyTextPathFilters(
                actionLayerId,
                FilterSource.promptContributor(contributor),
                rawContribution,
                new DefaultPathFilterContext(actionLayerId, promptContributorContext)
        );

        // 7. Verify the AI-produced instructions were applied through markdown-path dispatch
        String filtered = filterResult.t();
        assertThat(filtered).contains("AI_REPLACED_CONTENT");
        assertThat(filtered).doesNotContain("original content here");
        assertThat(filtered).doesNotContain("## Remove Section");
        assertThat(filtered).doesNotContain("this should be removed");
        assertThat(filtered).contains("## Unaffected Section");
        assertThat(filtered).contains("this should remain");

        // 8. Verify LlmRunner was called with the correct template and context
        Mockito.verify(llmRunner).runWithTemplate(
                Mockito.eq("filter/ai_filter"),
                Mockito.any(PromptContext.class),
                Mockito.argThat(model ->
                        model.containsKey("payload")
                                && model.containsKey("policyId")
                                && model.containsKey("registrarPrompt")
                                && "integration-registrar-prompt".equals(model.get("registrarPrompt"))),
                Mockito.any(ToolContext.class),
                Mockito.eq(AgentModels.AiFilterResult.class),
                Mockito.eq(mockOperationContext)
        );

        // 9. Verify decision record was persisted
        assertRecentDecisionWithoutError(aiPolicyId, actionLayerId, "TRANSFORMED");
        assertRecentDecisionContainsExpectedInstructions(aiPolicyId, actionLayerId, Set.of(
                "## Target Section",
                "## Remove Section"
        ));

        // 10. Verify filtered records can be retrieved via the inspection endpoint
        mockMvc.perform(post("/api/filters/policies/records/recent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "policyId", aiPolicyId,
                                "layerId", actionLayerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", greaterThanOrEqualTo(1)));
    }

    @Test
    void aiPathFilter_withoutOperationContext_skipsWithPassthrough() throws Exception {
        ArtifactKey actionKey = ArtifactKey.createRoot().createChild();
        String actionNodeId = actionKey.value();
        String actionLayerId = "layer-action-ai-noctx-" + UUID.randomUUID();
        String contributorName = "it-test-contributor-ai-noctx";

        saveLayer(actionLayerId, "WORKFLOW_AGENT_ACTION", actionNodeId, null, 1);

        String aiPolicyId = registerAiPolicy(
                "ai-markdown-filter-noctx",
                actionLayerId,
                "WORKFLOW_AGENT_ACTION",
                actionNodeId,
                "PROMPT_CONTRIBUTOR",
                contributorName
        );
        // Build PromptContext WITHOUT OperationContext
        PromptContext promptContext = PromptContext.builder()
                .agentType(AgentType.ORCHESTRATOR)
                .currentContextId(actionKey)
                .build();

        String rawContribution = "# Report\n## Section\noriginal content\n";

        PromptContributorContext promptContributorContext = new PromptContributorContext(actionLayerId, promptContext);
        PromptContributor contributor = new StaticPromptContributor(contributorName, "template", rawContribution);

        var filterResult = pathFilterIntegration.applyTextPathFilters(
                actionLayerId,
                FilterSource.promptContributor(contributor),
                rawContribution,
                new DefaultPathFilterContext(actionLayerId, promptContributorContext)
        );

        // Without OperationContext, AI filter should PASSTHROUGH — content unchanged
        assertThat(filterResult.t()).isEqualTo(rawContribution);

        // LlmRunner should NOT have been called
        Mockito.verifyNoInteractions(llmRunner);
    }

    @Test
    void aiPathFilter_deactivationAndReactivation_lifecycle() throws Exception {
        String actionLayerId = "layer-action-ai-lifecycle-" + UUID.randomUUID();
        String actionNodeId = ArtifactKey.createRoot().createChild().value();

        saveLayer(actionLayerId, "WORKFLOW_AGENT_ACTION", actionNodeId, null, 1);

        String aiPolicyId = registerAiPolicy(
                "ai-lifecycle-test",
                actionLayerId,
                "WORKFLOW_AGENT_ACTION",
                actionNodeId,
                "PROMPT_CONTRIBUTOR",
                "lifecycle-contributor"
        );

        // Verify active
        mockMvc.perform(post("/api/filters/layers/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "layerId", actionLayerId,
                                "status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policies[0].policyId").value(aiPolicyId));

        // Deactivate
        mockMvc.perform(post("/api/filters/policies/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "policyId", aiPolicyId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        // Verify no longer in active list
        mockMvc.perform(post("/api/filters/layers/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "layerId", actionLayerId,
                                "status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policies").isEmpty());
    }

    @Test
    void aiPathFilter_registration_acceptsSimplifiedSessionModes() throws Exception {
        String actionLayerId = "layer-action-ai-session-modes-" + UUID.randomUUID();
        String actionNodeId = ArtifactKey.createRoot().createChild().value();
        saveLayer(actionLayerId, "WORKFLOW_AGENT_ACTION", actionNodeId, null, 1);

        for (String sessionMode : List.of(
                "PER_INVOCATION",
                "SAME_SESSION_FOR_ALL",
                "SAME_SESSION_FOR_ACTION",
                "SAME_SESSION_FOR_AGENT")) {
            String policyId = registerAiPolicy(
                    "ai-session-mode-" + UUID.randomUUID(),
                    actionLayerId,
                    "WORKFLOW_AGENT_ACTION",
                    actionNodeId,
                    "PROMPT_CONTRIBUTOR",
                    "session-mode-" + sessionMode,
                    sessionMode
            );

            var policyEntity = policyRegistrationRepository.findByRegistrationId(policyId).orElseThrow();
            JsonNode executorNode = objectMapper.readTree(policyEntity.getFilterJson()).path("executor");
            assertThat(executorNode.path("sessionMode").asText()).isEqualTo(sessionMode);
        }
    }

    private String registerAiPolicy(String name,
                                    String layerId,
                                    String layerType,
                                    String layerKey,
                                    String matchOn,
                                    String matcherText) throws Exception {
        return registerAiPolicy(name, layerId, layerType, layerKey, matchOn, matcherText, "PER_INVOCATION");
    }

    private String registerAiPolicy(String name,
                                    String layerId,
                                    String layerType,
                                    String layerKey,
                                    String matchOn,
                                    String matcherText,
                                    String sessionMode) throws Exception {
        Map<String, Object> executor = new LinkedHashMap<>();
        executor.put("executorType", "AI");
        executor.put("modelRef", "test-model");
        executor.put("promptTemplate", "filter/test_ai_filter");
        executor.put("timeoutMs", 5000);
        executor.put("maxTokens", 1024);
        executor.put("registrarPrompt", "integration-registrar-prompt");
        executor.put("sessionMode", sessionMode);
        executor.put("includeAgentDecorators", true);
        executor.put("configVersion", "v1-test");

        return registerPolicy(
                "/api/filters/ai-path-filters/policies",
                name,
                layerId,
                layerType,
                layerKey,
                matchOn,
                matcherText,
                executor
        );
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

    private String registerPolicy(String endpoint,
                                  String name,
                                  String layerId,
                                  String layerType,
                                  String layerKey,
                                  String matchOn,
                                  String matcherText) throws Exception {
        return registerPolicy(
                endpoint,
                name,
                layerId,
                layerType,
                layerKey,
                matchOn,
                matcherText,
                "EQUALS",
                javaFunctionExecutor(name)
        );
    }

    private String registerPolicy(String endpoint,
                                  String name,
                                  String layerId,
                                  String layerType,
                                  String layerKey,
                                  String matchOn,
                                  String matcherText,
                                  Map<String, Object> executor) throws Exception {
        return registerPolicy(
                endpoint,
                name,
                layerId,
                layerType,
                layerKey,
                matchOn,
                matcherText,
                "EQUALS",
                executor
        );
    }

    private String registerPolicy(String endpoint,
                                  String name,
                                  String layerId,
                                  String layerType,
                                  String layerKey,
                                  String matchOn,
                                  String matcherText,
                                  String matcherType,
                                  Map<String, Object> executor) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", "Integration test policy " + name);
        body.put("sourcePath", "specs/001-data-layer-policy-filter/tests/" + name + ".json");
        body.put("priority", 1);
        body.put("isInheritable", false);
        body.put("isPropagatedToParent", false);
        body.put("activate", true);
        body.put("executor", executor);
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("layerId", layerId);
        binding.put("layerType", layerType);
        binding.put("layerKey", layerKey);
        binding.put("enabled", true);
        binding.put("includeDescendants", false);
        binding.put("isInheritable", false);
        binding.put("isPropagatedToParent", false);
        binding.put("matcherKey", "NAME");
        binding.put("matcherType", matcherType);
        binding.put("matcherText", matcherText);
        binding.put("matchOn", matchOn);
        body.put("layerBindings", List.of(binding));

        MvcResult result = mockMvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.path("policyId").asText();
    }

    private Map<String, Object> javaFunctionExecutor(String name) {
        return Map.of(
                "executorType", "JAVA_FUNCTION",
                "functionRef", "tests." + name,
                "timeoutMs", 1000
        );
    }

    private Map<String, Object> pythonExecutor(Path scriptPath, String entryFunction) {
        return Map.of(
                "executorType", "PYTHON",
                "scriptPath", scriptPath.toAbsolutePath().normalize().toString(),
                "entryFunction", entryFunction,
                "runtimeArgsSchema", Map.of(),
                "timeoutMs", 5_000
        );
    }

    private Path createPythonExecutorScript() throws Exception {
        Path scriptPath = Files.createTempFile("filter-policy-it-", ".py");
        String script = """
                import json
                import sys

                def instruction(op, path_type, expression, order, value=None, regex=None, matcher=None):
                    node = {
                        "op": op,
                        "targetPath": {
                            "pathType": path_type,
                            "expression": expression,
                        },
                        "order": order,
                    }
                    if value is not None:
                        node["value"] = value
                    if regex is not None:
                        node["regex"] = regex
                    if matcher is not None:
                        node["matcher"] = matcher
                    return node

                def markdown_instructions(_payload):
                    return [
                        instruction("REPLACE", "MARKDOWN_PATH", "## Replace Section", 1, value="REPLACED_MARKDOWN_CONTENT"),
                        instruction("SET", "MARKDOWN_PATH", "## Set Section", 2, value="SET_MARKDOWN_CONTENT"),
                        instruction("REMOVE", "MARKDOWN_PATH", "## Remove Section", 3),
                        instruction(
                            "REPLACE_IF_MATCH",
                            "MARKDOWN_PATH",
                            "## Replace Within Regex Section",
                            4,
                            value="token: masked",
                            matcher={"matcherType": "REGEX", "value": "abc-\\\\d+"},
                        ),
                        instruction(
                            "REPLACE_IF_MATCH",
                            "MARKDOWN_PATH",
                            "## Remove Within Regex Section",
                            5,
                            value="keep this but  disappears",
                            matcher={"matcherType": "REGEX", "value": "SECRET"},
                        ),
                        instruction(
                            "REPLACE_IF_MATCH",
                            "MARKDOWN_PATH",
                            "## Replace If Match Section",
                            6,
                            value="MATCH_REPLACED_MARKDOWN",
                            matcher={"matcherType": "EQUALS", "value": "MATCHME"},
                        ),
                        instruction(
                            "REMOVE_IF_MATCH",
                            "MARKDOWN_PATH",
                            "## Remove If Match Section",
                            7,
                            matcher={"matcherType": "REGEX", "value": "DELETE_ME"},
                        ),
                    ]

                def json_instructions(_payload):
                    return [
                        instruction("REPLACE", "JSON_PATH", "$.replaceField", 1, value="REPLACED_JSON"),
                        instruction("SET", "JSON_PATH", "$.setField", 2, value="SET_JSON"),
                        instruction("REMOVE", "JSON_PATH", "$.removeField", 3),
                        instruction(
                            "REPLACE_IF_MATCH",
                            "JSON_PATH",
                            "$.replaceWithin",
                            4,
                            value="token-masked",
                            matcher={"matcherType": "REGEX", "value": "token-\\\\d+"},
                        ),
                        instruction(
                            "REPLACE_IF_MATCH",
                            "JSON_PATH",
                            "$.removeWithin",
                            5,
                            value="keep  hidden",
                            matcher={"matcherType": "REGEX", "value": "SECRET"},
                        ),
                        instruction(
                            "REPLACE_IF_MATCH",
                            "JSON_PATH",
                            "$.replaceIf",
                            6,
                            value="MATCH_REPLACED_JSON",
                            matcher={"matcherType": "EQUALS", "value": "MATCH_THIS"},
                        ),
                        instruction(
                            "REMOVE_IF_MATCH",
                            "JSON_PATH",
                            "$.removeIf",
                            7,
                            matcher={"matcherType": "REGEX", "value": "DELETE_THIS"},
                        ),
                    ]

                def main() -> int:
                    payload = sys.stdin.read()
                    request = json.loads(payload) if payload else {}

                    entry = sys.argv[1] if len(sys.argv) > 1 else ""
                    if entry == "event_serdes":
                        output = event_serdes(request)
                    elif entry == "markdown_instructions":
                        output = markdown_instructions(request)
                    elif entry == "json_instructions":
                        output = json_instructions(request)
                    else:
                        output = request.get("input")
                    sys.stdout.write(json.dumps(output))
                    return 0

                if __name__ == "__main__":
                    raise SystemExit(main())
                """;
        Files.writeString(scriptPath, script, StandardCharsets.UTF_8);
        return scriptPath;
    }

    private void assertPolicyUsesPythonExecutor(String policyId) {
        String filterJson = policyRegistrationRepository.findByRegistrationId(policyId)
                .orElseThrow()
                .getFilterJson();
        assertThat(filterJson).contains("\"executorType\":\"PYTHON\"");
    }

    private void assertRecentDecisionWithoutError(String policyId, String layerId, String expectedAction) {
        List<FilterDecisionRecordEntity> records =
                filterDecisionRecordRepository.findByPolicyIdAndLayerIdOrderByCreatedAtDesc(
                        policyId, layerId, PageRequest.of(0, 20));
        assertThat(records).isNotEmpty();
        FilterDecisionRecordEntity recent = records.getFirst();
        assertThat(recent.getAction()).isEqualTo(expectedAction);
        assertThat(recent.getErrorMessage()).isNull();
    }

    private void assertRecentDecisionsWithoutError(String policyId,
                                                   String layerId,
                                                   int minimumCount,
                                                   Set<String> allowedActions) {
        List<FilterDecisionRecordEntity> records =
                filterDecisionRecordRepository.findByPolicyIdAndLayerIdOrderByCreatedAtDesc(
                        policyId, layerId, PageRequest.of(0, 50));
        assertThat(records).hasSizeGreaterThanOrEqualTo(minimumCount);
        assertThat(records)
                .allMatch(record -> record.getErrorMessage() == null)
                .allMatch(record -> allowedActions.contains(record.getAction()));
    }

    private void assertRecentDecisionContainsExpectedInstructions(String policyId,
                                                                  String layerId,
                                                                  Set<String> expectedExpressions) throws Exception {
        List<FilterDecisionRecordEntity> records =
                filterDecisionRecordRepository.findByPolicyIdAndLayerIdOrderByCreatedAtDesc(
                        policyId, layerId, PageRequest.of(0, 20));
        assertThat(records).isNotEmpty();
        FilterDecisionRecordEntity recent = records.getFirst();
        String instructionsJson = recent.getAppliedInstructionsJson();
        assertThat(instructionsJson).isNotBlank();
        JsonNode parsed = objectMapper.readTree(instructionsJson);
        assertThat(parsed.isArray()).isTrue();
        List<JsonNode> instructions = objectMapper.readerForListOf(JsonNode.class).readValue(instructionsJson);
        assertThat(instructions).hasSize(expectedExpressions.size());
        Set<String> expressions = instructions.stream()
                .map(node -> node.path("targetPath").path("expression").asText())
                .collect(Collectors.toSet());
        assertThat(expressions).containsAll(expectedExpressions);
        assertThat(instructions).anyMatch(node -> node.has("value"));
        boolean hasMatchOps = instructions.stream()
                .anyMatch(node -> {
                    String op = node.path("op").asText("");
                    return "REPLACE_IF_MATCH".equals(op) || "REMOVE_IF_MATCH".equals(op);
                });
        if (hasMatchOps) {
            assertThat(instructions).anyMatch(node -> node.has("matcher"));
        }
        assertThat(recent.getErrorMessage()).isNull();
    }

    private record StaticPromptContributor(String name, String template, String contributionText) implements PromptContributor {
        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            return contributionText;
        }

        @Override
        public int priority() {
            return 100;
        }
    }
}
