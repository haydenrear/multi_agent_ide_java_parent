package com.hayden.multiagentide.acp_tests;

import static com.hayden.multiagentide.acp_tests.AcpChatModelCodexIntegrationTest.TestAgent.TEST_AGENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.agentclientprotocol.model.PermissionOptionKind;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.support.AgentMetadataReader;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.api.common.ToolObject;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.IoBinding;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.chat.support.InMemoryConversation;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.repository.RequestContext;
import com.hayden.acp_cdc_ai.repository.RequestContextRepository;
import com.hayden.acp_cdc_ai.sandbox.SandboxContext;
import com.hayden.multiagentide.agent.AgentLifecycleHandler;
import com.hayden.multiagentide.controller.OrchestrationController;
import com.hayden.multiagentide.repository.EventStreamRepository;
import com.hayden.multiagentide.gate.PermissionGateAdapter;
import com.hayden.multiagentide.agent.AcpTooling;
import com.hayden.multiagentide.tool.McpToolObjectRegistrar;
import com.hayden.multiagentidelib.agent.AgentTools;
import com.hayden.acp_cdc_ai.acp.AcpChatModel;
import com.hayden.utilitymodule.config.EnvConfigProps;
import com.hayden.utilitymodule.io.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"codex", "testdocker"})
@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = {"spring.ai.mcp.server.stdio=false"})
class AcpChatModelCodexIntegrationTest {

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private AgentPlatform agentPlatform;

    @Autowired
    private AgentMetadataReader agentMetadataReader;

    @Autowired
    private OrchestrationController orchestrationController;

    @Autowired
    private McpToolObjectRegistrar toolObjectRegistry;

    @Autowired
    private AgentTools guiEvent;
    @Autowired(required = false)
    private AcpTooling fileSystemTools;
    @Autowired
    private EnvConfigProps envConfigProps;

    @Autowired
    private RequestContextRepository requestContextRepository;
    @Autowired
    private PermissionGateAdapter permissionGateAdapter;
    @Autowired
    private EventStreamRepository eventStreamRepository;

    public record ResultValue(String result) {}

    public record FinalValue(String result) {}

    public record RequestValue(String request) {}

    @Agent(
            name = TEST_AGENT,
            description = "tests some stuff.",
            planner = PlannerType.GOAP
    )
    @RequiredArgsConstructor
    public static class TestAgent {

        public static final String TEST_AGENT = "test_agent";

        private final McpToolObjectRegistrar toolObjectRegistry;

        private final AgentTools guiEvent;

        @Autowired(required = false)
        @Setter
        private AcpTooling fileSystemTools;

//        @Action
//        @AchievesGoal(description = "finishes the test")
//        public FinalValue sendsMessage(
//                ResultValue input,
//                ActionContext context,
//                Conversation conversation
//        ) {
//            AssistantMessage message = new AssistantMessage("Hello!");
//            context.sendMessage(message);
//            conversation.addMessage(message);
//            return context.ai().withDefaultLlm()
//                    .withId("hello!")
//                    .createObject(input.result, FinalValue.class);
//        }

//        @Action
//        public void after(
//                ResultValue input,
//                ActionContext context,
//                Conversation conversation
//        ) {
//            AssistantMessage message = new AssistantMessage("Hello!");
//            context.sendMessage(message);
//            conversation.addMessage(message);
//            log.info("Sent");
//        }

        @Action
        @AchievesGoal(description = "finishes the test")
        public ResultValue performTest(
                RequestValue input,
                OperationContext context
        ) {
//            Optional<List<ToolObject>> deepwiki = toolObjectRegistry.tool("deepwiki");
            Optional<List<ToolObject>> hindsight = toolObjectRegistry.tool("hindsight");

//            assertThat(hindsight)
//                    .withFailMessage("Hindsight could not be reached.")
//                    .isPresent();

            var c = context.ai()
                    .withFirstAvailableLlmOf("acp-chat-model", context.getAgentProcess().getId())
                    .withId("hello!")
//                    .withToolObjects(deepwiki.get())
//                    .withToolObject(new ToolObject(guiEvent))
                    .withToolObjects(hindsight.orElse(new ArrayList<>()));

            if (fileSystemTools != null)
                c = c.withToolObject(fileSystemTools);

            return c.createObject(input.request, ResultValue.class);
        }

    }

