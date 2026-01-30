import type { UiStateSnapshot } from "../state/graphStore";

const baseUrl = process.env.NEXT_PUBLIC_CONTROL_API_URL ?? "";

const postJson = async (path: string, body: unknown) => {
  const response = await fetch(`${baseUrl}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw new Error(`GUI action failed: ${response.status}`);
  }

  return response.json().catch(() => ({}));
};

export const submitGuiFeedback = async (payload: {
  eventId: string;
  nodeId: string;
  message: string;
  snapshot?: UiStateSnapshot;
}) => {
  return postJson("/api/ui/feedback", payload);
};

export const requestUiRevert = async (payload: {
  eventId: string;
  nodeId: string;
}) => {
  return postJson("/api/ui/diff/revert", payload);
};

export const submitUiMessage = async (payload: {
  nodeId: string;
  message: string;
}) => {
  return postJson("/api/ui/message", payload);
};
