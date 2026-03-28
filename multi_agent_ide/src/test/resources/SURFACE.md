# Minimum Viable Test Surface

Test scenarios derived from code analysis of the workflow agent system.
Each scenario targets a distinct code path, branch, or failure mode.
Organized by subsystem, then by priority (P0 = must-have, P1 = important, P2 = nice-to-have).

## Coverage Status (2026-03-27)

| Scenario | Covered By | Status |
|----------|-----------|--------|
| A1 | `fullWorkflow_discoveryToPlanningSingleAgentsToCompletion`, `happyPath_graphNodesCreatedWithCorrectParentage` | COVERED |
| A2 | — | **GAP (P0)** |
| A3 | `skipDiscovery_startAtPlanning` (stub only) | **GAP (P1)** |
| A4 | — | GAP (P2) |
| B1 | `discoveryCollector_loopsBackForMoreInvestigation` | COVERED |
| B2 | `planningCollector_loopsBackToDiscovery_needsMoreContext` | COVERED |
| B3 | `orchestratorCollector_loopsBackMultipleTimes` (stub only) | **GAP (P1)** |
| B4 | — | GAP (P1) |
| C1 | `orchestratorPause_resolveInterruptContinues`, `humanReviewInterrupt_*` (3 tests) | COVERED |
| C2 | `externalInterrupt_duringDiscoveryAgent_interruptHandledAndCompletes` | COVERED |
| C3 | `discoveryCollector_agentInitiatedInterrupt_resolvesAndContinues` | COVERED |
| C4 | `planningAgentDispatch_interruptAndResume` | COVERED |
| C5 | `orchestratorCollector_interruptBeforeFinalConsolidation` | COVERED |
| C6 | — | GAP (P2) |
| C7 | `humanReviewInterrupt_pendingInterruptNotRemovedBeforeResolution` | COVERED |
| C8 | `humanReviewInterrupt_workflowCompletesWithFeedbackAfterResolution` | COVERED |
| D1 | `callAgent_createsAndCompletesAgentToAgentNode` | COVERED |
| D2 | `callAgent_chainedCalls_callChainTrackedCorrectly` | COVERED |
| D3 | — | **GAP (P0)** |
| D4 | — | **GAP (P0)** |
| D5 | — | GAP (P1) |
| D6 | — | GAP (P1) |
| D7 | — | GAP (P1) |
| D8 | `listAgents_duringWorkflow_returnsCorrectTopology` | COVERED |
| D9 | — | GAP (P2) |
| E1 | `fullWorkflow_realMerges_changesReachSource` | COVERED |
| E2 | `discoveryPhase_twoAgents_bothMergeSuccessfully` | COVERED |
| E3 | `discoveryPhase_twoAgents_conflictDetected` | COVERED |
| E4 | `fullWorkflow_withSubmoduleChanges_propagateToSource` | COVERED |
| E5 | — | GAP (P2) |
| E6 | — | GAP (P2) |
| F1 | `dataLayerOperationNodes_createdDuringWorkflow` | COVERED |
| F2 | `propagator_chatSessionKey_setCorrectly` | COVERED |
| F3 | `parallelAgents_propagatorNodes_distinctChatKeys` | COVERED |
| F4–F5 | — | GAP (P1) |
| G1 | `happyPath_graphNodesCreatedWithCorrectParentage` | COVERED |
| H4 | `callAgent_createsAndCompletesAgentToAgentNode` | COVERED |
| I1–I2 | — (implicit via D1/H4 passing) | COVERED (indirect) |
| K1–K2 | — | **GAP (P1)** |
| L1 | `fullWorkflow_persistsArtifactTree` | COVERED |
| M1 | `externalInterrupt_atOrchestrator_storesInFilterPropertiesAndCompletes` | COVERED |
| M2 | `externalInterrupt_duringDiscoveryAgent_interruptHandledAndCompletes` | COVERED |
| N1 | `callController_happyPath_interruptResolvedAndAgentContinues` | COVERED |
| N2 | `callController_messageBudgetExceeded_returnsError` | COVERED |
| N3 | — | GAP (P1) |
| N4 | `callAgent_createsAndCompletesAgentToAgentNode` (enriched assertions) | COVERED |
| N5 | — | **GAP (P1)** |
| N6 | — | **GAP (P1)** |
| N7 | — | **GAP (P1)** |
| N8 | — | **GAP (P1)** |

