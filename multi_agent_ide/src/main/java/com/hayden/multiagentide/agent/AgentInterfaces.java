package com.hayden.multiagentide.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.EmbabelComponent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.StuckHandler;
import com.embabel.agent.api.common.StuckHandlerResult;
import com.embabel.agent.api.common.StuckHandlingResultCode;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.InjectedType;
import com.embabel.agent.core.Operation;
import com.hayden.multiagentide.agent.decorator.prompt.PromptContextDecorator;
import com.hayden.multiagentide.agent.decorator.prompt.ToolContextDecorator;
import com.hayden.multiagentide.agent.decorator.request.DispatchedAgentRequestDecorator;
import com.hayden.multiagentide.agent.decorator.request.RequestDecorator;
import com.hayden.multiagentide.agent.decorator.request.ResultsRequestDecorator;
import com.hayden.multiagentide.agent.decorator.result.DispatchedAgentResultDecorator;
import com.hayden.multiagentide.agent.decorator.result.FinalResultDecorator;
import com.hayden.multiagentide.agent.decorator.result.ResultDecorator;
import com.hayden.multiagentidelib.events.DegenerateLoopException;
import com.hayden.multiagentide.service.InterruptService;
import com.hayden.multiagentide.tool.ToolAbstraction;
import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.multiagentidelib.service.RequestEnrichment;
import com.hayden.multiagentidelib.agent.AgentModels;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.agent.BlackboardHistory;
import com.hayden.multiagentidelib.agent.ContextManagerTools;
import com.hayden.multiagentidelib.agent.AgentContext;
import com.hayden.multiagentidelib.agent.UpstreamContext;
import com.hayden.multiagentidelib.agent.BlackboardHistoryService;
import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContextFactory;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentidelib.model.nodes.*;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.function.Predicate;

/**
 * Embabel @Agent definition for multi-agent IDE.
 * Single agent with all workflow actions.
 */
public interface AgentInterfaces {

    String WORKFLOW_DISCOVERY_DISPATCH_SUBAGENT = "WorkflowDiscoveryDispatchSubagent";
    String WORKFLOW_PLANNING_DISPATCH_SUBAGENT = "WorkflowPlanningDispatchSubagent";
    String WORKFLOW_TICKET_DISPATCH_SUBAGENT = "WorkflowTicketDispatchSubagent";
    String WORKFLOW_CONTEXT_DISPATCH_SUBAGENT = "WorkflowContextDispatchSubagent";

    String AGENT_NAME_NONE = "";
    String AGENT_NAME_TICKET_ROUTING = "ticket-routing";
    String AGENT_NAME_PLANNING_AGENT_DISPATCH = "planning-agent-dispatch";

    String ACTION_NONE = "";
    String ACTION_CONTEXT_MANAGER_STUCK = "context-manager-stuck";
    String ACTION_CONTEXT_MANAGER = "context-manager";
    String ACTION_CONTEXT_MANAGER_INTERRUPT = "context-manager-interrupt";
    String ACTION_CONTEXT_MANAGER_ROUTE = "context-manager-route";
    String ACTION_ORCHESTRATOR_COLLECTOR = "orchestrator-collector";
    String ACTION_DISCOVERY_COLLECTOR = "discovery-collector";
    String ACTION_PLANNING_COLLECTOR = "planning-collector";
    String ACTION_TICKET_COLLECTOR = "ticket-collector";
    String ACTION_ORCHESTRATOR = "orchestrator";
    String ACTION_ORCHESTRATOR_INTERRUPT = "orchestrator-interrupt";
    String ACTION_DISCOVERY_ORCHESTRATOR = "discovery-orchestrator";
    String ACTION_DISCOVERY_DISPATCH = "discovery-dispatch";
    String ACTION_DISCOVERY_INTERRUPT = "discovery-interrupt";
    String ACTION_PLANNING_ORCHESTRATOR = "planning-orchestrator";
    String ACTION_PLANNING_DISPATCH = "planning-dispatch";
    String ACTION_PLANNING_AGENT_POST_DISPATCH = "planning-agent-post-dispatch";
    String ACTION_PLANNING_INTERRUPT = "planning-interrupt";
    String ACTION_TICKET_ORCHESTRATOR = "ticket-orchestrator";
    String ACTION_TICKET_DISPATCH = "ticket-dispatch";
    String ACTION_TICKET_INTERRUPT = "ticket-interrupt";
    String ACTION_MERGER_AGENT = "merger-agent";
    String ACTION_MERGER_INTERRUPT = "merger-interrupt";
    String ACTION_REVIEW_AGENT = "review-agent";
    String ACTION_REVIEW_INTERRUPT = "review-interrupt";
    String ACTION_TICKET_COLLECTOR_ROUTING_BRANCH = "ticket-collector-routing-branch";
    String ACTION_TICKET_AGENT_INTERRUPT = "ticket-agent-interrupt";
    String ACTION_TICKET_AGENT = "ticket-agent";
    String ACTION_PLANNING_AGENT_INTERRUPT = "planning-agent-interrupt";
    String ACTION_PLANNING_AGENT = "planning-agent";
    String ACTION_DISCOVERY_AGENT_INTERRUPT = "discovery-agent-interrupt";
    String ACTION_DISCOVERY_AGENT = "discovery-agent";

    String METHOD_NONE = "";
    String METHOD_HANDLE_STUCK = "handleStuck";
    String METHOD_HANDLE_CONTEXT_MANAGER_INTERRUPT = "handleContextManagerInterrupt";
    String METHOD_ROUTE_TO_CONTEXT_MANAGER = "routeToContextManager";
    String METHOD_CONTEXT_MANAGER = "contextManagerRequest";
    String METHOD_FINAL_COLLECTOR_RESULT = "finalCollectorResult";
    String METHOD_CONSOLIDATE_WORKFLOW_OUTPUTS = "consolidateWorkflowOutputs";
    String METHOD_CONSOLIDATE_DISCOVERY_FINDINGS = "consolidateDiscoveryFindings";
    String METHOD_CONSOLIDATE_PLANS_INTO_TICKETS = "consolidatePlansIntoTickets";
    String METHOD_CONSOLIDATE_TICKET_RESULTS = "consolidateTicketResults";
    String METHOD_COORDINATE_WORKFLOW = "coordinateWorkflow";
    String METHOD_HANDLE_ORCHESTRATOR_INTERRUPT = "handleOrchestratorInterrupt";
    String METHOD_KICK_OFF_ANY_NUMBER_OF_AGENTS_FOR_CODE_SEARCH = "kickOffAnyNumberOfAgentsForCodeSearch";
    String METHOD_DISPATCH_DISCOVERY_AGENT_REQUESTS = "dispatchDiscoveryAgentRequests";
    String METHOD_HANDLE_DISCOVERY_INTERRUPT = "handleDiscoveryInterrupt";
    String METHOD_DECOMPOSE_PLAN_AND_CREATE_WORK_ITEMS = "decomposePlanAndCreateWorkItems";
    String METHOD_DISPATCH_PLANNING_AGENT_REQUESTS = "dispatchPlanningAgentRequests";
    String METHOD_HANDLE_PLANNING_INTERRUPT = "handlePlanningInterrupt";
    String METHOD_FINALIZE_TICKET_ORCHESTRATOR = "finalizeTicketOrchestrator";
    String METHOD_ORCHESTRATE_TICKET_EXECUTION = "orchestrateTicketExecution";
    String METHOD_DISPATCH_TICKET_AGENT_REQUESTS = "dispatchTicketAgentRequests";
    String METHOD_HANDLE_TICKET_INTERRUPT = "handleTicketInterrupt";
    String METHOD_PERFORM_MERGE = "performMerge";
    String METHOD_PERFORM_REVIEW = "performReview";
    String METHOD_HANDLE_REVIEW_INTERRUPT = "handleReviewInterrupt";
    String METHOD_HANDLE_TICKET_COLLECTOR_BRANCH = "handleTicketCollectorBranch";
    String METHOD_HANDLE_DISCOVERY_COLLECTOR_BRANCH = "handleDiscoveryCollectorBranch";
    String METHOD_HANDLE_ORCHESTRATOR_COLLECTOR_BRANCH = "handleOrchestratorCollectorBranch";
    String METHOD_HANDLE_PLANNING_COLLECTOR_BRANCH = "handlePlanningCollectorBranch";
    String METHOD_HANDLE_MERGER_INTERRUPT = "handleMergerInterrupt";
    String METHOD_TRANSITION_TO_INTERRUPT_STATE = "transitionToInterruptState";
    String METHOD_RUN_TICKET_AGENT = "runTicketAgent";
    String METHOD_RUN_PLANNING_AGENT = "runPlanningAgent";
    String METHOD_RUN_DISCOVERY_AGENT = "runDiscoveryAgent";
    String METHOD_RAN_TICKET_AGENT_RESULT = "ranTicketAgentResult";
    String METHOD_RAN_PLANNING_AGENT = "ranPlanningAgent";
    String METHOD_RAN_DISCOVERY_AGENT = "ranDiscoveryAgent";

    String STUCK_HANDLER = "stuck-handler";
    String TEMPLATE_WORKFLOW_CONTEXT_MANAGER = "workflow/context_manager";
    String TEMPLATE_WORKFLOW_CONTEXT_MANAGER_INTERRUPT = "workflow/context_manager_interrupt";
    String TEMPLATE_WORKFLOW_ORCHESTRATOR_COLLECTOR = "workflow/orchestrator_collector";
    String TEMPLATE_WORKFLOW_DISCOVERY_COLLECTOR = "workflow/discovery_collector";
    String TEMPLATE_WORKFLOW_PLANNING_COLLECTOR = "workflow/planning_collector";
    String TEMPLATE_WORKFLOW_TICKET_COLLECTOR = "workflow/ticket_collector";
    String TEMPLATE_WORKFLOW_ORCHESTRATOR = "workflow/orchestrator";
    String TEMPLATE_WORKFLOW_DISCOVERY_ORCHESTRATOR = "workflow/discovery_orchestrator";
    String TEMPLATE_WORKFLOW_DISCOVERY_DISPATCH = "workflow/discovery_dispatch";
    String TEMPLATE_WORKFLOW_PLANNING_ORCHESTRATOR = "workflow/planning_orchestrator";
    String TEMPLATE_WORKFLOW_PLANNING_DISPATCH = "workflow/planning_dispatch";
    String TEMPLATE_WORKFLOW_TICKET_ORCHESTRATOR = "workflow/ticket_orchestrator";
    String TEMPLATE_WORKFLOW_TICKET_DISPATCH = "workflow/ticket_dispatch";
    String TEMPLATE_WORKFLOW_MERGER = "workflow/merger";
    String TEMPLATE_WORKFLOW_REVIEW = "workflow/review";
    String TEMPLATE_WORKFLOW_TICKET_AGENT = "workflow/ticket_agent";
    String TEMPLATE_WORKFLOW_PLANNING_AGENT = "workflow/planning_agent";
    String TEMPLATE_WORKFLOW_DISCOVERY_AGENT = "workflow/discovery_agent";
    String UNKNOWN_VALUE = "unknown";
    String RETURN_ROUTE_NONE = "none";

    String multiAgentAgentName();

    String WORKFLOW_AGENT_NAME = WorkflowAgent.class.getName();

    AgentInterfaces WORKFLOW_AGENT = () -> WORKFLOW_AGENT_NAME;
    AgentInterfaces ORCHESTRATOR_AGENT = WORKFLOW_AGENT;
    AgentInterfaces DISCOVERY_ORCHESTRATOR_AGENT = WORKFLOW_AGENT;
    AgentInterfaces PLANNING_ORCHESTRATOR_AGENT = WORKFLOW_AGENT;
    AgentInterfaces TICKET_ORCHESTRATOR_AGENT = WORKFLOW_AGENT;
    AgentInterfaces REVIEW_AGENT = WORKFLOW_AGENT;
    AgentInterfaces MERGER_AGENT = WORKFLOW_AGENT;
    AgentInterfaces CONTEXT_ORCHESTRATOR_AGENT = WORKFLOW_AGENT;


    @EmbabelComponent(scan = false)
    @RequiredArgsConstructor
    @Slf4j
    class WorkflowAgent implements AgentInterfaces, StuckHandler {

        private final WorkflowGraphService workflowGraphService;
        private final InterruptService interruptService;
        private final PromptContextFactory promptContextFactory;
        private final com.hayden.multiagentide.service.LlmRunner llmRunner;
        private final ContextManagerTools contextManagerTools;
        private final BlackboardHistoryService blackboardHistoryService;

        @Autowired(required = false)
        private List<ResultDecorator> resultDecorators = new ArrayList<>();

        @Autowired(required = false)
        private List<RequestDecorator> requestDecorators = new ArrayList<>();

        @Autowired(required = false)
        private List<ResultsRequestDecorator> resultsRequestDecorators = new ArrayList<>();

        @Autowired(required = false)
        private List<FinalResultDecorator> finalResultDecorators = new ArrayList<>();

        @Autowired(required = false)
        private List<PromptContextDecorator> promptContextDecorators = new ArrayList<>();

        @Autowired(required = false)
        private List<ToolContextDecorator> toolContextDecorators = new ArrayList<>();

        @Override
        public String multiAgentAgentName() {
            return WORKFLOW_AGENT_NAME;
        }

        private static final int MAX_STUCK_HANDLER_INVOCATIONS = 3;

        @Override
        public StuckHandlerResult handleStuck(AgentProcess agentProcess) {
            OperationContext context = buildStuckHandlerContext(agentProcess);
            BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);

            if (history == null)
                return new StuckHandlerResult(
                        "Stuck handler called with no blackboard history. Systemic failure!",
                        this,
                        StuckHandlingResultCode.NO_RESOLUTION,
                        agentProcess
                );

