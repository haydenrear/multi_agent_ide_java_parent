import React, {
  createElement,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import type { GraphEventRecord, GraphNode } from "@/state/graphStore";
import { A2uiCore } from "@/lib/a2uiBridge";
import type { A2uiServerMessage } from "@/lib/a2uiMessageBuilder";
import { snapshotActions, useSnapshotTracking } from "@/state/snapshotStore";
import { ensureA2uiThemeProvider } from "@/lib/a2uiTheme";

export type A2uiSurfaceRendererProps = {
  messages: A2uiServerMessage[];
  event?: GraphEventRecord;
  node?: GraphNode;
  instanceId?: string;
  onAction?: (action: {
    name: string;
    context: Record<string, string>;
  }) => void;
};

type SurfaceEntry = [string, A2uiCore.Types.Surface];
type A2uiProcessor = ReturnType<typeof A2uiCore.Data.createSignalA2uiMessageProcessor>;

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

const resolveActionContext = (
  context?: { key: string; value: { literalString?: string } }[],
) => {
  if (!context) {
    return {};
  }
  return context.reduce<Record<string, string>>((acc, entry) => {
    if (entry.value.literalString) {
      acc[entry.key] = entry.value.literalString;
    }
    return acc;
  }, {});
};

const A2uiSurface = ({
  surfaceId,
  surface,
  processor,
  onAction,
}: {
  surfaceId: string;
  surface: A2uiCore.Types.Surface;
  processor: A2uiProcessor;
  onAction?: (action: {
    name: string;
    context: Record<string, string>;
  }) => void;
}) => {
  const ref = useRef<HTMLElement | null>(null);

  useEffect(() => {
    const element = ref.current as
      | (HTMLElement & {
          surfaceId?: string;
          surface?: A2uiCore.Types.Surface;
          processor?: A2uiProcessor;
        })
      | null;
    if (!element) {
      return;
    }
    if (!(surface as { styles?: unknown }).styles) {
      (surface as { styles: Record<string, unknown> }).styles = {};
    }
    element.surfaceId = surfaceId;
    element.surface = surface;
    element.processor = processor;
  }, [surfaceId, surface, processor]);

  useEffect(() => {
    const element = ref.current;
    if (!element || !onAction) {
      return;
    }
    const handler = (evt: Event) => {
      const detail = (evt as CustomEvent).detail as {
        action?: {
          name: string;
          context?: { key: string; value: { literalString?: string } }[];
        };
      };
      if (!detail?.action) {
        return;
      }
      onAction({
        name: detail.action.name,
        context: resolveActionContext(detail.action.context),
      });
    };
    element.addEventListener("a2uiaction", handler as EventListener);
    return () =>
      element.removeEventListener("a2uiaction", handler as EventListener);
  }, [onAction]);

  return createElement("a2ui-surface" as any, {
    ref,
    surfaceId,
    surface,
    processor,
  });
};

export const A2uiSurfaceRenderer = ({
  messages,
  event,
  instanceId,
  onAction,
}: A2uiSurfaceRendererProps) => {
  ensureA2uiThemeProvider();
  const processor = useMemo(
    () => A2uiCore.Data.createSignalA2uiMessageProcessor(),
    [instanceId],
  );
  const [surfaces, setSurfaces] = useState<SurfaceEntry[]>([]);
  const surfaceCache = useRef(new Map<string, A2uiCore.Types.Surface>());

  useEffect(() => {
    surfaceCache.current = new Map();
    setSurfaces([]);
  }, [instanceId]);

  useEffect(() => {
    processor.processMessages(messages);
    const entries = Array.from(processor.getSurfaces().entries());
    const activeSurfaceIds = extractSurfaceIds(messages);
    const filteredEntries =
      activeSurfaceIds.size > 0
        ? entries.filter(([surfaceId]) => activeSurfaceIds.has(surfaceId))
        : entries;
    surfaceCache.current = new Map(filteredEntries);
    setSurfaces(filteredEntries);
  }, [event, messages, processor]);

  useSnapshotTracking({ messages, event, surfaceCache });

  if (surfaces.length === 0) {
    return null;
  }

  return (
    <div className="a2ui-surface">
      {createElement(
        "a2ui-theme-provider" as any,
        null,
        surfaces.map(([surfaceId, surface]) => (
          <A2uiSurface
            key={surfaceId}
            surfaceId={surfaceId}
            surface={surface}
            processor={processor}
            onAction={(action) => {
              if (action.name === "ui.revert") {
                const targetSurfaceId = action.context.surfaceId ?? surfaceId;
                const snapshot =
                  snapshotActions.revertSurfaceSnapshot(targetSurfaceId);
                if (snapshot?.surface) {
                  const updated = new Map(surfaceCache.current);
                  updated.set(targetSurfaceId, snapshot.surface);
                  surfaceCache.current = updated;
                  setSurfaces(Array.from(updated.entries()));
                }
              }
              onAction?.(action);
            }}
          />
        )),
      )}
    </div>
  );
};
