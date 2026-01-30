package com.hayden.multiagentidelib.agent;

public record WorkflowGraphState(
        String orchestratorNodeId,
        String orchestratorCollectorNodeId,
        String discoveryOrchestratorNodeId,
        String discoveryCollectorNodeId,
        String planningOrchestratorNodeId,
        String planningCollectorNodeId,
        String ticketOrchestratorNodeId,
        String ticketCollectorNodeId,
        String reviewNodeId,
        String mergeNodeId
) {
    public static WorkflowGraphState initial(String orchestratorNodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public WorkflowGraphState withOrchestratorCollectorNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                nodeId,
                discoveryOrchestratorNodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withDiscoveryOrchestratorNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                nodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withDiscoveryCollectorNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                nodeId,
                planningOrchestratorNodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withPlanningOrchestratorNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                discoveryCollectorNodeId,
                nodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withPlanningCollectorNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                nodeId,
                ticketOrchestratorNodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withTicketOrchestratorNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                planningCollectorNodeId,
                nodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withTicketCollectorNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                nodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withReviewNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                ticketCollectorNodeId,
                nodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withMergeNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                nodeId
        );
    }
}
