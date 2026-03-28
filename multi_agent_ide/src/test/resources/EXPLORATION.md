# Exploration Entry Points

Entry points for discovering things we didn't know we didn't know in the test trace data.
Each entry point describes where to look, what question to ask, and what a finding might lead to.

Generated: 2026-03-27

## How to Use

1. Read each entry point and the question it asks
2. Investigate the trace data (files in `test_work/queued/`)
3. If a finding reveals unexpected behavior, update SURFACE.md (new scenario) or INVARIANTS.md (new invariant)
4. Mark the entry point as INVESTIGATED with your findings

---

## E1. NodeErrorEvent in Happy-Path-Adjacent Tests

**Where**: `*.events.md` — grep for `NodeErrorEvent`
**Question**: Do NodeErrorEvents appear in tests that should be clean happy paths (no loopbacks, no interrupts)?
**Why it matters**: NodeErrorEvents with message "Skipping trunk->child merge: child and trunk worktree are the same" appear in ALL tests (12+ per test), including `fullWorkflow_discoveryToPlanningSingleAgentsToCompletion`. If these are expected (mocked worktree behavior), they're noise. If they mask real errors, we need filtering.
**What to look for**: NodeErrorEvent messages that are NOT "Skipping trunk->child merge" — those would be real errors.

**Status**: INVESTIGATED
**Finding**: All NodeErrorEvents across happy path tests are "Skipping trunk->child merge" from the mocked worktree service. These are benign — the mock stubs return a `MergeDescriptor` with the same worktree path for child and trunk, triggering the skip. NOT masking real errors. However, the error count inflates the "errors" column in any trace summary. Consider adding a `NodeErrorEvent.severity` field or filtering known-benign messages in TestTraceWriter.

---

## E2. Running/Completed Transition Asymmetry

**Where**: `*.events.md` — count `newStatus.*RUNNING` vs `newStatus.*COMPLETED`
**Question**: Why do some tests have more RUNNING transitions than COMPLETED transitions?
**Why it matters**: In a well-behaved workflow, every node that enters RUNNING should eventually enter COMPLETED (or FAILED/CANCELED). An asymmetry could indicate leaked state.

**Status**: INVESTIGATED
**Finding**: The asymmetry comes from rerunnable nodes (DiscoveryOrchestratorNode, collectors) in loopback scenarios. They transition PENDING->RUNNING multiple times but COMPLETED only once. `planningCollector_loopsBackToDiscovery` shows 46 RUNNING vs 32 COMPLETED — the extra 14 are from second-pass phase transitions on reused nodes. Additionally, the A-INV2 trace timing gap means the final snapshot captures nodes still in RUNNING/PENDING before result decorators complete them. This is expected behavior for the current trace capture design. **Potential invariant**: For non-rerunnable node types, RUNNING count should equal COMPLETED count.

---

## E3. Blackboard History Size Across Test Scenarios

**Where**: `*.blackboard.md` — grep for `History entries:`
**Question**: How large does the blackboard history get in different scenarios? Is there a growth pattern we should bound?
**Why it matters**: Unbounded blackboard growth could indicate a memory leak in long-running workflows. The `messageBudgetExceeded` test makes 4 callController calls — how does this affect history size compared to a simple happy path?

**Status**: INVESTIGATED
**Finding**: Happy path (`fullWorkflow`) ends with ~12 history entries. `callController_messageBudgetExceeded` ends with ~15 entries — the extra 3 are from the callController interrupt resolution cycles. `planningCollector_loopsBackToDiscovery` ends with ~18 entries due to replayed phases. Blackboard growth is linear with workflow complexity. No unbounded growth observed. G10 invariant (monotonic growth) holds across all scenarios.

---

## E4. AgentCallEvent Target Field Consistency

**Where**: `*.events.md` — grep for `AgentCallEvent`
**Question**: For A2A calls, do the AgentCallEvent `targetSessionId` and `targetAgentType` fields match the A2A node's `targetAgentKey` and `targetAgentType` in the graph?
**Why it matters**: If these diverge, the event stream tells a different story than the graph, making debugging unreliable.

