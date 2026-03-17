package com.hayden.multiagentide.integration.propagation;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.PlatformServices;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.common.textio.template.CompiledTemplate;
import com.embabel.common.textio.template.TemplateRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.agent.decorator.prompt.LlmCallDecorator;
import com.hayden.multiagentide.agent.decorator.prompt.PromptHealthCheckLlmCallDecorator;
import com.hayden.multiagentide.agent.decorator.request.DecorateRequestResults;
import com.hayden.multiagentide.agent.decorator.request.WorktreeContextRequestDecorator;
import com.hayden.multiagentide.filter.repository.LayerRepository;
import com.hayden.multiagentide.filter.service.FilterLayerCatalog;
import com.hayden.multiagentide.filter.service.LayerHierarchyBootstrap;
import com.hayden.multiagentide.propagation.repository.PropagationItemRepository;
import com.hayden.multiagentide.propagation.repository.PropagationRecordEntity;
import com.hayden.multiagentide.propagation.repository.PropagationRecordRepository;
import com.hayden.multiagentide.propagation.repository.PropagatorRegistrationRepository;
import com.hayden.multiagentide.support.AgentTestBase;
import com.hayden.multiagentide.support.QueuedLlmRunner;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.WorkflowGraphState;
import com.hayden.multiagentidelib.llm.LlmRunner;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorAdapter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test that verifies the full prompt-health-check propagator pipeline:
 * 1. Layers are seeded from FilterLayerCatalog
 * 2. Propagators can be registered against the prompt-health-check layer
 * 3. PromptHealthCheckLlmCallDecorator discovers and attempts to execute them
 * 4. Propagation records are created confirming the routing worked
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "testdocker"})
class HealthCheckDecoratorIntTest extends AgentTestBase {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        LlmRunner llmRunner() {
            return new QueuedLlmRunner();
        }

