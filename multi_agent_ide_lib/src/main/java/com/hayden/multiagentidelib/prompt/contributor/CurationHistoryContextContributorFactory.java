package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.agent.AgentContext;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.UpstreamContext;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import com.hayden.acp_cdc_ai.acp.events.Events;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.*;

@Component
public class CurationHistoryContextContributorFactory implements PromptContributorFactory {

    private static final int BASE_PRIORITY = -1100;

    static final String DISCOVERY_CURATION_HEADER = """
            ## Discovery Curation
            The consolidated curation from the discovery collector, summarizing code analysis,
            architecture findings, and recommendations from discovery agents.
            """;

    static final String DISCOVERY_AGENT_REPORT_HEADER = """
            ## Discovery Agent Report
            Individual report from a discovery agent with detailed code findings, file references,
            and subdomain analysis.
            """;

    static final String PLANNING_CURATION_HEADER = """
            ## Planning Curation
            The consolidated curation from the planning collector, including finalized tickets,
            dependency graphs, and planning summaries.
            """;

    static final String PLANNING_AGENT_RESULT_HEADER = """
            ## Planning Agent Result
            Individual result from a planning agent with proposed tickets, architecture decisions,
            and implementation strategies.
            """;

    static final String TICKET_CURATION_HEADER = """
            ## Ticket Curation
            The consolidated curation from the ticket collector, including completion status,
            follow-up items, and execution summaries.
            """;

    static final String TICKET_AGENT_RESULT_HEADER = """
            ## Ticket Agent Result
            Individual result from a ticket execution agent with implementation summary,
            files modified, test results, and verification status.
            """;

    // --- Phase classification for binder insertion ---

    private enum Phase {
        DISCOVERY_CURATION,
        DISCOVERY_AGENT,
        PLANNING_CURATION,
        PLANNING_AGENT,
        TICKET_CURATION,
        TICKET_AGENT,
        INTERRUPT,
        OTHER
    }

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        var bh = context.blackboardHistory();
        if (bh == null || CollectionUtils.isEmpty(bh.copyOfEntries())) {
            return List.of();
        }

        // Step 1: Determine which types are allowed for the current request,
        // and collect any curation overrides from the request itself.
        Set<Class<?>> allowedTypes = new HashSet<>();
        Map<Class<?>, Object> curationOverrides = new HashMap<>();
        populateAllowedTypes(context.currentRequest(), allowedTypes, curationOverrides, bh);

        if (allowedTypes.isEmpty()) {
            return List.of();
        }

        // Step 2: Walk history chronologically to build temporally-ordered contributors.
        List<BlackboardHistory.Entry> entries = bh.copyOfEntries();
        List<PromptContributor> contributors = new ArrayList<>();
        int seq = 0;
        Phase lastPhase = null;
        int interruptIndex = 0;

        // Track which curation overrides have been emitted (so we emit them at the right
        // temporal position instead of the history entry).
        Set<Class<?>> emittedOverrides = new HashSet<>();

        // Track which history-based curations we've emitted so we don't duplicate
        // when the override was already used.
        Set<Class<?>> emittedCurationTypes = new HashSet<>();

