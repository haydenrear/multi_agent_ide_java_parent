package com.hayden.multiagentide.filter.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationEntity;
import com.hayden.multiagentide.filter.service.LayerIdResolver;
import com.hayden.multiagentide.filter.service.PolicyDiscoveryService;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import com.hayden.multiagentidelib.prompt.PromptContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PromptContributorFactory that injects a description of all active data-layer
 * policy filters into the prompt at the highest precedence (priority 0).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActiveFiltersPromptContributorFactory implements PromptContributorFactory {

    private final PolicyDiscoveryService policyDiscoveryService;
    private final LayerIdResolver layerIdResolver;
    private final ObjectMapper objectMapper;

    @Override
    public List<PromptContributor> create(PromptContext context) {
        Optional<String> layerId = layerIdResolver.resolveForPromptContributor(context);
        if (layerId.isEmpty()) {
            return List.of();
        }

        List<PolicyRegistrationEntity> policies =
                policyDiscoveryService.getActivePoliciesByLayer(layerId.get());
        if (policies.isEmpty()) {
            return List.of();
        }

        String description = buildFilterDescription(policies);
        return List.of(new ActiveFiltersPromptContributor(description));
    }

    private String buildFilterDescription(List<PolicyRegistrationEntity> policies) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Active Data Filters\n");
        sb.append("The following data-layer policy filters are active for this context:\n\n");

        for (PolicyRegistrationEntity policy : policies) {
            String name = extractFilterField(policy, "name", policy.getRegistrationId());
            String desc = extractFilterField(policy, "description", "No description");
            String kind = policy.getFilterKind() != null ? policy.getFilterKind() : "UNKNOWN";
            int priority = extractPriority(policy);

            sb.append("- **").append(name).append("**: ")
                    .append(desc)
                    .append(" (type: ").append(kind)
                    .append(", priority: ").append(priority)
                    .append(")\n");
        }
        return sb.toString();
    }

    private String extractFilterField(PolicyRegistrationEntity policy, String field, String defaultValue) {
        try {
            JsonNode node = objectMapper.readTree(policy.getFilterJson());
            return node.has(field) ? node.get(field).asText(defaultValue) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int extractPriority(PolicyRegistrationEntity policy) {
        try {
            JsonNode node = objectMapper.readTree(policy.getFilterJson());
            return node.has("priority") ? node.get("priority").asInt(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private record ActiveFiltersPromptContributor(String filterDescription) implements PromptContributor {
        @Override
        public String name() {
            return "active-data-filters";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            return filterDescription;
        }

        @Override
        public String template() {
            return filterDescription;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
