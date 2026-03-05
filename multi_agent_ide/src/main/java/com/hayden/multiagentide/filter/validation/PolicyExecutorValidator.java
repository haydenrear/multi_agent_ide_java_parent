package com.hayden.multiagentide.filter.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Structural validation of executor config: required fields per executor type, timeoutMs > 0.
 */
@Component
public class PolicyExecutorValidator {

    public List<String> validate(JsonNode executorNode) {
        List<String> errors = new ArrayList<>();

        if (executorNode == null || executorNode.isNull()) {
            errors.add("Executor configuration is required");
            return errors;
        }

        String executorType = executorNode.has("executorType")
                ? executorNode.get("executorType").asText()
                : null;

        if (executorType == null || executorType.isBlank()) {
            errors.add("executorType is required");
            return errors;
        }

        int timeoutMs = executorNode.has("timeoutMs") ? executorNode.get("timeoutMs").asInt(0) : 0;
        if (timeoutMs <= 0) {
            errors.add("timeoutMs must be > 0");
        }

        switch (executorType) {
            case "BINARY" -> validateBinaryExecutor(executorNode, errors);
            case "JAVA_FUNCTION" -> validateJavaFunctionExecutor(executorNode, errors);
            case "PYTHON" -> validatePythonExecutor(executorNode, errors);
            case "AI" -> validateAiExecutor(executorNode, errors);
            default -> errors.add("Unknown executorType: " + executorType);
        }

        return errors;
    }

    private void validateBinaryExecutor(JsonNode node, List<String> errors) {
        if (!node.has("command") || !node.get("command").isArray() || node.get("command").isEmpty()) {
            errors.add("BINARY executor requires non-empty 'command' array");
        }
    }

    private void validateJavaFunctionExecutor(JsonNode node, List<String> errors) {
        requireNonBlank(node, "functionRef", "JAVA_FUNCTION", errors);
    }

    private void validatePythonExecutor(JsonNode node, List<String> errors) {
        requireNonBlank(node, "scriptPath", "PYTHON", errors);
        requireNonBlank(node, "entryFunction", "PYTHON", errors);
    }

    private void validateAiExecutor(JsonNode node, List<String> errors) {
        requireNonBlank(node, "modelRef", "AI", errors);
        requireNonBlank(node, "promptTemplate", "AI", errors);
        if (node.hasNonNull("sessionMode")) {
            String sessionMode = node.get("sessionMode").asText("");
            boolean valid = "PER_INVOCATION".equals(sessionMode)
                    || "SAME_SESSION_FOR_ALL".equals(sessionMode)
                    || "SAME_SESSION_FOR_ACTION".equals(sessionMode)
                    || "SAME_SESSION_FOR_AGENT".equals(sessionMode);
            if (!valid) {
                errors.add("AI executor sessionMode must be one of "
                        + "PER_INVOCATION, SAME_SESSION_FOR_ALL, SAME_SESSION_FOR_ACTION, "
                        + "SAME_SESSION_FOR_AGENT");
            }
        }
    }

    private void requireNonBlank(JsonNode node, String field, String executorType, List<String> errors) {
        if (!node.has(field) || node.get(field).asText("").isBlank()) {
            errors.add(executorType + " executor requires non-blank '" + field + "'");
        }
    }
}