        for (BlackboardHistory.Entry entry : entries) {
            if (!(entry instanceof BlackboardHistory.DefaultEntry de)) {
                continue;
            }
            Object input = de.input();
            if (input == null) {
                continue;
            }
            Class<?> inputType = de.inputType();

            // --- Collector results: extract the curation context from them ---
            if (AgentModels.DiscoveryCollectorResult.class.equals(inputType)
                    && allowedTypes.contains(AgentModels.DiscoveryCollectorResult.class)) {
                var result = (AgentModels.DiscoveryCollectorResult) input;
                var curation = result.discoveryCollectorContext();
                if (curation != null && !emittedCurationTypes.contains(UpstreamContext.DiscoveryCollectorContext.class)) {
                    // Use override if available, otherwise history
                    var toEmit = curationOverrides.containsKey(UpstreamContext.DiscoveryCollectorContext.class)
                            ? (UpstreamContext.DiscoveryCollectorContext) curationOverrides.get(UpstreamContext.DiscoveryCollectorContext.class)
                            : curation;
                    Phase newPhase = Phase.DISCOVERY_CURATION;
                    String binder = binderText(lastPhase, newPhase);
                    if (binder != null) {
                        contributors.add(new NarrativeBinderContributor(
                                "binder-%s-to-%s".formatted(lastPhase, newPhase).toLowerCase(),
                                binder, BASE_PRIORITY + seq++));
                    }
                    contributors.add(new DataCurationContributor("curation-discovery-curation", DISCOVERY_CURATION_HEADER, toEmit, BASE_PRIORITY + seq++));
                    lastPhase = newPhase;
                    emittedCurationTypes.add(UpstreamContext.DiscoveryCollectorContext.class);
                    emittedOverrides.add(UpstreamContext.DiscoveryCollectorContext.class);
                }
                continue;
            }

            if (AgentModels.PlanningCollectorResult.class.equals(inputType)
                    && allowedTypes.contains(AgentModels.PlanningCollectorResult.class)) {
                var result = (AgentModels.PlanningCollectorResult) input;
                var curation = result.planningCuration();
                if (curation != null && !emittedCurationTypes.contains(UpstreamContext.PlanningCollectorContext.class)) {
                    var toEmit = curationOverrides.containsKey(UpstreamContext.PlanningCollectorContext.class)
                            ? (UpstreamContext.PlanningCollectorContext) curationOverrides.get(UpstreamContext.PlanningCollectorContext.class)
                            : curation;
                    Phase newPhase = Phase.PLANNING_CURATION;
                    String binder = binderText(lastPhase, newPhase);
                    if (binder != null) {
                        contributors.add(new NarrativeBinderContributor(
                                "binder-%s-to-%s".formatted(lastPhase, newPhase).toLowerCase(),
                                binder, BASE_PRIORITY + seq++));
                    }
                    contributors.add(new DataCurationContributor("curation-planning-curation", PLANNING_CURATION_HEADER, toEmit, BASE_PRIORITY + seq++));
                    lastPhase = newPhase;
                    emittedCurationTypes.add(UpstreamContext.PlanningCollectorContext.class);
                    emittedOverrides.add(UpstreamContext.PlanningCollectorContext.class);
                }
                continue;
            }

            if (AgentModels.TicketCollectorResult.class.equals(inputType)
                    && allowedTypes.contains(AgentModels.TicketCollectorResult.class)) {
                var result = (AgentModels.TicketCollectorResult) input;
                var curation = result.ticketCuration();
                if (curation != null && !emittedCurationTypes.contains(UpstreamContext.TicketCollectorContext.class)) {
                    var toEmit = curationOverrides.containsKey(UpstreamContext.TicketCollectorContext.class)
                            ? (UpstreamContext.TicketCollectorContext) curationOverrides.get(UpstreamContext.TicketCollectorContext.class)
                            : curation;
                    Phase newPhase = Phase.TICKET_CURATION;
                    String binder = binderText(lastPhase, newPhase);
                    if (binder != null) {
                        contributors.add(new NarrativeBinderContributor(
                                "binder-%s-to-%s".formatted(lastPhase, newPhase).toLowerCase(),
                                binder, BASE_PRIORITY + seq++));
                    }
                    contributors.add(new DataCurationContributor("curation-ticket-curation", TICKET_CURATION_HEADER, toEmit, BASE_PRIORITY + seq++));
                    lastPhase = newPhase;
                    emittedCurationTypes.add(UpstreamContext.TicketCollectorContext.class);
                    emittedOverrides.add(UpstreamContext.TicketCollectorContext.class);
                }
                continue;
            }

            // --- Agent results ---
            if (AgentModels.DiscoveryAgentResult.class.equals(inputType)
                    && allowedTypes.contains(AgentModels.DiscoveryAgentResult.class)) {
                var result = (AgentModels.DiscoveryAgentResult) input;
                if (result.report() != null) {
                    Phase newPhase = Phase.DISCOVERY_AGENT;
                    String binder = binderText(lastPhase, newPhase);
                    if (binder != null) {
                        contributors.add(new NarrativeBinderContributor(
                                "binder-%s-to-%s-%d".formatted(lastPhase, newPhase, seq).toLowerCase(),
                                binder, BASE_PRIORITY + seq++));
                    }
                    contributors.add(new DataCurationContributor("curation-discovery-report", DISCOVERY_AGENT_REPORT_HEADER, result, BASE_PRIORITY + seq++));
                    lastPhase = newPhase;
                }
                continue;
            }

            if (AgentModels.PlanningAgentResult.class.equals(inputType)
                    && allowedTypes.contains(AgentModels.PlanningAgentResult.class)) {
                var result = (AgentModels.PlanningAgentResult) input;
                Phase newPhase = Phase.PLANNING_AGENT;
                String binder = binderText(lastPhase, newPhase);
                if (binder != null) {
                    contributors.add(new NarrativeBinderContributor(
                            "binder-%s-to-%s-%d".formatted(lastPhase, newPhase, seq).toLowerCase(),
                            binder, BASE_PRIORITY + seq++));
                }
                contributors.add(new DataCurationContributor("curation-planning-result", PLANNING_AGENT_RESULT_HEADER, result, BASE_PRIORITY + seq++));
                lastPhase = newPhase;
                continue;
            }

            if (AgentModels.TicketAgentResult.class.equals(inputType)
                    && allowedTypes.contains(AgentModels.TicketAgentResult.class)) {
                var result = (AgentModels.TicketAgentResult) input;
                Phase newPhase = Phase.TICKET_AGENT;
                String binder = binderText(lastPhase, newPhase);
                if (binder != null) {
                    contributors.add(new NarrativeBinderContributor(
                            "binder-%s-to-%s-%d".formatted(lastPhase, newPhase, seq).toLowerCase(),
                            binder, BASE_PRIORITY + seq++));
                }
                contributors.add(new DataCurationContributor("curation-ticket-result", TICKET_AGENT_RESULT_HEADER, result, BASE_PRIORITY + seq++));
                lastPhase = newPhase;
                continue;
            }

            // --- Interrupt requests: pair with the next resolution ---
            if (AgentModels.InterruptRequest.class.isAssignableFrom(inputType)
                    && allowedTypes.contains(AgentModels.InterruptRequest.class)) {
                var interrupt = (AgentModels.InterruptRequest) input;
                String resolution = findResolutionAfter(entries, entries.indexOf(entry));
                Phase newPhase = Phase.INTERRUPT;
                String binder = binderText(lastPhase, newPhase);
                if (binder != null) {
                    contributors.add(new NarrativeBinderContributor(
                            "binder-%s-to-interrupt-%d".formatted(lastPhase, seq).toLowerCase(),
                            binder, BASE_PRIORITY + seq++));
                }
                interruptIndex++;
                contributors.add(new InterruptResolutionContributor(
                        new InterruptResolutionEntry(interrupt, de.timestamp(), resolution, null),
                        interruptIndex, BASE_PRIORITY + seq++));
                lastPhase = newPhase;
            }
        }

