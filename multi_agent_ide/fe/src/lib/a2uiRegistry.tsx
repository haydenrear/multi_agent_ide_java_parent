import React from "react";
import type { GraphEventRecord, GraphNode } from "@/state/graphStore";
import { A2uiSurfaceRenderer } from "@/components/A2uiSurfaceRenderer";
import type { A2uiPayload, A2uiRenderContext, A2uiRenderer } from "./a2uiTypes";
import {
  buildNodeSummaryMessages,
  buildPayloadMessages,
  extractA2uiMessages,
} from "./a2uiMessageBuilder";
import {
  requestBranch,
  requestPause,
  requestPrune,
  requestResume,
  requestReview,
  requestStop,
} from "./agentControls";
import { requestUiRevert, submitGuiFeedback } from "./guiActions";
import { registerToolRenderers } from "@/plugins/a2ui/toolRenderers";
import { registerControlRenderers } from "@/plugins/a2ui/controlRenderers";
import { registerEventRenderers } from "@/plugins/a2ui/eventRenderers";

const registry = new Map<string, A2uiRenderer>();
let defaultsRegistered = false;

export const registerA2uiRenderer = (name: string, renderer: A2uiRenderer) => {
  registry.set(name, renderer);
};

const registerCoreRenderers = () => {
  registerA2uiRenderer("event-default", ({ payload, event }) =>
    buildPayloadMessages({
      title: payload.title ?? event?.type ?? payload.renderer,
      payload: payload.props ?? event?.rawEvent ?? payload.fallback,
      timestamp: event?.timestamp,
      eventId: event?.id,
      nodeId: event?.nodeId ?? payload.sessionId,
      includeActions: !!event,
    }),
  );

  registerA2uiRenderer("node-summary", ({ payload }) => {
    const node = payload.props?.node as GraphNode | undefined;
    if (!node) {
      return buildPayloadMessages({
        title: payload.title ?? "Node",
        payload: payload.props ?? payload.fallback,
      });
    }
    return buildNodeSummaryMessages(node);
  });

  registerA2uiRenderer("gui-payload", ({ payload, event }) =>
    buildPayloadMessages({
      title: payload.title ?? "UI payload",
      payload: payload.props ?? payload.fallback,
      timestamp: event?.timestamp,
      eventId: event?.id,
      nodeId: event?.nodeId ?? payload.sessionId,
      includeActions: !!event,
    }),
  );
};

export const ensureDefaultA2uiRenderers = () => {
  if (defaultsRegistered) {
    return;
  }
  defaultsRegistered = true;
  registerCoreRenderers();
  registerToolRenderers(registerA2uiRenderer);
  registerControlRenderers(registerA2uiRenderer);
  registerEventRenderers(registerA2uiRenderer);
};

const resolveRendererName = (event: GraphEventRecord): string => {
  if (event.type.includes("MERGE") || event.type.includes("REVIEW")) {
    return "merge-event";
  }
  if (event.type.includes("WORKTREE")) {
    return "worktree-event";
  }
  if (event.type.includes("TOOL_CALL")) {
    return "tool-call";
  }
  if (
    event.type === "NODE_STREAM_DELTA" ||
    event.type.includes("TEXT_MESSAGE")
  ) {
    return "stream";
  }
  return "event-default";
};

const tryParseJson = (value: unknown): Record<string, unknown> | null => {
  if (typeof value !== "string") {
    return value && typeof value === "object"
      ? (value as Record<string, unknown>)
      : null;
  }
  try {
    const parsed = JSON.parse(value);
    if (parsed && typeof parsed === "object") {
      return parsed as Record<string, unknown>;
    }
  } catch {
    return null;
  }
  return null;
};

const buildToolPayload = (event: GraphEventRecord): A2uiPayload => {
  const raw = event.rawEvent as Record<string, unknown> | undefined;
  const title = typeof raw?.title === "string" ? raw.title : undefined;
  const rawInput = raw?.rawInput;
  const rawOutput = raw?.rawOutput;
  const parsedInput = tryParseJson(rawInput);
  const parsedOutput = tryParseJson(rawOutput);
  const name = (title ?? "").toLowerCase();

  const path =
    (typeof parsedInput?.path === "string" ? parsedInput.path : undefined) ??
    (typeof parsedOutput?.path === "string" ? parsedOutput.path : undefined);
  const content =
    (typeof parsedInput?.content === "string"
      ? parsedInput.content
      : undefined) ??
    (typeof parsedOutput?.content === "string"
      ? parsedOutput.content
      : undefined);
  const line =
    typeof parsedInput?.line === "number" ? parsedInput.line : undefined;
  const limit =
    typeof parsedInput?.limit === "number" ? parsedInput.limit : undefined;

  const isWrite =
    name.includes("write") ||
    name.includes("save") ||
    name.includes("edit") ||
    (!!path && !!content);
  const isRead =
    name.includes("read") ||
    name.includes("open") ||
    (!!path && (line != null || limit != null));

  const renderer = isWrite ? "tool-write" : isRead ? "tool-read" : "tool-call";

  const props: Record<string, unknown> = {
    toolName: title,
    phase: raw?.phase,
    status: raw?.status,
    kind: raw?.kind,
    content: raw?.content,
    locations: raw?.locations,
    rawInput,
    rawOutput,
  };
  if (path) {
    props.path = path;
  }
  if (content) {
    props.content = content;
  }
  if (line != null) {
    props.line = line;
  }
  if (limit != null) {
    props.limit = limit;
  }

  return {
    renderer,
    sessionId: event.nodeId,
    title: title ?? event.type,
    props,
    fallback: raw ?? event.payload,
  };
};

