# Multi-Agent IDE Workflows

This document defines the agent orchestration workflows for the multi-agent IDE system. The system follows a specific sequential and nested orchestration pattern where agents can kick off other agents, sometimes recursively.

## Architecture Overview

The system uses a computation graph where each agent invocation creates a node. Orchestrator agents coordinate multiple child agents, collecting their results and merging them into a unified output. Key principle: orchestrators process child agent results and may trigger merger agents or other orchestrators.

### Node Lifecycle
1. **Before Invocation**: Agent node is registered in the computation graph as RUNNING
2. **During Invocation**: Agent performs its task (may spawn child agents)
3. **After Invocation**: Agent node is updated with results and marked as COMPLETED, may trigger dependent agents

---

## Main Orchestration Loop

The OrchestratorAgent coordinates the following sequence:

```
OrchestratorAgent (Entry Point)
    │
    ├─ Phase 1: Discovery
    ├─ Phase 2: Planning  
    ├─ Phase 3: Ticket-Based Implementation
    └─ Phase 4: Integration & Merge
```

---

## Phase 1: Discovery Workflow

### Participants
- **OrchestratorAgent** (root coordinator)
- **DiscoveryOrchestrator** (decides how to divide discovery work)
- **DiscoveryAgent(s)** (retrieves contextual information per module/section)
- **DiscoveryMerger** (merges all discovery files into unified document)

### Flow

```
OrchestratorAgent.coordinateWorkflow()
    │
    ├→ afterDiscoveryComplete() [if previous discovery exists]
    │   ↓
    ├→ PHASE 1: Discovery
    │
    ├→ OrchestratorAgent kicks off DiscoveryOrchestrator
    │   (Goal: "Understand codebase for feature X")
    │
    ├→ DiscoveryOrchestrator (RUNNING)
    │   │
    │   ├→ Analyze goal and repository structure
    │   │
    │   ├→ Determine division strategy:
    │   │  - Repository size: large repos → divide by module/directory
    │   │  - Ticket scope: multiple components → divide by component
    │   │  - Decision output: number of agents and their subdomain goals
    │   │
    │   ├→ For each discovery subdomain, create DiscoveryAgent with:
    │   │  - Subdomain focus (e.g., "authentication module")
    │   │  - Goal context
    │   │  - Worktree reference
    │   │
    │   └─ afterDiscoveryOrchestratorInvocation()
    │      │
    │      ├→ Register DiscoveryOrchestratorNode
    │      └→ Kick off N DiscoveryAgent(s) in parallel
    │
    ├→ DiscoveryAgent(s) (RUNNING - parallel execution)
    │   │
    │   ├→ Receive goal + subdomain focus
    │   │
    │   ├→ Use tools to:
    │   │  ├─ Search codebase for relevant patterns
    │   │  ├─ Read key source files
    │   │  ├─ Analyze dependencies and imports
    │   │  └─ Identify architectural patterns
    │   │
    │   ├→ Write discovery file:
    │   │  File: `DISCOVERY_SKILLS_{agent_id}.md`
    │   │  Contains:
    │   │    - Module/component overview
    │   │    - Key classes and responsibilities
    │   │    - Data flow patterns
    │   │    - Integration points
    │   │    - Technology stack
    │   │    - Test patterns
    │   │
    │   └─ afterDiscoveryAgentInvocation(filename, metadata)
    │      ├→ Register DiscoveryNode
    │      ├→ Update node with filename
    │      └→ Mark COMPLETED
    │
    ├→ [All DiscoveryAgent(s) completed]
    │
    ├→ OrchestratorAgent kicks off DiscoveryMerger
    │   (Input: all DISCOVERY_SKILLS_*.md files)
    │
    ├→ DiscoveryMerger (RUNNING)
    │   │
    │   ├→ Collect all DISCOVERY_SKILLS_*.md files
    │   │
    │   ├→ Merge into unified document:
    │   │  File: `DISCOVERY_UNIFIED.md`
    │   │
    │   ├→ Update base Spec with discovery findings
    │   │
    │   └─ afterDiscoveryMergerInvocation(mergedFile)
    │      ├→ Register DiscoveryMergerNode
    │      ├→ Update DiscoveryOrchestratorNode to COMPLETED
    │      └→ Return merged discovery to OrchestratorAgent
    │
    └─ OrchestratorAgent receives Discovery results
       └→ Proceed to Phase 2
```

