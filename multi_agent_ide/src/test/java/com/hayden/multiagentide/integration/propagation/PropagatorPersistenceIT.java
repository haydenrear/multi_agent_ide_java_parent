package com.hayden.multiagentide.integration.propagation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.filter.repository.LayerRepository;
import com.hayden.multiagentide.filter.service.LayerHierarchyBootstrap;
import com.hayden.multiagentide.propagation.repository.PropagationItemEntity;
import com.hayden.multiagentide.propagation.repository.PropagationItemRepository;
import com.hayden.multiagentide.propagation.repository.PropagationRecordEntity;
import com.hayden.multiagentide.propagation.repository.PropagationRecordRepository;
import com.hayden.multiagentide.propagation.repository.PropagatorRegistrationEntity;
import com.hayden.multiagentide.propagation.repository.PropagatorRegistrationRepository;
import com.hayden.multiagentidelib.propagation.model.PropagationItemStatus;
import com.hayden.multiagentidelib.propagation.model.PropagationResolutionType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "testdocker"})
class PropagatorPersistenceIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private LayerHierarchyBootstrap layerHierarchyBootstrap;
    @Autowired
    private LayerRepository layerRepository;
    @Autowired
    private PropagatorRegistrationRepository propagatorRegistrationRepository;
    @Autowired
    private PropagationItemRepository propagationItemRepository;
    @Autowired
    private PropagationRecordRepository propagationRecordRepository;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        propagationRecordRepository.deleteAll();
        propagationItemRepository.deleteAll();
        propagatorRegistrationRepository.deleteAll();
        layerRepository.deleteAll();
        layerHierarchyBootstrap.seedLayersIfAbsent();
    }

    @Test
    void attachables_excludesInternalAutomationActions() throws Exception {
        mockMvc.perform(get("/api/propagators/attachables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions[?(@.layerId == 'ai-filter/filter-action')]").doesNotExist())
                .andExpect(jsonPath("$.actions[?(@.layerId == 'ai-propagator/propagate-action')]").doesNotExist())
                .andExpect(jsonPath("$.actions[?(@.layerId == 'ai-transformer/transform-controller-response')]").doesNotExist());
    }

    @Test
    void registrationLifecycle_andQueryEndpoints_persistCorrectly() throws Exception {
        String registrationId = registerPropagator("workflow-agent");

        mockMvc.perform(get("/api/propagators/layers/workflow-agent/registrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.propagators[0].registrationId").value(registrationId));

        PropagatorRegistrationEntity entity = propagatorRegistrationRepository.findByRegistrationId(registrationId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");

        mockMvc.perform(put("/api/propagators/registrations/{registrationId}/layers/{layerId}", registrationId, "controller")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"registrationId":"%s","layerBinding":{"layerId":"controller","enabled":true,"includeDescendants":false,"isInheritable":false,"isPropagatedToParent":false,"matcherKey":"TEXT","matcherType":"EQUALS","matcherText":"workflow","matchOn":"ACTION_RESPONSE"}}
                                """.formatted(registrationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        entityManager.clear();
        entity = propagatorRegistrationRepository.findByRegistrationId(registrationId).orElseThrow();
        List<Map<String, Object>> layerBindings = objectMapper.readValue(entity.getLayerBindingsJson(), new TypeReference<>() {});
        assertThat(layerBindings).anyMatch(binding -> "controller".equals(binding.get("layerId")));

        mockMvc.perform(post("/api/propagators/registrations/{registrationId}/deactivate", registrationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void registration_rejectsInternalAutomationLayer() throws Exception {
        mockMvc.perform(post("/api/propagators/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequestJson("ai-propagator/propagate-action")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("layerId cannot target internal automation layers")));
    }

    @Test
    void propagationItems_andRecordsEndpoints_roundTripPersistence() throws Exception {
        propagationItemRepository.save(PropagationItemEntity.builder()
                .itemId("prop-item-1")
                .registrationId("propagator-1")
                .layerId("workflow-agent/coordinateWorkflow")
                .sourceNodeId("node-1")
                .sourceName("workflow")
                .summaryText("needs review")
                .propagatedText("payload")
                .mode("ESCALATE")
                .status(PropagationItemStatus.PENDING.name())
                .resolutionType("UNRESOLVED")
                .correlationKey("corr-1")
                .createdAt(Instant.parse("2026-03-12T10:15:30Z"))
                .updatedAt(Instant.parse("2026-03-12T10:15:30Z"))
                .build());

        propagationRecordRepository.save(PropagationRecordEntity.builder()
                .recordId("prop-record-1")
                .registrationId("propagator-1")
                .layerId("workflow-agent/coordinateWorkflow")
                .sourceNodeId("node-1")
                .sourceType("ACTION_REQUEST")
                .action("ITEM_CREATED")
                .beforePayload("before")
                .afterPayload("after")
                .mode("ESCALATE")
                .correlationKey("corr-1")
                .createdAt(Instant.parse("2026-03-12T10:15:30Z"))
                .build());

        mockMvc.perform(get("/api/propagations/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].itemId").value("prop-item-1"));

        mockMvc.perform(post("/api/propagations/items/prop-item-1/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolutionType":"ACKNOWLEDGED","resolutionNotes":"approved"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionType").value(PropagationResolutionType.ACKNOWLEDGED.name()));

        entityManager.clear();
        PropagationItemEntity updated = propagationItemRepository.findByItemId("prop-item-1").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("RESOLVED");

        mockMvc.perform(get("/api/propagations/records")
                        .param("layerId", "workflow-agent/coordinateWorkflow")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].recordId").exists());
    }

    private String registerPropagator(String layerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/propagators/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequestJson(layerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("registrationId").asText();
    }

    private String registrationRequestJson(String layerId) {
        return """
                {"name":"AI Propagator","description":"routes output upward","sourcePath":"propagator.json","propagatorKind":"AI_TEXT","priority":1,"propagationMode":"ESCALATION_REQUIRED","isInheritable":false,"isPropagatedToParent":false,"layerBindings":[{"layerId":"%s","layerType":"WORKFLOW_AGENT_ACTION","layerKey":"coordinateWorkflow","enabled":true,"includeDescendants":false,"isInheritable":false,"isPropagatedToParent":false,"matcherKey":"TEXT","matcherType":"EQUALS","matcherText":"workflow","matchOn":"ACTION_REQUEST"}],"executor":{"executorType":"AI_PROPAGATOR","modelRef":"gpt-4.1","timeoutMs":1000,"registrarPrompt":"monitor"},"activate":true}
                """.formatted(layerId);
    }
}
