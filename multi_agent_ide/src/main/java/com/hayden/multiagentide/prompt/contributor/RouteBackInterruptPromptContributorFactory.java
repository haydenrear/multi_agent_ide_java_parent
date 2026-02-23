package com.hayden.multiagentide.prompt.contributor;

import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.model.nodes.*;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouteBackInterruptPromptContributorFactory implements PromptContributorFactory {

    private final GraphRepository graphRepository;

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        if (context.currentRequest() instanceof AgentModels.InterruptRequest) {
            return List.of();
        }

        if (!(context.currentRequest() instanceof AgentModels.HasRouteBack)) {
            return List.of();
        }

        // If previous request was an interrupt, the collector is being called back after review.
        // Provide the post-review translation prompt with the actual review feedback.
        var req = BlackboardHistory.getLastMatching(
                context.operationContext().getAgentProcess(),
                s -> {
                    if (!Objects.equals(s.inputType(), context.currentRequest().getClass())) {
                        return Optional.of(s.input());
                    }

                    return Optional.empty();
                });

        if (req instanceof AgentModels.InterruptRequest interruptRequest) {
            String originalRequest = resolveOriginalRequest(context);
            String reviewResponse = resolveReviewResponse(context, interruptRequest);
            return List.of(new RouteBackReviewReceivedPromptContributor(originalRequest, reviewResponse));
        }

        return List.of(new RouteBackInterruptPromptContributor());
    }

    /**
     * Extract the original interrupt request details from the previous request.
     */
    private String resolveOriginalRequest(PromptContext context) {
        if (!(context.previousRequest() instanceof AgentModels.InterruptRequest interruptRequest)) {
            return "(Original request not available)";
        }
        var sb = new StringBuilder();
        if (interruptRequest.reason() != null && !interruptRequest.reason().isBlank()) {
            sb.append("Reason: ").append(interruptRequest.reason()).append("\n");
        }
        if (interruptRequest.contextForDecision() != null && !interruptRequest.contextForDecision().isBlank()) {
            sb.append("Context for Decision: ").append(interruptRequest.contextForDecision()).append("\n");
        }
        if (interruptRequest.choices() != null && !interruptRequest.choices().isEmpty()) {
            sb.append("Choices Offered: ").append(String.join(", ", interruptRequest.choices().stream().map(AgentModels.InterruptRequest.StructuredChoice::prettyPrint).toList())).append("\n");
        }
        return sb.isEmpty() ? "(No details in original request)" : sb.toString();
    }

    /**
     * Look up the review response from the graph. The origin node (collector) stores an
     * InterruptContext with interruptNodeId pointing to the ReviewNode that holds the
     * reviewer's feedback and resolution status.
     */
    private String resolveReviewResponse(PromptContext context, AgentModels.InterruptRequest interruptRequest) {
        if (context.currentContextId() == null) {
            return null;
        }

        String originNodeId = interruptRequest.key().value();
        GraphNode interruptNode = graphRepository.findInterruptByOrigin(originNodeId).orElse(null);
        InterruptContext interruptContext;

        if (interruptNode instanceof ReviewNode reviewNode) {
            interruptContext = reviewNode.interruptContext();
            var sb = new StringBuilder();

            sb.append("Resolution Type: ").append(reviewNode.approved() ? "APPROVED" : "REJECTED").append("\n");

            if (reviewNode.agentFeedback() != null && !reviewNode.agentFeedback().isBlank()) {
                sb.append("Feedback: ").append(reviewNode.agentFeedback()).append("\n");
            }

            if (reviewNode.reviewResult() != null) {
                var result = reviewNode.reviewResult();
                if (result.assessmentStatus() != null && !result.assessmentStatus().isBlank()) {
                    sb.append("Assessment Status: ").append(result.assessmentStatus()).append("\n");
                }
                if (result.feedback() != null && !result.feedback().isBlank()) {
                    sb.append("Review Feedback: ").append(result.feedback()).append("\n");
                }
                if (result.suggestions() != null && !result.suggestions().isEmpty()) {
                    sb.append("Suggestions: ").append(String.join("; ", result.suggestions())).append("\n");
                }
                if (result.output() != null && !result.output().isBlank()) {
                    sb.append("Review Output: ").append(result.output()).append("\n");
                }
            }

            return sb.isEmpty() ? "(No review details available)" : sb.toString();
        } else if (interruptNode instanceof InterruptNode i) {
            log.error("Received interrupt.");
            interruptContext = i.interruptContext();
        } else {
            return null;
        }

        // Fallback: check resultPayload on the interrupt context itself
        if (interruptContext.resultPayload() != null && !interruptContext.resultPayload().isBlank()) {
            return interruptContext.resultPayload();
        }

        log.debug("Could not resolve review feedback for interrupt node {}.", interruptContext.interruptNodeId());
        return null;
    }

    /**
     * Pre-review prompt: tells the collector how to request review before routing back.
     * Emitted when a HasRouteBack collector has NOT just come from an interrupt review.
     */
    public record RouteBackInterruptPromptContributor() implements PromptContributor {

        private static final String TEMPLATE = """
                ## Route-Back Review Protocol

                If you are considering `ROUTE_BACK`, you MUST follow this protocol:

                ### Step 1: Request Review (first call)
                Do NOT set collectorResult with ROUTE_BACK directly. Instead, in your structured JSON response,
                return a result with only the `interruptRequest` field populated:
                - type: AGENT_REVIEW
                - reason: why you believe routing back is needed
                - contextForDecision: what specific gaps remain and what the reviewer should evaluate
                - choices: provide structured options (e.g., "Approve route-back to [phase]" / "Advance to next phase instead" / "Stop workflow")
                Leave collectorResult null — returning only interruptRequest in the JSON routes to the review handler.

                ### Step 2: Interpret Review Result (after review callback)
                After the reviewer responds, you will be called again with their feedback. Based on the review:
                - If the reviewer **approves** routing back: set collectorResult with collectorDecision.decisionType = ROUTE_BACK,
                  including all relevant context so the phase being routed back to can proceed effectively.
                - If the reviewer **rejects** routing back: set collectorResult with collectorDecision.decisionType = ADVANCE_PHASE,
                  filling in all fields needed for the next phase to succeed with the current results.

                ### Why This Matters
                The interruptRequest field is the FIRST field in your routing result. If you set it, it takes priority
                and the system routes to the interrupt handler. If you set collectorResult with ROUTE_BACK instead, the branch
                handler routes back immediately without review — bypassing the safeguard.
                """;

        @Override
        public String name() {
            return "route-back-interrupt-guardrail";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            return TEMPLATE;
        }

        @Override
        public String template() {
            return TEMPLATE;
        }

        @Override
        public int priority() {
            return 45;
        }
    }

    /**
     * Post-review prompt: tells the collector to translate the review feedback into a routing decision.
     * Emitted when a HasRouteBack collector has just returned from an interrupt review.
     * The original request and review response are injected as dynamic content while the
     * instructional text remains static for hashing.
     */
    public record RouteBackReviewReceivedPromptContributor(String originalRequest, String reviewResponse) implements PromptContributor {

        private static final String TEMPLATE = """
                ## Route-Back Review Received — Translate to Routing Decision

                You previously requested a review before routing back. Below is your original request
                and the reviewer's response. You must now translate the review into a routing decision.

                ### Your Original Review Request
                {{ original_request }}

                ### Reviewer's Response
                {{ review_response }}

                ### Instructions
                Based on the reviewer's decision above:

                - **If the resolution type is APPROVED (reviewer approves routing back):**
                  In your structured JSON response, return `collectorResult` with `collectorDecision.decisionType = ROUTE_BACK`.
                  Include all relevant context (goal, gaps, prior results) so the phase being routed back to
                  can proceed effectively without losing information.

                - **If the resolution type is REJECTED (reviewer rejects routing back):**
                  In your structured JSON response, return `collectorResult` with `collectorDecision.decisionType = ADVANCE_PHASE`.
                  Fill in all fields needed for the next phase to succeed with the best available results.
                  Do not leave fields empty — summarize what was accomplished and note any limitations.

                - **If the reviewer requests stopping:**
                  In your structured JSON response, return `collectorResult` with `collectorDecision.decisionType = STOP`.

                Do NOT request another interrupt. Translate the review into a concrete `collectorResult` in your JSON response now.
                """;

        @Override
        public String name() {
            return "route-back-review-received";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            String origReq = originalRequest != null ? originalRequest : "(No original request available)";
            String revResp = reviewResponse != null ? reviewResponse : "(No review response available)";
            return TEMPLATE
                    .replace("{{ original_request }}", origReq)
                    .replace("{{ review_response }}", revResp);
        }

        @Override
        public String template() {
            return TEMPLATE;
        }

        @Override
        public int priority() {
            return 45;
        }
    }
}
