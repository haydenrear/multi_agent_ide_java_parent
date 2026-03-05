package com.hayden.acp_cdc_ai.acp.filter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hayden.acp_cdc_ai.acp.filter.path.Path;
import lombok.Builder;

/**
 * Sealed instruction type for path-based mutations.
 * Operations are applied in ascending order.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "op")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Instruction.Replace.class, name = "REPLACE"),
        @JsonSubTypes.Type(value = Instruction.Set.class, name = "SET"),
        @JsonSubTypes.Type(value = Instruction.Remove.class, name = "REMOVE"),
        @JsonSubTypes.Type(value = Instruction.ReplaceIfMatch.class, name = "REPLACE_IF_MATCH"),
        @JsonSubTypes.Type(value = Instruction.RemoveIfMatch.class, name = "REMOVE_IF_MATCH")
})
public sealed interface Instruction
        permits Instruction.Replace, Instruction.Set, Instruction.Remove,
                Instruction.ReplaceIfMatch, Instruction.RemoveIfMatch {

    FilterEnums.InstructionOp op();
    Path targetPath();
    int order();

    @Builder(toBuilder = true)
    record Replace(
            Path targetPath,
            Object value,
            int order
    ) implements Instruction {
        @Override
        public FilterEnums.InstructionOp op() {
            return FilterEnums.InstructionOp.REPLACE;
        }
    }

    @Builder(toBuilder = true)
    record Set(
            Path targetPath,
            Object value,
            int order
    ) implements Instruction {
        @Override
        public FilterEnums.InstructionOp op() {
            return FilterEnums.InstructionOp.SET;
        }
    }

    @Builder(toBuilder = true)
    record Remove(
            Path targetPath,
            int order
    ) implements Instruction {
        @Override
        public FilterEnums.InstructionOp op() {
            return FilterEnums.InstructionOp.REMOVE;
        }
    }

    @Builder(toBuilder = true)
    record ReplaceIfMatch(
            Path targetPath,
            InstructionMatcher matcher,
            Object value,
            int order
    ) implements Instruction {
        @Override
        public FilterEnums.InstructionOp op() {
            return FilterEnums.InstructionOp.REPLACE_IF_MATCH;
        }
    }

    @Builder(toBuilder = true)
    record RemoveIfMatch(
            Path targetPath,
            InstructionMatcher matcher,
            int order
    ) implements Instruction {
        @Override
        public FilterEnums.InstructionOp op() {
            return FilterEnums.InstructionOp.REMOVE_IF_MATCH;
        }
    }
}
