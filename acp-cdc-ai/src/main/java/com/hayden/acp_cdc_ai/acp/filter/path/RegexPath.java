package com.hayden.acp_cdc_ai.acp.filter.path;

import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import lombok.Builder;

@Builder(toBuilder = true)
public record RegexPath(
        String expression
) implements Path {

    @Override
    public FilterEnums.PathType pathType() {
        return FilterEnums.PathType.REGEX;
    }
}
