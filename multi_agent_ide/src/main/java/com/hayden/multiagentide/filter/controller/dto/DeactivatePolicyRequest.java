package com.hayden.multiagentide.filter.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record DeactivatePolicyRequest(
        String policyId
) {
}