        // Emit any curation overrides that weren't found in history (e.g., passed on the request
        // but no corresponding collector result in history yet).
        for (var override : curationOverrides.entrySet()) {
            if (emittedOverrides.contains(override.getKey())) {
                continue;
            }
            if (override.getKey().equals(UpstreamContext.DiscoveryCollectorContext.class)
                    && override.getValue() instanceof UpstreamContext.DiscoveryCollectorContext dc) {
                Phase newPhase = Phase.DISCOVERY_CURATION;
                String binder = binderText(lastPhase, newPhase);
                if (binder != null) {
                    contributors.add(new NarrativeBinderContributor(
                            "binder-%s-to-%s".formatted(lastPhase, newPhase).toLowerCase(),
                            binder, BASE_PRIORITY + seq++));
                }
                contributors.add(new DataCurationContributor("curation-discovery-curation", DISCOVERY_CURATION_HEADER, dc, BASE_PRIORITY + seq++));
                lastPhase = newPhase;
            } else if (override.getKey().equals(UpstreamContext.PlanningCollectorContext.class)
                    && override.getValue() instanceof UpstreamContext.PlanningCollectorContext pc) {
                Phase newPhase = Phase.PLANNING_CURATION;
                String binder = binderText(lastPhase, newPhase);
                if (binder != null) {
                    contributors.add(new NarrativeBinderContributor(
                            "binder-%s-to-%s".formatted(lastPhase, newPhase).toLowerCase(),
                            binder, BASE_PRIORITY + seq++));
                }
                contributors.add(new DataCurationContributor("curation-planning-curation", PLANNING_CURATION_HEADER, pc, BASE_PRIORITY + seq++));
                lastPhase = newPhase;
            } else if (override.getKey().equals(UpstreamContext.TicketCollectorContext.class)
                    && override.getValue() instanceof UpstreamContext.TicketCollectorContext tc) {
                Phase newPhase = Phase.TICKET_CURATION;
                String binder = binderText(lastPhase, newPhase);
                if (binder != null) {
                    contributors.add(new NarrativeBinderContributor(
                            "binder-%s-to-%s".formatted(lastPhase, newPhase).toLowerCase(),
                            binder, BASE_PRIORITY + seq++));
                }
                contributors.add(new DataCurationContributor("curation-ticket-curation", TICKET_CURATION_HEADER, tc, BASE_PRIORITY + seq++));
                lastPhase = newPhase;
            }
        }

