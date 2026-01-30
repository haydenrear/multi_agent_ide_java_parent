import React, { useEffect, useLayoutEffect, useRef, useState } from "react";
import type { GraphProps } from "@/state/graphStore";
import {
  graphSelectors,
  useGraphNode,
  useGraphNodeEvents,
  useGraphStore,
} from "@/state/graphStore";
import {
  requestUiRevert,
  submitGuiFeedback,
  submitUiMessage,
} from "@/lib/guiActions";
import { renderA2ui } from "@/lib/a2uiRegistry";
import { A2uiEventViewer } from "@/plugins/A2uiEventViewer";
import { isDeltaEvent } from "@/lib/a2uiEventPolicy";

type NodeDetailsPanelProps = GraphProps;

export const NodeDetailsPanel = ({ nodeId }: NodeDetailsPanelProps) => {
  const node = useGraphNode(nodeId);
  const uiSnapshot = useGraphStore(graphSelectors.uiSnapshot);
  const [draft, setDraft] = useState("");
  const [isSending, setIsSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const eventsRef = useRef<HTMLDivElement | null>(null);
  const autoScrollRef = useRef(true);
  if (!node) {
    return null;
  }

  const nodeEvents = useGraphNodeEvents(node.id);
  const orderedEvents = [...nodeEvents].sort(
    (a, b) => (a.sortTime ?? 0) - (b.sortTime ?? 0),
  );
  const collapsedEvents = orderedEvents.filter((event) => !isDeltaEvent(event));
  const visibleEvents =
    collapsedEvents.length > 50
      ? collapsedEvents.slice(collapsedEvents.length - 50)
      : collapsedEvents;

  const scrollToBottom = () => {
    const container = eventsRef.current;
    if (!container || !autoScrollRef.current) {
      return;
    }
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        container.scrollTop = container.scrollHeight;
      });
    });
  };

  useLayoutEffect(() => {
    autoScrollRef.current = true;
    scrollToBottom();
  }, [node.id]);

  useEffect(() => {
    scrollToBottom();
  }, [visibleEvents.length, node.id]);

  useEffect(() => {
    const container = eventsRef.current;
    if (!container) {
      return;
    }
    const observer = new ResizeObserver(() => {
      scrollToBottom();
    });
    observer.observe(container);
    return () => {
      observer.disconnect();
    };
  }, [node.id]);

  useEffect(() => {
    autoScrollRef.current = true;
  }, [node.id]);

  const sendMessage = async () => {
    const message = draft.trim();
    if (!message || isSending) {
      return;
    }
    setIsSending(true);
    setSendError(null);
    try {
      await submitUiMessage({ nodeId: node.id, message });
      setDraft("");
    } catch (error) {
      const detail =
        error instanceof Error ? error.message : "Failed to send message.";
      setSendError(detail);
    } finally {
      setIsSending(false);
    }
  };

  return (
    <div className="chat-surface" data-node-id={node.id}>
      <div className="chat-header">
        <div>
          <h2>Node Chat Test</h2>
          <p className="muted">
            {node.nodeType ?? "Node"} • {node.id}
          </p>
        </div>
        <div className="chat-controls">
          {renderA2ui({
            payload: {
              renderer: "control-actions",
              sessionId: node.id,
              props: { nodeId: node.id },
            },
            instanceId: `controls-${node.id}`,
          })}
        </div>
      </div>
      <div
        className="event-list chat-events"
        ref={eventsRef}
        onScroll={() => {
          const container = eventsRef.current;
          if (!container) {
            return;
          }
          const distanceFromBottom =
            container.scrollHeight -
            container.scrollTop -
            container.clientHeight;
          autoScrollRef.current = distanceFromBottom < 64;
        }}
      >
        {visibleEvents.map((event) => (
          <A2uiEventViewer
            key={event.id}
            event={event}
            node={node}
            onFeedback={(evt, message) => {
              submitGuiFeedback({
                eventId: evt.id,
                nodeId: node.id,
                message,
                snapshot: uiSnapshot,
              });
            }}
            onRevert={(evt) => {
              requestUiRevert({
                eventId: evt.id,
                nodeId: node.id,
              });
            }}
          />
        ))}
      </div>
      <form
        className="chat-input"
        onSubmit={(event) => {
          event.preventDefault();
          sendMessage();
        }}
      >
        <input
          placeholder="Send a message to this node..."
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          disabled={isSending}
        />
        <button type="submit" disabled={isSending || draft.trim().length === 0}>
          {isSending ? "Sending..." : "Send"}
        </button>
      </form>
      {sendError ? <p className="muted chat-error">{sendError}</p> : null}
    </div>
  );
};
