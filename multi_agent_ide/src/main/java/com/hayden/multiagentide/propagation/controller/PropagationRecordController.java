package com.hayden.multiagentide.propagation.controller;

import com.hayden.multiagentide.propagation.controller.dto.ReadPropagationRecordsResponse;
import com.hayden.multiagentide.propagation.service.PropagationRecordQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/propagations/records")
@RequiredArgsConstructor
public class PropagationRecordController {

    private final PropagationRecordQueryService queryService;

    @GetMapping
    public ResponseEntity<ReadPropagationRecordsResponse> recent(@RequestParam(value = "layerId", required = false) String layerId,
                                                                 @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(queryService.recent(layerId, limit));
    }
}
