import type { GraphEventRecord, GraphNode } from "../state/graphStore";
import { A2uiCore } from "./a2uiBridge";

export type A2uiServerMessage = A2uiCore.Types.ServerToClientMessage;

const literalText = (text: string) => ({ literalString: text });

const textComponent = (
  id: string,
  text: string,
  usageHint: "h1" | "h2" | "h3" | "h4" | "h5" | "caption" | "body" = "body",
) => ({
  id,
  component: {
    Text: {
      text: literalText(text),
      usageHint,
    },
  },
});

const buildButtonComponents = (
  id: string,
  labelId: string,
  label: string,
  actionName: string,
  context: Record<string, string>,
) => {
  return [
    {
      id: labelId,
      component: {
        Text: {
          text: literalText(label),
          usageHint: "caption",
        },
      },
    },
    {
      id,
      component: {
        Button: {
          child: labelId,
          action: {
            name: actionName,
            context: Object.entries(context).map(([key, value]) => ({
              key,
              value: {
                literalString: value,
              },
            })),
          },
        },
      },
    },
  ];
};

export const buildPayloadMessages = (params: {
  title: string;
  payload?: unknown;
  timestamp?: string;
  eventId?: string;
  nodeId?: string;
  includeActions?: boolean;
  bodyText?: string;
}): A2uiServerMessage[] => {
  const {
    title,
    payload,
    timestamp,
    eventId,
    nodeId,
    includeActions,
    bodyText,
  } = params;
  const shouldIncludeActions = !!(includeActions && eventId && nodeId);
  const baseId = eventId ?? nodeId ?? title.replace(/\s+/g, "-").toLowerCase();
  const surfaceId = `payload-${baseId}`;
  const rootId = `${surfaceId}-root`;
  const headerId = `${surfaceId}-header`;
  const timeId = `${surfaceId}-time`;
  const payloadId = `${surfaceId}-payload`;
  const actionsId = `${surfaceId}-actions`;

  const payloadText = bodyText ?? JSON.stringify(payload ?? {}, null, 2);

  const components: Array<{ id: string; component: Record<string, unknown> }> =
    [
      {
        id: rootId,
        component: {
          Card: {
            child: `${surfaceId}-card-body`,
          },
        },
      },
      {
        id: `${surfaceId}-card-body`,
        component: {
          Column: {
            children: {
              explicitList: [
                headerId,
                ...(timestamp ? [timeId] : []),
                payloadId,
                ...(shouldIncludeActions ? [actionsId] : []),
              ],
            },
          },
        },
      },
      textComponent(headerId, title, "h4"),
      ...(timestamp ? [textComponent(timeId, timestamp, "caption")] : []),
      textComponent(payloadId, payloadText, "body"),
    ];

  if (shouldIncludeActions) {
    const feedbackId = `${surfaceId}-feedback`;
    const feedbackLabelId = `${feedbackId}-label`;
    const revertId = `${surfaceId}-revert`;
    const revertLabelId = `${revertId}-label`;

    const feedbackComponents = buildButtonComponents(
      feedbackId,
      feedbackLabelId,
      "Feedback",
      "ui.feedback",
      { eventId, nodeId, surfaceId },
    );
    const revertComponents = buildButtonComponents(
      revertId,
      revertLabelId,
      "Revert",
      "ui.revert",
      { eventId, nodeId, surfaceId },
    );

    components.push(...feedbackComponents, ...revertComponents, {
      id: actionsId,
      component: {
        Row: {
          children: {
            explicitList: [feedbackId, revertId],
          },
        },
      },
    });
  }

  return [
    {
      surfaceUpdate: {
        surfaceId,
        components,
      },
    },
    {
      beginRendering: {
        surfaceId,
        root: rootId,
      },
    },
  ];
};

