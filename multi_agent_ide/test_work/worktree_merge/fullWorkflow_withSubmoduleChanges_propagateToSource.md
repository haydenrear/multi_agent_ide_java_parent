# QueuedLlmRunner Call Log

| Field | Value |
|-------|-------|
| **Test class** | `WorkflowAgentWorktreeMergeIntTest` |
| **Test method** | `fullWorkflow_withSubmoduleChanges_propagateToSource` |
| **Started at** | 2026-02-25T06:19:23.968734Z |

---

## Call 1: `workflow/orchestrator`

**Request type**: `OrchestratorRequest`  
**Response type**: `OrchestratorRouting`  

### Decorated Request (`OrchestratorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.846107Z",
      "lastCommitHash" : "e0be9f500e85d6d54f8e6608364e71fa6ba0d85a",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
        "baseBranch" : "derived-Y7SP5XKF",
        "status" : "ACTIVE",
        "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
        "createdAt" : "2026-02-25T06:19:18.845609Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
      "baseBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.845609Z",
      "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature with lib changes",
  "phase" : "DISCOVERY"
}
```

### Response (`OrchestratorRouting`)

```json
{
  "discoveryOrchestratorRequest" : {
    "goal" : "Implement feature with lib changes"
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
  "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7K4QQA476H9A35HF7A2R",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.846107Z",
      "lastCommitHash" : "e0be9f500e85d6d54f8e6608364e71fa6ba0d85a",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
        "baseBranch" : "derived-Y7SP5XKF",
        "status" : "ACTIVE",
        "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
        "createdAt" : "2026-02-25T06:19:18.845609Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
      "baseBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.845609Z",
      "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature with lib changes"
}
```

### Response (`DiscoveryOrchestratorRouting`)

```json
{
  "agentRequests" : {
    "requests" : [ {
      "goal" : "Implement feature with lib changes",
      "subdomainFocus" : "Primary"
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
  "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7K4QQA476H9A35HF7A2R/01KJ9Q7N3FCJT6JH2NEC5H8D76/01KJ9Q7N3F9HVYTHGT6AR61AEG",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/6522ffae-d878-4e5d-9152-8bf0ee8cae1b/",
      "baseBranch" : "derived-Y7SP5XKF",
      "derivedBranch" : "discovery-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7K4QQA476H9A35HF7A2R/01KJ9Q7N3FCJT6JH2NEC5H8D76",
      "createdAt" : "2026-02-25T06:19:28.200984Z",
      "lastCommitHash" : "e0be9f500e85d6d54f8e6608364e71fa6ba0d85a",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "4e0a633e-80e9-4724-997a-f2a6e3230c90",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/6522ffae-d878-4e5d-9152-8bf0ee8cae1b/libs/test-sub/",
        "baseBranch" : "discovery-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7K4QQA476H9A35HF7A2R/01KJ9Q7N3FCJT6JH2NEC5H8D76",
        "createdAt" : "2026-02-25T06:19:28.610318Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "4e0a633e-80e9-4724-997a-f2a6e3230c90",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/6522ffae-d878-4e5d-9152-8bf0ee8cae1b/libs/test-sub/",
      "baseBranch" : "discovery-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7K4QQA476H9A35HF7A2R/01KJ9Q7N3FCJT6JH2NEC5H8D76",
      "createdAt" : "2026-02-25T06:19:28.610318Z",
      "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature with lib changes",
  "subdomainFocus" : "Primary"
}
```

### Response (`DiscoveryAgentRouting`)

```json
{
  "agentResult" : {
    "output" : "Found stuff"
  }
}
```

---

## Call 4: `workflow/discovery_dispatch`

**Request type**: `DiscoveryAgentResults`  
**Response type**: `DiscoveryAgentDispatchRouting`  

### Decorated Request (`DiscoveryAgentResults`)

```json
{
  "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7K4QQA476H9A35HF7A2R/01KJ9Q7Q1XXBXRCK35NY0RMFF2",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.846107Z",
      "lastCommitHash" : "e0be9f500e85d6d54f8e6608364e71fa6ba0d85a",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
        "baseBranch" : "derived-Y7SP5XKF",
        "status" : "ACTIVE",
        "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
        "createdAt" : "2026-02-25T06:19:18.845609Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
      "baseBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.845609Z",
      "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "metadata" : { }
    } ]
  },
  "result" : [ {
    "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7K4QQA476H9A35HF7A2R/01KJ9Q7Q1XXBXRCK35NY0RMFF2/01KJ9Q7Q1X7WFRPKGYJZQJWMG7",
    "output" : "Found stuff",
    "mergeDescriptor" : {
      "mergeDirection" : "TRUNK_TO_CHILD",
      "successful" : true,
      "conflictFiles" : [ ],
      "submoduleMergeResults" : [ ],
      "mainWorktreeMergeResult" : {
        "mergeId" : "9ba0322f-9650-4d68-8012-3143a9f5fbb0",
        "childWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "parentWorktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
        "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
        "successful" : true,
        "mergeCommitHash" : "3e3e68c2b07cfbc9c9999c144c33f51ca468345a",
        "conflicts" : [ ],
        "submoduleUpdates" : [ ],
        "mergeMessage" : "Merge successful",
        "mergedAt" : "2026-02-25T06:19:29.312024Z"
      },
      "commitMetadata" : [ ]
    },
    "worktreeContext" : {
      "mainWorktree" : {
        "worktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/6522ffae-d878-4e5d-9152-8bf0ee8cae1b/",
        "baseBranch" : "derived-Y7SP5XKF",
        "derivedBranch" : "discovery-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7K4QQA476H9A35HF7A2R/01KJ9Q7N3FCJT6JH2NEC5H8D76",
        "createdAt" : "2026-02-25T06:19:28.200984Z",
        "lastCommitHash" : "e0be9f500e85d6d54f8e6608364e71fa6ba0d85a",
        "hasSubmodules" : true,
        "submoduleWorktrees" : [ {
          "worktreeId" : "4e0a633e-80e9-4724-997a-f2a6e3230c90",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/6522ffae-d878-4e5d-9152-8bf0ee8cae1b/libs/test-sub/",
          "baseBranch" : "discovery-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
          "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7K4QQA476H9A35HF7A2R/01KJ9Q7N3FCJT6JH2NEC5H8D76",
          "createdAt" : "2026-02-25T06:19:28.610318Z",
          "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
          "metadata" : { }
        } ],
        "metadata" : { }
      },
      "submoduleWorktrees" : [ {
        "worktreeId" : "4e0a633e-80e9-4724-997a-f2a6e3230c90",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/6522ffae-d878-4e5d-9152-8bf0ee8cae1b/libs/test-sub/",
        "baseBranch" : "discovery-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7K4QQA476H9A35HF7A2R/01KJ9Q7N3FCJT6JH2NEC5H8D76",
        "createdAt" : "2026-02-25T06:19:28.610318Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
        "metadata" : { }
      } ]
    }
  } ],
  "mergeAggregation" : {
    "merged" : [ {
      "agentResultId" : "unknown",
      "worktreeContext" : {
        "mainWorktree" : {
          "worktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/6522ffae-d878-4e5d-9152-8bf0ee8cae1b/",
          "baseBranch" : "derived-Y7SP5XKF",
          "derivedBranch" : "discovery-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
          "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7K4QQA476H9A35HF7A2R/01KJ9Q7N3FCJT6JH2NEC5H8D76",
          "createdAt" : "2026-02-25T06:19:28.200984Z",
          "lastCommitHash" : "e0be9f500e85d6d54f8e6608364e71fa6ba0d85a",
          "hasSubmodules" : true,
          "submoduleWorktrees" : [ {
            "worktreeId" : "4e0a633e-80e9-4724-997a-f2a6e3230c90",
            "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/6522ffae-d878-4e5d-9152-8bf0ee8cae1b/libs/test-sub/",
            "baseBranch" : "discovery-1-ak-01KJ9",
            "status" : "ACTIVE",
            "parentWorktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
            "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7K4QQA476H9A35HF7A2R/01KJ9Q7N3FCJT6JH2NEC5H8D76",
            "createdAt" : "2026-02-25T06:19:28.610318Z",
            "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
            "submoduleName" : "libs/test-sub",
            "mainWorktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
            "metadata" : { }
          } ],
          "metadata" : { }
        },
        "submoduleWorktrees" : [ {
          "worktreeId" : "4e0a633e-80e9-4724-997a-f2a6e3230c90",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/6522ffae-d878-4e5d-9152-8bf0ee8cae1b/libs/test-sub/",
          "baseBranch" : "discovery-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
          "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7K4QQA476H9A35HF7A2R/01KJ9Q7N3FCJT6JH2NEC5H8D76",
          "createdAt" : "2026-02-25T06:19:28.610318Z",
          "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
          "metadata" : { }
        } ]
      },
      "mergeDescriptor" : {
        "mergeDirection" : "CHILD_TO_TRUNK",
        "successful" : true,
        "conflictFiles" : [ ],
        "submoduleMergeResults" : [ ],
        "mainWorktreeMergeResult" : {
          "mergeId" : "5b492f95-c2dd-4962-95d2-d56fad55cd5b",
          "childWorktreeId" : "6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
          "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
          "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/6522ffae-d878-4e5d-9152-8bf0ee8cae1b",
          "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b",
          "successful" : true,
          "mergeCommitHash" : "92428eb94f68258b5c2f2470601ff302bc4a1508",
          "conflicts" : [ ],
          "submoduleUpdates" : [ ],
          "mergeMessage" : "Merge successful",
          "mergedAt" : "2026-02-25T06:19:29.706114Z"
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
    "goal" : "Implement feature with lib changes",
    "discoveryResults" : "discovery-results"
  }
}
```

---

## Call 5: `workflow/discovery_collector`

**Request type**: `DiscoveryCollectorRequest`  
**Response type**: `DiscoveryCollectorRouting`  

### Decorated Request (`DiscoveryCollectorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7K4QQA476H9A35HF7A2R/01KJ9Q7RT6R7YRAKEJWWB5XG5M",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.846107Z",
      "lastCommitHash" : "92428eb94f68258b5c2f2470601ff302bc4a1508",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
        "baseBranch" : "derived-Y7SP5XKF",
        "status" : "ACTIVE",
        "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
        "createdAt" : "2026-02-25T06:19:18.845609Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
      "baseBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.845609Z",
      "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature with lib changes",
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

## Call 6: `workflow/planning_orchestrator`

**Request type**: `PlanningOrchestratorRequest`  
**Response type**: `PlanningOrchestratorRouting`  

### Decorated Request (`PlanningOrchestratorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7W755MRMWG1FEAYSQA79",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.846107Z",
      "lastCommitHash" : "92428eb94f68258b5c2f2470601ff302bc4a1508",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
        "baseBranch" : "derived-Y7SP5XKF",
        "status" : "ACTIVE",
        "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
        "createdAt" : "2026-02-25T06:19:18.845609Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
      "baseBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.845609Z",
      "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature with lib changes"
}
```

### Response (`PlanningOrchestratorRouting`)

```json
{
  "agentRequests" : {
    "requests" : [ {
      "goal" : "Implement feature with lib changes"
    } ]
  }
}
```

---

## Call 7: `workflow/planning_agent`

**Request type**: `PlanningAgentRequest`  
**Response type**: `PlanningAgentRouting`  

### Decorated Request (`PlanningAgentRequest`)

```json
{
  "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7W755MRMWG1FEAYSQA79/01KJ9Q7XZ8WN5WJGNEKPPMFH0X/01KJ9Q7XZ89HN6MG8R6K4PS2C1",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/ff742e77-c718-4d06-8b89-5b6ee757d7ec/",
      "baseBranch" : "derived-Y7SP5XKF",
      "derivedBranch" : "planning-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7W755MRMWG1FEAYSQA79/01KJ9Q7XZ8WN5WJGNEKPPMFH0X",
      "createdAt" : "2026-02-25T06:19:37.300326Z",
      "lastCommitHash" : "4f769ba8e3f73307b09d46517bfc177a6cdf52a8",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "29e115ac-77f1-477b-a352-d15147cc7c06",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/ff742e77-c718-4d06-8b89-5b6ee757d7ec/libs/test-sub/",
        "baseBranch" : "planning-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7W755MRMWG1FEAYSQA79/01KJ9Q7XZ8WN5WJGNEKPPMFH0X",
        "createdAt" : "2026-02-25T06:19:37.791921Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "29e115ac-77f1-477b-a352-d15147cc7c06",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/ff742e77-c718-4d06-8b89-5b6ee757d7ec/libs/test-sub/",
      "baseBranch" : "planning-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7W755MRMWG1FEAYSQA79/01KJ9Q7XZ8WN5WJGNEKPPMFH0X",
      "createdAt" : "2026-02-25T06:19:37.791921Z",
      "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature with lib changes"
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

## Call 8: `workflow/planning_dispatch`

**Request type**: `PlanningAgentResults`  
**Response type**: `PlanningAgentDispatchRouting`  

### Decorated Request (`PlanningAgentResults`)

```json
{
  "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7W755MRMWG1FEAYSQA79/01KJ9Q7ZVZQ891JKQXVNM2T8QS",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.846107Z",
      "lastCommitHash" : "92428eb94f68258b5c2f2470601ff302bc4a1508",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
        "baseBranch" : "derived-Y7SP5XKF",
        "status" : "ACTIVE",
        "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
        "createdAt" : "2026-02-25T06:19:18.845609Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
      "baseBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.845609Z",
      "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "metadata" : { }
    } ]
  },
  "planningAgentResults" : [ {
    "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7W755MRMWG1FEAYSQA79/01KJ9Q7ZVZQ891JKQXVNM2T8QS/01KJ9Q7ZVZPNF8MJD6N5A74XEF",
    "output" : "Plan output",
    "mergeDescriptor" : {
      "mergeDirection" : "TRUNK_TO_CHILD",
      "successful" : true,
      "conflictFiles" : [ ],
      "submoduleMergeResults" : [ ],
      "mainWorktreeMergeResult" : {
        "mergeId" : "0e724baf-1b33-4dab-9036-cfa153116cb8",
        "childWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "parentWorktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
        "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/ff742e77-c718-4d06-8b89-5b6ee757d7ec",
        "successful" : true,
        "mergeCommitHash" : "fad3a04e3dd8166610f9947157f09f2e1d44925e",
        "conflicts" : [ ],
        "submoduleUpdates" : [ ],
        "mergeMessage" : "Merge successful",
        "mergedAt" : "2026-02-25T06:19:38.348674Z"
      },
      "commitMetadata" : [ ]
    },
    "worktreeContext" : {
      "mainWorktree" : {
        "worktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/ff742e77-c718-4d06-8b89-5b6ee757d7ec/",
        "baseBranch" : "derived-Y7SP5XKF",
        "derivedBranch" : "planning-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7W755MRMWG1FEAYSQA79/01KJ9Q7XZ8WN5WJGNEKPPMFH0X",
        "createdAt" : "2026-02-25T06:19:37.300326Z",
        "lastCommitHash" : "4f769ba8e3f73307b09d46517bfc177a6cdf52a8",
        "hasSubmodules" : true,
        "submoduleWorktrees" : [ {
          "worktreeId" : "29e115ac-77f1-477b-a352-d15147cc7c06",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/ff742e77-c718-4d06-8b89-5b6ee757d7ec/libs/test-sub/",
          "baseBranch" : "planning-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
          "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7W755MRMWG1FEAYSQA79/01KJ9Q7XZ8WN5WJGNEKPPMFH0X",
          "createdAt" : "2026-02-25T06:19:37.791921Z",
          "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
          "metadata" : { }
        } ],
        "metadata" : { }
      },
      "submoduleWorktrees" : [ {
        "worktreeId" : "29e115ac-77f1-477b-a352-d15147cc7c06",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/ff742e77-c718-4d06-8b89-5b6ee757d7ec/libs/test-sub/",
        "baseBranch" : "planning-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7W755MRMWG1FEAYSQA79/01KJ9Q7XZ8WN5WJGNEKPPMFH0X",
        "createdAt" : "2026-02-25T06:19:37.791921Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
        "metadata" : { }
      } ]
    }
  } ],
  "mergeAggregation" : {
    "merged" : [ {
      "agentResultId" : "unknown",
      "worktreeContext" : {
        "mainWorktree" : {
          "worktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/ff742e77-c718-4d06-8b89-5b6ee757d7ec/",
          "baseBranch" : "derived-Y7SP5XKF",
          "derivedBranch" : "planning-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
          "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7W755MRMWG1FEAYSQA79/01KJ9Q7XZ8WN5WJGNEKPPMFH0X",
          "createdAt" : "2026-02-25T06:19:37.300326Z",
          "lastCommitHash" : "4f769ba8e3f73307b09d46517bfc177a6cdf52a8",
          "hasSubmodules" : true,
          "submoduleWorktrees" : [ {
            "worktreeId" : "29e115ac-77f1-477b-a352-d15147cc7c06",
            "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/ff742e77-c718-4d06-8b89-5b6ee757d7ec/libs/test-sub/",
            "baseBranch" : "planning-1-ak-01KJ9",
            "status" : "ACTIVE",
            "parentWorktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
            "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7W755MRMWG1FEAYSQA79/01KJ9Q7XZ8WN5WJGNEKPPMFH0X",
            "createdAt" : "2026-02-25T06:19:37.791921Z",
            "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
            "submoduleName" : "libs/test-sub",
            "mainWorktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
            "metadata" : { }
          } ],
          "metadata" : { }
        },
        "submoduleWorktrees" : [ {
          "worktreeId" : "29e115ac-77f1-477b-a352-d15147cc7c06",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/ff742e77-c718-4d06-8b89-5b6ee757d7ec/libs/test-sub/",
          "baseBranch" : "planning-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
          "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7W755MRMWG1FEAYSQA79/01KJ9Q7XZ8WN5WJGNEKPPMFH0X",
          "createdAt" : "2026-02-25T06:19:37.791921Z",
          "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
          "metadata" : { }
        } ]
      },
      "mergeDescriptor" : {
        "mergeDirection" : "CHILD_TO_TRUNK",
        "successful" : true,
        "conflictFiles" : [ ],
        "submoduleMergeResults" : [ ],
        "mainWorktreeMergeResult" : {
          "mergeId" : "9cf14476-a655-4911-a34e-cf6b48d8d6d4",
          "childWorktreeId" : "ff742e77-c718-4d06-8b89-5b6ee757d7ec",
          "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
          "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/ff742e77-c718-4d06-8b89-5b6ee757d7ec",
          "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b",
          "successful" : true,
          "mergeCommitHash" : "7bfb6e0f964573860cec4ef158efb0f583fd2c78",
          "conflicts" : [ ],
          "submoduleUpdates" : [ ],
          "mergeMessage" : "Merge successful",
          "mergedAt" : "2026-02-25T06:19:38.738859Z"
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
    "goal" : "Implement feature with lib changes",
    "planningResults" : "planning-results"
  }
}
```

---

## Call 9: `workflow/planning_collector`

**Request type**: `PlanningCollectorRequest`  
**Response type**: `PlanningCollectorRouting`  

### Decorated Request (`PlanningCollectorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q7W755MRMWG1FEAYSQA79/01KJ9Q81MPEYBM4HE5F5W9NNYG",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.846107Z",
      "lastCommitHash" : "7bfb6e0f964573860cec4ef158efb0f583fd2c78",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
        "baseBranch" : "derived-Y7SP5XKF",
        "status" : "ACTIVE",
        "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
        "createdAt" : "2026-02-25T06:19:18.845609Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
      "baseBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.845609Z",
      "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature with lib changes",
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

## Call 10: `workflow/ticket_orchestrator`

**Request type**: `TicketOrchestratorRequest`  
**Response type**: `TicketOrchestratorRouting`  

### Decorated Request (`TicketOrchestratorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q8580VNF7YKRZEXZCQ6KA",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.846107Z",
      "lastCommitHash" : "7bfb6e0f964573860cec4ef158efb0f583fd2c78",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
        "baseBranch" : "derived-Y7SP5XKF",
        "status" : "ACTIVE",
        "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
        "createdAt" : "2026-02-25T06:19:18.845609Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
      "baseBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.845609Z",
      "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature with lib changes"
}
```

### Response (`TicketOrchestratorRouting`)

```json
{
  "agentRequests" : {
    "requests" : [ {
      "ticketDetails" : "Implement feature with lib changes",
      "ticketDetailsFilePath" : "ticket-1.md"
    } ]
  }
}
```

---

## Call 11: `workflow/ticket_agent`

**Request type**: `TicketAgentRequest`  
**Response type**: `TicketAgentRouting`  

### Decorated Request (`TicketAgentRequest`)

```json
{
  "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q8580VNF7YKRZEXZCQ6KA/01KJ9Q86ZCBT948GTFCBKN0VH0/01KJ9Q86ZDHZ2H4G5QMKS8AK9T",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/4f4b31a5-e69a-44bf-b7f6-518760533aef/",
      "baseBranch" : "derived-Y7SP5XKF",
      "derivedBranch" : "ticket-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q8580VNF7YKRZEXZCQ6KA/01KJ9Q86ZCBT948GTFCBKN0VH0",
      "createdAt" : "2026-02-25T06:19:46.521290Z",
      "lastCommitHash" : "823eccbc58a6a66c98f661e5215ea341f2f66b86",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "a6235e24-4209-4d92-b3bf-50ebd9ed3633",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/4f4b31a5-e69a-44bf-b7f6-518760533aef/libs/test-sub/",
        "baseBranch" : "ticket-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q8580VNF7YKRZEXZCQ6KA/01KJ9Q86ZCBT948GTFCBKN0VH0",
        "createdAt" : "2026-02-25T06:19:46.985078Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "a6235e24-4209-4d92-b3bf-50ebd9ed3633",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/4f4b31a5-e69a-44bf-b7f6-518760533aef/libs/test-sub/",
      "baseBranch" : "ticket-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q8580VNF7YKRZEXZCQ6KA/01KJ9Q86ZCBT948GTFCBKN0VH0",
      "createdAt" : "2026-02-25T06:19:46.985078Z",
      "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
      "metadata" : { }
    } ]
  },
  "ticketDetails" : "Implement feature with lib changes",
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

## Call 12: `workflow/ticket_dispatch`

**Request type**: `TicketAgentResults`  
**Response type**: `TicketAgentDispatchRouting`  

### Decorated Request (`TicketAgentResults`)

```json
{
  "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q8580VNF7YKRZEXZCQ6KA/01KJ9Q88VDW8DK0J8PGYRBQNE9",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.846107Z",
      "lastCommitHash" : "7bfb6e0f964573860cec4ef158efb0f583fd2c78",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
        "baseBranch" : "derived-Y7SP5XKF",
        "status" : "ACTIVE",
        "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
        "createdAt" : "2026-02-25T06:19:18.845609Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
      "baseBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.845609Z",
      "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "metadata" : { }
    } ]
  },
  "ticketAgentResults" : [ {
    "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q8580VNF7YKRZEXZCQ6KA/01KJ9Q88VDW8DK0J8PGYRBQNE9/01KJ9Q88VD4DC3YJSGJ3R29TV8",
    "output" : "Ticket output",
    "mergeDescriptor" : {
      "mergeDirection" : "TRUNK_TO_CHILD",
      "successful" : true,
      "conflictFiles" : [ ],
      "submoduleMergeResults" : [ ],
      "mainWorktreeMergeResult" : {
        "mergeId" : "de660a4e-937f-4d7d-90d5-0f6f74664100",
        "childWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "parentWorktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
        "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/4f4b31a5-e69a-44bf-b7f6-518760533aef",
        "successful" : true,
        "mergeCommitHash" : "fade8d5a1f38054f4a7422b0c96db19f16a1cb78",
        "conflicts" : [ ],
        "submoduleUpdates" : [ ],
        "mergeMessage" : "Merge successful",
        "mergedAt" : "2026-02-25T06:19:47.530079Z"
      },
      "commitMetadata" : [ ]
    },
    "worktreeContext" : {
      "mainWorktree" : {
        "worktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/4f4b31a5-e69a-44bf-b7f6-518760533aef/",
        "baseBranch" : "derived-Y7SP5XKF",
        "derivedBranch" : "ticket-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q8580VNF7YKRZEXZCQ6KA/01KJ9Q86ZCBT948GTFCBKN0VH0",
        "createdAt" : "2026-02-25T06:19:46.521290Z",
        "lastCommitHash" : "823eccbc58a6a66c98f661e5215ea341f2f66b86",
        "hasSubmodules" : true,
        "submoduleWorktrees" : [ {
          "worktreeId" : "a6235e24-4209-4d92-b3bf-50ebd9ed3633",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/4f4b31a5-e69a-44bf-b7f6-518760533aef/libs/test-sub/",
          "baseBranch" : "ticket-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
          "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q8580VNF7YKRZEXZCQ6KA/01KJ9Q86ZCBT948GTFCBKN0VH0",
          "createdAt" : "2026-02-25T06:19:46.985078Z",
          "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
          "metadata" : { }
        } ],
        "metadata" : { }
      },
      "submoduleWorktrees" : [ {
        "worktreeId" : "a6235e24-4209-4d92-b3bf-50ebd9ed3633",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/4f4b31a5-e69a-44bf-b7f6-518760533aef/libs/test-sub/",
        "baseBranch" : "ticket-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q8580VNF7YKRZEXZCQ6KA/01KJ9Q86ZCBT948GTFCBKN0VH0",
        "createdAt" : "2026-02-25T06:19:46.985078Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
        "metadata" : { }
      } ]
    }
  } ],
  "mergeAggregation" : {
    "merged" : [ {
      "agentResultId" : "unknown",
      "worktreeContext" : {
        "mainWorktree" : {
          "worktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/4f4b31a5-e69a-44bf-b7f6-518760533aef/",
          "baseBranch" : "derived-Y7SP5XKF",
          "derivedBranch" : "ticket-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
          "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q8580VNF7YKRZEXZCQ6KA/01KJ9Q86ZCBT948GTFCBKN0VH0",
          "createdAt" : "2026-02-25T06:19:46.521290Z",
          "lastCommitHash" : "823eccbc58a6a66c98f661e5215ea341f2f66b86",
          "hasSubmodules" : true,
          "submoduleWorktrees" : [ {
            "worktreeId" : "a6235e24-4209-4d92-b3bf-50ebd9ed3633",
            "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/4f4b31a5-e69a-44bf-b7f6-518760533aef/libs/test-sub/",
            "baseBranch" : "ticket-1-ak-01KJ9",
            "status" : "ACTIVE",
            "parentWorktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
            "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q8580VNF7YKRZEXZCQ6KA/01KJ9Q86ZCBT948GTFCBKN0VH0",
            "createdAt" : "2026-02-25T06:19:46.985078Z",
            "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
            "submoduleName" : "libs/test-sub",
            "mainWorktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
            "metadata" : { }
          } ],
          "metadata" : { }
        },
        "submoduleWorktrees" : [ {
          "worktreeId" : "a6235e24-4209-4d92-b3bf-50ebd9ed3633",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/4f4b31a5-e69a-44bf-b7f6-518760533aef/libs/test-sub/",
          "baseBranch" : "ticket-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
          "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q8580VNF7YKRZEXZCQ6KA/01KJ9Q86ZCBT948GTFCBKN0VH0",
          "createdAt" : "2026-02-25T06:19:46.985078Z",
          "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
          "metadata" : { }
        } ]
      },
      "mergeDescriptor" : {
        "mergeDirection" : "CHILD_TO_TRUNK",
        "successful" : true,
        "conflictFiles" : [ ],
        "submoduleMergeResults" : [ ],
        "mainWorktreeMergeResult" : {
          "mergeId" : "66c08683-3009-4bb0-ad6a-f144496dda8f",
          "childWorktreeId" : "4f4b31a5-e69a-44bf-b7f6-518760533aef",
          "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
          "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/4f4b31a5-e69a-44bf-b7f6-518760533aef",
          "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b",
          "successful" : true,
          "mergeCommitHash" : "c48670b07b765e226e78a35bde46c2a002e5a6d5",
          "conflicts" : [ ],
          "submoduleUpdates" : [ ],
          "mergeMessage" : "Merge successful",
          "mergedAt" : "2026-02-25T06:19:47.923418Z"
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
    "goal" : "Implement feature with lib changes",
    "ticketResults" : "ticket-results"
  }
}
```

---

## Call 13: `workflow/ticket_collector`

**Request type**: `TicketCollectorRequest`  
**Response type**: `TicketCollectorRouting`  

### Decorated Request (`TicketCollectorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q8580VNF7YKRZEXZCQ6KA/01KJ9Q8AK3J32ZMGZK23R8YJHJ",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.846107Z",
      "lastCommitHash" : "c48670b07b765e226e78a35bde46c2a002e5a6d5",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
        "baseBranch" : "derived-Y7SP5XKF",
        "status" : "ACTIVE",
        "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
        "createdAt" : "2026-02-25T06:19:18.845609Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
      "baseBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.845609Z",
      "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature with lib changes",
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

## Call 14: `workflow/orchestrator_collector`

**Request type**: `OrchestratorCollectorRequest`  
**Response type**: `OrchestratorCollectorRouting`  

### Decorated Request (`OrchestratorCollectorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF/01KJ9Q8E07D3WNJJ5TAVA33HTE",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.846107Z",
      "lastCommitHash" : "c48670b07b765e226e78a35bde46c2a002e5a6d5",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
        "baseBranch" : "derived-Y7SP5XKF",
        "status" : "ACTIVE",
        "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
        "createdAt" : "2026-02-25T06:19:18.845609Z",
        "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2d3a0485-bb9d-493e-95b5-3fb7df8d98bb",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b/libs/test-sub/",
      "baseBranch" : "derived-Y7SP5XKF",
      "status" : "ACTIVE",
      "parentWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "associatedNodeId" : "ak:01KJ9Q7BJGPG52WJ5ZY7SP5XKF",
      "createdAt" : "2026-02-25T06:19:18.845609Z",
      "lastCommitHash" : "d1571c6bac42cb2b3cd23363b41b21735cb9253f",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature with lib changes",
  "phase" : "DISCOVERY",
  "mergeDescriptor" : {
    "mergeDirection" : "WORKTREE_TO_SOURCE",
    "successful" : true,
    "conflictFiles" : [ ],
    "submoduleMergeResults" : [ ],
    "mainWorktreeMergeResult" : {
      "mergeId" : "f68fe165-9a3e-48a5-9b87-56a1b1d2e138",
      "childWorktreeId" : "21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "parentWorktreeId" : "source",
      "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/21390ac4-8a5c-4407-be1e-fe4a57d6287b",
      "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/test-main17121393469116214479",
      "successful" : true,
      "mergeCommitHash" : "89cbbf3cb6ef476839a803596d86df1c38e53637",
      "conflicts" : [ ],
      "submoduleUpdates" : [ ],
      "mergeMessage" : "Final merge to source successful",
      "mergedAt" : "2026-02-25T06:19:53.581112Z"
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

