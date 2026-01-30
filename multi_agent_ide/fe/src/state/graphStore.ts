import { useRef, useSyncExternalStore } from "react";

export type RawGraphEvent = {
  eventType?: string;
  nodeId?: string;
  timestamp?: string;
  [key: string]: unknown;
};

export type AgUiEventEnvelope = {
  type?: string | { name?: string };
  name?: string;
  timestamp?: number;
  rawEvent?: RawGraphEvent;
  payload?: unknown;
  value?: unknown;
  mapping?: Record<string, unknown>;
};

export type GraphEventRecord = {
  id: string;
  type: string;
  nodeId?: string;
  timestamp?: string;
  sortTime?: number;
  payload?: unknown;
  rawEvent?: RawGraphEvent;
  isGuiEvent?: boolean;
};

export type UiStateSnapshot = {
  sessionId: string;
  revision?: string;
  timestamp?: string;
  renderTree?: unknown;
  lastEvents?: GraphEventRecord[];
};

export type GraphFilter = {
  nodeId?: string;
  worktree?: string;
};

export type WorktreeMeta = {
  id: string;
  path?: string;
  worktreeType?: string;
  submoduleName?: string;
};

export type GraphNode = {
  id: string;
  title?: string;
  nodeType?: string;
  status?: string;
  parentId?: string;
  worktrees: WorktreeMeta[];
  lastUpdatedAt?: string;
  events: GraphEventRecord[];
  latestMessage?: string;
};

export type GraphProps = {
  nodeId: string;
};

export type GraphState = {
  nodes: Record<string, GraphNode>;
  selectedNodeId?: string;
  unknownEvents: GraphEventRecord[];
  filters: GraphFilter;
  uiSnapshot?: UiStateSnapshot;
};

type AgUiTestProbe = {
  events: GraphEventRecord[];
  max?: number;
};

const subscribers = new Set<() => void>();
const nodeListSubscribers = new Set<() => void>();
const nodeSubscribers = new Map<string, Set<() => void>>();
const EMPTY_EVENTS: GraphEventRecord[] = [];
let nodesArrayCache: GraphNode[] = [];

let state: GraphState = {
  nodes: {},
  selectedNodeId: undefined,
  unknownEvents: [],
  filters: {},
};

const pushTestProbeEvent = (record: GraphEventRecord) => {
  if (typeof window === "undefined") {
    return;
  }
  const probe = (window as Window & { __agUiTestProbe?: AgUiTestProbe })
    .__agUiTestProbe;
  if (!probe || !Array.isArray(probe.events)) {
    return;
  }
  const max = typeof probe.max === "number" ? probe.max : 200;
  probe.events.push({
    id: record.id,
    type: record.type,
    nodeId: record.nodeId,
    timestamp: record.timestamp,
    sortTime: record.sortTime,
    payload: record.payload,
    rawEvent: record.rawEvent,
    isGuiEvent: record.isGuiEvent,
  });
  if (probe.events.length > max) {
    probe.events.splice(0, probe.events.length - max);
  }
};

const notify = () => {
  subscribers.forEach((callback) => callback());
};

const notifyNodeList = () => {
  nodeListSubscribers.forEach((callback) => callback());
};

const notifyNode = (nodeId?: string) => {
  if (!nodeId) {
    return;
  }
  nodeSubscribers.get(nodeId)?.forEach((callback) => callback());
};

const updateState = (next: GraphState) => {
  state = next;
  notify();
};

const ensureNode = (nodeId: string): GraphNode => {
  if (!state.nodes[nodeId]) {
    state.nodes[nodeId] = {
      id: nodeId,
      worktrees: [],
      events: [],
    };
    nodesArrayCache = Object.values(state.nodes);
    notifyNodeList();
  }
  return state.nodes[nodeId];
};

const extractEventType = (event: AgUiEventEnvelope): string => {
  if (typeof event.type === "string") {
    if (event.type === "CUSTOM" && event.name) {
      return event.name;
    }
    return event.type;
  }
  if (event.type && typeof event.type === "object") {
    return event.type.name ?? "UNKNOWN";
  }
  if (event.rawEvent?.eventType) {
    return event.rawEvent.eventType;
  }
  if (event.name) {
    return event.name;
  }
  return "UNKNOWN";
};

const extractNodeId = (raw?: RawGraphEvent): string | undefined => {
  if (!raw) {
    return undefined;
  }
  return (
    (raw.nodeId as string | undefined) ??
    (raw as { associatedNodeId?: string }).associatedNodeId ??
    (raw as { branchedNodeId?: string }).branchedNodeId ??
    (raw as { originalNodeId?: string }).originalNodeId ??
    (raw as { orchestratorNodeId?: string }).orchestratorNodeId ??
    (raw as { reviewNodeId?: string }).reviewNodeId
  );
};

