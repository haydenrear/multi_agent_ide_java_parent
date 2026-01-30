package com.hayden.multiagentidelib.model.nodes;

/**
 * Capability mixin: Nodes that orchestrate a workflow phase.
 */
public sealed interface Orchestrator
        permits DiscoveryOrchestratorNode, OrchestratorNode, PlanningOrchestratorNode, TicketOrchestratorNode {
}
