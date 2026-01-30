import React, { useEffect, useMemo } from "react";
import type { GraphEventRecord, GraphNode } from "@/state/graphStore";
import { GraphNodeCard } from "./GraphNodeCard";
import { A2uiEventViewer } from "@/plugins/A2uiEventViewer";
import { NodeDetailsPanel } from "./NodeDetailsPanel";

type GraphViewProps = {
  nodes: GraphNode[];
  selectedNodeId?: string;
  unknownEvents: GraphEventRecord[];
  filters: { nodeId?: string; worktree?: string };
  onFilterChange: (next: { nodeId?: string; worktree?: string }) => void;
  onSelectNode: (nodeId: string) => void;
};

export const GraphView = ({
  nodes,
  selectedNodeId,
  unknownEvents,
  filters,
  onFilterChange,
  onSelectNode,
}: GraphViewProps) => {
  const filteredNodes = useMemo(() => {
    return nodes.filter((node) => {
      if (filters.nodeId && !node.id.includes(filters.nodeId)) {
        return false;
      }
      if (filters.worktree) {
        const match = node.worktrees.some(
          (wt) =>
            wt.id.includes(filters.worktree ?? "") ||
            (wt.path ?? "").includes(filters.worktree ?? ""),
        );
        if (!match) {
          return false;
        }
      }
      return true;
    });
  }, [nodes, filters.nodeId, filters.worktree]);

  const activeNodeId =
    selectedNodeId && filteredNodes.some((node) => node.id === selectedNodeId)
      ? selectedNodeId
      : filteredNodes[0]?.id;

  useEffect(() => {
    if (activeNodeId && activeNodeId !== selectedNodeId) {
      onSelectNode(activeNodeId);
    }
  }, [activeNodeId, onSelectNode, selectedNodeId]);

  return (
    <div className="graph-shell">
      <aside className="panel node-menu">
        <div className="node-menu-header">
          <div>
            <h2>Nodes</h2>
            <p className="muted">Traverse each node to see its live stream.</p>
          </div>
          <span className="badge">{filteredNodes.length} nodes</span>
        </div>
        <div className="filter-row">
          <input
            placeholder="Filter by node id"
            value={filters.nodeId ?? ""}
            onChange={(event) => onFilterChange({ nodeId: event.target.value })}
          />
          <input
            placeholder="Filter by worktree"
            value={filters.worktree ?? ""}
            onChange={(event) =>
              onFilterChange({ worktree: event.target.value })
            }
          />
        </div>
        <div className="node-menu-list">
          {filteredNodes.length === 0 ? (
            <div className="event-item">Waiting for events...</div>
          ) : (
            filteredNodes.map((node) => (
              <GraphNodeCard
                key={node.id}
                nodeId={node.id}
                isSelected={activeNodeId === node.id}
                onSelect={onSelectNode}
              />
            ))
          )}
        </div>
        {unknownEvents.length > 0 ? (
          <div className="viewer">
            <h3>Unknown events</h3>
            <div className="event-list">
              {unknownEvents.map((event) => (
                <A2uiEventViewer key={event.id} event={event} />
              ))}
            </div>
          </div>
        ) : null}
      </aside>
      <section className="panel chat-panel">
        {activeNodeId ? (
          <NodeDetailsPanel nodeId={activeNodeId} />
        ) : (
          <div className="empty-chat">
            <h2>No node selected</h2>
            <p className="muted">
              Start a goal to populate the node list and open a chat stream.
            </p>
          </div>
        )}
      </section>
    </div>
  );
};
