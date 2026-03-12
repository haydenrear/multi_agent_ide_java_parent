package com.hayden.multiagentide.transformation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.transformation.controller.dto.*;
import com.hayden.multiagentide.transformation.repository.TransformerRegistrationEntity;
import com.hayden.multiagentide.transformation.service.TransformerAttachableCatalogService;
import com.hayden.multiagentide.transformation.service.TransformerDiscoveryService;
import com.hayden.multiagentide.transformation.service.TransformerRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transformers")
@RequiredArgsConstructor
public class TransformerController {

    private final TransformerRegistrationService registrationService;
    private final TransformerDiscoveryService discoveryService;
    private final TransformerAttachableCatalogService attachableCatalogService;
    private final ObjectMapper objectMapper;

    @GetMapping("/attachables")
    public ResponseEntity<ReadTransformerAttachableTargetsResponse> attachables() {
        return ResponseEntity.ok(attachableCatalogService.readAttachableTargets());
    }

    @PostMapping("/registrations")
    public ResponseEntity<TransformerRegistrationResponse> register(@RequestBody TransformerRegistrationRequest request) {
        return ResponseEntity.ok(registrationService.register(request));
    }

    @GetMapping("/layers/{layerId}/registrations")
    public ResponseEntity<ReadTransformersByLayerResponse> byLayer(@PathVariable String layerId) {
        List<ReadTransformersByLayerResponse.TransformerSummary> summaries = discoveryService.getActiveTransformersByLayer(layerId).stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(ReadTransformersByLayerResponse.builder().layerId(layerId).transformers(summaries).totalCount(summaries.size()).build());
    }

    @PostMapping("/registrations/{registrationId}/deactivate")
    public ResponseEntity<DeactivateTransformerResponse> deactivate(@PathVariable String registrationId) {
        return ResponseEntity.ok(registrationService.deactivate(registrationId));
    }

    @PutMapping("/registrations/{registrationId}/layers/{layerId}")
    public ResponseEntity<PutTransformerLayerResponse> updateLayer(@PathVariable String registrationId,
                                                                   @PathVariable String layerId,
                                                                   @RequestBody PutTransformerLayerRequest request) {
        return ResponseEntity.ok(registrationService.updateLayerBinding(registrationId, request));
    }

    private ReadTransformersByLayerResponse.TransformerSummary toSummary(TransformerRegistrationEntity entity) {
        try {
            var node = objectMapper.readTree(entity.getTransformerJson());
            return ReadTransformersByLayerResponse.TransformerSummary.builder()
                    .registrationId(entity.getRegistrationId())
                    .name(node.path("name").asText(entity.getRegistrationId()))
                    .description(node.path("description").asText(""))
                    .transformerKind(entity.getTransformerKind())
                    .status(entity.getStatus())
                    .priority(node.path("priority").asInt(0))
                    .build();
        } catch (Exception e) {
            return ReadTransformersByLayerResponse.TransformerSummary.builder()
                    .registrationId(entity.getRegistrationId())
                    .name(entity.getRegistrationId())
                    .description("")
                    .transformerKind(entity.getTransformerKind())
                    .status(entity.getStatus())
                    .priority(0)
                    .build();
        }
    }
}
