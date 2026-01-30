package com.hayden.multiagentide.tool;

import com.agentclientprotocol.model.McpServer;
import com.embabel.agent.api.common.ToolObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.utilitymodule.MapFunctions;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.DelegatingHttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


@Slf4j
class LazyToolObjectRegistration {

    final EmbabelToolObjectRegistry.ToolRegistration underlying;
    final String name;

    final CountDownLatch countDownLatch = new CountDownLatch(1);

    volatile List<McpSyncClient> clients;

    volatile List<ToolObject> setValues;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    LazyToolObjectRegistration(McpServer value, String name) {
        this.name = name;
        underlying = tor -> initializeToolObjects(value, tor);
    }

    private @NonNull Optional<List<ToolObject>> initializeToolObjects(McpServer value, LazyToolObjectRegistration tor) {
        var t = toToolObjects(tor, name, value);
        return t;
    }

    synchronized Optional<List<ToolObject>> compute() {
        if (prev().isPresent())
            return prev();

        var g = computeToolObject(this);
        g.ifPresent(this::setToolObjects);
        return g;
    }

    private Consumer<List<McpSchema.Tool>> toolsChangeConsumer() {
        return t -> doToolChangeConsumer();
    }

    private void doToolChangeConsumer() {
        log.info("Received tool change notification - updating tools.");
        try {
            boolean waitedCountdownLatch = countDownLatch.await(5, TimeUnit.SECONDS);
            if (waitedCountdownLatch && clients != null) {
                log.info("Resetting tools from notification.");
                setValues();
            } else if (clients == null && waitedCountdownLatch) {
                log.error("Found tools change consumer execution but clients was null. " +
                          "A race thing before it was set but ultimately very weird - a supposed impossible state.");
            } else {
                log.error("Clients: {}, waitedCountdownLatch: {}.", clients, waitedCountdownLatch);
            }
        } catch (InterruptedException e) {
            log.error("Found interrupted exception while waiting for countdown latch.");
            Thread.currentThread().interrupt();
        }
    }

    private void setValues() {
        List<McpSyncClient> localClients = clients;
        var tc = SyncMcpToolCallbackProvider.syncToolCallbacks(localClients);
        setToolObjects(EmbabelToolObjectProvider.parseToolObjects(name, tc));
    }

    private Optional<List<ToolObject>> computeToolObject(LazyToolObjectRegistration toolObjectRegistration) {
        return underlying.computeToolObject(toolObjectRegistration);
    }

    private Optional<List<ToolObject>> prev() {
        return Optional.ofNullable(setValues);
    }

    private void setToolObjects(List<ToolObject> values) {
        this.setValues = List.copyOf(values);
    }

    private @NonNull Optional<List<ToolObject>> toToolObjects(LazyToolObjectRegistration tor, String name, McpServer value) {
        var tc = resolveToolCallbacks(name, value, tor);
        return tc.map(toolCallbacks -> EmbabelToolObjectProvider.parseToolObjects(name, toolCallbacks));
    }

    private @NonNull Optional<List<ToolCallback>> resolveToolCallbacks(String name, McpServer value, LazyToolObjectRegistration toolObjectRegistration) {

        try {
            switch (value) {
                case McpServer.Http http -> {
                    var m = McpClient.sync(
                                    DelegatingHttpClientStreamableHttpTransport
                                            .builder(http.getUrl())
                                            .build()
                            )
                            .toolsChangeConsumer(toolObjectRegistration.toolsChangeConsumer())
                            .build();

                    List<McpSyncClient> m1 = List.of(m);
                    var tc = SyncMcpToolCallbackProvider.syncToolCallbacks(m1);
                    publishClients(m1);
                    return Optional.of(tc);
                }
                case McpServer.Stdio stdio -> {
                    var m = McpClient.sync(
                                    new StdioClientTransport(
                                            ServerParameters.builder(stdio.getCommand())
                                                    .env(MapFunctions.CollectMap(stdio.getEnv().stream()
                                                            .map(e -> Map.entry(e.getName(), e.getValue()))))
                                                    .args(stdio.getArgs())
                                                    .build(),
                                            new JacksonMcpJsonMapper(objectMapper))
                            )
                            .toolsChangeConsumer(toolObjectRegistration.toolsChangeConsumer())
                            .build();

                    List<McpSyncClient> m1 = List.of(m);
                    var tc = SyncMcpToolCallbackProvider.syncToolCallbacks(m1);
                    publishClients(m1);
                    return Optional.of(tc);
                }
                case McpServer.Sse sse -> {
                    log.error("Dont support SSE!");
                }
                default -> {

                }
            }

        } catch (Exception e) {
            log.error("Error attempting to build {}.", name);

            if (countDownLatch.getCount() != 1L && clients == null) {
                log.error("Found inconsistent count in countdown latch.");
            }
        }

        return Optional.empty();
    }

    private void publishClients(List<McpSyncClient> m1) {
        this.clients = m1;
        this.countDownLatch.countDown();
    }
}
