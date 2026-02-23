package com.hayden.multiagentidelib.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface AgentPretty {

    @JsonIgnore
    String prettyPrint();

    sealed interface AgentSerializationCtx {

        record StdReceiverSerialization() implements AgentSerializationCtx {
        }

        record InterruptSerialization() implements AgentSerializationCtx {
        }

        record GoalResolutionSerialization() implements AgentSerializationCtx {
        }

        record MergeSummarySerialization() implements AgentSerializationCtx {
        }

        record ResultsSerialization() implements AgentSerializationCtx {
        }

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

}
