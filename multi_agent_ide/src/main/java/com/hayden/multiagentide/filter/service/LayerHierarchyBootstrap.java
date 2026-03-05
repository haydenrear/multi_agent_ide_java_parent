package com.hayden.multiagentide.filter.service;

import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.filter.repository.LayerEntity;
import com.hayden.multiagentide.filter.repository.LayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Bootstraps the filter layer hierarchy on startup.
 *
 * Hierarchy:
 *   controller (CONTROLLER, depth 0)
 *   ├── controller-ui-event-poll (CONTROLLER_UI_EVENT_POLL, depth 1)
 *   └── workflow-agent (WORKFLOW_AGENT, depth 1)
 *       ├── [22 WorkflowAgent @Action methods] (WORKFLOW_AGENT_ACTION, depth 2)
 *       ├── discovery-dispatch-subagent (WORKFLOW_AGENT, depth 2)
 *       │   └── [3 actions] (WORKFLOW_AGENT_ACTION, depth 3)
 *       ├── planning-dispatch-subagent (WORKFLOW_AGENT, depth 2)
 *       │   └── [3 actions] (WORKFLOW_AGENT_ACTION, depth 3)
 *       └── ticket-dispatch-subagent (WORKFLOW_AGENT, depth 2)
 *           └── [3 actions] (WORKFLOW_AGENT_ACTION, depth 3)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LayerHierarchyBootstrap {

    private final LayerRepository layerRepository;

    // --- Layer IDs (stable, used for API references) ---

    public static final String CONTROLLER = "controller";
    public static final String CONTROLLER_UI_EVENT_POLL = "controller-ui-event-poll";
    public static final String WORKFLOW_AGENT = "workflow-agent";

    public static final String DISCOVERY_DISPATCH_SUBAGENT = "discovery-dispatch-subagent";
    public static final String PLANNING_DISPATCH_SUBAGENT = "planning-dispatch-subagent";
    public static final String TICKET_DISPATCH_SUBAGENT = "ticket-dispatch-subagent";

    // --- WorkflowAgent action names ---

    private static final List<String> WORKFLOW_AGENT_ACTIONS = List.of(
            "routeToContextManager",
            "contextManagerRequest",
            "coordinateWorkflow",
            "handleUnifiedInterrupt",
            "finalCollectorResult",
            "consolidateWorkflowOutputs",
            "consolidateDiscoveryFindings",
            "consolidatePlansIntoTickets",
            "consolidateTicketResults",
            "kickOffAnyNumberOfAgentsForCodeSearch",
            "dispatchDiscoveryAgentRequests",
            "decomposePlanAndCreateWorkItems",
            "dispatchPlanningAgentRequests",
            "finalizeTicketOrchestrator",
            "orchestrateTicketExecution",
            "dispatchTicketAgentRequests",
            "performMerge",
            "performReview",
            "handleTicketCollectorBranch",
            "handleDiscoveryCollectorBranch",
            "handleOrchestratorCollectorBranch",
            "handlePlanningCollectorBranch"
    );

    private static final List<String> DISCOVERY_SUBAGENT_ACTIONS = List.of(
            "ranDiscoveryAgent", "transitionToInterruptState", "runDiscoveryAgent"
    );

    private static final List<String> PLANNING_SUBAGENT_ACTIONS = List.of(
            "ranPlanningAgent", "transitionToInterruptState", "runPlanningAgent"
    );

    private static final List<String> TICKET_SUBAGENT_ACTIONS = List.of(
            "ranTicketAgentResult", "transitionToInterruptState", "runTicketAgent"
    );

    @Bean
    public ApplicationRunner bootstrapFilterLayers() {
        return args -> seedLayersIfAbsent();
    }

    @Transactional
    public void seedLayersIfAbsent() {
        if (layerRepository.findByLayerId(CONTROLLER).isPresent()) {
            log.info("Filter layer hierarchy already exists — skipping bootstrap.");
            return;
        }

        log.info("Bootstrapping filter layer hierarchy...");
        List<LayerEntity> all = new ArrayList<>();

        // depth 0: controller root
        var controllerLayer = layer(CONTROLLER, FilterEnums.LayerType.CONTROLLER, CONTROLLER, null, 0);
        all.add(controllerLayer);

        // depth 1: controller-ui-event-poll (child of controller)
        var uiEventPoll = layer(CONTROLLER_UI_EVENT_POLL, FilterEnums.LayerType.CONTROLLER_UI_EVENT_POLL,
                CONTROLLER_UI_EVENT_POLL, CONTROLLER, 1);
        all.add(uiEventPoll);
        controllerLayer.getChildLayerIds().add(CONTROLLER_UI_EVENT_POLL);

        // depth 1: workflow-agent (child of controller)
        var workflowAgent = layer(WORKFLOW_AGENT, FilterEnums.LayerType.WORKFLOW_AGENT,
                WORKFLOW_AGENT, CONTROLLER, 1);
        all.add(workflowAgent);
        controllerLayer.getChildLayerIds().add(WORKFLOW_AGENT);

        // depth 2: WorkflowAgent actions
        for (String action : WORKFLOW_AGENT_ACTIONS) {
            String actionId = WORKFLOW_AGENT + "/" + action;
            var actionLayer = layer(actionId, FilterEnums.LayerType.WORKFLOW_AGENT_ACTION,
                    action, WORKFLOW_AGENT, 2);
            all.add(actionLayer);
            workflowAgent.getChildLayerIds().add(actionId);
        }

        // depth 2: sub-agents (children of workflow-agent)
        addSubagent(all, workflowAgent, DISCOVERY_DISPATCH_SUBAGENT, DISCOVERY_SUBAGENT_ACTIONS);
        addSubagent(all, workflowAgent, PLANNING_DISPATCH_SUBAGENT, PLANNING_SUBAGENT_ACTIONS);
        addSubagent(all, workflowAgent, TICKET_DISPATCH_SUBAGENT, TICKET_SUBAGENT_ACTIONS);

        layerRepository.saveAll(all);
        log.info("Bootstrapped {} filter layers.", all.size());
    }

    private void addSubagent(List<LayerEntity> all, LayerEntity parent,
                             String subagentId, List<String> actions) {
        var subagent = layer(subagentId, FilterEnums.LayerType.WORKFLOW_AGENT,
                subagentId, parent.getLayerId(), 2);
        all.add(subagent);
        parent.getChildLayerIds().add(subagentId);

        for (String action : actions) {
            String actionId = subagentId + "/" + action;
            var actionLayer = layer(actionId, FilterEnums.LayerType.WORKFLOW_AGENT_ACTION,
                    action, subagentId, 3);
            all.add(actionLayer);
            subagent.getChildLayerIds().add(actionId);
        }
    }

    private static LayerEntity layer(String layerId, FilterEnums.LayerType type,
                                     String key, String parentId, int depth) {
        return LayerEntity.builder()
                .layerId(layerId)
                .layerType(type.name())
                .layerKey(key)
                .parentLayerId(parentId)
                .depth(depth)
                .isInheritable(true)
                .isPropagatedToParent(false)
                .build();
    }
}
