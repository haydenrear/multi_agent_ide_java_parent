# QueuedLlmRunner Call Log

| Field | Value |
|-------|-------|
| **Test class** | `WorkflowAgentWorktreeMergeIntTest` |
| **Test method** | `discoveryPhase_twoAgents_bothMergeSuccessfully` |
| **Started at** | 2026-02-25T06:18:00.428137Z |

---

## Call 1: `workflow/orchestrator`

**Request type**: `OrchestratorRequest`  
**Response type**: `OrchestratorRouting`  

### Decorated Request (`OrchestratorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.561349Z",
      "lastCommitHash" : "5a2c815c3b35bf75d63a00cfc3629fdbefbc1802",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
        "baseBranch" : "derived-EC7MDW4D",
        "status" : "ACTIVE",
        "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
        "createdAt" : "2026-02-25T06:17:54.560844Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
      "baseBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.560844Z",
      "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "metadata" : { }
    } ]
  },
  "goal" : "Discover features",
  "phase" : "DISCOVERY"
}
```

### Response (`OrchestratorRouting`)

```json
{
  "discoveryOrchestratorRequest" : {
    "goal" : "Discover features"
  }
}
```

---

## Call 2: `workflow/discovery_orchestrator`

**Request type**: `DiscoveryOrchestratorRequest`  
**Response type**: `DiscoveryOrchestratorRouting`  

### Decorated Request (`DiscoveryOrchestratorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.561349Z",
      "lastCommitHash" : "5a2c815c3b35bf75d63a00cfc3629fdbefbc1802",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
        "baseBranch" : "derived-EC7MDW4D",
        "status" : "ACTIVE",
        "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
        "createdAt" : "2026-02-25T06:17:54.560844Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
      "baseBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.560844Z",
      "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "metadata" : { }
    } ]
  },
  "goal" : "Discover features"
}
```

### Response (`DiscoveryOrchestratorRouting`)

```json
{
  "agentRequests" : {
    "requests" : [ {
      "goal" : "Discover features",
      "subdomainFocus" : "Area 1"
    }, {
      "goal" : "Discover features",
      "subdomainFocus" : "Area 2"
    } ]
  }
}
```

---

## Call 3: `workflow/discovery_agent`

**Request type**: `DiscoveryAgentRequest`  
**Response type**: `DiscoveryAgentRouting`  

### Decorated Request (`DiscoveryAgentRequest`)

```json
{
  "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA/01KJ9Q53SBEYYX6KN4RYES9RAR",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/25fe6e68-3fe9-44c8-b134-713a300b6ea4/",
      "baseBranch" : "derived-EC7MDW4D",
      "derivedBranch" : "discovery-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
      "createdAt" : "2026-02-25T06:18:04.908782Z",
      "lastCommitHash" : "5a2c815c3b35bf75d63a00cfc3629fdbefbc1802",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "0c77df25-a5c7-49a7-86a7-daac37c41da1",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/25fe6e68-3fe9-44c8-b134-713a300b6ea4/libs/test-sub/",
        "baseBranch" : "discovery-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
        "createdAt" : "2026-02-25T06:18:05.315711Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "0c77df25-a5c7-49a7-86a7-daac37c41da1",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/25fe6e68-3fe9-44c8-b134-713a300b6ea4/libs/test-sub/",
      "baseBranch" : "discovery-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
      "createdAt" : "2026-02-25T06:18:05.315711Z",
      "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
      "metadata" : { }
    } ]
  },
  "goal" : "Discover features",
  "subdomainFocus" : "Area 1"
}
```

### Response (`DiscoveryAgentRouting`)

```json
{
  "agentResult" : {
    "output" : "Agent 1 findings"
  }
}
```

---

## Call 4: `workflow/discovery_agent`

**Request type**: `DiscoveryAgentRequest`  
**Response type**: `DiscoveryAgentRouting`  

### Decorated Request (`DiscoveryAgentRequest`)

```json
{
  "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA/01KJ9Q53SBK9ESPKJ1ES42H3DJ",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/05a25c3d-f69e-4184-9336-60af94d06013/",
      "baseBranch" : "derived-EC7MDW4D",
      "derivedBranch" : "discovery-2-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
      "createdAt" : "2026-02-25T06:18:05.711014Z",
      "lastCommitHash" : "5a2c815c3b35bf75d63a00cfc3629fdbefbc1802",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "6542bb55-912c-4462-98be-a943ec0858fa",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/05a25c3d-f69e-4184-9336-60af94d06013/libs/test-sub/",
        "baseBranch" : "discovery-2-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
        "createdAt" : "2026-02-25T06:18:06.115593Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "6542bb55-912c-4462-98be-a943ec0858fa",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/05a25c3d-f69e-4184-9336-60af94d06013/libs/test-sub/",
      "baseBranch" : "discovery-2-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
      "createdAt" : "2026-02-25T06:18:06.115593Z",
      "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
      "metadata" : { }
    } ]
  },
  "goal" : "Discover features",
  "subdomainFocus" : "Area 2"
}
```

### Response (`DiscoveryAgentRouting`)

```json
{
  "agentResult" : {
    "output" : "Agent 2 findings"
  }
}
```

---

## Call 5: `workflow/discovery_dispatch`

**Request type**: `DiscoveryAgentResults`  
**Response type**: `DiscoveryAgentDispatchRouting`  

### Decorated Request (`DiscoveryAgentResults`)

```json
{
  "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q578TSQEKAKMDDGC1B9DW",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.561349Z",
      "lastCommitHash" : "5a2c815c3b35bf75d63a00cfc3629fdbefbc1802",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
        "baseBranch" : "derived-EC7MDW4D",
        "status" : "ACTIVE",
        "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
        "createdAt" : "2026-02-25T06:17:54.560844Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
      "baseBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.560844Z",
      "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "metadata" : { }
    } ]
  },
  "result" : [ {
    "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q578TSQEKAKMDDGC1B9DW/01KJ9Q578T8NKMPK42VCGWBKR5",
    "output" : "Agent 1 findings",
    "mergeDescriptor" : {
      "mergeDirection" : "TRUNK_TO_CHILD",
      "successful" : true,
      "conflictFiles" : [ ],
      "submoduleMergeResults" : [ ],
      "mainWorktreeMergeResult" : {
        "mergeId" : "64f841f9-86c8-4432-8ce8-104110aa94d1",
        "childWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "parentWorktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
        "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416",
        "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/25fe6e68-3fe9-44c8-b134-713a300b6ea4",
        "successful" : true,
        "mergeCommitHash" : "997fe33b1adee30628751192e25254605f91aa76",
        "conflicts" : [ ],
        "submoduleUpdates" : [ ],
        "mergeMessage" : "Merge successful",
        "mergedAt" : "2026-02-25T06:18:06.629604Z"
      },
      "commitMetadata" : [ ]
    },
    "worktreeContext" : {
      "mainWorktree" : {
        "worktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/25fe6e68-3fe9-44c8-b134-713a300b6ea4/",
        "baseBranch" : "derived-EC7MDW4D",
        "derivedBranch" : "discovery-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
        "createdAt" : "2026-02-25T06:18:04.908782Z",
        "lastCommitHash" : "5a2c815c3b35bf75d63a00cfc3629fdbefbc1802",
        "hasSubmodules" : true,
        "submoduleWorktrees" : [ {
          "worktreeId" : "0c77df25-a5c7-49a7-86a7-daac37c41da1",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/25fe6e68-3fe9-44c8-b134-713a300b6ea4/libs/test-sub/",
          "baseBranch" : "discovery-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
          "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
          "createdAt" : "2026-02-25T06:18:05.315711Z",
          "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
          "metadata" : { }
        } ],
        "metadata" : { }
      },
      "submoduleWorktrees" : [ {
        "worktreeId" : "0c77df25-a5c7-49a7-86a7-daac37c41da1",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/25fe6e68-3fe9-44c8-b134-713a300b6ea4/libs/test-sub/",
        "baseBranch" : "discovery-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
        "createdAt" : "2026-02-25T06:18:05.315711Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
        "metadata" : { }
      } ]
    }
  }, {
    "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q578TSQEKAKMDDGC1B9DW/01KJ9Q578TDFB6JJ2H889H5TSE",
    "output" : "Agent 2 findings",
    "mergeDescriptor" : {
      "mergeDirection" : "TRUNK_TO_CHILD",
      "successful" : true,
      "conflictFiles" : [ ],
      "submoduleMergeResults" : [ ],
      "mainWorktreeMergeResult" : {
        "mergeId" : "670bfddf-6e24-4fc0-80e7-95e04d7b11ce",
        "childWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "parentWorktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
        "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416",
        "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/05a25c3d-f69e-4184-9336-60af94d06013",
        "successful" : true,
        "mergeCommitHash" : "fb3c89146ea189f973bfc1212ff3bedc16017fcc",
        "conflicts" : [ ],
        "submoduleUpdates" : [ ],
        "mergeMessage" : "Merge successful",
        "mergedAt" : "2026-02-25T06:18:07.435206Z"
      },
      "commitMetadata" : [ ]
    },
    "worktreeContext" : {
      "mainWorktree" : {
        "worktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/05a25c3d-f69e-4184-9336-60af94d06013/",
        "baseBranch" : "derived-EC7MDW4D",
        "derivedBranch" : "discovery-2-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
        "createdAt" : "2026-02-25T06:18:05.711014Z",
        "lastCommitHash" : "5a2c815c3b35bf75d63a00cfc3629fdbefbc1802",
        "hasSubmodules" : true,
        "submoduleWorktrees" : [ {
          "worktreeId" : "6542bb55-912c-4462-98be-a943ec0858fa",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/05a25c3d-f69e-4184-9336-60af94d06013/libs/test-sub/",
          "baseBranch" : "discovery-2-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
          "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
          "createdAt" : "2026-02-25T06:18:06.115593Z",
          "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
          "metadata" : { }
        } ],
        "metadata" : { }
      },
      "submoduleWorktrees" : [ {
        "worktreeId" : "6542bb55-912c-4462-98be-a943ec0858fa",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/05a25c3d-f69e-4184-9336-60af94d06013/libs/test-sub/",
        "baseBranch" : "discovery-2-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
        "createdAt" : "2026-02-25T06:18:06.115593Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
        "metadata" : { }
      } ]
    }
  } ],
  "mergeAggregation" : {
    "merged" : [ {
      "agentResultId" : "unknown",
      "worktreeContext" : {
        "mainWorktree" : {
          "worktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/25fe6e68-3fe9-44c8-b134-713a300b6ea4/",
          "baseBranch" : "derived-EC7MDW4D",
          "derivedBranch" : "discovery-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
          "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
          "createdAt" : "2026-02-25T06:18:04.908782Z",
          "lastCommitHash" : "5a2c815c3b35bf75d63a00cfc3629fdbefbc1802",
          "hasSubmodules" : true,
          "submoduleWorktrees" : [ {
            "worktreeId" : "0c77df25-a5c7-49a7-86a7-daac37c41da1",
            "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/25fe6e68-3fe9-44c8-b134-713a300b6ea4/libs/test-sub/",
            "baseBranch" : "discovery-1-ak-01KJ9",
            "status" : "ACTIVE",
            "parentWorktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
            "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
            "createdAt" : "2026-02-25T06:18:05.315711Z",
            "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
            "submoduleName" : "libs/test-sub",
            "mainWorktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
            "metadata" : { }
          } ],
          "metadata" : { }
        },
        "submoduleWorktrees" : [ {
          "worktreeId" : "0c77df25-a5c7-49a7-86a7-daac37c41da1",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/25fe6e68-3fe9-44c8-b134-713a300b6ea4/libs/test-sub/",
          "baseBranch" : "discovery-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
          "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
          "createdAt" : "2026-02-25T06:18:05.315711Z",
          "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
          "metadata" : { }
        } ]
      },
      "mergeDescriptor" : {
        "mergeDirection" : "CHILD_TO_TRUNK",
        "successful" : true,
        "conflictFiles" : [ ],
        "submoduleMergeResults" : [ ],
        "mainWorktreeMergeResult" : {
          "mergeId" : "6867f3b6-ad99-4668-abf5-417e159122fc",
          "childWorktreeId" : "25fe6e68-3fe9-44c8-b134-713a300b6ea4",
          "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
          "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/25fe6e68-3fe9-44c8-b134-713a300b6ea4",
          "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416",
          "successful" : true,
          "mergeCommitHash" : "9f4adee028ccb81c04a26e7d6506fe9d30ec4b54",
          "conflicts" : [ ],
          "submoduleUpdates" : [ ],
          "mergeMessage" : "Merge successful",
          "mergedAt" : "2026-02-25T06:18:07.807624Z"
        },
        "commitMetadata" : [ ]
      },
      "merged" : true
    }, {
      "agentResultId" : "unknown",
      "worktreeContext" : {
        "mainWorktree" : {
          "worktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/05a25c3d-f69e-4184-9336-60af94d06013/",
          "baseBranch" : "derived-EC7MDW4D",
          "derivedBranch" : "discovery-2-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
          "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
          "createdAt" : "2026-02-25T06:18:05.711014Z",
          "lastCommitHash" : "5a2c815c3b35bf75d63a00cfc3629fdbefbc1802",
          "hasSubmodules" : true,
          "submoduleWorktrees" : [ {
            "worktreeId" : "6542bb55-912c-4462-98be-a943ec0858fa",
            "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/05a25c3d-f69e-4184-9336-60af94d06013/libs/test-sub/",
            "baseBranch" : "discovery-2-ak-01KJ9",
            "status" : "ACTIVE",
            "parentWorktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
            "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
            "createdAt" : "2026-02-25T06:18:06.115593Z",
            "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
            "submoduleName" : "libs/test-sub",
            "mainWorktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
            "metadata" : { }
          } ],
          "metadata" : { }
        },
        "submoduleWorktrees" : [ {
          "worktreeId" : "6542bb55-912c-4462-98be-a943ec0858fa",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/05a25c3d-f69e-4184-9336-60af94d06013/libs/test-sub/",
          "baseBranch" : "discovery-2-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
          "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q53SB9PWDEGXXNBTCW1FA",
          "createdAt" : "2026-02-25T06:18:06.115593Z",
          "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
          "metadata" : { }
        } ]
      },
      "mergeDescriptor" : {
        "mergeDirection" : "CHILD_TO_TRUNK",
        "successful" : true,
        "conflictFiles" : [ ],
        "submoduleMergeResults" : [ ],
        "mainWorktreeMergeResult" : {
          "mergeId" : "fa46751f-c3ac-47f3-978b-29b4caac54eb",
          "childWorktreeId" : "05a25c3d-f69e-4184-9336-60af94d06013",
          "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
          "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/05a25c3d-f69e-4184-9336-60af94d06013",
          "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416",
          "successful" : true,
          "mergeCommitHash" : "44c7814baeb009b0b08f65f08108aab9e16b9624",
          "conflicts" : [ ],
          "submoduleUpdates" : [ ],
          "mergeMessage" : "Merge successful",
          "mergedAt" : "2026-02-25T06:18:08.011833Z"
        },
        "commitMetadata" : [ ]
      },
      "merged" : true
    } ],
    "pending" : [ ]
  }
}
```

### Response (`DiscoveryAgentDispatchRouting`)

```json
{
  "collectorRequest" : {
    "goal" : "Discover features",
    "discoveryResults" : "discovery-results"
  }
}
```

---

## Call 6: `workflow/discovery_collector`

**Request type**: `DiscoveryCollectorRequest`  
**Response type**: `DiscoveryCollectorRouting`  

### Decorated Request (`DiscoveryCollectorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q51TG9P5YCH16733V0GKF/01KJ9Q5940HW1Q0K6T3906VKSG",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.561349Z",
      "lastCommitHash" : "44c7814baeb009b0b08f65f08108aab9e16b9624",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
        "baseBranch" : "derived-EC7MDW4D",
        "status" : "ACTIVE",
        "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
        "createdAt" : "2026-02-25T06:17:54.560844Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
      "baseBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.560844Z",
      "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "metadata" : { }
    } ]
  },
  "goal" : "Discover features",
  "discoveryResults" : "discovery-results"
}
```

### Response (`DiscoveryCollectorRouting`)

```json
{
  "collectorResult" : {
    "consolidatedOutput" : "Discovery complete",
    "collectorDecision" : {
      "decisionType" : "ADVANCE_PHASE",
      "rationale" : "Advance to planning",
      "requestedPhase" : "PLANNING"
    }
  }
}
```

---

## Call 7: `workflow/planning_orchestrator`

**Request type**: `PlanningOrchestratorRequest`  
**Response type**: `PlanningOrchestratorRouting`  

### Decorated Request (`PlanningOrchestratorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5D5RYGF2YKD3RC0715WH",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.561349Z",
      "lastCommitHash" : "44c7814baeb009b0b08f65f08108aab9e16b9624",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
        "baseBranch" : "derived-EC7MDW4D",
        "status" : "ACTIVE",
        "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
        "createdAt" : "2026-02-25T06:17:54.560844Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
      "baseBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.560844Z",
      "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "metadata" : { }
    } ]
  },
  "goal" : "Discover features"
}
```

### Response (`PlanningOrchestratorRouting`)

```json
{
  "agentRequests" : {
    "requests" : [ {
      "goal" : "Discover features"
    } ]
  }
}
```

---

## Call 8: `workflow/planning_agent`

**Request type**: `PlanningAgentRequest`  
**Response type**: `PlanningAgentRouting`  

### Decorated Request (`PlanningAgentRequest`)

```json
{
  "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5D5RYGF2YKD3RC0715WH/01KJ9Q5F4P21KYTK2B0Q5E9WDT/01KJ9Q5F4PHGFY8HMWJGNHV8DY",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/71dd9692-c181-48d6-b0b4-f69f8ca99e75/",
      "baseBranch" : "derived-EC7MDW4D",
      "derivedBranch" : "planning-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5D5RYGF2YKD3RC0715WH/01KJ9Q5F4P21KYTK2B0Q5E9WDT",
      "createdAt" : "2026-02-25T06:18:16.657243Z",
      "lastCommitHash" : "85edaf38d40f16c33a291320cf413f169cf86243",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "b16cb7b8-296d-4ee7-b7ad-f9c07185cebd",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/71dd9692-c181-48d6-b0b4-f69f8ca99e75/libs/test-sub/",
        "baseBranch" : "planning-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5D5RYGF2YKD3RC0715WH/01KJ9Q5F4P21KYTK2B0Q5E9WDT",
        "createdAt" : "2026-02-25T06:18:17.145612Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "b16cb7b8-296d-4ee7-b7ad-f9c07185cebd",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/71dd9692-c181-48d6-b0b4-f69f8ca99e75/libs/test-sub/",
      "baseBranch" : "planning-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5D5RYGF2YKD3RC0715WH/01KJ9Q5F4P21KYTK2B0Q5E9WDT",
      "createdAt" : "2026-02-25T06:18:17.145612Z",
      "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
      "metadata" : { }
    } ]
  },
  "goal" : "Discover features"
}
```

### Response (`PlanningAgentRouting`)

```json
{
  "agentResult" : {
    "output" : "Plan output"
  }
}
```

---

## Call 9: `workflow/planning_dispatch`

**Request type**: `PlanningAgentResults`  
**Response type**: `PlanningAgentDispatchRouting`  

### Decorated Request (`PlanningAgentResults`)

```json
{
  "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5D5RYGF2YKD3RC0715WH/01KJ9Q5H339GNA6K02ES215TED",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.561349Z",
      "lastCommitHash" : "44c7814baeb009b0b08f65f08108aab9e16b9624",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
        "baseBranch" : "derived-EC7MDW4D",
        "status" : "ACTIVE",
        "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
        "createdAt" : "2026-02-25T06:17:54.560844Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
      "baseBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.560844Z",
      "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "metadata" : { }
    } ]
  },
  "planningAgentResults" : [ {
    "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5D5RYGF2YKD3RC0715WH/01KJ9Q5H339GNA6K02ES215TED/01KJ9Q5H34R275EHFF9AJ9S36F",
    "output" : "Plan output",
    "mergeDescriptor" : {
      "mergeDirection" : "TRUNK_TO_CHILD",
      "successful" : true,
      "conflictFiles" : [ ],
      "submoduleMergeResults" : [ ],
      "mainWorktreeMergeResult" : {
        "mergeId" : "39924733-fdca-4284-80a6-c3b1c419433f",
        "childWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "parentWorktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
        "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416",
        "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/71dd9692-c181-48d6-b0b4-f69f8ca99e75",
        "successful" : true,
        "mergeCommitHash" : "5a5c06c96a87538af906bc88e4ee53f065e8ea38",
        "conflicts" : [ ],
        "submoduleUpdates" : [ ],
        "mergeMessage" : "Merge successful",
        "mergedAt" : "2026-02-25T06:18:17.682969Z"
      },
      "commitMetadata" : [ ]
    },
    "worktreeContext" : {
      "mainWorktree" : {
        "worktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/71dd9692-c181-48d6-b0b4-f69f8ca99e75/",
        "baseBranch" : "derived-EC7MDW4D",
        "derivedBranch" : "planning-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5D5RYGF2YKD3RC0715WH/01KJ9Q5F4P21KYTK2B0Q5E9WDT",
        "createdAt" : "2026-02-25T06:18:16.657243Z",
        "lastCommitHash" : "85edaf38d40f16c33a291320cf413f169cf86243",
        "hasSubmodules" : true,
        "submoduleWorktrees" : [ {
          "worktreeId" : "b16cb7b8-296d-4ee7-b7ad-f9c07185cebd",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/71dd9692-c181-48d6-b0b4-f69f8ca99e75/libs/test-sub/",
          "baseBranch" : "planning-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
          "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5D5RYGF2YKD3RC0715WH/01KJ9Q5F4P21KYTK2B0Q5E9WDT",
          "createdAt" : "2026-02-25T06:18:17.145612Z",
          "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
          "metadata" : { }
        } ],
        "metadata" : { }
      },
      "submoduleWorktrees" : [ {
        "worktreeId" : "b16cb7b8-296d-4ee7-b7ad-f9c07185cebd",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/71dd9692-c181-48d6-b0b4-f69f8ca99e75/libs/test-sub/",
        "baseBranch" : "planning-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5D5RYGF2YKD3RC0715WH/01KJ9Q5F4P21KYTK2B0Q5E9WDT",
        "createdAt" : "2026-02-25T06:18:17.145612Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
        "metadata" : { }
      } ]
    }
  } ],
  "mergeAggregation" : {
    "merged" : [ {
      "agentResultId" : "unknown",
      "worktreeContext" : {
        "mainWorktree" : {
          "worktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/71dd9692-c181-48d6-b0b4-f69f8ca99e75/",
          "baseBranch" : "derived-EC7MDW4D",
          "derivedBranch" : "planning-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
          "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5D5RYGF2YKD3RC0715WH/01KJ9Q5F4P21KYTK2B0Q5E9WDT",
          "createdAt" : "2026-02-25T06:18:16.657243Z",
          "lastCommitHash" : "85edaf38d40f16c33a291320cf413f169cf86243",
          "hasSubmodules" : true,
          "submoduleWorktrees" : [ {
            "worktreeId" : "b16cb7b8-296d-4ee7-b7ad-f9c07185cebd",
            "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/71dd9692-c181-48d6-b0b4-f69f8ca99e75/libs/test-sub/",
            "baseBranch" : "planning-1-ak-01KJ9",
            "status" : "ACTIVE",
            "parentWorktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
            "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5D5RYGF2YKD3RC0715WH/01KJ9Q5F4P21KYTK2B0Q5E9WDT",
            "createdAt" : "2026-02-25T06:18:17.145612Z",
            "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
            "submoduleName" : "libs/test-sub",
            "mainWorktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
            "metadata" : { }
          } ],
          "metadata" : { }
        },
        "submoduleWorktrees" : [ {
          "worktreeId" : "b16cb7b8-296d-4ee7-b7ad-f9c07185cebd",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/71dd9692-c181-48d6-b0b4-f69f8ca99e75/libs/test-sub/",
          "baseBranch" : "planning-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
          "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5D5RYGF2YKD3RC0715WH/01KJ9Q5F4P21KYTK2B0Q5E9WDT",
          "createdAt" : "2026-02-25T06:18:17.145612Z",
          "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
          "metadata" : { }
        } ]
      },
      "mergeDescriptor" : {
        "mergeDirection" : "CHILD_TO_TRUNK",
        "successful" : true,
        "conflictFiles" : [ ],
        "submoduleMergeResults" : [ ],
        "mainWorktreeMergeResult" : {
          "mergeId" : "b3f02bf8-9dd8-4070-936a-409eb03a0bb5",
          "childWorktreeId" : "71dd9692-c181-48d6-b0b4-f69f8ca99e75",
          "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
          "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/71dd9692-c181-48d6-b0b4-f69f8ca99e75",
          "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416",
          "successful" : true,
          "mergeCommitHash" : "ed9a1c28615fa37dd6b75ea6fec7a8b5bc16061a",
          "conflicts" : [ ],
          "submoduleUpdates" : [ ],
          "mergeMessage" : "Merge successful",
          "mergedAt" : "2026-02-25T06:18:18.059876Z"
        },
        "commitMetadata" : [ ]
      },
      "merged" : true
    } ],
    "pending" : [ ]
  }
}
```

### Response (`PlanningAgentDispatchRouting`)

```json
{
  "planningCollectorRequest" : {
    "goal" : "Discover features",
    "planningResults" : "planning-results"
  }
}
```

---

## Call 10: `workflow/planning_collector`

**Request type**: `PlanningCollectorRequest`  
**Response type**: `PlanningCollectorRouting`  

### Decorated Request (`PlanningCollectorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5D5RYGF2YKD3RC0715WH/01KJ9Q5JYTDWSMGJ05QRRQVA07",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.561349Z",
      "lastCommitHash" : "ed9a1c28615fa37dd6b75ea6fec7a8b5bc16061a",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
        "baseBranch" : "derived-EC7MDW4D",
        "status" : "ACTIVE",
        "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
        "createdAt" : "2026-02-25T06:17:54.560844Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
      "baseBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.560844Z",
      "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "metadata" : { }
    } ]
  },
  "goal" : "Discover features",
  "planningResults" : "planning-results"
}
```

### Response (`PlanningCollectorRouting`)

```json
{
  "collectorResult" : {
    "consolidatedOutput" : "Planning complete",
    "collectorDecision" : {
      "decisionType" : "ADVANCE_PHASE",
      "rationale" : "Advance to tickets",
      "requestedPhase" : "TICKETS"
    }
  }
}
```

---

## Call 11: `workflow/ticket_orchestrator`

**Request type**: `TicketOrchestratorRequest`  
**Response type**: `TicketOrchestratorRouting`  

### Decorated Request (`TicketOrchestratorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5PHJHY0JTJ4SMJE7AFPW",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.561349Z",
      "lastCommitHash" : "ed9a1c28615fa37dd6b75ea6fec7a8b5bc16061a",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
        "baseBranch" : "derived-EC7MDW4D",
        "status" : "ACTIVE",
        "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
        "createdAt" : "2026-02-25T06:17:54.560844Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
      "baseBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.560844Z",
      "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "metadata" : { }
    } ]
  },
  "goal" : "Discover features"
}
```

### Response (`TicketOrchestratorRouting`)

```json
{
  "agentRequests" : {
    "requests" : [ {
      "ticketDetails" : "Discover features",
      "ticketDetailsFilePath" : "ticket-1.md"
    } ]
  }
}
```

---

## Call 12: `workflow/ticket_agent`

**Request type**: `TicketAgentRequest`  
**Response type**: `TicketAgentRouting`  

### Decorated Request (`TicketAgentRequest`)

```json
{
  "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5PHJHY0JTJ4SMJE7AFPW/01KJ9Q5RB5KDKR4K9RHPK2VSS3/01KJ9Q5RB6P7E6JJGHC3X9TGHY",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/b19fe4cf-c723-4355-9b8e-23addff26282/",
      "baseBranch" : "derived-EC7MDW4D",
      "derivedBranch" : "ticket-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5PHJHY0JTJ4SMJE7AFPW/01KJ9Q5RB5KDKR4K9RHPK2VSS3",
      "createdAt" : "2026-02-25T06:18:26.045862Z",
      "lastCommitHash" : "dab1610addb02709d8a78ad6dbdd70c1ca42f3b5",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "53a383c1-3df0-47ba-889f-d73102a8a643",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/b19fe4cf-c723-4355-9b8e-23addff26282/libs/test-sub/",
        "baseBranch" : "ticket-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5PHJHY0JTJ4SMJE7AFPW/01KJ9Q5RB5KDKR4K9RHPK2VSS3",
        "createdAt" : "2026-02-25T06:18:26.596756Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "53a383c1-3df0-47ba-889f-d73102a8a643",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/b19fe4cf-c723-4355-9b8e-23addff26282/libs/test-sub/",
      "baseBranch" : "ticket-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5PHJHY0JTJ4SMJE7AFPW/01KJ9Q5RB5KDKR4K9RHPK2VSS3",
      "createdAt" : "2026-02-25T06:18:26.596756Z",
      "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
      "metadata" : { }
    } ]
  },
  "ticketDetails" : "Discover features",
  "ticketDetailsFilePath" : "ticket-1.md"
}
```

### Response (`TicketAgentRouting`)

```json
{
  "agentResult" : {
    "output" : "Ticket output"
  }
}
```

---

## Call 13: `workflow/ticket_dispatch`

**Request type**: `TicketAgentResults`  
**Response type**: `TicketAgentDispatchRouting`  

### Decorated Request (`TicketAgentResults`)

```json
{
  "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5PHJHY0JTJ4SMJE7AFPW/01KJ9Q5T92E4JC8HRQG1JWX0M9",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.561349Z",
      "lastCommitHash" : "ed9a1c28615fa37dd6b75ea6fec7a8b5bc16061a",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
        "baseBranch" : "derived-EC7MDW4D",
        "status" : "ACTIVE",
        "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
        "createdAt" : "2026-02-25T06:17:54.560844Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
      "baseBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.560844Z",
      "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "metadata" : { }
    } ]
  },
  "ticketAgentResults" : [ {
    "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5PHJHY0JTJ4SMJE7AFPW/01KJ9Q5T92E4JC8HRQG1JWX0M9/01KJ9Q5T92WW67MJTYKW5AKJ0X",
    "output" : "Ticket output",
    "mergeDescriptor" : {
      "mergeDirection" : "TRUNK_TO_CHILD",
      "successful" : true,
      "conflictFiles" : [ ],
      "submoduleMergeResults" : [ ],
      "mainWorktreeMergeResult" : {
        "mergeId" : "0b785950-f517-46d5-a723-77509738c2af",
        "childWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "parentWorktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
        "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416",
        "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/b19fe4cf-c723-4355-9b8e-23addff26282",
        "successful" : true,
        "mergeCommitHash" : "ab573e261a6f4aaafd12320988b28d8ecc75a42d",
        "conflicts" : [ ],
        "submoduleUpdates" : [ ],
        "mergeMessage" : "Merge successful",
        "mergedAt" : "2026-02-25T06:18:27.127412Z"
      },
      "commitMetadata" : [ ]
    },
    "worktreeContext" : {
      "mainWorktree" : {
        "worktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/b19fe4cf-c723-4355-9b8e-23addff26282/",
        "baseBranch" : "derived-EC7MDW4D",
        "derivedBranch" : "ticket-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5PHJHY0JTJ4SMJE7AFPW/01KJ9Q5RB5KDKR4K9RHPK2VSS3",
        "createdAt" : "2026-02-25T06:18:26.045862Z",
        "lastCommitHash" : "dab1610addb02709d8a78ad6dbdd70c1ca42f3b5",
        "hasSubmodules" : true,
        "submoduleWorktrees" : [ {
          "worktreeId" : "53a383c1-3df0-47ba-889f-d73102a8a643",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/b19fe4cf-c723-4355-9b8e-23addff26282/libs/test-sub/",
          "baseBranch" : "ticket-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
          "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5PHJHY0JTJ4SMJE7AFPW/01KJ9Q5RB5KDKR4K9RHPK2VSS3",
          "createdAt" : "2026-02-25T06:18:26.596756Z",
          "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
          "metadata" : { }
        } ],
        "metadata" : { }
      },
      "submoduleWorktrees" : [ {
        "worktreeId" : "53a383c1-3df0-47ba-889f-d73102a8a643",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/b19fe4cf-c723-4355-9b8e-23addff26282/libs/test-sub/",
        "baseBranch" : "ticket-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5PHJHY0JTJ4SMJE7AFPW/01KJ9Q5RB5KDKR4K9RHPK2VSS3",
        "createdAt" : "2026-02-25T06:18:26.596756Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
        "metadata" : { }
      } ]
    }
  } ],
  "mergeAggregation" : {
    "merged" : [ {
      "agentResultId" : "unknown",
      "worktreeContext" : {
        "mainWorktree" : {
          "worktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/b19fe4cf-c723-4355-9b8e-23addff26282/",
          "baseBranch" : "derived-EC7MDW4D",
          "derivedBranch" : "ticket-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
          "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5PHJHY0JTJ4SMJE7AFPW/01KJ9Q5RB5KDKR4K9RHPK2VSS3",
          "createdAt" : "2026-02-25T06:18:26.045862Z",
          "lastCommitHash" : "dab1610addb02709d8a78ad6dbdd70c1ca42f3b5",
          "hasSubmodules" : true,
          "submoduleWorktrees" : [ {
            "worktreeId" : "53a383c1-3df0-47ba-889f-d73102a8a643",
            "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/b19fe4cf-c723-4355-9b8e-23addff26282/libs/test-sub/",
            "baseBranch" : "ticket-1-ak-01KJ9",
            "status" : "ACTIVE",
            "parentWorktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
            "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5PHJHY0JTJ4SMJE7AFPW/01KJ9Q5RB5KDKR4K9RHPK2VSS3",
            "createdAt" : "2026-02-25T06:18:26.596756Z",
            "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
            "submoduleName" : "libs/test-sub",
            "mainWorktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
            "metadata" : { }
          } ],
          "metadata" : { }
        },
        "submoduleWorktrees" : [ {
          "worktreeId" : "53a383c1-3df0-47ba-889f-d73102a8a643",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/b19fe4cf-c723-4355-9b8e-23addff26282/libs/test-sub/",
          "baseBranch" : "ticket-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
          "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5PHJHY0JTJ4SMJE7AFPW/01KJ9Q5RB5KDKR4K9RHPK2VSS3",
          "createdAt" : "2026-02-25T06:18:26.596756Z",
          "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
          "metadata" : { }
        } ]
      },
      "mergeDescriptor" : {
        "mergeDirection" : "CHILD_TO_TRUNK",
        "successful" : true,
        "conflictFiles" : [ ],
        "submoduleMergeResults" : [ ],
        "mainWorktreeMergeResult" : {
          "mergeId" : "e2870cde-e68d-4215-ab8a-d114534ee100",
          "childWorktreeId" : "b19fe4cf-c723-4355-9b8e-23addff26282",
          "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
          "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/b19fe4cf-c723-4355-9b8e-23addff26282",
          "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416",
          "successful" : true,
          "mergeCommitHash" : "dc11ecb29e5343b2a1fb5358f5517035c1803f65",
          "conflicts" : [ ],
          "submoduleUpdates" : [ ],
          "mergeMessage" : "Merge successful",
          "mergedAt" : "2026-02-25T06:18:27.478954Z"
        },
        "commitMetadata" : [ ]
      },
      "merged" : true
    } ],
    "pending" : [ ]
  }
}
```

### Response (`TicketAgentDispatchRouting`)

```json
{
  "ticketCollectorRequest" : {
    "goal" : "Discover features",
    "ticketResults" : "ticket-results"
  }
}
```

---

## Call 14: `workflow/ticket_collector`

**Request type**: `TicketCollectorRequest`  
**Response type**: `TicketCollectorRouting`  

### Decorated Request (`TicketCollectorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5PHJHY0JTJ4SMJE7AFPW/01KJ9Q5W6B1X318K0GB6XFP0RK",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.561349Z",
      "lastCommitHash" : "dc11ecb29e5343b2a1fb5358f5517035c1803f65",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
        "baseBranch" : "derived-EC7MDW4D",
        "status" : "ACTIVE",
        "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
        "createdAt" : "2026-02-25T06:17:54.560844Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
      "baseBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.560844Z",
      "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "metadata" : { }
    } ]
  },
  "goal" : "Discover features",
  "ticketResults" : "ticket-results"
}
```

### Response (`TicketCollectorRouting`)

```json
{
  "collectorResult" : {
    "consolidatedOutput" : "Tickets complete",
    "collectorDecision" : {
      "decisionType" : "ADVANCE_PHASE",
      "rationale" : "Advance to orchestrator collector",
      "requestedPhase" : "COMPLETE"
    }
  }
}
```

---

## Call 15: `workflow/orchestrator_collector`

**Request type**: `OrchestratorCollectorRequest`  
**Response type**: `OrchestratorCollectorRouting`  

### Decorated Request (`OrchestratorCollectorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D/01KJ9Q5ZKY7CF9YKDC7WSHXW48",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.561349Z",
      "lastCommitHash" : "dc11ecb29e5343b2a1fb5358f5517035c1803f65",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
        "baseBranch" : "derived-EC7MDW4D",
        "status" : "ACTIVE",
        "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
        "createdAt" : "2026-02-25T06:17:54.560844Z",
        "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2edc8fe5-adb5-4dff-905a-f04dd2d67b7e",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416/libs/test-sub/",
      "baseBranch" : "derived-EC7MDW4D",
      "status" : "ACTIVE",
      "parentWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "associatedNodeId" : "ak:01KJ9Q4S84MQHZYKZYEC7MDW4D",
      "createdAt" : "2026-02-25T06:17:54.560844Z",
      "lastCommitHash" : "79d4344a3740b5ea1a10141366c989ce0969108a",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "metadata" : { }
    } ]
  },
  "goal" : "Discover features",
  "phase" : "DISCOVERY",
  "mergeDescriptor" : {
    "mergeDirection" : "WORKTREE_TO_SOURCE",
    "successful" : true,
    "conflictFiles" : [ ],
    "submoduleMergeResults" : [ ],
    "mainWorktreeMergeResult" : {
      "mergeId" : "e77bfa09-04f6-4a3b-89b4-4f425b93ffc6",
      "childWorktreeId" : "beacf420-f8e5-482c-acb2-dd5f66f97416",
      "parentWorktreeId" : "source",
      "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/beacf420-f8e5-482c-acb2-dd5f66f97416",
      "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/test-main5673890610397038888",
      "successful" : true,
      "mergeCommitHash" : "e981727279aeaeaf30898c2969df7d2612683182",
      "conflicts" : [ ],
      "submoduleUpdates" : [ ],
      "mergeMessage" : "Final merge to source successful",
      "mergedAt" : "2026-02-25T06:18:33.263229Z"
    },
    "commitMetadata" : [ ]
  }
}
```

### Response (`OrchestratorCollectorRouting`)

```json
{
  "collectorResult" : {
    "consolidatedOutput" : "Workflow complete",
    "collectorDecision" : {
      "decisionType" : "ADVANCE_PHASE",
      "rationale" : "All phases done",
      "requestedPhase" : "COMPLETE"
    }
  }
}
```

---

