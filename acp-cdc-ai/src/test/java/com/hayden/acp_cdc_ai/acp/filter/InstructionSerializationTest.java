package com.hayden.acp_cdc_ai.acp.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.filter.path.MarkdownPath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstructionSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rawObjectListSerialization_dropsInstructionTypeDiscriminator() throws Exception {
        List<Instruction> instructions = List.of(
                new Instruction.RemoveIfMatch(
                        new MarkdownPath("## Active Data Filters"),
                        new InstructionMatcher(FilterEnums.MatcherType.EQUALS, "value"),
                        0
                )
        );

        String json = objectMapper.writeValueAsString((Object) instructions);

        assertFalse(json.contains("\"op\""), json);
        assertTrue(json.contains("\"matcher\""), json);
        assertTrue(json.contains("\"targetPath\""), json);
    }

    @Test
    void typedInstructionListSerialization_preservesInstructionTypeDiscriminatorAndRoundTrips() throws Exception {
        List<Instruction> instructions = List.of(
                new Instruction.RemoveIfMatch(
                        new MarkdownPath("## Active Data Filters"),
                        new InstructionMatcher(FilterEnums.MatcherType.EQUALS, "value"),
                        0
                )
        );

        String json = objectMapper.writerFor(new TypeReference<List<Instruction>>() { })
                .writeValueAsString(instructions);

        assertTrue(json.contains("\"op\":\"REMOVE_IF_MATCH\""), json);

        List<Instruction> deserialized = objectMapper.readValue(
                json,
                new TypeReference<List<Instruction>>() { }
        );

        Instruction.RemoveIfMatch removeIfMatch =
                assertInstanceOf(Instruction.RemoveIfMatch.class, deserialized.getFirst());
        assertEquals("## Active Data Filters", removeIfMatch.targetPath().expression());
        assertEquals("value", removeIfMatch.matcher().value());
        assertEquals(FilterEnums.MatcherType.EQUALS, removeIfMatch.matcher().matcherType());
    }

    @Test
    void instructionJsonHelper_preservesInstructionTypeDiscriminator() {
        List<Instruction> instructions = List.of(
                new Instruction.RemoveIfMatch(
                        new MarkdownPath("## Active Data Filters"),
                        new InstructionMatcher(FilterEnums.MatcherType.EQUALS, "value"),
                        0
                )
        );

        String json = InstructionJson.toJsonSafely(objectMapper, instructions);

        assertTrue(json.contains("\"op\":\"REMOVE_IF_MATCH\""), json);
    }
}