const pushEvent = (record: GraphEventRecord) => {
  if (!record.nodeId) {
    if (!state.unknownEvents.some((event) => event.id === record.id)) {
      state.unknownEvents = [record, ...state.unknownEvents]
        .sort((a, b) => (a.sortTime ?? 0) - (b.sortTime ?? 0))
        .slice(0, 50);
    }
    return;
  }
  const node = ensureNode(record.nodeId);
  if (node.events.some((event) => event.id === record.id)) {
    return;
  }
  node.events = [record, ...node.events]
    .sort((a, b) => (a.sortTime ?? 0) - (b.sortTime ?? 0))
    .slice(0, 100);
};

const isGuiEventPayload = (payload?: unknown): boolean => {
  if (!payload || typeof payload !== "object") {
    return false;
  }
  const record = payload as { renderer?: unknown; a2uiMessages?: unknown };
  return (
    typeof record.renderer === "string" || Array.isArray(record.a2uiMessages)
  );
};

export const graphActions = {
  applyEvent(event: AgUiEventEnvelope) {
    const eventType = extractEventType(event);
    const nodeId = extractNodeId(event.rawEvent);
    const parsedTime = event.rawEvent?.timestamp
      ? Date.parse(event.rawEvent.timestamp)
      : NaN;
    const sortTime = Number.isNaN(parsedTime)
      ? typeof event.timestamp === "number"
        ? event.timestamp
        : Date.now()
      : parsedTime;
    const record: GraphEventRecord = {
      id: `${eventType}-${nodeId ?? "global"}-${sortTime}`,
      type: eventType,
      nodeId,
      timestamp: event.rawEvent?.timestamp,
      sortTime,
      payload: event.payload ?? event.value,
      rawEvent: event.rawEvent,
      isGuiEvent:
        eventType === "GUI_RENDER" ||
        isGuiEventPayload(event.payload) ||
        isGuiEventPayload(event.value),
    };

    const renderTree =
      (event.payload as { renderTree?: unknown } | undefined)?.renderTree ??
      (event.value as { renderTree?: unknown } | undefined)?.renderTree;
    const revision =
      (event.payload as { revision?: string } | undefined)?.revision ??
      (event.value as { revision?: string } | undefined)?.revision;

    if (renderTree) {
      state.uiSnapshot = {
        sessionId: nodeId ?? "unknown",
        timestamp: event.rawEvent?.timestamp,
        renderTree,
        revision,
        lastEvents: [record, ...(state.uiSnapshot?.lastEvents ?? [])].slice(
          0,
          25,
        ),
      };
    }

    if (eventType === "NODE_ADDED" && event.rawEvent) {
      const node = ensureNode(nodeId ?? event.rawEvent.nodeId ?? "unknown");
      node.title =
        (event.rawEvent as { nodeTitle?: string }).nodeTitle ?? node.title;
      node.nodeType =
        (event.rawEvent as { nodeType?: string }).nodeType ?? node.nodeType;
      node.parentId =
        (event.rawEvent as { parentNodeId?: string }).parentNodeId ??
        node.parentId;
    }

    if (eventType === "NODE_STATUS_CHANGED" && event.rawEvent) {
      const node = ensureNode(nodeId ?? event.rawEvent.nodeId ?? "unknown");
      node.status =
        (event.rawEvent as { newStatus?: string }).newStatus ?? node.status;
      node.lastUpdatedAt = event.rawEvent.timestamp ?? node.lastUpdatedAt;
    }

    if (eventType === "WORKTREE_CREATED" && event.rawEvent) {
      const nodeRef = (event.rawEvent as { associatedNodeId?: string })
        .associatedNodeId;
      if (nodeRef) {
        const node = ensureNode(nodeRef);
        node.worktrees = [
          {
            id:
              (event.rawEvent as { worktreeId?: string }).worktreeId ??
              "worktree",
            path: (event.rawEvent as { worktreePath?: string }).worktreePath,
            worktreeType: (event.rawEvent as { worktreeType?: string })
              .worktreeType,
            submoduleName: (event.rawEvent as { submoduleName?: string })
              .submoduleName,
          },
          ...node.worktrees,
        ];
      }
    }

    if (eventType === "ADD_MESSAGE_EVENT" && event.rawEvent) {
      const node = ensureNode(nodeId ?? event.rawEvent.nodeId ?? "unknown");
      node.latestMessage =
        (event.rawEvent as { toAddMessage?: string }).toAddMessage ??
        node.latestMessage;
    }

    if (eventType === "NODE_STREAM_DELTA" && event.rawEvent) {
      const node = ensureNode(nodeId ?? event.rawEvent.nodeId ?? "unknown");
      node.latestMessage =
        (event.rawEvent as { deltaContent?: string }).deltaContent ??
        node.latestMessage;
    }

    pushEvent(record);
    pushTestProbeEvent(record);
    updateState({ ...state, nodes: { ...state.nodes } });
    notifyNode(record.nodeId);
  },
  removeNode(nodeId: string) {
    if (!state.nodes[nodeId]) {
      return;
    }
    const { [nodeId]: removed, ...rest } = state.nodes;
    state.nodes = rest;
    nodesArrayCache = Object.values(rest);
    updateState({ ...state, nodes: { ...rest } });
    notifyNodeList();
  },
  selectNode(nodeId?: string) {
    updateState({ ...state, selectedNodeId: nodeId });
  },
  setFilters(filters: GraphFilter) {
    updateState({ ...state, filters: { ...state.filters, ...filters } });
  },
};