**Status**: INVESTIGATED
**Finding**: In `callAgent_createsAndCompletesAgentToAgentNode`, the AgentCallEvent has `targetSessionId` matching the A2A node's targetAgentKey, and `targetAgentType: DISCOVERY_COLLECTOR` matching the graph. Consistent. For `callController_happyPath`, targetSessionId is "controller" (literal string) since there's no real controller session — this is a design choice, not a bug, but worth noting as a divergence from the A2A pattern where targetSessionId is a real ArtifactKey.

---

## E5. Interrupt Resolution Timing Window

**Where**: `callController_happyPath*.events.md` — examine timestamps between INITIATED AgentCallEvent and RETURNED AgentCallEvent
**Question**: How long does the agent actually block waiting for controller resolution? Is the timing realistic for the test, or does it suggest a polling/sleep overhead?
**Why it matters**: If `awaitInterruptBlocking` has significant polling overhead, production callController calls will have unnecessary latency.

**Status**: INVESTIGATED
**Finding**: INITIATED (Event 36, 01:50:24.295) vs RETURNED (Event 38, 01:50:25.396) — approximately 1.1 seconds. This includes the test's `Thread.sleep(1000)` assertion delay plus the `await().atMost()` polling overhead. The actual `awaitInterruptBlocking` wake-up is sub-millisecond once `resolveInterrupt` is called. No polling overhead in the production interrupt mechanism — it uses a blocking latch pattern.

---

## E6. CallChain Serialization Format in Events vs Graph

**Where**: `callAgent_chainedCalls_*.events.md` and `*.graph.md`
**Question**: The callChain in AgentCallEvent is `List<String>` (e.g., `["ak:XXX->ak:YYY"]`), while in the graph it's `List<CallChainEntry>` (full objects with timestamps). Do these tell the same story?
**Why it matters**: If an investigator sees the event stream and graph separately, they should be able to cross-reference. Format divergence makes this harder.

**Status**: INVESTIGATED
**Finding**: CallChain in AgentCallEvent JSON is always `[]` (empty) — because `emitCallEvent` in `callAgent` is called with `List.of()` for the callChain parameter. The call chain is tracked on the A2A *request* and *node*, not the event. The graph trace shows callChain in `### AgentToAgent:` sections as a serialized `List<CallChainEntry>` with full objects (agentKey, agentType, targetAgentKey, targetAgentType, timestamp). **Gap identified**: The AgentCallEvent should include the actual call chain for observability. Currently an event consumer cannot reconstruct call chain depth from events alone — they must cross-reference the graph. Consider populating the callChain parameter in `emitCallEvent` calls.

---

## E7. What Happens Between callController Budget Calls?

**Where**: `callController_messageBudgetExceeded*.events.md` and `*.blackboard.md`
**Question**: Between the 3 budget-allowed callController calls, what events fire? Does the blackboard accumulate AgentToControllerRequest entries? Can we see the counter incrementing?
**Why it matters**: Understanding the intermediate state helps debug budget-related issues in production.

**Status**: INVESTIGATED
**Finding**: Clean 3 pairs of INITIATED/RETURNED (events 36/38, 41/43, 46/48). No 4th INITIATED event — budget check fires before interrupt publish. Between each pair, event 37/42/47 are the events during the interrupt resolution window. The blackboard does NOT accumulate AgentToControllerRequest entries because `callController` builds the request for decorator enrichment but the interrupt resolution bypasses the normal blackboard append path. The per-session counter in `conversationMessageCounts` ConcurrentHashMap is the only stateful tracking. **Gap identified**: AgentToControllerRequest is not persisted to blackboard history, so `findLastWorkflowRequest` and `getLastOfType` cannot see it. This is by design (interrupt-based, not LLM-based flow) but means there's no blackboard audit trail for controller conversations.

---

## E8. Node Type Distribution Across Scenarios

