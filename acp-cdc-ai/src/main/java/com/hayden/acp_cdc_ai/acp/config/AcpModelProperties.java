package com.hayden.acp_cdc_ai.acp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@ConfigurationProperties(prefix = "multi-agent-embabel.acp")
public record AcpModelProperties(
        String defaultProvider,
        Map<String, AcpProviderDefinition> providers
) {

    public AcpModelProperties {
        defaultProvider = blankToNull(defaultProvider);
        providers = providers == null ? Map.of() : copyProviders(providers);
    }

    public Optional<AcpProviderDefinition> findProvider(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(providerName));
    }

    public String resolveProviderName(String requestedProvider) {
        String providerName = blankToNull(requestedProvider);
        if (providerName != null) {
            if (!providers.containsKey(providerName)) {
                throw new IllegalStateException("Unknown ACP provider: " + providerName);
            }
            return providerName;
        }
        if (defaultProvider == null) {
            throw new IllegalStateException("No ACP provider requested and no default ACP provider configured");
        }
        if (!providers.containsKey(defaultProvider)) {
            throw new IllegalStateException("Configured default ACP provider does not exist: " + defaultProvider);
        }
        return defaultProvider;
    }

    public AcpProviderDefinition resolveProvider(String requestedProvider) {
        return providers.get(resolveProviderName(requestedProvider));
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public Map<String, AcpProviderDefinition> getProviders() {
        return providers;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Map<String, AcpProviderDefinition> copyProviders(Map<String, AcpProviderDefinition> providers) {
        Map<String, AcpProviderDefinition> copied = new LinkedHashMap<>();
        providers.forEach((name, provider) -> {
            if (provider != null) {
                copied.put(name, provider.name() == null || provider.name().isBlank()
                        ? new AcpProviderDefinition(
                        name,
                        provider.transport(),
                        provider.command(),
                        provider.args(),
                        provider.workingDirectory(),
                        provider.endpoint(),
                        provider.apiKey(),
                        provider.authMethod(),
                        provider.env(),
                        provider.defaultModel()
                )
                        : provider);
            }
        });
        return Map.copyOf(copied);
    }
}
