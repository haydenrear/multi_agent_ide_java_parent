package com.hayden.multiagentide.filter.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record ReadPoliciesByLayerRequest(
        String layerId,
        String status
) {
    public String statusOrDefault() {
        return status == null || status.isBlank() ? "ACTIVE" : status;
    }
}
