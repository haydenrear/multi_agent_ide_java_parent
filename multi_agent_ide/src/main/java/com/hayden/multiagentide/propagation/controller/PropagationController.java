package com.hayden.multiagentide.propagation.controller;

import com.hayden.multiagentide.propagation.controller.dto.ReadPropagationItemsResponse;
import com.hayden.multiagentide.propagation.controller.dto.ResolvePropagationItemRequest;
import com.hayden.multiagentide.propagation.controller.dto.ResolvePropagationItemResponse;
import com.hayden.multiagentide.propagation.service.PropagationItemService;
import com.hayden.multiagentidelib.propagation.model.PropagationResolutionType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/propagations/items")
@RequiredArgsConstructor
public class PropagationController {

    private final PropagationItemService propagationItemService;

    @GetMapping
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
    public ResponseEntity<ResolvePropagationItemResponse> resolve(@PathVariable String itemId,
                                                                  @RequestBody ResolvePropagationItemRequest request) {
        PropagationResolutionType resolutionType = PropagationResolutionType.valueOf(request.resolutionType());
        return ResponseEntity.ok(propagationItemService.resolve(itemId, resolutionType, request.resolutionNotes()));
    }
}
