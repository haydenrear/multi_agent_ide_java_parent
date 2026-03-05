package com.hayden.multiagentide.filter.validation;

import com.hayden.multiagentide.filter.controller.dto.PolicyRegistrationRequest;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Semantic validation: matchOn constraints per filter type, layer binding validity, priority warnings.
 */
@Component
@RequiredArgsConstructor
public class PolicySemanticValidator {

    public List<String> validate(FilterEnums.FilterKind filterKind, PolicyRegistrationRequest request) {
        List<String> errors = new ArrayList<>();

        if (request.name() == null || request.name().isBlank()) {
            errors.add("Policy name is required");
        }
        if (request.description() == null || request.description().isBlank()) {
            errors.add("Policy description is required");
        }
        if (request.sourcePath() == null || request.sourcePath().isBlank()) {
            errors.add("Policy sourcePath is required");
        }
        if (request.priority() < 0) {
            errors.add("Priority must be >= 0");
        }
        if (request.layerBindings() == null || request.layerBindings().isEmpty()) {
            errors.add("At least one layer binding is required");
        }

        if (request.layerBindings() != null) {
            for (int i = 0; i < request.layerBindings().size(); i++) {
                validateBinding(filterKind, request.layerBindings().get(i), i, errors);
            }
        }

        return errors;
    }

    private void validateBinding(FilterEnums.FilterKind filterKind,
                                 PolicyRegistrationRequest.LayerBindingRequest binding,
                                 int index, List<String> errors) {
        String prefix = "layerBindings[" + index + "]: ";

        if (binding.layerId() == null || binding.layerId().isBlank()) {
            errors.add(prefix + "layerId is required");
        }
        if (binding.matcherText() == null || binding.matcherText().isBlank()) {
            errors.add(prefix + "matcherText is required");
        }

        // matchOn constraint enforcement
        String matchOn = binding.matchOn();
        if (matchOn != null) {
            switch (filterKind) {
                case JSON_PATH, MARKDOWN_PATH, REGEX_PATH, AI_PATH -> {
                    if (!"PROMPT_CONTRIBUTOR".equals(matchOn) && !"GRAPH_EVENT".equals(matchOn)) {
                        errors.add(prefix + "matchOn must be PROMPT_CONTRIBUTOR or GRAPH_EVENT");
                    }
                }
            }
        } else {
            errors.add(prefix + "matchOn is required");
        }
    }
}