---

## A. Full Workflow Lifecycle

### A1. Happy Path — Straight Through (P0)
Orchestrator → DiscoveryOrchestrator → DiscoveryDispatch (1 agent) → DiscoveryAgent → DiscoveryCollector → PlanningOrchestrator → PlanningDispatch (1 agent) → PlanningAgent → PlanningCollector → TicketOrchestrator → TicketDispatch (1 agent) → TicketAgent → TicketCollector → OrchestratorCollector → GoalCompleted.

**Validates**: Every phase transition fires. Every node created. Final status = all COMPLETED. GoalCompletedEvent fires exactly once.

### A2. Happy Path — Parallel Agents (P0)
Same as A1 but DiscoveryOrchestrator dispatches 3 agents in parallel. PlanningOrchestrator dispatches 2.

**Validates**: Fan-out creates N DiscoveryNode children under single DiscoveryDispatchAgentNode. All complete before collector runs. Node count matches expected.

### A3. Skip Discovery — Start at Planning (P1)
Orchestrator routes directly to PlanningOrchestrator (no discovery phase).

**Validates**: The orchestrator routing can skip phases. Discovery nodes never created. Planning proceeds normally.

### A4. Skip to Tickets — Minimal Path (P2)
Orchestrator routes directly to TicketOrchestrator.

**Validates**: Shortest possible workflow still creates correct graph structure and fires GoalCompletedEvent.

---

## B. Collector Loopback / Re-dispatch

### B1. Discovery Collector Loops Back to Discovery Orchestrator (P0)
DiscoveryCollector returns a DiscoveryCollectorRouting with `discoveryOrchestratorRequest` set (needs more investigation). Discovery re-runs with new agents, then collector succeeds.

**Validates**: Rerunnable node logic (`startOrReuseRerunnableNode`). DiscoveryOrchestratorNode reused (same nodeId, updated goal). No duplicate nodes. Second pass completes normally.

### B2. Planning Collector Loops Back to Discovery (P0)
PlanningCollector determines more context needed. Returns routing that sends workflow back to DiscoveryOrchestrator. Discovery runs again, then planning re-runs and succeeds.

**Validates**: Cross-phase loopback. Discovery nodes from second pass coexist with first-pass nodes. All eventually COMPLETED.

### B3. Orchestrator Collector Loops Back Multiple Times (P1)
OrchestratorCollector returns to discovery 3 times before finally completing.

**Validates**: Multiple re-dispatch cycles don't corrupt state. Node count grows monotonically. No infinite loop (if max reached, stuck handler fires).

### B4. Discovery Collector Loops Back Twice Then Succeeds (P1)
Two consecutive loopbacks from collector before final success.

**Validates**: Rerunnable nodes handle repeated reuse. Blackboard history grows but findLastWorkflowRequest still returns correct request.

---

## C. Interrupt Handling

### C1. Human Review Interrupt at Orchestrator (P0)
Orchestrator returns InterruptRequest with type=HUMAN_REVIEW. Workflow blocks. External resolution fires. Workflow resumes and completes.

**Validates**: PermissionGate.publishInterrupt called. awaitInterruptBlocking blocks until resolveInterrupt. InterruptContext stored on node with status REQUESTED→RESOLVED. Feedback passed to next LLM call.

### C2. Human Review Interrupt During Discovery Agent (P0)
Discovery agent (not orchestrator) returns interrupt. Agent blocks. External resolution resumes the agent, which completes. Workflow proceeds through remaining phases.

**Validates**: Interrupts work at non-orchestrator levels. Correct node receives InterruptContext. Discovery dispatch waits for interrupted agent.

