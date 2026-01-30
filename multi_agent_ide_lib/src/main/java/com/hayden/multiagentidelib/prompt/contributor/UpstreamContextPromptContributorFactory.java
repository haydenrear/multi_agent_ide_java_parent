package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.UpstreamContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import com.hayden.multiagentidelib.prompt.SimplePromptContributor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class UpstreamContextPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null) {
            return List.of();
        }
        List<PromptContributor> contributors = new ArrayList<>();
        AgentType agentType = context.agentType() != null ? context.agentType() : AgentType.ALL;
        addUpstreamContributors(contributors, agentType, context.upstreamContexts());

        return List.copyOf(contributors);
    }

    private void addUpstreamContributors(
            List<PromptContributor> contributors,
            AgentType agentType,
            List<UpstreamContext> upstreamContexts
    ) {
        if (upstreamContexts == null || upstreamContexts.isEmpty()) {
            return;
        }
        for (UpstreamContext upstream : upstreamContexts) {
            if (upstream == null) {
                continue;
            }
            switch (upstream) {
                case UpstreamContext.DiscoveryCollectorContext discovery -> addContributor(
                        contributors,
                        agentType,
                        "discovery-curation",
                        "Discovery Context:\n" + discovery.prettyPrint(),
                        20
                );
                case UpstreamContext.PlanningCollectorContext planning -> {
                    addContributor(
                            contributors,
                            agentType,
                            "planning-curation",
                            "Planning Context:\n" + planning.prettyPrint(),
                            20
                    );
                    addContributor(
                            contributors,
                            agentType,
                            "tickets",
                            "Tickets:\n" + planning.prettyPrintTickets(),
                            21
                    );
                }
                case UpstreamContext.TicketCollectorContext ticket -> addContributor(
                        contributors,
                        agentType,
                        "ticket-curation",
                        "Ticket Context:\n" + ticket.prettyPrint(),
                        20
                );
                default -> {
                }
            }
        }
    }

    private void addContributor(
            List<PromptContributor> contributors,
            AgentType agentType,
            String name,
            String content,
            int priority
    ) {
        if (content == null || content.isBlank()) {
            return;
        }
        contributors.add(new SimplePromptContributor(name, content, Set.of(agentType), priority));
    }
}
