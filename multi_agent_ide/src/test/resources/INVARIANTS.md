# Trace Invariants

Invariants validated against markdown trace data (`*.md`, `*.graph.md`, `*.events.md`, `*.blackboard.md`).
Each invariant references the SURFACE.md scenarios it applies to.

## Validation Run: 2026-03-27

| Invariant | Status | Notes |
|-----------|--------|-------|
| G1 | PASS (refined) | Compound keys `ak:ULID/ULID` are valid (from `createChild()`) |
| G2 | PASS | No orphan parents in workflow-created nodes |
| G3 | PASS (refined) | `WAITING_INPUT→RUNNING` allowed for interrupt resume |
| G4 | PASS | Exactly 1 root OrchestratorNode per workflow |
| G5 | PASS | All nodeIds unique (truncation in trace is cosmetic) |
| G6 | PASS | Node count never decreases |
| G7 | PASS (refined) | Added `workflow/review_resolution` to known set |
| G8 | PASS | All template→response type mappings correct |
| G9 | PASS | Event timestamps non-decreasing |
| G10 | PASS | Blackboard history grows monotonically |
| A-INV2 | KNOWN GAP | Final snapshot taken BEFORE result decorator completes nodes — see Trace Timing below |
| C-INV1 | PASS (refined) | Some tests use `InterruptStatusEvent` RESOLVED instead of `ResolveInterruptEvent` |
| D-INV1–2 | PASS | A2A nodes complete correctly, required fields all non-null |
| J-INV2 | PASS | AgentCallStarted/Completed events paired correctly |
| J4 | PASS | Exactly 1 GoalCompletedEvent per workflow |
| N-INV1 | PASS | callController publishes HUMAN_REVIEW interrupt, agent blocks until resolved |
| N-INV2 | PASS | callController returns controller's response text after resolution |
| N-INV3 | PASS | 4th call (budget=3) returns ERROR, only 3 INITIATED events emitted |
| N-INV4 | PASS | INITIATED and RETURNED AgentCallEvents emitted with target="controller" |
| N-INV5 | PASS | A2A node target fields (targetAgentKey, targetAgentType) populated correctly |
| N-INV6 | — | Not yet validated (needs dedicated unit test for prompt contributor factory) |
| H-INV3 | — | Not yet validated (needs unit test verifying LlmCallDecorators don't modify tcc()) |
| H-INV4 | — | Not yet validated (needs integration test verifying topology tools in ToolContext) |
| H-INV5 | — | Not yet validated (needs unit test on decorator ordering) |
| O-INV1 | — | Not yet validated (needs assertion: A2A request chatId ≠ sourceSessionId) |
| O-INV2 | — | Not yet validated (needs C2A request chatId test) |
| O-INV3 | — | Not yet validated (needs log assertion for A2C chatId() call) |
| O-INV4 | — | Not yet validated (needs chatId on A2A node assertion in existing D1 test) |
| O-INV5 | PASS (indirect) | Covered by existing F2 test (chatId plumbing verified) |
| O-INV6 | — | Not yet validated (needs exhaustive switch coverage test) |

### Trace Timing Gap (A-INV2)

The graph snapshot is taken inside `QueuedLlmRunner.runWithTemplate()` — BEFORE `decorateRouting` completes the node. The final `orchestrator_collector` call appears with `CollectorNode` RUNNING and 10 nodes PENDING. The actual runtime graph IS correct (test assertions verify COMPLETED status). **TODO**: Add a post-completion snapshot after `runAgentFrom` returns.

### Future: Recursive Invariant Decomposition

Each invariant listed here should eventually be expanded into its own group of sub-invariants forming a test tensor. Example: G3 (Status Monotonicity) would decompose into per-node-type, per-transition, per-scenario sub-invariants. This recursive decomposition continues until the desired granularity is reached.

---

## Global Invariants (apply to ALL scenarios)

### G1. ArtifactKey Format
**Applies to**: All scenarios
**Invariant**: Every `nodeId`, `parentNodeId`, `callingNodeId`, `targetNodeId`, `originatingA2ANodeId`, `contextId`, and `artifactKey.value` must match `ak:[0-9A-Z]{26}` (simple key) or `ak:[0-9A-Z]{26}(/[0-9A-Z]{26})*` (compound child key from `ArtifactKey.createChild()`).
**Search**: `*.graph.md`, `*.md` — all `ak:` references.

### G2. Parent Chain Integrity
**Applies to**: All scenarios (especially A1, A2, G1)
**Invariant**: Every non-root node's `parentNodeId` must reference an existing `nodeId` in the same snapshot. OrchestratorNode has parent `—`.
**Search**: `*.graph.md` — every `Parent` value ≠ `—` must appear as a `NodeId`.

### G3. Status Monotonicity
**Applies to**: All scenarios (especially G3, B1–B4)
**Invariant**: Node status never goes backward. Allowed transitions: RUNNING→PENDING, RUNNING→COMPLETED, PENDING→COMPLETED, PENDING→RUNNING (rerunnable nodes only), WAITING_INPUT→RUNNING (interrupt resume), WAITING_REVIEW→RUNNING (interrupt resume).
**Exception**: Rerunnable nodes (DiscoveryOrchestratorNode, collectors, dispatchers) in loopback scenarios (B1–B4) may transition PENDING→RUNNING when reused. Interrupt-paused nodes may transition WAITING_INPUT/WAITING_REVIEW→RUNNING when the interrupt is resolved.
**Search**: Track each `NodeId` across successive snapshots in `*.graph.md`.

### G4. Exactly One Root OrchestratorNode
**Applies to**: All scenarios (A1–A4, G1)
**Invariant**: Final snapshot has exactly one `OrchestratorNode` with parent `—`.
**Search**: `*.graph.md` — last snapshot, count `OrchestratorNode` rows with `Parent` = `—`.

### G5. Distinct NodeIds
**Applies to**: All scenarios (G2, A2)
**Invariant**: No two nodes share the same `NodeId` in any single snapshot.
**Search**: `*.graph.md` — extract all `NodeId` values per snapshot, check for duplicates.

### G6. Node Count Monotonically Increases
**Applies to**: All scenarios (G3, B1–B4)
**Invariant**: `Total nodes` in successive snapshots is ≥ previous.
**Search**: `*.graph.md` — extract `Total nodes` per snapshot.

### G7. LLM Call Template Names Are Valid
**Applies to**: All scenarios (H1, H2)
**Invariant**: Template names in `## After Call N:` must be in: `workflow/orchestrator`, `workflow/discovery_orchestrator`, `workflow/discovery_agent`, `workflow/discovery_dispatch`, `workflow/discovery_collector`, `workflow/planning_orchestrator`, `workflow/planning_agent`, `workflow/planning_dispatch`, `workflow/planning_collector`, `workflow/ticket_orchestrator`, `workflow/ticket_agent`, `workflow/ticket_dispatch`, `workflow/ticket_collector`, `workflow/orchestrator_collector`, `workflow/context_manager`, `workflow/interrupt_handler`, `workflow/review_resolution`, `communication/agent_call`, `communication/controller_call`, `communication/controller_response`.
**Search**: `*.graph.md` — extract template names from headers.

### G8. Request/Response Type Consistency
**Applies to**: All scenarios (H1, H2)
**Invariant**: Response type matches template: `workflow/orchestrator`→`OrchestratorRouting`, `workflow/discovery_orchestrator`→`DiscoveryOrchestratorRouting`, `workflow/discovery_agent`→`DiscoveryAgentRouting`, `workflow/discovery_dispatch`→`DiscoveryAgentDispatchRouting`, `workflow/discovery_collector`→`DiscoveryCollectorRouting`, `communication/agent_call`→`AgentCallRouting`, etc.
**Search**: `*.md` — match `Call N: \`template\`` with `Response type`.

### G9. Event Temporal Ordering
**Applies to**: All scenarios (J1)
**Invariant**: Events in `*.events.md` have non-decreasing timestamps.
**Search**: Extract consecutive `timestamp` fields.

### G10. Blackboard History Grows Monotonically
**Applies to**: All scenarios (I4)
**Invariant**: `History entries` count in `*.blackboard.md` never decreases.
**Search**: Extract `History entries: \`N\`` per call section.

---

## A. Full Workflow Lifecycle

### A-INV1. All Phase Nodes Created (A1)
**Invariant**: Final graph contains exactly: 1 OrchestratorNode, 1 DiscoveryOrchestratorNode, 1 DiscoveryDispatchAgentNode, ≥1 DiscoveryNode, 1 DiscoveryCollectorNode, 1 PlanningOrchestratorNode, 1 PlanningDispatchAgentNode, ≥1 PlanningNode, 1 PlanningCollectorNode, 1 TicketOrchestratorNode, 1 TicketDispatchAgentNode, ≥1 TicketNode, 1 TicketCollectorNode, 1 OrchestratorCollectorNode.
**Search**: `*.graph.md` — last snapshot, count by `Type`.

### A-INV2. All Nodes Terminal at Completion (A1, G4)
**Invariant**: In final snapshot, every node status is COMPLETED, FAILED, PRUNED, or CANCELED. No RUNNING or PENDING.
**Search**: `*.graph.md` — last snapshot, check `Status` column.

### A-INV3. GoalCompletedEvent Fires Exactly Once (A1, J4)
**Invariant**: Exactly one `GoalCompletedEvent` in event stream.
**Search**: `*.events.md` — count `GoalCompletedEvent` entries.

### A-INV4. Parallel Agent Fan-Out (A2)
**Invariant**: When N agents dispatched, exactly N DiscoveryNode children under one DiscoveryDispatchAgentNode.
**Search**: `*.graph.md` — count DiscoveryNode rows sharing same parent.

### A-INV5. Template Sequence for Happy Path (A1)
**Invariant**: Templates appear in order: `workflow/orchestrator`, `workflow/discovery_orchestrator`, `workflow/discovery_agent` (×N), `workflow/discovery_dispatch` (×N), `workflow/discovery_collector`, `workflow/planning_orchestrator`, ..., `workflow/orchestrator_collector`.
**Search**: `*.graph.md` — extract template sequence from call headers.

---

## B. Collector Loopback

### B-INV1. Rerunnable Node Same ID (B1, G5)
**Invariant**: After collector loopback, the DiscoveryOrchestratorNode retains the same `nodeId` as before loopback. No duplicate orchestrator nodes.
**Search**: `*.graph.md` — track DiscoveryOrchestratorNode nodeId across snapshots before and after loopback.

### B-INV2. Loopback Increases Template Count (B1, B2)
**Invariant**: A loopback scenario has more LLM calls than a straight-through scenario. The extra calls correspond to the re-executed phase.
**Search**: `*.graph.md` — count total `## After Call` sections. Compare with expected.

### B-INV3. Cross-Phase Loopback Creates Additional Nodes (B2)
**Invariant**: When planning loops back to discovery, new DiscoveryNode(s) appear in addition to the first-pass nodes.
**Search**: `*.graph.md` — count DiscoveryNode rows in final snapshot. Must be > initial count.

### B-INV4. No Infinite Loop (B3, K2)
**Invariant**: Total LLM calls stay bounded. If stuck handler fires, NodeErrorEvent published with loop details.
**Search**: `*.md` — total call count < reasonable bound (e.g., 30). `*.events.md` — check for NodeErrorEvent if loop exceeded.

---

## C. Interrupt Handling

### C-INV1. Interrupt Resolution Before Completion (C1–C8)
**Invariant**: In interrupt tests, either `ResolveInterruptEvent` or `InterruptStatusEvent` with RESOLVED status appears before `GoalCompletedEvent`.
**Search**: `*.events.md` — event ordering. Check for either event type.

### C-INV2. InterruptContext on Node (C1, C2)
**Invariant**: After interrupt, the interrupted node's snapshot shows interrupt-related status (WAITING_REVIEW or WAITING_INPUT).
**Search**: `*.graph.md` — check status of interrupted node in snapshots during interrupt window.

### C-INV3. Feedback in Next LLM Call (C8)
**Invariant**: After interrupt resolution with feedback notes, the next `*.md` call record contains `interruptFeedback` in the decorated request JSON.
**Search**: `*.md` — find call immediately after interrupt resolution, check request JSON for feedback.

### C-INV4. Interrupt at Each Phase Level (C1–C5)
**Invariant**: Interrupts at orchestrator, discovery agent, collector, planning dispatch, and orchestrator collector all produce InterruptStatusEvent with correct nodeId.
**Search**: `*.events.md` — InterruptStatusEvent nodeId matches the phase-level node.

### C-INV5. Pending Interrupt Persists (C7)
**Invariant**: Between interrupt publication and resolution, the interrupt remains in pendingInterrupts. awaitInterruptBlocking does not return prematurely.
**Search**: Code-level assertion (not trace-based). Test verifies permissionGate.isInterruptPending returns true during window.

---

## D. Agent-to-Agent Communication

### D-INV1. A2A Node Lifecycle (D1, D5)
**Invariant**: Every `AgentToAgentConversationNode` transitions RUNNING→COMPLETED. None remain RUNNING in final snapshot.
**Search**: `*.graph.md` — track A2A nodes across snapshots.

### D-INV2. A2A Required Fields (D1, D6)
**Invariant**: Every A2A detail section has non-null `source`, `target`, `callingNodeId`, `targetNodeId`, `sourceSessionId`, `chatId`. `originatingA2ANodeId` null only for first-level calls.
**Search**: `*.graph.md` — `### AgentToAgent:` sections.

### D-INV3. Chained Call Chain Growth (D2)
**Invariant**: In chained call scenario, second A2A node's call chain contains the first call's entry.
**Search**: `*.graph.md` — check `callChain` field in second A2A detail section.

### D-INV4. Self-Call Produces No A2A Node (D3)
**Invariant**: When self-call rejected, no AgentToAgentConversationNode appears in graph. callAgent returns ERROR string.
**Search**: `*.graph.md` — zero A2A nodes. Test assertion on return value.

### D-INV5. Topology Rejection Produces No A2A Node (D4)
**Invariant**: When topology rule blocks call, no A2A node created. ERROR message mentions topology.
**Search**: Same as D-INV4.

### D-INV6. Depth Limit Error Message (D5)
**Invariant**: When call chain depth exceeded, error message includes actual depth and maximum.
**Search**: Test assertion on return value string matching pattern.

### D-INV7. Loop Detection Error (D6)
**Invariant**: When A→B→C→A attempted, C's callAgent returns error mentioning cycle. No A2A node for the cyclic call.
**Search**: Test assertion + graph check.

### D-INV8. list_agents Excludes Self (D8)
**Invariant**: list_agents result never includes the caller's own session key.
**Search**: Test assertion on returned entries.

### D-INV9. list_agents Topology Filtering (D8)
**Invariant**: Entries in list_agents with `callable=false` correspond to disallowed topology pairs.
**Search**: Test assertion cross-referencing allowedCommunications config.

---

## E. Worktree Merge

### E-INV1. Changes Reach Source (E1)
**Invariant**: After final merge to source, source repo contains files committed by agents.
**Search**: Git assertion — file exists in source HEAD.

### E-INV2. Parallel Merges Non-Conflicting (E2)
**Invariant**: When two agents modify different files, both merges succeed. MergeAggregation.conflicted is empty.
**Search**: Test assertion. `*.events.md` — MergePhaseCompletedEvent with success=true.

### E-INV3. Conflict Detection (E3)
**Invariant**: When two agents modify same file, exactly one merge fails. MergeAggregation.conflicted has 1 entry. MergePhaseCompletedEvent has conflict info.
**Search**: Test assertion + `*.events.md`.

### E-INV4. Submodule Pointer Updated (E4)
**Invariant**: After submodule merge, source repo's submodule pointer references the merged commit.
**Search**: Git assertion — `git submodule status` in source.

### E-INV5. MergePhaseStarted/Completed Pairing (E1–E5, J5)
**Invariant**: Every MergePhaseStartedEvent has matching MergePhaseCompletedEvent.
**Search**: `*.events.md` — pair by nodeId and phase.

---

## F. Data Layer Operations

### F-INV1. DataLayer Node Created and Completed (F1)
**Invariant**: Every DataLayerOperationNode transitions RUNNING→COMPLETED. operationType and chatId non-null.
**Search**: `*.graph.md` — `### DataLayer:` sections.

### F-INV2. chatId Matches Workflow Session (F2)
**Invariant**: DataLayerOperationNode.chatId is a valid `ak:` ULID that appears as a nodeId in the graph.
**Search**: `*.graph.md` — cross-reference chatId with NodeId column.

### F-INV3. Parallel Agents Have Distinct chatIds (F3)
**Invariant**: When N agents run in parallel, their DataLayerOperationNodes have N distinct chatId values.
**Search**: `*.graph.md` — extract chatIds from DataLayer sections, check uniqueness.

---

## H. Decorator Chain

### H-INV1. A2A Routing Uses Direct Blackboard Lookup (H4)
**Invariant**: AgentCallRouting result resolution finds AgentToAgentConversationNode via `getLastOfType(AgentToAgentRequest.class)`, not via `findLastWorkflowRequest`. The A2A node transitions to COMPLETED.
**Search**: `*.graph.md` — A2A node status = COMPLETED after `communication/agent_call` template.

### H-INV2. DataLayer Results Self-Addressed (H5)
**Invariant**: DataLayer result types (AiFilter, AiPropagator, CommitAgent, MergeConflict) resolve their node via result's own contextId. DataLayerOperationNode transitions to COMPLETED.
**Search**: `*.graph.md` — DataLayer node status after corresponding call.

### H-INV3. ToolContextDecorators Run Before LlmCallDecorators (H6)
**Invariant**: All `ToolContextDecorator` implementations contribute to the `ToolContext` during `DecorateRequestResults.decorateToolContext()`, which executes in the agent pipeline before `DefaultLlmRunner.runWithTemplate()`. No `LlmCallDecorator` modifies tool context — only prompt-level concerns (FilterProperties, ArtifactEmission, PromptHealthCheck).
**Search**: Code-level assertion. Verify `DefaultLlmRunner.llmCallDecorators` contains zero instances that modify `tcc()`.

### H-INV4. Topology Tools Present in Every Agent ToolContext (H7)
**Invariant**: After `decorateToolContext()` completes, the resulting `ToolContext.tools()` contains a `ToolAbstraction` wrapping `AgentTopologyTools` (which exposes call_controller, call_agent, list_agents). This holds for all agent types regardless of profile.
**Search**: Code-level unit test. Integration evidence: agents can invoke `call_controller` during live workflow runs.

### H-INV5. ToolContextDecorator Order Matches Declared Priority (H8)
**Invariant**: `DecorateRequestResults.decorateToolContext()` sorts decorators by `order()` before applying. RemoveIntellij (-10_001) < AddIntellij/AddMemoryToolCallDecorator/AddAcpTools (-10_000) < AddTopologyTools (-9_000) < AddSkillToolContextDecorator (0).
**Search**: Code-level unit test on sorted decorator list.

---

## I. BlackboardHistory

### I-INV1. findLastWorkflowRequest Skips Non-Workflow Types (I1)
**Invariant**: After AgentToAgentRequest, AiFilterRequest, etc. are on the blackboard, `findLastWorkflowRequest` still returns the most recent workflow request (e.g., DiscoveryAgentRequest).
**Search**: Code-level unit test. Trace evidence: workflow proceeds normally after A2A/filter calls.

### I-INV2. Direct Type Lookup Finds Filtered Requests (I2)
**Invariant**: `getLastOfType(AgentToAgentRequest.class)` returns the request even though `findLastWorkflowRequest` skips it.
**Search**: Code-level unit test. Trace evidence: A2A node completes (H-INV1).

### I-INV3. State Accumulates Across Phases (I3)
**Invariant**: WorkflowGraphState after planning has both discoveryOrchestratorNodeId and planningOrchestratorNodeId set.
**Search**: `*.blackboard.md` — check state fields in later call sections.

---

## J. Events

### J-INV1. NodeAddedEvent Before Status Changes (J2)
**Invariant**: Every NodeStatusChangedEvent.nodeId has an earlier NodeAddedEvent or NodeUpdatedEvent for same nodeId.
**Search**: `*.events.md` — collect nodeIds from status change events, verify prior add event.

### J-INV2. AgentCallStarted/Completed Pairing (J3, D-INV1)
**Invariant**: Every AgentCallStartedEvent.callId has exactly one matching AgentCallCompletedEvent.callId.
**Search**: `*.events.md` — pair by callId.

### J-INV3. InterruptStatusEvent Sequence (J6)
**Invariant**: For each interrupt: REQUESTED appears before RESOLVED. No backward transitions.
**Search**: `*.events.md` — track InterruptStatusEvent by nodeId.

---

## K. Stuck Handler

### K-INV1. Max Invocations Guard (K2)
**Invariant**: After 3 ContextManagerRequest with reason="stuck-handler", next attempt returns NO_RESOLUTION and publishes NodeErrorEvent.
**Search**: `*.events.md` — NodeErrorEvent with degenerate loop message. `*.blackboard.md` — ContextManagerRequest count.

---

## L. Artifacts

### L-INV1. Artifact Tree Exists After Completion (L1)
**Invariant**: After workflow completes, ArtifactEntity tree has root with non-null artifactKey matching orchestrator nodeId.
**Search**: Test assertion on ArtifactRepository.

---

## N. Agent-to-Controller Communication

### N-INV1. callController Publishes HUMAN_REVIEW Interrupt (N1)
**Invariant**: When `callController` is invoked, a HUMAN_REVIEW interrupt is published via `permissionGate.publishInterrupt`. The interrupt's originNodeId matches the calling node's nodeId. The agent thread blocks until `resolveInterrupt` is called.
**Search**: `*.events.md` — InterruptStatusEvent with type=HUMAN_REVIEW during callController flow. Code-level assertion on PermissionGate.

### N-INV2. callController Resolution Returns Controller Text (N1)
**Invariant**: After the controller resolves the HUMAN_REVIEW interrupt with response text, `callController` returns that exact text. If resolution has empty notes, returns default message.
**Search**: Test assertion on returned string.

### N-INV3. Message Budget Enforced Before Interrupt (N2)
**Invariant**: When per-session message count exceeds `communicationTopology.messageBudget`, `callController` returns error WITHOUT publishing an interrupt. The counter increments atomically per session.
**Search**: Test assertion. Verify `permissionGate.publishInterrupt` NOT called when budget exceeded.

### N-INV4. AgentCallEvent Emitted for Controller Calls (N1)
**Invariant**: `callController` emits AgentCallEvent at INITIATED (before blocking) and RETURNED (after response) stages. On error, emits ERROR event. Target is always "controller".
**Search**: `*.events.md` — AgentCallEvent entries with target="controller".

### N-INV5. CallChainEntry Target Fields Populated (N4, D-INV2)
**Invariant**: Every CallChainEntry in a callAgent flow has non-null `targetAgentKey` and `targetAgentType` fields. In chained calls (D2), each hop's target fields match the subsequent call's source fields.
**Search**: `*.graph.md` — A2A detail sections, callChain field.

### N-INV6. Topology Prompt Excluded for Communication Requests (N5)
**Invariant**: AgentTopologyPromptContributorFactory produces zero contributors when the request type is AgentToAgentRequest, AgentToControllerRequest, or ControllerToAgentRequest. For standard workflow requests, it produces a contributor containing available agent topology XML.
**Search**: Code-level unit test.

---

## O. Chat ID Traceability (sourceSessionId / chatId)

The chatId field identifies the target ACP session for LLM routing. The sourceSessionId identifies the calling agent's session header. Previously these were conflated as `chatSessionKey`, which caused calls to route to the caller's session instead of the target's. These invariants ensure chatId is set correctly for every request type and graph node type.

### O-INV1. AgentToAgentRequest chatId Points to Target (O1, D-INV2)
**Invariant**: `AgentToAgentRequest.chatId()` is non-null and equals `new ArtifactKey(targetNodeId)` when targetNodeId is set, or `targetAgentKey` when targetNodeId is blank. It must NOT equal `sourceAgentKey` or `sourceSessionId`.
**Search**: Code-level assertion. Trace evidence: `### AgentToAgent:` sections — chatId ≠ sourceSessionId.

### O-INV2. ControllerToAgentRequest chatId Points to Target (O2)
**Invariant**: `ControllerToAgentRequest.chatId()` is non-null and matches the target agent's ACP session key. Fallback: `targetAgentKey` when chatId not explicitly set.
**Search**: Code-level assertion on `PromptContext.chatId()` for ControllerToAgentRequest.

### O-INV3. AgentToControllerRequest chatId Logs Error (O3)
**Invariant**: `PromptContext.chatId()` for `AgentToControllerRequest` logs an error (because A2C routes through the permission gate, not ACP) and returns `sourceAgentKey` as fallback. No ACP call should be made for this request type.
**Search**: Code-level assertion. Runtime log — error message "chatId() called on AgentToControllerRequest".

### O-INV4. AgentToAgentConversationNode chatId Matches Request chatId (O4, D-INV2)
**Invariant**: `AgentToAgentConversationNode.chatId()` equals `request.chatId().value()`. `sourceSessionId()` equals `request.sourceSessionId()`. `HasChatId.chatId()` (used by `SessionKeyResolutionService.resolveNodeBySessionKey()`) returns the target session key, enabling lookup by the target's session rather than the caller's.
**Search**: `*.graph.md` — `### AgentToAgent:` sections. Verify chatId is an `ak:` key different from sourceSessionId.

### O-INV5. DataLayerOperationNode chatId from Request chatKey (O5, F-INV2)
**Invariant**: `DataLayerOperationNode.chatId()` equals the owning agent's ACP session key, derived from `request.chatKey().value()` in `StartWorkflowRequestDecorator`. For AiFilter/AiPropagator/AiTransformer/CommitAgent/MergeConflict types, this is the parent agent's key.
**Search**: `*.graph.md` — `### DataLayer:` sections. Cross-reference chatId with parent agent nodeId.

### O-INV6. PromptContext.chatId() Dispatch Table (O1–O6)
**Invariant**: `PromptContext.chatId()` switch expression covers all request types:
- `AgentToAgentRequest` → `aar.chatId()` (fallback: targetNodeId/targetAgentKey)
- `AgentToControllerRequest` → logs error, yields `sourceAgentKey`
- `ControllerToAgentRequest` → `car.chatId()` (fallback: targetAgentKey)
- `CommitAgentRequest` → `chatKey()` ?? `contextId()`
- `MergeConflictRequest` → `chatKey()` ?? `contextId()`
- `AiFilterRequest/AiPropagatorRequest/AiTransformerRequest` → `chatKey()` ?? `contextId()`
- Default `AgentRequest` → `contextId()`
**Search**: Code inspection of `PromptContext.chatId()`. All branches must be exhaustive over the sealed `AgentRequest` hierarchy.

---

## Coverage: Surface → Invariants

| Surface | Invariants |
|---------|-----------|
| A1 | G1–G10, A-INV1–5 |
| A2 | G1–G10, A-INV1–4 |
| A3 | G1–G10, A-INV2–3 |
| A4 | G1–G10, A-INV2–3 |
| B1 | G1–G10, B-INV1–2, G3 exception |
| B2 | G1–G10, B-INV2–3 |
| B3 | G1–G10, B-INV4 |
| B4 | G1–G10, B-INV1–2 |
| C1 | G1–G10, C-INV1–2, C-INV4 |
| C2 | G1–G10, C-INV1–2, C-INV4 |
| C3 | G1–G10, C-INV1, C-INV4 |
| C4 | G1–G10, C-INV1, C-INV4 |
| C5 | G1–G10, C-INV1, C-INV4 |
| C6 | G1–G10, C-INV2 |
| C7 | C-INV5 |
| C8 | C-INV3 |
| D1 | G1–G10, D-INV1–2, H-INV1 |
| D2 | G1–G10, D-INV1–3 |
| D3 | D-INV4 |
| D4 | D-INV5 |
| D5 | D-INV6 |
| D6 | D-INV7 |
| D7 | D-INV1 (partial) |
| D8 | D-INV8–9 |
| D9 | D-INV8 |
| E1 | G1–G10, E-INV1, E-INV5 |
| E2 | G1–G10, E-INV2, E-INV5 |
| E3 | G1–G10, E-INV3, E-INV5 |
| E4 | G1–G10, E-INV4 |
| E5 | E-INV3 |
| E6 | — |
| F1 | G1–G10, F-INV1 |
| F2 | G1–G10, F-INV1–2 |
| F3 | G1–G10, F-INV1–3 |
| F4 | F-INV1 |
| F5 | F-INV1 |
| G1 | G2, A-INV1 |
| G2 | G5 |
| G3 | G3 |
| G4 | A-INV2 |
| G5 | B-INV1 |
| G6 | G2 |
| H1–H2 | G7, G8 |
| H3 | — (code-level) |
| H4 | H-INV1, I-INV2 |
| H5 | H-INV2 |
| H6 | H-INV3 |
| H7 | H-INV4 |
| H8 | H-INV5 |
| I1 | I-INV1 |
| I2 | I-INV2 |
| I3 | I-INV3 |
| I4 | G10 |
| I5 | — (code-level) |
| J1–J6 | G9, J-INV1–3 |
| K1–K2 | K-INV1, B-INV4 |
| K3 | — (code-level) |
| L1 | L-INV1 |
| L2 | — (code-level) |
| M1 | C-INV4 |
| M2 | C-INV4 |
| N1 | N-INV1, N-INV2, N-INV4 |
| N2 | N-INV3 |
| N3 | — (code-level) |
| N4 | N-INV5, D-INV2 |
| N5 | N-INV6 |
| N6 | — (code-level) |
| N7 | N-INV1–2 |
| N8 | — (code-level) |
| O1 | O-INV1, O-INV6 |
| O2 | O-INV2, O-INV6 |
| O3 | O-INV3, O-INV6 |
| O4 | O-INV4, D-INV2 |
| O5 | O-INV5, F-INV2 |
| O6 | O-INV6 |
