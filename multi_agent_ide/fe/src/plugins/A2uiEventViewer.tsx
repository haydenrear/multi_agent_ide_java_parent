import React, { useMemo } from "react";
import type { GraphEventRecord, GraphNode } from "@/state/graphStore";
import { useGraphNodeEvents } from "@/state/graphStore";
import { renderA2ui } from "@/lib/a2uiRegistry";
import { resolveA2uiPayloadFactory } from "@/lib/a2uiEventPolicy";

type A2uiEventViewerProps = {
  event: GraphEventRecord;
  node?: GraphNode;
  onFeedback?: (event: GraphEventRecord, message: string) => void;
  onRevert?: (event: GraphEventRecord) => void;
};

export const A2uiEventViewer = ({
  event,
  node,
  onFeedback,
  onRevert,
}: A2uiEventViewerProps) => {
  const policy = resolveA2uiPayloadFactory(event);
  const nodeId = node?.id ?? event.nodeId;
  const matchingEvents = useGraphNodeEvents(nodeId, policy.followupFilter);
  // TODO: renderA2ui should accept multip event records - should not skip
  const activeEvent = useMemo(() => {
    if (!policy.followupFilter || matchingEvents.length === 0) {
      return event;
    }
    const [first, ...rest] = matchingEvents;
    return rest.reduce<GraphEventRecord>(
      (latest, current) =>
        (current.sortTime ?? 0) >= (latest.sortTime ?? 0) ? current : latest,
      first ?? event,
    );
  }, [matchingEvents]);

  return renderA2ui({
    payload: policy.resolvePayload(activeEvent),
    event: activeEvent,
    node,
    onFeedback,
    onRevert,
  });
};
