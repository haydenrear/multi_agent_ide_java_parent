package com.hayden.multiagentide.transformation.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record ReadTransformersByLayerRequest(String layerId) {
}
