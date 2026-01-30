const baseUrl = process.env.NEXT_PUBLIC_CONTROL_API_URL ?? "";

const post = async (path: string) => {
  const response = await fetch(`${baseUrl}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`Control request failed: ${response.status}`);
  }

  return response.json().catch(() => ({}));
};

export const requestPause = (nodeId: string) =>
  post(`/api/agents/${nodeId}/pause`);
export const requestResume = (nodeId: string) =>
  post(`/api/agents/${nodeId}/resume`);
export const requestStop = (nodeId: string) =>
  post(`/api/agents/${nodeId}/stop`);
export const requestPrune = (nodeId: string) =>
  post(`/api/agents/${nodeId}/prune`);
export const requestBranch = (nodeId: string) =>
  post(`/api/agents/${nodeId}/branch`);
export const requestReview = (nodeId: string) =>
  post(`/api/agents/${nodeId}/review-request`);
