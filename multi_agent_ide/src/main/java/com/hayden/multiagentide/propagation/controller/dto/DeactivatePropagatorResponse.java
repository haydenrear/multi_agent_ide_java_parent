package com.hayden.multiagentide.propagation.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record DeactivatePropagatorResponse(boolean ok, String registrationId, String status, String message) {
}