            // Guard against infinite stuck handler re-entry.
            var lastSize = history.fromHistory(s -> {
                List<AgentModels.ContextManagerRequest> cmr = s.getLast(AgentModels.ContextManagerRequest.class);
                if (cmr.size() >= MAX_STUCK_HANDLER_INVOCATIONS
                        && cmr.stream().allMatch(c -> STUCK_HANDLER.equals(c.reason()))) {
                    return cmr.size();
                }

                return -1;
            });

            if (lastSize != -1)
                return new StuckHandlerResult(
                        "Stuck handler exhausted after " + lastSize + " attempts",
                        this,
                        StuckHandlingResultCode.NO_RESOLUTION,
                        agentProcess
                );

            String loopSummary = Optional.of(history)
                    .map(BlackboardHistory::summary)
                    .filter(StringUtils::isNotBlank)
                    .orElse("No history available");

            AgentModels.AgentRequest lastRequest = findLastRequest(
                    history,
                    a -> !(a instanceof AgentModels.InterruptRequest)
                            && !(a instanceof AgentModels.ContextManagerRequest)
                            && !(a instanceof AgentModels.ContextManagerRoutingRequest));

            AgentModels.ContextManagerRequest request = AgentModels.ContextManagerRequest.builder()
                    .reason(STUCK_HANDLER)
                    .build()
                    .addRequest(lastRequest);

            request = decorateRequest(
                    request,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_CONTEXT_MANAGER_STUCK,
                    METHOD_HANDLE_STUCK,
                    lastRequest
            );

            PromptContext promptContext = buildPromptContext(
                    AgentType.CONTEXT_MANAGER,
                    request,
                    lastRequest,
                    request,
                    context,
                    ACTION_CONTEXT_MANAGER_STUCK,
                    METHOD_HANDLE_STUCK,
                    TEMPLATE_WORKFLOW_CONTEXT_MANAGER
            );

            AgentModels.ContextManagerResultRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_CONTEXT_MANAGER,
                    promptContext,
                    Map.of("reason", loopSummary),
                    buildToolContext(
                            AgentType.CONTEXT_MANAGER,
                            request,
                            lastRequest,
                            request,
                            context,
                            ACTION_CONTEXT_MANAGER_STUCK,
                            METHOD_HANDLE_STUCK,
                            TEMPLATE_WORKFLOW_CONTEXT_MANAGER,
                            ToolContext.of(ToolAbstraction.fromToolCarrier(contextManagerTools))
                    ),
                    AgentModels.ContextManagerResultRouting.class,
                    context
            );

