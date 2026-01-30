import type { A2uiRenderer } from "../../lib/a2uiTypes";
import { buildPayloadMessages } from "../../lib/a2uiMessageBuilder";

const FileWriteRenderer: A2uiRenderer = ({ payload, event }) => {
  const props = payload.props as Record<string, unknown> | undefined;
  const path = typeof props?.path === "string" ? props.path : undefined;
  const content =
    typeof props?.content === "string" ? props.content : undefined;
  const bodyText = content ?? JSON.stringify(props ?? payload, null, 2);
  return buildPayloadMessages({
    title: "File write",
    payload: { ...props, path, content },
    bodyText,
    timestamp: event?.timestamp,
    eventId: event?.id,
    nodeId: event?.nodeId ?? payload.sessionId,
    includeActions: !!event,
  });
};

const FileReadRenderer: A2uiRenderer = ({ payload, event }) => {
  const props = payload.props as Record<string, unknown> | undefined;
  const path = typeof props?.path === "string" ? props.path : undefined;
  const line = typeof props?.line === "number" ? props.line : undefined;
  const limit = typeof props?.limit === "number" ? props.limit : undefined;
  const summary = [
    path ? `Path: ${path}` : null,
    line != null ? `Line: ${line}` : null,
    limit != null ? `Limit: ${limit}` : null,
  ]
    .filter(Boolean)
    .join("\n");

  return buildPayloadMessages({
    title: "File read",
    payload: { ...props, path, line, limit },
    bodyText: summary || JSON.stringify(props ?? payload, null, 2),
    timestamp: event?.timestamp,
    eventId: event?.id,
    nodeId: event?.nodeId ?? payload.sessionId,
    includeActions: !!event,
  });
};

const GenericToolRenderer: A2uiRenderer = ({ payload, event }) => {
  return buildPayloadMessages({
    title: "Tool call",
    payload: payload.props ?? payload,
    timestamp: event?.timestamp,
    eventId: event?.id,
    nodeId: event?.nodeId ?? payload.sessionId,
    includeActions: !!event,
  });
};

export const registerToolRenderers = (
  register: (name: string, renderer: A2uiRenderer) => void,
) => {
  register("tool-write", FileWriteRenderer);
  register("tool-read", FileReadRenderer);
  register("tool-generic", GenericToolRenderer);
  register("tool-call", GenericToolRenderer);
};
