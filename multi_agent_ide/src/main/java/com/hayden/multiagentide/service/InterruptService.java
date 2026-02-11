package com.hayden.multiagentide.service;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.decorator.prompt.PromptContextDecorator;
import com.hayden.multiagentide.agent.decorator.prompt.ToolContextDecorator;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContextFactory;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.model.nodes.GraphNode;
import com.hayden.multiagentidelib.model.nodes.InterruptContext;
import com.hayden.multiagentidelib.model.nodes.InterruptNode;
import com.hayden.multiagentidelib.model.nodes.Interruptible;
import com.hayden.multiagentidelib.model.nodes.ReviewNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterruptService {

    private static final String REVIEW_CRITERIA =
            "Review for correctness and completeness. Reply with approved if correct, otherwise explain issues.";
    private static final String TEMPLATE_WORKFLOW_REVIEW = "workflow/review";
    private static final String TEMPLATE_REVIEW_RESOLUTION = "workflow/review_resolution";
    private static final String AGENT_NAME = "interrupt-service";
    private static final String ACTION_AGENT_REVIEW = "agent-review";
    private static final String METHOD_RUN_INTERRUPT_AGENT_REVIEW = "runInterruptAgentReview";
    public static final String INTERRUPT_ID_NOT_FOUND = "interrupt_id_not_found";

    private final PermissionGate permissionGate;
    private final LlmRunner llmRunner;
    private final PromptContextFactory promptContextFactory;
    private final List<PromptContextDecorator> promptContextDecorators;
    private final List<ToolContextDecorator> toolContextDecorators;

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
                log.info("Handling feedback from AI.");
                String feedback = resolveInterruptFeedback(context, request, originNode, promptContext);
                log.info("Resolved feedback: {}.", feedback);

                Map<String, Object> modelWithFeedback = new java.util.HashMap<>(templateModel);
                modelWithFeedback.put("interruptFeedback", feedback);
                promptContext = promptContextFactory.build(
                        AgentType.REVIEW_RESOLUTION_AGENT,
                        promptContext.currentRequest(),
                        promptContext.previousRequest(),
                        promptContext.currentRequest(),
                        promptContext.blackboardHistory(),
                        TEMPLATE_REVIEW_RESOLUTION,
                        modelWithFeedback
                );

                toolContext = AgentInterfaces.decorateToolContext(
                        toolContext,
                        request,
                        promptContext.previousRequest(),
                        context,
                        toolContextDecorators,
                        AGENT_NAME,
                        ACTION_AGENT_REVIEW,
                        METHOD_RUN_INTERRUPT_AGENT_REVIEW
                );

                var s = llmRunner.runWithTemplate(
                        TEMPLATE_REVIEW_RESOLUTION,
                        promptContext,
                        modelWithFeedback,
                        toolContext,
                        routingClass,
                        context
                );

                log.info("After feedback handled: {}, {}.", s.getClass().getSimpleName(), s);
                yield s;
            }
            case BRANCH, STOP, PRUNE -> {
//
                log.error("Received branch, stop, prune, unexpectedly - not implemented..");
                Map<String, Object> modelWithFeedback = new java.util.HashMap<>(templateModel);
                modelWithFeedback.put("interruptFeedback", """
                        Please route back to the orchestrator with instructions to use best judgement and recommended approach.
                        """);
                yield llmRunner.runWithTemplate(
                    TEMPLATE_REVIEW_RESOLUTION,
                    promptContext,
                    templateModel,
                    toolContext,
                    routingClass,
                    context
            );
            }
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
                        runInterruptAgentReview(context, promptContext, result, request);
                String feedback = reviewResult != null ? reviewResult.output() : "";
                permissionGate.resolveInterrupt(result.interruptId(), "agent-review", feedback, reviewResult);
                yield feedback;
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
            PromptContext callerPromptContext,
            InterruptData result,
            AgentModels.InterruptRequest request
    ) {
        BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);

        // Rebuild prompt context with the interrupt request as currentRequest so that
        // prompt contributor factories (e.g. InterruptPromptContributorFactory,
        // WorktreeSandboxPromptContributorFactory) can see it and contribute.
        PromptContext promptContext = promptContextFactory.build(
                AgentType.REVIEW_AGENT,
                callerPromptContext.previousRequest(),
                callerPromptContext.previousRequest(),
                request,
                history,
                TEMPLATE_WORKFLOW_REVIEW,
                callerPromptContext.model()
        );

        promptContext = AgentInterfaces.decoratePromptContext(
                promptContext,
                context,
                promptContextDecorators,
                AGENT_NAME,
                ACTION_AGENT_REVIEW,
                METHOD_RUN_INTERRUPT_AGENT_REVIEW,
                callerPromptContext.previousRequest(),
                request
        );

        ToolContext toolContext = AgentInterfaces.decorateToolContext(
                ToolContext.empty(),
                request,
                callerPromptContext.previousRequest(),
                context,
                toolContextDecorators,
                AGENT_NAME,
                ACTION_AGENT_REVIEW,
                METHOD_RUN_INTERRUPT_AGENT_REVIEW
        );

        AgentModels.ReviewAgentResult routing = llmRunner.runWithTemplate(
                TEMPLATE_WORKFLOW_REVIEW,
                promptContext,
                Map.of(
                        "content", Objects.toString(result.reviewContent, ""),
                        "criteria", REVIEW_CRITERIA,
                        "returnRoute", "interrupt"
                ),
                toolContext,
                AgentModels.ReviewAgentResult.class,
                context
        );

        return routing;
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
