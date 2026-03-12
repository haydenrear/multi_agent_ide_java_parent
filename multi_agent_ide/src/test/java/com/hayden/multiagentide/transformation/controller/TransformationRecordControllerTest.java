package com.hayden.multiagentide.transformation.controller;

import com.hayden.multiagentide.transformation.controller.dto.ReadTransformationRecordsResponse;
import com.hayden.multiagentide.transformation.service.TransformationRecordQueryService;
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

class TransformationRecordControllerTest {

    private final TransformationRecordQueryService queryService = mock(TransformationRecordQueryService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TransformationRecordController(queryService)).build();
    }

    @Test
    void recent_returnsRecordSummaries() throws Exception {
        when(queryService.recent("controller", 10)).thenReturn(
                ReadTransformationRecordsResponse.builder()
                        .records(List.of(ReadTransformationRecordsResponse.RecordSummary.builder()
                                .recordId("rec-1")
                                .registrationId("transformer-1")
                                .layerId("controller")
                                .controllerId("UiController")
                                .endpointId("index")
                                .action("TRANSFORMED")
                                .createdAt(Instant.parse("2026-03-12T10:15:30Z"))
                                .build()))
                        .totalCount(1)
                        .build());

        mockMvc.perform(get("/api/transformations/records")
                        .param("layerId", "controller")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].recordId").value("rec-1"));
    }
}