**Where**: `*.graph.md` — final snapshot, count node types
**Question**: Is the node type distribution consistent across similar scenarios? Do A2A tests add exactly the expected extra nodes?
**Why it matters**: If A2A tests create unexpected node types, it may indicate side effects from the callAgent decorator chain.

**Status**: INVESTIGATED
**Finding**: Happy path = 14 nodes. A2A single call = 16 nodes (+1 AgentToAgentConversationNode, +1 DataLayerOperationNode from auto-commit). A2A chained = 18 nodes (+2 A2A nodes, +2 DataLayer nodes). callController happy path = 14 nodes (no extra graph nodes — controller calls don't create graph nodes, they use the interrupt system). This is a clean, predictable pattern.

---

## E9. Prompt Template Names Across All Tests

**Where**: `*.graph.md` — extract all template names from `## After Call N:` headers
**Question**: Are there any template names appearing in traces that are NOT in the G7 known set? Were `communication/controller_call` or `communication/controller_response` used in any test?
**Why it matters**: G7 invariant needs to be exhaustive. New templates slipping in without invariant updates is a maintenance risk.

**Status**: INVESTIGATED
**Finding**: 16 unique templates across all traces: `communication/agent_call`, `workflow/discovery_agent`, `workflow/discovery_collector`, `workflow/discovery_dispatch`, `workflow/discovery_orchestrator`, `workflow/orchestrator`, `workflow/orchestrator_collector`, `workflow/planning_agent`, `workflow/planning_collector`, `workflow/planning_dispatch`, `workflow/planning_orchestrator`, `workflow/review_resolution`, `workflow/ticket_agent`, `workflow/ticket_collector`, `workflow/ticket_dispatch`, `workflow/ticket_orchestrator`. Notably absent: `communication/controller_call` and `communication/controller_response` — because `callController` uses the interrupt mechanism, not the LLM template pipeline. Also absent: `workflow/context_manager`, `workflow/interrupt_handler` — these may only appear in stuck-handler or explicit interrupt-handler tests which aren't in the current integration suite. G7 invariant is satisfied for all templates found.

---

## E10. Event Count Variation Across Interrupt Tests

**Where**: `*.events.md` — compare event counts across interrupt scenarios
**Question**: Do all interrupt tests (C1-C8, N1) produce roughly the same number of events? Outliers may indicate extra or missing lifecycle events.
**Why it matters**: If one interrupt scenario produces significantly more events, it may indicate a retry loop, duplicate event emission, or a missed event in other scenarios.

**Status**: INVESTIGATED
**Finding**: Interrupt tests range from 223-295 events. Baseline (happy path) is 220. The extra events come from: InterruptHandlerNode creation/transitions (+2 node events), interrupt-related status changes (+2-4), and review resolution calls (+10-20 events for the extra LLM call). `discoveryCollector_agentInitiatedInterrupt` is highest at 295 because AGENT_REVIEW triggers an additional review agent LLM call. This distribution is expected.

---

## E11. ToolContextDecorator Contributions Visible in ACP Session

**Where**: Application runtime log (`multi-agent-ide.log`) — grep for `applyToolContext` or `ToolAbstraction` or `AgentTopologyTools`
**Question**: After the ToolContextDecorator→LlmCallDecorator split, are all expected tools actually reaching the ACP session? Specifically: do call_controller, call_agent, and list_agents appear in the tool list sent to the LLM?
**Why it matters**: The previous bug (tools added via LlmCallDecorator but applied before decorators ran) was silent — no error, agents just didn't see the tools. If the ToolContextDecorator pipeline has an ordering or registration issue, the same silent failure occurs.
**What to look for**: Set a breakpoint at `DefaultLlmRunner.applyToolContext()` (line ~79). Inspect the `toolContext.tools()` list. It should contain: AskUserQuestion (always), AgentTopologyTools (from AddTopologyTools), hindsight tools (from AddMemoryToolCallDecorator if available), skill tools (from AddSkillToolContextDecorator if applicable). If AgentTopologyTools is missing, the ToolContextDecorator was not wired or ordered correctly.

**Status**: NOT YET INVESTIGATED

---

## E12. Live Workflow: call_controller Invocation by Agents

**Where**: `POST /api/agent-conversations/list` with the workflow nodeId — or `poll.py` CONVERSATIONS section
**Question**: During a live deployed workflow with justification prompts in templates, do agents actually invoke `call_controller`? If not, why — is the tool missing, is the prompt not compelling enough, or does the LLM ignore it?
**Why it matters**: The justification dialogue is the core verification mechanism for the controller skill. If agents don't call it, the entire review checklist workflow is dead. The prompt says "MUST use call_controller" but LLMs may not comply.
**What to look for**:
1. Poll output → CONVERSATIONS section shows entries with `agentType` and `pending=true`
2. If 0 conversations: check poll → PENDING PERMISSIONS for tool call evidence
3. If still nothing: check runtime log for "AgentTopologyTools" or "call_controller" to see if the tool was even offered
4. Breakpoint: `AgentTopologyTools.callController()` — if never hit, the tool isn't in the ACP session

**Status**: NOT YET INVESTIGATED

---

## E13. DecoratorContext.agentRequest() Cast Safety

**Where**: `AddIntellij.resolveMainWorktreePath()` and `RemoveIntellij.resolveMainWorktreePath()` — both cast `DecoratorContext.agentRequest()` from `Artifact.AgentModel` to `AgentModels.AgentRequest`
**Question**: Are there cases where `agentRequest()` is NOT an `AgentModels.AgentRequest` (e.g., InterruptRequest, AgentToAgentRequest, ControllerToAgentRequest)? If so, the `instanceof` check silently returns empty — is that the right behavior for those request types?
**Why it matters**: If an interrupt handler or A2A call triggers AddIntellij, it won't find a worktree path (even if one exists on the underlying request). This could mean IntelliJ MCP tools are missing from those call contexts.
**What to look for**: Set breakpoint at `AddIntellij.decorate()`. Check what type `decoratorContext.agentRequest()` actually is during interrupt-handler and A2A call flows. If it's `InterruptRequest`, the worktree path comes from `InterruptRequest.worktreeContext()` which exists — but the cast will miss it.

**Status**: NOT YET INVESTIGATED

---

## E14. Propagation Item Payloads During Review Justification

**Where**: `POST /api/propagations/items/by-node` or `poll.py` PROPAGATION ITEMS section — `propagation_detail.py` for full payload
**Question**: When agents do call `call_controller`, what does the propagation item look like? Does the `summaryText` capture the justification, or is it lost in the interrupt resolution flow?
**Why it matters**: The controller skill user needs to see the agent's justification rationale in the propagation data to make a review decision. If propagation items only capture the structured output (DiscoveryAgentRouting, etc.) and not the justification dialogue, the controller is flying blind.
**What to look for**: After a live run where agents call `call_controller`, use `propagation_detail.py` to examine the full payload. Check if `llmOutput` or `propagationRequest` contains the justification text. If not, the justification lives only in the interrupt resolution text, which is not propagated.

**Status**: NOT YET INVESTIGATED

---

## Future Exploration Candidates

- **F-E1**: Data layer operation timing relative to agent completion — do DataLayerOperationNodes always complete before their parent agent completes?
- **F-E2**: Blackboard entry types at each phase boundary — does findLastWorkflowRequest consistently return the right type?
- **F-E3**: Concurrent event emission ordering under parallel agents — are events interleaved or batched?
- **F-E4**: Graph snapshot consistency — does the same graph state ever appear in two consecutive snapshots (indicating a no-op LLM call)?
- **F-E5**: ToolContextDecorator sort stability — if two decorators share the same `order()` value, does their relative execution order vary between runs? Could cause non-deterministic tool registration.
- **F-E6**: Justification prompt template resolution — do all 16 workflow templates actually include `_review_justification.jinja` and their role-specific justification partial? Grep the resolved prompt text in trace data.
