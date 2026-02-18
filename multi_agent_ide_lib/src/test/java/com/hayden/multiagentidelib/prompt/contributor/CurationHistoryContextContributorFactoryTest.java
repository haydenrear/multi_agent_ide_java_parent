package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.UpstreamContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.template.ConsolidationTemplate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CurationHistoryContextContributorFactoryTest {

    private final CurationHistoryContextContributorFactory factory = new CurationHistoryContextContributorFactory();

    @Test
    void createReturnsEmptyWhenContextRequestOrHistoryIsMissing() {
        assertThat(factory.create(null)).isEmpty();

        var noRequest = PromptContext.builder()
                .blackboardHistory(historyOf())
                .build();
        assertThat(factory.create(noRequest)).isEmpty();

        var noHistory = PromptContext.builder()
                .currentRequest(new AgentModels.OrchestratorRequest("goal", "DISCOVERY"))
                .build();
        assertThat(factory.create(noHistory)).isEmpty();

        var emptyHistory = promptContext(
                new AgentModels.OrchestratorRequest("goal", "DISCOVERY"),
                historyOf());
        assertThat(factory.create(emptyHistory)).isEmpty();
    }

    @Test
    void createBuildsChronologicalWorkflowContextWithBinders() {
        var context = promptContext(
                new AgentModels.OrchestratorRequest("goal", "DISCOVERY"),
                historyOf(
                        discoveryCollectorResult(discoveryContext("history-discovery"), "discovery collector output"),
                        new AgentModels.DiscoveryAgentResult("discovery report output"),
                        planningCollectorResult(planningContext("history-planning"), "planning collector output"),
                        new AgentModels.PlanningAgentResult("planning result output"),
                        ticketCollectorResult(ticketContext("history-ticket"), "ticket collector output"),
                        new AgentModels.TicketAgentResult("ticket result output")
                ));

        var contributors = factory.create(context);
        var rendered = render(contributors, context);

        assertThat(contributors).isNotEmpty();
        assertThat(contributors.getFirst().name()).isEqualTo("curation-workflow-instructions");

        assertThat(rendered).contains(CurationHistoryContextContributorFactory.DISCOVERY_CURATION_HEADER.trim());
        assertThat(rendered).contains(CurationHistoryContextContributorFactory.DISCOVERY_AGENT_REPORT_HEADER.trim());
        assertThat(rendered).contains(CurationHistoryContextContributorFactory.PLANNING_CURATION_HEADER.trim());
        assertThat(rendered).contains(CurationHistoryContextContributorFactory.PLANNING_AGENT_RESULT_HEADER.trim());
        assertThat(rendered).contains(CurationHistoryContextContributorFactory.TICKET_CURATION_HEADER.trim());
        assertThat(rendered).contains(CurationHistoryContextContributorFactory.TICKET_AGENT_RESULT_HEADER.trim());

        assertThat(rendered).contains("With discovery complete, the workflow advanced to the planning phase.");
        assertThat(rendered).contains("After planning was finalized, ticket execution began.");
        assertThat(indexOf(rendered, "## Discovery Curation"))
                .isLessThan(indexOf(rendered, "## Planning Curation"));
        assertThat(indexOf(rendered, "## Planning Curation"))
                .isLessThan(indexOf(rendered, "## Ticket Curation"));
    }

    @Test
    void createUsesRequestCurationOverridesAndAvoidsDuplicateCurationSections() {
        var historyDiscovery = discoveryContext("history-discovery");
        var historyPlanning = planningContext("history-planning");
        var historyTicket = ticketContext("history-ticket");

        var overrideDiscovery = discoveryContext("override-discovery");
        var overridePlanning = planningContext("override-planning");
        var overrideTicket = ticketContext("override-ticket");

        var request = AgentModels.OrchestratorRequest.builder()
                .goal("goal")
                .phase("DISCOVERY")
                .discoveryCuration(overrideDiscovery)
                .planningCuration(overridePlanning)
                .ticketCuration(overrideTicket)
                .build();

        var context = promptContext(
                request,
                historyOf(
                        discoveryCollectorResult(historyDiscovery, "discovery collector output"),
                        planningCollectorResult(historyPlanning, "planning collector output"),
                        ticketCollectorResult(historyTicket, "ticket collector output")
                ));

        var contributors = factory.create(context);
        var rendered = render(contributors, context);

        assertThat(rendered).contains("selection-override-discovery");
        assertThat(rendered).contains("selection-override-planning");
        assertThat(rendered).contains("selection-override-ticket");

        assertThat(rendered).doesNotContain("selection-history-discovery");
        assertThat(rendered).doesNotContain("selection-history-planning");
        assertThat(rendered).doesNotContain("selection-history-ticket");

        assertThat(countOccurrences(rendered, "## Discovery Curation")).isEqualTo(1);
        assertThat(countOccurrences(rendered, "## Planning Curation")).isEqualTo(1);
        assertThat(countOccurrences(rendered, "## Ticket Curation")).isEqualTo(1);
    }

    @Test
    void createEmitsOverrideWhenMatchingCollectorResultIsMissing() {
        var request = AgentModels.TicketOrchestratorRequest.builder()
                .goal("goal")
                .planningCuration(planningContext("override-only-planning"))
                .build();

        var context = promptContext(
                request,
                historyOf(new AgentModels.DiscoveryAgentResult("only discovery report")));

        var contributors = factory.create(context);
        var rendered = render(contributors, context);

        assertThat(contributors).isNotEmpty();
        assertThat(rendered).contains("## Planning Curation");
        assertThat(rendered).contains("selection-override-only-planning");
    }

    @Test
    void createPairsInterruptWithResolutionAndAddsInterruptNarrativeBinders() {
        var interrupt = AgentModels.InterruptRequest.OrchestratorInterruptRequest.builder()
                .type(Events.InterruptType.HUMAN_REVIEW)
                .reason("Need a decision")
                .contextForDecision("Important context")
                .choices(List.of(
                        AgentModels.InterruptRequest.StructuredChoice.builder()
                                .choiceId("choose-path")
                                .question("Choose a path")
                                .options(Map.of("A", "Option A"))
                                .recommended("A")
                                .build()))
                .build();

        var resolution = new Events.ResolveInterruptEvent(
                "event-id",
                Instant.parse("2025-01-01T00:00:00Z"),
                "node-1",
                "interrupt-1",
                "Option A",
                Events.InterruptType.HUMAN_REVIEW
        );

        var context = promptContext(
                new AgentModels.OrchestratorRequest("goal", "DISCOVERY"),
                historyOf(
                        new AgentModels.DiscoveryAgentResult("before interrupt"),
                        interrupt,
                        resolution,
                        new AgentModels.PlanningAgentResult("after interrupt")
                ));

        var rendered = render(factory.create(context), context);

        assertThat(rendered).contains("### Interrupt 1 - HUMAN_REVIEW");
        assertThat(rendered).contains("We asked: \"Need a decision\"");
        assertThat(rendered).contains("Context for decision: Important context");
        assertThat(rendered).contains("Options presented: (A) Option A");
        assertThat(rendered).contains("The user responded: \"Option A\"");
        assertThat(rendered).contains("At this point in the workflow, clarification or review was needed:");
        assertThat(rendered).contains("With the feedback incorporated, the workflow continued:");
    }

    @Test
    void createIncludesAllSupplementaryResultTypeSections() {
        var collectorDecision = new AgentModels.CollectorDecision(
                Events.CollectorDecisionType.ADVANCE_PHASE,
                "ok",
                "PLANNING"
        );

        var context = promptContext(
                new AgentModels.OrchestratorRequest("goal", "DISCOVERY"),
                historyOf(
                        new AgentModels.DiscoveryOrchestratorResult("discovery orchestrator output"),
                        new AgentModels.PlanningOrchestratorResult("planning orchestrator output"),
                        new AgentModels.TicketOrchestratorResult("ticket orchestrator output"),
                        new AgentModels.OrchestratorAgentResult("orchestrator agent output"),
                        new AgentModels.OrchestratorCollectorResult("orchestrator collector output", collectorDecision),
                        new AgentModels.ReviewAgentResult("review output"),
                        new AgentModels.MergerAgentResult("merger output")
                ));

        var rendered = render(factory.create(context), context);

        assertThat(rendered).contains(CurationHistoryContextContributorFactory.DISCOVERY_ORCHESTRATOR_RESULT_HEADER.trim());
        assertThat(rendered).contains(CurationHistoryContextContributorFactory.PLANNING_ORCHESTRATOR_RESULT_HEADER.trim());
        assertThat(rendered).contains(CurationHistoryContextContributorFactory.TICKET_ORCHESTRATOR_RESULT_HEADER.trim());
        assertThat(rendered).contains(CurationHistoryContextContributorFactory.ORCHESTRATOR_AGENT_RESULT_HEADER.trim());
        assertThat(rendered).contains(CurationHistoryContextContributorFactory.ORCHESTRATOR_COLLECTOR_RESULT_HEADER.trim());
        assertThat(rendered).contains(CurationHistoryContextContributorFactory.REVIEW_AGENT_RESULT_HEADER.trim());
        assertThat(rendered).contains(CurationHistoryContextContributorFactory.MERGER_AGENT_RESULT_HEADER.trim());
    }

    @Test
    void createSkipsNonRenderableAgentResultSections() {
        var context = promptContext(
                new AgentModels.OrchestratorRequest("goal", "DISCOVERY"),
                historyOf(
                        new AgentModels.DiscoveryOrchestratorResult(" "),
                        new AgentModels.PlanningOrchestratorResult(" "),
                        new AgentModels.TicketOrchestratorResult(" "),
                        new AgentModels.OrchestratorAgentResult(" "),
                        new AgentModels.ReviewAgentResult((String) null),
                        new AgentModels.MergerAgentResult((String) null)
                ));

        var contributors = factory.create(context);

        assertThat(contributors).isEmpty();
    }

    @Test
    void createRespectsAllowedTypesForDiscoveryCollectorRequest() {
        var context = promptContext(
                new AgentModels.DiscoveryCollectorRequest("goal", "results"),
                historyOf(
                        discoveryCollectorResult(discoveryContext("not-allowed"), "collector should be skipped"),
                        new AgentModels.PlanningAgentResult("planning allowed")
                ));

        var rendered = render(factory.create(context), context);

        assertThat(rendered).doesNotContain("## Discovery Curation");
        assertThat(rendered).contains("## Planning Agent Result");
    }

    @Test
    void createWeavesRequestContextChronologicallyWithResults() {
        var context = promptContext(
                new AgentModels.OrchestratorCollectorRequest("final goal", "COMPLETE"),
                historyOf(
                        new AgentModels.OrchestratorRequest("initial goal", "DISCOVERY"),
                        new AgentModels.DiscoveryOrchestratorRequest("discover goal"),
                        discoveryCollectorResult(discoveryContext("d1"), "discovery output")
                ));

        var rendered = render(factory.create(context), context);

        assertThat(rendered).contains("## Request Context");
        assertThat(rendered).contains("Action: action-0");
        assertThat(rendered).contains("Now, you are in this phase: DISCOVERY");
        assertThat(rendered).contains("Goal extraction: initial goal");
        assertThat(rendered).contains("Action: action-1");
        assertThat(rendered).contains("Now, you are in this phase: DISCOVERY_ORCHESTRATOR");
        assertThat(rendered).contains("Goal extraction: discover goal");
        assertThat(indexOf(rendered, "Action: action-1"))
                .isLessThan(indexOf(rendered, "## Discovery Curation"));
    }

    private static PromptContext promptContext(AgentModels.AgentRequest request, BlackboardHistory history) {
        return PromptContext.builder()
                .currentRequest(request)
                .blackboardHistory(history)
                .build();
    }

    private static BlackboardHistory historyOf(Object... inputs) {
        List<BlackboardHistory.Entry> entries = new ArrayList<>();
        Instant base = Instant.parse("2025-01-01T00:00:00Z");
        for (int i = 0; i < inputs.length; i++) {
            Object input = inputs[i];
            entries.add(new BlackboardHistory.DefaultEntry(
                    base.plusSeconds(i),
                    "action-" + i,
                    input,
                    input != null ? input.getClass() : null
            ));
        }
        return new BlackboardHistory(
                new BlackboardHistory.History(entries),
                "node-1",
                null
        );
    }

    private static String render(List<PromptContributor> contributors, PromptContext context) {
        StringBuilder builder = new StringBuilder();
        for (PromptContributor contributor : contributors) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(contributor.contribute(context));
        }
        return builder.toString();
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static int indexOf(String text, String token) {
        return text.indexOf(token);
    }

    private static UpstreamContext.DiscoveryCollectorContext discoveryContext(String suffix) {
        return new UpstreamContext.DiscoveryCollectorContext(
                ArtifactKey.createRoot(),
                new AgentModels.DiscoveryCuration(
                        ArtifactKey.createRoot(),
                        List.of(),
                        null,
                        List.of(),
                        Map.of(),
                        List.of(),
                        new ConsolidationTemplate.ConsolidationSummary("discovery summary " + suffix, Map.of())
                ),
                "selection-" + suffix
        );
    }

    private static UpstreamContext.PlanningCollectorContext planningContext(String suffix) {
        return new UpstreamContext.PlanningCollectorContext(
                ArtifactKey.createRoot(),
                new AgentModels.PlanningCuration(
                        ArtifactKey.createRoot(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        new ConsolidationTemplate.ConsolidationSummary("planning summary " + suffix, Map.of())
                ),
                "selection-" + suffix
        );
    }

    private static UpstreamContext.TicketCollectorContext ticketContext(String suffix) {
        return new UpstreamContext.TicketCollectorContext(
                ArtifactKey.createRoot(),
                new AgentModels.TicketCuration(
                        ArtifactKey.createRoot(),
                        List.of(),
                        "complete-" + suffix,
                        List.of(),
                        List.of(),
                        new ConsolidationTemplate.ConsolidationSummary("ticket summary " + suffix, Map.of())
                ),
                "selection-" + suffix
        );
    }

    private static AgentModels.DiscoveryCollectorResult discoveryCollectorResult(
            UpstreamContext.DiscoveryCollectorContext context,
            String consolidatedOutput
    ) {
        return new AgentModels.DiscoveryCollectorResult(
                ArtifactKey.createRoot(),
                consolidatedOutput,
                new AgentModels.CollectorDecision(
                        Events.CollectorDecisionType.ADVANCE_PHASE,
                        "discovery complete",
                        "PLANNING"),
                Map.of(),
                null,
                List.of(),
                Map.of(),
                context
        );
    }

    private static AgentModels.PlanningCollectorResult planningCollectorResult(
            UpstreamContext.PlanningCollectorContext context,
            String consolidatedOutput
    ) {
        return new AgentModels.PlanningCollectorResult(
                ArtifactKey.createRoot(),
                consolidatedOutput,
                new AgentModels.CollectorDecision(
                        Events.CollectorDecisionType.ADVANCE_PHASE,
                        "planning complete",
                        "TICKETS"),
                Map.of(),
                List.of(),
                List.of(),
                context
        );
    }

    private static AgentModels.TicketCollectorResult ticketCollectorResult(
            UpstreamContext.TicketCollectorContext context,
            String consolidatedOutput
    ) {
        return new AgentModels.TicketCollectorResult(
                ArtifactKey.createRoot(),
                consolidatedOutput,
                new AgentModels.CollectorDecision(
                        Events.CollectorDecisionType.ADVANCE_PHASE,
                        "tickets complete",
                        "COMPLETE"),
                Map.of(),
                "complete",
                List.of(),
                context
        );
    }
}