### C3. Agent-Initiated Interrupt at Collector (P1)
Discovery collector returns AGENT_REVIEW interrupt. Review agent runs automatically. Resolution agent resolves. Collector continues with feedback.

**Validates**: AGENT_REVIEW path (no human gate). Review template called. Resolution routing returns to collector context.

### C4. Interrupt at Planning Dispatch (P1)
Planning agent dispatch returns interrupt during multi-agent fan-out. One agent interrupts while others complete.

**Validates**: Interrupt in parallel context. Other agents can complete while one blocks. Resume only resumes the interrupted agent.

### C5. Interrupt at Orchestrator Collector (P1)
Interrupt fires at the very last stage before goal completion.

**Validates**: Interrupt doesn't skip final merge/collection. GoalCompletedEvent fires only after interrupt resolved.

### C6. Interrupt with PAUSE Type (P2)
Orchestrator returns PAUSE interrupt. Workflow suspends.

**Validates**: PAUSE type handled differently from HUMAN_REVIEW. Status = WAITING_REVIEW. No automatic resolution.

### C7. Pending Interrupt Not Prematurely Removed (P0)
Race condition guard: interrupt entry must remain in pendingInterrupts map until resolveInterrupt is called.

**Validates**: awaitInterruptBlocking does not return early with invalidInterrupt. Thread-safety of interrupt storage.

### C8. Interrupt Feedback Reaches Next LLM Call (P1)
After human resolves with notes "Please reconsider X", the next LLM call includes interruptFeedback in its model.

**Validates**: Feedback plumbed from resolution → InterruptService.handleInterrupt → template model → LLM call.

---

## D. Agent-to-Agent Communication (Topology)

### D1. Single call_agent — Happy Path (P0)
Discovery agent calls a collector via call_agent tool. AgentToAgentConversationNode created with status RUNNING, then COMPLETED. Response returned to caller.

**Validates**: Full callAgent flow: validate → build request → decorate → LLM → decorate routing → complete A2A node. callingNodeId, targetNodeId, chatSessionKey all set.

### D2. Chained Calls — A Calls B Calls C (P0)
Discovery agent calls collector, which (in its callback) calls orchestrator. Two A2A nodes created. Call chain tracked correctly.

**Validates**: Call chain depth tracking. Both A2A nodes have distinct nodeIds. Both complete. callChain field grows with each hop.

### D3. Self-Call Rejection (P0)
Agent attempts to call itself (same session key). callAgent returns ERROR string.

**Validates**: filterSelfCalls removes same-key candidates. validateCall returns reject. No A2A node created.

### D4. Topology Rule Rejection (P0)
Agent of type DISCOVERY_AGENT attempts to call TICKET_AGENT (not in allowedCommunications map). Rejected.

**Validates**: CommunicationTopologyConfig.isCommunicationAllowed returns false. validateCall returns reject with topology error message. No A2A node created.

### D5. Call Chain Depth Exceeded (P1)
Call chain already at max depth (e.g., 5). Next callAgent attempt rejected.

**Validates**: `depth >= topologyConfig.maxCallChainDepth()` check. Error message includes depth and max.

### D6. Loop Detection via Call Chain (P1)
Agent A calls B, B calls C, C attempts to call A. Rejected because A is in the call chain.

**Validates**: Call chain contains A's key. validateCall detects cycle. Error returned, no infinite recursion.

### D7. Target Agent No Longer Available (P1)
callAgent targets a session key that existed at list_agents time but has since closed.

**Validates**: Session lookup fails. Returns "Agent is no longer available" error. No partial A2A node left in graph.

### D8. list_agents Returns Correct Topology (P0)
During active workflow with multiple sessions, list_agents returns only callable agents (filtered by self-call, topology rules).

**Validates**: Self-calls filtered. Topology-disallowed agents marked callable=false. Agent types correctly resolved from sessions.

### D9. list_agents with No Available Agents (P2)
Only the calling agent's session exists. list_agents returns empty list.

**Validates**: Graceful handling of empty result. No crash on empty topology.

---

## E. Worktree Merge Operations

