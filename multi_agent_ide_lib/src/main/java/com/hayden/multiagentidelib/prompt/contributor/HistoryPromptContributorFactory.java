package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.*;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import com.hayden.multiagentidelib.prompt.SimplePromptContributor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class HistoryPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.blackboardHistory() == null || context.currentRequest() == null) {
            return List.of();
        }
        if (context.currentRequest() instanceof AgentModels.OrchestratorRequest r
                && context.blackboardHistory().fromHistory(h -> h.entries().getFirst().input() == r || h.entries().isEmpty())) {
            return List.of(
//                  put the prompt contributor for the first orchestrator request
                    new FirstOrchestratorRequestPromptContributor(r)
            );
        }

        if (context.currentRequest() instanceof AgentModels.InterruptRequest)
            return new ArrayList<>();

        AgentModels.InterruptRequest interruptDescriptor = findLastInterrupt(context.blackboardHistory());

        if (interruptDescriptor == null) {
            return List.of();
        }
        String contribution = buildInterruptContribution(context.blackboardHistory(), interruptDescriptor, context.currentRequest());
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

    public record FirstOrchestratorRequestPromptContributor(AgentModels.OrchestratorRequest orchestratorRequest) implements PromptContributor {

        private static final String TEMPLATE = """
                ## First Orchestrator Request

                This is the first orchestrator request in a new workflow (no prior history).
                Your job is to start discovery.

                **Required routing for this step**
                - Return an `OrchestratorRouting` with `orchestratorRequest` set to a `DiscoveryOrchestratorRequest`, as the first step will be to perform discovery to achieve our goal.
                - Do NOT return a new `OrchestratorRequest` or route back to the Orchestrator.
                - Do NOT route to `collectorRequest` or `contextManagerRequest` at this stage.

                **Discovery request guidance**
                - Set `goal` to a rich, detailed statement of the user's intent.
                - Include scope, constraints, desired outcomes, and explicit preferences.
                - If a phase was provided, incorporate it into the goal text.
                - Do not set `contextId`.

                Current orchestrator request summary:
                """;

        @Override
        public String name() {
            return "first-orchestrator-request";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            String summary = orchestratorRequest != null
                    ? orchestratorRequest.prettyPrintInterruptContinuation()
                    : "Goal: (none)";
            if (summary == null || summary.isBlank()) {
                summary = "Goal: (none)";
            }
            return TEMPLATE + summary.trim();
        }

        @Override
        public String template() {
            return TEMPLATE;
        }

        @Override
        public int priority() {
            return 0;
        }
    }

    private String buildInterruptContribution(
            BlackboardHistory history,
            AgentModels.InterruptRequest interrupt,
            AgentModels.AgentRequest request
    ) {
        if (history == null || interrupt == null) {
            return "";
        }
        List<BlackboardHistory.Entry> entries = history.copyOfEntries();
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        Object previousRequest = findPreviousNonInterrupt(entries);
        StringBuilder builder = new StringBuilder();
        builder.append(interrupt.prettyPrint(new AgentPretty.AgentSerializationCtx.InterruptSerialization()));
        appendSection(builder, "Before Interrupt", formatRequestSummary(previousRequest));
        appendSection(builder, "After Interrupt", formatRequestSummary(request));
        appendSection(builder, "Instructions", """
                Now that we have routed to an interrupt and been routed back with the desired information, please continue the process to the next agent,
                as desired by the standard workflow.
                """);
        return builder.toString().trim();
    }

    private AgentModels.InterruptRequest findLastInterrupt(BlackboardHistory history) {
        if (history == null)
            return null;

        var e = history.copyOfEntries();

        if (!CollectionUtils.isEmpty(e)) {
            return null;
        }

        List<BlackboardHistory.Entry> entries = e;

        if (entries.size() == 1)
            return null;

        if (entries.get(entries.size() - 2) == null) {
            return null;
        }

        if (!(entries.get(entries.size() - 2).input() instanceof AgentModels.InterruptRequest)) {
            return null;
        }

        return (AgentModels.InterruptRequest) entries.get(entries.size() - 2).input();
    }

    private Object findPreviousNonInterrupt(List<BlackboardHistory.Entry> entries) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            BlackboardHistory.Entry entry = entries.get(i);
            Object input = entryInput(entry);
            if (input == null) {
                continue;
            }
            if (input instanceof AgentModels.InterruptRequest) {
                continue;
            }
            return input;
        }
        return null;
    }

    private Object entryInput(BlackboardHistory.Entry entry) {
        if (entry == null) {
            return null;
        }
        return switch (entry) {
            case BlackboardHistory.DefaultEntry defaultEntry -> defaultEntry.input();
            case BlackboardHistory.MessageEntry messageEntry -> null;
        };
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
            return agentRequest.prettyPrint(new AgentPretty.AgentSerializationCtx.InterruptSerialization());
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
