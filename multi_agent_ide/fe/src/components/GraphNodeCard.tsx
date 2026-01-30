import React from "react";
import type { GraphProps } from "../state/graphStore";
import { useGraphNode } from "../state/graphStore";
import { GraphNodeSummary } from "./GraphNodeSummary";

type GraphNodeCardProps = GraphProps & {
  isSelected: boolean;
  onSelect: (nodeId: string) => void;
};

export const GraphNodeCard = ({
  nodeId,
  isSelected,
  onSelect,
}: GraphNodeCardProps) => {
  const node = useGraphNode(nodeId);
  if (!node) {
    return null;
  }
  return (
    <div
      className={`node-menu-item${isSelected ? " active" : ""}`}
      data-node-id={node.id}
      onClick={() => onSelect(node.id)}
      role="button"
      tabIndex={0}
      onKeyDown={(event) => {
        if (event.key === "Enter") {
          onSelect(node.id);
        }
      }}
    >
      <GraphNodeSummary node={node} />
    </div>
  );
};
