package com.hayden.multiagentide.artifacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.PreviousContext;
import com.hayden.multiagentidelib.agent.UpstreamContext;
import com.hayden.multiagentidelib.artifact.PromptTemplateVersion;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for artifact serialization/deserialization through the persistence layer.
 * 
 * Tests verify:
 * - AgentModelMixin correctly handles all AgentModel types
 * - ArtifactMixin correctly handles all Artifact types
 * - Nested structures serialize and deserialize correctly
 * - Complex inheritance hierarchies work end-to-end
 * - Database round-trips preserve all data
 */
@Slf4j
@SpringBootTest
@Profile("test")
@Transactional
class ArtifactSerializationTest {

    @Autowired
    private ArtifactRepository artifactRepository;
    
    @Autowired
    private ArtifactService artifactService;
    
    @Autowired
    private ArtifactTreeBuilder artifactTreeBuilder;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private String executionKey;
    private ArtifactKey rootKey;

    @BeforeEach
    void performArtifactSerializationSetup() {
        rootKey = ArtifactKey.createRoot();
        executionKey = rootKey.value();
        
        // Clean up any previous test data
        artifactRepository.deleteAll();
        artifactRepository.flush();
        assertThat(artifactRepository.count()).isZero();
    }
    
    @AfterEach
    void performArtifactSerializationTeardown() {
        artifactRepository.deleteAll();
        artifactRepository.flush();
        assertThat(artifactRepository.count()).isZero();
    }
    
    @Nested
    @DisplayName("Core Artifact Serialization")
    class CoreArtifactSerialization {
        
        @Test
        @DisplayName("ExecutionArtifact serializes and deserializes correctly")
        void executionArtifactRoundTrip() {
            Artifact.ExecutionArtifact execution = Artifact.ExecutionArtifact.builder()
                    .artifactKey(rootKey)
                    .workflowRunId("workflow-123")
                    .startedAt(Instant.now().minusSeconds(60))
                    .finishedAt(Instant.now())
                    .status(Artifact.ExecutionStatus.COMPLETED)
                    .metadata(Map.of("env", "test", "version", "1.0"))
                    .hash(UUID.randomUUID().toString())
                    .children(new ArrayList<>())
                    .build();
            
            artifactTreeBuilder.addArtifact(executionKey, execution);
            Optional<Artifact> persisted = artifactTreeBuilder.persistRemoveExecution(executionKey);
            
            assertThat(persisted).isPresent();
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(rootKey.value());
            assertThat(entity).isPresent();
            
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            assertThat(deserialized).isPresent();
            assertThat(deserialized.get()).isInstanceOf(Artifact.ExecutionArtifact.class);
            
            Artifact.ExecutionArtifact result = (Artifact.ExecutionArtifact) deserialized.get();
            assertThat(result.workflowRunId()).isEqualTo("workflow-123");
            assertThat(result.status()).isEqualTo(Artifact.ExecutionStatus.COMPLETED);
            assertThat(result.metadata()).containsEntry("env", "test");
        }
        
        @Test
        @DisplayName("ExecutionConfigArtifact with complex config serializes correctly")
        void executionConfigArtifactRoundTrip() {
            ArtifactKey configKey = rootKey.createChild();
            
            Map<String, Object> modelRefs = Map.of(
                    "orchestrator", "claude-3-opus-20240229",
                    "discovery", "claude-3-sonnet-20240229"
            );
            
            Map<String, Object> toolPolicy = Map.of(
                    "maxRetries", 3,
                    "timeout", 30000,
                    "allowedTools", List.of("readFile", "writeFile", "search")
            );
            
            Artifact.ExecutionConfigArtifact config = Artifact.ExecutionConfigArtifact.builder()
                    .artifactKey(configKey)
                    .repositorySnapshotId("snapshot-abc123")
                    .modelRefs(modelRefs)
                    .toolPolicy(toolPolicy)
                    .routingPolicy(Map.of("defaultRoute", "orchestrator"))
                    .hash("config-hash")
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();
            
            // Add root first
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, config);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(configKey.value());
            assertThat(entity).isPresent();
            
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            assertThat(deserialized).isPresent();
            
