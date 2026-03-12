package com.hayden.multiagentide.transformation.validation;

import com.hayden.multiagentide.filter.service.FilterLayerCatalog;
import com.hayden.multiagentide.transformation.controller.dto.TransformerRegistrationRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TransformerSemanticValidator {

    public List<String> validate(TransformerRegistrationRequest request) {
        List<String> errors = new ArrayList<>();
        if (request.name() == null || request.name().isBlank()) errors.add("Transformer name is required");
        if (request.description() == null || request.description().isBlank()) errors.add("Transformer description is required");
        if (request.sourcePath() == null || request.sourcePath().isBlank()) errors.add("Transformer sourcePath is required");
        if (request.priority() < 0) errors.add("Priority must be >= 0");
        if (request.layerBindings() == null || request.layerBindings().isEmpty()) errors.add("At least one layer binding is required");
        if (request.layerBindings() != null) {
            for (int i = 0; i < request.layerBindings().size(); i++) {
                var binding = request.layerBindings().get(i);
                String prefix = "layerBindings[" + i + "]: ";
                if (binding.layerId() == null || binding.layerId().isBlank()) {
                    errors.add(prefix + "layerId is required");
                } else if (FilterLayerCatalog.isInternalAutomationLayer(binding.layerId())) {
                    errors.add(prefix + "layerId cannot target internal automation layers");
                }
                if (binding.matchOn() == null || binding.matchOn().isBlank()) errors.add(prefix + "matchOn is required");
                if (binding.matchOn() != null && !"CONTROLLER_ENDPOINT_RESPONSE".equals(binding.matchOn())) {
                    errors.add(prefix + "matchOn must be CONTROLLER_ENDPOINT_RESPONSE");
                }
            }
        }
        return errors;
    }
}
