package com.hayden.multiagentide.tool;

import com.agentclientprotocol.model.McpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.config.McpProperties;
import com.hayden.acp_cdc_ai.mcp.RequiredProtocolProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolObjectRegistrar {

    private final EmbabelToolObjectRegistry embabelToolObjectRegistry;

    private final McpProperties mcpProperties;

    private final RequiredProtocolProperties requiredProtocolProperties;

    private final ObjectMapper objectMapper;

    @PostConstruct
    public void initialize() {
        for (Map.Entry<String, McpServer> entry : mcpProperties.getCollected().entrySet()) {
            String name = entry.getKey();
            McpServer value = entry.getValue();
            embabelToolObjectRegistry.register(name, new LazyToolObjectRegistration(new LazyToolObjectRegistration.McpServerDescriptor(value, requiredProtocolProperties.getRequired().get(value.getName())), entry.getKey(), objectMapper));
        }
    }

}
