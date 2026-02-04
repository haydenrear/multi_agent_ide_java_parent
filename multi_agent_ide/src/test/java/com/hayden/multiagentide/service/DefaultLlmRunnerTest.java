package com.hayden.multiagentide.service;

import com.embabel.agent.api.common.ContextualPromptElement;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.agent.api.common.ToolObject;
import com.embabel.agent.api.common.nested.TemplateOperations;
import com.embabel.agent.core.AgentPlatform;
import com.hayden.multiagentide.agent.decorator.prompt.AddMemoryToolCallDecorator;
import com.hayden.multiagentide.agent.decorator.prompt.ArtifactEmissionLlmCallDecorator;
import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Slf4j
@SpringBootTest
@Profile("test")
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

    @Test
    void runWithTemplate_usesAutoconfiguredDecorators() {
        OperationContext operationContext = mock(OperationContext.class, Answers.RETURNS_DEEP_STUBS);
        PromptRunner promptRunner = mock(PromptRunner.class);
        TemplateOperations templateOperations = new TemplateOperations(
            "workflow/orchestrator",
                agentPlatform.getPlatformServices().getTemplateRenderer(),
                promptRunner
        );

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
                .templateName("workflow/orchestrator")
                .metadata(Map.of())
                .build();

        ToolContext toolContext = new ToolContext(List.of());

        ResponseValue result = defaultLlmRunner.runWithTemplate(
                "workflow/orchestrator",
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
        verify(promptRunner).createObject(promptCaptor.capture(), any(Class.class));

        var p = promptCaptor.getValue();
        log.info("Found prompt result\n{}", p);
    }
}