### Lifecycle Handlers

**beforeDiscoveryOrchestratorInvocation**:
```java
beforeDiscoveryOrchestratorInvocation(String goal, String parentNodeId, String nodeId)
- Register DiscoveryOrchestratorNode in graph with status RUNNING
```

**afterDiscoveryOrchestratorInvocation**:
```java
afterDiscoveryOrchestratorInvocation(String divisionStrategy, String nodeId)
- Update DiscoveryOrchestratorNode with division plan
- Kick off N DiscoveryAgent(s) based on strategy
- Wait for all DiscoveryAgent(s) to complete before proceeding
```

**beforeDiscoveryAgentInvocation**:
```java
beforeDiscoveryAgentInvocation(String goal, String subdomainFocus, String parentNodeId, String nodeId)
- Register DiscoveryNode in graph with status RUNNING
- Link to DiscoveryOrchestratorNode as parent
```

**afterDiscoveryAgentInvocation**:
```java
afterDiscoveryAgentInvocation(String discoveryFilename, String metadata, String nodeId)
- Update DiscoveryNode with filename and metadata
- Mark as COMPLETED
- Track completion; signal when all siblings done
```

**beforeDiscoveryMergerInvocation**:
```java
beforeDiscoveryMergerInvocation(String allDiscoveryFiles, String parentNodeId, String nodeId)
- Register DiscoveryMergerNode in graph with status RUNNING
- Only invoked AFTER ALL DiscoveryAgent siblings are COMPLETED
- Provide list of all discovery files as input
```

**afterDiscoveryMergerInvocation**:
```java
afterDiscoveryMergerInvocation(String mergedDiscoveryFile, String nodeId)
- Update DiscoveryMergerNode with merged filename
- Update parent DiscoveryOrchestratorNode to COMPLETED
- Mark as COMPLETED
- Signal OrchestratorAgent to proceed to Phase 2 (Planning)
```

---

## Phase 2: Planning Workflow

### Participants
- **OrchestratorAgent** (root coordinator)
- **PlanningOrchestrator** (decomposes goals into work items)
- **PlanningAgent(s)** (generates structured plans)
- **PlanningMerger** (merges planning files into tickets)

### Flow

```
OrchestratorAgent receives Discovery results
    │
    ├→ PHASE 2: Planning
    │
    ├→ OrchestratorAgent kicks off PlanningOrchestrator
    │   (Input: Goal + Discovery context)
    │
    ├→ PlanningOrchestrator (RUNNING)
    │   │
    │   ├→ Analyze goal with discovery context
    │   │
    │   ├→ Determine division strategy:
    │   │  - Simple goal → 1 PlanningAgent
    │   │  - Complex goal → Multiple PlanningAgents
    │   │
    │   ├→ For each planning subdomain:
    │   │  - Create PlanningAgent with goal + discovery context
    │   │
    │   └─ afterPlanningOrchestratorInvocation()
    │      │
    │      ├→ Register PlanningOrchestratorNode
    │      └→ Kick off N PlanningAgent(s) in parallel
    │
    ├→ PlanningAgent(s) (RUNNING - parallel execution)
    │   │
    │   ├→ Analyze goal with discovery context
    │   │
    │   ├→ Break down into work items:
    │   │  1. Architecture & Setup
    │   │  2. Implementation
    │   │  3. Testing & Validation
    │   │
    │   ├→ Each work item becomes a Ticket (future agent task)
    │   │
    │   ├→ Write planning file:
    │   │  File: `PLANNING_SKILLS_{agent_id}.md`
    │   │  Contains:
    │   │    - Work item breakdown
    │   │    - Ticket definitions with clear tasks
    │   │    - Dependencies between tickets
    │   │    - Estimated effort
    │   │    - Key architectural decisions
    │   │
    │   └─ afterPlanningAgentInvocation(filename, metadata)
    │      ├→ Register PlanningNode
    │      ├→ Update node with filename
    │      └→ Mark COMPLETED
    │
    ├→ [All PlanningAgent(s) completed]
    │
    ├→ OrchestratorAgent kicks off PlanningMerger
    │   (Input: all PLANNING_SKILLS_*.md files)
    │
    ├→ PlanningMerger (RUNNING)
    │   │
    │   ├→ Collect all PLANNING_SKILLS_*.md files
    │   │
    │   ├→ Merge into unified plan:
    │   │  File: `UNIFIED_PLAN.md`
    │   │
    │   ├→ Extract and organize all tickets:
    │   │  File: `TICKETS.md`
    │   │  Format: Each ticket has:
    │   │    - Ticket ID
    │   │    - Title and description
    │   │    - Implementation tasks
    │   │    - Acceptance criteria
    │   │    - Dependencies
    │   │
    │   ├→ Update base Spec with finalized plan and tickets
    │   │
    │   └─ afterPlanningMergerInvocation(ticketsFile)
    │      ├→ Register PlanningMergerNode
    │      ├→ Update PlanningOrchestratorNode to COMPLETED
    │      ├→ Extract ticket count
    │      └→ Return tickets to OrchestratorAgent
    │
    └─ OrchestratorAgent receives tickets
       └→ Proceed to Phase 3
```

