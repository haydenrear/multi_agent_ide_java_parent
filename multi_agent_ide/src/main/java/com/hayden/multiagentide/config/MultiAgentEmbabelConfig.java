package com.hayden.multiagentide.config;

import com.embabel.agent.api.annotation.support.AgentMetadataReader;
import com.embabel.agent.api.channel.*;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.support.DefaultAgentPlatform;
import com.embabel.agent.spi.AgentProcessIdGenerator;
import com.embabel.common.ai.model.Llm;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.AgentQuestionAnswerFunction;
import com.hayden.multiagentide.agent.AskUserQuestionTool;
import com.hayden.acp_cdc_ai.acp.config.AcpModelProperties;
import com.hayden.acp_cdc_ai.acp.config.McpProperties;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.AcpChatModel;
import com.hayden.multiagentide.repository.EventStreamRepository;
import io.micrometer.common.util.StringUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Embabel configuration for chat models and LLM integration.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({AcpModelProperties.class, McpProperties.class})
@ComponentScan(basePackages = {"com.hayden.acp_cdc_ai"})
public class MultiAgentEmbabelConfig {

    @Value("${multi-agent-embabel.chat-model.provider:acp}")
    private String modelProvider;



    @SneakyThrows
    @Bean
    public ApplicationRunner deployAgents(List<AgentInterfaces> agents,
                                          AgentPlatform agentPlatform,
                                          AgentMetadataReader agentMetadataReader,
                                          BlackboardRoutingPlannerFactory b) {
        if (agentPlatform instanceof DefaultAgentPlatform) {
            Field declaredField = agentPlatform.getClass().getDeclaredField("plannerFactory");
            declaredField.trySetAccessible();
            ReflectionUtils.setField(declaredField, agentPlatform, b);
        }
        for (AgentInterfaces agent : agents) {
            AgentScope agentMetadata = agentMetadataReader.createAgentMetadata(agent);
            deployAgent(
                    agentMetadata,
                    agentPlatform,
                    agent.getClass().getName());
        }


        return args -> {
        };
    }

    @Bean
    public AskUserQuestionTool askUserQuestionTool(AgentQuestionAnswerFunction agentQuestionAnswerFunction) {
        return AskUserQuestionTool.builder()
                .questionAnswerFunction(agentQuestionAnswerFunction)
                .answersValidation(false)
                .build();
    }

    private static void deployAgent(AgentScope agentMetadataReader, AgentPlatform agentPlatform, String workflowAgent) {
        Optional.ofNullable(agentMetadataReader)
                .ifPresentOrElse(s -> {
                    log.info("Starting deployment of {}", s.getName());
                    agentPlatform.deploy(s);
                    log.info("Finished deployment of {}", s.getName());
                }, () -> log.error(
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
    public OutputChannel llmOutputChannel(ChatModel chatModel, @Lazy AgentPlatform agentPlatform, EventStreamRepository graphRepository) {
        return new MulticastOutputChannel(List.of(
                (OutputChannel) event -> {
                    switch (event) {
                        case MessageOutputChannelEvent evt -> {
                            String content = evt.getMessage().getContent();

                            if (StringUtils.isNotBlank(content)) {

                                if (content.startsWith("proc:")) {
//                                    TODO: add sending messages to particular graph nodes
                                }

                                if (Objects.equals(content, "STOPQ")) {
                                    var ap = agentPlatform.getAgentProcess(evt.getProcessId());

                                    Optional.ofNullable(ap)
                                            .ifPresentOrElse(
                                                    proc -> {
                                                        log.info("Received STOPQ - killing process {}.", proc.getId());
                                                        var killed = proc.kill();
                                                        log.info("Received STOPQ - killed process {}, {}.", proc.getId(), killed);
                                                    },
                                                    () -> log.info("Received kill request for unknown process, {}", evt.getProcessId()));
                                }

//                              Have to do this because single agent process opens many chat sessions.
                                var thisArtifactKeyForMessage = graphRepository
                                        .getLastMatching(Events.NodeThoughtDeltaEvent.class, n -> matchesThisSession(evt, n))
                                                .map(Events.GraphEvent::nodeId)
                                                .flatMap(MultiAgentEmbabelConfig::getDescendent)
                                        .or(() -> graphRepository.getLastMatching(Events.ToolCallEvent.class, n -> matchesThisSession(evt, n))
                                                .map(Events.GraphEvent::nodeId)
                                                .flatMap(MultiAgentEmbabelConfig::getDescendent))
                                        .or(() -> graphRepository.getLastMatching(Events.NodeStreamDeltaEvent.class, n -> matchesThisSession(evt, n))
                                                .map(Events.GraphEvent::nodeId)
                                                .flatMap(MultiAgentEmbabelConfig::getDescendent))
                                        .or(() -> graphRepository
                                                .getLastMatching(Events.ChatSessionCreatedEvent.class, n -> matchesThisSession(evt, n))
                                                .map(Events.GraphEvent::nodeId))
                                        .orElseGet(evt::getProcessId);

                                var prev = EventBus.agentProcess.get();

                                try {
                                    EventBus.agentProcess.set(new EventBus.AgentNodeKey(thisArtifactKeyForMessage));
                                    chatModel.call(new Prompt(new AssistantMessage(content)));
                                } finally {
                                    EventBus.agentProcess.set(prev);
                                }
                            }
                        }
                        default -> {
                        }
                    }
                },
                DevNullOutputChannel.INSTANCE
        ));
    }

    private static @NonNull Optional<String> getDescendent(String s) {
        try {
            return new ArtifactKey(s)
                    .parent()
                    .flatMap(ArtifactKey::parent)
                    .map(ArtifactKey::value);
        } catch (Exception e) {
            log.error("Error getting descendent for {}.", s);
            return Optional.empty();
        }
    }

    private static boolean matchesThisSession(MessageOutputChannelEvent evt, Events.GraphEvent n) {
        try {
            var a = new ArtifactKey(n.nodeId());
            boolean equals;
            if (a.isRoot()) {
                equals = evt.getProcessId().equals(a.value());
            } else {
                equals = evt.getProcessId().equals(a.root().value());
            }

            if (equals) {
                log.info("Found matching {}: {}.", n.getClass(), n.nodeId());
            }

            return equals;
        } catch (Exception e) {
            log.error("Error finding session {}", e.getMessage(), e);
            return false;
        }
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
