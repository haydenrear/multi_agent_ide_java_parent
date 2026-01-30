package com.hayden.multiagentide.tool;

import com.hayden.utilitymodule.stream.StreamUtil;
import lombok.Builder;

import java.util.List;
import java.util.Objects;

/**
 * Tool container for LLM runs.
 */
@Builder(toBuilder = true)
public record ToolContext(List<ToolAbstraction> tools) {

    public ToolContext {
        Objects.requireNonNull(tools, "tools");
        tools = List.copyOf(tools);
    }

    public static ToolContext empty() {
        return new ToolContext(List.of());
    }

    public static ToolContext of(ToolAbstraction... tools) {
        return new ToolContext(StreamUtil.toStream(tools).toList());
    }
}
