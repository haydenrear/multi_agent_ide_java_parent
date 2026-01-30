package com.hayden.multiagentidelib.template;

import com.hayden.multiagentidelib.agent.AgentContext;
import com.hayden.multiagentidelib.agent.ContextId;

import java.util.List;

public record PlanningTicket(
        String schemaVersion,
        ContextId resultId,
        String ticketId,
        String title,
        String description,
        List<TicketTask> tasks,
        List<String> dependencies,
        List<String> acceptanceCriteria,
        String effortEstimate,
        List<DiscoveryLink> discoveryLinks,
        int priority,
        List<MemoryReference> memoryReferences
) implements AgentContext {

    @Override
    public String prettyPrint() {
        StringBuilder builder = new StringBuilder();
        if (ticketId != null && !ticketId.isBlank()) {
            builder.append(ticketId.trim());
        }
        if (title != null && !title.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append(": ");
            }
            builder.append(title.trim());
        }
        if (description != null && !description.isBlank()) {
            builder.append(" - ").append(description.trim());
        }
        return builder.toString().trim();
    }

    public record TicketTask(
            String taskId,
            String description,
            Double estimatedHours,
            List<String> relatedFiles
    ) {
    }

    public record DiscoveryLink(
            ContextId discoveryResultId,
            String referenceId,
            String linkRationale
    ) {
    }
}
