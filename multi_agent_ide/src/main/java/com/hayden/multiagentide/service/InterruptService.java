package com.hayden.multiagentide.service;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.model.nodes.GraphNode;
import com.hayden.multiagentidelib.model.nodes.InterruptContext;
import com.hayden.multiagentidelib.model.nodes.InterruptNode;
import com.hayden.multiagentidelib.model.nodes.Interruptible;
import com.hayden.multiagentidelib.model.nodes.ReviewNode;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterruptService {

    private static final String REVIEW_CRITERIA =
            "Review for correctness and completeness. Reply with approved if correct, otherwise explain issues.";
    public static final String INTERRUPT_ID_NOT_FOUND = "interrupt_id_not_found";

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
            ToolContext toolContext,
            Class<T> routingClass
    ) {
        return switch (request.type()) {
            case HUMAN_REVIEW, AGENT_REVIEW, PAUSE -> {
                String feedback = resolveInterruptFeedback(context, request, originNode, promptContext);
                
                Map<String, Object> modelWithFeedback = new java.util.HashMap<>(templateModel);
                modelWithFeedback.put("interruptFeedback", feedback);

                yield llmRunner.runWithTemplate(
                        templateName,
                        promptContext,
                        modelWithFeedback,
                        toolContext,
                        routingClass,
                        context
                );
            }
            case BRANCH, STOP, PRUNE -> llmRunner.runWithTemplate(
                    templateName,
                    promptContext,
                    templateModel,
                    toolContext,
                    routingClass,
                    context
            );
        };
    }

    public <T> T handleInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest request,
            GraphNode originNode,
            String templateName,
            PromptContext promptContext,
            Map<String, Object> templateModel,
            Class<T> routingClass
    ) {
        return handleInterrupt(
                context,
                request,
                originNode,
                templateName,
                promptContext,
                templateModel,
                ToolContext.empty(),
                routingClass
        );
    }

    public PermissionGate.InterruptResolution awaitHumanReview(
            AgentModels.InterruptRequest request,
            @Nullable GraphNode originNodeId
    ) {
        return resolveInterruptHumanAgent(request, originNodeId);
    }

    private PermissionGate.InterruptResolution resolveInterruptHumanAgent(
            @jakarta.annotation.Nullable AgentModels.InterruptRequest request,
            @jakarta.annotation.Nullable GraphNode originNode
    ) {
        if (request == null)
            return permissionGate.invalidInterrupt(INTERRUPT_ID_NOT_FOUND);

        InterruptData result = getInterruptData(request, originNode);
        var interruptId = result.interruptId;
        var reviewContent = result.reviewContent;
        if (interruptId.isBlank()) {
            return permissionGate.invalidInterrupt(INTERRUPT_ID_NOT_FOUND);
        }
        permissionGate.publishInterrupt(
                interruptId,
                originNode != null ? originNode.nodeId() : interruptId,
                request.type(),
                reviewContent
        );
        PermissionGate.InterruptResolution resolution =
                permissionGate.awaitInterruptBlocking(interruptId);
        return resolution;
    }

    private String resolveInterruptFeedback(
            OperationContext context,
            @jakarta.annotation.Nullable AgentModels.InterruptRequest request,
            @jakarta.annotation.Nullable GraphNode originNode,
            PromptContext promptContext
    ) {
        InterruptData result = getInterruptData(request, originNode);
        if (request == null || request.type() == null) {
            var resolution = permissionGate.invalidInterrupt(result.interruptId());
            String feedback = resolution.getResolutionNotes();
            return firstNonBlank(feedback, result.reviewContent());
        }
        return switch(request.type()) {
            case HUMAN_REVIEW, PAUSE -> {
                PermissionGate.InterruptResolution resolution =
                        resolveInterruptHumanAgent(request, originNode);
                String feedback = resolution != null ? resolution.getResolutionNotes() : null;
                yield firstNonBlank(feedback, result.reviewContent());
            }
            case AGENT_REVIEW, STOP, BRANCH, PRUNE -> {
                if (result.interruptId().isBlank()) {
                    yield result.reviewContent();
                }
                permissionGate.publishInterrupt(
                        result.interruptId(),
                        originNode != null ? originNode.nodeId() : result.interruptId(),
                        request.type(),
                        result.reviewContent()
                );
                AgentModels.ReviewAgentResult reviewResult =
                        runInterruptAgentReview(context, promptContext, result.reviewContent());
                String feedback = reviewResult != null ? reviewResult.output() : "";
                permissionGate.resolveInterrupt(result.interruptId(), "agent-review", feedback, reviewResult);
                yield  feedback;
            }
        };
    }

    private @NonNull InterruptData getInterruptData(@jakarta.annotation.Nullable AgentModels.InterruptRequest request,
                                                    @jakarta.annotation.Nullable GraphNode originNode) {
        InterruptContext interruptContext = resolveInterruptContext(originNode);
        String reviewContent = firstNonBlank(
                request != null ? request.reason() : null,
                interruptContext != null ? interruptContext.resultPayload() : null
        );
        String interruptId = firstNonBlank(
                interruptContext != null ? interruptContext.interruptNodeId() : null,
                originNode != null ? originNode.nodeId() : null,
                INTERRUPT_ID_NOT_FOUND
        );
        InterruptData result = new InterruptData(reviewContent, interruptId);
        return result;
    }

    private record InterruptData(String reviewContent, String interruptId) {
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
                ToolContext.empty(),
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
