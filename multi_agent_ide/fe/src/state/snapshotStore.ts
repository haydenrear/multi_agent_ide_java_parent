import { useEffect } from "react";
import { A2uiCore } from "@/lib/a2uiBridge";
import type { GraphEventRecord } from "@/state/graphStore";
import type { A2uiServerMessage } from "@/lib/a2uiMessageBuilder";

export type SurfaceSnapshot = {
  surfaceId: string;
  surface: A2uiCore.Types.Surface;
  timestamp: number;
  eventId?: string;
  nodeId?: string;
  source: "llm" | "system";
};

export type SurfaceSnapshotState = {
  current?: SurfaceSnapshot;
  history: SurfaceSnapshot[];
};

type SnapshotStoreState = {
  snapshotLimit: number;
  surfaceSnapshots: Record<string, SurfaceSnapshotState>;
};

const DEFAULT_LIMIT = 10;
const state: SnapshotStoreState = {
  snapshotLimit: DEFAULT_LIMIT,
  surfaceSnapshots: {},
};

const extractSurfaceIds = (messages: A2uiServerMessage[]) => {
  const surfaceIds = new Set<string>();
  messages.forEach((message) => {
    if ("surfaceUpdate" in message && message.surfaceUpdate?.surfaceId) {
      surfaceIds.add(message.surfaceUpdate.surfaceId);
    }
    if ("beginRendering" in message && message.beginRendering?.surfaceId) {
      surfaceIds.add(message.beginRendering.surfaceId);
    }
  });
  return surfaceIds;
};

export const snapshotActions = {
  setSnapshotLimit(limit: number) {
    if (!Number.isFinite(limit) || limit <= 0) {
      return;
    }
    state.snapshotLimit = Math.floor(limit);
  },
  recordSurfaceSnapshot(params: {
    surfaceId: string;
    surface: A2uiCore.Types.Surface;
    event?: GraphEventRecord;
    source: "llm" | "system";
  }) {
    const { surfaceId, surface, event, source } = params;
    const existing = state.surfaceSnapshots[surfaceId] ?? { history: [] };
    const next: SurfaceSnapshotState = {
      current: {
        surfaceId,
        surface,
        timestamp: Date.now(),
        eventId: event?.id,
        nodeId: event?.nodeId,
        source,
      },
      history: [...existing.history],
    };

    if (source === "llm" && existing.current) {
      next.history = [existing.current, ...next.history];
    }

    if (next.history.length > state.snapshotLimit) {
      next.history = next.history.slice(0, state.snapshotLimit);
    }

    state.surfaceSnapshots[surfaceId] = next;
  },
  revertSurfaceSnapshot(surfaceId: string) {
    const existing = state.surfaceSnapshots[surfaceId];
    if (!existing || existing.history.length === 0) {
      return undefined;
    }
    const [previous, ...rest] = existing.history;
    state.surfaceSnapshots[surfaceId] = {
      current: previous,
      history: rest,
    };
    return previous;
  },
};

export const useSnapshotTracking = (params: {
  messages: A2uiServerMessage[];
  event?: GraphEventRecord;
  surfaceCache: React.MutableRefObject<Map<string, A2uiCore.Types.Surface>>;
}) => {
  const { messages, event, surfaceCache } = params;
  useEffect(() => {
    if (!event) {
      return;
    }
    const surfaceIds = extractSurfaceIds(messages);
    const source = event.type === "GUI_RENDER" ? "llm" : "system";
    surfaceIds.forEach((surfaceId) => {
      const surface = surfaceCache.current.get(surfaceId);
      if (!surface) {
        return;
      }
      snapshotActions.recordSurfaceSnapshot({
        surfaceId,
        surface,
        event,
        source,
      });
    });
  }, [event, messages, surfaceCache]);
};
