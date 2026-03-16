package com.hayden.multiagentide.propagation.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record ReadPropagatorsByLayerRequest(String layerId) {
}
