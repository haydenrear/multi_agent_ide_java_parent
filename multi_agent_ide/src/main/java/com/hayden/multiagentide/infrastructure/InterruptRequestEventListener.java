package com.hayden.multiagentide.infrastructure;

import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.decorator.prompt.FilterPropertiesDecorator;
import com.hayden.multiagentide.orchestration.ComputationGraphOrchestrator;
import com.hayden.multiagentide.service.InterruptSchemaGenerator;
import com.hayden.multiagentidelib.agent.AgentType;
import com.hayden.multiagentidelib.model.nodes.GraphNode;
import com.hayden.multiagentidelib.prompt.contributor.NodeMappings;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InterruptRequestEventListener implements EventListener {

    private final ComputationGraphOrchestrator orchestrator;
    private final FilterPropertiesDecorator filterPropertiesDecorator;
    private final InterruptSchemaGenerator interruptSchemaGenerator;


    private EventBus eventBus;

    @Autowired @Lazy
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public String listenerId() {
        return "InterruptRequestEventListener";
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        if (!(event instanceof Events.InterruptRequestEvent interruptRequestEvent)) {
            return;
        }

        Optional<GraphNode> nodeOpt = orchestrator.getNode(interruptRequestEvent.nodeId());
        if (nodeOpt.isEmpty() || nodeOpt.get().status() != Events.NodeStatus.RUNNING) {
            eventBus.publish(new Events.InterruptStatusEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    interruptRequestEvent.nodeId(),
                    String.valueOf(interruptRequestEvent.interruptType()),
                    "IGNORED_TARGET_NOT_ACTIVE",
                    interruptRequestEvent.nodeId(),
                    null
            ));
            return;
        }

        GraphNode node = nodeOpt.get();
        String addMessage = composeInterruptMessage(interruptRequestEvent, node);
        eventBus.publish(new Events.AddMessageEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                interruptRequestEvent.nodeId(),
                addMessage
        ));
        filterPropertiesDecorator.storeEvent(interruptRequestEvent);
        eventBus.publish(new Events.InterruptStatusEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                interruptRequestEvent.nodeId(),
                String.valueOf(interruptRequestEvent.interruptType()),
                "REQUESTED",
                interruptRequestEvent.nodeId(),
                null
        ));
    }

    private String composeInterruptMessage(Events.InterruptRequestEvent event, GraphNode node) {
        String reason = event.reason() == null || event.reason().isBlank()
                ? "Interrupt requested by external actor."
                : event.reason();

        // Resolve the agent type from the event's sourceAgentType or from the graph node
        AgentType agentType = resolveAgentType(event.sourceAgentType(), node);
        String interruptSchema = agentType != null
                ? interruptSchemaGenerator.generateInterruptSchema(agentType)
                : null;

        StringBuilder message = new StringBuilder();
        message.append("## INTERRUPT: Your structured response type has changed\n\n");
        message.append("The controller has interrupted your current operation. ");
        message.append("You MUST now respond with an InterruptRequest instead of your normal routing response.\n\n");
        message.append("### Reason\n");
        message.append(reason).append("\n\n");

        if (event.contextForDecision() != null && !event.contextForDecision().isBlank()) {
            message.append("### Context\n");
            message.append(event.contextForDecision()).append("\n\n");
        }

        if (event.rerouteToAgentType() != null && !event.rerouteToAgentType().isBlank()) {
            message.append("### Routing Target\n");
            message.append("The controller has determined that this should be routed to agent type: **")
                    .append(event.rerouteToAgentType()).append("**\n\n");
        }

        if (interruptSchema != null) {
            message.append("### Override Schema (InterruptRequest)\n");
            message.append("Fill your response according to this schema:\n```json\n");
            message.append(interruptSchema);
            message.append("\n```\n");
        }

        return message.toString();
    }

    /**
     * Resolve the AgentType from the event's sourceAgentType string, or fall back
     * to inferring from the GraphNode subtype via {@link NodeMappings#agentTypeFromNode}.
     */
    static @Nullable AgentType resolveAgentType(@Nullable String sourceAgentTypeStr, GraphNode node) {
        if (sourceAgentTypeStr != null && !sourceAgentTypeStr.isBlank()) {
            try {
                return AgentType.fromWireValue(sourceAgentTypeStr);
            } catch (IllegalArgumentException ignored) {
                // fall through to node-based resolution
            }
        }
        return NodeMappings.agentTypeFromNode(node);
    }
}
