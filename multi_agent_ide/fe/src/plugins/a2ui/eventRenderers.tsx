import type { A2uiRenderer } from "@/lib/a2uiTypes";
import { buildPayloadMessages } from "@/lib/a2uiMessageBuilder";

const StreamRenderer: A2uiRenderer = ({ payload, event }) => {
  const props = payload.props as Record<string, unknown> | undefined;
  const content =
    (typeof props?.content === "string" ? props.content : undefined) ??
    (event?.rawEvent as { deltaContent?: string } | undefined)?.deltaContent;
  const isFinal =
    typeof props?.isFinal === "boolean"
      ? props.isFinal
      : (event?.rawEvent as { isFinal?: boolean } | undefined)?.isFinal;
  const tokenCount =
    typeof props?.tokenCount === "number"
      ? props.tokenCount
      : (event?.rawEvent as { tokenCount?: number } | undefined)?.tokenCount;
  const status = `Stream output ${isFinal ? "• final" : "• in progress"}`;
  const summary =
    tokenCount != null ? `${status}\nTokens: ${tokenCount}` : status;

  return buildPayloadMessages({
    title: payload.title ?? "Streaming output",
    payload: props ?? payload.fallback,
    bodyText: content ? `${summary}\n\n${content}` : summary,
    timestamp: event?.timestamp,
    eventId: event?.id,
    nodeId: event?.nodeId ?? payload.sessionId,
    includeActions: !!event,
  });
};

const ToolCallRenderer: A2uiRenderer = ({ payload, event }) => {
  return buildPayloadMessages({
    title: payload.title ?? "Tool call",
    payload: payload.props ?? payload.fallback ?? payload,
    timestamp: event?.timestamp,
    eventId: event?.id,
    nodeId: event?.nodeId ?? payload.sessionId,
    includeActions: !!event,
  });
};

const MergeRenderer: A2uiRenderer = ({ payload, event }) => {
  const rawEvent = event?.rawEvent ?? payload.props ?? payload.fallback;
  return buildPayloadMessages({
    title: payload.title ?? "Merge update",
    payload: rawEvent ?? payload,
    timestamp: event?.timestamp,
    eventId: event?.id,
    nodeId: event?.nodeId ?? payload.sessionId,
    includeActions: !!event,
  });
};

const WorktreeRenderer: A2uiRenderer = ({ payload, event }) => {
  const rawEvent = event?.rawEvent as
    | {
        worktreeId?: string;
        worktreePath?: string;
        worktreeType?: string;
        submoduleName?: string;
        reason?: string;
        mergeCommitHash?: string;
        conflictDetected?: boolean;
        conflictFiles?: string[];
      }
    | undefined;
  const lines = [
    rawEvent?.worktreeId ? `Worktree: ${rawEvent.worktreeId}` : null,
    rawEvent?.worktreePath ? `Path: ${rawEvent.worktreePath}` : null,
    rawEvent?.worktreeType ? `Type: ${rawEvent.worktreeType}` : null,
    rawEvent?.submoduleName ? `Submodule: ${rawEvent.submoduleName}` : null,
    rawEvent?.mergeCommitHash
      ? `Merge commit: ${rawEvent.mergeCommitHash}`
      : null,
    typeof rawEvent?.conflictDetected === "boolean"
      ? `Conflicts: ${rawEvent.conflictDetected ? "yes" : "no"}`
      : null,
    rawEvent?.conflictFiles?.length
      ? `Conflict files: ${rawEvent.conflictFiles.join(", ")}`
      : null,
    rawEvent?.reason ? `Reason: ${rawEvent.reason}` : null,
  ].filter((entry): entry is string => Boolean(entry));

  return buildPayloadMessages({
    title: payload.title ?? "Worktree update",
    payload: rawEvent ?? payload.props ?? payload.fallback,
    bodyText: lines.length > 0 ? lines.join("\n") : undefined,
    timestamp: event?.timestamp,
    eventId: event?.id,
    nodeId: event?.nodeId ?? payload.sessionId,
    includeActions: !!event,
  });
};

export const registerEventRenderers = (
  register: (name: string, renderer: A2uiRenderer) => void,
) => {
  register("stream", StreamRenderer);
  register("tool-call", ToolCallRenderer);
  register("merge-event", MergeRenderer);
  register("worktree-event", WorktreeRenderer);
};