            routing = decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_CONTEXT_MANAGER_STUCK,
                    METHOD_HANDLE_STUCK,
                    lastRequest
            );

            if (agentProcess != null) {
                agentProcess.addObject(routing);
            }

            return new StuckHandlerResult(
                    "Context manager recovery invoked",
                    this,
                    StuckHandlingResultCode.REPLAN,
                    agentProcess
            );
        }

        @Action(canRerun = true, cost = 1)
        public AgentModels.ContextManagerResultRouting contextManagerRequest(
                AgentModels.ContextManagerRequest request,
                OperationContext context
        ) {

            BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);

            String loopSummary = Optional.ofNullable(history)
                    .map(BlackboardHistory::summary)
                    .orElse("No history available");

            AgentModels.AgentRequest lastRequest = findLastRequest(
                    history,
                    a -> !(a instanceof AgentModels.InterruptRequest)
                            && !(a instanceof AgentModels.ContextManagerRequest)
                            && !(a instanceof AgentModels.ContextManagerRoutingRequest));

            request = decorateRequest(
                    request,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_CONTEXT_MANAGER,
                    METHOD_CONTEXT_MANAGER,
                    lastRequest
            );

            PromptContext promptContext = buildPromptContext(
                    AgentType.CONTEXT_MANAGER,
                    request,
                    lastRequest,
                    request,
                    context,
                    ACTION_CONTEXT_MANAGER,
                    METHOD_CONTEXT_MANAGER,
                    TEMPLATE_WORKFLOW_CONTEXT_MANAGER
            );

            AgentModels.ContextManagerResultRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_CONTEXT_MANAGER,
                    promptContext,
                    Map.of("reason", loopSummary),
                    buildToolContext(
                            AgentType.CONTEXT_MANAGER,
                            request,
                            lastRequest,
                            request,
                            context,
                            ACTION_CONTEXT_MANAGER,
                            METHOD_CONTEXT_MANAGER,
                            TEMPLATE_WORKFLOW_CONTEXT_MANAGER,
                            ToolContext.of(ToolAbstraction.fromToolCarrier(contextManagerTools))
                    ),
                    AgentModels.ContextManagerResultRouting.class,
                    context
            );

            routing = decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_CONTEXT_MANAGER,
                    METHOD_CONTEXT_MANAGER,
                    lastRequest
            );

            return routing;
        }

        @Action(canRerun = true)
        public AgentModels.OrchestratorRouting coordinateWorkflow(
                AgentModels.OrchestratorRequest input,
                OperationContext context
        ) {
            AgentModels.AgentRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.AgentRequest.class);

            input = AgentInterfaces.decorateRequest(
                    input,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_ORCHESTRATOR,
                    METHOD_COORDINATE_WORKFLOW,
                    lastRequest
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.ORCHESTRATOR,
                    input,
                    lastRequest,
                    input,
                    context,
                    ACTION_ORCHESTRATOR,
                    METHOD_COORDINATE_WORKFLOW,
                    TEMPLATE_WORKFLOW_ORCHESTRATOR
            );

            var model = new HashMap<String, Object>();

            Optional.ofNullable(input.goal())
                    .ifPresent(g -> model.put("goal", g));
            Optional.ofNullable(input.phase())
                    .ifPresent(g -> model.put("phase", g));

            AgentModels.OrchestratorRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_ORCHESTRATOR,
                    promptContext,
                    model,
                    buildToolContext(
                            AgentType.ORCHESTRATOR,
                            input,
                            lastRequest,
                            input,
                            context,
                            ACTION_ORCHESTRATOR,
                            METHOD_COORDINATE_WORKFLOW,
                            TEMPLATE_WORKFLOW_ORCHESTRATOR,
                            ToolContext.empty()
                    ),
                    AgentModels.OrchestratorRouting.class,
                    context
            );

            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_ORCHESTRATOR,
                    METHOD_COORDINATE_WORKFLOW,
                    lastRequest
            );
        }


        @Action(canRerun = true, cost = 1)
        public AgentModels.ContextManagerResultRouting handleContextManagerInterrupt(
                @NotNull AgentModels.InterruptRequest.ContextManagerInterruptRequest request,
                OperationContext context
        ) {
            AgentModels.ContextManagerRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.ContextManagerRequest.class);

            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Context manager request not found - cannot recover from interrupt.",
                        METHOD_HANDLE_CONTEXT_MANAGER_INTERRUPT,
                        AgentModels.InterruptRequest.ContextManagerInterruptRequest.class,
                        1
                );
            }

            request = AgentInterfaces.decorateRequest(
                    request,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_CONTEXT_MANAGER_INTERRUPT,
                    METHOD_HANDLE_CONTEXT_MANAGER_INTERRUPT,
                    lastRequest
            );

            PromptContext promptContext = buildPromptContext(
                    AgentType.CONTEXT_MANAGER,
                    lastRequest,
                    lastRequest,
                    request,
                    context,
                    ACTION_CONTEXT_MANAGER_INTERRUPT,
                    METHOD_HANDLE_CONTEXT_MANAGER_INTERRUPT,
                    TEMPLATE_WORKFLOW_CONTEXT_MANAGER_INTERRUPT
            );

            String reason = firstNonBlank(
                    request.reason(),
                    request.contextForDecision(),
                    request.contextFindings()
            );

            AgentModels.ContextManagerResultRouting resumed = interruptService.handleInterrupt(
                    context,
                    request,
                    workflowGraphService.requireOrchestrator(context),
                    TEMPLATE_WORKFLOW_CONTEXT_MANAGER_INTERRUPT,
                    promptContext,
                    Map.of("reason", reason),
                    buildToolContext(
                            AgentType.CONTEXT_MANAGER,
                            lastRequest,
                            lastRequest,
                            request,
                            context,
                            ACTION_CONTEXT_MANAGER_INTERRUPT,
                            METHOD_HANDLE_CONTEXT_MANAGER_INTERRUPT,
                            TEMPLATE_WORKFLOW_CONTEXT_MANAGER_INTERRUPT,
                            ToolContext.of(ToolAbstraction.fromToolCarrier(contextManagerTools))
                    ),
                    AgentModels.ContextManagerResultRouting.class
            );

            return AgentInterfaces.decorateRouting(
                    resumed,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_CONTEXT_MANAGER_INTERRUPT,
                    lastRequest
            );
        }

        @Action(canRerun = true, cost = 2)
        public AgentModels.ContextManagerRequest routeToContextManager(
                AgentModels.ContextManagerRoutingRequest request,
                OperationContext context
        ) {
            BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);
            AgentModels.AgentRequest lastRequest = findLastNonContextRequest(history);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Upstream request not found - cannot route to context manager.",
                        METHOD_ROUTE_TO_CONTEXT_MANAGER,
                        AgentModels.ContextManagerRoutingRequest.class,
                        1
                );
            }

            request = AgentInterfaces.decorateRequest(
                    request,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_CONTEXT_MANAGER_ROUTE,
                    METHOD_ROUTE_TO_CONTEXT_MANAGER,
                    lastRequest
            );

            AgentModels.ContextManagerRequest contextManagerRequest = AgentModels.ContextManagerRequest.builder()
                    .reason(request != null ? request.reason() : "%s routed to context manager, but did not provide reason.".formatted(Optional.ofNullable(lastRequest).map(a -> a.getClass().getName()).orElse("Unknown agent")))
                    .type(Optional.ofNullable(request)
                            .flatMap(r -> Optional.ofNullable(r.type()))
                            .orElse(AgentModels.ContextManagerRequestType.INTROSPECT_AGENT_CONTEXT))
                    .build()
                    .addRequest(lastRequest);

            contextManagerRequest = AgentInterfaces.decorateRequest(
                    contextManagerRequest,
                    context,
                    requestDecorators,
                    AGENT_NAME_NONE,
                    ACTION_CONTEXT_MANAGER_ROUTE,
                    METHOD_ROUTE_TO_CONTEXT_MANAGER,
                    lastRequest
            );

            return AgentInterfaces.decorateRequestResult(
                    contextManagerRequest,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_CONTEXT_MANAGER_ROUTE,
                    METHOD_ROUTE_TO_CONTEXT_MANAGER,
                    lastRequest
            );
        }

        private AgentModels.AgentRequest findLastRequest(BlackboardHistory bh,
                                                         Predicate<AgentModels.AgentRequest> r) {
            if (bh == null)
                return null;

            return bh.fromHistory(history -> {
                if (history == null || history.entries() == null) {
                    return null;
                }
                List<BlackboardHistory.Entry> entries = history.entries();
                for (int i = entries.size() - 1; i >= 0; i--) {
                    BlackboardHistory.Entry entry = entries.get(i);
                    if (entry == null) {
                        continue;
                    }
                    Object input = switch (entry) {
                        case BlackboardHistory.DefaultEntry defaultEntry ->
                                defaultEntry.input();
                        case BlackboardHistory.MessageEntry ignored ->
                                null;
                    };
                    if (input instanceof AgentModels.AgentRequest agentRequest && r.test(agentRequest)) {
                        return agentRequest;
                    }
                }
                return null;
            });
        }

        private AgentModels.AgentRequest findLastNonContextRequest(BlackboardHistory history) {
            return history.getValue(entry -> {
                        Object input = switch (entry) {
                            case BlackboardHistory.DefaultEntry defaultEntry ->
                                    defaultEntry.input();
                            case BlackboardHistory.MessageEntry ignored ->
                                    null;
                        };
                        if (input instanceof AgentModels.AgentRequest agentRequest
                                && !(agentRequest instanceof AgentModels.ContextManagerRoutingRequest)
                                && !(agentRequest instanceof AgentModels.ContextManagerRequest)) {
                            return Optional.of(agentRequest);
                        }
                        return Optional.empty();
                    })
                    .orElse(null);
        }

        private OperationContext buildStuckHandlerContext(AgentProcess agentProcess) {
            if (agentProcess == null) {
                return null;
            }
            Operation operation = InjectedType.Companion.named(STUCK_HANDLER);
            return OperationContext.Companion.invoke(
                    agentProcess.getProcessContext(),
                    operation,
                    Set.of()
            );
        }

        /**
         * Build prompt context by extracting upstream contexts from typed curation fields on the input.
         * Delegates to PromptContextFactory for pattern matching logic.
         */
        private PromptContext buildPromptContext(
                AgentType agentType,
                AgentModels.AgentRequest contextRequest,
                AgentModels.AgentRequest previousRequest,
                AgentModels.AgentRequest currentRequest,
                OperationContext context,
                String actionName,
                String methodName,
                String templateName
        ) {
            BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);
            PromptContext promptContext = promptContextFactory.build(
                    agentType,
                    contextRequest,
                    previousRequest,
                    currentRequest,
                    history,
                    templateName
            );
            return AgentInterfaces.decoratePromptContext(
                    promptContext,
                    context,
                    promptContextDecorators,
                    multiAgentAgentName(),
                    actionName,
                    methodName,
                    previousRequest,
                    currentRequest
            );
        }

        private ToolContext buildToolContext(
                AgentType agentType,
                AgentModels.AgentRequest contextRequest,
                AgentModels.AgentRequest previousRequest,
                AgentModels.AgentRequest currentRequest,
                OperationContext context,
                String actionName,
                String methodName,
                String templateName,
                ToolContext toolContext
        ) {
            return AgentInterfaces.decorateToolContext(
                    toolContext,
                    currentRequest,
                    previousRequest,
                    context,
                    toolContextDecorators,
                    multiAgentAgentName(),
                    actionName,
                    methodName
            );
        }

        @Action
        @AchievesGoal(description = "Finished orchestrator collector")
        public AgentModels.OrchestratorCollectorResult finalCollectorResult(
                AgentModels.OrchestratorCollectorResult input,
                OperationContext context
        ) {
            AgentModels.OrchestratorCollectorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorCollectorRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Orchestrator collector request not found - cannot finalize collector result.",
                        METHOD_FINAL_COLLECTOR_RESULT,
                        AgentModels.OrchestratorCollectorResult.class,
                        1
                );
            }

            return AgentInterfaces.decorateFinalResult(
                    input,
                    lastRequest,
                    lastRequest,
                    context,
                    finalResultDecorators,
                    multiAgentAgentName(),
                    ACTION_ORCHESTRATOR_COLLECTOR,
                    METHOD_FINAL_COLLECTOR_RESULT
            );
        }
        


        @Action(canRerun = true)
        public AgentModels.OrchestratorCollectorRouting consolidateWorkflowOutputs(
                AgentModels.OrchestratorCollectorRequest input,
                OperationContext context
        ) {
            AgentModels.OrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Orchestrator request not found - cannot consolidate workflow outputs.",
                        METHOD_CONSOLIDATE_WORKFLOW_OUTPUTS,
                        AgentModels.OrchestratorCollectorRequest.class,
                        1
                );
            }
            input = AgentInterfaces.decorateRequest(
                    input,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_ORCHESTRATOR_COLLECTOR,
                    METHOD_CONSOLIDATE_WORKFLOW_OUTPUTS,
                    lastRequest
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.ORCHESTRATOR_COLLECTOR,
                    input,
                    lastRequest,
                    input,
                    context,
                    ACTION_ORCHESTRATOR_COLLECTOR,
                    METHOD_CONSOLIDATE_WORKFLOW_OUTPUTS,
                    TEMPLATE_WORKFLOW_ORCHESTRATOR_COLLECTOR
            );

            var model = new HashMap<String, Object>();
            Optional.ofNullable(input.goal())
                    .ifPresent(g -> model.put("goal", g));
            Optional.ofNullable(input.phase())
                    .ifPresent(g -> model.put("phase", g));

            AgentModels.OrchestratorCollectorRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_ORCHESTRATOR_COLLECTOR,
                    promptContext,
                    model,
                    buildToolContext(
                            AgentType.ORCHESTRATOR_COLLECTOR,
                            input,
                            lastRequest,
                            input,
                            context,
                            ACTION_ORCHESTRATOR_COLLECTOR,
                            METHOD_CONSOLIDATE_WORKFLOW_OUTPUTS,
                            TEMPLATE_WORKFLOW_ORCHESTRATOR_COLLECTOR,
                            ToolContext.empty()
                    ),
                    AgentModels.OrchestratorCollectorRouting.class,
                    context
            );

            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_ORCHESTRATOR_COLLECTOR,
                    METHOD_CONSOLIDATE_WORKFLOW_OUTPUTS,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.DiscoveryCollectorRouting consolidateDiscoveryFindings(
                AgentModels.DiscoveryCollectorRequest input,
                OperationContext context
        ) {
            AgentModels.DiscoveryOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.DiscoveryOrchestratorRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Discovery orchestrator request not found - cannot consolidate discovery findings.",
                        METHOD_CONSOLIDATE_DISCOVERY_FINDINGS,
                        AgentModels.DiscoveryCollectorRequest.class,
                        1
                );
            }
            input = AgentInterfaces.decorateRequest(
                    input,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_DISCOVERY_COLLECTOR,
                    METHOD_CONSOLIDATE_DISCOVERY_FINDINGS,
                    lastRequest
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.DISCOVERY_COLLECTOR,
                    input,
                    lastRequest,
                    input,
                    context,
                    ACTION_DISCOVERY_COLLECTOR,
                    METHOD_CONSOLIDATE_DISCOVERY_FINDINGS,
                    TEMPLATE_WORKFLOW_DISCOVERY_COLLECTOR
            );

            AgentModels.DiscoveryCollectorRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_DISCOVERY_COLLECTOR,
                    promptContext,
                    Map.of("goal", Optional.ofNullable(input.goal()).orElse(""), "discoveryResults", Optional.ofNullable(input.discoveryResults()).orElse("")),
                    buildToolContext(
                            AgentType.DISCOVERY_COLLECTOR,
                            input,
                            lastRequest,
                            input,
                            context,
                            ACTION_DISCOVERY_COLLECTOR,
                            METHOD_CONSOLIDATE_DISCOVERY_FINDINGS,
                            TEMPLATE_WORKFLOW_DISCOVERY_COLLECTOR,
                            ToolContext.empty()
                    ),
                    AgentModels.DiscoveryCollectorRouting.class,
                    context
            );

            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_DISCOVERY_COLLECTOR,
                    METHOD_CONSOLIDATE_DISCOVERY_FINDINGS,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.PlanningCollectorRouting consolidatePlansIntoTickets(
                AgentModels.PlanningCollectorRequest input,
                OperationContext context
        ) {
            AgentModels.PlanningOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningOrchestratorRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Planning request not found - cannot consolidate plans into tickets.",
                        METHOD_CONSOLIDATE_PLANS_INTO_TICKETS,
                        AgentModels.PlanningCollectorRequest.class,
                        1
                );
            }
            input = AgentInterfaces.decorateRequest(
                    input,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_PLANNING_COLLECTOR,
                    METHOD_CONSOLIDATE_PLANS_INTO_TICKETS,
                    lastRequest
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.PLANNING_COLLECTOR,
                    input,
                    lastRequest,
                    input,
                    context,
                    ACTION_PLANNING_COLLECTOR,
                    METHOD_CONSOLIDATE_PLANS_INTO_TICKETS,
                    TEMPLATE_WORKFLOW_PLANNING_COLLECTOR
            );

            AgentModels.PlanningCollectorRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_PLANNING_COLLECTOR,
                    promptContext,
                    Map.of("goal", Optional.ofNullable(input.goal()).orElse(""), "planningResults", Optional.ofNullable(input.planningResults()).orElse("")),
                    buildToolContext(
                            AgentType.PLANNING_COLLECTOR,
                            input,
                            lastRequest,
                            input,
                            context,
                            ACTION_PLANNING_COLLECTOR,
                            METHOD_CONSOLIDATE_PLANS_INTO_TICKETS,
                            TEMPLATE_WORKFLOW_PLANNING_COLLECTOR,
                            ToolContext.empty()
                    ),
                    AgentModels.PlanningCollectorRouting.class,
                    context
            );

            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_PLANNING_COLLECTOR,
                    METHOD_CONSOLIDATE_PLANS_INTO_TICKETS,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.TicketCollectorRouting consolidateTicketResults(
                AgentModels.TicketCollectorRequest input,
                OperationContext context
        ) {
            AgentModels.TicketOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.TicketOrchestratorRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Ticket request not found - cannot consolidate ticket results.",
                        METHOD_CONSOLIDATE_TICKET_RESULTS,
                        AgentModels.TicketCollectorRequest.class,
                        1
                );
            }
            input = AgentInterfaces.decorateRequest(
                    input,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_TICKET_COLLECTOR,
                    METHOD_CONSOLIDATE_TICKET_RESULTS,
                    lastRequest
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.TICKET_COLLECTOR,
                    input,
                    lastRequest,
                    input,
                    context,
                    ACTION_TICKET_COLLECTOR,
                    METHOD_CONSOLIDATE_TICKET_RESULTS,
                    TEMPLATE_WORKFLOW_TICKET_COLLECTOR
            );

            AgentModels.TicketCollectorRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_TICKET_COLLECTOR,
                    promptContext,
                    Map.of("goal", Optional.ofNullable(input.goal()).orElse(""), "ticketResults", input.ticketResults()),
                    buildToolContext(
                            AgentType.TICKET_COLLECTOR,
                            input,
                            lastRequest,
                            input,
                            context,
                            ACTION_TICKET_COLLECTOR,
                            METHOD_CONSOLIDATE_TICKET_RESULTS,
                            TEMPLATE_WORKFLOW_TICKET_COLLECTOR,
                            ToolContext.empty()
                    ),
                    AgentModels.TicketCollectorRouting.class,
                    context
            );

            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_TICKET_COLLECTOR,
                    METHOD_CONSOLIDATE_TICKET_RESULTS,
                    lastRequest
            );
        }


        @Action(canRerun = true, cost = 1)
        public AgentModels.OrchestratorRouting handleOrchestratorInterrupt(
                AgentModels.InterruptRequest.OrchestratorInterruptRequest request,
                OperationContext context
        ) {
            OrchestratorNode originNode = workflowGraphService.requireOrchestrator(context);
            AgentModels.OrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorRequest.class);
            if (lastRequest == null) {
                workflowGraphService.emitErrorEvent(
                        originNode,
                        "Orchestrator request not found - cannot recover from interrupt."
                );
                throw new DegenerateLoopException("Found strange situation where OrchestratorRequest not found. Impossible state.",
                        METHOD_HANDLE_ORCHESTRATOR_INTERRUPT, AgentModels.InterruptRequest.OrchestratorInterruptRequest.class, 1);
            }
            request = AgentInterfaces.decorateRequest(
                    request,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_ORCHESTRATOR_INTERRUPT,
                    METHOD_HANDLE_ORCHESTRATOR_INTERRUPT,
                    lastRequest
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.ORCHESTRATOR,
                    lastRequest,
                    lastRequest,
                    request,
                    context,
                    ACTION_ORCHESTRATOR_INTERRUPT,
                    METHOD_HANDLE_ORCHESTRATOR_INTERRUPT,
                    TEMPLATE_WORKFLOW_ORCHESTRATOR
            );
            var resumed = interruptService.handleInterrupt(
                    context,
                    request,
                    originNode,
                    TEMPLATE_WORKFLOW_ORCHESTRATOR,
                    promptContext,
                    Map.of("goal", lastRequest.goal(), "phase", lastRequest.phase()),
                    AgentModels.OrchestratorRouting.class
            );
            return AgentInterfaces.decorateRouting(
                    resumed,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_ORCHESTRATOR_INTERRUPT,
                    METHOD_HANDLE_ORCHESTRATOR_INTERRUPT,
                    lastRequest
            );
        }


        @Action(canRerun = true)
        public AgentModels.DiscoveryOrchestratorRouting kickOffAnyNumberOfAgentsForCodeSearch(
                AgentModels.DiscoveryOrchestratorRequest input,
                OperationContext context
        ) {
            AgentModels.OrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Discovery orchestrator request not found - cannot kick off discovery agents.",
                        METHOD_KICK_OFF_ANY_NUMBER_OF_AGENTS_FOR_CODE_SEARCH,
                        AgentModels.DiscoveryOrchestratorRequest.class,
                        1
                );
            }
            input = AgentInterfaces.decorateRequest(
                    input,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_DISCOVERY_ORCHESTRATOR,
                    METHOD_KICK_OFF_ANY_NUMBER_OF_AGENTS_FOR_CODE_SEARCH,
                    lastRequest
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.DISCOVERY_ORCHESTRATOR,
                    input,
                    lastRequest,
                    input,
                    context,
                    ACTION_DISCOVERY_ORCHESTRATOR,
                    METHOD_KICK_OFF_ANY_NUMBER_OF_AGENTS_FOR_CODE_SEARCH,
                    TEMPLATE_WORKFLOW_DISCOVERY_ORCHESTRATOR
            );

            AgentModels.DiscoveryOrchestratorRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_DISCOVERY_ORCHESTRATOR,
                    promptContext,
                    Map.of("goal", input.goal()),
                    buildToolContext(
                            AgentType.DISCOVERY_ORCHESTRATOR,
                            input,
                            lastRequest,
                            input,
                            context,
                            ACTION_DISCOVERY_ORCHESTRATOR,
                            METHOD_KICK_OFF_ANY_NUMBER_OF_AGENTS_FOR_CODE_SEARCH,
                            TEMPLATE_WORKFLOW_DISCOVERY_ORCHESTRATOR,
                            ToolContext.empty()
                    ),
                    AgentModels.DiscoveryOrchestratorRouting.class,
                    context
            );

            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_DISCOVERY_ORCHESTRATOR,
                    METHOD_KICK_OFF_ANY_NUMBER_OF_AGENTS_FOR_CODE_SEARCH,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.DiscoveryAgentDispatchRouting dispatchDiscoveryAgentRequests(
                @NotNull AgentModels.DiscoveryAgentRequests input,
                ActionContext context
        ) {
            AgentModels.DiscoveryOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.DiscoveryOrchestratorRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Discovery dispatch request not found - cannot dispatch discovery agents.",
                        METHOD_DISPATCH_DISCOVERY_AGENT_REQUESTS,
                        AgentModels.DiscoveryAgentRequests.class,
                        1
                );
            }
            input = AgentInterfaces.decorateRequest(
                    input,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_DISCOVERY_DISPATCH,
                    METHOD_DISPATCH_DISCOVERY_AGENT_REQUESTS,
                    lastRequest
            );
            String goal = resolveDiscoveryGoal(context, input);
            List<AgentModels.DiscoveryAgentResult> discoveryResults = new ArrayList<>();
            var discoveryDispatchAgent = context.agentPlatform().agents()
                    .stream().filter(a -> Objects.equals(a.getName(), AgentInterfaces.WORKFLOW_DISCOVERY_DISPATCH_SUBAGENT))
                    .findAny().orElse(null);
            for (AgentModels.DiscoveryAgentRequest request : input.requests()) {
                if (request == null) {
                    continue;
                }

                context.addObject(input);

                AgentModels.DiscoveryAgentRouting response = runSubProcess(
                        context,
                        request,
                        discoveryDispatchAgent,
                        AgentModels.DiscoveryAgentRouting.class
                );

                AgentModels.DiscoveryAgentResult agentResult = response != null ? response.agentResult() : null;
                if (agentResult != null) {
                    discoveryResults.add(agentResult);
                }
            }

            var d = AgentModels.DiscoveryAgentResults.builder()
                    .result(discoveryResults)
                    .worktreeContext(input.worktreeContext())
                    .build();

            // Decorate with ResultsRequestDecorator chain (Phase 2: child→trunk merge)
            d = AgentInterfaces.decorateResultsRequest(
                    d,
                    context,
                    resultsRequestDecorators,
                    multiAgentAgentName(),
                    ACTION_DISCOVERY_DISPATCH,
                    METHOD_DISPATCH_DISCOVERY_AGENT_REQUESTS,
                    lastRequest
            );

            PromptContext promptContext = buildPromptContext(
                    AgentType.DISCOVERY_AGENT_DISPATCH,
                    d,
                    lastRequest,
                    d,
                    context,
                    ACTION_DISCOVERY_DISPATCH,
                    METHOD_DISPATCH_DISCOVERY_AGENT_REQUESTS,
                    TEMPLATE_WORKFLOW_DISCOVERY_DISPATCH
            );

            AgentModels.DiscoveryAgentDispatchRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_DISCOVERY_DISPATCH,
                    promptContext,
                    Map.of(
                            "goal",
                            goal,
                            "discoveryResults",
                            d.prettyPrint(new AgentContext.AgentSerializationCtx.ResultsSerialization())
                    ),
                    buildToolContext(
                            AgentType.DISCOVERY_AGENT_DISPATCH,
                            d,
                            lastRequest,
                            d,
                            context,
                            ACTION_DISCOVERY_DISPATCH,
                            METHOD_DISPATCH_DISCOVERY_AGENT_REQUESTS,
                            TEMPLATE_WORKFLOW_DISCOVERY_DISPATCH,
                            ToolContext.empty()
                    ),
                    AgentModels.DiscoveryAgentDispatchRouting.class,
                    context
            );
            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_DISCOVERY_DISPATCH,
                    METHOD_DISPATCH_DISCOVERY_AGENT_REQUESTS,
                    lastRequest
            );
        }

        @Action(canRerun = true, cost = 1.0)
        public AgentModels.DiscoveryOrchestratorRouting handleDiscoveryInterrupt(
                AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest request,
                OperationContext context
        ) {
            DiscoveryOrchestratorNode originNode = workflowGraphService.requireDiscoveryOrchestrator(context);
            AgentModels.DiscoveryOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.DiscoveryOrchestratorRequest.class);
            if (lastRequest == null) {
                workflowGraphService.emitErrorEvent(
                        originNode,
                        "Discovery orchestrator request not found - cannot recover from interrupt."
                );
                throw new DegenerateLoopException(
                        "Discovery orchestrator request not found - cannot recover from interrupt.",
                        METHOD_HANDLE_DISCOVERY_INTERRUPT,
                        AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest.class,
                        1
                );
            }
            request = AgentInterfaces.decorateRequest(
                    request,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_DISCOVERY_INTERRUPT,
                    METHOD_HANDLE_DISCOVERY_INTERRUPT,
                    lastRequest
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.DISCOVERY_ORCHESTRATOR,
                    lastRequest,
                    lastRequest,
                    request,
                    context,
                    ACTION_DISCOVERY_INTERRUPT,
                    METHOD_HANDLE_DISCOVERY_INTERRUPT,
                    TEMPLATE_WORKFLOW_DISCOVERY_ORCHESTRATOR
            );
            var routing = interruptService.handleInterrupt(
                    context,
                    request,
                    originNode,
                    TEMPLATE_WORKFLOW_DISCOVERY_ORCHESTRATOR,
                    promptContext,
                    Map.of("goal", lastRequest.goal()),
                    AgentModels.DiscoveryOrchestratorRouting.class
            );
            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_DISCOVERY_INTERRUPT,
                    METHOD_HANDLE_DISCOVERY_INTERRUPT,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.PlanningOrchestratorRouting decomposePlanAndCreateWorkItems(
                AgentModels.PlanningOrchestratorRequest input,
                OperationContext context
        ) {
            AgentModels.OrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Planning orchestrator request not found - cannot decompose plan.",
                        METHOD_DECOMPOSE_PLAN_AND_CREATE_WORK_ITEMS,
                        AgentModels.PlanningOrchestratorRequest.class,
                        1
                );
            }
            input = AgentInterfaces.decorateRequest(
                    input,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_PLANNING_ORCHESTRATOR,
                    METHOD_DECOMPOSE_PLAN_AND_CREATE_WORK_ITEMS,
                    lastRequest
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.PLANNING_ORCHESTRATOR,
                    input,
                    lastRequest,
                    input,
                    context,
                    ACTION_PLANNING_ORCHESTRATOR,
                    METHOD_DECOMPOSE_PLAN_AND_CREATE_WORK_ITEMS,
                    TEMPLATE_WORKFLOW_PLANNING_ORCHESTRATOR
            );

            AgentModels.PlanningOrchestratorRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_PLANNING_ORCHESTRATOR,
                    promptContext,
                    Map.of("goal", input.goal()),
                    buildToolContext(
                            AgentType.PLANNING_ORCHESTRATOR,
                            input,
                            lastRequest,
                            input,
                            context,
                            ACTION_PLANNING_ORCHESTRATOR,
                            METHOD_DECOMPOSE_PLAN_AND_CREATE_WORK_ITEMS,
                            TEMPLATE_WORKFLOW_PLANNING_ORCHESTRATOR,
                            ToolContext.empty()
                    ),
                    AgentModels.PlanningOrchestratorRouting.class,
                    context
            );

            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_PLANNING_ORCHESTRATOR,
                    METHOD_DECOMPOSE_PLAN_AND_CREATE_WORK_ITEMS,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.PlanningAgentDispatchRouting dispatchPlanningAgentRequests(
                @NotNull AgentModels.PlanningAgentRequests input,
                ActionContext context
        ) {
            AgentModels.PlanningOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningOrchestratorRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Planning dispatch request not found - cannot dispatch planning agents.",
                        METHOD_DISPATCH_PLANNING_AGENT_REQUESTS,
                        AgentModels.PlanningAgentRequests.class,
                        1
                );
            }
            input = AgentInterfaces.decorateRequest(
                    input,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_PLANNING_DISPATCH,
                    METHOD_DISPATCH_PLANNING_AGENT_REQUESTS,
                    lastRequest
            );

            String goal = resolvePlanningGoal(context, input);

            var planningDispatchAgent = context.agentPlatform().agents()
                    .stream().filter(a -> Objects.equals(a.getName(), AgentInterfaces.WORKFLOW_PLANNING_DISPATCH_SUBAGENT))
                    .findAny().orElse(null);

            List<AgentModels.PlanningAgentResult> planningResults = new ArrayList<>();

            for (AgentModels.PlanningAgentRequest request : input.requests()) {
                if (request == null) {
                    continue;
                }

                context.addObject(input);

                AgentModels.PlanningAgentRouting response = runSubProcess(
                        context,
                        request,
                        planningDispatchAgent,
                        AgentModels.PlanningAgentRouting.class
                );

                AgentModels.PlanningAgentResult agentResult = response != null ? response.agentResult() : null;
                if (agentResult != null) {
                    planningResults.add(agentResult);
                }
            }

            AgentModels.PlanningAgentResults planningAgentResults = AgentModels.PlanningAgentResults.builder()
                    .planningAgentResults(planningResults)
                    .worktreeContext(input.worktreeContext())
                    .build();

            // Decorate with ResultsRequestDecorator chain (Phase 2: child→trunk merge)
            planningAgentResults = AgentInterfaces.decorateResultsRequest(
                    planningAgentResults,
                    context,
                    resultsRequestDecorators,
                    multiAgentAgentName(),
                    ACTION_PLANNING_DISPATCH,
                    METHOD_DISPATCH_PLANNING_AGENT_REQUESTS,
                    lastRequest
            );

            planningAgentResults = AgentInterfaces.decorateRequest(
                    planningAgentResults,
                    context,
                    requestDecorators,
                    AGENT_NAME_PLANNING_AGENT_DISPATCH,
                    ACTION_PLANNING_AGENT_POST_DISPATCH,
                    METHOD_DISPATCH_PLANNING_AGENT_REQUESTS,
                    lastRequest
            );

            PromptContext promptContext = buildPromptContext(
                    AgentType.PLANNING_AGENT_DISPATCH,
                    planningAgentResults,
                    lastRequest,
                    planningAgentResults,
                    context,
                    ACTION_PLANNING_DISPATCH,
                    METHOD_DISPATCH_PLANNING_AGENT_REQUESTS,
                    TEMPLATE_WORKFLOW_PLANNING_DISPATCH
            );

            AgentModels.PlanningAgentDispatchRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_PLANNING_DISPATCH,
                    promptContext,
                    Map.of(
                            "goal",
                            goal,
                            "planningResults",
                            planningAgentResults.prettyPrint(new AgentContext.AgentSerializationCtx.ResultsSerialization())
                    ),
                    buildToolContext(
                            AgentType.PLANNING_AGENT_DISPATCH,
                            planningAgentResults,
                            lastRequest,
                            planningAgentResults,
                            context,
                            ACTION_PLANNING_DISPATCH,
                            METHOD_DISPATCH_PLANNING_AGENT_REQUESTS,
                            TEMPLATE_WORKFLOW_PLANNING_DISPATCH,
                            ToolContext.empty()
                    ),
                    AgentModels.PlanningAgentDispatchRouting.class,
                    context
            );

            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_PLANNING_DISPATCH,
                    METHOD_DISPATCH_PLANNING_AGENT_REQUESTS,
                    lastRequest
            );
        }

        @Action(canRerun = true, cost = 1.0)
        public AgentModels.PlanningOrchestratorRouting handlePlanningInterrupt(
                AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest request,
                OperationContext context
        ) {
            PlanningOrchestratorNode originNode = workflowGraphService.requirePlanningOrchestrator(context);

            AgentModels.PlanningOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningOrchestratorRequest.class);

            if (lastRequest == null) {
                workflowGraphService.emitErrorEvent(
                        originNode,
                        "Planning orchestrator request not found - cannot recover from interrupt."
                );
                throw new DegenerateLoopException(
                        "Planning orchestrator request not found - cannot recover from interrupt.",
                        METHOD_HANDLE_PLANNING_INTERRUPT,
                        AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest.class,
                        1
                );
            }
            request = AgentInterfaces.decorateRequest(
                    request,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_PLANNING_INTERRUPT,
                    METHOD_HANDLE_PLANNING_INTERRUPT,
                    lastRequest
            );

            PromptContext promptContext = buildPromptContext(
                    AgentType.PLANNING_ORCHESTRATOR,
                    lastRequest,
                    lastRequest,
                    request,
                    context,
                    ACTION_PLANNING_INTERRUPT,
                    METHOD_HANDLE_PLANNING_INTERRUPT,
                    TEMPLATE_WORKFLOW_PLANNING_ORCHESTRATOR
            );
            var routing = interruptService.handleInterrupt(
                    context,
                    request,
                    originNode,
                    TEMPLATE_WORKFLOW_PLANNING_ORCHESTRATOR,
                    promptContext,
                    Map.of("goal", lastRequest.goal()),
                    AgentModels.PlanningOrchestratorRouting.class
            );
            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_PLANNING_INTERRUPT,
                    METHOD_HANDLE_PLANNING_INTERRUPT,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.OrchestratorCollectorResult finalizeTicketOrchestrator(
                AgentModels.TicketOrchestratorResult input,
                OperationContext context
        ) {
            AgentModels.TicketOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.TicketOrchestratorRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Ticket orchestrator request not found - cannot finalize ticket orchestrator.",
                        METHOD_FINALIZE_TICKET_ORCHESTRATOR,
                        AgentModels.TicketOrchestratorResult.class,
                        1
                );
            }
            blackboardHistoryService.registerAndHideInput(context, METHOD_FINALIZE_TICKET_ORCHESTRATOR, input);
            AgentModels.OrchestratorCollectorResult result = new AgentModels.OrchestratorCollectorResult(
                    input.output(),
                    new AgentModels.CollectorDecision(Events.CollectorDecisionType.ADVANCE_PHASE, "", ""));

            return AgentInterfaces.decorateFinalResult(
                    result,
                    lastRequest,
                    lastRequest,
                    context,
                    finalResultDecorators,
                    multiAgentAgentName(),
                    ACTION_ORCHESTRATOR_COLLECTOR,
                    METHOD_FINALIZE_TICKET_ORCHESTRATOR
            );
        }

        @Action(canRerun = true)
        public AgentModels.TicketOrchestratorRouting orchestrateTicketExecution(
                AgentModels.TicketOrchestratorRequest input,
                OperationContext context
        ) {
            AgentModels.OrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Ticket orchestrator request not found - cannot orchestrate ticket execution.",
                        METHOD_ORCHESTRATE_TICKET_EXECUTION,
                        AgentModels.TicketOrchestratorRequest.class,
                        1
                );
            }
            input = AgentInterfaces.decorateRequest(
                    input,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_TICKET_ORCHESTRATOR,
                    METHOD_ORCHESTRATE_TICKET_EXECUTION,
                    lastRequest
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.TICKET_ORCHESTRATOR,
                    input,
                    lastRequest,
                    input,
                    context,
                    ACTION_TICKET_ORCHESTRATOR,
                    METHOD_ORCHESTRATE_TICKET_EXECUTION,
                    TEMPLATE_WORKFLOW_TICKET_ORCHESTRATOR
            );

            AgentModels.TicketOrchestratorRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_TICKET_ORCHESTRATOR,
                    promptContext,
                    Map.of(
                            "goal", input.goal()
                    ),
                    buildToolContext(
                            AgentType.TICKET_ORCHESTRATOR,
                            input,
                            lastRequest,
                            input,
                            context,
                            ACTION_TICKET_ORCHESTRATOR,
                            METHOD_ORCHESTRATE_TICKET_EXECUTION,
                            TEMPLATE_WORKFLOW_TICKET_ORCHESTRATOR,
                            ToolContext.empty()
                    ),
                    AgentModels.TicketOrchestratorRouting.class,
                    context
            );

            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_TICKET_ORCHESTRATOR,
                    METHOD_ORCHESTRATE_TICKET_EXECUTION,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.TicketAgentDispatchRouting dispatchTicketAgentRequests(
                @NotNull AgentModels.TicketAgentRequests input,
                ActionContext context
        ) {
            AgentModels.TicketOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.TicketOrchestratorRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Ticket dispatch request not found - cannot dispatch ticket agents.",
                        METHOD_DISPATCH_TICKET_AGENT_REQUESTS,
                        AgentModels.TicketAgentRequests.class,
                        1
                );
            }
            input = AgentInterfaces.decorateRequest(
                    input,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_TICKET_DISPATCH,
                    METHOD_DISPATCH_TICKET_AGENT_REQUESTS,
                    lastRequest
            );

            String goal = resolveTicketGoal(context, input);

            List<AgentModels.TicketAgentResult> ticketResults = new ArrayList<>();

            var ticketDispatchAgent = context.agentPlatform().agents()
                    .stream().filter(a -> Objects.equals(a.getName(), AgentInterfaces.WORKFLOW_TICKET_DISPATCH_SUBAGENT))
                    .findAny().orElse(null);

            for (AgentModels.TicketAgentRequest request : input.requests()) {
                if (request == null) {
                    continue;
                }

                context.addObject(input);

                AgentModels.TicketAgentRouting response = runSubProcess(
                        context,
                        request,
                        ticketDispatchAgent,
                        AgentModels.TicketAgentRouting.class
                );

                AgentModels.TicketAgentResult agentResult = response != null ? response.agentResult() : null;

                if (agentResult != null) {
                    ticketResults.add(agentResult);
                }
            }

            var ticketAgentResults = AgentModels.TicketAgentResults.builder()
                    .ticketAgentResults(ticketResults)
                    .worktreeContext(input.worktreeContext())
                    .build();

            // Decorate with ResultsRequestDecorator chain (Phase 2: child→trunk merge)
            ticketAgentResults = AgentInterfaces.decorateResultsRequest(
                    ticketAgentResults,
                    context,
                    resultsRequestDecorators,
                    multiAgentAgentName(),
                    ACTION_TICKET_DISPATCH,
                    METHOD_DISPATCH_TICKET_AGENT_REQUESTS,
                    lastRequest
            );

            PromptContext promptContext = buildPromptContext(
                    AgentType.TICKET_AGENT_DISPATCH,
                    ticketAgentResults,
                    lastRequest,
                    ticketAgentResults,
                    context,
                    ACTION_TICKET_DISPATCH,
                    METHOD_DISPATCH_TICKET_AGENT_REQUESTS,
                    TEMPLATE_WORKFLOW_TICKET_DISPATCH
            );

            AgentModels.TicketAgentDispatchRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_TICKET_DISPATCH,
                    promptContext,
                    Map.of(
                            "goal",
                            goal,
                            "ticketResults",
                            ticketAgentResults.prettyPrint(new AgentContext.AgentSerializationCtx.ResultsSerialization())
                    ),
                    buildToolContext(
                            AgentType.TICKET_AGENT_DISPATCH,
                            ticketAgentResults,
                            lastRequest,
                            ticketAgentResults,
                            context,
                            ACTION_TICKET_DISPATCH,
                            METHOD_DISPATCH_TICKET_AGENT_REQUESTS,
                            TEMPLATE_WORKFLOW_TICKET_DISPATCH,
                            ToolContext.empty()
                    ),
                    AgentModels.TicketAgentDispatchRouting.class,
                    context
            );

            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_TICKET_DISPATCH,
                    METHOD_DISPATCH_TICKET_AGENT_REQUESTS,
                    lastRequest
            );
        }

        @Action(canRerun = true, cost = 1.0)
        public AgentModels.TicketOrchestratorRouting handleTicketInterrupt(
                AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest request,
                OperationContext context
        ) {
            TicketOrchestratorNode originNode = workflowGraphService.requireTicketOrchestrator(context);
            AgentModels.TicketOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.TicketOrchestratorRequest.class);
            if (lastRequest == null) {
                workflowGraphService.emitErrorEvent(
                        originNode,
                        "Ticket orchestrator request not found - cannot recover from interrupt."
                );
                throw new DegenerateLoopException(
                        "Ticket orchestrator request not found - cannot recover from interrupt.",
                        METHOD_HANDLE_TICKET_INTERRUPT,
                        AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest.class,
                        1
                );
            }
            request = AgentInterfaces.decorateRequest(
                    request,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_TICKET_INTERRUPT,
                    METHOD_HANDLE_TICKET_INTERRUPT,
                    lastRequest
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.TICKET_ORCHESTRATOR,
                    lastRequest,
                    lastRequest,
                    request,
                    context,
                    ACTION_TICKET_INTERRUPT,
                    METHOD_HANDLE_TICKET_INTERRUPT,
                    TEMPLATE_WORKFLOW_TICKET_ORCHESTRATOR
            );
            var routing = interruptService.handleInterrupt(
                    context,
                    request,
                    originNode,
                    TEMPLATE_WORKFLOW_TICKET_ORCHESTRATOR,
                    promptContext,
                    Map.of(
                            "goal", lastRequest.goal()
                    ),
                    AgentModels.TicketOrchestratorRouting.class
            );
            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_TICKET_INTERRUPT,
                    METHOD_HANDLE_TICKET_INTERRUPT,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.MergerRouting performMerge(
                AgentModels.MergerRequest input,
                OperationContext context
        ) {
            AgentModels.AgentRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.AgentRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Merger request not found - cannot perform merge.",
                        METHOD_PERFORM_MERGE,
                        AgentModels.MergerRequest.class,
                        1
                );
            }
            input = AgentInterfaces.decorateRequest(
                    input,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_MERGER_AGENT,
                    METHOD_PERFORM_MERGE,
                    lastRequest
            );
            String returnRoute = renderReturnRoute(
                    input.returnToOrchestratorCollector(),
                    input.returnToDiscoveryCollector(),
                    input.returnToPlanningCollector(),
                    input.returnToTicketCollector()
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.MERGER_AGENT,
                    input,
                    lastRequest,
                    input,
                    context,
                    ACTION_MERGER_AGENT,
                    METHOD_PERFORM_MERGE,
                    TEMPLATE_WORKFLOW_MERGER
            );

            AgentModels.MergerRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_MERGER,
                    promptContext,
                    Map.of(
                            "mergeContext", input.mergeContext(),
                            "mergeSummary", input.mergeSummary(),
                            "conflictFiles", input.conflictFiles(),
                            "returnRoute", returnRoute
                    ),
                    buildToolContext(
                            AgentType.MERGER_AGENT,
                            input,
                            lastRequest,
                            input,
                            context,
                            ACTION_MERGER_AGENT,
                            METHOD_PERFORM_MERGE,
                            TEMPLATE_WORKFLOW_MERGER,
                            ToolContext.empty()
                    ),
                    AgentModels.MergerRouting.class,
                    context
            );
            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_MERGER_AGENT,
                    METHOD_PERFORM_MERGE,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.ReviewRouting performReview(
                AgentModels.ReviewRequest input,
                OperationContext context
        ) {
            AgentModels.AgentRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.AgentRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Review request not found - cannot perform review.",
                        METHOD_PERFORM_REVIEW,
                        AgentModels.ReviewRequest.class,
                        1
                );
            }
            input = AgentInterfaces.decorateRequest(
                    input,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_REVIEW_AGENT,
                    METHOD_PERFORM_REVIEW,
                    lastRequest
            );
            String returnRoute = renderReturnRoute(
                    input.returnToOrchestratorCollector(),
                    input.returnToDiscoveryCollector(),
                    input.returnToPlanningCollector(),
                    input.returnToTicketCollector()
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.REVIEW_AGENT,
                    input,
                    lastRequest,
                    input,
                    context,
                    ACTION_REVIEW_AGENT,
                    METHOD_PERFORM_REVIEW,
                    TEMPLATE_WORKFLOW_REVIEW
            );

            AgentModels.ReviewRouting response = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_REVIEW,
                    promptContext,
                    Map.of(
                            "content", input.content(),
                            "criteria", input.criteria(),
                            "returnRoute", returnRoute
                    ),
                    buildToolContext(
                            AgentType.REVIEW_AGENT,
                            input,
                            lastRequest,
                            input,
                            context,
                            ACTION_REVIEW_AGENT,
                            METHOD_PERFORM_REVIEW,
                            TEMPLATE_WORKFLOW_REVIEW,
                            ToolContext.empty()
                    ),
                    AgentModels.ReviewRouting.class,
                    context
            );
            return AgentInterfaces.decorateRouting(
                    response,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_REVIEW_AGENT,
                    METHOD_PERFORM_REVIEW,
                    lastRequest
            );
        }

        @Action(canRerun = true, cost = 1.0)
        public AgentModels.ReviewRouting handleReviewInterrupt(
                AgentModels.InterruptRequest.ReviewInterruptRequest request,
                OperationContext context
        ) {
            ReviewNode originNode = workflowGraphService.requireReviewNode(context);
            AgentModels.ReviewRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.ReviewRequest.class);
            if (lastRequest == null) {
                workflowGraphService.emitErrorEvent(
                        originNode,
                        "Review request not found - cannot recover from interrupt."
                );
                throw new DegenerateLoopException(
                        "Review request not found - cannot recover from interrupt.",
                        METHOD_HANDLE_REVIEW_INTERRUPT,
                        AgentModels.InterruptRequest.ReviewInterruptRequest.class,
                        1
                );
            }
            request = AgentInterfaces.decorateRequest(
                    request,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_REVIEW_INTERRUPT,
                    METHOD_HANDLE_REVIEW_INTERRUPT,
                    lastRequest
            );
            String returnRoute = renderReturnRoute(
                    lastRequest.returnToOrchestratorCollector(),
                    lastRequest.returnToDiscoveryCollector(),
                    lastRequest.returnToPlanningCollector(),
                    lastRequest.returnToTicketCollector()
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.REVIEW_AGENT,
                    lastRequest,
                    lastRequest,
                    request,
                    context,
                    ACTION_REVIEW_INTERRUPT,
                    METHOD_HANDLE_REVIEW_INTERRUPT,
                    TEMPLATE_WORKFLOW_REVIEW
            );
            var routing = interruptService.handleInterrupt(
                    context,
                    request,
                    originNode,
                    TEMPLATE_WORKFLOW_REVIEW,
                    promptContext,
                    Map.of(
                            "content", lastRequest.content(),
                            "criteria", lastRequest.criteria(),
                            "returnRoute", returnRoute
                    ),
                    AgentModels.ReviewRouting.class
            );
            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_REVIEW_INTERRUPT,
                    METHOD_HANDLE_REVIEW_INTERRUPT,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.TicketCollectorRouting handleTicketCollectorBranch(
                AgentModels.TicketCollectorResult request,
                OperationContext context
        ) {
            AgentModels.TicketOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.TicketOrchestratorRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Ticket collector request not found - cannot handle ticket collector branch.",
                        METHOD_HANDLE_TICKET_COLLECTOR_BRANCH,
                        AgentModels.TicketCollectorResult.class,
                        1
                );
            }
            blackboardHistoryService.registerAndHideInput(context, METHOD_HANDLE_TICKET_COLLECTOR_BRANCH, request);

            // Get upstream curations from context for routing back
            AgentModels.TicketOrchestratorRequest lastTicketOrchestratorRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.TicketOrchestratorRequest.class);
            UpstreamContext.DiscoveryCollectorContext discoveryCuration = lastTicketOrchestratorRequest != null
                    ? lastTicketOrchestratorRequest.discoveryCuration()
                    : null;
            UpstreamContext.PlanningCollectorContext planningCuration = lastTicketOrchestratorRequest != null
                    ? lastTicketOrchestratorRequest.planningCuration()
                    : null;

            AgentModels.TicketCollectorRouting routing = switch (request.collectorDecision().decisionType()) {
                case ROUTE_BACK -> {
                    AgentModels.TicketOrchestratorRequest ticketRequest = AgentModels.TicketOrchestratorRequest.builder()
                            .goal(request.consolidatedOutput())
                            .discoveryCuration(discoveryCuration)
                            .planningCuration(planningCuration)
                            .build();

                    yield AgentModels.TicketCollectorRouting.builder()
                            .ticketRequest(ticketRequest)
                            .build();
                }
                case ADVANCE_PHASE -> {
                    AgentModels.OrchestratorCollectorRequest orchestratorCollectorRequest = AgentModels.OrchestratorCollectorRequest.builder()
                            .goal(request.consolidatedOutput())
                            .phase(request.collectorDecision().requestedPhase())
                            .discoveryCuration(discoveryCuration)
                            .planningCuration(planningCuration)
                            .ticketCuration(request.ticketCuration())
                            .build();

                    yield AgentModels.TicketCollectorRouting.builder()
                            .orchestratorCollectorRequest(orchestratorCollectorRequest)
                            .build();
                }
                case STOP -> {
                    AgentModels.OrchestratorCollectorRequest orchestratorCollectorRequest = AgentModels.OrchestratorCollectorRequest.builder()
                            .goal(request.consolidatedOutput())
                            .phase(request.collectorDecision().requestedPhase())
                            .discoveryCuration(discoveryCuration)
                            .planningCuration(planningCuration)
                            .ticketCuration(request.ticketCuration())
                            .build();

                    yield AgentModels.TicketCollectorRouting.builder()
                            .orchestratorCollectorRequest(orchestratorCollectorRequest)
                            .build();
                }
            };

            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    AGENT_NAME_TICKET_ROUTING,
                    ACTION_TICKET_COLLECTOR_ROUTING_BRANCH,
                    METHOD_HANDLE_TICKET_COLLECTOR_BRANCH,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.DiscoveryCollectorRouting handleDiscoveryCollectorBranch(
                AgentModels.DiscoveryCollectorResult request,
                OperationContext context
        ) {
            AgentModels.DiscoveryOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.DiscoveryOrchestratorRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Discovery collector request not found - cannot handle discovery collector branch.",
                        METHOD_HANDLE_DISCOVERY_COLLECTOR_BRANCH,
                        AgentModels.DiscoveryCollectorResult.class,
                        1
                );
            }
            blackboardHistoryService.registerAndHideInput(context, METHOD_HANDLE_DISCOVERY_COLLECTOR_BRANCH, request);
            AgentModels.DiscoveryCollectorRouting routing = switch (request.collectorDecision().decisionType()) {
                case ROUTE_BACK -> {
                    AgentModels.DiscoveryOrchestratorRequest discoveryRequest = AgentModels.DiscoveryOrchestratorRequest.builder()
                            .goal(request.consolidatedOutput())
                            .build();
                    yield AgentModels.DiscoveryCollectorRouting.builder()
                            .discoveryRequest(discoveryRequest)
                            .build();
                }
                case ADVANCE_PHASE -> {
                    // Pass the discovery curation directly to planning orchestrator
                    AgentModels.PlanningOrchestratorRequest planningRequest = AgentModels.PlanningOrchestratorRequest.builder()
                            .goal(request.consolidatedOutput())
                            .discoveryCuration(request.discoveryCollectorContext())
                            .build();
                    yield AgentModels.DiscoveryCollectorRouting.builder()
                            .planningRequest(planningRequest)
                            .build();
                }
                case STOP -> {
                    AgentModels.OrchestratorRequest orchestratorRequest = AgentModels.OrchestratorRequest.builder()
                            .goal(request.consolidatedOutput())
                            .phase(request.collectorDecision().requestedPhase())
                            .discoveryCuration(request.discoveryCollectorContext())
                            .build();

                    yield AgentModels.DiscoveryCollectorRouting.builder()
                            .orchestratorRequest(orchestratorRequest)
                            .build();
                }
            };
            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    AGENT_NAME_NONE,
                    ACTION_NONE,
                    METHOD_NONE,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.OrchestratorCollectorRouting handleOrchestratorCollectorBranch(
                AgentModels.OrchestratorCollectorResult request,
                OperationContext context
        ) {
            AgentModels.OrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Orchestrator collector request not found - cannot handle orchestrator collector branch.",
                        METHOD_HANDLE_ORCHESTRATOR_COLLECTOR_BRANCH,
                        AgentModels.OrchestratorCollectorResult.class,
                        1
                );
            }
            blackboardHistoryService.registerAndHideInput(context, METHOD_HANDLE_ORCHESTRATOR_COLLECTOR_BRANCH, request);
            AgentModels.OrchestratorCollectorRouting routing = switch (request.collectorDecision().decisionType()) {
                case ROUTE_BACK -> {
                    AgentModels.OrchestratorRequest orchestratorRequest = AgentModels.OrchestratorRequest.builder()
                            .goal(request.consolidatedOutput())
                            .phase(request.collectorDecision().requestedPhase())
                            .discoveryCuration(request.discoveryCollectorResult() != null
                                    ? request.discoveryCollectorResult().discoveryCollectorContext()
                                    : null)
                            .planningCuration(request.planningCollectorResult() != null
                                    ? request.planningCollectorResult().planningCuration()
                                    : null)
                            .ticketCuration(request.ticketCollectorResult() != null
                                    ? request.ticketCollectorResult().ticketCuration()
                                    : null)
                            .build();

                    yield AgentModels.OrchestratorCollectorRouting.builder()
                            .orchestratorRequest(orchestratorRequest)
                            .build();
                }
                case ADVANCE_PHASE, STOP -> {
                    AgentModels.OrchestratorCollectorResult collectorResult
                            = new AgentModels.OrchestratorCollectorResult(request.consolidatedOutput(), request.collectorDecision());
                    yield AgentModels.OrchestratorCollectorRouting.builder()
                            .collectorResult(collectorResult)
                            .build();
                }
            };
            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    AGENT_NAME_NONE,
                    ACTION_NONE,
                    METHOD_NONE,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.PlanningCollectorRouting handlePlanningCollectorBranch(
                AgentModels.PlanningCollectorResult request,
                OperationContext context
        ) {
            AgentModels.PlanningOrchestratorRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningOrchestratorRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Planning collector request not found - cannot handle planning collector branch.",
                        METHOD_HANDLE_PLANNING_COLLECTOR_BRANCH,
                        AgentModels.PlanningCollectorResult.class,
                        1
                );
            }
            blackboardHistoryService.registerAndHideInput(context, METHOD_HANDLE_PLANNING_COLLECTOR_BRANCH, request);
            // Get discovery curation from prior planning orchestrator request
            AgentModels.PlanningOrchestratorRequest lastPlanningOrchestratorRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningOrchestratorRequest.class);
            UpstreamContext.DiscoveryCollectorContext discoveryCuration = lastPlanningOrchestratorRequest != null
                    ? lastPlanningOrchestratorRequest.discoveryCuration()
                    : null;

            AgentModels.PlanningCollectorRouting routing = switch (request.collectorDecision().decisionType()) {
                case ROUTE_BACK -> {
                    // Pass discovery curation back when routing back
                    AgentModels.PlanningOrchestratorRequest planningRequest = AgentModels.PlanningOrchestratorRequest.builder()
                            .goal(request.consolidatedOutput())
                            .discoveryCuration(discoveryCuration)
                            .build();

                    yield AgentModels.PlanningCollectorRouting.builder()
                            .planningRequest(planningRequest)
                            .build();
                }
                case ADVANCE_PHASE -> {
                    // Pass both discovery and planning curations to ticket orchestrator
                    AgentModels.TicketOrchestratorRequest ticketRequest = AgentModels.TicketOrchestratorRequest.builder()
                            .goal(request.consolidatedOutput())
                            .discoveryCuration(discoveryCuration)
                            .planningCuration(request.planningCuration())
                            .build();

                    yield AgentModels.PlanningCollectorRouting.builder()
                            .ticketOrchestratorRequest(ticketRequest)
                            .build();
                }
                case STOP -> {
                    AgentModels.OrchestratorCollectorRequest orchestratorCollectorRequest = AgentModels.OrchestratorCollectorRequest.builder()
                            .goal(request.consolidatedOutput())
                            .phase(request.collectorDecision().requestedPhase())
                            .discoveryCuration(discoveryCuration)
                            .planningCuration(request.planningCuration())
                            .build();

                    yield AgentModels.PlanningCollectorRouting.builder()
                            .orchestratorCollectorRequest(orchestratorCollectorRequest)
                            .build();
                }
            };
            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    AGENT_NAME_NONE,
                    ACTION_NONE,
                    METHOD_NONE,
                    lastRequest
            );
        }

        @Action(canRerun = true, cost = 1.0)
        public AgentModels.MergerRouting handleMergerInterrupt(
                AgentModels.InterruptRequest.MergerInterruptRequest request,
                OperationContext context
        ) {
            MergeNode originNode = workflowGraphService.requireMergeNode(context);
            AgentModels.MergerRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.MergerRequest.class);
            if (lastRequest == null) {
                workflowGraphService.emitErrorEvent(
                        originNode,
                        "Merger request not found - cannot recover from interrupt."
                );
                throw new DegenerateLoopException(
                        "Merger request not found - cannot recover from interrupt.",
                        METHOD_HANDLE_MERGER_INTERRUPT,
                        AgentModels.InterruptRequest.MergerInterruptRequest.class,
                        1
                );
            }
            request = AgentInterfaces.decorateRequest(
                    request,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_MERGER_INTERRUPT,
                    METHOD_HANDLE_MERGER_INTERRUPT,
                    lastRequest
            );
            String returnRoute = renderReturnRoute(
                    lastRequest.returnToOrchestratorCollector(),
                    lastRequest.returnToDiscoveryCollector(),
                    lastRequest.returnToPlanningCollector(),
                    lastRequest.returnToTicketCollector()
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.MERGER_AGENT,
                    lastRequest,
                    lastRequest,
                    request,
                    context,
                    ACTION_MERGER_INTERRUPT,
                    METHOD_HANDLE_MERGER_INTERRUPT,
                    TEMPLATE_WORKFLOW_MERGER
            );
            var routing = interruptService.handleInterrupt(
                    context,
                    request,
                    originNode,
                    TEMPLATE_WORKFLOW_MERGER,
                    promptContext,
                    Map.of(
                            "mergeContext", lastRequest.mergeContext(),
                            "mergeSummary", lastRequest.mergeSummary(),
                            "conflictFiles", lastRequest.conflictFiles(),
                            "returnRoute", returnRoute
                    ),
                    AgentModels.MergerRouting.class
            );
            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_MERGER_INTERRUPT,
                    METHOD_HANDLE_MERGER_INTERRUPT,
                    lastRequest
            );
        }

        private <T> T runSubProcess(
                ActionContext context,
                Object request,
                com.embabel.agent.core.Agent agent,
                Class<T> outputClass
        ) {
            if (request == null) {
                return null;
            }
            context.addObject(request);
            T result = context.asSubProcess(outputClass, agent);
            return result;
        }

        private static String resolveDiscoveryGoal(
                ActionContext context,
                AgentModels.DiscoveryAgentRequests input
        ) {
            String resolved = input != null
                    ? input.prettyPrint(new AgentContext.AgentSerializationCtx.GoalResolutionSerialization())
                    : "";
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
            AgentModels.DiscoveryOrchestratorRequest orchestratorRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.DiscoveryOrchestratorRequest.class);
            return firstNonBlank(
                    orchestratorRequest != null ? orchestratorRequest.goal() : null,
                    resolveRootGoal(context));
        }

        private static String resolvePlanningGoal(
                ActionContext context,
                AgentModels.PlanningAgentRequests input
        ) {
            String resolved = input != null
                    ? input.prettyPrint(new AgentContext.AgentSerializationCtx.GoalResolutionSerialization())
                    : "";
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
            AgentModels.PlanningOrchestratorRequest orchestratorRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningOrchestratorRequest.class);
            return firstNonBlank(
                    orchestratorRequest != null ? orchestratorRequest.goal() : null,
                    resolveRootGoal(context));
        }

        private static String resolveTicketGoal(
                ActionContext context,
                AgentModels.TicketAgentRequests input
        ) {
            String resolved = input != null
                    ? input.prettyPrint(new AgentContext.AgentSerializationCtx.GoalResolutionSerialization())
                    : "";
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
            AgentModels.TicketOrchestratorRequest orchestratorRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.TicketOrchestratorRequest.class);
            return firstNonBlank(
                    orchestratorRequest != null ? orchestratorRequest.goal() : null,
                    resolveRootGoal(context));
        }

        private static String resolveRootGoal(ActionContext context) {
            AgentModels.OrchestratorRequest rootRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorRequest.class);
            return rootRequest != null ? rootRequest.goal() : "Continue workflow";
        }

        private static String resolveMergeSummary(
                AgentModels.MergerRequest request,
                AgentModels.MergerRouting routing
        ) {
            String requestSummary = request != null
                    ? request.prettyPrint(new AgentContext.AgentSerializationCtx.MergeSummarySerialization())
                    : "";
            AgentModels.MergerAgentResult result = routing != null ? routing.mergerResult() : null;
            String resultSummary = result != null
                    ? result.prettyPrint(new AgentContext.AgentSerializationCtx.MergeSummarySerialization())
                    : "";
            return firstNonBlank(requestSummary, resultSummary);
        }

        private static String firstNonBlank(String... values) {
            if (values == null) {
                return "";
            }
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return "";
        }


    }

    @Agent(
            name = WORKFLOW_TICKET_DISPATCH_SUBAGENT,
            description = "Runs ticket agent request in a subprocess"
    )
    @RequiredArgsConstructor
    class TicketDispatchSubagent implements AgentInterfaces {
        private final EventBus eventBus;
        private final PromptContextFactory promptContextFactory;
        private final InterruptService interruptService;
        private final com.hayden.multiagentide.service.LlmRunner llmRunner;
        private final WorkflowGraphService workflowGraphService;
        private final BlackboardHistoryService blackboardHistoryService;

        @Autowired(required = false)
        private List<DispatchedAgentResultDecorator> resultDecorators;

        @Autowired(required = false)
        private List<DispatchedAgentRequestDecorator> requestDecorators;

        @Autowired(required = false)
        private List<PromptContextDecorator> promptContextDecorators;

        @Autowired(required = false)
        private List<ToolContextDecorator> toolContextDecorators;

        private ToolContext buildToolContext(
                AgentType agentType,
                AgentModels.AgentRequest contextRequest,
                AgentModels.AgentRequest previousRequest,
                AgentModels.AgentRequest currentRequest,
                OperationContext context,
                String actionName,
                String methodName,
                String templateName,
                ToolContext toolContext
        ) {
            return AgentInterfaces.decorateToolContext(
                    toolContext,
                    currentRequest,
                    previousRequest,
                    context,
                    toolContextDecorators,
                    multiAgentAgentName(),
                    actionName,
                    methodName
            );
        }

        @Override
        public String multiAgentAgentName() {
            return WORKFLOW_TICKET_DISPATCH_SUBAGENT;
        }

        private PromptContext buildPromptContext(
                AgentType agentType,
                AgentModels.AgentRequest contextRequest,
                AgentModels.AgentRequest previousRequest,
                AgentModels.AgentRequest currentRequest,
                OperationContext context,
                String actionName,
                String methodName,
                String templateName
        ) {
            BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);
            PromptContext promptContext = promptContextFactory.build(
                    agentType,
                    contextRequest,
                    previousRequest,
                    currentRequest,
                    history,
                    templateName
            );
            return AgentInterfaces.decoratePromptContext(
                    promptContext,
                    context,
                    promptContextDecorators,
                    multiAgentAgentName(),
                    actionName,
                    methodName,
                    previousRequest,
                    currentRequest
            );
        }

        @Action
        @AchievesGoal(description = "Handle context agent request")
        public AgentModels.TicketAgentResult ranTicketAgentResult(
                AgentModels.TicketAgentResult input,
                OperationContext context
        ) {
            AgentModels.TicketAgentRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.TicketAgentRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Ticket agent request not found - cannot record ticket agent result.",
                        METHOD_RAN_TICKET_AGENT_RESULT,
                        AgentModels.TicketAgentResult.class,
                        1
                );
            }
            return input;
        }

        @Action
        public AgentModels.TicketAgentRouting transitionToInterruptState(
                AgentModels.InterruptRequest.TicketAgentInterruptRequest interruptRequest,
                OperationContext context
        ) {
            AgentModels.TicketAgentRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.TicketAgentRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Ticket agent request not found - cannot recover from interrupt.",
                        METHOD_TRANSITION_TO_INTERRUPT_STATE,
                        AgentModels.InterruptRequest.TicketAgentInterruptRequest.class,
                        1
                );
            }
            interruptRequest = AgentInterfaces.decorateRequest(
                    interruptRequest,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_TICKET_AGENT_INTERRUPT,
                    METHOD_TRANSITION_TO_INTERRUPT_STATE,
                    lastRequest
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.TICKET_AGENT,
                    lastRequest,
                    lastRequest,
                    interruptRequest,
                    context,
                    ACTION_TICKET_AGENT_INTERRUPT,
                    METHOD_TRANSITION_TO_INTERRUPT_STATE,
                    TEMPLATE_WORKFLOW_TICKET_AGENT
            );
            GraphNode originNode = workflowGraphService.findNodeForContext(context)
                    .orElseGet(() -> workflowGraphService.requireTicketOrchestrator(context));
            AgentModels.TicketAgentRouting routing = interruptService.handleInterrupt(
                    context,
                    interruptRequest,
                    originNode,
                    TEMPLATE_WORKFLOW_TICKET_AGENT,
                    promptContext,
                    Map.of(
                            "ticketDetails", lastRequest.ticketDetails() != null ? lastRequest.ticketDetails() : "",
                            "ticketDetailsFilePath", lastRequest.ticketDetailsFilePath() != null ? lastRequest.ticketDetailsFilePath() : ""
                    ),
                    AgentModels.TicketAgentRouting.class
            );
            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_TICKET_AGENT_INTERRUPT,
                    METHOD_TRANSITION_TO_INTERRUPT_STATE,
                    lastRequest
            );
        }

        @Action
        public AgentModels.TicketAgentRouting runTicketAgent(
                AgentModels.TicketAgentRequest input,
                OperationContext context
        ) {
            AgentModels.TicketAgentRequests lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.TicketAgentRequests.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Ticket agent request not found - cannot run ticket agent.",
                        METHOD_RUN_TICKET_AGENT,
                        AgentModels.TicketAgentRequest.class,
                        1
                );
            }

            input = AgentInterfaces.decorateRequest(
                    input,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_TICKET_AGENT,
                    METHOD_RUN_TICKET_AGENT,
                    lastRequest
            );

            PromptContext promptContext = buildPromptContext(
                    AgentType.TICKET_AGENT,
                    input,
                    lastRequest,
                    input,
                    context,
                    ACTION_TICKET_AGENT,
                    METHOD_RUN_TICKET_AGENT,
                    TEMPLATE_WORKFLOW_TICKET_AGENT
            );

            AgentModels.TicketAgentRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_TICKET_AGENT,
                    promptContext,
                    Map.of(
                            "ticketDetails", input.ticketDetails() != null ? input.ticketDetails() : "",
                            "ticketDetailsFilePath", input.ticketDetailsFilePath() != null ? input.ticketDetailsFilePath() : ""
                    ),
                    buildToolContext(
                            AgentType.TICKET_AGENT,
                            input,
                            lastRequest,
                            input,
                            context,
                            ACTION_TICKET_AGENT,
                            METHOD_RUN_TICKET_AGENT,
                            TEMPLATE_WORKFLOW_TICKET_AGENT,
                            ToolContext.empty()
                    ),
                    AgentModels.TicketAgentRouting.class,
                    context
            );

            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_TICKET_AGENT,
                    METHOD_RUN_TICKET_AGENT,
                    lastRequest
            );
        }
    }

    @Agent(
            name = WORKFLOW_PLANNING_DISPATCH_SUBAGENT,
            description = "Runs planning agent request in a subprocess"
    )
    @RequiredArgsConstructor
    class PlanningDispatchSubagent implements AgentInterfaces {

        private final EventBus eventBus;
        private final PromptContextFactory promptContextFactory;
        private final RequestEnrichment requestEnrichment;
        private final InterruptService interruptService;
        private final com.hayden.multiagentide.service.LlmRunner llmRunner;
        private final WorkflowGraphService workflowGraphService;
        private final BlackboardHistoryService blackboardHistoryService;

        @Autowired(required = false)
        private List<DispatchedAgentResultDecorator> resultDecorators;

        @Autowired(required = false)
        private List<DispatchedAgentRequestDecorator> requestDecorators;

        @Autowired(required = false)
        private List<PromptContextDecorator> promptContextDecorators;

        @Autowired(required = false)
        private List<ToolContextDecorator> toolContextDecorators;

        private ToolContext buildToolContext(
                AgentType agentType,
                AgentModels.AgentRequest contextRequest,
                AgentModels.AgentRequest previousRequest,
                AgentModels.AgentRequest currentRequest,
                OperationContext context,
                String actionName,
                String methodName,
                String templateName,
                ToolContext toolContext
        ) {
            return AgentInterfaces.decorateToolContext(
                    toolContext,
                    currentRequest,
                    previousRequest,
                    context,
                    toolContextDecorators,
                    multiAgentAgentName(),
                    actionName,
                    methodName
            );
        }

        @Override
        public String multiAgentAgentName() {
            return WORKFLOW_PLANNING_DISPATCH_SUBAGENT;
        }

        private PromptContext buildPromptContext(
                AgentType agentType,
                AgentModels.AgentRequest contextRequest,
                AgentModels.AgentRequest previousRequest,
                AgentModels.AgentRequest currentRequest,
                OperationContext context,
                String actionName,
                String methodName,
                String templateName
        ) {
            BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);
            PromptContext promptContext = promptContextFactory.build(
                    agentType,
                    contextRequest,
                    previousRequest,
                    currentRequest,
                    history,
                    templateName
            );
            return AgentInterfaces.decoratePromptContext(
                    promptContext,
                    context,
                    promptContextDecorators,
                    multiAgentAgentName(),
                    actionName,
                    methodName,
                    previousRequest,
                    currentRequest
            );
        }

        @Action
        @AchievesGoal(description = "Handle context agent request")
        public AgentModels.PlanningAgentResult ranPlanningAgent(
                AgentModels.PlanningAgentResult input,
                OperationContext context
        ) {
            AgentModels.PlanningAgentRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningAgentRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Planning agent request not found - cannot record planning agent result.",
                        METHOD_RAN_PLANNING_AGENT,
                        AgentModels.PlanningAgentResult.class,
                        1
                );
            }
            return input;
        }

        @Action(canRerun = true)
        public AgentModels.PlanningAgentRouting transitionToInterruptState(
                AgentModels.InterruptRequest.PlanningAgentInterruptRequest interruptRequest,
                OperationContext context
        ) {
            AgentModels.PlanningAgentRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningAgentRequest.class);

            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Planning agent request not found - cannot recover from interrupt.",
                        METHOD_TRANSITION_TO_INTERRUPT_STATE,
                        AgentModels.InterruptRequest.PlanningAgentInterruptRequest.class,
                        1
                );
            }
            interruptRequest = AgentInterfaces.decorateRequest(
                    interruptRequest,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_PLANNING_AGENT_INTERRUPT,
                    METHOD_TRANSITION_TO_INTERRUPT_STATE,
                    lastRequest
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.PLANNING_AGENT,
                    lastRequest,
                    lastRequest,
                    interruptRequest,
                    context,
                    ACTION_PLANNING_AGENT_INTERRUPT,
                    METHOD_TRANSITION_TO_INTERRUPT_STATE,
                    TEMPLATE_WORKFLOW_PLANNING_AGENT
            );
            GraphNode originNode = workflowGraphService.findNodeForContext(context)
                    .orElseGet(() -> workflowGraphService.requirePlanningOrchestrator(context));
            AgentModels.PlanningAgentRouting routing = interruptService.handleInterrupt(
                    context,
                    interruptRequest,
                    originNode,
                    TEMPLATE_WORKFLOW_PLANNING_AGENT,
                    promptContext,
                    Map.of("goal", Objects.toString(lastRequest.goal(), "")),
                    AgentModels.PlanningAgentRouting.class
            );
            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_PLANNING_AGENT_INTERRUPT,
                    METHOD_TRANSITION_TO_INTERRUPT_STATE,
                    lastRequest
            );
        }

        @Action(canRerun = true)
        public AgentModels.PlanningAgentRouting runPlanningAgent(
                AgentModels.PlanningAgentRequest input,
                OperationContext context
        ) {
            AgentModels.PlanningAgentRequests lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.PlanningAgentRequests.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Planning agent request not found - cannot run planning agent.",
                        METHOD_RUN_PLANNING_AGENT,
                        AgentModels.PlanningAgentRequest.class,
                        1
                );
            }
            input = AgentInterfaces.decorateRequest(
                    input,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_PLANNING_AGENT,
                    METHOD_RUN_PLANNING_AGENT,
                    lastRequest
            );

            PromptContext promptContext = buildPromptContext(
                    AgentType.PLANNING_AGENT,
                    input,
                    lastRequest,
                    input,
                    context,
                    ACTION_PLANNING_AGENT,
                    METHOD_RUN_PLANNING_AGENT,
                    TEMPLATE_WORKFLOW_PLANNING_AGENT
            );

            AgentModels.PlanningAgentRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_PLANNING_AGENT,
                    promptContext,
                    Map.of("goal", input.goal()),
                    buildToolContext(
                            AgentType.PLANNING_AGENT,
                            input,
                            lastRequest,
                            input,
                            context,
                            ACTION_PLANNING_AGENT,
                            METHOD_RUN_PLANNING_AGENT,
                            TEMPLATE_WORKFLOW_PLANNING_AGENT,
                            ToolContext.empty()
                    ),
                    AgentModels.PlanningAgentRouting.class,
                    context
            );

            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_PLANNING_AGENT,
                    METHOD_RUN_PLANNING_AGENT,
                    lastRequest
            );
        }
    }

    @Agent(
            name = WORKFLOW_DISCOVERY_DISPATCH_SUBAGENT,
            description = "Runs discovery agent request in a subprocess"
    )
    @RequiredArgsConstructor
    class DiscoveryDispatchSubagent implements AgentInterfaces {

        private final EventBus eventBus;
        private final PromptContextFactory promptContextFactory;
        private final InterruptService interruptService;
        private final com.hayden.multiagentide.service.LlmRunner llmRunner;
        private final WorkflowGraphService workflowGraphService;
        private final BlackboardHistoryService blackboardHistoryService;

        @Autowired(required = false)
        private List<DispatchedAgentResultDecorator> resultDecorators;

        @Autowired(required = false)
        private List<DispatchedAgentRequestDecorator> requestDecorators;

        @Autowired(required = false)
        private List<PromptContextDecorator> promptContextDecorators;

        @Autowired(required = false)
        private List<ToolContextDecorator> toolContextDecorators;

        private ToolContext buildToolContext(
                AgentType agentType,
                AgentModels.AgentRequest contextRequest,
                AgentModels.AgentRequest previousRequest,
                AgentModels.AgentRequest currentRequest,
                OperationContext context,
                String actionName,
                String methodName,
                String templateName,
                ToolContext toolContext
        ) {
            return AgentInterfaces.decorateToolContext(
                    toolContext,
                    currentRequest,
                    previousRequest,
                    context,
                    toolContextDecorators,
                    multiAgentAgentName(),
                    actionName,
                    methodName
            );
        }

        @Override
        public String multiAgentAgentName() {
            return WORKFLOW_DISCOVERY_DISPATCH_SUBAGENT;
        }

        private PromptContext buildPromptContext(
                AgentType agentType,
                AgentModels.AgentRequest contextRequest,
                AgentModels.AgentRequest previousRequest,
                AgentModels.AgentRequest currentRequest,
                OperationContext context,
                String actionName,
                String methodName,
                String templateName
        ) {
            BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);
            PromptContext promptContext = promptContextFactory.build(
                    agentType,
                    contextRequest,
                    previousRequest,
                    currentRequest,
                    history,
                    templateName
            );
            return AgentInterfaces.decoratePromptContext(
                    promptContext,
                    context,
                    promptContextDecorators,
                    multiAgentAgentName(),
                    actionName,
                    methodName,
                    previousRequest,
                    currentRequest
            );
        }

        @Action
        @AchievesGoal(description = "Handle context agent request")
        public AgentModels.DiscoveryAgentResult ranDiscoveryAgent(
                AgentModels.DiscoveryAgentResult input,
                OperationContext context
        ) {
//            do an event emission of complete
            return input;
        }

        @Action
        public AgentModels.DiscoveryAgentRouting transitionToInterruptState(
                AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest interruptRequest,
                OperationContext context
        ) {

            AgentModels.DiscoveryAgentRequest lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.DiscoveryAgentRequest.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Discovery agent request not found - cannot recover from interrupt.",
                        METHOD_TRANSITION_TO_INTERRUPT_STATE,
                        AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest.class,
                        1
                );
            }
            interruptRequest = AgentInterfaces.decorateRequest(
                    interruptRequest,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_DISCOVERY_AGENT_INTERRUPT,
                    METHOD_TRANSITION_TO_INTERRUPT_STATE,
                    lastRequest
            );
            PromptContext promptContext = buildPromptContext(
                    AgentType.DISCOVERY_AGENT,
                    lastRequest,
                    lastRequest,
                    interruptRequest,
                    context,
                    ACTION_DISCOVERY_AGENT_INTERRUPT,
                    METHOD_TRANSITION_TO_INTERRUPT_STATE,
                    TEMPLATE_WORKFLOW_DISCOVERY_AGENT
            );
            GraphNode originNode = workflowGraphService.findNodeForContext(context)
                    .orElseGet(() -> workflowGraphService.requireDiscoveryOrchestrator(context));
            AgentModels.DiscoveryAgentRouting routing = interruptService.handleInterrupt(
                    context,
                    interruptRequest,
                    originNode,
                    TEMPLATE_WORKFLOW_DISCOVERY_AGENT,
                    promptContext,
                    Map.of(
                            "goal", Objects.toString(lastRequest.goal(), ""),
                            "subdomainFocus", Objects.toString(lastRequest.subdomainFocus(), "")
                    ),
                    AgentModels.DiscoveryAgentRouting.class
            );
            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_DISCOVERY_AGENT_INTERRUPT,
                    METHOD_TRANSITION_TO_INTERRUPT_STATE,
                    lastRequest
            );
        }

        @Action
        public AgentModels.DiscoveryAgentRouting runDiscoveryAgent(
                AgentModels.DiscoveryAgentRequest input,
                OperationContext context
        ) {
            AgentModels.DiscoveryAgentRequests lastRequest =
                    BlackboardHistory.getLastFromHistory(context, AgentModels.DiscoveryAgentRequests.class);
            if (lastRequest == null) {
                throw new DegenerateLoopException(
                        "Discovery agent request not found - cannot run discovery agent.",
                        METHOD_RUN_DISCOVERY_AGENT,
                        AgentModels.DiscoveryAgentRequest.class,
                        1
                );
            }

            input = AgentInterfaces.decorateRequest(
                    input,
                    context,
                    requestDecorators,
                    multiAgentAgentName(),
                    ACTION_DISCOVERY_AGENT,
                    METHOD_RUN_DISCOVERY_AGENT,
                    lastRequest
            );

            PromptContext promptContext = buildPromptContext(
                    AgentType.DISCOVERY_AGENT,
                    input,
                    lastRequest,
                    input,
                    context,
                    ACTION_DISCOVERY_AGENT,
                    METHOD_RUN_DISCOVERY_AGENT,
                    TEMPLATE_WORKFLOW_DISCOVERY_AGENT
            );

            AgentModels.DiscoveryAgentRouting routing = llmRunner.runWithTemplate(
                    TEMPLATE_WORKFLOW_DISCOVERY_AGENT,
                    promptContext,
                    Map.of("goal", input.goal(), "subdomainFocus", input.subdomainFocus()),
                    buildToolContext(
                            AgentType.DISCOVERY_AGENT,
                            input,
                            lastRequest,
                            input,
                            context,
                            ACTION_DISCOVERY_AGENT,
                            METHOD_RUN_DISCOVERY_AGENT,
                            TEMPLATE_WORKFLOW_DISCOVERY_AGENT,
                            ToolContext.empty()
                    ),
                    AgentModels.DiscoveryAgentRouting.class,
                    context
            );

            return AgentInterfaces.decorateRouting(
                    routing,
                    context,
                    resultDecorators,
                    multiAgentAgentName(),
                    ACTION_DISCOVERY_AGENT,
                    METHOD_RUN_DISCOVERY_AGENT,
                    lastRequest
            );
        }

    }

    static PromptContext decoratePromptContext(
            PromptContext promptContext,
            OperationContext context,
            List<? extends PromptContextDecorator> decorators,
            String agentName,
            String actionName,
            String methodName,
            Artifact.AgentModel lastRequest,
            Artifact.AgentModel agentRequest
    ) {
        if (promptContext == null || decorators == null || decorators.isEmpty()) {
            return promptContext;
        }
        DecoratorContext decoratorContext = new DecoratorContext(
                context, agentName, actionName, methodName, lastRequest, agentRequest
        );
        List<? extends PromptContextDecorator> sortedDecorators = decorators.stream()
                .filter(d -> d != null)
                .sorted(Comparator.comparingInt(PromptContextDecorator::order))
                .toList();
        PromptContext decorated = promptContext;
        for (PromptContextDecorator decorator : sortedDecorators) {
            decorated = decorator.decorate(decorated, decoratorContext);
        }
        return decorated;
    }

    static <T extends AgentModels.Routing> T decorateRouting(
            T routing,
            OperationContext context,
            List<? extends ResultDecorator> decorators,
            String agentName,
            String actionName,
            Artifact.AgentModel lastRequest
    ) {
        return decorateRouting(
                routing,
                context,
                decorators,
                agentName,
                actionName,
                "",
                lastRequest
        );
    }

    static <T extends AgentModels.Routing> T decorateRouting(
            T routing,
            OperationContext context,
            List<? extends ResultDecorator> decorators,
            String agentName,
            String actionName,
            String methodName,
            Artifact.AgentModel lastRequest
    ) {
        if (routing == null || decorators == null || decorators.isEmpty()) {
            return routing;
        }
        DecoratorContext decoratorContext = new DecoratorContext(
                context, agentName, actionName, methodName, lastRequest, null
        );
        T decorated = routing;
        // Sort decorators by order (lower values first)
        List<? extends ResultDecorator> sortedDecorators = decorators.stream()
                .filter(d -> d != null)
                .sorted(Comparator.comparingInt(ResultDecorator::order))
                .toList();
        for (ResultDecorator decorator : sortedDecorators) {
            decorated = decorator.decorate(decorated, decoratorContext);
        }
        return decorated;
    }

    static <T extends AgentModels.AgentRequest> T decorateRequestResult(
            T result,
            OperationContext context,
            List<? extends ResultDecorator> decorators,
            String agentName,
            String actionName,
            String methodName,
            Artifact.AgentModel lastRequest
    ) {
        if (result == null || decorators == null || decorators.isEmpty()) {
            return result;
        }
        DecoratorContext decoratorContext = new DecoratorContext(
                context, agentName, actionName, methodName, lastRequest, result
        );
        List<? extends ResultDecorator> sortedDecorators = decorators.stream()
                .filter(d -> d != null)
                .sorted(Comparator.comparingInt(ResultDecorator::order))
                .toList();
        T decorated = result;
        for (ResultDecorator decorator : sortedDecorators) {
            decorated = decorator.decorateRequestResult(decorated, decoratorContext);
        }
        return decorated;
    }

    /**
     * @deprecated Use {@link #decorateRouting(AgentModels.Routing, OperationContext, List, String, String, Artifact.AgentModel)} instead.
     * This overload exists for backwards compatibility and does not emit action completed events.
     */
    @Deprecated
    static <T extends AgentModels.Routing> T decorateRouting(
            T routing,
            OperationContext context,
            List<ResultDecorator> decorators,
            Artifact.AgentModel lastRequest
    ) {
        return decorateRouting(routing, context, decorators, AGENT_NAME_NONE, ACTION_NONE, METHOD_NONE, lastRequest);
    }

    static ToolContext decorateToolContext(ToolContext toolContext,
                                           AgentModels.AgentRequest enrichedRequest,
                                           AgentModels.AgentRequest lastRequest,
                                           OperationContext context,
                                           List<ToolContextDecorator> decorators,
                                           String agentName,
                                           String actionName,
                                           String methodName) {

        if (toolContext == null)
            toolContext = ToolContext.of();
        if (decorators == null || decorators.isEmpty()) {
            return toolContext;
        }
        DecoratorContext decoratorContext = new DecoratorContext(
                context, agentName, actionName, methodName, lastRequest, enrichedRequest);

        for (var d : decorators) {
            toolContext = d.decorate(toolContext, decoratorContext);
        }

        return toolContext;

    }

    static <T extends AgentModels.AgentResult> T decorateFinalResult(
            T finalResult,
            AgentModels.AgentRequest enrichedRequest,
            AgentModels.AgentRequest lastRequest,
            OperationContext context,
            List<FinalResultDecorator> decorators,
            String agentName,
            String actionName,
            String methodName
    ) {
        if (finalResult == null || decorators == null || decorators.isEmpty()) {
            return finalResult;
        }
        DecoratorContext decoratorContext = new DecoratorContext(
                context, agentName, actionName, methodName, lastRequest, enrichedRequest);
        FinalResultDecorator.FinalResultDecoratorContext finalResultDecoratorContext
                = new FinalResultDecorator.FinalResultDecoratorContext(enrichedRequest, decoratorContext);
        // Sort decorators by order (lower values first)
        List<FinalResultDecorator> sortedDecorators = decorators.stream()
                .filter(d -> d != null)
                .sorted(Comparator.comparingInt(FinalResultDecorator::order))
                .toList();
        T decorated = finalResult;
        for (FinalResultDecorator decorator : sortedDecorators) {
            decorated = decorator.decorateFinalResult(decorated, finalResultDecoratorContext);
        }
        return decorated;
    }

    static <T extends AgentModels.AgentRequest> T decorateRequest(
            T request,
            OperationContext context,
            List<? extends RequestDecorator> decorators,
            String agentName,
            String actionName,
            String methodName,
            AgentModels.AgentRequest lastRequest
    ) {
        if (request == null || decorators == null || decorators.isEmpty()) {
            return request;
        }
        DecoratorContext decoratorContext = new DecoratorContext(
                context, agentName, actionName, methodName, lastRequest, request
        );
        // Sort decorators by order (lower values first)
        List<? extends RequestDecorator> sortedDecorators = decorators.stream()
                .filter(d -> d != null)
                .sorted(Comparator.comparingInt(RequestDecorator::order))
                .toList();
        T decorated = request;
        for (RequestDecorator decorator : sortedDecorators) {
            decorated = decorator.decorate(decorated, decoratorContext);
        }
        return decorated;
    }

    /**
     * Decorates a ResultsRequest (TicketAgentResults, PlanningAgentResults, DiscoveryAgentResults)
     * using the provided ResultsRequestDecorator chain.
     * 
     * Used for Phase 2 of the worktree merge flow: merging child agent worktrees
     * back to the parent (trunk) worktree before routing decisions.
     * 
     * @param resultsRequest The results request to decorate
     * @param context The operation context
     * @param decorators The list of decorators to apply
     * @param agentName The agent name for context
     * @param actionName The action name for context
     * @param methodName The method name for context
     * @param lastRequest The last request for context
     * @return The decorated results request
     */
    static <T extends AgentModels.ResultsRequest> T decorateResultsRequest(
            T resultsRequest,
            OperationContext context,
            List<? extends ResultsRequestDecorator> decorators,
            String agentName,
            String actionName,
            String methodName,
            AgentModels.AgentRequest lastRequest
    ) {
        if (resultsRequest == null || decorators == null || decorators.isEmpty()) {
            return resultsRequest;
        }
        DecoratorContext decoratorContext = new DecoratorContext(
                context, agentName, actionName, methodName, lastRequest, resultsRequest
        );
        // Sort decorators by order (lower values first)
        List<? extends ResultsRequestDecorator> sortedDecorators = decorators.stream()
                .filter(d -> d != null)
                .sorted(Comparator.comparingInt(ResultsRequestDecorator::order))
                .toList();
        T decorated = resultsRequest;
        for (ResultsRequestDecorator decorator : sortedDecorators) {
            decorated = decorator.decorate(decorated, decoratorContext);
        }
        return decorated;
    }

    static String renderReturnRoute(
            AgentModels.OrchestratorCollectorRequest orchestratorCollector,
            AgentModels.DiscoveryCollectorRequest discoveryCollector,
            AgentModels.PlanningCollectorRequest planningCollector,
            AgentModels.TicketCollectorRequest ticketCollector
    ) {
        if (orchestratorCollector != null) {
            return "orchestratorCollectorRequest(goal=" + orchestratorCollector.goal() + ", phase=" + orchestratorCollector.phase() + ")";
        }
        if (discoveryCollector != null) {
            return "discoveryCollectorRequest(goal=" + discoveryCollector.goal() + ")";
        }
        if (planningCollector != null) {
            return "planningCollectorRequest(goal=" + planningCollector.goal() + ")";
        }
        if (ticketCollector != null) {
            return "ticketCollectorRequest(goal=" + ticketCollector.goal() + ")";
        }
        return RETURN_ROUTE_NONE;
    }

}