export const buildEventMessages = (
  event: GraphEventRecord,
  includeActions: boolean,
): A2uiServerMessage[] => {
  const surfaceId = `event-${event.id}`;
  const rootId = `${surfaceId}-root`;
  const headerId = `${surfaceId}-header`;
  const timeId = `${surfaceId}-time`;
  const payloadId = `${surfaceId}-payload`;
  const actionsId = `${surfaceId}-actions`;

  const payloadText = JSON.stringify(
    event.payload ?? event.rawEvent ?? {},
    null,
    2,
  );

  const components: Array<{ id: string; component: Record<string, unknown> }> =
    [
      {
        id: rootId,
        component: {
          Card: {
            child: `${surfaceId}-card-body`,
          },
        },
      },
      {
        id: `${surfaceId}-card-body`,
        component: {
          Column: {
            children: {
              explicitList: [
                headerId,
                timeId,
                payloadId,
                ...(includeActions ? [actionsId] : []),
              ],
            },
          },
        },
      },
      textComponent(headerId, event.type, "h4"),
      textComponent(timeId, event.timestamp ?? "timestamp pending", "caption"),
      textComponent(payloadId, payloadText, "body"),
    ];

  if (includeActions) {
    const feedbackId = `${surfaceId}-feedback`;
    const feedbackLabelId = `${feedbackId}-label`;
    const revertId = `${surfaceId}-revert`;
    const revertLabelId = `${revertId}-label`;

    const feedbackComponents = buildButtonComponents(
      feedbackId,
      feedbackLabelId,
      "Feedback",
      "ui.feedback",
      { eventId: event.id, nodeId: event.nodeId ?? "", surfaceId },
    );
    const revertComponents = buildButtonComponents(
      revertId,
      revertLabelId,
      "Revert",
      "ui.revert",
      { eventId: event.id, nodeId: event.nodeId ?? "", surfaceId },
    );

    components.push(...feedbackComponents, ...revertComponents, {
      id: actionsId,
      component: {
        Row: {
          children: {
            explicitList: [feedbackId, revertId],
          },
        },
      },
    });
  }

  return [
    {
      surfaceUpdate: {
        surfaceId,
        components,
      },
    },
    {
      beginRendering: {
        surfaceId,
        root: rootId,
      },
    },
  ];
};

export const buildNodeSummaryMessages = (
  node: GraphNode,
): A2uiServerMessage[] => {
  const surfaceId = `node-${node.id}`;
  const rootId = `${surfaceId}-root`;
  const titleId = `${surfaceId}-title`;
  const statusId = `${surfaceId}-status`;
  const metaId = `${surfaceId}-meta`;

  const components: Array<{ id: string; component: Record<string, unknown> }> =
    [
      {
        id: rootId,
        component: {
          Column: {
            children: {
              explicitList: [statusId, titleId, metaId],
            },
          },
        },
      },
      textComponent(statusId, node.status ?? "UNKNOWN", "caption"),
      textComponent(titleId, node.title ?? node.id, "h4"),
      textComponent(metaId, node.nodeType ?? "Node", "caption"),
    ];

  return [
    {
      surfaceUpdate: {
        surfaceId,
        components,
      },
    },
    {
      beginRendering: {
        surfaceId,
        root: rootId,
      },
    },
  ];
};

export const buildControlMessages = (nodeId: string): A2uiServerMessage[] => {
  const surfaceId = `controls-${nodeId}`;
  const rootId = `${surfaceId}-root`;
  const rowId = `${surfaceId}-row`;

  const actions = [
    { id: "pause", label: "Pause", action: "control.pause" },
    { id: "resume", label: "Resume", action: "control.resume" },
    { id: "stop", label: "Stop", action: "control.stop" },
    { id: "review", label: "Review", action: "control.review" },
    { id: "prune", label: "Prune", action: "control.prune" },
    { id: "branch", label: "Branch", action: "control.branch" },
  ];

  const components: Array<{ id: string; component: Record<string, unknown> }> =
    [
      {
        id: rootId,
        component: {
          Row: {
            children: {
              explicitList: [rowId],
            },
          },
        },
      },
      {
        id: rowId,
        component: {
          Row: {
            children: {
              explicitList: actions.map(
                (action) => `${surfaceId}-${action.id}`,
              ),
            },
          },
        },
      },
    ];

  actions.forEach((action) => {
    const buttonId = `${surfaceId}-${action.id}`;
    const labelId = `${buttonId}-label`;
    const buttonComponents = buildButtonComponents(
      buttonId,
      labelId,
      action.label,
      action.action,
      { nodeId },
    );
    components.push(...buttonComponents);
  });

  return [
    {
      surfaceUpdate: {
        surfaceId,
        components,
      },
    },
    {
      beginRendering: {
        surfaceId,
        root: rootId,
      },
    },
  ];
};

export const extractA2uiMessages = (
  payload: unknown,
): A2uiServerMessage[] | null => {
  if (!payload || typeof payload !== "object") {
    return null;
  }
  const record = payload as { a2uiMessages?: unknown; messages?: unknown };
  const messages = record.a2uiMessages ?? record.messages;
  if (!Array.isArray(messages)) {
    return null;
  }
  return messages as A2uiServerMessage[];
};
