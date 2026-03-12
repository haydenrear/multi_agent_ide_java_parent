package com.hayden.multiagentide.propagation.controller;

import com.hayden.multiagentide.propagation.controller.dto.ReadPropagationRecordsResponse;
import com.hayden.multiagentide.propagation.service.PropagationRecordQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PropagationRecordControllerTest {

    private final PropagationRecordQueryService queryService = mock(PropagationRecordQueryService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PropagationRecordController(queryService)).build();
    }

    @Test
    void recent_returnsRecordSummaries() throws Exception {
        when(queryService.recent("workflow-agent/coordinateWorkflow", 10)).thenReturn(
                ReadPropagationRecordsResponse.builder()
                        .records(List.of(ReadPropagationRecordsResponse.RecordSummary.builder()
                                .recordId("rec-1")
                                .registrationId("prop-1")
                                .layerId("workflow-agent/coordinateWorkflow")
                                .sourceType("ACTION_REQUEST")
                                .action("ITEM_CREATED")
                                .sourceNodeId("node-1")
                                .createdAt(Instant.parse("2026-03-12T10:15:30Z"))
                                .build()))
                        .totalCount(1)
                        .build());

        mockMvc.perform(get("/api/propagations/records")
                        .param("layerId", "workflow-agent/coordinateWorkflow")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].recordId").value("rec-1"));
    }
}
