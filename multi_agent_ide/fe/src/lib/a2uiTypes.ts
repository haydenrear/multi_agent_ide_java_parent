import type { GraphEventRecord, GraphNode } from "../state/graphStore";
import type { A2uiServerMessage } from "./a2uiMessageBuilder";

export type A2uiPayload = {
  renderer: string;
  sessionId?: string;
  title?: string;
  props?: Record<string, unknown>;
  a2uiMessages?: unknown;
  fallback?: unknown;
  eventId?: string;
  sourceEventType?: string;
};

export type A2uiRendererProps = {
  payload: A2uiPayload;
  event?: GraphEventRecord;
  node?: GraphNode;
  onFeedback?: (event: GraphEventRecord, message: string) => void;
  onRevert?: (event: GraphEventRecord) => void;
};

export type A2uiRenderer = (props: A2uiRendererProps) => A2uiServerMessage[];

export type A2uiRenderContext = {
  payload: A2uiPayload;
  event?: GraphEventRecord;
  node?: GraphNode;
  instanceId?: string;
  onFeedback?: (event: GraphEventRecord, message: string) => void;
  onRevert?: (event: GraphEventRecord) => void;
};