        @Bean
        QueuedLlmRunner queuedLlmRunner(LlmRunner llmRunner) {
            return (QueuedLlmRunner) llmRunner;
        }
    }

    @Autowired
    private QueuedLlmRunner queuedLlmRunner;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private LayerHierarchyBootstrap layerHierarchyBootstrap;
    @Autowired
    private LayerRepository layerRepository;
    @Autowired
    private PropagatorRegistrationRepository propagatorRegistrationRepository;
    @Autowired
    private PropagationRecordRepository propagationRecordRepository;
    @Autowired
    private PropagationItemRepository propagationItemRepository;
    @Autowired
    private PromptHealthCheckLlmCallDecorator decorator;

    @MockitoBean
    private DecorateRequestResults decorateRequestResults;

    @BeforeEach
    void setUp() {
        queuedLlmRunner.clear();
        propagationRecordRepository.deleteAll();
        propagationItemRepository.deleteAll();
        propagatorRegistrationRepository.deleteAll();
        layerRepository.deleteAll();
        layerHierarchyBootstrap.seedLayersIfAbsent();

        Mockito.when(decorateRequestResults.decorateRequest(any()))
                .thenAnswer(inv -> inv.<DecorateRequestResults.DecorateRequestArgs>getArgument(0)
                        .request());
        Mockito.when(decorateRequestResults.decorateResult(any()))
                .thenAnswer(inv -> inv.<DecorateRequestResults.DecorateResultArgs>getArgument(0)
                        .result());
        Mockito.when(decorateRequestResults.decorateToolContext(any()))
                .thenAnswer(inv -> inv.<DecorateRequestResults.DecorateToolArgs>getArgument(0)
                        .toolContext());
        Mockito.when(decorateRequestResults.decoratePromptContext(any()))
                .thenAnswer(inv -> inv.<DecorateRequestResults.DecoratePromptContextArgs>getArgument(0)
                        .promptContext());
    }

    @Test
    void decorator_routesToRegisteredPropagators_whenLayerIdIsPromptHealthCheck() throws Exception {
        String reg1 = registerPropagator(
                "prompt-consistency-checker",
                "Review prompts for inconsistencies, duplications, or ambiguities");
        String reg2 = registerPropagator(
                "prompt-worktree-path-checker",
                "Identify if the worktree path is resolvable or ambiguous");

        // Enqueue a result for each of the two propagator LLM calls
        AgentModels.AiPropagatorResult successResult = AgentModels.AiPropagatorResult.builder()
                .successful(true)
                .propagatedText("No issues detected")
                .summaryText("Prompt looks clean")
                .build();

        queuedLlmRunner.enqueue(successResult);
        queuedLlmRunner.enqueue(successResult);

        ArtifactKey contextId = ArtifactKey.createRoot();
        OperationContext mockOp = Mockito.mock(OperationContext.class);

        AgentPlatform mockAgentPlatform = Mockito.mock(AgentPlatform.class);
        AgentProcess mockAgentProcess = Mockito.mock(AgentProcess.class);
        PlatformServices mockPlatformServices = Mockito.mock(PlatformServices.class);
        TemplateRenderer mockTemplateRenderer = Mockito.mock(TemplateRenderer.class);
        CompiledTemplate mockCompiledTemplate = Mockito.mock(CompiledTemplate.class);

        Mockito.when(mockCompiledTemplate.render(any())).thenReturn("test prompt.");
        Mockito.when(mockTemplateRenderer.compileLoadedTemplate(Mockito.anyString())).thenReturn(mockCompiledTemplate);
        Mockito.when(mockPlatformServices.getTemplateRenderer()).thenReturn(mockTemplateRenderer);
        Mockito.when(mockAgentPlatform.getPlatformServices()).thenReturn(mockPlatformServices);
        Mockito.when(mockOp.agentPlatform()).thenReturn(mockAgentPlatform);
        Mockito.when(mockOp.getAgentProcess()).thenReturn(mockAgentProcess);
        Mockito.when(mockAgentProcess.getId()).thenReturn(contextId.value());
        Mockito.when(mockAgentProcess.last(BlackboardHistory.class)).thenReturn(new BlackboardHistory(new BlackboardHistory.History(), contextId.value(), WorkflowGraphState.initial(contextId.value())));

        PromptContext promptContext = PromptContext.builder()
                .agentType(AgentType.ORCHESTRATOR)
                .currentContextId(contextId)
                .templateName("")
                .promptContributors(List.of(
                        new PromptContributorAdapter(
                                new StaticPromptContributor(
                                        "worktree-context",
                                        "Worktree path: /tmp/worktrees/abc123 and also /tmp/worktrees/def456",
                                        10),
                                null)))
                .build();

        LlmCallDecorator.LlmCallContext<?> ctx = LlmCallDecorator.LlmCallContext.builder()
                .promptContext(promptContext)
                .op(mockOp)
                .build();

        decorator.decorate(ctx);

        assertThat(queuedLlmRunner.getCallCount())
                .as("LlmRunner should be called once per registered propagator")
                .isEqualTo(2);

        List<PropagationRecordEntity> records = propagationRecordRepository.findAll();
        assertThat(records)
                .as("Both registered prompt-health-check propagators should produce records")
                .hasSize(2);
        assertThat(records)
                .allMatch(r -> FilterLayerCatalog.PROMPT_HEALTH_CHECK.equals(r.getLayerId()),
                        "all records should have layerId=" + FilterLayerCatalog.PROMPT_HEALTH_CHECK);
        assertThat(records)
                .extracting(PropagationRecordEntity::getRegistrationId)
                .containsExactlyInAnyOrder(reg1, reg2);
    }

    @Test
    void decorator_skipsInternalAgentTypes_toPreventRecursion() {
        PromptContext propagatorCtx = PromptContext.builder()
                .agentType(AgentType.AI_PROPAGATOR)
                .promptContributors(List.of(
                        new PromptContributorAdapter(
                                new StaticPromptContributor("internal", "some prompt", 0), null)))
                .build();

        LlmCallDecorator.LlmCallContext<?> ctx = LlmCallDecorator.LlmCallContext.builder()
                .promptContext(propagatorCtx)
                .op(Mockito.mock(OperationContext.class))
                .build();

        decorator.decorate(ctx);

        assertThat(propagationRecordRepository.findAll())
                .as("Internal AI_PROPAGATOR calls must not trigger health check propagation")
                .isEmpty();
    }

    private String registerPropagator(String name, String registrarPrompt) throws Exception {
        String json = """
                {
                  "name": "%s",
                  "description": "%s",
                  "sourcePath": "auto://prompt-health-check/%s",
                  "propagatorKind": "AI_TEXT",
                  "priority": 100,
                  "isInheritable": false,
                  "isPropagatedToParent": false,
                  "layerBindings": [{
                    "layerId": "prompt-health-check",
                    "enabled": true,
                    "includeDescendants": false,
                    "isInheritable": false,
                    "isPropagatedToParent": false,
                    "matcherKey": "TEXT",
                    "matcherType": "EQUALS",
                    "matcherText": "prompt-health",
                    "matchOn": "ACTION_REQUEST"
                  }],
                  "executor": {
                    "registrarPrompt": "%s",
                    "sessionMode": "PER_INVOCATION"
                  },
                  "activate": true
                }
                """.formatted(name, name, name, registrarPrompt);
        MvcResult result = mockMvc.perform(post("/api/propagators/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("registrationId").asText();
    }

    record StaticPromptContributor(String name, String content, int priority) implements PromptContributor {
        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            return content;
        }

        @Override
        public String template() {
            return """
                    Hello!
                    """;
        }
    }
}
