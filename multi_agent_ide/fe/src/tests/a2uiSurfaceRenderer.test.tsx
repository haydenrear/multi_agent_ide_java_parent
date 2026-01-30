import React from "react";
import { render, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { A2uiSurfaceRenderer } from "@/components/A2uiSurfaceRenderer";
import { snapshotActions } from "@/state/snapshotStore";
import {
  buildControlMessages,
  buildPayloadMessages,
} from "@/lib/a2uiMessageBuilder";

// Mock the ResizeObserver
const ResizeObserverMock = vi.fn(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
}));

// Stub the global ResizeObserver
vi.stubGlobal('ResizeObserver', ResizeObserverMock);

describe("A2uiSurfaceRenderer", () => {
  it("renders a2ui surface and forwards actions", async () => {
    const messages = buildControlMessages("node-1");
    const onAction = vi.fn();

    const { container } = render(
      <A2uiSurfaceRenderer messages={messages} onAction={onAction} />,
    );

    await waitFor(() => {
      expect(container.querySelector("a2ui-surface")).toBeTruthy();
    });
    const surface = container.querySelector("a2ui-surface");

    surface?.dispatchEvent(
      new CustomEvent("a2uiaction", {
        detail: {
          action: {
            name: "submit_form",
            context: [
              {
                key: "endpoint",
                value: { literalString: "/submit" },
              },
            ],
          },
        },
      }),
    );

    expect(onAction).toHaveBeenCalledWith({
      name: "submit_form",
      context: { endpoint: "/submit" },
    });
  });

  it("reverts surface when ui.revert action is emitted", async () => {
    const messages = buildPayloadMessages({
      title: "Snapshot Test",
      payload: { ok: true },
      eventId: "evt-1",
      nodeId: "node-1",
      includeActions: true,
    });

    const revertSpy = vi
      .spyOn(snapshotActions, "revertSurfaceSnapshot");

    const { container } = render(
      <A2uiSurfaceRenderer messages={messages} onAction={vi.fn()} />,
    );

    await waitFor(() => {
      expect(container.querySelector("a2ui-surface")).toBeTruthy();
    });
    const surface = container.querySelector("a2ui-surface");
    surface?.dispatchEvent(
      new CustomEvent("a2uiaction", {
        detail: {
          action: {
            name: "ui.revert",
            context: [
              {
                key: "surfaceId",
                value: { literalString: "payload-evt-1" },
              },
            ],
          },
        },
      }),
    );

    expect(revertSpy).toHaveBeenCalledWith("payload-evt-1");
  });
});
