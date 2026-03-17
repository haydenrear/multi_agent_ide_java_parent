package com.hayden.multiagentide.propagation.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PropagatorExecutorValidator {

    public List<String> validate(JsonNode executorNode) {
        List<String> errors = new ArrayList<>();
        if (executorNode == null || executorNode.isNull()) {
            errors.add("Executor configuration is required");
            return errors;
        }
        // executor field is strongly typed as AiPropagatorTool — validate its required fields directly
        requireNonBlank(executorNode, "registrarPrompt", errors);
        validateSessionMode(executorNode, errors);
        return errors;
    }

    private void requireNonBlank(JsonNode node, String field, List<String> errors) {
        if (node.path(field).asText("").isBlank()) {
            errors.add(field + " is required");
        }
    }

    private void validateSessionMode(JsonNode node, List<String> errors) {
        if (!node.hasNonNull("sessionMode")) {
            return;
        }
        String sessionMode = node.get("sessionMode").asText("");
        boolean valid = "PER_INVOCATION".equals(sessionMode)
                || "SAME_SESSION_FOR_ALL".equals(sessionMode)
                || "SAME_SESSION_FOR_ACTION".equals(sessionMode)
                || "SAME_SESSION_FOR_AGENT".equals(sessionMode);
        if (!valid) {
            errors.add("AI propagator executor sessionMode must be one of "
                    + "[PER_INVOCATION, SAME_SESSION_FOR_ALL, SAME_SESSION_FOR_ACTION, SAME_SESSION_FOR_AGENT]");
        }
    }
}
