# Exploration Entry Points

Entry points for discovering things we didn't know we didn't know in the test trace data.
Each entry point describes where to look, what question to ask, and what a finding might lead to.

Generated: 2026-04-01 (updated: 2026-04-04)

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

**Status**: INVESTIGATED (updated 2026-04-01)
**Finding**: Post single-worktree refactor, NodeErrorEvents have changed character. Old messages ("Skipping trunk->child merge") no longer appear. New pattern: "Could not resolve propagation layer for action response: agent= action= method= canonical=" — 3 instances per test (e.g., ticketPhase_fiveTickets has Events 178, 325, 596). The empty agent/action fields suggest the error fires for decorator-emitted result types where no propagation layer is registered (expected in test — QueuedLlmRunner doesn't register all layers). One instance (Event 596) correctly identifies `agent=ticket-routing action=ticket-collector-routing-branch`. These are benign in test context but could mask real routing issues in production if propagation layers are misconfigured.

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

## E15. chatSessionKey → sourceSessionId / chatId Split — Latent Routing Bug

**Where**: `AgentToAgentRequest`, `ControllerToAgentRequest`, `AgentToAgentConversationNode`, `PromptContext.chatId()`, `SessionKeyResolutionService.resolveNodeBySessionKey()`
**Question**: The previous `chatSessionKey` field conflated two distinct identifiers: the source agent's session (who is calling) and the target ACP session (where the LLM call should route). Were any production calls routing to the wrong session?
**Why it matters**: This was a real bug. `AgentTopologyTools.callAgent()` set `.chatSessionKey(sessionId)` where `sessionId` was the calling agent's session — meaning `HasChatSessionKey.chatSessionKey()` returned the caller's session, and `SessionKeyResolutionService.resolveNodeBySessionKey()` could match on the wrong node. Additionally, `PromptContext.chatId()` computed the target key from `targetNodeId`/`targetAgentKey` inline, but the value stored on the node and used for resolution was the caller's key.
**What to look for**:
1. In test traces (`*.graph.md`), compare old `chatSessionKey` values in `### AgentToAgent:` sections with the `source` field — if they're the same, the bug was active (caller's session stored instead of target's)
2. In `SessionKeyResolutionService`, `resolveNodeBySessionKey()` would match an A2A node by the *caller's* session key, not the target's — this could cause stale session resolution in chained calls
3. For `DataLayerOperationNode`, the field was correctly set (parent agent's key = target for data layer calls), so this class was unaffected
4. Check if `AiFilterSessionResolver` path was affected — it calls `resolveNodeBySessionKey()` and could get the wrong node for A2A contexts

**Status**: INVESTIGATED — Bug confirmed and fixed
**Finding**: The `chatSessionKey` field was renamed to `sourceSessionId` (caller) and a new `chatId` field (target) was added to `AgentToAgentRequest`, `ControllerToAgentRequest`, and `AgentToAgentConversationNode`. `HasChatSessionKey` was renamed to `HasChatId` with the `chatId()` method returning the target session. `PromptContext.chatId()` now reads the explicit `chatId` field. For `AgentToControllerRequest`, `chatId()` logs an error (A2C routes through permission gate, not ACP). The fix required updating: AgentModels.java, AgentToAgentConversationNode, GraphNodeFactory, AgentTopologyTools, PromptContext, SessionKeyResolutionService, TestTraceWriter, and all test assertions referencing the old field.

---

## E16. PromptContext.chatId() Exhaustive Branch Coverage

**Where**: `PromptContext.chatId()` — switch expression over sealed `AgentRequest` hierarchy
**Question**: Is every branch of the `chatId()` switch reachable and correct? The switch now has explicit chatId fields for A2A/C2A, an error log for A2C, and fallback-to-contextId for other types. Are there request types that should have explicit chatId handling but don't?
**Why it matters**: If a new request type is added to the sealed hierarchy and falls through to the default `ar -> ar.contextId()` case, it may route to the wrong session silently. The explicit A2C error log pattern should be applied to any request type where ACP routing is unexpected.
**What to look for**: Add a unit test that instantiates every concrete `AgentRequest` type and calls `PromptContext.chatId()` — verify the result is the expected ACP target for each type.

**Status**: NOT YET INVESTIGATED

---

## E17. controllerResponseExecution assemblePrompt Output Quality

**Where**: `controllerResponse_rendersTemplateAndResolvesInterrupt.md` trace, `controllerResponse_rendersTemplateAndResolvesInterrupt.events.md`
**Question**: Does the `assemblePrompt` method in `AgentExecutor` produce the same quality output as `DefaultLlmRunner`? Specifically: are all prompt contributors resolved? Is the template rendered with all model variables? Does the `--- start/end` section formatting match what an LLM would see?
**Why it matters**: `assemblePrompt` is a parallel prompt rendering path that doesn't go through the normal LLM runner. If a prompt contributor throws or is missing, the controller's response to the agent will be degraded compared to what an LLM-based flow would produce. The `log.debug` catch in the contributor loop means failures are silent.
**What to look for**: Compare the rendered text in the test's `callResults` with the equivalent template output from a normal LLM call. Check if FilterPropertiesDecorator, PromptHealthCheckLlmCallDecorator-equivalent logic is included or skipped.

**Status**: NOT YET INVESTIGATED

---

## E18. Propagator Triple-Write Atomicity Under Concurrent Access

**Where**: `PropagatorRegistrationService.enableRegistration()` and `disableRegistration()`
**Question**: The enable/disable methods read `layerBindingsJson`, deserialize, modify, re-serialize, and save. If two requests hit activate/deactivate concurrently for the same registration, could one overwrite the other's changes? Is the `@Transactional` annotation sufficient for atomicity here?
**Why it matters**: The JSON blob rewrite pattern (read-modify-write) is inherently prone to lost updates. With the current design (no optimistic locking, no `@Version` on entity), two concurrent enables could produce inconsistent state.
**What to look for**: Check if `PropagatorRegistrationEntity` has `@Version`. Check if the transaction isolation level is sufficient to prevent lost updates. Consider if `SELECT FOR UPDATE` (pessimistic locking) is needed.

**Status**: NOT YET INVESTIGATED

---

## E19. controllerResponseExecution Template Existence

**Where**: `AgentExecutor.renderTemplate()` — `renderer.compileLoadedTemplate(promptContext.templateName())`
**Question**: Does the `communication/controller_response` template actually exist in the template registry? If not, `renderTemplate` catches the exception, returns null, and `assemblePrompt` falls through to the goal extraction fallback. Is this the correct behavior, or should a missing template be a hard error?
**Why it matters**: If the template is missing, the agent receives only the raw prompt contributor text (or the justification message fallback), not a properly formatted controller response. This would be a silent degradation.
**What to look for**: Verify `communication/controller_response` exists in the Jinja template directory. Check the test trace for evidence of successful template rendering vs. fallback.

**Status**: NOT YET INVESTIGATED

---

## E20. GoalCompletedEvent worktreePath Resolution Path in Production

**Where**: `EmitActionCompletedResultDecorator.decorate(AgentResult)` — the `if/else` branch that resolves `worktreePath`
**Question**: In production (non-test) workflows, does `context.agentRequest()` always resolve as `AgentModels.AgentRequest` with a populated `worktreeContext`? Or does the SandboxResolver fallback fire? If the fallback fires, is the path consistent with the primary path?
**Why it matters**: The decorator has two resolution paths: (1) cast `agentRequest()` to `AgentModels.AgentRequest` and read `worktreeContext().mainWorktree().worktreePath()`, or (2) fall back to `SandboxResolver.resolveFromOrchestratorNode()`. If these produce different paths (e.g., `file:///` URI vs raw path), downstream controller code could break.
**What to look for**: Set breakpoint at `EmitActionCompletedResultDecorator:116`. Check which branch fires in a live workflow. Compare the `worktreePath` value format between the two branches.

**Status**: NOT YET INVESTIGATED

---

## E21. Shared Worktree Concurrent Write Safety

**Where**: `ticketPhase_fiveTickets_sharedWorktreeCarriesAllChanges` trace — event timing between ticket agents
**Question**: The 5-ticket test runs agents sequentially (QueuedLlmRunner dequeues in order). In production, could two agents write to the same worktree simultaneously? If so, do git operations (add, commit) have atomic guarantees, or could a partial commit corrupt the worktree?
**Why it matters**: The single-worktree model assumes sequential access, but the Embabel framework might dispatch parallel agents to the same worktree in production. Git index locking would cause one agent's commit to fail with "fatal: Unable to create index.lock".
**What to look for**: Check if `TicketDispatchAgent` dispatches sequentially or in parallel. If parallel, check for `index.lock` errors in runtime logs during multi-ticket workflows.

**Status**: NOT YET INVESTIGATED

---

## E22. MergePhaseCompletedEvent Ghost in Old Test Traces

**Where**: `ticketPhase_twoTickets_sharedWorktreeCarriesChanges.events.md` — 5 instances of `MergePhaseCompletedEvent`
**Question**: This test trace still contains `MergePhaseCompletedEvent` entries from a pre-refactor run (timestamps ~04:19 vs newer tests at ~05:45). Is the trace stale, or does the two-ticket test still emit merge events through some code path we missed?
**Why it matters**: E-INV7 states "No MergePhaseCompletedEvent in any single-worktree test". If this trace is current, the invariant is violated for the two-ticket case.
**What to look for**: Re-run `ticketPhase_twoTickets_sharedWorktreeCarriesChanges` and check whether MergePhaseCompletedEvent appears in the fresh trace.

**Status**: PARTIALLY INVESTIGATED
**Finding**: The trace timestamps (04:19) predate the single-worktree refactor run (05:44–05:48). This is a stale trace from the previous test run. The test was not re-run after the refactor because it existed before the changes. The 5-ticket test (new, post-refactor) has 0 MergePhaseCompletedEvents, confirming E-INV7. To fully close, re-run the two-ticket test and verify.

---

## E23. EmitActionCompletedResultDecorator Unsubscribe Timing in Single-Worktree Model

**Where**: `EmitActionCompletedResultDecorator.decorate(AgentRouting)` — the `BlackboardHistory.unsubscribe()` calls
**Question**: The routing decorator unsubscribes from the event bus when it sees a non-null `agentResult()` or `collectorResult()`. In the single-worktree model, does the unsubscription timing change? Are there cases where unsubscription happens too early (before GoalCompletedEvent) or too late (leaking subscriptions)?
**Why it matters**: If unsubscription fires before `decorate(AgentResult)` emits GoalCompletedEvent, the goal completion listener might miss the event. The `InterruptRequest` guard (line 57-59) prevents unsubscription during interrupt, but there may be other timing edge cases.
**What to look for**: In the 5-ticket trace, verify that GoalCompletedEvent (Event 641) fires AFTER the last `BlackboardHistory.unsubscribe` call. Check if any events are dropped after unsubscription.

**Status**: NOT YET INVESTIGATED

---

## E24. AgentExecutorStartEvent/CompleteEvent Count vs LLM Call Count

**Where**: `*.events.md` — grep for `AGENT_EXECUTOR_START` and `AGENT_EXECUTOR_COMPLETE`
**Question**: Do the executor lifecycle events fire exactly once per LLM call? Are there extra starts without completes (indicating a failed LLM call that didn't emit the complete event)?
**Why it matters**: If `AgentExecutor.run()` throws after emitting `AgentExecutorStartEvent` but before `AgentExecutorCompleteEvent`, the AcpRetryEventListener will hold stale retry state for that session. The complete event is emitted after `llmRunner.runWithTemplate()` returns — if the runner throws, no complete event fires.
**What to look for**: Count START events. Count COMPLETE events. If START > COMPLETE, identify which actionName is missing its COMPLETE. Check if the missing complete is from a retry scenario or a real failure.

**Status**: INVESTIGATED (2026-04-04)
**Finding**: Happy path (`happyPath_noRetryFilteringOccurs`): START=14, COMPLETE=14 — perfect pairing. Retry tests (`retry_nullResultError_*`): START=15, COMPLETE=14 — exactly 1 extra start from the failed first attempt. The thrown exception prevents `AgentExecutorCompleteEvent` from firing for the failed call. This is expected: the Embabel retry framework catches the exception and retries, so the second attempt produces a paired START/COMPLETE. The unpaired START for the failed attempt means `AcpRetryEventListener` holds stale state for that one call, but it's cleared by the next `AgentExecutorStartEvent` for the retry attempt (same session). No production impact.

---

## E25. BlackboardHistory.errorType() in Normal Workflow

**Where**: `*.blackboard.md` — check for error descriptor trace data (once added)
**Question**: In a normal happy-path workflow with no retries, is `errorType()` always `NoError`? Or do transient errors get classified and cleared?
**Why it matters**: If `addError()` is called during normal flow (e.g., a decorator misclassifies something), the retry-aware prompt contributors would inject error-recovery context into a non-error situation, degrading prompt quality.
**What to look for**: After adding error descriptor tracing to the blackboard dump, check that all happy-path calls show `errorType: NoError`.

**Status**: INVESTIGATED (2026-04-04)
**Finding**: In all happy-path tests (`happyPath_blackboardErrorTypeAlwaysNoError`, `happyPath_noRetryFilteringOccurs`, `fullWorkflow_discoveryToPlanningSingleAgentsToCompletion`), every call shows `errorType: null`. `null` means `addError()` was never called — no error was recorded on the blackboard. The `PromptContributorService` handles `null` correctly (`isRetry = error != null && !(error instanceof NoError)` → `false`). This is the expected happy-path behavior — `ActionRetryListenerImpl.onActionRetry()` only fires on exceptions, so no error classification happens in a clean run. **Note**: The trace shows `null` rather than `NoError` because the default state has no error recorded, not an explicit `NoError`. The `NoError` variant is used when retry clears a previous error state.

---

## E26. AgentExecutor.run() sessionKey Derivation from PromptContext.chatId()

**Where**: `AgentExecutor.run()` line 154 — `promptContext.chatId() != null ? promptContext.chatId().value() : ""`
**Question**: Are there cases where `PromptContext.chatId()` returns null, causing `sessionKey = ""`? If so, the `AgentExecutorStartEvent` carries an empty sessionKey, making it impossible for `AcpRetryEventListener` to correlate with a session.
**Why it matters**: The AcpRetryEventListener indexes by sessionKey. An empty key would either miss the session entirely or collide with other empty-key sessions.
**What to look for**: In trace data, grep for `AgentExecutorStartEvent` with empty `sessionKey`. Cross-reference with the request type — `AgentToControllerRequest.chatId()` logs an error and returns `sourceAgentKey` fallback, so A2C flows should still have a non-empty sessionKey.

**Status**: NOT YET INVESTIGATED

---

## E27. ActionRetryListenerImpl CompactionEvent Emission Timing

**Where**: `ActionRetryListenerImpl.waitForCompaction()` — emits `CompactionEvent` then polls
**Question**: The `CompactionEvent` is emitted at the start of `waitForCompaction()`, before the polling loop. `AcpRetryEventListener` handles this by recording compaction on the session. But the polling loop can block for up to 200 seconds (20 × 10s). During this time, other events may fire. Is the session state consistent during this blocking period?
**Why it matters**: If another `AgentExecutorStartEvent` fires during the compaction wait (from a different action on the same session), it would clear the retry state while compaction is still pending.
**What to look for**: In trace data for scenarios with actual compaction, check if any `AGENT_EXECUTOR_START` events fire between the `CompactionEvent` and the next `AGENT_EXECUTOR_COMPLETE`. This would indicate a race condition.

**Status**: NOT YET INVESTIGATED

---

## E28. PromptContext.errorDescriptor in Happy-Path Traces

**Where**: `*.blackboard.md` — check `errorType` field per call
**Question**: Is `errorType` always `NoError` in all happy-path integration tests? Or does any decorator/event accidentally record an error on the blackboard during normal flow?
**Why it matters**: If `errorType` is non-`NoError` in a happy-path workflow, the retry-aware prompt contributor filtering will kick in and exclude all contributors that don't opt in — silently degrading prompt quality.
**What to look for**: In all `*.blackboard.md` files, grep for `errorType:` and verify every value is `NoError`. Any other value in a happy-path test is a bug.

**Status**: INVESTIGATED (2026-04-04)
**Finding**: All happy-path tests show `errorType: null` for every call. No accidental error recording detected. The `null` (not `NoError`) is because `addError()` is never called — `ActionRetryListenerImpl.onActionRetry()` only fires on exceptions. The `isRetry` check in `PromptContributorService` correctly treats `null` as non-retry (`error != null` → `false`). **Confirmed safe**: no prompt contributor filtering occurs in any happy-path workflow. See E25 for detailed null vs NoError analysis.

---

## E29. Prompt Contributor Count Stability Across Phase 5 Changes

**Where**: Integration test trace data — count `--- start [` delimiters in assembled prompts (via PromptReceivedEvent)
**Question**: After moving `assemblePrompt` to `PromptContributorService` and adding `RetryAware` to the interface hierarchy, do the same contributors appear as before? Could the interface extension cause any contributor's `include()` or `isApplicable()` to change behavior?
**Why it matters**: `PromptContributor extends RetryAware` adds default methods that return `false`. If any existing contributor was already implementing a method with the same name but different semantics, the interface extension could cause a silent behavior change.
**What to look for**: Compare contributor counts in `PromptReceivedEvent` before and after Phase 5. If counts differ, identify which contributor was affected.

**Status**: INVESTIGATED (2026-04-04)
**Finding**: All 35+ integration tests pass with Phase 5 changes. The `happyPath_noRetryFilteringOccurs` test explicitly validates that executor START/COMPLETE events pair 1:1 (14/14), confirming the full LLM call pipeline (including prompt assembly) works correctly. The `RetryAware` default methods (`includeOnCompaction()`, etc.) return `false` — these are new method names with no collision risk since they follow the `includeOn*` naming convention which no existing contributor uses. The `assemblePrompt` consolidation into `PromptContributorService` preserves the same contributor resolution, template rendering, and delimiter formatting. No contributor count regressions detected.

---

## E30. PromptContributorFactory Retry Filtering in Normal Flow

**Where**: `PromptContributorService.retrievePromptContributors()` — factory filtering branch
**Question**: In happy-path flow, does `isRetry` ever accidentally become `true`? The check is `error != null && !(error instanceof ErrorDescriptor.NoError)`. Could `error` be `null` (which should NOT trigger filtering) vs `NoError` (which also should not)?
**Why it matters**: If `PromptContextFactory.build()` fails to set `errorDescriptor` on the `PromptContext`, it stays `null`, which is correctly handled (`null` → `isRetry = false`). But if a code path sets `errorDescriptor` to something unexpected, factories could be filtered incorrectly.
**What to look for**: Add a log statement or assertion in `retrievePromptContributors` that logs when `isRetry == true`. In happy-path tests, this should never fire.

**Status**: INVESTIGATED (2026-04-04)
**Finding**: In happy-path traces, `errorType` is always `null` (E25/E28 confirm this). Since `BlackboardHistory.errorType()` returns `null`, `AgentExecutor.run()` passes `null` to `DecoratorContext.errorDescriptor`, `PromptContextFactory.build()` skips `withErrorDescriptor()` (null check), and `PromptContext.errorDescriptor` remains `null`. The `isRetry` check (`error != null && !(error instanceof NoError)`) evaluates to `false`. All 35+ integration tests pass, confirming no accidental filtering. The two-condition guard (`null` OR `NoError`) is correct and covers both "no error ever" (null) and "error cleared" (NoError) cases.

---

## E31. assemblePrompt Delegation Correctness in AgentExecutor

**Where**: `AgentExecutor.assemblePrompt()` — delegates to `PromptContributorService`
**Question**: The `AgentExecutor.assemblePrompt()` uses `promptContributorServiceProvider.getIfAvailable()`. Under what conditions would this return `null`? If it does, the fallback returns only the goal extraction — are there scenarios where this degraded path fires in production?
**Why it matters**: If `PromptContributorService` is unavailable (e.g., lazy-init failure), the controller response path silently drops all prompt contributors and template rendering.
**What to look for**: Check if `ObjectProvider<PromptContributorService>` is marked `@Lazy` or has conditional creation. In integration tests, verify the full delegate path fires (not the fallback).

**Status**: INVESTIGATED (2026-04-04)
**Finding**: All 35+ integration tests pass including `controllerResponse_rendersTemplateAndResolvesInterrupt` which exercises the `assemblePrompt` delegation path. The `PromptContributorService` is a standard `@Service` bean with no `@Lazy` or `@Conditional`. `ObjectProvider.getIfAvailable()` only returns null if the bean doesn't exist in the context — which would only happen if Spring component scanning excluded it (unlikely in test/prod contexts). The fallback exists as defensive programming against circular dependency scenarios where `ObjectProvider` is needed to break the cycle. In practice, the delegate path fires for all tested scenarios.

---

## E32. ErrorDescriptor Persistence After Retry Recovery

**Where**: `retry_*.blackboard.md` — check `errorType` across ALL calls (not just the retry call)
**Question**: After a retry succeeds, does `errorType` get reset to `NoError` or `null`? Or does the error from the failed first attempt persist for the entire remaining workflow?
**Why it matters**: If the error persists (which the trace data shows it does), then ALL subsequent LLM calls in the workflow see a non-NoError `errorDescriptor`. This means retry-aware filtering is active for every call after the first failure — potentially excluding prompt contributors that should be included in normal (non-retry) calls. The first call after recovery should have retry filtering, but subsequent calls (discovery agent, planning, etc.) are not retries and should not be filtered.
**What to look for**: In `retry_nullResultError_*.blackboard.md`, check if `errorType: NullResultError` appears in calls 3, 4, 5... (not just call 2). If it does, the error is never cleared.

**Status**: INVESTIGATED (2026-04-04)
**Finding**: **Confirmed — errorType persists for the ENTIRE workflow after a retry**. In `retry_nullResultError_classifiedAndRecoveredOnRetry.blackboard.md`, ALL 14 calls after the retry show `errorType: NullResultError`. Same pattern in all 6 retry tests: CompactionError, TimeoutError, UnparsedToolCallError, IncompleteJsonError, and ParseError all persist across the entire workflow. **This is a potential issue**: after the orchestrator retry succeeds, the discovery agent, planning agent, ticket agent, and all other phases see `isRetry = true` and have their prompt contributors filtered. Currently no contributor overrides `RetryAware` defaults (all return `false`), so ALL contributors would be excluded on retry for the rest of the workflow. In the current tests this doesn't cause failures because `QueuedLlmRunner` returns pre-queued responses regardless of prompt content, but in production this could degrade prompt quality for the entire workflow after a single transient error.
**Action needed**: `AgentExecutor.run()` should clear `errorType` (set to `NoError`) after a successful LLM call, so that only the immediate retry call has error-aware filtering, not subsequent calls.

---

## Future Exploration Candidates

- **F-E1**: Data layer operation timing relative to agent completion — do DataLayerOperationNodes always complete before their parent agent completes?
- **F-E2**: Blackboard entry types at each phase boundary — does findLastWorkflowRequest consistently return the right type?
- **F-E3**: Concurrent event emission ordering under parallel agents — are events interleaved or batched?
- **F-E4**: Graph snapshot consistency — does the same graph state ever appear in two consecutive snapshots (indicating a no-op LLM call)?
- **F-E5**: ToolContextDecorator sort stability — if two decorators share the same `order()` value, does their relative execution order vary between runs? Could cause non-deterministic tool registration.
- **F-E6**: Justification prompt template resolution — do all 16 workflow templates actually include `_review_justification.jinja` and their role-specific justification partial? Grep the resolved prompt text in trace data.
- **F-E7**: chatId correctness in chained A2A calls — when agent A calls B calls C, does each hop's A2A node have the correct chatId pointing to the *next* target, not a stale value from a prior hop? Cross-reference chatId in `### AgentToAgent:` sections across the call chain.
- **F-E8**: SessionKeyResolutionService behavior after chatId fix — does `resolveNodeBySessionKey()` now correctly find A2A nodes by the target session key? Test with concurrent parallel calls where two A2A nodes target different agents.
- **F-E9**: Layer binding entity extraction impact — the current JSON blob pattern for layer bindings means enable/disable must deserialize, modify, and re-serialize the entire blob. With the planned `LayerBindingEntity` extraction (see `tickets/data-layer-filter/layer-binding-entities.md`), each binding gets its own row with direct SQL updates. Explore whether current tests cover the edge case where a registration has zero bindings.
- **F-E10**: `AgentExecutor.assemblePrompt` vs `DefaultLlmRunner` prompt assembly divergence — `assemblePrompt` uses `PromptContributorService` (Spring bean), so new `PromptContributor` beans auto-included. Risk limited to `LlmCallDecorator`-level concerns (FilterPropertiesDecorator, etc.) which are NOT applied in controller response path by design.
- **F-E11**: ~~`publishInterrupt()` does not set `interruptibleContext` on origin node~~ — **FIXED**: Added `InterruptService.publishInterruptWithContext()` which sets `interruptibleContext` on the origin node (if null) before publishing. All three call sites in InterruptService now use this method. Both `callController_happyPath` and `controllerResponse` tests now emit `InterruptStatusEvent(RESOLVED)`. Guard: only sets context if null (won't overwrite existing), logs error if existing context has unexpected non-REQUESTED status.
- **F-E12**: Worktree cleanup after GoalCompletedEvent — who deletes the shared worktree? The controller receives `worktreePath` but there's no explicit cleanup lifecycle. If the controller crashes before merge-to-source, the worktree becomes an orphan on disk. Explore whether `ExecutionScopeService.completeExecution()` triggers cleanup or if it's left to the controller.
- **F-E13**: `worktreePath` format consistency — GoalCompletedEvent.worktreePath is a raw path string (`/var/folders/...`) while `MainWorktreeContext.worktreePath` is a `file:///` URI. Are downstream consumers prepared for both formats? The `EmitActionCompletedResultDecorator` calls `Path::toString` which strips the URI scheme.
- **F-E14**: Single-worktree model under loopback — when `planningCollector_loopsBackToDiscovery` triggers a second discovery phase, does the same shared worktree carry forward? Or does the second discovery phase create a new worktree? The current tests don't cover loopback + worktree interaction.
- **F-E18**: ErrorDescriptor clearing after successful retry — `AgentExecutor.run()` should call `history.addError(new ErrorDescriptor.NoError())` after a successful `llmRunner.runWithTemplate()` return. This ensures only the immediate retry call sees error-aware prompt filtering, not the entire remaining workflow. See E32 for details.
- **F-E15**: RetryAware method collision — do any existing `PromptContributor` or `PromptContributorFactory` implementations define methods like `includeOnCompaction()` with different signatures or semantics that could conflict with the new `RetryAware` defaults?
- **F-E16**: Prompt assembly delimiter consistency — after consolidating `assemblePrompt` into `PromptContributorService`, verify that the `--- start [name]` / `--- end [name]` delimiter format in `PromptReceivedEvent` traces matches what was emitted before the consolidation. Any format change could break downstream parsers (prompt health check propagators).
- **F-E17**: NullResultError/IncompleteJsonError classification boundary — `ActionRetryListenerImpl.classify()` checks for "null" AND "result" in the message, OR "empty response". Could a legitimate non-null-result error message contain these substrings and be misclassified? Similarly for "incomplete json" or "truncated".
- **F-E19**: requestContextId stability across retries — in retry traces, verify that two consecutive `AgentExecutorStartEvent`s for the same session have identical `requestContextId` but different `nodeId`. If `requestContextId` differs, the `promptContext.currentContextId()` is changing between retries (possible if PromptContext is rebuilt). This would break the semantic contract.
- **F-E20**: startNodeId↔nodeId matching correctness in integration — in retry traces, verify that the `AgentExecutorCompleteEvent.startNodeId` matches the second `AgentExecutorStartEvent.nodeId` (the successful retry), not the first (failed). The first has no matching complete. The second's complete should have `startNodeId` = second start's `nodeId`.
- **F-E21**: ErrorContext chain in integration retry traces — in retry tests, check `*.blackboard.md` for the `errorContext` field. After a single-retry scenario, the retry call's error should show `errorContext: 0 previous errors` (it's the first error). If multiple retries are ever tested, the second error should show `1 previous error` with the first error's type.
- **F-E22**: Cross-session error isolation — if two different sessions (e.g., discovery agent and planning agent) both experience retries, do their `errorType(sessionKey)` calls return independent errors? The `sessionKey` scoping should prevent cross-contamination. Currently no integration test covers two-session concurrent retry.
