package com.hayden.acp_cdc_ai.acp.filter;

import lombok.Builder;

@Builder(toBuilder = true)
public record InstructionMatcher(FilterEnums.MatcherType matcherType, String value) {
}
