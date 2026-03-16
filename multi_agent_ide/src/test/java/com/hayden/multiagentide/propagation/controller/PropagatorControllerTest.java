package com.hayden.multiagentide.propagation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.propagation.controller.dto.*;
import com.hayden.multiagentide.propagation.repository.PropagatorRegistrationEntity;
import com.hayden.multiagentide.propagation.service.PropagatorAttachableCatalogService;
import com.hayden.multiagentide.propagation.service.PropagatorDiscoveryService;
import com.hayden.multiagentide.propagation.service.PropagatorRegistrationService;
import com.hayden.multiagentide.config.ValidationExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

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

class PropagatorControllerTest {

    private final PropagatorRegistrationService registrationService = mock(PropagatorRegistrationService.class);
    private final PropagatorDiscoveryService discoveryService = mock(PropagatorDiscoveryService.class);
    private final PropagatorAttachableCatalogService attachableCatalogService = mock(PropagatorAttachableCatalogService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PropagatorController(registrationService, discoveryService, attachableCatalogService, objectMapper))
                .setValidator(validator)
                .setControllerAdvice(new ValidationExceptionHandler())
                .build();
    }

    @Test
    void attachables_returnsCatalog() throws Exception {
        when(attachableCatalogService.readAttachableTargets()).thenReturn(
                new ReadPropagatorAttachableTargetsResponse(List.of(
                        new ReadPropagatorAttachableTargetsResponse.ActionTarget(
                                "workflow-agent/coordinateWorkflow", "workflow-agent", "coordinateWorkflow", "coordinateWorkflow",
                                List.of("ACTION_REQUEST", "ACTION_RESPONSE"))
                )));

        mockMvc.perform(get("/api/propagators/attachables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions[0].layerId").value("workflow-agent/coordinateWorkflow"));
    }

    @Test
    void register_returnsServiceResponse() throws Exception {
        when(registrationService.register(any())).thenReturn(
                PropagatorRegistrationResponse.builder().ok(true).registrationId("prop-1").status("ACTIVE").message("ok").build());

        mockMvc.perform(post("/api/propagators/registrations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"prop","description":"prop","sourcePath":"prop.json","propagatorKind":"AI_TEXT","priority":1,"propagationMode":"ESCALATE","layerBindings":[],"executor":{"executorType":"AI_PROPAGATOR","modelRef":"gpt","timeoutMs":1000},"activate":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationId").value("prop-1"));
    }

    @Test
    void byLayer_returnsMappedSummaries() throws Exception {
        when(discoveryService.getActivePropagatorsByLayer("workflow-agent"))
                .thenReturn(List.of(PropagatorRegistrationEntity.builder()
                        .registrationId("prop-1")
                        .propagatorKind("AI_TEXT")
                        .status("ACTIVE")
                        .propagatorJson("""
                                {"name":"Propagator One","description":"desc","priority":3}
                                """)
                        .build()));

        mockMvc.perform(post("/api/propagators/registrations/by-layer")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"layerId":"workflow-agent"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.propagators[0].name").value("Propagator One"));
    }

    @Test
    void deactivate_returnsServiceResponse() throws Exception {
        when(registrationService.deactivate("prop-1")).thenReturn(
                DeactivatePropagatorResponse.builder().ok(true).registrationId("prop-1").status("INACTIVE").message("done").build());

        mockMvc.perform(post("/api/propagators/registrations/prop-1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void byLayer_blankLayerId_returns400() throws Exception {
        mockMvc.perform(post("/api/propagators/registrations/by-layer")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"layerId":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields[0].field").value("layerId"));
    }

    @Test
    void updateLayer_returnsServiceResponse() throws Exception {
        when(registrationService.updateLayerBinding(any(), any())).thenReturn(
                PutPropagatorLayerResponse.builder().ok(true).registrationId("prop-1").layerId("workflow-agent").message("updated").build());

        mockMvc.perform(put("/api/propagators/registrations/prop-1/layers")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"registrationId":"prop-1","layerBinding":{"layerId":"workflow-agent","enabled":true,"includeDescendants":false,"isInheritable":false,"isPropagatedToParent":false,"matcherKey":"PATH","matcherType":"EXACT","matcherText":"workflow-agent","matchOn":"ACTION_REQUEST"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layerId").value("workflow-agent"));
    }
}
