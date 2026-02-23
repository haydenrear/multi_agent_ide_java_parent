package com.hayden.multiagentide.service;

import com.embabel.agent.api.common.ContextualPromptElement;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.agent.api.common.ToolObject;
import com.embabel.agent.api.common.nested.TemplateOperations;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Conversation;
import com.embabel.common.textio.template.JinjavaTemplateRenderer;
import com.hayden.multiagentide.agent.decorator.prompt.AddMemoryToolCallDecorator;
import com.hayden.multiagentide.agent.decorator.prompt.ArtifactEmissionLlmCallDecorator;
import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.hayden.multiagentide.agent.AgentInterfaces.TEMPLATE_WORKFLOW_ORCHESTRATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Slf4j
@SpringBootTest
@ActiveProfiles({"test", "testdocker"})
class DefaultLlmRunnerSpringBootTest {


    @Autowired
    private DefaultLlmRunner defaultLlmRunner;

    @MockitoSpyBean
    private AddMemoryToolCallDecorator addMemoryToolCallDecorator;

    @MockitoSpyBean
    private ArtifactEmissionLlmCallDecorator artifactEmissionLlmCallDecorator;

    @Autowired
    private AgentPlatform agentPlatform;

    @Captor
    private ArgumentCaptor<String> promptCaptor;

    public record ResponseValue(String value) {}

//    @Test
    void runWithTemplate_usesAutoconfiguredDecorators() {
        OperationContext operationContext = mock(OperationContext.class, Answers.RETURNS_DEEP_STUBS);
        PromptRunner promptRunner = mock(PromptRunner.class);
        String templateName = TEMPLATE_WORKFLOW_ORCHESTRATOR;
        AtomicReference<String>ref = new AtomicReference<>();
        TemplateOperations templateOperations = new TemplateOperations(){
            @Override
            public @NonNull AssistantMessage respondWithSystemPrompt(@NonNull Conversation conversation, @NonNull Map<String, ?> model) {
                return new AssistantMessage("", "", null);
            }

            @Override
            public @NonNull String generateText(@NonNull Map<String, ?> model) {
                var r = agentPlatform.getPlatformServices().getTemplateRenderer()
                        .renderLoadedTemplate(TEMPLATE_WORKFLOW_ORCHESTRATOR, model);

                ref.set(r);
                return r;
            }

            @Override
            public <T> T createObject(@NonNull Class<T> outputClass, @NonNull Map<String, ?> model) {
                return (T) new ResponseValue("ok");
            }
        };

        when(operationContext.ai().withDefaultLlm()).thenReturn(promptRunner);
        when(promptRunner.withPromptElements(any(ContextualPromptElement[].class))).thenReturn(promptRunner);
        when(promptRunner.createObject(anyString(), any(Class.class)))
                .thenReturn(new ResponseValue("ok"));
        when(promptRunner.withTemplate(anyString()))
                .thenReturn(templateOperations);
        when(promptRunner.withToolObject(any(ToolObject.class)))
                .thenReturn(promptRunner);

        PromptContext promptContext = PromptContext.builder()
                .agentType(AgentType.ORCHESTRATOR)
                .currentContextId(ArtifactKey.createRoot())
                .currentRequest(new AgentModels.OrchestratorRequest(ArtifactKey.createRoot(), "Goal", "DISCOVERY"))
                .promptContributors(List.of())
                .templateName(templateName)
                .metadata(Map.of())
                .build();

        ToolContext toolContext = new ToolContext(List.of());

        ResponseValue result = defaultLlmRunner.runWithTemplate(
                templateName,
                promptContext,
                Map.of("goal", "Goal",
                       "phase", "orchestrator"),
                toolContext,
                ResponseValue.class,
                operationContext
        );

        assertThat(result).isNotNull();
        assertThat(result.value()).isEqualTo("ok");

        verify(addMemoryToolCallDecorator, atLeastOnce()).decorate(any());
        verify(artifactEmissionLlmCallDecorator, atLeastOnce()).decorate(any());

        var p = ref.get();
        log.info("Found prompt result\n{}", p);
    }
}
