import { renderHook, act } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import {
  graphActions,
  useGraphNodeEvents,
  useGraphNodes,
} from "@/state/graphStore";

// Mock the ResizeObserver
const ResizeObserverMock = vi.fn(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
}));

// Stub the global ResizeObserver
vi.stubGlobal('ResizeObserver', ResizeObserverMock);

describe("graphStore subscriptions", () => {
  it("publishes node additions to subscribers", () => {
    const { result } = renderHook(() => useGraphNodes());
    expect(result.current).toHaveLength(0);

    act(() => {
      graphActions.applyEvent({
        rawEvent: {
          eventType: "NODE_ADDED",
          nodeId: "node-1",
          nodeTitle: "Node One",
          nodeType: "WORK",
        },
        timestamp: Date.now(),
      });
    });

    expect(result.current).toHaveLength(1);
    expect(result.current[0].id).toBe("node-1");
  });

  it("streams node events oldest to newest", () => {
    const { result } = renderHook(() => useGraphNodeEvents("node-2"));

    act(() => {
      graphActions.applyEvent({
        rawEvent: {
          eventType: "NODE_ADDED",
          nodeId: "node-2",
          nodeTitle: "Node Two",
          nodeType: "WORK",
          timestamp: "2025-01-01T00:00:00.000Z",
        },
        timestamp: Date.parse("2025-01-01T00:00:00.000Z"),
      });
      graphActions.applyEvent({
        rawEvent: {
          eventType: "NODE_STATUS_CHANGED",
          nodeId: "node-2",
          newStatus: "RUNNING",
          timestamp: "2025-01-01T00:00:01.000Z",
        },
        timestamp: Date.parse("2025-01-01T00:00:01.000Z"),
      });
    });

    expect(result.current.map((event) => event.type)).toEqual([
      "NODE_ADDED",
      "NODE_STATUS_CHANGED",
    ]);
  });
});
