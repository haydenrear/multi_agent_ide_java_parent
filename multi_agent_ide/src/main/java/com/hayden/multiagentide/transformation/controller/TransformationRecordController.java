package com.hayden.multiagentide.transformation.controller;

import com.hayden.multiagentide.transformation.controller.dto.ReadTransformationRecordsResponse;
import com.hayden.multiagentide.transformation.service.TransformationRecordQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transformations/records")
@RequiredArgsConstructor
public class TransformationRecordController {

    private final TransformationRecordQueryService queryService;

    @GetMapping
    public ResponseEntity<ReadTransformationRecordsResponse> recent(@RequestParam(value = "layerId", required = false) String layerId,
                                                                    @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(queryService.recent(layerId, limit));
    }
}