### Lifecycle Handlers

**beforePlanningOrchestratorInvocation**:
```java
beforePlanningOrchestratorInvocation(String goal, String discoveryContext, String parentNodeId, String nodeId)
- Register PlanningOrchestratorNode in graph with status RUNNING
```

**afterPlanningOrchestratorInvocation**:
```java
afterPlanningOrchestratorInvocation(String divisionStrategy, String nodeId)
- Update PlanningOrchestratorNode with division plan
- Kick off N PlanningAgent(s) based on strategy
```

**beforePlanningAgentInvocation**:
```java
beforePlanningAgentInvocation(String goal, String discoveryContext, String parentNodeId, String nodeId)
- Register PlanningNode in graph with status RUNNING
- Store discovery context as reference
```

**afterPlanningAgentInvocation**:
```java
afterPlanningAgentInvocation(String planningFilename, String metadata, String nodeId)
- Update PlanningNode with filename
- Mark as COMPLETED
- Track completion; signal when all siblings done
```

**beforePlanningMergerInvocation**:
```java
beforePlanningMergerInvocation(String allPlanningFiles, String parentNodeId, String nodeId)
- Register PlanningMergerNode in graph with status RUNNING
- Only invoked AFTER ALL PlanningAgent siblings are COMPLETED
- Provide list of all planning files as input
```

**afterPlanningMergerInvocation**:
```java
afterPlanningMergerInvocation(String ticketsFile, int ticketCount, String nodeId)
- Update PlanningMergerNode with tickets filename
- Extract ticket count
- Update parent PlanningOrchestratorNode to COMPLETED
- Mark as COMPLETED
- Signal OrchestratorAgent with tickets to proceed to Phase 3
```

---

## Phase 3: Ticket-Based Implementation Workflow

### Participants
- **OrchestratorAgent** (root coordinator)
- **TicketOrchestrator** (manages ticket execution)
- **TicketAgent(s)** (implements individual tickets)
- **ReviewAgent** (reviews each ticket + all tickets merged)
- **MergerAgent** (merges ticket worktrees incrementally + final merge)

### Flow - Single Repository (No Submodules)

