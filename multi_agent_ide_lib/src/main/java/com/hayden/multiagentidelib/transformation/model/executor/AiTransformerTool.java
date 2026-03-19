package com.hayden.multiagentidelib.transformation.model.executor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.filter.model.executor.ExecutableTool;
import com.hayden.multiagentidelib.filter.model.executor.AiFilterTool;
import com.hayden.multiagentidelib.filter.service.FilterDescriptor;
import com.hayden.multiagentidelib.filter.service.FilterResult;
import com.hayden.multiagentidelib.llm.LlmRunner;
import com.hayden.multiagentidelib.transformation.model.layer.AiTransformerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AiTransformerTool implements ExecutableTool<AgentModels.AiTransformerRequest, AgentModels.AiTransformerResult, AiTransformerContext> {

    public static final String TEMPLATE_NAME = "transformation/ai_transformer";

    private final String registrarPrompt;
    private final AiFilterTool.SessionMode sessionMode;
    private final String configVersion;

    @Autowired
    @JsonIgnore
    private LlmRunner llmRunner;

    @JsonCreator
    public AiTransformerTool(
            @JsonProperty("registrarPrompt") String registrarPrompt,
            @JsonProperty("sessionMode") AiFilterTool.SessionMode sessionMode,
            @JsonProperty("configVersion") String configVersion) {
        this.registrarPrompt = registrarPrompt;
        this.sessionMode = sessionMode;
        this.configVersion = configVersion;
    }

    @Override
    public FilterEnums.ExecutorType executorType() {
        return FilterEnums.ExecutorType.AI_TRANSFORMER;
    }

    @Override
    public FilterResult<AgentModels.AiTransformerResult> apply(AgentModels.AiTransformerRequest input, AiTransformerContext ctx) {
        if (ctx == null || ctx.promptContext() == null || llmRunner == null) {
            return fail("AI transformer context is not fully initialized", input);
        }
        String templateName = ctx.templateName() != null && !ctx.templateName().isBlank()
                ? ctx.templateName()
                : TEMPLATE_NAME;
        try {
            AgentModels.AiTransformerResult result = llmRunner.runWithTemplate(
                    templateName,
                    ctx.promptContext(),
                    ctx.model() == null ? Map.of() : ctx.model(),
                    ctx.toolContext(),
                    AgentModels.AiTransformerResult.class,
                    ctx.context()
            );
            if (result == null) {
                return fail("AI transformer returned null", input);
            }
            return new FilterResult<>(result, descriptor());
        } catch (Exception e) {
            log.error("AI transformer execution failed", e);
            return fail(e.getMessage(), input);
        }
    }

    private FilterResult<AgentModels.AiTransformerResult> fail(String message, AgentModels.AiTransformerRequest input) {
        return new FilterResult<>(AgentModels.AiTransformerResult.builder()
                .successful(false)
                .transformedText(input == null ? null : input.input())
                .errorMessage(message)
                .build(), descriptor());
    }

    private FilterDescriptor descriptor() {
        Map<String, String> details = new LinkedHashMap<>();
        if (registrarPrompt != null && !registrarPrompt.isBlank()) details.put("registrarPrompt", registrarPrompt);
        if (sessionMode != null) details.put("sessionMode", sessionMode.name());
        details.put("templateName", TEMPLATE_NAME);
        if (configVersion != null && !configVersion.isBlank()) details.put("configVersion", configVersion);
        return new FilterDescriptor.SimpleFilterDescriptor(
                java.util.List.of(),
                new FilterDescriptor.Entry(
                        "EXECUTOR", null, null, null, null, null,
                        "TRANSFORMED",
                        executorType().name(),
                        details,
                        java.util.List.of()
                )
        );
    }

    @Override
    public String configVersion() {
        return configVersion;
    }

    public String registrarPrompt() {
        return registrarPrompt;
    }

    public AiFilterTool.SessionMode sessionMode() {
        return sessionMode;
    }

    public String templateName() {
        return TEMPLATE_NAME;
    }
}
