package com.hayden.multiagentidelib.transformation.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentidelib.filter.model.executor.ExecutableTool;
import com.hayden.multiagentidelib.filter.model.layer.FilterContext;

import java.time.Instant;
import java.util.function.BiFunction;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "transformerType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextTransformer.class, name = "TEXT"),
        @JsonSubTypes.Type(value = AiTextTransformer.class, name = "AI_TEXT")
})
public sealed interface Transformer<I, O, CTX extends FilterContext> extends BiFunction<I, CTX, O>
        permits TextTransformer, AiTextTransformer {

    String id();
    String name();
    String description();
    String sourcePath();
    ExecutableTool<?, ?, ?> executor();
    FilterEnums.PolicyStatus status();
    int priority();
    boolean replaceEndpointResponse();
    Instant createdAt();
    Instant updatedAt();
}
