package com.hayden.multiagentide.transformation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.transformation.controller.dto.*;
import com.hayden.multiagentide.transformation.repository.TransformerRegistrationEntity;
import com.hayden.multiagentide.transformation.service.TransformerAttachableCatalogService;
import com.hayden.multiagentide.transformation.service.TransformerDiscoveryService;
import com.hayden.multiagentide.transformation.service.TransformerRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransformerControllerTest {

    private final TransformerRegistrationService registrationService = mock(TransformerRegistrationService.class);
    private final TransformerDiscoveryService discoveryService = mock(TransformerDiscoveryService.class);
    private final TransformerAttachableCatalogService attachableCatalogService = mock(TransformerAttachableCatalogService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new TransformerController(registrationService, discoveryService, attachableCatalogService, objectMapper))
                .build();
    }

    @Test
    void attachables_returnsCatalog() throws Exception {
        when(attachableCatalogService.readAttachableTargets()).thenReturn(
                new ReadTransformerAttachableTargetsResponse(List.of(
                        new ReadTransformerAttachableTargetsResponse.EndpointTarget(
                                "controller", "UiController", "com.hayden.multiagentide.controller.UiController", "index", "GET", "/api/ui")
                )));

        mockMvc.perform(get("/api/transformers/attachables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoints[0].controllerId").value("UiController"));
    }

    @Test
    void register_returnsServiceResponse() throws Exception {
        when(registrationService.register(any())).thenReturn(
                TransformerRegistrationResponse.builder().ok(true).registrationId("transformer-1").status("ACTIVE").message("ok").build());

        mockMvc.perform(post("/api/transformers/registrations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"transformer","description":"transformer","sourcePath":"transformer.json","transformerKind":"AI_TEXT","priority":1,"replaceEndpointResponse":true,"layerBindings":[],"executor":{"executorType":"AI_TRANSFORMER","modelRef":"gpt","timeoutMs":1000},"activate":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationId").value("transformer-1"));
    }

    @Test
    void byLayer_returnsMappedSummaries() throws Exception {
        when(discoveryService.getActiveTransformersByLayer("controller"))
                .thenReturn(List.of(TransformerRegistrationEntity.builder()
                        .registrationId("transformer-1")
                        .transformerKind("AI_TEXT")
                        .status("ACTIVE")
                        .transformerJson("""
                                {"name":"Transformer One","description":"desc","priority":4}
                                """)
                        .build()));

        mockMvc.perform(get("/api/transformers/layers/controller/registrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transformers[0].name").value("Transformer One"));
    }

    @Test
    void deactivate_returnsServiceResponse() throws Exception {
        when(registrationService.deactivate("transformer-1")).thenReturn(
                DeactivateTransformerResponse.builder().ok(true).registrationId("transformer-1").status("INACTIVE").message("done").build());

        mockMvc.perform(post("/api/transformers/registrations/transformer-1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void updateLayer_returnsServiceResponse() throws Exception {
        when(registrationService.updateLayerBinding(any(), any())).thenReturn(
                PutTransformerLayerResponse.builder().ok(true).registrationId("transformer-1").layerId("controller").message("updated").build());

        mockMvc.perform(put("/api/transformers/registrations/transformer-1/layers/controller")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"registrationId":"transformer-1","layerBinding":{"layerId":"controller","enabled":true,"includeDescendants":false,"isInheritable":false,"isPropagatedToParent":false,"matcherKey":"PATH","matcherType":"EXACT","matcherText":"UiController#index","matchOn":"CONTROLLER_ENDPOINT_RESPONSE"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layerId").value("controller"));
    }
}