export const useGraphStore = <T>(selector: (s: GraphState) => T): T => {
  return useSyncExternalStore(
    (callback) => {
      subscribers.add(callback);
      return () => subscribers.delete(callback);
    },
    () => selector(state),
    () => selector(state),
  );
};

export const useGraphNodes = (): GraphNode[] => {
  return useSyncExternalStore(
    (callback) => {
      nodeListSubscribers.add(callback);
      return () => nodeListSubscribers.delete(callback);
    },
    () => nodesArrayCache,
    () => nodesArrayCache,
  );
};

export const useGraphNodeEvents = (
  nodeId?: string,
  filter?: (event: GraphEventRecord) => boolean,
): GraphEventRecord[] => {
  const cacheRef = useRef<{
    eventsRef: GraphEventRecord[] | null;
    filterRef: ((event: GraphEventRecord) => boolean) | null;
    result: GraphEventRecord[];
  }>({ eventsRef: null, filterRef: null, result: EMPTY_EVENTS });
  return useSyncExternalStore(
    (callback) => {
      if (!nodeId) {
        return () => undefined;
      }
      const subscribersForNode =
        nodeSubscribers.get(nodeId) ?? new Set<() => void>();
      subscribersForNode.add(callback);
      nodeSubscribers.set(nodeId, subscribersForNode);
      return () => {
        subscribersForNode.delete(callback);
        if (subscribersForNode.size === 0) {
          nodeSubscribers.delete(nodeId);
        }
      };
    },
    () => {
      if (!nodeId) {
        return EMPTY_EVENTS;
      }
      const node = state.nodes[nodeId];
      if (!node) {
        return EMPTY_EVENTS;
      }
      const events = node.events;
      if (!filter) {
        return events;
      }
      const cache = cacheRef.current;
      if (cache.eventsRef === events && cache.filterRef === filter) {
        return cache.result;
      }
      const result = events.filter(filter);
      cacheRef.current = { eventsRef: events, filterRef: filter, result };
      return result;
    },
    () => {
      if (!nodeId) {
        return EMPTY_EVENTS;
      }
      const node = state.nodes[nodeId];
      if (!node) {
        return EMPTY_EVENTS;
      }
      const events = node.events;
      if (!filter) {
        return events;
      }
      const cache = cacheRef.current;
      if (cache.eventsRef === events && cache.filterRef === filter) {
        return cache.result;
      }
      const result = events.filter(filter);
      cacheRef.current = { eventsRef: events, filterRef: filter, result };
      return result;
    },
  );
};

export const useGraphNode = (nodeId?: string): GraphNode | undefined => {
  return useSyncExternalStore(
    (callback) => {
      if (!nodeId) {
        return () => undefined;
      }
      const subscribersForNode =
        nodeSubscribers.get(nodeId) ?? new Set<() => void>();
      subscribersForNode.add(callback);
      nodeSubscribers.set(nodeId, subscribersForNode);
      return () => {
        subscribersForNode.delete(callback);
        if (subscribersForNode.size === 0) {
          nodeSubscribers.delete(nodeId);
        }
      };
    },
    () => {
      if (!nodeId) {
        return undefined;
      }
      return state.nodes[nodeId];
    },
    () => {
      if (!nodeId) {
        return undefined;
      }
      return state.nodes[nodeId];
    },
  );
};

export const graphSelectors = {
  nodesArray: (s: GraphState) => Object.values(s.nodes),
  filteredNodes: (s: GraphState) => {
    const nodes = Object.values(s.nodes);
    const { nodeId, worktree } = s.filters;
    return nodes.filter((node) => {
      if (nodeId && !node.id.includes(nodeId)) {
        return false;
      }
      if (worktree) {
        const match = node.worktrees.some(
          (wt) =>
            wt.id.includes(worktree) || (wt.path ?? "").includes(worktree),
        );
        if (!match) {
          return false;
        }
      }
      return true;
    });
  },
  selectedNode: (s: GraphState) =>
    s.selectedNodeId ? s.nodes[s.selectedNodeId] : undefined,
  filters: (s: GraphState) => s.filters,
  uiSnapshot: (s: GraphState) => s.uiSnapshot,
};
