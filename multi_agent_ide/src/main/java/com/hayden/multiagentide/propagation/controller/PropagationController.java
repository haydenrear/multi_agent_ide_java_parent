package com.hayden.multiagentide.propagation.controller;

import jakarta.validation.Valid;
import com.hayden.multiagentide.propagation.controller.dto.ReadPropagationItemsResponse;
import com.hayden.multiagentide.propagation.controller.dto.ResolvePropagationItemRequest;
import com.hayden.multiagentide.propagation.controller.dto.ResolvePropagationItemResponse;
import com.hayden.multiagentide.propagation.service.PropagationItemService;
import com.hayden.multiagentidelib.propagation.model.PropagationResolutionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/propagations/items")
@RequiredArgsConstructor
@Tag(name = "Propagation Items", description = "List and resolve pending propagation items")
public class PropagationController {

    private final PropagationItemService propagationItemService;

    @GetMapping
    @Operation(summary = "List all pending propagation items")
    public ResponseEntity<ReadPropagationItemsResponse> items() {
        var items = propagationItemService.findPendingItems().stream()
                .map(item -> ReadPropagationItemsResponse.ItemSummary.builder()
                        .itemId(item.getItemId())
                        .registrationId(item.getRegistrationId())
                        .layerId(item.getLayerId())
                        .sourceNodeId(item.getSourceNodeId())
                        .sourceName(item.getSourceName())
                        .summaryText(item.getSummaryText())
                        .mode(item.getMode())
                        .status(item.getStatus())
                        .createdAt(item.getCreatedAt())
                        .resolvedAt(item.getResolvedAt())
                        .build())
                .toList();
        return ResponseEntity.ok(ReadPropagationItemsResponse.builder().items(items).totalCount(items.size()).build());
    }

    @PostMapping("/{itemId}/resolve")
    @Operation(summary = "Resolve a propagation item by ID")
    public ResponseEntity<ResolvePropagationItemResponse> resolve(@PathVariable String itemId,
                                                                  @RequestBody @Valid ResolvePropagationItemRequest request) {
        PropagationResolutionType resolutionType = PropagationResolutionType.valueOf(request.resolutionType());
        return ResponseEntity.ok(propagationItemService.resolve(itemId, resolutionType, request.resolutionNotes()));
    }
}