```
OrchestratorAgent receives tickets
    │
    ├→ PHASE 3: Ticket-Based Implementation
    │
    ├→ OrchestratorAgent kicks off TicketOrchestrator
    │   (Input: tickets list, goal context)
    │
    ├→ TicketOrchestrator (RUNNING)
    │   │
    │   ├→ Create worktree for ticket orchestration
    │   │
    │   ├→ For each ticket in tickets list:
    │   │  - Create detail/requirements file at worktree root:
    │   │    File: `TICKET_DETAILS.md` or similar
    │   │    Content: Ticket description, requirements, acceptance criteria
    │   │  - Kick off TicketAgent(s) with:
    │   │    - Ticket details file path (at worktree root)
    │   │    - Worktree reference
    │   │    - Discovery + Planning context
    │   │
    │   └─ afterTicketOrchestratorInvocation()
    │      ├→ Register TicketOrchestratorNode
    │      └→ Kick off N TicketAgent(s) in sequence or parallel
    │
    ├→ TicketAgent(s) (RUNNING - per ticket)
    │   │
    │   ├→ Receive ticket details file path + worktree
    │   │
    │   ├→ Create feature branch in worktree
    │   │
    │   ├→ Read ticket details file to understand requirements
    │   │
    │   ├→ Use tools to:
    │   │  ├─ Read discovery and planning context
    │   │  ├─ Analyze existing code patterns
    │   │  ├─ Generate implementation
    │   │  ├─ Create/modify files
    │   │  └─ Run tests in worktree
    │   │
    │   ├→ Commit implementation to feature branch
    │   │
    │   └─ afterTicketAgentInvocation(implementationSummary)
    │      ├→ Register TicketNode
    │      ├→ Store implementation summary
    │      └→ Mark COMPLETED
    │
    ├→ [First TicketAgent completed]
    │
    ├→ TicketOrchestrator receives TicketAgent result
    │   │
    │   ├→ ReviewAgent (RUNNING)
    │   │   │
    │   │   ├→ Review first ticket's generated code:
    │   │   │  - Code quality and patterns
    │   │   │  - Test coverage
    │   │   │  - Spec compliance
    │   │   │
    │   │   ├→ Decision: APPROVED or NEEDS_REVISION
    │   │   │
    │   │   └─ afterReviewAgentInvocation(approval, feedback)
    │   │      ├→ Register AgentReviewNode
    │   │      └→ Mark with approval status
    │   │
    │   ├→ [Review APPROVED]
    │   │
    │   ├→ MergerAgent (RUNNING)
    │   │   │
    │   │   ├→ Merge feature branch into TicketOrchestrator worktree
    │   │   │
    │   │   ├→ Merge TICKET_DETAILS.md with TicketOrchestrator's TICKET_DETAILS.md:
    │   │   │  - Track completed tickets
    │   │   │  - Update progress
    │   │   │
    │   │   └─ afterMergerAgentInvocation(mergeResult)
    │   │      ├→ Register MergeNode
    │   │      ├→ Mark completed ticket
    │   │      └→ Update TicketOrchestrator worktree state
    │   │
    │   ├→ TicketOrchestrator updates ticket queue metadata (pointer + queue) after each merge; conflicts mark MergeNode as WAITING_INPUT with conflict files recorded.
    │   │
    │   └→ [Ready for next ticket]
    │
    ├→ [Repeat for each remaining TicketAgent]
    │   - TicketAgent for ticket 2 → ReviewAgent → MergerAgent
    │   - TicketAgent for ticket 3 → ReviewAgent → MergerAgent
    │   - ... and so on
    │
    ├→ [All TicketAgent(s) completed and merged into TicketOrchestrator worktree]
    │
    ├→ TicketOrchestrator calls ReviewAgent (final review)
    │   │
    │   ├→ ReviewAgent (RUNNING)
    │   │   │
    │   │   ├→ Review all merged code in TicketOrchestrator worktree:
    │   │   │  - Integration between tickets
    │   │   │  - No conflicts or regressions
    │   │   │  - Full test suite passes
    │   │   │  - Spec compliance
    │   │   │
    │   │   ├→ Decision: APPROVED or NEEDS_REVISION
    │   │   │
    │   │   └─ afterReviewAgentInvocation(approval, feedback)
    │   │      └→ Mark with final approval status
    │   │
    │   └→ [Review APPROVED]
    │
    ├→ TicketOrchestrator calls MergerAgent (final merge)
    │   │
    │   ├→ MergerAgent (RUNNING)
    │   │   │
    │   │   ├→ Merge TicketOrchestrator worktree into main repository
    │   │   │
    │   │   ├→ Merge TICKET_DETAILS.md into root SPEC.md or results file:
    │   │   │  - Record all completed tickets
    │   │   │  - Document final implementation state
    │   │   │
    │   │   ├→ Run full integration test suite
    │   │   │
    │   │   └─ afterMergerAgentInvocation(mergeResult)
    │   │      ├→ Register final MergeNode
    │   │      └→ Mark all COMPLETED
    │   │
    │   └→ afterTicketOrchestratorInvocation(finalResult)
    │      ├→ Update TicketOrchestratorNode to COMPLETED
    │      └→ Return result to OrchestratorAgent
    │
    └─ OrchestratorAgent receives implementation results
       └→ Workflow complete
```

### Flow - Multiple Repositories With Submodules

When the main repository has submodules, the flow is identical EXCEPT:

