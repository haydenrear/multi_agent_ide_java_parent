package com.hayden.multiagentide.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.DelegatingHttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CodeSearchMcpTools focusing on behavior validation.
 * Tests code search, AST parsing, node extraction, and search functionality
 * using real files and minimal mocking to ensure robustness.
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"inttest", "testdocker"})
@TestPropertySource(properties = {"spring.ai.mcp.server.stdio=false"})
class McpServerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @SneakyThrows
    @Test
    void whenTokenThenConnectsWithMcpClient() {
        try (var m = McpClient.sync(
                        DelegatingHttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                                .endpoint("/mcp")
                                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                                .build())
                .build()) {
            var initialized = m.initialize();
            var listed = m.listTools();
            log.info("Found list tools {}", listed);
        }

    }


}
