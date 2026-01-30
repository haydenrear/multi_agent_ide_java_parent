import React from "react";
import { act, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { buildControlMessages } from "@/lib/a2uiMessageBuilder";

// Mock the ResizeObserver
const ResizeObserverMock = vi.fn(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
}));

// Stub the global ResizeObserver
vi.stubGlobal('ResizeObserver', ResizeObserverMock);

vi.mock("@/lib/agentControls", () => ({
  requestPause: vi.fn(),
  requestResume: vi.fn(),
  requestStop: vi.fn(),
  requestReview: vi.fn(),
  requestPrune: vi.fn(),
  requestBranch: vi.fn(),
}));

const setupGraphView = async () => {
  vi.resetModules();
  const [{ GraphView }, store] = await Promise.all([
    import("@/components/GraphView"),
    import("@/state/graphStore"),
  ]);
  const { useGraphNodes, useGraphStore, graphSelectors, graphActions } = store;

  const Harness = () => {
    const nodes = useGraphNodes();
    const filters = useGraphStore(graphSelectors.filters);
    const selectedNodeId = useGraphStore((state) => state.selectedNodeId);
    const unknownEvents = useGraphStore((state) => state.unknownEvents);
    return (
      <GraphView
        nodes={nodes}
        selectedNodeId={selectedNodeId}
        unknownEvents={unknownEvents}
        filters={filters}
        onFilterChange={graphActions.setFilters}
        onSelectNode={graphActions.selectNode}
      />
    );
  };

  const utils = render(<Harness />);
  return { ...utils, graphActions };
};

