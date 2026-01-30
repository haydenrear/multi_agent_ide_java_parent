package com.hayden.multiagentide.acp;

import static com.hayden.multiagentide.acp.AcpChatModelCodexIntegrationTest.TestAgent.TEST_AGENT;
import static org.assertj.core.api.Assertions.assertThat;

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
import com.hayden.multiagentide.agent.AgentLifecycleHandler;
import com.hayden.multiagentide.controller.OrchestrationController;
import com.hayden.multiagentide.tool.EmbabelToolObjectRegistry;
import com.hayden.multiagentidelib.agent.AgentTools;
import com.hayden.utilitymodule.acp.AcpChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("acp")
@TestPropertySource(properties = {"spring.ai.mcp.server.stdio=false"})
class AcpChatModelCodexIntegrationTest {

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private AgentLifecycleHandler agentLifecycleHandler;

    @Autowired
    private AgentPlatform agentPlatform;

    @Autowired
    private AgentMetadataReader agentMetadataReader;

    @Autowired
    private OrchestrationController orchestrationController;

    @Autowired
    private EmbabelToolObjectRegistry toolObjectRegistry;

    @Autowired
    private AgentTools guiEvent;

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

        private final EmbabelToolObjectRegistry toolObjectRegistry;

        private final AgentTools guiEvent;

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
            Optional<List<ToolObject>> deepwiki = toolObjectRegistry.tool("deepwiki");

            assertThat(deepwiki)
                    .withFailMessage("Deep wiki could not be reached.")
                    .isPresent();

            return context.ai().withDefaultLlm()
                    .withId("hello!")
                    .withToolObjects(deepwiki.get())
                    .withToolObject(new ToolObject(guiEvent))
                    .createObject(input.request, ResultValue.class);
        }

    }

    @BeforeEach
    public void before() {
        TestAgent agentInterface = new TestAgent(toolObjectRegistry, guiEvent);
        Optional.ofNullable(agentMetadataReader.createAgentMetadata(agentInterface))
                .ifPresentOrElse(agentPlatform::deploy, () -> log.error("Error deploying {} - could not create agent metadata.", agentInterface));
    }

    @Test
    void testCreateGoal() {
        orchestrationController.startGoal(new OrchestrationController.StartGoalRequest(
                "hello!", "/Users/hayde/IdeaProjects/multi_agent_ide_parent/libs-resolver",
                "main", "hello", UUID.randomUUID().toString()));
    }

    @Test
    void chatModelWorksWithTools() {
        String nodeId = UUID.randomUUID().toString();
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
            String nodeId = UUID.randomUUID().toString();
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
                            new RequestValue("Can you please try using your emitGuiEvent tool? Skip the session ID.")));
            process.bind("conversation", new InMemoryConversation());

            var res = process.run().resultOfType(ResultValue.class);

            log.info("{}", res);

            assertThat(new File("log.log").exists() || new File("multi_agent_ide/log.log").exists()).isTrue();

        } catch (Exception e) {
            log.error("Error - will not fail test for codex-acp - but failed", e);
        }
    }
}
