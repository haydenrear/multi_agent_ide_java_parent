package com.hayden.multiagentide.propagation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.model.PropagationResolutionType;
import com.hayden.multiagentide.propagation.controller.dto.ResolvePropagationItemResponse;
import com.hayden.multiagentide.propagation.service.PropagationItemService;
import com.hayden.multiagentide.propagation.repository.PropagationItemEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PropagationControllerTest {

    private final PropagationItemService propagationItemService = mock(PropagationItemService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PropagationController(propagationItemService, new ObjectMapper())).build();
    }

    @Test
    void items_returnsPendingItems() throws Exception {
        when(propagationItemService.findPendingItems()).thenReturn(List.of(
                PropagationItemEntity.builder()
                        .itemId("item-1")
                        .registrationId("prop-1")
                        .layerId("workflow-agent/coordinateWorkflow")
                        .sourceNodeId("node-1")
                        .sourceName("workflow")
                        .summaryText("needs review")
                        .mode("ESCALATE")
                        .status("PENDING")
                        .createdAt(Instant.parse("2026-03-12T10:15:30Z"))
                        .resolvedAt(null)
                        .build()
        ));

        mockMvc.perform(get("/api/propagations/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].itemId").value("item-1"));
    }

    @Test
    void resolve_returnsServiceResponse() throws Exception {
        when(propagationItemService.resolve(eq("item-1"), eq(PropagationResolutionType.ACKNOWLEDGED), eq("looks good")))
                .thenReturn(ResolvePropagationItemResponse.builder()
                        .ok(true)
                        .itemId("item-1")
                        .status("RESOLVED")
                        .resolutionType("ACKNOWLEDGED")
                        .message("done")
                        .build());

        mockMvc.perform(post("/api/propagations/items/item-1/resolve")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"resolutionType":"ACKNOWLEDGED","resolutionNotes":"looks good"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }
}
