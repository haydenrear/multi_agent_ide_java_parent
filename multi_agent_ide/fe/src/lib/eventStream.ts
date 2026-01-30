import type { AgUiEventEnvelope } from "../state/graphStore";

type EventStreamOptions = {
  url: string;
  onEvent: (event: AgUiEventEnvelope) => void;
  onError?: (error: Event) => void;
};

export const connectEventStream = ({
  url,
  onEvent,
  onError,
}: EventStreamOptions) => {
  const source = new EventSource(url);

  const handleMessage = (message: MessageEvent) => {
    try {
      const parsed = JSON.parse(message.data) as AgUiEventEnvelope;
      onEvent(parsed);
    } catch (error) {
      console.error("Failed to parse event stream payload", error);
    }
  };

  source.addEventListener("ag-ui", handleMessage);
  source.onmessage = handleMessage;

  source.onerror = (event) => {
    if (onError) {
      onError(event);
    }
  };

  return () => {
    source.removeEventListener("ag-ui", handleMessage);
    source.close();
  };
};
