package com.hayden.multiagentidelib.filter.model;

import com.hayden.acp_cdc_ai.acp.filter.Instruction;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.filter.model.executor.AiFilterTool;
import com.hayden.multiagentidelib.filter.model.interpreter.Interpreter;
import com.hayden.multiagentidelib.filter.model.layer.FilterContext;
import com.hayden.multiagentidelib.filter.service.FilterDescriptor;
import com.hayden.multiagentidelib.filter.service.FilterResult;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Path-instruction filtering over partial document/object structure.
 * All serialization (including instruction deserialization) is Jackson/JSON.
 */
@Builder(toBuilder = true)
public record AiPathFilter(
        String id,
        String name,
        String description,
        String sourcePath,
        AiFilterTool<AgentModels.AiFilterRequest, AgentModels.AiFilterResult> executor,
        com.hayden.acp_cdc_ai.acp.filter.FilterEnums.PolicyStatus status,
        int priority,
        Interpreter interpreter,
        com.hayden.acp_cdc_ai.acp.filter.FilterEnums.InstructionLanguage instructionLanguage,
        Instant createdAt,
        Instant updatedAt
) implements Filter<AgentModels.AiFilterRequest, AiPathFilter.AiPathFilterResult, FilterContext.AiFilterContext> {

    @Builder(toBuilder = true)
    public record AiPathFilterResult(String r, AgentModels.AiFilterResult res, FilterDescriptor descriptor) {
        public List<Instruction> instructions() {
            return res.output();
        }
    }

    @Override
    public AiPathFilterResult apply(AgentModels.AiFilterRequest s, FilterContext.AiFilterContext ctx) {
        var executionResult = executor.apply(s, ctx);


        FilterDescriptor descriptor = executionResult.descriptor() == null
                ? new FilterDescriptor.NoOpFilterDescriptor()
                : executionResult.descriptor();

        var r = interpreter.apply(s.input(), executionResult.t().output());

        if (r.isOk())
            return new AiPathFilterResult(r.unwrap(), executionResult.t(), descriptor);

        return new AiPathFilterResult(s.input(), executionResult.t(), descriptor);
    }

}
