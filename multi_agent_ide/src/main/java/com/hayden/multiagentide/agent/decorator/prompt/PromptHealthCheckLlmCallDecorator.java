package com.hayden.multiagentide.agent.decorator.prompt;

import com.embabel.common.textio.template.CompiledTemplate;
import com.embabel.common.textio.template.TemplateRenderer;
import com.hayden.multiagentide.filter.prompt.FilteredPromptContributorAdapter;
import com.hayden.multiagentide.filter.service.FilterLayerCatalog;
import com.hayden.multiagentide.propagation.service.PropagationExecutionService;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorAdapter;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.propagation.model.PropagatorMatchOn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decorator that fires registered propagators on the {@code prompt-health-check} layer
 * before every LLM call. Propagators attached to this layer receive the full assembled
 * prompt — rendered template first, then each contributor's output in priority order,
 * separated by named dividers — and can flag issues such as:
 * <ul>
 *   <li>Duplicate worktree paths referenced in multiple contributors</li>
 *   <li>Worktree/repository URL ambiguity (agent sees both paths and may choose wrong one)</li>
 *   <li>Repeated content blocks emitted by more than one contributor</li>
 * </ul>
 *
 * To attach a propagator, register it against layer ID {@value FilterLayerCatalog#PROMPT_HEALTH_CHECK}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromptHealthCheckLlmCallDecorator implements LlmCallDecorator {

    @Autowired
    @Lazy
    private PropagationExecutionService propagationExecutionService;

    @Override
    public int order() {
        return 11_000;
    }

    @Override
    public <T> LlmCallContext<T> decorate(LlmCallContext<T> context) {
        if (context == null || context.promptContext() == null) {
            return context;
        }
        // Avoid recursion: do not fire health checks for internal automation LLM calls
        // (AI_PROPAGATOR, AI_FILTER, AI_TRANSFORMER all invoke the LLM themselves).
        var agentType = context.promptContext().agentType();
        if (agentType == com.hayden.multiagentidelib.agent.AgentType.AI_PROPAGATOR
                || agentType == com.hayden.multiagentidelib.agent.AgentType.AI_FILTER
                || agentType == com.hayden.multiagentidelib.agent.AgentType.AI_TRANSFORMER) {
            return context;
        }
        try {
            PromptContext promptContext = context.promptContext();

            String assembledPrompt = assembleFullPrompt(context, promptContext);
            if (assembledPrompt == null || assembledPrompt.isBlank()) {
                return context;
            }

            String sourceNodeId = promptContext.currentContextId() != null
                    ? promptContext.currentContextId().value()
                    : null;
            String sourceName = promptContext.agentType() != null
                    ? promptContext.agentType().name()
                    : "UNKNOWN";

            AgentModels.AiPropagatorRequest payload = AgentModels.AiPropagatorRequest.builder()
                    .input(assembledPrompt)
                    .sourceName(sourceName)
                    .sourceNodeId(sourceNodeId)
                    .goal("prompt-health-check")
                    .build();

            propagationExecutionService.execute(
                    FilterLayerCatalog.PROMPT_HEALTH_CHECK,
                    PropagatorMatchOn.ACTION_REQUEST,
                    payload,
                    sourceNodeId,
                    sourceName,
                    context.op()
            );
        } catch (Exception e) {
            log.warn("Prompt health check propagation failed — skipping", e);
        }
        return context;
    }

    /**
     * Assembles the full prompt text as the agent will see it:
     * the rendered Jinja template first, then each contributor's output
     * in ascending priority order, separated by named dividers.
     */
    private String assembleFullPrompt(LlmCallContext<?> context, PromptContext promptContext) {
        String templateText = renderTemplate(context);

        List<PromptContributor> contributors = unwrapContributors(promptContext);
        contributors.sort(Comparator.comparingInt(PromptContributor::priority));

        StringBuilder sb = new StringBuilder();
        if (templateText != null && !templateText.isBlank()) {
            sb.append(templateText.trim());
        }

        for (int i = 0; i < contributors.size(); i++) {
            PromptContributor pc = contributors.get(i);
            String content;
            try {
                content = pc.contribute(promptContext);
            } catch (Exception e) {
                log.debug("Contributor {} threw during prompt health assembly — skipping", pc.name(), e);
                continue;
            }
            if (content == null || content.isBlank()) {
                continue;
            }
            if (i == 0) {
                sb.append("\n\n--- start [").append(pc.name()).append("] ---\n");
            } else {
                sb.append("\n--- end [").append(contributors.get(i - 1).name()).append("] ");
                sb.append("--- start [").append(pc.name()).append("] ---\n");
            }
            sb.append(content.trim());
        }

        if (!contributors.isEmpty()) {
            sb.append("\n--- end [").append(contributors.getLast().name()).append("] ---");
        }

        return sb.toString();
    }

    private String renderTemplate(LlmCallContext<?> context) {
        try {
            TemplateRenderer renderer = context.op().agentPlatform().getPlatformServices().getTemplateRenderer();
            if (renderer == null) {
                return null;
            }
            CompiledTemplate compiled = renderer.compileLoadedTemplate(context.promptContext().templateName());
            return compiled.render(context.templateArgs());
        } catch (Exception e) {
            log.debug("Could not render template for prompt health check", e);
            return null;
        }
    }

    private List<PromptContributor> unwrapContributors(PromptContext promptContext) {
        List<PromptContributor> result = new ArrayList<>();
        for (var element : promptContext.promptContributors()) {
            if (element instanceof FilteredPromptContributorAdapter f) {
                result.add(f.getContributor());
            } else if (element instanceof PromptContributorAdapter adapter) {
                result.add(adapter.getContributor());
            } else {
                log.debug("Prompt health check: unknown ContextualPromptElement type {} — skipping",
                        element.getClass().getSimpleName());
            }
        }
        return result;
    }

}
