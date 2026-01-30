import React from "react";
import type { GraphNode } from "@/state/graphStore";
import { renderA2ui } from "@/lib/a2uiRegistry";

type GraphNodeSummaryProps = {
  node: GraphNode;
};

export const GraphNodeSummary = ({ node }: GraphNodeSummaryProps) => {
  return renderA2ui({
    payload: {
      renderer: "node-summary",
      sessionId: node.id,
      props: { node },
    },
  });
};
