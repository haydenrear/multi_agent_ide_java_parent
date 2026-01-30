package com.hayden.multiagentide.tool;

import com.embabel.agent.api.common.ToolObject;
import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupRequirement;
import com.hayden.commitdiffcontext.mcp.ToolCarrier;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.Objects;
import java.util.Set;

/**
 * Sealed wrapper for tool abstractions across frameworks.
 */
public sealed interface ToolAbstraction permits
        ToolAbstraction.SpringToolCallback,
        ToolAbstraction.SpringToolCallbackProvider,
        ToolAbstraction.EmbabelTool,
        ToolAbstraction.EmbabelToolObject,
        ToolAbstraction.EmbabelToolGroup,
        ToolAbstraction.EmbabelToolGroupRequirement,
        ToolAbstraction.ToolGroupStrings {

    record SpringToolCallback(ToolCallback toolCallback) implements ToolAbstraction {
        public SpringToolCallback {
            Objects.requireNonNull(toolCallback, "toolCallback");
        }
    }

    record SpringToolCallbackProvider(ToolCallbackProvider toolCallbackProvider) implements ToolAbstraction {
        public SpringToolCallbackProvider {
            Objects.requireNonNull(toolCallbackProvider, "toolCallbackProvider");
        }
    }

    record EmbabelTool(Tool tool) implements ToolAbstraction {
        public EmbabelTool {
            Objects.requireNonNull(tool, "tool");
        }
    }

    record EmbabelToolObject(ToolObject toolObject) implements ToolAbstraction {
        public EmbabelToolObject {
            Objects.requireNonNull(toolObject, "toolObject");
        }
    }

    record EmbabelToolGroup(ToolGroup toolGroup) implements ToolAbstraction {
        public EmbabelToolGroup {
            Objects.requireNonNull(toolGroup, "toolGroup");
        }
    }

    record EmbabelToolGroupRequirement(ToolGroupRequirement requirement) implements ToolAbstraction {
        public EmbabelToolGroupRequirement {
            Objects.requireNonNull(requirement, "requirement");
        }
    }

    record ToolGroupStrings(Set<String> toolGroups) implements ToolAbstraction {
        public ToolGroupStrings {
            Objects.requireNonNull(toolGroups, "toolGroups");
        }
    }

    static ToolAbstraction fromToolCarrier(ToolCarrier toolCarrier) {
        Objects.requireNonNull(toolCarrier, "toolCarrier");
        return new EmbabelToolObject(new ToolObject(toolCarrier));
    }
}
