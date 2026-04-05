package com.hayden.multiagentide.model.executor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.filter.model.executor.ExecutableTool;
import com.hayden.multiagentide.filter.model.executor.AiFilterTool;
import com.hayden.multiagentide.filter.service.FilterDescriptor;
import com.hayden.multiagentide.filter.service.FilterResult;
import com.hayden.multiagentide.llm.AgentLlmExecutor;
import com.hayden.multiagentide.llm.AgentLlmExecutor.DirectExecutorArgs;
import com.hayden.multiagentide.model.layer.AiTransformerContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AiTransformerTool implements ExecutableTool<AgentModels.AiTransformerRequest, AgentModels.AiTransformerResult, AiTransformerContext> {

    public static final String TEMPLATE_NAME = "transformation/ai_transformer";

    @Schema(description = "Specific guidance for this transformer — what it should transform and how.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String registrarPrompt;

    @Schema(description = "Session reuse strategy. PER_INVOCATION=fresh session each time; SAME_SESSION_FOR_ACTION=reuse within one action pair; SAME_SESSION_FOR_ALL=single shared session.", allowableValues = {"PER_INVOCATION", "SAME_SESSION_FOR_ACTION", "SAME_SESSION_FOR_ALL", "SAME_SESSION_FOR_AGENT"})
    private AiFilterTool.SessionMode sessionMode;

    @Schema(description = "Optional config version for cache-busting transformer state.")
    private String configVersion;

    @Autowired
    @JsonIgnore
    private AgentLlmExecutor agentLlmExecutor;

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
        if (ctx == null || ctx.promptContext() == null || agentLlmExecutor == null) {
            return fail("AI transformer context is not fully initialized", input);
        }
        String templateName = ctx.templateName() != null && !ctx.templateName().isBlank()
                ? ctx.templateName()
                : TEMPLATE_NAME;
        try {
            AgentModels.AiTransformerResult result = agentLlmExecutor.runDirect(
                    DirectExecutorArgs.<AgentModels.AiTransformerResult>builder()
                            .responseClazz(AgentModels.AiTransformerResult.class)
                            .agentName(AgentInterfaces.AGENT_NAME_AI_TRANSFORMER)
                            .actionName(AgentInterfaces.ACTION_AI_TRANSFORMER)
                            .methodName(AgentInterfaces.METHOD_AI_TRANSFORMER)
                            .template(templateName)
                            .promptContext(ctx.promptContext())
                            .templateModel(ctx.model() == null ? Map.of() : ctx.model())
                            .toolContext(ctx.toolContext())
                            .operationContext(ctx.context())
                            .build());
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

    @JsonIgnore
    public String templateName() {
        return TEMPLATE_NAME;
    }
}
