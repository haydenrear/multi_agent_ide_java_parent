package com.hayden.acp_cdc_ai.acp.config;

import java.util.LinkedHashMap;
import java.util.Map;

public record AcpResolvedCall(
        String sessionArtifactKey,
        String providerName,
        String effectiveModel,
        AcpProviderDefinition providerDefinition,
        Map<String, Object> options
) {

    public AcpResolvedCall {
        options = options == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(options));
    }

    public String getSessionArtifactKey() {
        return sessionArtifactKey;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getEffectiveModel() {
        return effectiveModel;
    }

    public AcpProviderDefinition getProviderDefinition() {
        return providerDefinition;
    }

    public Map<String, Object> getOptions() {
        return options;
    }
}
