package com.hayden.multiagentide.propagation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.propagation.controller.dto.*;
import com.hayden.multiagentide.propagation.repository.PropagatorRegistrationEntity;
import com.hayden.multiagentide.propagation.service.PropagatorAttachableCatalogService;
import com.hayden.multiagentide.propagation.service.PropagatorDiscoveryService;
import com.hayden.multiagentide.propagation.service.PropagatorRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/propagators")
@RequiredArgsConstructor
public class PropagatorController {

    private final PropagatorRegistrationService registrationService;
    private final PropagatorDiscoveryService discoveryService;
    private final PropagatorAttachableCatalogService attachableCatalogService;
    private final ObjectMapper objectMapper;

    @GetMapping("/attachables")
    public ResponseEntity<ReadPropagatorAttachableTargetsResponse> attachables() {
        return ResponseEntity.ok(attachableCatalogService.readAttachableTargets());
    }

    @PostMapping("/registrations")
    public ResponseEntity<PropagatorRegistrationResponse> register(@RequestBody PropagatorRegistrationRequest request) {
        return ResponseEntity.ok(registrationService.register(request));
    }

    @GetMapping("/layers/{layerId}/registrations")
    public ResponseEntity<ReadPropagatorsByLayerResponse> byLayer(@PathVariable String layerId) {
        List<ReadPropagatorsByLayerResponse.PropagatorSummary> summaries = discoveryService.getActivePropagatorsByLayer(layerId).stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(ReadPropagatorsByLayerResponse.builder().layerId(layerId).propagators(summaries).totalCount(summaries.size()).build());
    }

    @PostMapping("/registrations/{registrationId}/deactivate")
    public ResponseEntity<DeactivatePropagatorResponse> deactivate(@PathVariable String registrationId) {
        return ResponseEntity.ok(registrationService.deactivate(registrationId));
    }

    @PutMapping("/registrations/{registrationId}/layers/{layerId}")
    public ResponseEntity<PutPropagatorLayerResponse> updateLayer(@PathVariable String registrationId,
                                                                  @PathVariable String layerId,
                                                                  @RequestBody PutPropagatorLayerRequest request) {
        return ResponseEntity.ok(registrationService.updateLayerBinding(registrationId, request));
    }

    private ReadPropagatorsByLayerResponse.PropagatorSummary toSummary(PropagatorRegistrationEntity entity) {
        try {
            var node = objectMapper.readTree(entity.getPropagatorJson());
            return ReadPropagatorsByLayerResponse.PropagatorSummary.builder()
                    .registrationId(entity.getRegistrationId())
                    .name(node.path("name").asText(entity.getRegistrationId()))
                    .description(node.path("description").asText(""))
                    .propagatorKind(entity.getPropagatorKind())
                    .status(entity.getStatus())
                    .priority(node.path("priority").asInt(0))
                    .build();
        } catch (Exception e) {
            return ReadPropagatorsByLayerResponse.PropagatorSummary.builder()
                    .registrationId(entity.getRegistrationId())
                    .name(entity.getRegistrationId())
                    .description("")
                    .propagatorKind(entity.getPropagatorKind())
                    .status(entity.getStatus())
                    .priority(0)
                    .build();
        }
    }
}
