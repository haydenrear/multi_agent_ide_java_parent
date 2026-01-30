"use client";

import { useEffect, useState, type FormEvent } from "react";
import { connectEventStream } from "../lib/eventStream";
import { GraphView } from "../components/GraphView";
import {
  graphActions,
  graphSelectors,
  useGraphNodes,
  useGraphStore,
} from "@/state/graphStore";

export default function Home() {
  const nodes = useGraphNodes();
  const filters = useGraphStore(graphSelectors.filters);
  const selectedNodeId = useGraphStore((state) => state.selectedNodeId);
  const unknownEvents = useGraphStore((state) => state.unknownEvents);
  const [goal, setGoal] = useState("");
  const [repositoryUrl, setRepositoryUrl] = useState("");
  const [baseBranch, setBaseBranch] = useState("main");
  const [submitStatus, setSubmitStatus] = useState<{
    state: "idle" | "submitting" | "success" | "error";
    message?: string;
  }>({ state: "idle" });

  useEffect(() => {
    console.log("Loading event stream ...")
    const streamUrl =
      process.env.NEXT_PUBLIC_EVENT_STREAM_URL ?? "/api/events/stream";
    const disconnect = connectEventStream({
      url: streamUrl,
      onEvent: graphActions.applyEvent,
      onError: () => {
        console.error("Event stream connection lost");
      },
    });

    return () => disconnect();
  }, []);

  const submitGoal = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!goal.trim() || !repositoryUrl.trim()) {
      setSubmitStatus({
        state: "error",
        message: "Provide a goal and repository URL to start.",
      });
      return;
    }
    setSubmitStatus({ state: "submitting" });
    try {
      const response = await fetch("/api/orchestrator/start", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          goal,
          repositoryUrl,
          baseBranch: baseBranch.trim() || "main",
        }),
      });
      if (!response.ok) {
        const text = await response.text();
        throw new Error(text || "Failed to start goal.");
      }
      setSubmitStatus({ state: "success", message: "Goal submitted." });
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Failed to start goal.";
      setSubmitStatus({ state: "error", message });
    }
  };

  return (
    <main>
      <header>
        <div>
          <h1>Agent Graph UI</h1>
          <p className="muted">Live signal from multi-agent orchestration.</p>
        </div>
        <span className="badge">{nodes.length} nodes</span>
      </header>
      <section className="panel goal-panel">
        <h2>Start a goal</h2>
        <p className="muted">
          Submit a goal to initialize a new orchestrator run.
        </p>
        <form className="goal-form" onSubmit={submitGoal}>
          <input
            data-testid="goal-input"
            placeholder="Goal description"
            value={goal}
            onChange={(event) => setGoal(event.target.value)}
          />
          <input
            data-testid="repo-input"
            placeholder="Repository URL or local path"
            value={repositoryUrl}
            onChange={(event) => setRepositoryUrl(event.target.value)}
          />
          <input
            data-testid="branch-input"
            placeholder="Base branch"
            value={baseBranch}
            onChange={(event) => setBaseBranch(event.target.value)}
          />
          <button
            data-testid="goal-submit"
            type="submit"
            disabled={submitStatus.state === "submitting"}
          >
            {submitStatus.state === "submitting" ? "Starting..." : "Start goal"}
          </button>
        </form>
        {submitStatus.message ? (
          <p className={`muted goal-status ${submitStatus.state}`}>
            {submitStatus.message}
          </p>
        ) : null}
      </section>
      <div className="layout">
        <GraphView
          nodes={nodes}
          selectedNodeId={selectedNodeId}
          unknownEvents={unknownEvents}
          filters={filters}
          onFilterChange={graphActions.setFilters}
          onSelectNode={graphActions.selectNode}
        />
      </div>
    </main>
  );
}
