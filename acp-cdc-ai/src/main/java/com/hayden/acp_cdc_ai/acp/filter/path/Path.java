package com.hayden.acp_cdc_ai.acp.filter.path;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;

/**
 * Sealed path type for targeting document structure locations.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "pathType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MarkdownPath.class, name = "MARKDOWN_PATH"),
        @JsonSubTypes.Type(value = JsonPath.class, name = "JSON_PATH")
})
public sealed interface Path
        permits MarkdownPath, JsonPath {

    FilterEnums.PathType pathType();
    String expression();
}