```
TicketOrchestrator
    │
    ├→ Create main worktree + ALL submodule worktrees
    │
    ├→ For each ticket:
    │   │
    │   ├→ TicketAgent receives:
    │   │  ├─ Ticket details file
    │   │  ├─ Main worktree reference
    │   │  ├─ ALL submodule worktree references
    │   │
    │   ├→ TicketAgent may modify:
    │   │  ├─ Files in main worktree
    │   │  ├─ Files in submodule worktrees
    │   │  └─ Both simultaneously if needed
    │   │
    │   ├→ ReviewAgent reviews:
    │   │  ├─ All changed files across main + submodules
    │   │  └─ Cross-module integration
    │   │
    │   └→ MergerAgent merges:
    │      ├─ Feature branches in main worktree
    │      ├─ Feature branches in ALL submodule worktrees
    │      └─ TICKET_DETAILS.md with submodule tracking
    │
    ├→ Final ReviewAgent reviews:
    │  └─ All merged code in main + all submodules
    │
    └→ Final MergerAgent merges:
       ├─ Main worktree changes into main repository
       ├─ All submodule worktree changes into respective submodules
       └─ Updates SPEC.md with complete implementation state
```

### Lifecycle Handlers

**beforeTicketOrchestratorInvocation**:
```java
beforeTicketOrchestratorInvocation(String tickets, String discoveryContext, String planningContext, 
                                   boolean hasSubmodules, String parentNodeId, String nodeId)
- Create TicketOrchestrator worktree (main + submodules if present)
- Create initial TICKET_DETAILS.md at worktree root
- Register TicketOrchestratorNode in graph with status RUNNING
```

**afterTicketOrchestratorInvocation**:
```java
afterTicketOrchestratorInvocation(String ticketCount, String nodeId)
- Extract ticket count
- Kick off first TicketAgent
```

**beforeTicketAgentInvocation**:
```java
beforeTicketAgentInvocation(String ticketDetails, String ticketDetailsFilePath, String mainWorktreeId, 
                            List<String> submoduleWorktreeIds, String discoveryContext, 
                            String planningContext, String parentNodeId, String nodeId)
- Create feature branch in worktree(s)
- Register TicketNode in graph with status RUNNING
```

**afterTicketAgentInvocation**:
```java
afterTicketAgentInvocation(String implementationSummary, String nodeId)
- Update TicketNode with implementation summary
- Commit to feature branch
- Mark as COMPLETED
- Signal TicketOrchestrator to kick off ReviewAgent for this ticket
```

**beforeReviewAgentInvocation (per-ticket)**:
```java
beforeReviewAgentInvocation(String generatedCode, String criteria, String parentNodeId, String nodeId)
- Register AgentReviewNode in graph with status RUNNING
- Input: code from TicketAgent(s)
```

**afterReviewAgentInvocation (per-ticket)**:
```java
afterReviewAgentInvocation(String evaluation, String nodeId)
- Determine APPROVED or NEEDS_REVISION
- If APPROVED: signal TicketOrchestrator to call MergerAgent
- If NEEDS_REVISION: signal TicketOrchestrator to re-invoke TicketAgent with feedback
```

**beforeMergerAgentInvocation (per-ticket)**:
```java
beforeMergerAgentInvocation(String ticketBranchName, String mainWorktreeId, List<String> submoduleWorktreeIds, 
                            String parentNodeId, String nodeId)
- Register MergeNode in graph with status RUNNING
- Only invoked AFTER ReviewAgent approves
```

**afterMergerAgentInvocation (per-ticket)**:
```java
afterMergerAgentInvocation(String mergeResult, String nodeId)
- Merge ticket feature branch into TicketOrchestrator worktree
- Merge TICKET_DETAILS.md to track progress
- Update TicketOrchestrator state
- Mark as COMPLETED
- If more tickets exist: signal TicketOrchestrator to proceed to next ticket
- If all tickets done: signal TicketOrchestrator to call final ReviewAgent
```

**beforeReviewAgentInvocation (final)**:
```java
beforeReviewAgentInvocation(String allMergedCode, String finalCriteria, String parentNodeId, String nodeId)
- Register final AgentReviewNode in graph
- Input: all code merged in TicketOrchestrator worktree(s)
```

**afterReviewAgentInvocation (final)**:
```java
afterReviewAgentInvocation(String finalEvaluation, String nodeId)
- Determine final APPROVED or NEEDS_REVISION
- If APPROVED: signal TicketOrchestrator to call final MergerAgent
- If NEEDS_REVISION: escalate for human review or re-plan
```

**beforeMergerAgentInvocation (final)**:
```java
beforeMergerAgentInvocation(String mainWorktreeId, List<String> submoduleWorktreeIds, 
                            String parentNodeId, String nodeId)
- Register final MergeNode in graph with status RUNNING
```

