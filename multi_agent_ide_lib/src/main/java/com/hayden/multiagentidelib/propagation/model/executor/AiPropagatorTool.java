package com.hayden.multiagentidelib.propagation.model.executor;

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
import com.hayden.multiagentidelib.propagation.model.layer.AiPropagatorContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AiPropagatorTool
        implements ExecutableTool<AgentModels.AiPropagatorRequest, AgentModels.AiPropagatorResult, AiPropagatorContext> {

    public static final String TEMPLATE_NAME = "propagation/ai_propagator";

    @Schema(description = "Specific guidance for this propagator — what it should look for and flag. Distinct from the default OOD coverage already applied to every action.", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String registrarPrompt;

    @Schema(description = "Session reuse strategy. PER_INVOCATION=fresh session each time; SAME_SESSION_FOR_ACTION=reuse within one action pair; SAME_SESSION_FOR_ALL=single shared session.", allowableValues = {"PER_INVOCATION", "SAME_SESSION_FOR_ACTION", "SAME_SESSION_FOR_ALL", "SAME_SESSION_FOR_AGENT"})
    private final AiFilterTool.SessionMode sessionMode;

    @Schema(description = "Optional config version for cache-busting propagator state.")
    private final String configVersion;

    @Autowired
    @JsonIgnore
    private LlmRunner llmRunner;

    @JsonCreator
    public AiPropagatorTool(
            @JsonProperty("registrarPrompt") String registrarPrompt,
            @JsonProperty("sessionMode") AiFilterTool.SessionMode sessionMode,
            @JsonProperty("configVersion") String configVersion) {
        this.registrarPrompt = registrarPrompt;
        this.sessionMode = sessionMode;
        this.configVersion = configVersion;
    }

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
