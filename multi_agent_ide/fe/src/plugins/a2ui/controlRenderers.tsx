import type { A2uiRenderer } from "../../lib/a2uiTypes";
import {
  buildControlMessages,
  buildPayloadMessages,
} from "../../lib/a2uiMessageBuilder";

const resolveNodeId = (payload: { props?: Record<string, unknown> }) => {
  const props = payload.props as Record<string, unknown> | undefined;
  return typeof props?.nodeId === "string" ? props.nodeId : undefined;
};

const ControlActionsRenderer: A2uiRenderer = ({ payload, event }) => {
  const nodeId = resolveNodeId(payload) ?? event?.nodeId;
  if (!nodeId) {
    return buildPayloadMessages({
      title: "Controls",
      payload: { message: "No node selected for controls." },
      timestamp: event?.timestamp,
      eventId: event?.id,
      nodeId: event?.nodeId ?? payload.sessionId,
      includeActions: !!event,
    });
  }

  return buildControlMessages(nodeId);
};

export const registerControlRenderers = (
  register: (name: string, renderer: A2uiRenderer) => void,
) => {
  register("control-actions", ControlActionsRenderer);
};