### E1. Full Workflow — Changes Reach Source (P0)
Real git worktrees created. Agent commits to child worktree. Merge child→trunk succeeds. Final merge to source repo succeeds. Source repo contains agent's changes.

**Validates**: End-to-end worktree flow. Git operations succeed. No orphaned branches.

### E2. Two Parallel Agents — Both Merge Successfully (P0)
Two discovery agents each work on different files in their own worktrees. Both merge to trunk without conflict.

**Validates**: Parallel worktree isolation. Merge order doesn't matter when no conflicts. Both agents' changes present in trunk.

### E3. Two Parallel Agents — Merge Conflict (P0)
Two agents modify the same file. First merge succeeds, second merge detects conflict. MergeAggregation.conflicted populated.

**Validates**: Conflict detection works. MergePhaseCompletedEvent has conflict info. Workflow receives conflict data for LLM handling.

### E4. Submodule Changes Propagate to Source (P1)
Agent modifies files in a submodule worktree. Changes merge through submodule→main worktree→source.

**Validates**: Submodule pointer update after merge. Source repo's submodule reference updated. Leaves-first merge order for submodules.

### E5. Merge Conflict in Submodule (P2)
Two agents modify the same file in the same submodule. Conflict detected at submodule level.

**Validates**: Submodule conflict handling differs from main repo conflict. ensureMergeConflictsCaptured works for submodules.

### E6. Worktree Created and Discarded (P2)
Agent creates worktree, does no work, worktree is discarded. No artifacts remain.

**Validates**: Cleanup path. No orphaned git worktrees on disk.

---

## F. Data Layer Operations (Filter / Propagator / Transformer)

### F1. Data Layer Nodes Created During Workflow (P0)
Full workflow triggers filter, propagator, or transformer operations. DataLayerOperationNode created with correct operationType and chatSessionKey.

**Validates**: DataLayerOperationNode lifecycle. chatSessionKey correctly set from request context. Node transitions RUNNING→COMPLETED.

### F2. Propagator chatSessionKey Set Correctly (P0)
Propagator runs during discovery phase. chatSessionKey on the DataLayerOperationNode matches the workflow's session key.

**Validates**: chatSessionKey plumbed from request → startDataLayerOperation → node.

### F3. Parallel Agents — Distinct chatSessionKeys (P0)
Three parallel discovery agents each trigger propagators. Each DataLayerOperationNode has a distinct chatSessionKey.

**Validates**: Session isolation for data layer operations. No cross-contamination between parallel agent sessions.

### F4. CommitAgent Creates DataLayerOperationNode (P1)
WorktreeAutoCommitService runs after agent completes work. DataLayerOperationNode with operationType="CommitAgent" created and completed.

**Validates**: CommitAgent integrated into data layer operation tracking.

### F5. MergeConflictAgent Creates DataLayerOperationNode (P1)
Merge conflict detected. MergeConflictAgent fires. DataLayerOperationNode with operationType="MergeConflictAgent" created.

**Validates**: Conflict resolution tracked as data layer operation.

---

## G. Graph Node Integrity

### G1. Happy Path — Correct Parentage (P0)
Full workflow. Verify parent-child relationships:
- OrchestratorNode: root (no parent)
- DiscoveryOrchestratorNode: parent = OrchestratorNode
- DiscoveryDispatchAgentNode: parent = DiscoveryOrchestratorNode
- DiscoveryNode: parent = DiscoveryDispatchAgentNode
- DiscoveryCollectorNode: parent = DiscoveryOrchestratorNode
- PlanningOrchestratorNode: parent = OrchestratorNode
- (same pattern for planning/ticket)
- OrchestratorCollectorNode: parent = OrchestratorNode

**Validates**: Every node's parentNodeId references an existing node. Tree structure is correct.

### G2. All NodeIds Are Unique (P0)
No two nodes in the graph share the same nodeId.

**Validates**: ArtifactKey.createChild() produces unique ULIDs. No accidental reuse.

### G3. Node Status Monotonicity (P0)
Track each node across snapshots. Status never goes backward (COMPLETED→RUNNING is a bug).

