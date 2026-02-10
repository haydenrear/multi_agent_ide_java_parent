package com.hayden.multiagentidelib.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;

import java.util.List;

public interface AgentContext extends Artifact.AgentModel {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    ArtifactKey contextId();

    @Override
    @JsonIgnore
    default ArtifactKey key() {
        return contextId();
    }

    @Override
    @JsonIgnore
    default List<Artifact.AgentModel> children() {
        return List.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    default <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> c) {
        return (T) this;
    }

    default String prettyPrint(AgentSerializationCtx serializationCtx) {
        return switch (serializationCtx) {
            case AgentSerializationCtx.StdReceiverSerialization stdReceiverSerialization ->
                    prettyPrint();
            case AgentSerializationCtx.InterruptSerialization interruptSerialization ->
                    prettyPrintInterruptContinuation();
            case AgentSerializationCtx.GoalResolutionSerialization goalResolutionSerialization ->
                    prettyPrint();
            case AgentSerializationCtx.MergeSummarySerialization mergeSummarySerialization ->
                    prettyPrint();
            case AgentSerializationCtx.ResultsSerialization resultsSerialization ->
                    prettyPrint();
        };
    }

    @JsonIgnore
    default String prettyPrintInterruptContinuation() {
        return prettyPrint();
    }

    @JsonIgnore
    String prettyPrint();

    sealed interface AgentSerializationCtx {

        record StdReceiverSerialization() implements AgentSerializationCtx {}

        record InterruptSerialization() implements AgentSerializationCtx {}

        record GoalResolutionSerialization() implements AgentSerializationCtx {}

        record MergeSummarySerialization() implements AgentSerializationCtx {}

        record ResultsSerialization() implements AgentSerializationCtx {}

    }

}
