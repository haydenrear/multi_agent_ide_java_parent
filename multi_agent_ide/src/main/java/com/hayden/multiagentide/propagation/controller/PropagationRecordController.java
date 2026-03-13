package com.hayden.multiagentide.propagation.controller;

import com.hayden.multiagentide.propagation.controller.dto.ReadPropagationRecordsResponse;
import com.hayden.multiagentide.propagation.service.PropagationRecordQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/propagations/records")
@RequiredArgsConstructor
@Tag(name = "Propagation Records", description = "Query recent propagation execution records")
public class PropagationRecordController {

    private final PropagationRecordQueryService queryService;

    @GetMapping
    @Operation(summary = "List recent propagation execution records",
            description = "Returns recent propagation execution records, optionally filtered by layerId. "
                    + "Propagators are the escalatory mechanism for out-of-domain (OOD) signal extraction — "
                    + "these records show what was propagated, when, and at which layer. "
                    + "Use this to audit propagator behavior or debug why an expected escalation did not fire. "
                    + "See PropagatorRegistrationEntity for the propagator lifecycle model.")
    public ResponseEntity<ReadPropagationRecordsResponse> recent(
            @Parameter(description = "Filter records to a specific layer ID (optional)") @RequestParam(value = "layerId", required = false) String layerId,
            @Parameter(description = "Maximum number of records to return (default 50)") @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(queryService.recent(layerId, limit));
    }
}
