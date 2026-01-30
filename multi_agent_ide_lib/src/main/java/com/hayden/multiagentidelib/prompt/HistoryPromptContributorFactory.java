package com.hayden.multiagentidelib.prompt;

import com.hayden.multiagentidelib.agent.AgentContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class HistoryPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.blackboardHistory() == null || context.request() == null) {
            return List.of();
        }
        AgentModels.InterruptRequest interruptDescriptor = findLastInterrupt(context.blackboardHistory());
        if (interruptDescriptor == null) {
            return List.of();
        }
        String contribution = buildInterruptContribution(context.blackboardHistory(), interruptDescriptor, context.request());
        if (contribution.isBlank()) {
            return List.of();
        }
        AgentType agentType = context.agentType() != null ? context.agentType() : AgentType.ALL;
        return List.of(new SimplePromptContributor(
                "interrupt-history",
                contribution,
                Set.of(agentType),
                30
        ));
    }

    private String buildInterruptContribution(
            BlackboardHistory.History history,
            AgentModels.InterruptRequest interrupt,
            AgentModels.AgentRequest request
    ) {
        if (history == null || interrupt == null) {
            return "";
        }
        List<BlackboardHistory.Entry> entries = history.entries();
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        Object previousRequest = findPreviousNonInterrupt(entries);
        StringBuilder builder = new StringBuilder();
        builder.append(interrupt.prettyPrint(new AgentContext.AgentSerializationCtx.InterruptSerialization()));
        appendSection(builder, "Before Interrupt", formatRequestSummary(previousRequest));
        appendSection(builder, "After Interrupt", formatRequestSummary(request));
        return builder.toString().trim();
    }

    private AgentModels.InterruptRequest findLastInterrupt(BlackboardHistory.History history) {
        if (history == null || history.entries() == null) {
            return null;
        }
        List<BlackboardHistory.Entry> entries = history.entries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            BlackboardHistory.Entry entry = entries.get(i);
            if (entry == null || entry.input() == null) {
                continue;
            }
            if (entry.input() instanceof AgentModels.InterruptRequest interrupt) {
                return interrupt;
            }
        }
        return null;
    }

    private Object findPreviousNonInterrupt(List<BlackboardHistory.Entry> entries) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            BlackboardHistory.Entry entry = entries.get(i);
            if (entry == null || entry.input() == null) {
                continue;
            }
            if (entry.input() instanceof AgentModels.InterruptRequest) {
                continue;
            }
            return entry.input();
        }
        return null;
    }

    private String formatRequestSummary(Object request) {
        if (request == null) {
            return "None";
        }
        if (request instanceof AgentModels.InterruptRequest interrupt) {
            String reason = interrupt.reason();
            return interrupt.type() + (reason != null && !reason.isBlank() ? " - " + reason.trim() : "");
        }
        if (request instanceof AgentModels.AgentRequest agentRequest) {
            return agentRequest.prettyPrint(new AgentContext.AgentSerializationCtx.InterruptSerialization());
        }
        return Objects.toString(request, "");
    }

    private void appendSection(StringBuilder builder, String label, String body) {
        if (label == null || label.isBlank()) {
            return;
        }
        builder.append(label).append(":\n");
        builder.append(body == null || body.isBlank() ? "None" : body.trim());
        builder.append("\n");
    }
}