const buildStreamPayload = (event: GraphEventRecord): A2uiPayload => {
  const raw = event.rawEvent as Record<string, unknown> | undefined;
  const deltaContent =
    typeof raw?.deltaContent === "string"
      ? raw.deltaContent
      : typeof raw?.content === "string"
        ? raw.content
        : typeof raw?.toAddMessage === "string"
          ? raw.toAddMessage
          : undefined;
  return {
    renderer: "stream",
    sessionId: event.nodeId,
    title: event.type,
    props: {
      content: deltaContent,
      tokenCount: raw?.tokenCount,
      isFinal: raw?.isFinal,
    },
    fallback: raw ?? event.payload,
  };
};

const isPayloadWithRenderer = (payload: unknown): payload is A2uiPayload => {
  if (!payload || typeof payload !== "object") {
    return false;
  }
  return typeof (payload as { renderer?: unknown }).renderer === "string";
};

export const resolveA2uiPayload = (event: GraphEventRecord): A2uiPayload => {
  if (isPayloadWithRenderer(event.payload)) {
    return event.payload;
  }
  if (event.type.includes("TOOL_CALL")) {
    return buildToolPayload(event);
  }
  if (
    event.type === "NODE_STREAM_DELTA" ||
    event.type.includes("TEXT_MESSAGE") ||
    event.type === "ADD_MESSAGE_EVENT" ||
    event.type === "PAUSE_EVENT"
  ) {
    return buildStreamPayload(event);
  }
  const renderer = resolveRendererName(event);
  return {
    renderer,
    sessionId: event.nodeId,
    title: event.type,
    props: {
      event,
    },
  };
};

export const renderA2ui = (context: A2uiRenderContext) => {
  ensureDefaultA2uiRenderers();
  const renderer =
    registry.get(context.payload.renderer) ?? registry.get("event-default");
  const resolvedMessages =
    extractA2uiMessages(context.payload) ?? renderer?.(context);
  const messages =
    resolvedMessages && resolvedMessages.length > 0
      ? resolvedMessages
      : buildPayloadMessages({
          title: context.payload.title ?? "Event",
          payload: context.payload,
          timestamp: context.event?.timestamp,
          eventId: context.event?.id,
          nodeId: context.event?.nodeId ?? context.payload.sessionId,
          includeActions: !!context.event,
        });

  return (
    <A2uiSurfaceRenderer
      key={context.instanceId}
      messages={messages}
      event={context.event}
      instanceId={context.instanceId}
      onAction={(action) => {
        if (action.name.startsWith("control.")) {
          const nodeId =
            action.context.nodeId ??
            context.event?.nodeId ??
            context.payload.sessionId;
          if (!nodeId) {
            return;
          }
          switch (action.name) {
            case "control.pause":
              requestPause(nodeId);
              return;
            case "control.resume":
              requestResume(nodeId);
              return;
            case "control.stop":
              requestStop(nodeId);
              return;
            case "control.review":
              requestReview(nodeId);
              return;
            case "control.prune":
              requestPrune(nodeId);
              return;
            case "control.branch":
              requestBranch(nodeId);
              return;
            default:
              return;
          }
        }

        if (action.name === "ui.feedback") {
          const event = context.event;
          if (!event) {
            return;
          }
          const message = window.prompt("Feedback for this UI event?");
          if (!message) {
            return;
          }
          if (context.onFeedback) {
            context.onFeedback(event, message);
            return;
          }
          submitGuiFeedback({
            eventId: action.context.eventId ?? event.id,
            nodeId: action.context.nodeId ?? event.nodeId ?? "unknown",
            message,
          });
          return;
        }

        if (action.name === "ui.revert") {
          const event = context.event;
          if (!event) {
            return;
          }
          if (context.onRevert) {
            context.onRevert(event);
            return;
          }
          requestUiRevert({
            eventId: action.context.eventId ?? event.id,
            nodeId: action.context.nodeId ?? event.nodeId ?? "unknown",
          });
        }
      }}
    />
  );
};