            Artifact.ExecutionConfigArtifact result = (Artifact.ExecutionConfigArtifact) deserialized.get();
            assertThat(result.modelRefs()).containsEntry("orchestrator", "claude-3-opus-20240229");
            assertThat(result.toolPolicy()).containsEntry("maxRetries", 3);
            assertThat(result.repositorySnapshotId()).isEqualTo("snapshot-abc123");
        }
        
        @Test
        @DisplayName("RenderedPromptArtifact serializes correctly")
        void renderedPromptArtifactRoundTrip() {
            ArtifactKey promptKey = rootKey.createChild();
            
            Artifact.RenderedPromptArtifact prompt = Artifact.RenderedPromptArtifact.builder()
                    .artifactKey(promptKey)
                    .renderedText("You are a helpful assistant. Task: {{ task }}")
                    .promptName("system-prompt")
                    .hash("prompt-hash-123")
                    .metadata(Map.of("type", "system"))
                    .children(new ArrayList<>())
                    .build();
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, prompt);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(promptKey.value());
            assertThat(entity).isPresent();
            
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            assertThat(deserialized).isPresent();
            
            Artifact.RenderedPromptArtifact result = (Artifact.RenderedPromptArtifact) deserialized.get();
            assertThat(result.renderedText()).contains("helpful assistant");
            assertThat(result.promptName()).isEqualTo("system-prompt");
            assertThat(result.contentHash()).contains("prompt-hash-123");
        }
        
        @Test
        @DisplayName("ToolCallArtifact with input and output serializes correctly")
        void toolCallArtifactRoundTrip() {
            ArtifactKey toolCallKey = rootKey.createChild();
            
            Artifact.ToolCallArtifact toolCall = Artifact.ToolCallArtifact.builder()
                    .artifactKey(toolCallKey)
                    .toolCallId("call-abc123")
                    .toolName("readFile")
                    .inputJson("{\"path\": \"/test/file.txt\"}")
                    .inputHash("input-hash")
                    .outputJson("{\"content\": \"file contents here\"}")
                    .outputHash("output-hash")
                    .error(null)
                    .metadata(Map.of("duration", "150ms"))
                    .children(new ArrayList<>())
                    .build();
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, toolCall);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(toolCallKey.value());
            assertThat(entity).isPresent();
            
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            assertThat(deserialized).isPresent();
            
            Artifact.ToolCallArtifact result = (Artifact.ToolCallArtifact) deserialized.get();
            assertThat(result.toolName()).isEqualTo("readFile");
            assertThat(result.inputJson()).contains("/test/file.txt");
            assertThat(result.outputJson()).contains("file contents");
            assertThat(result.error()).isNull();
        }
        
        @Test
        @DisplayName("ToolCallArtifact with error serializes correctly")
        void toolCallArtifactWithErrorRoundTrip() {
            ArtifactKey toolCallKey = rootKey.createChild();
            
            Artifact.ToolCallArtifact toolCall = Artifact.ToolCallArtifact.builder()
                    .artifactKey(toolCallKey)
                    .toolCallId("call-error-123")
                    .toolName("deleteFile")
                    .inputJson("{\"path\": \"/protected/file.txt\"}")
                    .inputHash("input-hash")
                    .outputJson(null)
                    .outputHash(null)
                    .error("Permission denied: /protected/file.txt")
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, toolCall);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(toolCallKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.ToolCallArtifact result = (Artifact.ToolCallArtifact) deserialized.get();
            assertThat(result.error()).contains("Permission denied");
            assertThat(result.outputJson()).isNull();
        }
        
        @Test
        @DisplayName("OutcomeEvidenceArtifact serializes correctly")
        void outcomeEvidenceArtifactRoundTrip() {
            ArtifactKey evidenceKey = rootKey.createChild();
            
            Artifact.OutcomeEvidenceArtifact evidence = Artifact.OutcomeEvidenceArtifact.builder()
                    .artifactKey(evidenceKey)
                    .evidenceType("TestResult")
                    .payload("{\"passed\": true, \"assertions\": 15}")
                    .hash("evidence-hash")
                    .metadata(Map.of("testSuite", "integration"))
                    .children(new ArrayList<>())
                    .build();
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, evidence);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(evidenceKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.OutcomeEvidenceArtifact result = (Artifact.OutcomeEvidenceArtifact) deserialized.get();
            assertThat(result.evidenceType()).isEqualTo("TestResult");
            assertThat(result.payload()).contains("\"passed\": true");
        }
        
        @Test
        @DisplayName("EventArtifact serializes correctly")
        void eventArtifactRoundTrip() {
            ArtifactKey eventKey = rootKey.createChild();
            
            Artifact.EventArtifact event = Artifact.EventArtifact.builder()
                    .artifactKey(eventKey)
                    .eventId("event-123")
                    .eventTimestamp(Instant.now())
                    .eventType("AgentStarted")
                    .payloadJson(Map.of("agentId", "agent-001", "phase", "discovery"))
                    .hash("event-hash")
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, event);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(eventKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.EventArtifact result = (Artifact.EventArtifact) deserialized.get();
            assertThat(result.eventType()).isEqualTo("AgentStarted");
            assertThat(result.payloadJson()).containsEntry("agentId", "agent-001");
        }
    }
    
    // ========== Templated Artifact Tests ==========
    
    @Nested
    @DisplayName("Templated Artifact Serialization")
    class TemplatedArtifactSerialization {
        
        @Test
        @DisplayName("PromptTemplateVersion serializes correctly")
        void promptTemplateVersionRoundTrip() {
            ArtifactKey templateKey = rootKey.createChild();
            
            PromptTemplateVersion template = new PromptTemplateVersion(
                    "tpl.agent.orchestrator.system",
                    "You are an orchestrator agent. Your role is to coordinate other agents.",
                    "template-hash-xyz",
                    templateKey,
                    Instant.now(),
                    new ArrayList<>()
            );
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, template);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(templateKey.value());
            assertThat(entity).isPresent();
            assertThat(entity.get().getTemplateStaticId()).isEqualTo("tpl.agent.orchestrator.system");
            
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            assertThat(deserialized).isPresent();
            
            PromptTemplateVersion result = (PromptTemplateVersion) deserialized.get();
            assertThat(result.templateStaticId()).isEqualTo("tpl.agent.orchestrator.system");
            assertThat(result.templateText()).contains("orchestrator agent");
            assertThat(result.contentHash()).contains("template-hash-xyz");
        }
        
        @Test
        @DisplayName("PromptContributionTemplate serializes correctly")
        void promptContributionTemplateRoundTrip() {
            ArtifactKey contributionKey = rootKey.createChild();
            
            Artifact.PromptContributionTemplate contribution = Artifact.PromptContributionTemplate.builder()
                    .artifactKey(contributionKey)
                    .contributorName("ContextContributor")
                    .priority(100)
                    .agentTypes(List.of("orchestrator", "discovery"))
                    .templateText("Current context: {{ context }}")
                    .orderIndex(1)
                    .hash("contribution-hash")
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, contribution);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(contributionKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.PromptContributionTemplate result = (Artifact.PromptContributionTemplate) deserialized.get();
            assertThat(result.contributorName()).isEqualTo("ContextContributor");
            assertThat(result.priority()).isEqualTo(100);
            assertThat(result.agentTypes()).containsExactly("orchestrator", "discovery");
            assertThat(result.templateText()).contains("Current context");
        }
        
        @Test
        @DisplayName("ToolPrompt serializes correctly")
        void toolPromptRoundTrip() {
            ArtifactKey toolPromptKey = rootKey.createChild();
            
            Artifact.ToolPrompt toolPrompt = Artifact.ToolPrompt.builder()
                    .artifactKey(toolPromptKey)
                    .toolCallName("searchCodebase")
                    .toolDescription("Search the codebase for files matching a pattern")
                    .hash("tool-prompt-hash")
                    .metadata(Map.of("category", "filesystem"))
                    .children(new ArrayList<>())
                    .build();
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, toolPrompt);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(toolPromptKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.ToolPrompt result = (Artifact.ToolPrompt) deserialized.get();
            assertThat(result.toolCallName()).isEqualTo("searchCodebase");
            assertThat(result.toolDescription()).contains("Search the codebase");
        }
    }
    
    // ========== AgentModel Serialization Tests ==========
    
    @Nested
    @DisplayName("AgentModel Serialization")
    class AgentModelSerialization {
        
        @Test
        @DisplayName("OrchestratorRequest serializes correctly")
        void orchestratorRequestRoundTrip() {
            ArtifactKey requestKey = rootKey.createChild();
            
            AgentModels.OrchestratorRequest request = AgentModels.OrchestratorRequest.builder()
                    .contextId(requestKey)
                    .goal("Implement user authentication")
                    .phase("planning")
                    .build();
            
            Artifact.AgentModelArtifact artifact = (Artifact.AgentModelArtifact) request.toArtifact(Artifact.HashContext.defaultHashContext());
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, artifact);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(requestKey.value());
            assertThat(entity).isPresent();
            
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            assertThat(deserialized).isPresent();
            assertThat(deserialized.get()).isInstanceOf(Artifact.AgentModelArtifact.class);
            
            Artifact.AgentModelArtifact result = (Artifact.AgentModelArtifact) deserialized.get();
            assertThat(result.agentModel()).isInstanceOf(AgentModels.OrchestratorRequest.class);
            
            AgentModels.OrchestratorRequest resultRequest = (AgentModels.OrchestratorRequest) result.agentModel();
            assertThat(resultRequest.goal()).isEqualTo("Implement user authentication");
            assertThat(resultRequest.phase()).isEqualTo("planning");
        }
        
        @Test
        @DisplayName("DiscoveryAgentRequest serializes correctly")
        void discoveryAgentRequestRoundTrip() {
            ArtifactKey requestKey = rootKey.createChild();
            
            AgentModels.DiscoveryAgentRequest request = AgentModels.DiscoveryAgentRequest.builder()
                    .contextId(requestKey)
                    .goal("Find authentication-related code")
                    .subdomainFocus("authentication")
                    .build();
            
            Artifact.AgentModelArtifact artifact = (Artifact.AgentModelArtifact) request.toArtifact(Artifact.HashContext.defaultHashContext());
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, artifact);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(requestKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.AgentModelArtifact result = (Artifact.AgentModelArtifact) deserialized.get();
            AgentModels.DiscoveryAgentRequest resultRequest = (AgentModels.DiscoveryAgentRequest) result.agentModel();
            
            assertThat(resultRequest.goal()).isEqualTo("Find authentication-related code");
            assertThat(resultRequest.subdomainFocus()).isEqualTo("authentication");
        }
        
        @Test
        @DisplayName("DiscoveryAgentResult with complex data serializes correctly")
        void discoveryAgentResultRoundTrip() {
            ArtifactKey resultKey = rootKey.createChild();
            
            AgentModels.DiscoveryAgentResult result = AgentModels.DiscoveryAgentResult.builder()
                    .contextId(resultKey)
                    .output("Found 5 authentication-related files")
                    .build();
            
            Artifact.AgentModelArtifact artifact = (Artifact.AgentModelArtifact) result.toArtifact(Artifact.HashContext.defaultHashContext());
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, artifact);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(resultKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.AgentModelArtifact resultArtifact = (Artifact.AgentModelArtifact) deserialized.get();
            AgentModels.DiscoveryAgentResult agentResult = (AgentModels.DiscoveryAgentResult) resultArtifact.agentModel();
            
            assertThat(agentResult.output()).contains("Found 5 authentication-related files");
        }
        
        @Test
        @DisplayName("InterruptRequest serializes correctly")
        void interruptRequestRoundTrip() {
            ArtifactKey interruptKey = rootKey.createChild();
            
            AgentModels.InterruptRequest.OrchestratorInterruptRequest interrupt = 
                    AgentModels.InterruptRequest.OrchestratorInterruptRequest.builder()
                            .contextId(interruptKey)
                            .type(com.hayden.acp_cdc_ai.acp.events.Events.InterruptType.HUMAN_REVIEW)
                            .reason("Need clarification on authentication method")
                            .choices(List.of(
                                    new AgentModels.InterruptRequest.StructuredChoice(
                                            "tpl.choice.authMethod",
                                            "authMethod",
                                            "Select authentication method",
                                            Map.of(
                                                    "jwt", "JSON Web Tokens",
                                                    "session", "Session-based auth",
                                                    "oauth", "OAuth 2.0"
                                            ),
                                            "jwt"
                                    )
                            ))
                            .contextForDecision("User authentication implementation in progress")
                            .build();
            
            Artifact.AgentModelArtifact artifact = (Artifact.AgentModelArtifact) interrupt.toArtifact(Artifact.HashContext.defaultHashContext());
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, artifact);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(interruptKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.AgentModelArtifact result = (Artifact.AgentModelArtifact) deserialized.get();
            AgentModels.InterruptRequest.OrchestratorInterruptRequest resultInterrupt = 
                    (AgentModels.InterruptRequest.OrchestratorInterruptRequest) result.agentModel();
            
            assertThat(resultInterrupt.reason()).contains("clarification on authentication");
            assertThat(resultInterrupt.choices()).hasSize(1);
            assertThat(resultInterrupt.choices().get(0).choiceId()).isEqualTo("tpl.choice.authMethod");
            assertThat(resultInterrupt.choices().get(0).options()).containsKey("jwt");
        }
        
        @Test
        @DisplayName("UpstreamContext serializes correctly")
        void upstreamContextRoundTrip() {
            ArtifactKey contextKey = rootKey.createChild();
            
            UpstreamContext.OrchestratorUpstreamContext upstreamContext = 
                    new UpstreamContext.OrchestratorUpstreamContext(
                            contextKey,
                            "Workflow goal",
                            "planning"
                    );
            
            Artifact.AgentModelArtifact artifact = (Artifact.AgentModelArtifact) upstreamContext.toArtifact(Artifact.HashContext.defaultHashContext());
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, artifact);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(contextKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.AgentModelArtifact result = (Artifact.AgentModelArtifact) deserialized.get();
            UpstreamContext.OrchestratorUpstreamContext resultContext = 
                    (UpstreamContext.OrchestratorUpstreamContext) result.agentModel();
            
            assertThat(resultContext.workflowGoal()).isEqualTo("Workflow goal");
            assertThat(resultContext.phase()).isEqualTo("planning");
        }
        
        @Test
        @DisplayName("PreviousContext serializes correctly")
        void previousContextRoundTrip() {
            ArtifactKey contextKey = rootKey.createChild();
            
            PreviousContext.DiscoveryAgentPreviousContext previousContext = 
                    PreviousContext.DiscoveryAgentPreviousContext.builder()
                            .artifactKey(contextKey)
                            .previousContextId(contextKey)
                            .serializedOutput("Previous output")
                            .attemptNumber(1)
                            .previousAttemptAt(Instant.now())
                            .build();
            
            Artifact.AgentModelArtifact artifact = (Artifact.AgentModelArtifact) previousContext.toArtifact(Artifact.HashContext.defaultHashContext());
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, artifact);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(contextKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.AgentModelArtifact result = (Artifact.AgentModelArtifact) deserialized.get();
            PreviousContext.DiscoveryAgentPreviousContext resultContext = 
                    (PreviousContext.DiscoveryAgentPreviousContext) result.agentModel();
            
            assertThat(resultContext.serializedOutput()).isEqualTo("Previous output");
            assertThat(resultContext.attemptNumber()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("Curation types serialize correctly")
        void curationTypesRoundTrip() {
            ArtifactKey curationKey = rootKey.createChild();
            
            AgentModels.DiscoveryCuration curation = AgentModels.DiscoveryCuration.builder()
                    .artifactKey(curationKey)
                    .discoveryReports(List.of())
                    .recommendations(List.of())
                    .build();
            
            Artifact.AgentModelArtifact artifact = (Artifact.AgentModelArtifact) curation.toArtifact(Artifact.HashContext.defaultHashContext());
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, artifact);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(curationKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.AgentModelArtifact result = (Artifact.AgentModelArtifact) deserialized.get();
            AgentModels.DiscoveryCuration resultCuration = (AgentModels.DiscoveryCuration) result.agentModel();
            
            assertThat(resultCuration.discoveryReports()).isEmpty();
            assertThat(resultCuration.recommendations()).isEmpty();
        }
    }
    
    // ========== Nested and Complex Structure Tests ==========
    
    @Nested
    @DisplayName("Complex Nested Structures")
    class ComplexNestedStructures {
        
        @Test
        @DisplayName("AgentModelArtifact with nested children serializes correctly")
        void agentModelWithNestedChildrenRoundTrip() {
            ArtifactKey parentKey = rootKey.createChild();
            ArtifactKey child1Key = parentKey.createChild();
            ArtifactKey child2Key = parentKey.createChild();
            
            // Create child agent models
            AgentModels.DiscoveryAgentResult child1 = AgentModels.DiscoveryAgentResult.builder()
                    .contextId(child1Key)
                    .output("Discovery result 1")
                    .build();
            
            AgentModels.DiscoveryAgentResult child2 = AgentModels.DiscoveryAgentResult.builder()
                    .contextId(child2Key)
                    .output("Discovery result 2")
                    .build();
            
            // Create parent with children - use builder for complex constructor
            ArtifactKey c = parentKey.createChild();
            AgentModels.DiscoveryCollectorResult parent = AgentModels.DiscoveryCollectorResult.builder()
                    .contextId(parentKey)
                    .consolidatedOutput("Consolidated discovery results")
                    .collectorDecision(new AgentModels.CollectorDecision(
                            com.hayden.acp_cdc_ai.acp.events.Events.CollectorDecisionType.ADVANCE_PHASE,
                            "All discoveries complete",
                            "planning"
                    ))
                    .discoveryCollectorContext(
                            UpstreamContext.DiscoveryCollectorContext
                                    .builder()
                                    .artifactKey(c)
                                    .selectionRationale("okay")
                                    .curation(
                                            AgentModels.DiscoveryCuration.builder()
                                                    .artifactKey(c.createChild())
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();
            
            Artifact.AgentModelArtifact parentArtifact = (Artifact.AgentModelArtifact) parent.toArtifact(Artifact.HashContext.defaultHashContext());
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, parentArtifact);
            
            // Add children separately (tree builder expects flat insertion)
            for (Artifact child : parentArtifact.children()) {
                artifactTreeBuilder.addArtifact(executionKey, child);
            }
            
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(parentKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.AgentModelArtifact result = (Artifact.AgentModelArtifact) deserialized.get();
            AgentModels.DiscoveryCollectorResult resultCollector = 
                    (AgentModels.DiscoveryCollectorResult) result.agentModel();
            
            assertThat(resultCollector.consolidatedOutput()).isEqualTo("Consolidated discovery results");
            assertThat(resultCollector.decision().decisionType()).isEqualTo(
                    com.hayden.acp_cdc_ai.acp.events.Events.CollectorDecisionType.ADVANCE_PHASE);
        }
        
        @Test
        @DisplayName("Deep artifact hierarchy with mixed types serializes correctly")
        void deepHierarchyMixedTypesRoundTrip() {
            // Build: Execution -> Config -> Request -> Result -> Curation
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            
            ArtifactKey configKey = rootKey.createChild();
            Artifact.ExecutionConfigArtifact config = Artifact.ExecutionConfigArtifact.builder()
                    .artifactKey(configKey)
                    .repositorySnapshotId("snapshot-123")
                    .modelRefs(Map.of("model", "claude-opus"))
                    .toolPolicy(new HashMap<>())
                    .routingPolicy(new HashMap<>())
                    .hash("config-hash")
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();
            artifactTreeBuilder.addArtifact(executionKey, config);
            
            ArtifactKey requestKey = configKey.createChild();
            AgentModels.OrchestratorRequest request = AgentModels.OrchestratorRequest.builder()
                    .contextId(requestKey)
                    .goal("Test goal")
                    .phase("discovery")
                    .build();
            artifactTreeBuilder.addArtifact(executionKey, (Artifact.AgentModelArtifact) request.toArtifact(Artifact.HashContext.defaultHashContext()));
            
            ArtifactKey resultKey = requestKey.createChild();
            AgentModels.OrchestratorAgentResult result = AgentModels.OrchestratorAgentResult.builder()
                    .contextId(resultKey)
                    .output("Test output")
                    .build();
            artifactTreeBuilder.addArtifact(executionKey, result.toArtifact(Artifact.HashContext.defaultHashContext()));
            
            ArtifactKey curationKey = resultKey.createChild();
            AgentModels.DiscoveryCuration curation = AgentModels.DiscoveryCuration.builder()
                    .artifactKey(curationKey)
                    .discoveryReports(new ArrayList<>())
                    .build();
            artifactTreeBuilder.addArtifact(executionKey, curation.toArtifact(Artifact.HashContext.defaultHashContext()));
            
            artifactTreeBuilder.persistExecution(executionKey);
            
            // Verify all levels deserialize correctly
            Optional<ArtifactEntity> configEntity = artifactRepository.findByArtifactKey(configKey.value());
            assertThat(artifactService.deserializeArtifact(configEntity.get())).isPresent();
            
            Optional<ArtifactEntity> requestEntity = artifactRepository.findByArtifactKey(requestKey.value());
            assertThat(artifactService.deserializeArtifact(requestEntity.get())).isPresent();
            
            Optional<ArtifactEntity> resultEntity = artifactRepository.findByArtifactKey(resultKey.value());
            assertThat(artifactService.deserializeArtifact(resultEntity.get())).isPresent();
            
            Optional<ArtifactEntity> curationEntity = artifactRepository.findByArtifactKey(curationKey.value());
            Optional<Artifact> deserializedCuration = artifactService.deserializeArtifact(curationEntity.get());
            assertThat(deserializedCuration).isPresent();

            Artifact.AgentModelArtifact curationArtifact = (Artifact.AgentModelArtifact) deserializedCuration.get();
            AgentModels.DiscoveryCuration deserializedCurationModel = 
                    (AgentModels.DiscoveryCuration) curationArtifact.agentModel();
            assertThat(deserializedCurationModel.discoveryReports()).isEmpty();
        }
        
        @Test
        @DisplayName("Multiple artifact types at same level serialize independently")
        void multipleTypesAtSameLevelRoundTrip() {
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            
            // Add multiple different types as siblings
            ArtifactKey prompt1Key = rootKey.createChild();
            artifactTreeBuilder.addArtifact(executionKey, Artifact.RenderedPromptArtifact.builder()
                    .artifactKey(prompt1Key)
                    .renderedText("Prompt 1")
                    .promptName("p1")
                    .hash("h1")
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build());
            
            ArtifactKey toolCallKey = rootKey.createChild();
            artifactTreeBuilder.addArtifact(executionKey, Artifact.ToolCallArtifact.builder()
                    .artifactKey(toolCallKey)
                    .toolCallId("call-1")
                    .toolName("tool1")
                    .inputJson("{}")
                    .inputHash("ih1")
                    .outputJson("{}")
                    .outputHash("oh1")
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build());
            
            ArtifactKey templateKey = rootKey.createChild();
            artifactTreeBuilder.addArtifact(executionKey, new PromptTemplateVersion(
                    "tpl.test",
                    "Template",
                    "th1",
                    templateKey,
                    Instant.now(),
                    new ArrayList<>()
            ));
            
            ArtifactKey requestKey = rootKey.createChild();
            AgentModels.OrchestratorRequest request = AgentModels.OrchestratorRequest.builder()
                    .contextId(requestKey)
                    .goal("Goal")
                    .phase("Phase")
                    .build();
            artifactTreeBuilder.addArtifact(executionKey, (Artifact.AgentModelArtifact) request.toArtifact(Artifact.HashContext.defaultHashContext()));
            
            artifactTreeBuilder.persistExecution(executionKey);
            
            // Verify each type deserializes correctly
            assertThat(artifactService.deserializeArtifact(
                    artifactRepository.findByArtifactKey(prompt1Key.value()).get()))
                    .get()
                    .isInstanceOf(Artifact.RenderedPromptArtifact.class);
            
            assertThat(artifactService.deserializeArtifact(
                    artifactRepository.findByArtifactKey(toolCallKey.value()).get()))
                    .get()
                    .isInstanceOf(Artifact.ToolCallArtifact.class);
            
            assertThat(artifactService.deserializeArtifact(
                    artifactRepository.findByArtifactKey(templateKey.value()).get()))
                    .get()
                    .isInstanceOf(PromptTemplateVersion.class);
            
            assertThat(artifactService.deserializeArtifact(
                    artifactRepository.findByArtifactKey(requestKey.value()).get()))
                    .get()
                    .isInstanceOf(Artifact.AgentModelArtifact.class);
        }
    }
    
    // ========== Edge Cases and Error Handling ==========
    
    @Nested
    @DisplayName("Edge Cases and Special Scenarios")
    class EdgeCasesAndSpecialScenarios {
        
        @Test
        @DisplayName("Empty collections serialize correctly")
        void emptyCollectionsSerialize() {
            ArtifactKey requestKey = rootKey.createChild();
            
            AgentModels.DiscoveryAgentRequest request = AgentModels.DiscoveryAgentRequest.builder()
                    .contextId(requestKey)
                    .goal("Goal")
                    .subdomainFocus("Focus")
                    .build();
            
            Artifact.AgentModelArtifact artifact = (Artifact.AgentModelArtifact) request.toArtifact(Artifact.HashContext.defaultHashContext());
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, artifact);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(requestKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.AgentModelArtifact result = (Artifact.AgentModelArtifact) deserialized.get();
            assertThat(result.children().size()).isEqualTo(1);
            assertThat(result.metadata()).isEmpty();
        }
        
        @Test
        @DisplayName("Null optional fields serialize correctly")
        void nullOptionalFieldsSerialize() {
            ArtifactKey toolCallKey = rootKey.createChild();
            
            Artifact.ToolCallArtifact toolCall = Artifact.ToolCallArtifact.builder()
                    .artifactKey(toolCallKey)
                    .toolCallId("call-123")
                    .toolName("tool")
                    .inputJson("{}")
                    .inputHash("hash")
                    .outputJson(null) // Null optional field
                    .outputHash(null) // Null optional field
                    .error(null) // Null optional field
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, toolCall);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(toolCallKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.ToolCallArtifact result = (Artifact.ToolCallArtifact) deserialized.get();
            assertThat(result.outputJson()).isNull();
            assertThat(result.outputHash()).isNull();
            assertThat(result.error()).isNull();
        }
        
        @Test
        @DisplayName("Large content serializes correctly")
        void largeContentSerializes() {
            ArtifactKey promptKey = rootKey.createChild();
            
            // Create a large prompt (10KB of text)
            String largeText = "Lorem ipsum dolor sit amet. ".repeat(400);
            
            Artifact.RenderedPromptArtifact prompt = Artifact.RenderedPromptArtifact.builder()
                    .artifactKey(promptKey)
                    .renderedText(largeText)
                    .promptName("large-prompt")
                    .hash("large-hash")
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, prompt);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(promptKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.RenderedPromptArtifact result = (Artifact.RenderedPromptArtifact) deserialized.get();
            assertThat(result.renderedText()).hasSize(largeText.length());
            assertThat(result.renderedText()).startsWith("Lorem ipsum");
        }
        
        @Test
        @DisplayName("Special characters in strings serialize correctly")
        void specialCharactersSerialize() {
            ArtifactKey promptKey = rootKey.createChild();
            
            String specialText = "Special chars: \n\t\r\"'\\/@#$%^&*()[]{}|<>~`";
            
            Artifact.RenderedPromptArtifact prompt = Artifact.RenderedPromptArtifact.builder()
                    .artifactKey(promptKey)
                    .renderedText(specialText)
                    .promptName("special-prompt")
                    .hash("hash")
                    .metadata(Map.of("key", "value with \"quotes\""))
                    .children(new ArrayList<>())
                    .build();
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, prompt);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(promptKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.RenderedPromptArtifact result = (Artifact.RenderedPromptArtifact) deserialized.get();
            assertThat(result.renderedText()).isEqualTo(specialText);
        }
        
        @Test
        @DisplayName("Unicode characters serialize correctly")
        void unicodeCharactersSerialize() {
            ArtifactKey promptKey = rootKey.createChild();
            
            String unicodeText = "Unicode: 你好世界 🌍 αβγδ Привет мир";
            
            Artifact.RenderedPromptArtifact prompt = Artifact.RenderedPromptArtifact.builder()
                    .artifactKey(promptKey)
                    .renderedText(unicodeText)
                    .promptName("unicode-prompt")
                    .hash("hash")
                    .metadata(new HashMap<>())
                    .children(new ArrayList<>())
                    .build();
            
            artifactTreeBuilder.addArtifact(executionKey, createExecutionArtifact(rootKey));
            artifactTreeBuilder.addArtifact(executionKey, prompt);
            artifactTreeBuilder.persistExecution(executionKey);
            
            Optional<ArtifactEntity> entity = artifactRepository.findByArtifactKey(promptKey.value());
            Optional<Artifact> deserialized = artifactService.deserializeArtifact(entity.get());
            
            Artifact.RenderedPromptArtifact result = (Artifact.RenderedPromptArtifact) deserialized.get();
            assertThat(result.renderedText()).isEqualTo(unicodeText);
        }
    }
    
    // ========== Helper Methods ==========
    
    private Artifact.ExecutionArtifact createExecutionArtifact(ArtifactKey key) {
        return Artifact.ExecutionArtifact.builder()
                .hash(UUID.randomUUID().toString())
                .artifactKey(key)
                .workflowRunId("test-run-" + System.nanoTime())
                .startedAt(Instant.now())
                .status(Artifact.ExecutionStatus.RUNNING)
                .metadata(new HashMap<>())
                .children(new ArrayList<>())
                .build();
    }
}
