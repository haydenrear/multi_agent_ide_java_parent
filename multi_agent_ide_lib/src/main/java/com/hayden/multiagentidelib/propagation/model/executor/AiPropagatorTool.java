package com.hayden.multiagentidelib.propagation.model.executor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.filter.model.executor.ExecutableTool;
import com.hayden.multiagentidelib.filter.model.executor.AiFilterTool;
import com.hayden.multiagentidelib.filter.service.FilterDescriptor;
import com.hayden.multiagentidelib.filter.service.FilterResult;
import com.hayden.multiagentidelib.llm.LlmRunner;
import com.hayden.multiagentidelib.propagation.model.layer.AiPropagatorContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AiPropagatorTool
        implements ExecutableTool<AgentModels.AiPropagatorRequest, AgentModels.AiPropagatorResult, AiPropagatorContext> {

    public static final String TEMPLATE_NAME = "propagation/ai_propagator";

    private final String registrarPrompt;
    private final AiFilterTool.SessionMode sessionMode;
    private final String configVersion;

    @Autowired
    private LlmRunner llmRunner;

    @Override
    public FilterEnums.ExecutorType executorType() {
        return FilterEnums.ExecutorType.AI_PROPAGATOR;
    }

    @Override
    public FilterResult<AgentModels.AiPropagatorResult> apply(AgentModels.AiPropagatorRequest input, AiPropagatorContext ctx) {
        if (ctx == null || ctx.promptContext() == null || llmRunner == null) {
            return fail("AI propagator context is not fully initialized", input);
        }
        String templateName = ctx.templateName() != null && !ctx.templateName().isBlank()
                ? ctx.templateName()
                : TEMPLATE_NAME;
        try {
            AgentModels.AiPropagatorResult result = llmRunner.runWithTemplate(
                    templateName,
                    ctx.promptContext(),
                    ctx.model() == null ? Map.of() : ctx.model(),
                    ctx.toolContext(),
                    AgentModels.AiPropagatorResult.class,
                    ctx.context()
            );
            if (result == null) {
                return fail("AI propagator returned null", input);
            }
            return new FilterResult<>(result, descriptor());
        } catch (Exception e) {
            log.error("AI propagator execution failed", e);
            return fail(e.getMessage(), input);
        }
    }

    private FilterResult<AgentModels.AiPropagatorResult> fail(String message, AgentModels.AiPropagatorRequest input) {
        return new FilterResult<>(AgentModels.AiPropagatorResult.builder()
                .successful(false)
                .propagatedText(input == null ? null : input.input())
                .summaryText(input == null ? null : input.input())
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