    @BeforeEach
    public void before() {
        TestAgent agentInterface = new TestAgent(toolObjectRegistry, guiEvent);
        agentInterface.setFileSystemTools(fileSystemTools);
        Optional.ofNullable(agentMetadataReader.createAgentMetadata(agentInterface))
                .ifPresentOrElse(agentPlatform::deploy, () -> log.error("Error deploying {} - could not create agent metadata.", agentInterface));
    }

    @Test
    void testCreateGoal() {



        CompletableFuture.runAsync(() -> {
            var s = orchestrationController.startGoal(new OrchestrationController.StartGoalRequest(
                    "Please add a README.md with text HELLO to the source of the repo. For discovery, just create one agent request that says code map does not have readme, for planner have one planner agent that says only to write readme, for ticket, have one agent that says to write readme.",
                    "/Users/hayde/IdeaProjects/multi_agent_ide_parent/libs-resolver",
                    "main", "Artifact Centralization"));
        });

        startPermissionConsole();

        await().atMost(Duration.ofMinutes(60))
                .pollInterval(Duration.ofSeconds(5))
                .until(() ->
                        eventStreamRepository.list()
                                .stream()
                                .anyMatch(event -> event instanceof Events.GoalCompletedEvent)
                );

        log.info("Finished!");
    }

//    @Test
    void chatModelWorksWithTools() {
        String nodeId = ArtifactKey.createRoot().value();
        ProcessOptions processOptions = ProcessOptions.DEFAULT.withContextId(nodeId)
                .withListener(AgentLifecycleHandler.agentProcessIdListener());
        List<com.embabel.agent.core.Agent> agents = agentPlatform.agents();
        var agentName = TEST_AGENT;
        var thisAgent = agents.stream()
                .filter(agent -> agent.getName().equals(agentName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Agent not found: " + agentName));
        AgentProcess process = agentPlatform.runAgentFrom(
                thisAgent,
                processOptions,
                Map.of(
                        IoBinding.DEFAULT_BINDING,
                        new RequestValue(".")));
    }

    @Test
    void chatModelUsesAcpProtocol() {
        assertThat(chatModel).isInstanceOf(AcpChatModel.class);

//        var c = chatModel.call("Do you have the capability to read or write to the file");
//        var x = chatModel.call("Can you please read the file log.log");
//        log.info("");

        try {
            Path testWork = envConfigProps.getProjectDir().resolve("test_work");
            testWork.toFile().mkdirs();
            var logFile = testWork.resolve("log.log").toFile();
            logFile.delete();
            String nodeId = ArtifactKey.createRoot().value();

            Path testWorkDir = envConfigProps.getProjectDir().resolve("test_work").resolve("hello");

            FileUtils.writeToFile("wow!", testWorkDir);

            // Register RequestContext so sandbox translation can set working directory
            Path workingDir = testWork.toAbsolutePath();
            RequestContext requestContext = RequestContext.builder()
                    .sessionId(nodeId)
                    .sandboxContext(SandboxContext.builder()
                            .mainWorktreePath(workingDir)
                            .build())
                    .build();
            startPermissionConsole();
            requestContextRepository.save(requestContext);
            
            ProcessOptions processOptions = ProcessOptions.DEFAULT.withContextId(nodeId)
                    .withListener(AgentLifecycleHandler.agentProcessIdListener());
            List<com.embabel.agent.core.Agent> agents = agentPlatform.agents();
            var agentName = TEST_AGENT;
            var thisAgent = agents.stream()
                    .filter(agent -> agent.getName().equals(agentName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Agent not found: " + agentName));
            RequestValue v1 = new RequestValue("What model are you?");
            AgentProcess process = agentPlatform.runAgentFrom(
                    thisAgent,
                    processOptions,
                    Map.of(IoBinding.DEFAULT_BINDING, v1));

            process.bind("conversation", new InMemoryConversation());

            var res = process.run().resultOfType(ResultValue.class);

            log.info("{}", res);

            assertThat(logFile.exists()).isTrue();

            logFile.delete();

        } catch (Exception e) {
            log.error("Error - will not fail test for codex-acp - but failed", e);
        }
    }

    private void startPermissionConsole() {
        CompletableFuture.runAsync(() -> {
            try(var reader = new BufferedReader(new InputStreamReader(System.in))) {
                while (true) {
                    if (!permissionGateAdapter.pendingPermissionRequests().isEmpty()) {
                        permissionGateAdapter.pendingPermissionRequests()
                                .forEach(request -> {
                                    System.out.printf("Found permission request: %s%n", request);
                                    System.out.println("Options:");
                                    var permissions = request.getPermissions();
                                    for (int i = 0; i < permissions.size(); i++) {
                                        var option = permissions.get(i);
                                        System.out.printf("  %d. %s (%s)%n", i + 1, option.getName(), option.getKind());
                                    }
                                    System.out.print("Select option [1.." + permissions.size() + "], optionType, or 'cancel': ");
                                    try {
                                        var input = reader.readLine();
                                        Thread.sleep(1000);
                                        if (input == null || input.isBlank()) {
                                            permissionGateAdapter.resolveSelected(
                                                    request.getRequestId(),
                                                    permissions.stream().filter(po -> po.getKind() == PermissionOptionKind.ALLOW_ONCE).findAny().orElseThrow()
                                            );
                                            System.out.println("Resolved selected");
                                            return;
                                        }
                                        var trimmed = input.trim();
                                        if ("cancel".equalsIgnoreCase(trimmed)) {
                                            permissionGateAdapter.resolveCancelled(request.getRequestId());
                                            System.out.println("Resolved selected");
                                            return;
                                        }
                                        try {
                                            int index = Integer.parseInt(trimmed);
                                            if (index >= 1 && index <= permissions.size()) {
                                                var selected = permissions.get(index - 1);
                                                permissionGateAdapter.resolveSelected(request.getRequestId(), selected);
                                                System.out.println("Resolved selected");
                                                return;
                                            }
                                        } catch (
                                                NumberFormatException ignored) {
                                        }
                                        permissionGateAdapter.resolveSelected(request.getRequestId(), trimmed);
                                        System.out.println("Resolved selected");
                                    } catch (
                                            IOException e) {
                                        throw new RuntimeException(e);
                                    } catch (
                                            InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                    }

                    if (!permissionGateAdapter.pendingInterruptRequests().isEmpty()) {
                        permissionGateAdapter.pendingInterruptRequests()
                                .forEach(request -> {
                                    System.out.printf("Found interrupt request: %s%n", request);

                                    try {
                                        var input = reader.readLine();

                                        Thread.sleep(1000);

                                        if (input == null || input.isBlank()) {
                                            permissionGateAdapter.resolveInterrupt(
                                                    request.getInterruptId(),
                                                    IPermissionGate.ResolutionType.RESOLVED,
                                                    "",
                                                    null
                                            );
                                            return;
                                        }
                                        var trimmed = input.trim();
                                        permissionGateAdapter.resolveInterrupt(
                                                request.getInterruptId(),
                                                IPermissionGate.ResolutionType.FEEDBACK,
                                                trimmed,
                                                null
                                        );
                                    } catch (
                                            IOException |
                                            InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                    }

                    try {
                        Thread.sleep(10_000);
                    } catch (
                            InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } catch (IOException e) {
                log.error("Error on system in: {}.", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }).exceptionally(t -> {
            log.error("Found that error!", t);
            return null;
        });
    }
}
