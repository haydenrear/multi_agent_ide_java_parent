package com.hayden.multiagentide.service;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.utilitymodule.acp.events.Events;
import com.hayden.multiagentidelib.model.nodes.GraphNode;
import com.hayden.multiagentidelib.model.nodes.InterruptContext;
import com.hayden.multiagentidelib.model.nodes.InterruptNode;
import com.hayden.multiagentidelib.model.nodes.Interruptible;
import com.hayden.multiagentidelib.model.nodes.ReviewNode;

import java.util.Map;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterruptService {

    private static final String REVIEW_CRITERIA =
            "Review for correctness and completeness. Reply with approved if correct, otherwise explain issues.";

    private final PermissionGate permissionGate;
    private final LlmRunner llmRunner;

    /**
     * Handle review interrupt using Jinja templates and PromptContext.
     * 
     * @param context The operation context
     * @param request The interrupt request
     * @param originNode The originating graph node
     * @param templateName The Jinja template name (e.g., "workflow/orchestrator")
     * @param promptContext The prompt context for contributors
     * @param templateModel The model data for template rendering
     * @param routingClass The expected routing class
     * @param <T> The routing type
     * @return Optional routing result if interrupt was handled
     */
    public <T> T handleInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest request,
            GraphNode originNode,
            String templateName,
            PromptContext promptContext,
            Map<String, Object> templateModel,
            Class<T> routingClass
    ) {
        return switch (request.type()) {
            case HUMAN_REVIEW, AGENT_REVIEW -> {
                String feedback = resolveInterruptFeedback(context, request, originNode, promptContext);
                
                Map<String, Object> modelWithFeedback = new java.util.HashMap<>(templateModel);
                modelWithFeedback.put("interruptFeedback", feedback);

                yield llmRunner.runWithTemplate(
                        templateName,
                        promptContext,
                        modelWithFeedback,
                        routingClass,
                        context
                );
            }
            default -> llmRunner.runWithTemplate(
                    templateName,
                    promptContext,
                    templateModel,
                    routingClass,
                    context
            );
        };
    }



    private String resolveInterruptFeedback(
            OperationContext context,
            AgentModels.InterruptRequest request,
            GraphNode originNode,
            PromptContext promptContext
    ) {
        InterruptContext interruptContext = resolveInterruptContext(originNode);
        String reviewContent = firstNonBlank(
                request != null ? request.reason() : null,
                interruptContext != null ? interruptContext.resultPayload() : null
        );
        String interruptId = firstNonBlank(
                interruptContext != null ? interruptContext.interruptNodeId() : null,
                originNode != null ? originNode.nodeId() : null
        );
        if (interruptId.isBlank()) {
            return reviewContent;
        }
        permissionGate.publishInterrupt(
                interruptId,
                originNode != null ? originNode.nodeId() : interruptId,
                request.type(),
                reviewContent
        );
        if (request.type() == Events.InterruptType.HUMAN_REVIEW) {
            PermissionGate.InterruptResolution resolution =
                    permissionGate.awaitInterruptBlocking(interruptId);
            String feedback = resolution != null ? resolution.getResolutionNotes() : null;
            return firstNonBlank(feedback, reviewContent);
        }
        AgentModels.ReviewAgentResult reviewResult =
                runInterruptAgentReview(context, promptContext, reviewContent);
        String feedback = reviewResult != null ? reviewResult.output() : "";
        permissionGate.resolveInterrupt(interruptId, "agent-review", feedback, reviewResult);
        return feedback;
    }
    


    private AgentModels.ReviewAgentResult runInterruptAgentReview(
            OperationContext context,
            PromptContext promptContext,
            String reviewContent
    ) {
        AgentModels.ReviewRouting routing = llmRunner.runWithTemplate(
                "workflow/review",
                promptContext,
                Map.of(
                        "content", Objects.toString(reviewContent, ""),
                        "criteria", REVIEW_CRITERIA,
                        "returnRoute", "interrupt"
                ),
                AgentModels.ReviewRouting.class,
                context
        );
        return routing != null ? routing.reviewResult() : null;
    }
    


    private InterruptContext resolveInterruptContext(GraphNode node) {
        if (node == null) {
            return null;
        }
        if (node instanceof Interruptible interruptible) {
            return interruptible.interruptibleContext();
        }
        if (node instanceof ReviewNode reviewNode) {
            return reviewNode.interruptContext();
        }
        if (node instanceof InterruptNode interruptNode) {
            return interruptNode.interruptContext();
        }
        return null;
    }

    private static String appendInterruptFeedback(String prompt, String feedback) {
        if (feedback == null || feedback.isBlank()) {
            return prompt;
        }
        return prompt + "\n\nReview feedback:\n" + feedback;
    }


    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
