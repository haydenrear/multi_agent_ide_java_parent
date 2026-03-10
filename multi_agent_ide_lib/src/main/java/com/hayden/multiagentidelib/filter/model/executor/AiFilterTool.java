package com.hayden.multiagentidelib.filter.model.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.filter.model.layer.FilterContext;
import com.hayden.multiagentidelib.filter.service.FilterDescriptor;
import com.hayden.multiagentidelib.filter.service.FilterResult;
import com.hayden.multiagentidelib.llm.LlmRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor that delegates to an AI model for filtering decisions.
 */
@Slf4j
@RequiredArgsConstructor
public final class AiFilterTool<I, O>
        implements ExecutableTool<AgentModels.AiFilterRequest, AgentModels.AiFilterResult, FilterContext.AiFilterContext> {

    private final String modelRef;
    private final String promptTemplate;
    private final String registrarPrompt;
    private final int maxTokens;
    private final Object outputSchema;
    private final SessionMode sessionMode;
    private final String sessionKeyOverride;
    private final String requestModelType;
    private final String resultModelType;
    private final Boolean includeAgentDecorators;
    private final String controllerModelRef;
    private final String controllerPromptTemplate;
    private final String responseMode;
    private final int timeoutMs;
    private final String configVersion;

    @Autowired
    private LlmRunner llmRunner;

    public enum SessionMode {
        PER_INVOCATION,
        SAME_SESSION_FOR_ALL,
        SAME_SESSION_FOR_ACTION,
        SAME_SESSION_FOR_AGENT
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        OBJECT_MAPPER.registerModule(module);
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    @Override
    public FilterEnums.ExecutorType executorType() {
        return FilterEnums.ExecutorType.AI;
    }

    @Override
    public @NotNull FilterResult<AgentModels.AiFilterResult> apply(AgentModels.AiFilterRequest i, FilterContext.AiFilterContext ctx) {
        try {
            String templateName = ctx.templateName() != null ? ctx.templateName()
                    : (promptTemplate != null && !promptTemplate.isBlank() ? promptTemplate : "filter/ai_filter");

            try {

                AgentModels.AiFilterResult aiResult = llmRunner.runWithTemplate(
                        templateName,
                        ctx.promptContext(),
                        ctx.model() != null ? ctx.model() : Map.of(),
                        ctx.toolContext(),
                        AgentModels.AiFilterResult.class,
                        ctx.context()
                );

                if (aiResult == null) {
                    return aiFailResult("LLM returned null result");
                }

                return new FilterResult<>(aiResult, buildAiFilterDescriptor());
            } catch (Exception e) {
                return aiFailResult("LLM call threw error: %s".formatted(e.getMessage()));
            }

        } catch (Exception e) {
            log.error("Error when attempting to filter {}.", i, e);
            return new FilterResult<>(
                    AgentModels.AiFilterResult.builder()
                            .successful(false)
                            .errorMessage(e.getMessage())
                            .output(List.of())
                            .build(),
                    new FilterDescriptor.NoOpFilterDescriptor());
        }
    }

    private @NonNull FilterResult<AgentModels.AiFilterResult> aiFailResult(String message) {
        return new FilterResult<>(
                AgentModels.AiFilterResult.builder()
                        .successful(false)
                        .errorMessage(message)
                        .output(List.of())
                        .build(),
                buildAiFilterDescriptor());
    }

    private FilterDescriptor buildAiFilterDescriptor() {
        Map<String, String> details = new LinkedHashMap<>();
        putIfPresent(details, "modelRef", modelRef);
        putIfPresent(details, "registrarPrompt", registrarPrompt);
        putIfPresent(details, "maxTokens", String.valueOf(maxTokens));
        putIfPresent(details, "sessionMode", sessionMode == null ? null : sessionMode.name());
        putIfPresent(details, "sessionKeyOverride", sessionKeyOverride);
        putIfPresent(details, "requestModelType", requestModelType);
        putIfPresent(details, "resultModelType", resultModelType);
        putIfPresent(details, "includeAgentDecorators", includeAgentDecorators == null ? null : includeAgentDecorators.toString());
        putIfPresent(details, "controllerModelRef", controllerModelRef);
        putIfPresent(details, "controllerPromptTemplate", controllerPromptTemplate);
        putIfPresent(details, "timeoutMs", String.valueOf(timeoutMs));
        putIfPresent(details, "configVersion", configVersion);

        FilterDescriptor.Entry entry = new FilterDescriptor.Entry(
                "EXECUTOR",
                null,
                null,
                null,
                null,
                null,
                "TRANSFORMED",
                FilterEnums.ExecutorType.AI.name(),
                details,
                List.of()
        );
        return new FilterDescriptor.SimpleFilterDescriptor(List.of(), entry);
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        target.put(key, value);
    }

    public String modelRef() {
        return modelRef;
    }

    public String promptTemplate() {
        return promptTemplate;
    }

    public int maxTokens() {
        return maxTokens;
    }

    public String registrarPrompt() {
        return registrarPrompt;
    }

    public Object outputSchema() {
        return outputSchema;
    }

    public SessionMode sessionMode() {
        return sessionMode;
    }

    public String sessionKeyOverride() {
        return sessionKeyOverride;
    }

    public String requestModelType() {
        return requestModelType;
    }

    public String resultModelType() {
        return resultModelType;
    }

    public Boolean includeAgentDecorators() {
        return includeAgentDecorators;
    }

    public String controllerModelRef() {
        return controllerModelRef;
    }

    public String controllerPromptTemplate() {
        return controllerPromptTemplate;
    }

    public String responseMode() {
        return responseMode;
    }

    @Override
    public int timeoutMs() {
        return timeoutMs;
    }

    @Override
    public String configVersion() {
        return configVersion;
    }

}
