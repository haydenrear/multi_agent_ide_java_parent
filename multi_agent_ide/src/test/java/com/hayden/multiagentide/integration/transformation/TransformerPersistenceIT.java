package com.hayden.multiagentide.integration.transformation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.filter.repository.LayerRepository;
import com.hayden.multiagentide.filter.service.LayerHierarchyBootstrap;
import com.hayden.multiagentide.transformation.repository.TransformationRecordEntity;
import com.hayden.multiagentide.transformation.repository.TransformationRecordRepository;
import com.hayden.multiagentide.transformation.repository.TransformerRegistrationEntity;
import com.hayden.multiagentide.transformation.repository.TransformerRegistrationRepository;
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
class TransformerPersistenceIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private LayerHierarchyBootstrap layerHierarchyBootstrap;
    @Autowired
    private LayerRepository layerRepository;
    @Autowired
    private TransformerRegistrationRepository transformerRegistrationRepository;
    @Autowired
    private TransformationRecordRepository transformationRecordRepository;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        transformationRecordRepository.deleteAll();
        transformerRegistrationRepository.deleteAll();
        layerRepository.deleteAll();
        layerHierarchyBootstrap.seedLayersIfAbsent();
    }

    @Test
    void attachables_excludesInternalAutomationControllers() throws Exception {
        mockMvc.perform(get("/api/transformers/attachables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoints[?(@.controllerClass =~ /.*filter.*/)]").doesNotExist())
                .andExpect(jsonPath("$.endpoints[?(@.controllerClass =~ /.*propagation.*/)]").doesNotExist())
                .andExpect(jsonPath("$.endpoints[?(@.controllerClass =~ /.*transformation.*/)]").doesNotExist());
    }

    @Test
    void registrationLifecycle_andQueryEndpoints_persistCorrectly() throws Exception {
        String registrationId = registerTransformer("controller");

        mockMvc.perform(post("/api/transformers/registrations/by-layer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"layerId":"controller"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.transformers[0].registrationId").value(registrationId));

        TransformerRegistrationEntity entity = transformerRegistrationRepository.findByRegistrationId(registrationId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");

        mockMvc.perform(put("/api/transformers/registrations/{registrationId}/layers", registrationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"registrationId":"%s","layerBinding":{"layerId":"controller-ui-event-poll","enabled":true,"includeDescendants":false,"isInheritable":false,"isPropagatedToParent":false,"matcherKey":"TEXT","matcherType":"EQUALS","matcherText":"UiController#index","matchOn":"CONTROLLER_ENDPOINT_RESPONSE"}}
                                """.formatted(registrationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        entityManager.clear();
        entity = transformerRegistrationRepository.findByRegistrationId(registrationId).orElseThrow();
        List<Map<String, Object>> layerBindings = objectMapper.readValue(entity.getLayerBindingsJson(), new TypeReference<>() {});
        assertThat(layerBindings).anyMatch(binding -> "controller-ui-event-poll".equals(binding.get("layerId")));

        mockMvc.perform(post("/api/transformers/registrations/{registrationId}/deactivate", registrationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void registration_rejectsInternalAutomationLayer() throws Exception {
        mockMvc.perform(post("/api/transformers/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequestJson("ai-transformer/transform-controller-response")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("layerId cannot target internal automation layers")));
    }

    @Test
    void transformationRecordsEndpoint_readsPersistedRecords() throws Exception {
        transformationRecordRepository.save(TransformationRecordEntity.builder()
                .recordId("transform-record-1")
                .registrationId("transformer-1")
                .layerId("controller")
                .controllerId("UiController")
                .endpointId("index")
                .action("TRANSFORMED")
                .beforePayload("before")
                .afterPayload("after")
                .createdAt(Instant.parse("2026-03-12T10:15:30Z"))
                .build());

        mockMvc.perform(get("/api/transformations/records")
                        .param("layerId", "controller")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].recordId").value("transform-record-1"));
    }

    private String registerTransformer(String layerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/transformers/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequestJson(layerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("registrationId").asText();
    }

    private String registrationRequestJson(String layerId) {
        return """
                {"name":"AI Transformer","description":"improves controller output","sourcePath":"transformer.json","transformerKind":"AI_TEXT","priority":1,"replaceEndpointResponse":true,"isInheritable":false,"isPropagatedToParent":false,"layerBindings":[{"layerId":"%s","layerType":"CONTROLLER","layerKey":"controller","enabled":true,"includeDescendants":false,"isInheritable":false,"isPropagatedToParent":false,"matcherKey":"TEXT","matcherType":"EQUALS","matcherText":"UiController#index","matchOn":"CONTROLLER_ENDPOINT_RESPONSE"}],"executor":{"executorType":"AI_TRANSFORMER","modelRef":"gpt-4.1","timeoutMs":1000,"registrarPrompt":"reshape"},"activate":true}
                """.formatted(layerId);
    }
}
