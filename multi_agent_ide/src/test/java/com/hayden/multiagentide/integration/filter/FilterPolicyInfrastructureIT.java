package com.hayden.multiagentide.integration.filter;

import com.embabel.agent.api.annotation.support.ActionQosProvider;
import com.embabel.agent.api.common.ContextualPromptElement;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.ActionQos;
import com.embabel.agent.core.Blackboard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.acp_cdc_ai.acp.filter.Instruction;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.filter.integration.PathFilterIntegration;
import com.hayden.multiagentide.filter.repository.FilterDecisionRecordEntity;
import com.hayden.multiagentide.filter.repository.FilterDecisionRecordRepository;
import com.hayden.multiagentide.filter.repository.LayerEntity;
import com.hayden.multiagentide.filter.repository.LayerRepository;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationRepository;
import com.hayden.multiagentide.filter.service.FilterLayerCatalog;
import com.hayden.multiagentide.repository.EventStreamRepository;
import com.hayden.multiagentide.repository.InMemoryGraphRepository;
import com.hayden.multiagentide.support.AgentTestBase;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.agent.WorkflowGraphState;
import com.hayden.multiagentide.filter.model.FilterSource;
import com.hayden.multiagentide.filter.model.layer.DefaultPathFilterContext;
import com.hayden.multiagentide.filter.model.layer.GraphEventObjectContext;
import com.hayden.multiagentide.filter.model.layer.PromptContributorContext;
import com.hayden.multiagentide.llm.LlmRunner;
import com.hayden.multiagentide.model.nodes.OrchestratorNode;
import com.hayden.multiagentide.model.worktree.MainWorktreeContext;
import com.hayden.multiagentide.model.worktree.WorktreeSandboxContext;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorAdapterFactory;
import com.hayden.multiagentide.prompt.PromptContributorService;
import com.hayden.multiagentide.prompt.contributor.WeAreHerePromptContributor;
import com.hayden.multiagentide.support.QueuedChatModel;
import com.hayden.multiagentide.support.TestEventListener;
import com.hayden.multiagentide.tool.ToolContext;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.lang.reflect.Method;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "testdocker"})
class FilterPolicyInfrastructureIT extends AgentTestBase {

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
    private PromptContributorService promptContributorService;

    @Autowired
    private PathFilterIntegration pathFilterIntegration;

