package com.hayden.multiagentide.config;

import com.embabel.agent.api.annotation.support.AgentMetadataReader;
import com.embabel.agent.api.channel.*;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.spi.AgentProcessIdGenerator;
import com.embabel.common.ai.model.Llm;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.utilitymodule.acp.config.AcpModelProperties;
import com.hayden.utilitymodule.acp.config.McpProperties;
import com.hayden.utilitymodule.acp.events.EventBus;
import com.hayden.utilitymodule.acp.AcpChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

/**
 * Embabel configuration for chat models and LLM integration.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({AcpModelProperties.class, McpProperties.class})
@ComponentScan(basePackages = "com.hayden.utilitymodule.acp")
public class MultiAgentEmbabelConfig {

    @Value("${multi-agent-embabel.chat-model.provider:acp}")
    private String modelProvider;

    @Bean
    public ApplicationRunner deployAgents(List<Object> agents,
                                          AgentPlatform agentPlatform,
                                          AgentMetadataReader agentMetadataReader,
                                          AgentInterfaces.WorkflowAgent workflowAgent) {
        for (Object agent : agents) {
            if (!agent.getClass().isAnnotationPresent(com.embabel.agent.api.annotation.Agent.class)) {
                continue;
            }
            deployAgent(agentMetadataReader.createAgentMetadata(agent), agentPlatform, agent.getClass().getName());

        }

        return args -> {
        };
    }

    private static void deployAgent(AgentScope agentMetadataReader, AgentPlatform agentPlatform, String workflowAgent) {
        Optional.ofNullable(agentMetadataReader)
                .ifPresentOrElse(agentPlatform::deploy, () -> log.error(
                        "Error deploying {} - could not create agent metadata.",
                        workflowAgent
                ));
    }

    @Bean
    @Profile("openai")
    public ApplicationRunner agenticApplicationRunner() {
        return args -> {

        };
    }

    @Bean
    @Primary
    public AgentProcessIdGenerator nodeIdAgentProcessIdGenerator() {
        return (agent, processOptions) -> Optional.ofNullable(processOptions.getContextIdString())
                .orElseGet(() -> AgentProcessIdGenerator.Companion.getRANDOM().createProcessId(agent, processOptions));
    }

    @Bean(name = "chatModel")
    @Primary
    public ChatModel acpChatModel(AcpChatModel chatModel) {
        return chatModel;
    }

    /**
     * Create Streaming Chat Language Model for streaming responses.
     */
    @Bean
    public StreamingChatModel streamingChatLanguageModel(AcpChatModel chatModel) {
        return chatModel;
    }

    @Bean
    public Llm llm(org.springframework.ai.chat.model.ChatModel chatModel) {
        return new Llm("acp-chat-model", modelProvider, chatModel);
    }

    @Bean
    @Primary
    public OutputChannel llmOutputChannel(ChatModel chatModel, @Lazy AgentPlatform agentPlatform) {
        return new MulticastOutputChannel(List.of(
                (OutputChannel) event -> {
                    switch (event) {
                        case MessageOutputChannelEvent evt -> {
//                          var process = agentPlatform.getAgentProcess(evt.getProcessId());
//                          process.getProcessContext().getAgentProcess().getLlmInvocations().getLast().getLlm();
                            var prev = EventBus.agentProcess.get();
                            try {
                                EventBus.agentProcess.set(new EventBus.AgentProcessData(evt.getProcessId()));
                                chatModel.call(new Prompt(new AssistantMessage(evt.getMessage().getContent())));
                            } finally {
                                EventBus.agentProcess.set(prev);
                            }
                        }
                        default -> {
                        }
                    }
                },
                DevNullOutputChannel.INSTANCE
        ));
    }

    /**
     * Mock Streaming ChatLanguageModel for testing without OpenAI API key.
     */
    private static class MockStreamingChatLanguageModel implements StreamingChatModel {
        @Override
        public @org.jspecify.annotations.NonNull Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(ChatResponse.builder().build());
        }
    }
}