**Validates**: Decorators don't re-process completed nodes. Status transitions are forward-only.

### G4. Final State — No RUNNING Nodes (P0)
After workflow completes, every node is in terminal state (COMPLETED, FAILED, PRUNED, or CANCELED). No RUNNING or PENDING nodes remain.

**Validates**: completeOrchestratorCollectorResult marks all descendants. No leaked RUNNING state.

### G5. Rerunnable Node Reuse (P1)
When collector loops back, the same DiscoveryOrchestratorNode is reused (same nodeId) with updated goal.

**Validates**: startOrReuseRerunnableNode finds existing node, updates it, returns same ID. State field updated.

### G6. Missing Parent Handled Gracefully (P2)
A dispatch creates child nodes before the dispatch node itself is persisted (race in event ordering). Child node creation doesn't crash.

**Validates**: ComputationGraphOrchestrator.addChildNodeAndEmitEvent logs warn but continues when parent not found.

---

## H. Decorator Chain

### H1. Request Decorators Execute in Order (P0)
Verify that for a standard workflow request:
1. EmitActionStartedRequestDecorator fires first (Integer.MIN_VALUE)
2. RegisterBlackboardHistoryInputRequestDecorator registers the request
3. StartWorkflowRequestDecorator creates the graph node
4. WorktreeContextRequestDecorator attaches worktree context

**Validates**: Decorator ordering by getOrder(). Each decorator's side effect observed in sequence.

### H2. Result Decorators Execute in Order (P0)
For a standard routing result:
1. WorkflowGraphResultDecorator completes the graph node
2. FinalResultDecorator runs cleanup (Integer.MAX_VALUE)

**Validates**: Result decorator ordering. Node completed before final cleanup.

### H3. Decorator Exception Doesn't Kill Workflow (P1)
A request decorator throws RuntimeException. Remaining decorators still execute. Workflow continues.

**Validates**: DecorateRequestResults catches and logs exceptions per decorator. No short-circuit.

### H4. WorkflowGraphResultDecorator — AgentCallRouting Resolves A2A Node (P0)
AgentCallRouting result triggers resolveRunningByRequestType to find AgentToAgentConversationNode via direct blackboard lookup (not findLastWorkflowRequest which filters it out).

**Validates**: The resolveRunning bug fix. AgentToAgentRequest retrieved by exact type, not by AgentRequest.class.

### H5. WorkflowGraphResultDecorator — DataLayer Results Use Own ContextId (P0)
AiFilterResult, AiPropagatorResult, etc. resolve their DataLayerOperationNode using the result's own contextId (from AgentResult extends AgentContext), not from blackboard lookup.

**Validates**: Data layer results are self-addressed. No dependency on findLastWorkflowRequest.

---

## I. BlackboardHistory / State Management

### I1. findLastWorkflowRequest Filters Non-Workflow Requests (P0)
Blackboard contains: OrchestratorRequest, AgentToAgentRequest, AiFilterRequest, DiscoveryAgentRequest. findLastWorkflowRequest returns DiscoveryAgentRequest (skipping A2A and filter).

**Validates**: The filter list in findLastWorkflowRequest correctly excludes: AgentToAgentRequest, CommitAgentRequest, MergeConflictRequest, AiFilterRequest, AiPropagatorRequest, AiTransformerRequest, AgentToControllerRequest, ControllerToAgentRequest, InterruptRequest, ContextManagerRequest.

### I2. getLastOfType Retrieves Filtered-Out Requests (P0)
AgentToAgentRequest IS on the blackboard even though findLastWorkflowRequest skips it. `history.getLastOfType(AgentToAgentRequest.class)` returns it.

**Validates**: Direct type lookup bypasses the workflow filter. This is the mechanism the resolveRunningByRequestType fix depends on.

### I3. WorkflowGraphState Tracks Node IDs Across Phases (P0)
After discovery completes, state has discoveryOrchestratorNodeId set. After planning starts, state has planningOrchestratorNodeId set. Previous IDs not cleared.