**afterMergerAgentInvocation (final)**:
```java
afterMergerAgentInvocation(String finalMergeResult, String nodeId)
- Merge main worktree changes back into repository
- Merge all submodule worktree changes into respective submodules
- Update root SPEC.md with implementation results
- Mark TicketOrchestratorNode as COMPLETED
- Return results to OrchestratorAgent
```

---

## Node Types

### Primary Node Types
- **OrchestratorNode**: Root orchestrator
- **DiscoveryOrchestratorNode**: Manages discovery phase
- **DiscoveryNode**: Individual discovery agent work
- **PlanningOrchestratorNode**: Manages planning phase
- **PlanningNode**: Individual planning agent work
- **TicketOrchestratorNode**: Manages ticket execution
- **TicketNode**: Individual ticket implementation
- **AgentReviewNode**: Code review at any phase
- **MergeNode**: Merge operations at any phase

### Merger Node Types
- **DiscoveryMergerNode**: Merges discovery files
- **PlanningMergerNode**: Merges planning files into tickets
- **TicketMergerNode**: Merges ticket worktrees

---

## Artifact Files Generated

### By Phase

**Discovery Phase**:
- `DISCOVERY_SKILLS_*.md` - Per-discovery-agent findings
- `DISCOVERY_UNIFIED.md` - Merged discovery document
- `SPEC.md` - Updated with discovery context

**Planning Phase**:
- `PLANNING_SKILLS_*.md` - Per-planning-agent plans
- `UNIFIED_PLAN.md` - Merged planning document
- `TICKETS.md` - Extracted tickets with IDs and tasks
- `SPEC.md` - Updated with plan and tickets

**Ticket Implementation Phase**:
- `TICKET_DETAILS.md` - At TicketOrchestrator and TicketAgent worktree roots
- Feature branches per ticket in worktrees
- Implementation code files (varies per ticket)
- `REVIEW_FEEDBACK.md` - If revisions needed (per ticket)
- Final merged code in repository/submodules

---

## Error Handling & Revision Cycles

### Within Phase
- **TicketAgent revision**: If ReviewAgent returns NEEDS_REVISION
  - TicketAgent is invoked again with review feedback
  - Modifications committed to same feature branch
  - ReviewAgent re-evaluates
  - Cycle repeats until APPROVED

### Between Phases
- **Phase failure**: Previous phase results are cached
  - Can re-trigger failed orchestrator from that phase
  - Dependent phases wait or are skipped
  - Human can intervene

### Critical Failures
- Workflow halts at failed node
- Human intervention required
- Can resume from checkpoints

---

## Context Propagation

Each phase builds on previous results:

```
Discovery Output (context) 
    ↓ input to
Planning (uses discovery to generate informed tickets)
    ↓ input to
Ticket Implementation (uses discovery + planning for each ticket)
    ↓ result
Final merge + spec update
```

Each child agent receives:
- Its immediate parent's requirements
- Discovery context (from Phase 1)
- Planning context (from Phase 2)
- Previous ticket results (from Phase 3)

---

## Implementation Checklist

### For Each Orchestrator
- [ ] Define decision logic (how to divide work)
- [ ] Implement before-invocation handler (create worktree, register node)
- [ ] Implement after-invocation handler (kick off child agents)
- [ ] Define child agent selection and parameterization
- [ ] Handle child agent result collection
- [ ] Implement merger logic (coordinate with MergerAgent)
- [ ] Wire into LangChain4jConfiguration beans
- [ ] Add lifecycle handlers to AgentLifecycleHandler

### For Each Work Agent
- [ ] Define task logic
- [ ] Implement before-invocation handler (register node, create branch)
- [ ] Implement after-invocation handler (commit, signal parent)
- [ ] Handle context from parent orchestrator
- [ ] Generate appropriate artifacts
- [ ] Wire into LangChain4jConfiguration beans
- [ ] Add lifecycle handlers to AgentLifecycleHandler

### For Review & Merge
- [ ] Define review criteria per phase
- [ ] Implement approval/rejection decision logic
- [ ] Define merge strategy per context
- [ ] Handle merge conflicts
- [ ] Wire both agents into LangChain4jConfiguration
- [ ] Add lifecycle handlers to AgentLifecycleHandler
- [ ] Test review + merge + revision cycles