        if (contributors.isEmpty()) {
            return List.of();
        }

        // Prepend instructions preamble
        contributors.addFirst(new CurationWorkflowInstructionsContributor(BASE_PRIORITY - 1));

        return contributors;
    }

    // -----------------------------------------------------------------------
    // Allowed-types population (preserves the original switch semantics)
    // -----------------------------------------------------------------------

    private void populateAllowedTypes(AgentModels.AgentRequest request,
                                      Set<Class<?>> allowed,
                                      Map<Class<?>, Object> overrides,
                                      BlackboardHistory bh) {
        switch (request) {
            case AgentModels.DiscoveryOrchestratorRequest ignored ->
                    addAllTypes(allowed);
            case AgentModels.DiscoveryAgentRequest ignored ->
                    addAllTypes(allowed);
            case AgentModels.DiscoveryAgentRequests ignored ->
                    addAllTypes(allowed);

            case AgentModels.DiscoveryCollectorRequest ignored -> {
                allowed.add(AgentModels.DiscoveryAgentResult.class);
                allowed.add(AgentModels.PlanningCollectorResult.class);
                allowed.add(AgentModels.PlanningAgentResult.class);
                allowed.add(AgentModels.TicketCollectorResult.class);
                allowed.add(AgentModels.TicketAgentResult.class);
                allowed.add(AgentModels.InterruptRequest.class);
            }

            case AgentModels.PlanningOrchestratorRequest req -> {
                addAllTypes(allowed);
                if (req.discoveryCuration() != null)
                    overrides.put(UpstreamContext.DiscoveryCollectorContext.class, req.discoveryCuration());
            }

            case AgentModels.PlanningAgentRequest req -> {
                addAllTypes(allowed);
                if (req.discoveryCuration() != null)
                    overrides.put(UpstreamContext.DiscoveryCollectorContext.class, req.discoveryCuration());
            }

            case AgentModels.PlanningAgentRequests ignored ->
                    addAllTypes(allowed);

            case AgentModels.PlanningCollectorRequest req -> {
                allowed.add(AgentModels.DiscoveryCollectorResult.class);
                allowed.add(AgentModels.DiscoveryAgentResult.class);
                allowed.add(AgentModels.PlanningAgentResult.class);
                allowed.add(AgentModels.TicketCollectorResult.class);
                allowed.add(AgentModels.TicketAgentResult.class);
                allowed.add(AgentModels.InterruptRequest.class);
                if (req.discoveryCuration() != null)
                    overrides.put(UpstreamContext.DiscoveryCollectorContext.class, req.discoveryCuration());
            }

            case AgentModels.TicketOrchestratorRequest req -> {
                addAllTypes(allowed);
                if (req.discoveryCuration() != null)
                    overrides.put(UpstreamContext.DiscoveryCollectorContext.class, req.discoveryCuration());
                if (req.planningCuration() != null)
                    overrides.put(UpstreamContext.PlanningCollectorContext.class, req.planningCuration());
            }

            case AgentModels.TicketAgentRequest req -> {
                addAllTypes(allowed);
                if (req.discoveryCuration() != null)
                    overrides.put(UpstreamContext.DiscoveryCollectorContext.class, req.discoveryCuration());
                if (req.planningCuration() != null)
                    overrides.put(UpstreamContext.PlanningCollectorContext.class, req.planningCuration());
            }

            case AgentModels.TicketAgentRequests ignored ->
                    addAllTypes(allowed);

            case AgentModels.TicketCollectorRequest req -> {
                addAllTypes(allowed);
                if (req.discoveryCuration() != null)
                    overrides.put(UpstreamContext.DiscoveryCollectorContext.class, req.discoveryCuration());
                if (req.planningCuration() != null)
                    overrides.put(UpstreamContext.PlanningCollectorContext.class, req.planningCuration());
            }

            case AgentModels.OrchestratorCollectorRequest req -> {
                addAllTypes(allowed);
                if (req.discoveryCuration() != null)
                    overrides.put(UpstreamContext.DiscoveryCollectorContext.class, req.discoveryCuration());
                if (req.planningCuration() != null)
                    overrides.put(UpstreamContext.PlanningCollectorContext.class, req.planningCuration());
                if (req.ticketCuration() != null)
                    overrides.put(UpstreamContext.TicketCollectorContext.class, req.ticketCuration());
            }

            case AgentModels.OrchestratorRequest req -> {
                addAllTypes(allowed);
                if (req.discoveryCuration() != null)
                    overrides.put(UpstreamContext.DiscoveryCollectorContext.class, req.discoveryCuration());
                if (req.planningCuration() != null)
                    overrides.put(UpstreamContext.PlanningCollectorContext.class, req.planningCuration());
                if (req.ticketCuration() != null)
                    overrides.put(UpstreamContext.TicketCollectorContext.class, req.ticketCuration());
            }

            case AgentModels.DiscoveryAgentResults ignored -> {
                allowed.add(AgentModels.DiscoveryAgentResult.class);
                allowed.add(AgentModels.InterruptRequest.class);
            }

            case AgentModels.PlanningAgentResults ignored -> {
                allowed.add(AgentModels.DiscoveryCollectorResult.class);
                allowed.add(AgentModels.DiscoveryAgentResult.class);
                allowed.add(AgentModels.DiscoveryAgentResults.class);
                allowed.add(AgentModels.PlanningAgentResult.class);
                allowed.add(AgentModels.InterruptRequest.class);
            }

            case AgentModels.TicketAgentResults ignored -> {
                allowed.add(AgentModels.DiscoveryCollectorResult.class);
                allowed.add(AgentModels.DiscoveryAgentResult.class);
                allowed.add(AgentModels.DiscoveryAgentResults.class);
                allowed.add(AgentModels.PlanningCollectorResult.class);
                allowed.add(AgentModels.PlanningAgentResult.class);
                allowed.add(AgentModels.TicketAgentResult.class);
                allowed.add(AgentModels.InterruptRequest.class);
            }

            case AgentModels.ContextManagerRequest ignored ->
                    addAllTypes(allowed);
            case AgentModels.ContextManagerRoutingRequest ignored ->
                    addAllTypes(allowed);
            case AgentModels.ReviewRequest ignored ->
                    addAllTypes(allowed);
            case AgentModels.MergerRequest ignored ->
                    addAllTypes(allowed);
            case AgentModels.InterruptRequest ignored ->
                    addAllTypes(allowed);
        }
    }

    private void addAllTypes(Set<Class<?>> allowed) {
        allowed.add(AgentModels.DiscoveryCollectorResult.class);
        allowed.add(AgentModels.DiscoveryAgentResult.class);
        allowed.add(AgentModels.PlanningCollectorResult.class);
        allowed.add(AgentModels.PlanningAgentResult.class);
        allowed.add(AgentModels.TicketCollectorResult.class);
        allowed.add(AgentModels.TicketAgentResult.class);
        allowed.add(AgentModels.InterruptRequest.class);
    }

    // -----------------------------------------------------------------------
    // Resolution pairing for interrupts
    // -----------------------------------------------------------------------

    private String findResolutionAfter(List<BlackboardHistory.Entry> entries, int startIndex) {
        for (int i = startIndex + 1; i < entries.size(); i++) {
            if (!(entries.get(i) instanceof BlackboardHistory.DefaultEntry de)) {
                continue;
            }
            if (de.input() instanceof Events.ResolveInterruptEvent re) {
                return re.toAddMessage();
            }
            if (de.input() instanceof AgentModels.ReviewAgentResult rar) {
                return rar.output();
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Binder text based on phase transitions
    // -----------------------------------------------------------------------

    private static String binderText(Phase from, Phase to) {
        if (from == null) {
            return null;
        }
        if (from == to) {
            return null;
        }

        // Interrupt transitions
        if (to == Phase.INTERRUPT) {
            return "At this point in the workflow, clarification or review was needed:";
        }
        if (from == Phase.INTERRUPT) {
            return "With the feedback incorporated, the workflow continued:";
        }

        // Discovery → Discovery agent reports
        if (from == Phase.DISCOVERY_CURATION && to == Phase.DISCOVERY_AGENT) {
            return """
                    Following the discovery curation above, individual discovery agents each produced \
                    detailed reports with their specific findings. These reports informed the consolidated \
                    curation and provide additional granular detail:""";
        }

        // Discovery → Planning
        if ((from == Phase.DISCOVERY_CURATION || from == Phase.DISCOVERY_AGENT) &&
                (to == Phase.PLANNING_CURATION || to == Phase.PLANNING_AGENT)) {
            return """
                    With discovery complete, the workflow advanced to the planning phase. The planning \
                    process used the discovery findings above to formulate implementation strategies, \
                    tickets, and dependency graphs:""";
        }

        // Planning → Planning agent results
        if (from == Phase.PLANNING_CURATION && to == Phase.PLANNING_AGENT) {
            return """
                    The following individual planning agent results contain the detailed tickets, \
                    architecture decisions, and implementation strategies that were synthesized into \
                    the planning curation above:""";
        }

        // Planning → Ticket
        if ((from == Phase.PLANNING_CURATION || from == Phase.PLANNING_AGENT) &&
                (to == Phase.TICKET_CURATION || to == Phase.TICKET_AGENT)) {
            return """
                    After planning was finalized, ticket execution began. Ticket agents worked on \
                    implementing the planned changes, running tests, and verifying results:""";
        }

        // Ticket curation → Ticket agent results
        if (from == Phase.TICKET_CURATION && to == Phase.TICKET_AGENT) {
            return """
                    The individual ticket execution results below detail what each ticket agent \
                    accomplished, including files modified, test outcomes, and verification status:""";
        }

        // Re-run: going back to an earlier phase
        if ((from == Phase.TICKET_CURATION || from == Phase.TICKET_AGENT) &&
                (to == Phase.DISCOVERY_CURATION || to == Phase.DISCOVERY_AGENT)) {
            return """
                    The workflow looped back to re-run discovery with the accumulated context from \
                    the previous iteration:""";
        }

        if ((from == Phase.TICKET_CURATION || from == Phase.TICKET_AGENT) &&
                (to == Phase.PLANNING_CURATION || to == Phase.PLANNING_AGENT)) {
            return """
                    The workflow looped back to re-run planning with the accumulated context from \
                    ticket execution:""";
        }

        if ((from == Phase.PLANNING_CURATION || from == Phase.PLANNING_AGENT) &&
                (to == Phase.DISCOVERY_CURATION || to == Phase.DISCOVERY_AGENT)) {
            return """
                    The workflow looped back to re-run discovery with the accumulated context from \
                    the previous planning iteration:""";
        }

        // Generic fallback for other transitions
        return "The workflow then progressed to the next phase:";
    }

    // -----------------------------------------------------------------------
    // Inner records: data holders
    // -----------------------------------------------------------------------

    record InterruptResolutionEntry(
            AgentModels.InterruptRequest request,
            Instant requestedAt,
            String resolution,
            Instant resolvedAt
    ) {
    }

    // -----------------------------------------------------------------------
    // Inner records: prompt contributors
    // -----------------------------------------------------------------------

    record NarrativeBinderContributor(
            String binderName,
            String narrative,
            int binderPriority
    ) implements PromptContributor {
        @Override
        public String name() {
            return binderName;
        }

        @Override
        public boolean include(PromptContext ctx) {
            return true;
        }

        @Override
        public String contribute(PromptContext ctx) {
            return narrative;
        }

        @Override
        public String template() {
            return narrative;
        }

        @Override
        public int priority() {
            return binderPriority;
        }
    }

    record CurationWorkflowInstructionsContributor(
            int contributorPriority) implements PromptContributor {

        private static final String TEMPLATE = """
                --- Curated Workflow Context ---
                
                The following context has been curated from prior workflow phases. This represents
                the accumulated knowledge from discovery, planning, and/or ticket execution that
                has been completed so far. Use this context to inform your current task. Each section
                is labeled by its source phase and type, presented in the order they occurred.
                """;

        @Override
        public String name() {
            return "curation-workflow-instructions";
        }

        @Override
        public boolean include(PromptContext ctx) {
            return true;
        }

        @Override
        public String contribute(PromptContext ctx) {
            return TEMPLATE;
        }

        @Override
        public String template() {
            return TEMPLATE;
        }

        @Override
        public int priority() {
            return contributorPriority;
        }
    }

    record DataCurationContributor(
            String contributorName,
            String header,
            AgentContext data,
            int contributorPriority
    ) implements PromptContributor {

        @Override
        public String name() {
            return contributorName;
        }

        @Override
        public boolean include(PromptContext ctx) {
            return true;
        }

        @Override
        public String contribute(PromptContext ctx) {
            String rendered = data != null ? data.prettyPrint() : null;
            return header + (rendered != null && !rendered.isBlank() ? rendered.trim() : "(none)");
        }

        @Override
        public String template() {
            return header;
        }

        @Override
        public int priority() {
            return contributorPriority;
        }
    }

    record InterruptResolutionContributor(InterruptResolutionEntry entry,
                                          int index,
                                          int contributorPriority) implements PromptContributor {

        @Override
        public String name() {
            return "curation-interrupt-resolution";
        }

        @Override
        public boolean include(PromptContext ctx) {
            return true;
        }

        @Override
        public String contribute(PromptContext ctx) {
            var req = entry.request();
            StringBuilder sb = new StringBuilder();
            sb.append("### Interrupt %d - %s".formatted(index, req.type()));
            if (entry.requestedAt() != null) {
                sb.append(" (at %s)".formatted(entry.requestedAt()));
            }
            sb.append("\n");

            if (req.reason() != null && !req.reason().isBlank()) {
                sb.append("We asked: \"%s\"\n".formatted(req.reason().trim()));
            }

            if (req.contextForDecision() != null && !req.contextForDecision().isBlank()) {
                sb.append("Context for decision: %s\n".formatted(req.contextForDecision().trim()));
            }

            if (req.choices() != null && !req.choices().isEmpty()) {
                sb.append("Options presented:");
                for (var choice : req.choices()) {
                    if (choice.options() != null) {
                        for (var opt : choice.options().entrySet()) {
                            sb.append(" (%s) %s".formatted(opt.getKey(), opt.getValue()));
                        }
                    }
                }
                sb.append("\n");
            }

            if (entry.resolution() != null && !entry.resolution().isBlank()) {
                String responder = req.type() == Events.InterruptType.AGENT_REVIEW
                        ? "The agent" : "The user";
                sb.append("%s responded: \"%s\"\n".formatted(responder, entry.resolution().trim()));
            }

            return sb.toString().trim();
        }

        @Override
        public String template() {
            return "### Interrupt %d".formatted(index);
        }

        @Override
        public int priority() {
            return contributorPriority;
        }
    }

}
