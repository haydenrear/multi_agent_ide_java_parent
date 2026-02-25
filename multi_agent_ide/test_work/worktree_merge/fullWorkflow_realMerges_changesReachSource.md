# QueuedLlmRunner Call Log

| Field | Value |
|-------|-------|
| **Test class** | `WorkflowAgentWorktreeMergeIntTest` |
| **Test method** | `fullWorkflow_realMerges_changesReachSource` |
| **Started at** | 2026-02-25T06:18:43.639795Z |

---

## Call 1: `workflow/orchestrator`

**Request type**: `OrchestratorRequest`  
**Response type**: `OrchestratorRouting`  

### Decorated Request (`OrchestratorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.242207Z",
      "lastCommitHash" : "0e015a5e22510f37d5a38868ec2ccba91f31df00",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
        "baseBranch" : "derived-AH4WQQ5C",
        "status" : "ACTIVE",
        "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
        "createdAt" : "2026-02-25T06:18:38.241744Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
      "baseBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.241744Z",
      "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature",
  "phase" : "DISCOVERY"
}
```

### Response (`OrchestratorRouting`)

```json
{
  "discoveryOrchestratorRequest" : {
    "goal" : "Implement feature"
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
  "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6BT8K2MYAJ0TX42KFG98",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.242207Z",
      "lastCommitHash" : "0e015a5e22510f37d5a38868ec2ccba91f31df00",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
        "baseBranch" : "derived-AH4WQQ5C",
        "status" : "ACTIVE",
        "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
        "createdAt" : "2026-02-25T06:18:38.241744Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
      "baseBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.241744Z",
      "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature"
}
```

### Response (`DiscoveryOrchestratorRouting`)

```json
{
  "agentRequests" : {
    "requests" : [ {
      "goal" : "Implement feature",
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
  "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6BT8K2MYAJ0TX42KFG98/01KJ9Q6DMEVQM62HDPCNE8YWH7/01KJ9Q6DMERBSPCKEGSCHKB4S4",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/288330ae-86ec-4e45-ad88-9d81c4a0f014/",
      "baseBranch" : "derived-AH4WQQ5C",
      "derivedBranch" : "discovery-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6BT8K2MYAJ0TX42KFG98/01KJ9Q6DMEVQM62HDPCNE8YWH7",
      "createdAt" : "2026-02-25T06:18:47.757525Z",
      "lastCommitHash" : "0e015a5e22510f37d5a38868ec2ccba91f31df00",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "0399d504-52fc-416a-b2b6-5e3b6e464b10",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/288330ae-86ec-4e45-ad88-9d81c4a0f014/libs/test-sub/",
        "baseBranch" : "discovery-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6BT8K2MYAJ0TX42KFG98/01KJ9Q6DMEVQM62HDPCNE8YWH7",
        "createdAt" : "2026-02-25T06:18:48.177710Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "0399d504-52fc-416a-b2b6-5e3b6e464b10",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/288330ae-86ec-4e45-ad88-9d81c4a0f014/libs/test-sub/",
      "baseBranch" : "discovery-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6BT8K2MYAJ0TX42KFG98/01KJ9Q6DMEVQM62HDPCNE8YWH7",
      "createdAt" : "2026-02-25T06:18:48.177710Z",
      "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature",
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
  "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6BT8K2MYAJ0TX42KFG98/01KJ9Q6FBNN53RGHAZYYG0S0AS",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.242207Z",
      "lastCommitHash" : "0e015a5e22510f37d5a38868ec2ccba91f31df00",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
        "baseBranch" : "derived-AH4WQQ5C",
        "status" : "ACTIVE",
        "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
        "createdAt" : "2026-02-25T06:18:38.241744Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
      "baseBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.241744Z",
      "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "metadata" : { }
    } ]
  },
  "result" : [ {
    "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6BT8K2MYAJ0TX42KFG98/01KJ9Q6FBNN53RGHAZYYG0S0AS/01KJ9Q6FBNZ9S3YHZB30W9M0V8",
    "output" : "Found stuff",
    "mergeDescriptor" : {
      "mergeDirection" : "TRUNK_TO_CHILD",
      "successful" : true,
      "conflictFiles" : [ ],
      "submoduleMergeResults" : [ ],
      "mainWorktreeMergeResult" : {
        "mergeId" : "074e3a1d-4176-487b-aa68-f36c39315e88",
        "childWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "parentWorktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
        "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75",
        "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/288330ae-86ec-4e45-ad88-9d81c4a0f014",
        "successful" : true,
        "mergeCommitHash" : "59c53f53f365aad68ab00681d688b87010a15832",
        "conflicts" : [ ],
        "submoduleUpdates" : [ ],
        "mergeMessage" : "Merge successful",
        "mergedAt" : "2026-02-25T06:18:48.686013Z"
      },
      "commitMetadata" : [ ]
    },
    "worktreeContext" : {
      "mainWorktree" : {
        "worktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/288330ae-86ec-4e45-ad88-9d81c4a0f014/",
        "baseBranch" : "derived-AH4WQQ5C",
        "derivedBranch" : "discovery-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6BT8K2MYAJ0TX42KFG98/01KJ9Q6DMEVQM62HDPCNE8YWH7",
        "createdAt" : "2026-02-25T06:18:47.757525Z",
        "lastCommitHash" : "0e015a5e22510f37d5a38868ec2ccba91f31df00",
        "hasSubmodules" : true,
        "submoduleWorktrees" : [ {
          "worktreeId" : "0399d504-52fc-416a-b2b6-5e3b6e464b10",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/288330ae-86ec-4e45-ad88-9d81c4a0f014/libs/test-sub/",
          "baseBranch" : "discovery-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
          "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6BT8K2MYAJ0TX42KFG98/01KJ9Q6DMEVQM62HDPCNE8YWH7",
          "createdAt" : "2026-02-25T06:18:48.177710Z",
          "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
          "metadata" : { }
        } ],
        "metadata" : { }
      },
      "submoduleWorktrees" : [ {
        "worktreeId" : "0399d504-52fc-416a-b2b6-5e3b6e464b10",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/288330ae-86ec-4e45-ad88-9d81c4a0f014/libs/test-sub/",
        "baseBranch" : "discovery-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6BT8K2MYAJ0TX42KFG98/01KJ9Q6DMEVQM62HDPCNE8YWH7",
        "createdAt" : "2026-02-25T06:18:48.177710Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
        "metadata" : { }
      } ]
    }
  } ],
  "mergeAggregation" : {
    "merged" : [ {
      "agentResultId" : "unknown",
      "worktreeContext" : {
        "mainWorktree" : {
          "worktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/288330ae-86ec-4e45-ad88-9d81c4a0f014/",
          "baseBranch" : "derived-AH4WQQ5C",
          "derivedBranch" : "discovery-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
          "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6BT8K2MYAJ0TX42KFG98/01KJ9Q6DMEVQM62HDPCNE8YWH7",
          "createdAt" : "2026-02-25T06:18:47.757525Z",
          "lastCommitHash" : "0e015a5e22510f37d5a38868ec2ccba91f31df00",
          "hasSubmodules" : true,
          "submoduleWorktrees" : [ {
            "worktreeId" : "0399d504-52fc-416a-b2b6-5e3b6e464b10",
            "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/288330ae-86ec-4e45-ad88-9d81c4a0f014/libs/test-sub/",
            "baseBranch" : "discovery-1-ak-01KJ9",
            "status" : "ACTIVE",
            "parentWorktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
            "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6BT8K2MYAJ0TX42KFG98/01KJ9Q6DMEVQM62HDPCNE8YWH7",
            "createdAt" : "2026-02-25T06:18:48.177710Z",
            "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
            "submoduleName" : "libs/test-sub",
            "mainWorktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
            "metadata" : { }
          } ],
          "metadata" : { }
        },
        "submoduleWorktrees" : [ {
          "worktreeId" : "0399d504-52fc-416a-b2b6-5e3b6e464b10",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/288330ae-86ec-4e45-ad88-9d81c4a0f014/libs/test-sub/",
          "baseBranch" : "discovery-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
          "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6BT8K2MYAJ0TX42KFG98/01KJ9Q6DMEVQM62HDPCNE8YWH7",
          "createdAt" : "2026-02-25T06:18:48.177710Z",
          "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
          "metadata" : { }
        } ]
      },
      "mergeDescriptor" : {
        "mergeDirection" : "CHILD_TO_TRUNK",
        "successful" : true,
        "conflictFiles" : [ ],
        "submoduleMergeResults" : [ ],
        "mainWorktreeMergeResult" : {
          "mergeId" : "38407dab-6c78-4b7c-8a55-501a215e6c51",
          "childWorktreeId" : "288330ae-86ec-4e45-ad88-9d81c4a0f014",
          "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
          "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/288330ae-86ec-4e45-ad88-9d81c4a0f014",
          "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75",
          "successful" : true,
          "mergeCommitHash" : "3fe89e6da79709aa68a5d889e07b87f42acad5eb",
          "conflicts" : [ ],
          "submoduleUpdates" : [ ],
          "mergeMessage" : "Merge successful",
          "mergedAt" : "2026-02-25T06:18:49.056896Z"
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
    "goal" : "Implement feature",
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
  "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6BT8K2MYAJ0TX42KFG98/01KJ9Q6H6KZHF5TGABWVT4Z446",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.242207Z",
      "lastCommitHash" : "3fe89e6da79709aa68a5d889e07b87f42acad5eb",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
        "baseBranch" : "derived-AH4WQQ5C",
        "status" : "ACTIVE",
        "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
        "createdAt" : "2026-02-25T06:18:38.241744Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
      "baseBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.241744Z",
      "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature",
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
  "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6MKA4FEM0K2Q9ED62F6X",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.242207Z",
      "lastCommitHash" : "3fe89e6da79709aa68a5d889e07b87f42acad5eb",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
        "baseBranch" : "derived-AH4WQQ5C",
        "status" : "ACTIVE",
        "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
        "createdAt" : "2026-02-25T06:18:38.241744Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
      "baseBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.241744Z",
      "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature"
}
```

### Response (`PlanningOrchestratorRouting`)

```json
{
  "agentRequests" : {
    "requests" : [ {
      "goal" : "Implement feature"
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
  "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6MKA4FEM0K2Q9ED62F6X/01KJ9Q6PC51S7RPGQTKZS888XS/01KJ9Q6PC5Q8CA6HX9T7XKR7RM",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/f8bc22c8-4ed7-4d08-a8af-0cdd015e5501/",
      "baseBranch" : "derived-AH4WQQ5C",
      "derivedBranch" : "planning-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6MKA4FEM0K2Q9ED62F6X/01KJ9Q6PC51S7RPGQTKZS888XS",
      "createdAt" : "2026-02-25T06:18:56.905017Z",
      "lastCommitHash" : "1f202a551fb5c1e20010eef798821a5c66983fd0",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "235b0635-00d5-459e-a146-d716ff526b9d",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/f8bc22c8-4ed7-4d08-a8af-0cdd015e5501/libs/test-sub/",
        "baseBranch" : "planning-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6MKA4FEM0K2Q9ED62F6X/01KJ9Q6PC51S7RPGQTKZS888XS",
        "createdAt" : "2026-02-25T06:18:57.519525Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "235b0635-00d5-459e-a146-d716ff526b9d",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/f8bc22c8-4ed7-4d08-a8af-0cdd015e5501/libs/test-sub/",
      "baseBranch" : "planning-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6MKA4FEM0K2Q9ED62F6X/01KJ9Q6PC51S7RPGQTKZS888XS",
      "createdAt" : "2026-02-25T06:18:57.519525Z",
      "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature"
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
  "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6MKA4FEM0K2Q9ED62F6X/01KJ9Q6RFP9K1NYHWBRVHAPY5T",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.242207Z",
      "lastCommitHash" : "3fe89e6da79709aa68a5d889e07b87f42acad5eb",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
        "baseBranch" : "derived-AH4WQQ5C",
        "status" : "ACTIVE",
        "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
        "createdAt" : "2026-02-25T06:18:38.241744Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
      "baseBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.241744Z",
      "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "metadata" : { }
    } ]
  },
  "planningAgentResults" : [ {
    "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6MKA4FEM0K2Q9ED62F6X/01KJ9Q6RFP9K1NYHWBRVHAPY5T/01KJ9Q6RFP2V4WAJA84RXEEHXT",
    "output" : "Plan output",
    "mergeDescriptor" : {
      "mergeDirection" : "TRUNK_TO_CHILD",
      "successful" : true,
      "conflictFiles" : [ ],
      "submoduleMergeResults" : [ ],
      "mainWorktreeMergeResult" : {
        "mergeId" : "8b9c1034-60ad-4110-a568-4112bfd01b94",
        "childWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "parentWorktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
        "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75",
        "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
        "successful" : true,
        "mergeCommitHash" : "58c25271b830f9c87da874a6335b11e5e71c08f2",
        "conflicts" : [ ],
        "submoduleUpdates" : [ ],
        "mergeMessage" : "Merge successful",
        "mergedAt" : "2026-02-25T06:18:58.044259Z"
      },
      "commitMetadata" : [ ]
    },
    "worktreeContext" : {
      "mainWorktree" : {
        "worktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/f8bc22c8-4ed7-4d08-a8af-0cdd015e5501/",
        "baseBranch" : "derived-AH4WQQ5C",
        "derivedBranch" : "planning-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6MKA4FEM0K2Q9ED62F6X/01KJ9Q6PC51S7RPGQTKZS888XS",
        "createdAt" : "2026-02-25T06:18:56.905017Z",
        "lastCommitHash" : "1f202a551fb5c1e20010eef798821a5c66983fd0",
        "hasSubmodules" : true,
        "submoduleWorktrees" : [ {
          "worktreeId" : "235b0635-00d5-459e-a146-d716ff526b9d",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/f8bc22c8-4ed7-4d08-a8af-0cdd015e5501/libs/test-sub/",
          "baseBranch" : "planning-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
          "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6MKA4FEM0K2Q9ED62F6X/01KJ9Q6PC51S7RPGQTKZS888XS",
          "createdAt" : "2026-02-25T06:18:57.519525Z",
          "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
          "metadata" : { }
        } ],
        "metadata" : { }
      },
      "submoduleWorktrees" : [ {
        "worktreeId" : "235b0635-00d5-459e-a146-d716ff526b9d",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/f8bc22c8-4ed7-4d08-a8af-0cdd015e5501/libs/test-sub/",
        "baseBranch" : "planning-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6MKA4FEM0K2Q9ED62F6X/01KJ9Q6PC51S7RPGQTKZS888XS",
        "createdAt" : "2026-02-25T06:18:57.519525Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
        "metadata" : { }
      } ]
    }
  } ],
  "mergeAggregation" : {
    "merged" : [ {
      "agentResultId" : "unknown",
      "worktreeContext" : {
        "mainWorktree" : {
          "worktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/f8bc22c8-4ed7-4d08-a8af-0cdd015e5501/",
          "baseBranch" : "derived-AH4WQQ5C",
          "derivedBranch" : "planning-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
          "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6MKA4FEM0K2Q9ED62F6X/01KJ9Q6PC51S7RPGQTKZS888XS",
          "createdAt" : "2026-02-25T06:18:56.905017Z",
          "lastCommitHash" : "1f202a551fb5c1e20010eef798821a5c66983fd0",
          "hasSubmodules" : true,
          "submoduleWorktrees" : [ {
            "worktreeId" : "235b0635-00d5-459e-a146-d716ff526b9d",
            "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/f8bc22c8-4ed7-4d08-a8af-0cdd015e5501/libs/test-sub/",
            "baseBranch" : "planning-1-ak-01KJ9",
            "status" : "ACTIVE",
            "parentWorktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
            "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6MKA4FEM0K2Q9ED62F6X/01KJ9Q6PC51S7RPGQTKZS888XS",
            "createdAt" : "2026-02-25T06:18:57.519525Z",
            "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
            "submoduleName" : "libs/test-sub",
            "mainWorktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
            "metadata" : { }
          } ],
          "metadata" : { }
        },
        "submoduleWorktrees" : [ {
          "worktreeId" : "235b0635-00d5-459e-a146-d716ff526b9d",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/f8bc22c8-4ed7-4d08-a8af-0cdd015e5501/libs/test-sub/",
          "baseBranch" : "planning-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
          "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6MKA4FEM0K2Q9ED62F6X/01KJ9Q6PC51S7RPGQTKZS888XS",
          "createdAt" : "2026-02-25T06:18:57.519525Z",
          "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
          "metadata" : { }
        } ]
      },
      "mergeDescriptor" : {
        "mergeDirection" : "CHILD_TO_TRUNK",
        "successful" : true,
        "conflictFiles" : [ ],
        "submoduleMergeResults" : [ ],
        "mainWorktreeMergeResult" : {
          "mergeId" : "be0d0b10-4093-4711-839f-bfbf355626c3",
          "childWorktreeId" : "f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
          "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
          "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/f8bc22c8-4ed7-4d08-a8af-0cdd015e5501",
          "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75",
          "successful" : true,
          "mergeCommitHash" : "e0bed08c1d8e98c5e5c98e8522c9668b73517be8",
          "conflicts" : [ ],
          "submoduleUpdates" : [ ],
          "mergeMessage" : "Merge successful",
          "mergedAt" : "2026-02-25T06:18:58.412588Z"
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
    "goal" : "Implement feature",
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
  "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6MKA4FEM0K2Q9ED62F6X/01KJ9Q6TK40P4N4GPKTYMJX1C3",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.242207Z",
      "lastCommitHash" : "e0bed08c1d8e98c5e5c98e8522c9668b73517be8",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
        "baseBranch" : "derived-AH4WQQ5C",
        "status" : "ACTIVE",
        "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
        "createdAt" : "2026-02-25T06:18:38.241744Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
      "baseBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.241744Z",
      "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature",
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
  "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6Y81K0NCWKV22KYPENYE",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.242207Z",
      "lastCommitHash" : "e0bed08c1d8e98c5e5c98e8522c9668b73517be8",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
        "baseBranch" : "derived-AH4WQQ5C",
        "status" : "ACTIVE",
        "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
        "createdAt" : "2026-02-25T06:18:38.241744Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
      "baseBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.241744Z",
      "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature"
}
```

### Response (`TicketOrchestratorRouting`)

```json
{
  "agentRequests" : {
    "requests" : [ {
      "ticketDetails" : "Implement feature",
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
  "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6Y81K0NCWKV22KYPENYE/01KJ9Q701QX4YY6GGN1W44RH25/01KJ9Q701Q2E59GJY8NCRENXYB",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/d8c4bf11-4653-46eb-b1da-2cac1c47f569/",
      "baseBranch" : "derived-AH4WQQ5C",
      "derivedBranch" : "ticket-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6Y81K0NCWKV22KYPENYE/01KJ9Q701QX4YY6GGN1W44RH25",
      "createdAt" : "2026-02-25T06:19:06.599371Z",
      "lastCommitHash" : "5587e6b14d068b319e4ff0eb9dae28ae9eddd41d",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "c3820347-29d4-4306-9835-db3afb0f2f56",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/d8c4bf11-4653-46eb-b1da-2cac1c47f569/libs/test-sub/",
        "baseBranch" : "ticket-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6Y81K0NCWKV22KYPENYE/01KJ9Q701QX4YY6GGN1W44RH25",
        "createdAt" : "2026-02-25T06:19:07.023539Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "c3820347-29d4-4306-9835-db3afb0f2f56",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/d8c4bf11-4653-46eb-b1da-2cac1c47f569/libs/test-sub/",
      "baseBranch" : "ticket-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6Y81K0NCWKV22KYPENYE/01KJ9Q701QX4YY6GGN1W44RH25",
      "createdAt" : "2026-02-25T06:19:07.023539Z",
      "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
      "metadata" : { }
    } ]
  },
  "ticketDetails" : "Implement feature",
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
  "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6Y81K0NCWKV22KYPENYE/01KJ9Q71SFA84B2JMRFD50WA9S",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.242207Z",
      "lastCommitHash" : "e0bed08c1d8e98c5e5c98e8522c9668b73517be8",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
        "baseBranch" : "derived-AH4WQQ5C",
        "status" : "ACTIVE",
        "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
        "createdAt" : "2026-02-25T06:18:38.241744Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
      "baseBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.241744Z",
      "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "metadata" : { }
    } ]
  },
  "ticketAgentResults" : [ {
    "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6Y81K0NCWKV22KYPENYE/01KJ9Q71SFA84B2JMRFD50WA9S/01KJ9Q71SGWN1G4J5VF98QKTXF",
    "output" : "Ticket output",
    "mergeDescriptor" : {
      "mergeDirection" : "TRUNK_TO_CHILD",
      "successful" : true,
      "conflictFiles" : [ ],
      "submoduleMergeResults" : [ ],
      "mainWorktreeMergeResult" : {
        "mergeId" : "786943e5-107d-491e-8370-962b61ec3cdc",
        "childWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "parentWorktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
        "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75",
        "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/d8c4bf11-4653-46eb-b1da-2cac1c47f569",
        "successful" : true,
        "mergeCommitHash" : "3562b359323d90724151f35756851d8bed8a5935",
        "conflicts" : [ ],
        "submoduleUpdates" : [ ],
        "mergeMessage" : "Merge successful",
        "mergedAt" : "2026-02-25T06:19:07.557651Z"
      },
      "commitMetadata" : [ ]
    },
    "worktreeContext" : {
      "mainWorktree" : {
        "worktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/d8c4bf11-4653-46eb-b1da-2cac1c47f569/",
        "baseBranch" : "derived-AH4WQQ5C",
        "derivedBranch" : "ticket-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6Y81K0NCWKV22KYPENYE/01KJ9Q701QX4YY6GGN1W44RH25",
        "createdAt" : "2026-02-25T06:19:06.599371Z",
        "lastCommitHash" : "5587e6b14d068b319e4ff0eb9dae28ae9eddd41d",
        "hasSubmodules" : true,
        "submoduleWorktrees" : [ {
          "worktreeId" : "c3820347-29d4-4306-9835-db3afb0f2f56",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/d8c4bf11-4653-46eb-b1da-2cac1c47f569/libs/test-sub/",
          "baseBranch" : "ticket-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
          "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6Y81K0NCWKV22KYPENYE/01KJ9Q701QX4YY6GGN1W44RH25",
          "createdAt" : "2026-02-25T06:19:07.023539Z",
          "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
          "metadata" : { }
        } ],
        "metadata" : { }
      },
      "submoduleWorktrees" : [ {
        "worktreeId" : "c3820347-29d4-4306-9835-db3afb0f2f56",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/d8c4bf11-4653-46eb-b1da-2cac1c47f569/libs/test-sub/",
        "baseBranch" : "ticket-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6Y81K0NCWKV22KYPENYE/01KJ9Q701QX4YY6GGN1W44RH25",
        "createdAt" : "2026-02-25T06:19:07.023539Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
        "metadata" : { }
      } ]
    }
  } ],
  "mergeAggregation" : {
    "merged" : [ {
      "agentResultId" : "unknown",
      "worktreeContext" : {
        "mainWorktree" : {
          "worktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/d8c4bf11-4653-46eb-b1da-2cac1c47f569/",
          "baseBranch" : "derived-AH4WQQ5C",
          "derivedBranch" : "ticket-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
          "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6Y81K0NCWKV22KYPENYE/01KJ9Q701QX4YY6GGN1W44RH25",
          "createdAt" : "2026-02-25T06:19:06.599371Z",
          "lastCommitHash" : "5587e6b14d068b319e4ff0eb9dae28ae9eddd41d",
          "hasSubmodules" : true,
          "submoduleWorktrees" : [ {
            "worktreeId" : "c3820347-29d4-4306-9835-db3afb0f2f56",
            "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/d8c4bf11-4653-46eb-b1da-2cac1c47f569/libs/test-sub/",
            "baseBranch" : "ticket-1-ak-01KJ9",
            "status" : "ACTIVE",
            "parentWorktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
            "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6Y81K0NCWKV22KYPENYE/01KJ9Q701QX4YY6GGN1W44RH25",
            "createdAt" : "2026-02-25T06:19:07.023539Z",
            "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
            "submoduleName" : "libs/test-sub",
            "mainWorktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
            "metadata" : { }
          } ],
          "metadata" : { }
        },
        "submoduleWorktrees" : [ {
          "worktreeId" : "c3820347-29d4-4306-9835-db3afb0f2f56",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/d8c4bf11-4653-46eb-b1da-2cac1c47f569/libs/test-sub/",
          "baseBranch" : "ticket-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
          "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6Y81K0NCWKV22KYPENYE/01KJ9Q701QX4YY6GGN1W44RH25",
          "createdAt" : "2026-02-25T06:19:07.023539Z",
          "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
          "metadata" : { }
        } ]
      },
      "mergeDescriptor" : {
        "mergeDirection" : "CHILD_TO_TRUNK",
        "successful" : true,
        "conflictFiles" : [ ],
        "submoduleMergeResults" : [ ],
        "mainWorktreeMergeResult" : {
          "mergeId" : "98b6130c-dd35-4572-8a49-3118f7196665",
          "childWorktreeId" : "d8c4bf11-4653-46eb-b1da-2cac1c47f569",
          "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
          "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/d8c4bf11-4653-46eb-b1da-2cac1c47f569",
          "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75",
          "successful" : true,
          "mergeCommitHash" : "cd15e2f57f2272d2ac0b4448bac2a1538a21e3a2",
          "conflicts" : [ ],
          "submoduleUpdates" : [ ],
          "mergeMessage" : "Merge successful",
          "mergedAt" : "2026-02-25T06:19:07.941507Z"
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
    "goal" : "Implement feature",
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
  "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q6Y81K0NCWKV22KYPENYE/01KJ9Q73KW7VHBWHE2EC2FANAN",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.242207Z",
      "lastCommitHash" : "cd15e2f57f2272d2ac0b4448bac2a1538a21e3a2",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
        "baseBranch" : "derived-AH4WQQ5C",
        "status" : "ACTIVE",
        "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
        "createdAt" : "2026-02-25T06:18:38.241744Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
      "baseBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.241744Z",
      "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature",
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
  "contextId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C/01KJ9Q779AM55NRJNZQ8FY7REH",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.242207Z",
      "lastCommitHash" : "cd15e2f57f2272d2ac0b4448bac2a1538a21e3a2",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
        "baseBranch" : "derived-AH4WQQ5C",
        "status" : "ACTIVE",
        "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
        "createdAt" : "2026-02-25T06:18:38.241744Z",
        "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "3c701c19-4d5c-4ebe-8dac-c06ac3038fa7",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75/libs/test-sub/",
      "baseBranch" : "derived-AH4WQQ5C",
      "status" : "ACTIVE",
      "parentWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "associatedNodeId" : "ak:01KJ9Q63W25EQJ6G8DAH4WQQ5C",
      "createdAt" : "2026-02-25T06:18:38.241744Z",
      "lastCommitHash" : "a02ec3323f995eddfe6cc2ee0d51efe341756baf",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "metadata" : { }
    } ]
  },
  "goal" : "Implement feature",
  "phase" : "DISCOVERY",
  "mergeDescriptor" : {
    "mergeDirection" : "WORKTREE_TO_SOURCE",
    "successful" : true,
    "conflictFiles" : [ ],
    "submoduleMergeResults" : [ ],
    "mainWorktreeMergeResult" : {
      "mergeId" : "ab0f97b5-c38f-4e91-a20c-8e5582f53a47",
      "childWorktreeId" : "73306cbe-9251-443f-b31b-5e8ed987fc75",
      "parentWorktreeId" : "source",
      "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/73306cbe-9251-443f-b31b-5e8ed987fc75",
      "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/test-main48683028331873679",
      "successful" : true,
      "mergeCommitHash" : "fd21985eb3770fca3f88a79c79f033862abf04f0",
      "conflicts" : [ ],
      "submoduleUpdates" : [ ],
      "mergeMessage" : "Final merge to source successful",
      "mergedAt" : "2026-02-25T06:19:13.904439Z"
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

