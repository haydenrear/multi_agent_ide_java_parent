package com.hayden.multiagentidelib.propagation.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentidelib.filter.model.executor.ExecutableTool;
import com.hayden.multiagentidelib.filter.model.layer.FilterContext;

import java.time.Instant;
import java.util.function.BiFunction;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "propagatorType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextPropagator.class, name = "TEXT"),
        @JsonSubTypes.Type(value = AiTextPropagator.class, name = "AI_TEXT")
})
public sealed interface Propagator<I, O, CTX extends FilterContext> extends BiFunction<I, CTX, O>
        permits TextPropagator, AiTextPropagator {

    String id();
    String name();
    String description();
    String sourcePath();
    ExecutableTool<?, ?, ?> executor();
    FilterEnums.PolicyStatus status();
    int priority();
    Instant createdAt();
    Instant updatedAt();

    Propagator<I, O, CTX> withStatus(FilterEnums.PolicyStatus status);
}