    @MockitoBean
    private LlmRunner llmRunner;
    @Autowired
    private InMemoryGraphRepository inMemoryGraphRepository;
//    @MockitoBean
//    private SandboxResolver sandboxResolver;

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        ActionQosProvider manager() {
            return new ActionQosProvider() {
                @Override
                public @NonNull ActionQos provideActionQos(@NonNull Method method, @NonNull Object instance) {
                    return new ActionQos(2, 50, 2, 60, false);
                }
            };
        }
    }

    @BeforeEach
    void setUp() {
        filterDecisionRecordRepository.deleteAll();
        policyRegistrationRepository.deleteAll();
        layerRepository.deleteAll();

//        Mockito.when(sandboxResolver.resolveSandboxContext(Mockito.any()))
//                .thenReturn(WorktreeSandboxContext.builder().build());
    }

    @Test
    void coordinateWorkflow_promptContributorService_includesWorkflowPositionContributor() {
        ArtifactKey actionKey = ArtifactKey.createRoot().createChild();
        PromptContext promptContext = workflowPromptContext(actionKey, mockOperationContext(actionKey.value()));

        List<String> contributorRoles = promptContributorService.getContributors(promptContext).stream()
                .map(ContextualPromptElement::getRole)
                .toList();

        assertThat(contributorRoles).contains("workflow-position");
    }

    @Test
    void attachablesEndpoint_listsGraphEventsAndPromptContributors() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/filters/attachables"))
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.graphEvents[?(@.eventType == 'GOAL_STARTED')]").exists())
                .andExpect(jsonPath("$.promptContributorNames[?(@ == 'active-data-filters')]").exists())
                .andExpect(jsonPath("$.promptContributors[?(@.layerId == 'workflow-agent/coordinateWorkflow' && @.contributorName == 'workflow-position')]").exists());
    }

    @Test
    void workflowPositionPolicy_matchesLiveCoordinateWorkflowContributor() throws Exception {
        ArtifactKey actionKey = ArtifactKey.createRoot().createChild();
        OperationContext operationContext = mockOperationContext(actionKey.value());
        seedCatalogLayers();

        String aiPolicyId = registerAiPolicy(
                "ai-workflow-position-live-match",
                FilterLayerCatalog.WORKFLOW_AGENT + "/" + AgentInterfaces.METHOD_COORDINATE_WORKFLOW,
                FilterEnums.LayerType.WORKFLOW_AGENT_ACTION.name(),
                AgentInterfaces.METHOD_COORDINATE_WORKFLOW,
                "PROMPT_CONTRIBUTOR",
                "workflow-position",
                "PER_INVOCATION",
                false
        );

        AgentModels.AiFilterResult mockResult = AgentModels.AiFilterResult.builder()
                .contextId(actionKey.createChild())
                .successful(true)
                .output(List.of())
                .build();
        Mockito.when(llmRunner.runWithTemplate(
                Mockito.anyString(),
                Mockito.any(PromptContext.class),
                Mockito.anyMap(),
                Mockito.any(ToolContext.class),
                Mockito.eq(AgentModels.AiFilterResult.class),
                Mockito.any(OperationContext.class)
        )).thenReturn(mockResult);

        PromptContext promptContext = workflowPromptContext(actionKey, operationContext);
        PromptContributor workflowPosition = new WeAreHerePromptContributor();

        ContextualPromptElement adapted = promptContributorAdapterFactory.create(workflowPosition, promptContext);
        String filtered = adapted.contribution(operationContext);

        assertThat(filtered).contains("## Workflow Position");
        assertRecentDecisionsWithoutError(
                aiPolicyId,
                FilterLayerCatalog.WORKFLOW_AGENT + "/" + AgentInterfaces.METHOD_COORDINATE_WORKFLOW,
                1,
                Set.of("PASSTHROUGH", "TRANSFORMED", "DROPPED")
        );
        Mockito.verify(llmRunner).runWithTemplate(
                Mockito.eq("filter/ai_filter"),
                Mockito.any(PromptContext.class),
                Mockito.anyMap(),
                Mockito.any(ToolContext.class),
                Mockito.eq(AgentModels.AiFilterResult.class),
                Mockito.eq(operationContext)
        );
    }

    @Test
    void activeFiltersPromptPolicy_matchesLiveCoordinateWorkflowContributor_byExactName() throws Exception {
        ArtifactKey actionKey = ArtifactKey.createRoot().createChild();
        OperationContext operationContext = mockOperationContext(actionKey.value());
        seedCatalogLayers();

        String policyId = registerPolicy(
                "/api/filters/markdown-path-filters/policies",
                "active-filters-exact-name-live-match",
                FilterLayerCatalog.WORKFLOW_AGENT + "/" + AgentInterfaces.METHOD_COORDINATE_WORKFLOW,
                FilterEnums.LayerType.WORKFLOW_AGENT_ACTION.name(),
                AgentInterfaces.METHOD_COORDINATE_WORKFLOW,
                "PROMPT_CONTRIBUTOR",
                "active-data-filters"
                ,
                "EQUALS",
                pythonExecutor(createNoOpPythonExecutorScript(), "no_op")
        );

        PromptContext promptContext = workflowPromptContext(actionKey, operationContext);
        List<ContextualPromptElement> contributors = promptContributorService.getContributors(promptContext);
        List<String> contributorRoles = contributors.stream()
                .map(ContextualPromptElement::getRole)
                .toList();

        assertThat(contributorRoles).contains("active-data-filters");

        ContextualPromptElement activeFilters = selectContributorByRole(contributors, "active-data-filters");
        assertThat(activeFilters.contribution(operationContext)).contains("## Active Data Filters");
        assertRecentDecisionsWithoutError(
                policyId,
                FilterLayerCatalog.WORKFLOW_AGENT + "/" + AgentInterfaces.METHOD_COORDINATE_WORKFLOW,
                1,
                Set.of("PASSTHROUGH", "TRANSFORMED", "DROPPED")
        );
    }

    @Test
    void activeFiltersAiPolicy_matchesLiveCoordinateWorkflowContributor_byExactName() throws Exception {
        ArtifactKey actionKey = ArtifactKey.createRoot().createChild();
        OperationContext operationContext = mockOperationContext(actionKey.value());
        seedCatalogLayers();

        String aiPolicyId = registerAiPolicy(
                "ai-active-filters-exact-name-live-match",
                FilterLayerCatalog.WORKFLOW_AGENT + "/" + AgentInterfaces.METHOD_COORDINATE_WORKFLOW,
                FilterEnums.LayerType.WORKFLOW_AGENT_ACTION.name(),
                AgentInterfaces.METHOD_COORDINATE_WORKFLOW,
                "PROMPT_CONTRIBUTOR",
                "active-data-filters",
                "PER_INVOCATION",
                false
        );

        AgentModels.AiFilterResult mockResult = AgentModels.AiFilterResult.builder()
                .contextId(actionKey.createChild())
                .successful(true)
                .output(List.of())
                .build();
        Mockito.when(llmRunner.runWithTemplate(
                Mockito.anyString(),
                Mockito.any(PromptContext.class),
                Mockito.anyMap(),
                Mockito.any(ToolContext.class),
                Mockito.eq(AgentModels.AiFilterResult.class),
                Mockito.any(OperationContext.class)
        )).thenReturn(mockResult);

        PromptContext promptContext = workflowPromptContext(actionKey, operationContext);
        List<ContextualPromptElement> contributors = promptContributorService.getContributors(promptContext);
        List<String> contributorRoles = contributors.stream()
                .map(ContextualPromptElement::getRole)
                .toList();

        assertThat(contributorRoles).contains("active-data-filters");

        ContextualPromptElement activeFilters = selectContributorByRole(contributors, "active-data-filters");
        assertThat(activeFilters.contribution(operationContext)).contains("## Active Data Filters");
        assertRecentDecisionsWithoutError(
                aiPolicyId,
                FilterLayerCatalog.WORKFLOW_AGENT + "/" + AgentInterfaces.METHOD_COORDINATE_WORKFLOW,
                1,
                Set.of("PASSTHROUGH", "TRANSFORMED", "DROPPED")
        );
        Mockito.verify(llmRunner).runWithTemplate(
                Mockito.eq("filter/ai_filter"),
                Mockito.any(PromptContext.class),
                Mockito.anyMap(),
                Mockito.any(ToolContext.class),
                Mockito.eq(AgentModels.AiFilterResult.class),
                Mockito.eq(operationContext)
        );
    }

    @Test
    void controllerLayer_registeredPolicies_areAppliedWhenViewingEvents() throws Exception {
        String nodeId = ArtifactKey.createRoot().value();
        seedCatalogLayers();
        String controllerLayerId = FilterLayerCatalog.CONTROLLER_UI_EVENT_POLL;

        String eventPathPolicyIdA = registerPolicy(
                "/api/filters/json-path-filters/policies",
                "controller-event-json-path",
                controllerLayerId,
                FilterEnums.LayerType.CONTROLLER_UI_EVENT_POLL.name(),
                FilterLayerCatalog.CONTROLLER_UI_EVENT_POLL,
                "GRAPH_EVENT",
                "AddMessageEvent"
        );
        String eventPathPolicyId = registerPolicy(
                "/api/filters/regex-path-filters/policies",
                "controller-event-path",
                controllerLayerId,
                FilterEnums.LayerType.CONTROLLER_UI_EVENT_POLL.name(),
                FilterLayerCatalog.CONTROLLER_UI_EVENT_POLL,
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

        mockMvc.perform(post("/api/ui/nodes/events/detail")
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
    void controllerAiPolicy_skipsMissingAgentProcessWithoutPublishingNodeError() throws Exception {
        String nodeId = ArtifactKey.createRoot().value();
        seedCatalogLayers();

        String aiPolicyId = registerAiPolicy(
                "controller-ai-no-process",
                FilterLayerCatalog.CONTROLLER_UI_EVENT_POLL,
                FilterEnums.LayerType.CONTROLLER_UI_EVENT_POLL.name(),
                FilterLayerCatalog.CONTROLLER_UI_EVENT_POLL,
                "GRAPH_EVENT",
                "AddMessageEvent",
                "PER_INVOCATION",
                false
        );

        Events.AddMessageEvent event = new Events.AddMessageEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                "Controller AI filter should skip without a live process"
        );
        eventStreamRepository.save(event);

        long nodeErrorCountBefore = eventStreamRepository.list().stream()
                .filter(Events.NodeErrorEvent.class::isInstance)
                .count();

        mockMvc.perform(post("/api/ui/nodes/events/detail")
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
                aiPolicyId, FilterLayerCatalog.CONTROLLER_UI_EVENT_POLL, PageRequest.of(0, 20)))
                .isNotEmpty();
        assertThat(eventStreamRepository.list().stream()
                .filter(Events.NodeErrorEvent.class::isInstance)
                .count())
                .isEqualTo(nodeErrorCountBefore);
        Mockito.verifyNoInteractions(llmRunner);
    }

    @Test
    void actionLayer_pythonMarkdownPolicy_appliesAllInstructionOps() throws Exception {
        ArtifactKey actionKey = ArtifactKey.createRoot().createChild();
        seedCatalogLayers();
        String actionLayerId = FilterLayerCatalog.WORKFLOW_AGENT + "/" + AgentInterfaces.METHOD_COORDINATE_WORKFLOW;
        String contributorName = "it-test-contributor-python";
        Path pythonScript = createPythonExecutorScript();

        String markdownPolicyId = registerPolicy(
                "/api/filters/markdown-path-filters/policies",
                "action-prompt-markdown-python",
                actionLayerId,
                FilterEnums.LayerType.WORKFLOW_AGENT_ACTION.name(),
                AgentInterfaces.METHOD_COORDINATE_WORKFLOW,
                "PROMPT_CONTRIBUTOR",
                contributorName,
                pythonExecutor(pythonScript, "markdown_instructions")
        );

        PromptContext promptContext = workflowPromptContext(actionKey, mockOperationContext(actionKey.value()));

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
                MATCHME
                ## Remove If Match Section
                DELETE_ME
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
                "ADD_MESSAGE_EVENT",
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
                  "replaceIf": "MATCH_THIS",
                  "removeIf": "DELETE_THIS"
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
        ArtifactKey root = ArtifactKey.createRoot();
        ArtifactKey actionKey = root.createChild();
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
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()
        )).thenReturn(mockResult);

        BlackboardHistory value = new BlackboardHistory(new BlackboardHistory.History(), root.value(), WorkflowGraphState.initial(root.value()));


        // 5. Build a PromptContext with OperationContext so AI filter can execute
        var mockAgentProcess = Mockito.mock(com.embabel.agent.core.AgentProcess.class);
        OperationContext mockOperationContext = Mockito.mock(OperationContext.class);
        Mockito.when(mockOperationContext.last(BlackboardHistory.class))
                .thenReturn(value);
        var mockBlackboard = Mockito.mock(Blackboard.class);
        Mockito.when(mockOperationContext.getAgentProcess()).thenReturn(mockAgentProcess);
        Mockito.when(mockAgentProcess.getBlackboard()).thenReturn(mockBlackboard);
        Mockito.when(mockBlackboard.last(BlackboardHistory.class))
                .thenReturn(value);
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
        return registerAiPolicy(name, layerId, layerType, layerKey, matchOn, matcherText, "PER_INVOCATION", true);
    }

    private String registerAiPolicy(String name,
                                    String layerId,
                                    String layerType,
                                    String layerKey,
                                    String matchOn,
                                    String matcherText,
                                    String sessionMode) throws Exception {
        return registerAiPolicy(name, layerId, layerType, layerKey, matchOn, matcherText, sessionMode, true);
    }

    private String registerAiPolicy(String name,
                                    String layerId,
                                    String layerType,
                                    String layerKey,
                                    String matchOn,
                                    String matcherText,
                                    String sessionMode,
                                    boolean includeAgentDecorators) throws Exception {
        Map<String, Object> executor = new LinkedHashMap<>();
        executor.put("executorType", "AI");
        executor.put("modelRef", "test-model");
        executor.put("promptTemplate", "filter/test_ai_filter");
        executor.put("timeoutMs", 5000);
        executor.put("maxTokens", 1024);
        executor.put("registrarPrompt", "integration-registrar-prompt");
        executor.put("sessionMode", sessionMode);
        executor.put("includeAgentDecorators", includeAgentDecorators);
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

    private void seedCatalogLayers() {
        for (FilterLayerCatalog.LayerDefinition layerDefinition : FilterLayerCatalog.layerDefinitions()) {
            saveLayer(
                    layerDefinition.layerId(),
                    layerDefinition.layerType().name(),
                    layerDefinition.layerKey(),
                    layerDefinition.parentLayerId(),
                    layerDefinition.depth()
            );
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
                "timeoutMs", 15_000
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

    private Path createNoOpPythonExecutorScript() throws Exception {
        Path scriptPath = Files.createTempFile("filter-policy-noop-it-", ".py");
        String script = """
                import json
                import sys

                def no_op(_payload):
                    return []

                def main() -> int:
                    payload = sys.stdin.read()
                    request = json.loads(payload) if payload else {}
                    entry = sys.argv[1] if len(sys.argv) > 1 else "no_op"
                    if entry == "no_op":
                        output = no_op(request)
                    else:
                        output = []
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

    private OperationContext mockOperationContext(String actionKey) {
        var mockAgentProcess = Mockito.mock(com.embabel.agent.core.AgentProcess.class);
        var mockProcessContext = Mockito.mock(com.embabel.agent.core.ProcessContext.class);
        var mockProcessOptions = Mockito.mock(com.embabel.agent.core.ProcessOptions.class);
        var mockBlackboard = Mockito.mock(Blackboard.class);
        var mockOperationContext = Mockito.mock(OperationContext.class);
        Mockito.when(mockBlackboard.last(BlackboardHistory.class))
                .thenReturn(new BlackboardHistory(new BlackboardHistory.History(), actionKey, WorkflowGraphState.initial(actionKey)));
        Mockito.when(mockOperationContext.last(BlackboardHistory.class))
                .thenReturn(new BlackboardHistory(new BlackboardHistory.History(), actionKey, WorkflowGraphState.initial(actionKey)));
        Mockito.when(mockOperationContext.getAgentProcess()).thenReturn(mockAgentProcess);
        Mockito.when(mockOperationContext.getProcessContext()).thenReturn(mockProcessContext);
        Mockito.when(mockProcessContext.getAgentProcess()).thenReturn(mockAgentProcess);
        Mockito.when(mockAgentProcess.getBlackboard()).thenReturn(mockBlackboard);
        Mockito.when(mockAgentProcess.getId()).thenReturn(actionKey);
        Mockito.when(mockAgentProcess.getProcessOptions()).thenReturn(mockProcessOptions);
        Mockito.when(mockProcessOptions.getContextIdString()).thenReturn(actionKey);
        return mockOperationContext;
    }

    private PromptContext workflowPromptContext(ArtifactKey actionKey, OperationContext operationContext) {
        WorktreeSandboxContext build = worktreeSandboxContext(actionKey);
        AgentModels.OrchestratorRequest request = AgentModels.OrchestratorRequest.builder()
                .contextId(actionKey)
                .goal("matrix goal")
                .worktreeContext(build)
                .build();


        return PromptContext.builder()
                .agentType(AgentType.ORCHESTRATOR)
                .currentContextId(actionKey)
                .blackboardHistory(emptyBlackboardHistory(actionKey))
                .currentRequest(request)
                .operationContext(operationContext)
                .metadata(new HashMap<>())
                .agentName(AgentInterfaces.WORKFLOW_AGENT_NAME)
                .actionName(AgentInterfaces.ACTION_ORCHESTRATOR)
                .methodName(AgentInterfaces.METHOD_COORDINATE_WORKFLOW)
                .agentType(AgentType.ORCHESTRATOR)
                .build();
    }

    private @NonNull WorktreeSandboxContext worktreeSandboxContext(ArtifactKey actionKey) {
        WorktreeSandboxContext build = WorktreeSandboxContext.builder()
                .mainWorktree(MainWorktreeContext.builder().repositoryUrl("something").build())
                .build();
        inMemoryGraphRepository.save(
                OrchestratorNode.builder()
                        .nodeId(actionKey.value())
                        .worktreeContext(build.mainWorktree())
                        .build());
        return build;
    }

    private PromptContext promptContextForAction(FilterLayerCatalog.ActionDefinition actionDefinition,
                                                 OperationContext operationContext, ArtifactKey actionKey) {
        AgentModels.AgentRequest currentRequest = minimalRequestFor(actionDefinition, actionKey, worktreeSandboxContext(actionKey));
        return PromptContext.builder()
                .agentType(actionDefinition.agentType())
                .currentContextId(actionKey)
                .blackboardHistory(emptyBlackboardHistory(actionKey))
                .currentRequest(currentRequest)
                .model(Map.of())
                .operationContext(operationContext)
                .metadata(new HashMap<>())
                .agentName(actionDefinition.agentName())
                .actionName(actionDefinition.actionName())
                .methodName(actionDefinition.methodName())
                .agentType(actionDefinition.agentType())
                .build();
    }

    private AgentModels.AgentRequest minimalRequestFor(FilterLayerCatalog.ActionDefinition actionDefinition,
                                                       ArtifactKey actionKey,
                                                       @NonNull WorktreeSandboxContext sandboxContext) {
        return actionDefinition.requestTypes().stream()
                .filter(AgentModels.AgentRequest.class::isAssignableFrom)
                .map(type -> instantiateRequest(type, actionKey, sandboxContext))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private AgentModels.AgentRequest instantiateRequest(Class<?> requestType, ArtifactKey actionKey, @NonNull WorktreeSandboxContext sandboxContext) {
        try {
            Object builder = requestType.getMethod("builder").invoke(null);
            invokeBuilderSetter(builder, "contextId", ArtifactKey.class, actionKey);
            invokeBuilderSetter(builder, "goal", String.class, "matrix goal");
            invokeBuilderSetter(builder, "worktreeContext", WorktreeSandboxContext.class, sandboxContext);
            Object built = builder.getClass().getMethod("build").invoke(builder);
            return built instanceof AgentModels.AgentRequest agentRequest ? agentRequest : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void invokeBuilderSetter(Object builder, String methodName, Class<?> parameterType, Object value) {
        try {
            builder.getClass().getMethod(methodName, parameterType).invoke(builder, value);
        } catch (Exception ignored) {
            // best-effort builder hydration for heterogeneous request types
        }
    }

    private String selectExactMatchRole(List<String> contributorRoles) {
        List<String> preferredRoles = List.of(
                "workflow-position",
                "request-context",
                "active-data-filters"
        );
        for (String preferredRole : preferredRoles) {
            for (String contributorRole : contributorRoles) {
                if (preferredRole.equals(contributorRole)) {
                    return contributorRole;
                }
            }
        }
        return contributorRoles.getFirst();
    }

    private ContextualPromptElement selectContributorByRole(List<ContextualPromptElement> contributors, String role) {
        return contributors.stream()
                .filter(contributor -> role.equals(contributor.getRole()))
                .findFirst()
                .orElseGet(contributors::getFirst);
    }

    private BlackboardHistory emptyBlackboardHistory(ArtifactKey actionKey) {
        return new BlackboardHistory(
                new BlackboardHistory.History(),
                actionKey.value(),
                null
        );
    }

    private int depthFor(String parentLayerId) {
        return layerRepository.findByLayerId(parentLayerId)
                .map(LayerEntity::getDepth)
                .map(depth -> depth + 1)
                .orElse(1);
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

    private void assertRecentDecisions(String policyId,
                                       String layerId,
                                       int minimumCount,
                                       Set<String> allowedActions) {
        List<FilterDecisionRecordEntity> records =
                filterDecisionRecordRepository.findByPolicyIdAndLayerIdOrderByCreatedAtDesc(
                        policyId, layerId, PageRequest.of(0, 50));
        assertThat(records).hasSizeGreaterThanOrEqualTo(minimumCount);
        assertThat(records)
                .allMatch(record -> allowedActions.contains(record.getAction()));
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
                .allMatch(record -> {
                    boolean b = record.getErrorMessage() == null;
                    if(!b)
                        log.error("Error ! {}.", record.getErrorMessage());

                    return b;
                })
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
