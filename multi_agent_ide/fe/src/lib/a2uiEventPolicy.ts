import type { GraphEventRecord } from "@/state/graphStore";
import type { A2uiPayload } from "@/lib/a2uiTypes";
import { resolveA2uiPayload } from "@/lib/a2uiRegistry";

export type A2uiEventPolicy = {
  payload: A2uiPayload;
  resolvePayload: (event: GraphEventRecord) => A2uiPayload;
  followupFilter?: (event: GraphEventRecord) => boolean;
};

export const isDeltaEvent = (event: GraphEventRecord) =>
  event.type.includes("DELTA");

const streamFilter = (event: GraphEventRecord) =>
  event.type === "NODE_STREAM_DELTA" ||
  event.type.includes("TEXT_MESSAGE") ||
  event.type === "ADD_MESSAGE_EVENT" ||
  event.type === "PAUSE_EVENT";

export const resolveA2uiPayloadFactory = (
  event: GraphEventRecord,
): A2uiEventPolicy => {
  const basePayload = resolveA2uiPayload(event);
  if (basePayload.renderer === "stream") {
    return {
      payload: basePayload,
      resolvePayload: (nextEvent) => ({
        ...resolveA2uiPayload(nextEvent),
        renderer: basePayload.renderer,
      }),
      followupFilter: streamFilter,
    };
  }
  return {
    payload: basePayload,
    resolvePayload: resolveA2uiPayload,
  };
};