**Validates**: updateState accumulates. Each phase's nodeId accessible to later phases.

### I4. Blackboard History Grows Monotonically (P1)
History entry count never decreases between successive LLM calls.

**Validates**: No entries dropped. Append-only behavior.

### I5. resolveState with No History Throws (P2)
If BlackboardHistory is null (e.g., context not initialized), resolveState throws.

**Validates**: Fail-fast on missing state. No silent null propagation.

---

## J. Event System

### J1. Event Temporal Ordering (P0)
Events in the event stream have non-decreasing timestamps.

**Validates**: Events appended in real time order. No time-travel.

### J2. NodeAddedEvent Before Any Status Change (P0)
Every NodeStatusChangedEvent references a nodeId that has an earlier NodeAddedEvent.

**Validates**: Nodes exist before their status changes.

### J3. AgentCallStarted/Completed Pairing (P0)
Every AgentCallStartedEvent has exactly one matching AgentCallCompletedEvent.

**Validates**: No orphaned start events. No phantom completions.

### J4. GoalCompletedEvent Fires Exactly Once (P0)
Successful workflow produces exactly one GoalCompletedEvent. No duplicates.

**Validates**: Goal detection logic doesn't fire multiple times.

### J5. MergePhaseStarted/Completed Pairing (P1)
Every MergePhaseStartedEvent has a matching MergePhaseCompletedEvent.

**Validates**: Merge phase always finishes (success or failure).

### J6. InterruptStatusEvent Sequence (P1)
REQUESTED → ACKNOWLEDGED (optional) → RESOLVED. No backward transitions.

**Validates**: Interrupt lifecycle events in correct order.

---

## K. Stuck Handler / Degenerate Loop Prevention

### K1. Stuck Handler Fires and Recovers (P1)
Agent enters a state where no action can proceed. Stuck handler invokes context manager. Context manager returns a viable replan. Workflow continues.

**Validates**: handleStuck integration. ContextManagerRequest created. Result used to re-route.

### K2. Stuck Handler Max Invocations (P1)
Stuck handler fires 3 times (MAX_STUCK_HANDLER_INVOCATIONS). On 4th attempt, returns NO_RESOLUTION. DegenerateLoopException published.

**Validates**: Guard against infinite re-entry. NodeErrorEvent published with loop details.

### K3. DegenerateLoopException from WorktreeContextRequestDecorator (P2)
Sandbox context cannot be resolved (null worktree). Decorator throws DegenerateLoopException.

**Validates**: Fail-fast when worktree context missing. NodeErrorEvent published.

---

## L. Artifact Tree

### L1. Full Workflow Persists Artifact Tree (P0)
After workflow completes, ArtifactEntity tree exists with root artifact. Children correspond to workflow phases.

**Validates**: ArtifactEventListener captures events. ArtifactTreeBuilder constructs tree. ExecutionScopeService tracks scope.

### L2. Artifact Keys Match Graph NodeIds (P1)
Every artifact's artifactKey corresponds to a graph node's nodeId.

**Validates**: Artifact tree and graph are synchronized.

---

## M. External Interrupt Routing (Controller-Initiated)

### M1. External Interrupt at Orchestrator (P1)
External system injects interrupt via FilterProperties during orchestrator phase. Orchestrator stores interrupt and completes with feedback.

**Validates**: External interrupts are distinct from LLM-initiated interrupts. FilterProperties storage. Interrupt does not block (external resolution already provided).

### M2. External Interrupt During Discovery Agent (P1)
External interrupt injected while discovery agents are running. Interrupted agent handles it. Other agents continue.

**Validates**: External interrupt targeting specific agent. Parallel agents unaffected.

---

## N. Agent-to-Controller Communication (Topology)

### N1. callController — Happy Path (P0)
Discovery agent calls `callController(sessionId, justification)`. HUMAN_REVIEW interrupt published. Controller resolves with response text. Agent receives response and continues workflow to completion.

