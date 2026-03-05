package com.hayden.multiagentide.filter.controller;

import com.hayden.multiagentide.filter.controller.dto.*;
import com.hayden.multiagentide.filter.repository.LayerEntity;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationEntity;
import com.hayden.multiagentide.filter.service.LayerService;
import com.hayden.multiagentide.filter.service.PolicyDiscoveryService;
import com.hayden.multiagentide.filter.service.PolicyRegistrationService;
import com.hayden.multiagentide.filter.service.FilterDecisionQueryService;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the layered data policy filtering subsystem.
 */
@RestController
@RequestMapping("/api/filters")
@RequiredArgsConstructor
public class FilterPolicyController {

    private final PolicyDiscoveryService policyDiscoveryService;
    private final PolicyRegistrationService policyRegistrationService;
    private final FilterDecisionQueryService filterDecisionQueryService;
    private final LayerService layerService;

    // ── US1: Discovery ───────────────────────────────────────────────

    @GetMapping("/layers/{layerId}/policies")
    public ResponseEntity<ReadPoliciesByLayerResponse> getPoliciesByLayer(
            @PathVariable String layerId,
            @RequestParam(defaultValue = "ACTIVE") String status) {

        List<PolicyRegistrationEntity> policies;
        if ("ACTIVE".equalsIgnoreCase(status)) {
            policies = policyDiscoveryService.getActivePoliciesByLayer(layerId);
        } else {
            policies = policyDiscoveryService.getActivePoliciesDirectlyBound(layerId);
        }

        List<ReadPoliciesByLayerResponse.PolicySummary> summaries = policies.stream()
                .map(p -> ReadPoliciesByLayerResponse.PolicySummary.builder()
                        .policyId(p.getRegistrationId())
                        .name(extractFilterField(p, "name"))
                        .description(extractFilterField(p, "description"))
                        .sourcePath(extractFilterField(p, "sourcePath"))
                        .filterType(p.getFilterKind())
                        .status(p.getStatus())
                        .priority(extractPriority(p))
                        .build())
                .toList();

        return ResponseEntity.ok(ReadPoliciesByLayerResponse.builder()
                .layerId(layerId)
                .policies(summaries)
                .totalCount(summaries.size())
                .build());
    }

    @GetMapping("/layers/{layerId}/children")
    public ResponseEntity<ReadLayerChildrenResponse> getLayerChildren(
            @PathVariable String layerId,
            @RequestParam(defaultValue = "false") boolean recursive) {

        List<LayerEntity> children = layerService.getChildLayers(layerId, recursive);

        List<ReadLayerChildrenResponse.LayerSummary> summaries = children.stream()
                .map(l -> ReadLayerChildrenResponse.LayerSummary.builder()
                        .layerId(l.getLayerId())
                        .layerType(l.getLayerType())
                        .layerKey(l.getLayerKey())
                        .parentLayerId(l.getParentLayerId())
                        .depth(l.getDepth())
                        .build())
                .toList();

        return ResponseEntity.ok(ReadLayerChildrenResponse.builder()
                .layerId(layerId)
                .children(summaries)
                .totalCount(summaries.size())
                .build());
    }

    @PostMapping("/json-path-filters/policies")
    public ResponseEntity<PolicyRegistrationResponse> registerJsonPathFilter(
            @RequestBody PolicyRegistrationRequest request) {
        return ResponseEntity.ok(policyRegistrationService.registerPolicy(
                FilterEnums.FilterKind.JSON_PATH, request));
    }

    @PostMapping("/markdown-path-filters/policies")
    public ResponseEntity<PolicyRegistrationResponse> registerMarkdownPathFilter(
            @RequestBody PolicyRegistrationRequest request) {
        return ResponseEntity.ok(policyRegistrationService.registerPolicy(
                FilterEnums.FilterKind.MARKDOWN_PATH, request));
    }

    @PostMapping("/regex-path-filters/policies")
    public ResponseEntity<PolicyRegistrationResponse> registerRegexPathFilter(
            @RequestBody PolicyRegistrationRequest request) {
        return ResponseEntity.ok(policyRegistrationService.registerPolicy(
                FilterEnums.FilterKind.REGEX_PATH, request));
    }

    @PostMapping("/ai-path-filters/policies")
    public ResponseEntity<PolicyRegistrationResponse> registerAiPathFilter(
            @RequestBody PolicyRegistrationRequest request) {
        return ResponseEntity.ok(policyRegistrationService.registerPolicy(
                FilterEnums.FilterKind.AI_PATH, request));
    }

    // ── US2: Deactivation & Layer Toggle ─────────────────────────────

    @PostMapping("/policies/{policyId}/deactivate")
    public ResponseEntity<DeactivatePolicyResponse> deactivatePolicy(
            @PathVariable String policyId) {
        return ResponseEntity.ok(policyRegistrationService.deactivatePolicy(policyId));
    }

    @PostMapping("/policies/{policyId}/layers/{layerId}/disable")
    public ResponseEntity<TogglePolicyLayerResponse> disablePolicyAtLayer(
            @PathVariable String policyId,
            @PathVariable String layerId,
            @RequestBody(required = false) TogglePolicyLayerRequest request) {
        boolean includeDescendants = request != null && request.includeDescendants();
        return ResponseEntity.ok(policyRegistrationService.togglePolicyAtLayer(
                policyId, layerId, false, includeDescendants));
    }

    @PostMapping("/policies/{policyId}/layers/{layerId}/enable")
    public ResponseEntity<TogglePolicyLayerResponse> enablePolicyAtLayer(
            @PathVariable String policyId,
            @PathVariable String layerId,
            @RequestBody(required = false) TogglePolicyLayerRequest request) {
        boolean includeDescendants = request != null && request.includeDescendants();
        return ResponseEntity.ok(policyRegistrationService.togglePolicyAtLayer(
                policyId, layerId, true, includeDescendants));
    }

    @PutMapping("/policies/{policyId}/layers")
    public ResponseEntity<PutPolicyLayerResponse> updatePolicyLayerBinding(
            @PathVariable String policyId,
            @RequestBody PutPolicyLayerRequest request) {
        return ResponseEntity.ok(policyRegistrationService.updatePolicyLayerBinding(policyId, request));
    }

    // ── US3: Inspection ──────────────────────────────────────────────

    @GetMapping("/policies/{policyId}/layers/{layerId}/records/recent")
    public ResponseEntity<ReadRecentFilteredRecordsResponse> getRecentFilteredRecords(
            @PathVariable String policyId,
            @PathVariable String layerId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok(filterDecisionQueryService.getRecentRecordsByPolicyAndLayer(
                policyId, layerId, limit, cursor));
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private String extractFilterField(PolicyRegistrationEntity entity, String field) {
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(entity.getFilterJson());
            return node.has(field) ? node.get(field).asText("") : "";
        } catch (Exception e) {
            return "";
        }
    }

    private int extractPriority(PolicyRegistrationEntity entity) {
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(entity.getFilterJson());
            return node.has("priority") ? node.get("priority").asInt(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