describe("GraphView", () => {
  it("renders structure and updates when nodes arrive", async () => {
    const { container, graphActions } = await setupGraphView();

    expect(screen.getByText("Nodes")).toBeInTheDocument();
    expect(container.querySelector(".node-menu")).toBeTruthy();
    expect(container.querySelector(".filter-row")).toBeTruthy();
    expect(container.querySelector(".node-menu-list")).toBeTruthy();
    expect(screen.getByText("Waiting for events...")).toBeInTheDocument();

    act(() => {
      graphActions.applyEvent({
        type: "NODE_ADDED",
        rawEvent: {
          nodeId: "node-1",
          nodeTitle: "Node One",
          nodeType: "WORK",
          timestamp: "2024-01-01T00:00:00Z",
        },
      });
      graphActions.applyEvent({
        type: "NODE_ADDED",
        rawEvent: {
          nodeId: "node-2",
          nodeTitle: "Node Two",
          nodeType: "REVIEW",
          timestamp: "2024-01-01T00:00:01Z",
        },
      });
    });

    await waitFor(() => {
      expect(container.querySelectorAll(".node-menu-item").length).toBe(2);
    });
  });

  it("renders unknown events and forwards control actions", async () => {
    const { container, graphActions } = await setupGraphView();
    const agentControls = await import("@/lib/agentControls");

    act(() => {
      graphActions.applyEvent({
        type: "GUI_RENDER",
        payload: {
          a2uiMessages: buildControlMessages("node-1"),
        },
        rawEvent: {
          timestamp: "2024-01-01T00:00:02Z",
        },
      });
      graphActions.applyEvent({
        type: "TEXT_MESSAGE",
        payload: {
          content: "hello",
        },
        rawEvent: {
          timestamp: "2024-01-01T00:00:03Z",
        },
      });
    });

    await waitFor(() => {
      expect(screen.getByText("Unknown events")).toBeInTheDocument();
    });
    await waitFor(() => {
      expect(container.querySelectorAll("a2ui-surface").length).toBeGreaterThan(
        1,
      );
    });

    const surface = container.querySelector("a2ui-surface");
    surface?.dispatchEvent(
      new CustomEvent("a2uiaction", {
        detail: {
          action: {
            name: "control.pause",
            context: [
              {
                key: "nodeId",
                value: { literalString: "node-1" },
              },
            ],
          },
        },
      }),
    );

    expect(agentControls.requestPause).toHaveBeenCalledWith("node-1");
  });

  it("renders multiple viewer types with event data", async () => {
    const { container, graphActions } = await setupGraphView();

    act(() => {
      graphActions.applyEvent({
        type: "NODE_ADDED",
        rawEvent: {
          nodeId: "node-merge",
          nodeTitle: "Merge Node",
          nodeType: "MERGE",
          timestamp: "2024-01-01T00:00:00Z",
        },
      });
      graphActions.applyEvent({
        type: "NODE_ADDED",
        rawEvent: {
          nodeId: "node-basic",
          nodeTitle: "Basic Node",
          nodeType: "WORK",
          timestamp: "2024-01-01T00:00:01Z",
        },
      });
      graphActions.applyEvent({
        type: "MERGE_STARTED",
        rawEvent: {
          nodeId: "node-merge",
          timestamp: "2024-01-01T00:00:02Z",
        },
      });
      graphActions.applyEvent({
        type: "NODE_STREAM_DELTA",
        rawEvent: {
          nodeId: "node-merge",
          deltaContent: "streaming update",
          timestamp: "2024-01-01T00:00:03Z",
        },
      });
      graphActions.applyEvent({
        type: "TEXT_MESSAGE",
        rawEvent: {
          nodeId: "node-merge",
          deltaContent: "hello",
          timestamp: "2024-01-01T00:00:03Z",
        },
      });
      graphActions.applyEvent({
        type: "TOOL_CALL_STARTED",
        rawEvent: {
          nodeId: "node-merge",
          title: "read_file",
          timestamp: "2024-01-01T00:00:04Z",
        },
      });
    });

    await waitFor(() => {
      const items = container.querySelectorAll(".node-menu-item");
      expect(items.length).toBe(2);
      const chatSurface = container.querySelector(
        '.chat-panel [data-node-id="node-merge"]',
      );
      expect(chatSurface).toBeTruthy();
      const mergeSurfaces =
        chatSurface?.querySelectorAll(".event-list a2ui-surface") ?? [];
      expect(mergeSurfaces.length).toBeGreaterThanOrEqual(3);
      const controlSurface = chatSurface?.querySelector(
        ".a2ui-surface a2ui-surface",
      );
      expect(controlSurface).toBeTruthy();
    });
  });

  it("fires control actions for each node and calls endpoints", async () => {
    const { graphActions } = await setupGraphView();
    const agentControls = await import("@/lib/agentControls");
    vi.clearAllMocks();
    const actions = [
      { name: "control.pause", fn: agentControls.requestPause },
      { name: "control.resume", fn: agentControls.requestResume },
      { name: "control.stop", fn: agentControls.requestStop },
      { name: "control.review", fn: agentControls.requestReview },
      { name: "control.prune", fn: agentControls.requestPrune },
      { name: "control.branch", fn: agentControls.requestBranch },
    ];

    act(() => {
      graphActions.applyEvent({
        type: "NODE_ADDED",
        rawEvent: {
          nodeId: "node-merge",
          nodeTitle: "Merge Node",
          nodeType: "MERGE",
          timestamp: "2024-01-01T00:00:00Z",
        },
      });
      graphActions.applyEvent({
        type: "NODE_ADDED",
        rawEvent: {
          nodeId: "node-basic",
          nodeTitle: "Basic Node",
          nodeType: "WORK",
          timestamp: "2024-01-01T00:00:01Z",
        },
      });
    });

    const findControlSurface = (nodeId: string) => {
      const chat = document.querySelector(
        `.chat-panel [data-node-id="${nodeId}"]`,
      );
      if (!chat) {
        return null;
      }
      return chat.querySelector(".a2ui-surface a2ui-surface");
    };

    const fireAction = (
      surface: Element | null,
      actionName: string,
      nodeId: string,
    ) => {
      surface?.dispatchEvent(
        new CustomEvent("a2uiaction", {
          detail: {
            action: {
              name: actionName,
              context: [
                {
                  key: "nodeId",
                  value: { literalString: nodeId },
                },
              ],
            },
          },
        }),
      );
    };

    await waitFor(() => {
      expect(findControlSurface("node-merge")).toBeTruthy();
    });

    const mergeSurface = findControlSurface("node-merge");
    actions.forEach(({ name }) => {
      fireAction(mergeSurface, name, "node-merge");
    });

    act(() => {
      graphActions.selectNode("node-basic");
    });

    await waitFor(() => {
      expect(findControlSurface("node-basic")).toBeTruthy();
    });

    const basicSurface = findControlSurface("node-basic");
    actions.forEach(({ name }) => {
      fireAction(basicSurface, name, "node-basic");
    });

    actions.forEach(({ fn }) => {
      expect(fn).toHaveBeenCalledWith("node-merge");
      expect(fn).toHaveBeenCalledWith("node-basic");
    });
  });
});
