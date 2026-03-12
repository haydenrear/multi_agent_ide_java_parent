package com.hayden.multiagentide.transformation.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TransformerExecutorValidator {

    public List<String> validate(JsonNode executorNode) {
        List<String> errors = new ArrayList<>();
        if (executorNode == null || executorNode.isNull()) {
            errors.add("Executor configuration is required");
            return errors;
        }
        String executorType = executorNode.path("executorType").asText("");
        if (executorType.isBlank()) {
            errors.add("executorType is required");
            return errors;
        }
        if (executorNode.path("timeoutMs").asInt(0) <= 0) {
            errors.add("timeoutMs must be > 0");
        }
        switch (executorType) {
            case "PYTHON" -> requireNonBlank(executorNode, "scriptPath", errors);
            case "AI_TRANSFORMER", "AI" -> {
                requireNonBlank(executorNode, "modelRef", errors);
                validateSessionMode(executorNode, errors);
            }
            case "BINARY", "JAVA_FUNCTION" -> {
            }
            default -> errors.add("Unknown executorType: " + executorType);
        }
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
            errors.add("AI transformer executor sessionMode must be one of "
                    + "[PER_INVOCATION, SAME_SESSION_FOR_ALL, SAME_SESSION_FOR_ACTION, SAME_SESSION_FOR_AGENT]");
        }
    }
}