**Validates**: Full callController flow: validate → build AgentToControllerRequest → decorate → publish HUMAN_REVIEW interrupt → awaitInterruptBlocking → resolve → return response. AgentCallEvent emitted at INITIATED/RETURNED stages.

### N2. callController — Message Budget Exceeded (P0)
Agent calls `callController` more times than `communicationTopology.messageBudget`. Returns ERROR with budget exceeded message. No interrupt published.

**Validates**: ConcurrentHashMap per-session counter increments. Budget check fires before interrupt. Error message includes count and limit.

### N3. callController — Missing/Invalid Session (P1)
Agent calls `callController` with empty or malformed sessionId. Returns ERROR immediately.

**Validates**: Input validation guard clauses. No interrupt published. No partial state left.

### N4. CallChainEntry Target Enrichment (P0)
During a standard callAgent flow, CallChainEntry entries contain both `agentKey`/`agentType` (source) and `targetAgentKey`/`targetAgentType` (target).

**Validates**: SessionKeyResolutionService.buildCallChainFromGraph() populates target fields. Target fields non-null for all hops in chained calls.

### N5. AgentTopologyPromptContributor Injection (P1)
During a standard workflow request (not a communication request), the agent's prompt context includes topology information listing available communication targets.

**Validates**: AgentTopologyPromptContributorFactory.create() returns contributor for non-communication requests. Prompt text contains available agent list XML. Communication request types (AgentToAgentRequest, AgentToControllerRequest) are excluded.

### N6. JustificationPromptContributor Injection (P1)
When an AgentToControllerRequest is processed, the prompt context includes a role-specific justification template (discovery/planning/ticket/default).

**Validates**: JustificationPromptContributorFactory matches on AgentToControllerRequest/ControllerToAgentRequest. Template selected by agent type. Prompt text contains structured justification guidance.

### N7. AgentConversationController — List and Respond (P1)
Controller lists pending conversations via `POST /api/agent-conversations/list`. Responds to a pending conversation via `POST /api/agent-conversations/respond` with interruptId and message.

**Validates**: REST endpoint returns conversation summaries. Respond resolves the pending HUMAN_REVIEW interrupt. Agent unblocks after response.

### N8. ActivityCheckController — Pending Counts (P1)
Polling `POST /api/ui/activity-check` with a nodeId returns counts of pending permissions, interrupts, and conversations. HUMAN_REVIEW interrupts classified as conversations.

**Validates**: Lightweight endpoint. Scope matching via ArtifactKey.isDescendantOf. hasActivity=true when any count > 0.

---

## Coverage Matrix

| Scenario | Graph | Events | Blackboard | Worktree | Interrupt | A2A | DataLayer |
|----------|-------|--------|------------|----------|-----------|-----|-----------|
| A1       | X     | X      | X          |          |           |     |           |
| A2       | X     | X      | X          |          |           |     |           |
| B1       | X     |        | X          |          |           |     |           |
| B2       | X     |        | X          |          |           |     |           |
| C1       | X     | X      |            |          | X         |     |           |
| C2       | X     | X      |            |          | X         |     |           |
| D1       | X     | X      | X          |          |           | X   |           |
| D2       | X     | X      | X          |          |           | X   |           |
| D3       |       |        |            |          |           | X   |           |
| D4       |       |        |            |          |           | X   |           |
| E1       | X     | X      |            | X        |           |     |           |
| E2       | X     | X      |            | X        |           |     |           |
| E3       | X     | X      |            | X        |           |     |           |
| F1       | X     |        |            |          |           |     | X         |
| F2       | X     |        |            |          |           |     | X         |
| F3       | X     |        |            |          |           |     | X         |
| G1       | X     |        |            |          |           |     |           |
| H4       |       |        | X          |          |           | X   |           |
| I1       |       |        | X          |          |           |     |           |
| I2       |       |        | X          |          |           |     |           |
| J1-J6    |       | X      |            |          |           |     |           |
| N1       | X     | X      |            |          | X         |     |           |
| N2       |       |        |            |          |           |     |           |
| N4       | X     |        |            |          |           | X   |           |
