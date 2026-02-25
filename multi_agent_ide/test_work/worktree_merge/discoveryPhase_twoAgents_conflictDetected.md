# QueuedLlmRunner Call Log

| Field | Value |
|-------|-------|
| **Test class** | `WorkflowAgentWorktreeMergeIntTest` |
| **Test method** | `discoveryPhase_twoAgents_conflictDetected` |
| **Started at** | 2026-02-25T06:17:13.802676Z |

---

## Call 1: `workflow/orchestrator`

**Request type**: `OrchestratorRequest`  
**Response type**: `OrchestratorRouting`  

### Decorated Request (`OrchestratorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.296503Z",
      "lastCommitHash" : "92b3b8bb2e46e364ea47f2970a842791e82626ca",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
        "baseBranch" : "derived-20CV2RAY",
        "status" : "ACTIVE",
        "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
        "createdAt" : "2026-02-25T06:17:07.295652Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
      "baseBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.295652Z",
      "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
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
  "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.296503Z",
      "lastCommitHash" : "92b3b8bb2e46e364ea47f2970a842791e82626ca",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
        "baseBranch" : "derived-20CV2RAY",
        "status" : "ACTIVE",
        "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
        "createdAt" : "2026-02-25T06:17:07.295652Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
      "baseBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.295652Z",
      "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
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
  "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT/01KJ9Q3PRTM9T0MKDXRHJ7A049",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/5cc3b6aa-1c81-4462-b166-14c5b8ac7e14/",
      "baseBranch" : "derived-20CV2RAY",
      "derivedBranch" : "discovery-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
      "createdAt" : "2026-02-25T06:17:18.815546Z",
      "lastCommitHash" : "92b3b8bb2e46e364ea47f2970a842791e82626ca",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "2b4779a1-b834-493a-b736-c2b4cecc32fa",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/5cc3b6aa-1c81-4462-b166-14c5b8ac7e14/libs/test-sub/",
        "baseBranch" : "discovery-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
        "createdAt" : "2026-02-25T06:17:19.251535Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "2b4779a1-b834-493a-b736-c2b4cecc32fa",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/5cc3b6aa-1c81-4462-b166-14c5b8ac7e14/libs/test-sub/",
      "baseBranch" : "discovery-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
      "createdAt" : "2026-02-25T06:17:19.251535Z",
      "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
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
  "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT/01KJ9Q3PRT2WCPGJ3GZV7REVCJ",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/c1378b4b-29dd-4c26-8b14-c9f1593e6bcd/",
      "baseBranch" : "derived-20CV2RAY",
      "derivedBranch" : "discovery-2-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
      "createdAt" : "2026-02-25T06:17:19.706352Z",
      "lastCommitHash" : "92b3b8bb2e46e364ea47f2970a842791e82626ca",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "3b106fb1-a846-4dc0-a3d3-7165bcac86da",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/c1378b4b-29dd-4c26-8b14-c9f1593e6bcd/libs/test-sub/",
        "baseBranch" : "discovery-2-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
        "createdAt" : "2026-02-25T06:17:20.132062Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "3b106fb1-a846-4dc0-a3d3-7165bcac86da",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/c1378b4b-29dd-4c26-8b14-c9f1593e6bcd/libs/test-sub/",
      "baseBranch" : "discovery-2-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
      "createdAt" : "2026-02-25T06:17:20.132062Z",
      "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
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
  "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3TC2RJ3XRJCDBTBPJ1ZQ",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.296503Z",
      "lastCommitHash" : "92b3b8bb2e46e364ea47f2970a842791e82626ca",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
        "baseBranch" : "derived-20CV2RAY",
        "status" : "ACTIVE",
        "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
        "createdAt" : "2026-02-25T06:17:07.295652Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
      "baseBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.295652Z",
      "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "metadata" : { }
    } ]
  },
  "result" : [ {
    "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3TC2RJ3XRJCDBTBPJ1ZQ/01KJ9Q3TC2B8S0JJK1239Q3RGJ",
    "output" : "Agent 1 findings",
    "mergeDescriptor" : {
      "mergeDirection" : "TRUNK_TO_CHILD",
      "successful" : true,
      "conflictFiles" : [ ],
      "submoduleMergeResults" : [ ],
      "mainWorktreeMergeResult" : {
        "mergeId" : "6b133faa-a1d4-4ca1-bb2c-4d909adc17d2",
        "childWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "parentWorktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
        "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
        "successful" : true,
        "mergeCommitHash" : "cd2fc4d6313ec3d8e325c06781a960df433b36fe",
        "conflicts" : [ ],
        "submoduleUpdates" : [ ],
        "mergeMessage" : "Merge successful",
        "mergedAt" : "2026-02-25T06:17:20.767290Z"
      },
      "commitMetadata" : [ ]
    },
    "worktreeContext" : {
      "mainWorktree" : {
        "worktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/5cc3b6aa-1c81-4462-b166-14c5b8ac7e14/",
        "baseBranch" : "derived-20CV2RAY",
        "derivedBranch" : "discovery-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
        "createdAt" : "2026-02-25T06:17:18.815546Z",
        "lastCommitHash" : "92b3b8bb2e46e364ea47f2970a842791e82626ca",
        "hasSubmodules" : true,
        "submoduleWorktrees" : [ {
          "worktreeId" : "2b4779a1-b834-493a-b736-c2b4cecc32fa",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/5cc3b6aa-1c81-4462-b166-14c5b8ac7e14/libs/test-sub/",
          "baseBranch" : "discovery-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
          "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
          "createdAt" : "2026-02-25T06:17:19.251535Z",
          "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
          "metadata" : { }
        } ],
        "metadata" : { }
      },
      "submoduleWorktrees" : [ {
        "worktreeId" : "2b4779a1-b834-493a-b736-c2b4cecc32fa",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/5cc3b6aa-1c81-4462-b166-14c5b8ac7e14/libs/test-sub/",
        "baseBranch" : "discovery-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
        "createdAt" : "2026-02-25T06:17:19.251535Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
        "metadata" : { }
      } ]
    }
  }, {
    "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3TC2RJ3XRJCDBTBPJ1ZQ/01KJ9Q3TC2NF9Q2H5G4KABCG7M",
    "output" : "Agent 2 findings",
    "mergeDescriptor" : {
      "mergeDirection" : "TRUNK_TO_CHILD",
      "successful" : true,
      "conflictFiles" : [ ],
      "submoduleMergeResults" : [ ],
      "mainWorktreeMergeResult" : {
        "mergeId" : "df0ef35c-94dc-4951-82d4-88cb3d5ae342",
        "childWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "parentWorktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
        "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
        "successful" : true,
        "mergeCommitHash" : "2095dd06f18bc2224397f23fcd27628c9152ddb3",
        "conflicts" : [ ],
        "submoduleUpdates" : [ ],
        "mergeMessage" : "Merge successful",
        "mergedAt" : "2026-02-25T06:17:21.519029Z"
      },
      "commitMetadata" : [ ]
    },
    "worktreeContext" : {
      "mainWorktree" : {
        "worktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/c1378b4b-29dd-4c26-8b14-c9f1593e6bcd/",
        "baseBranch" : "derived-20CV2RAY",
        "derivedBranch" : "discovery-2-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
        "createdAt" : "2026-02-25T06:17:19.706352Z",
        "lastCommitHash" : "92b3b8bb2e46e364ea47f2970a842791e82626ca",
        "hasSubmodules" : true,
        "submoduleWorktrees" : [ {
          "worktreeId" : "3b106fb1-a846-4dc0-a3d3-7165bcac86da",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/c1378b4b-29dd-4c26-8b14-c9f1593e6bcd/libs/test-sub/",
          "baseBranch" : "discovery-2-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
          "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
          "createdAt" : "2026-02-25T06:17:20.132062Z",
          "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
          "metadata" : { }
        } ],
        "metadata" : { }
      },
      "submoduleWorktrees" : [ {
        "worktreeId" : "3b106fb1-a846-4dc0-a3d3-7165bcac86da",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/c1378b4b-29dd-4c26-8b14-c9f1593e6bcd/libs/test-sub/",
        "baseBranch" : "discovery-2-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
        "createdAt" : "2026-02-25T06:17:20.132062Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
        "metadata" : { }
      } ]
    }
  } ],
  "mergeAggregation" : {
    "merged" : [ {
      "agentResultId" : "unknown",
      "worktreeContext" : {
        "mainWorktree" : {
          "worktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/5cc3b6aa-1c81-4462-b166-14c5b8ac7e14/",
          "baseBranch" : "derived-20CV2RAY",
          "derivedBranch" : "discovery-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
          "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
          "createdAt" : "2026-02-25T06:17:18.815546Z",
          "lastCommitHash" : "92b3b8bb2e46e364ea47f2970a842791e82626ca",
          "hasSubmodules" : true,
          "submoduleWorktrees" : [ {
            "worktreeId" : "2b4779a1-b834-493a-b736-c2b4cecc32fa",
            "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/5cc3b6aa-1c81-4462-b166-14c5b8ac7e14/libs/test-sub/",
            "baseBranch" : "discovery-1-ak-01KJ9",
            "status" : "ACTIVE",
            "parentWorktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
            "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
            "createdAt" : "2026-02-25T06:17:19.251535Z",
            "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
            "submoduleName" : "libs/test-sub",
            "mainWorktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
            "metadata" : { }
          } ],
          "metadata" : { }
        },
        "submoduleWorktrees" : [ {
          "worktreeId" : "2b4779a1-b834-493a-b736-c2b4cecc32fa",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/5cc3b6aa-1c81-4462-b166-14c5b8ac7e14/libs/test-sub/",
          "baseBranch" : "discovery-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
          "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
          "createdAt" : "2026-02-25T06:17:19.251535Z",
          "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
          "metadata" : { }
        } ]
      },
      "mergeDescriptor" : {
        "mergeDirection" : "CHILD_TO_TRUNK",
        "successful" : true,
        "conflictFiles" : [ ],
        "submoduleMergeResults" : [ ],
        "mainWorktreeMergeResult" : {
          "mergeId" : "7091f3ea-0d72-4016-9fdb-a9b3b73e84fd",
          "childWorktreeId" : "5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
          "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
          "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/5cc3b6aa-1c81-4462-b166-14c5b8ac7e14",
          "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
          "successful" : true,
          "mergeCommitHash" : "e4cfaf7a2c3bc3b44e8713bddc94279aeff5b8c9",
          "conflicts" : [ ],
          "submoduleUpdates" : [ ],
          "mergeMessage" : "Merge successful",
          "mergedAt" : "2026-02-25T06:17:21.894217Z"
        },
        "commitMetadata" : [ ]
      },
      "merged" : true
    } ],
    "pending" : [ ],
    "conflicted" : {
      "agentResultId" : "unknown",
      "worktreeContext" : {
        "mainWorktree" : {
          "worktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/c1378b4b-29dd-4c26-8b14-c9f1593e6bcd/",
          "baseBranch" : "derived-20CV2RAY",
          "derivedBranch" : "discovery-2-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
          "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
          "createdAt" : "2026-02-25T06:17:19.706352Z",
          "lastCommitHash" : "92b3b8bb2e46e364ea47f2970a842791e82626ca",
          "hasSubmodules" : true,
          "submoduleWorktrees" : [ {
            "worktreeId" : "3b106fb1-a846-4dc0-a3d3-7165bcac86da",
            "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/c1378b4b-29dd-4c26-8b14-c9f1593e6bcd/libs/test-sub/",
            "baseBranch" : "discovery-2-ak-01KJ9",
            "status" : "ACTIVE",
            "parentWorktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
            "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
            "createdAt" : "2026-02-25T06:17:20.132062Z",
            "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
            "submoduleName" : "libs/test-sub",
            "mainWorktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
            "metadata" : { }
          } ],
          "metadata" : { }
        },
        "submoduleWorktrees" : [ {
          "worktreeId" : "3b106fb1-a846-4dc0-a3d3-7165bcac86da",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/c1378b4b-29dd-4c26-8b14-c9f1593e6bcd/libs/test-sub/",
          "baseBranch" : "discovery-2-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
          "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3PRRKQX2RK2F0M58Q3GT",
          "createdAt" : "2026-02-25T06:17:20.132062Z",
          "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
          "metadata" : { }
        } ]
      },
      "mergeDescriptor" : {
        "mergeDirection" : "CHILD_TO_TRUNK",
        "successful" : false,
        "conflictFiles" : [ "shared-findings.md", "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00" ],
        "submoduleMergeResults" : [ ],
        "mainWorktreeMergeResult" : {
          "mergeId" : "be10663b-14f3-4a84-be49-3c32a232d7a5",
          "childWorktreeId" : "c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
          "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
          "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/c1378b4b-29dd-4c26-8b14-c9f1593e6bcd",
          "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
          "successful" : false,
          "conflicts" : [ {
            "filePath" : "shared-findings.md",
            "conflictType" : "content",
            "baseContent" : "",
            "oursContent" : "",
            "theirsContent" : ""
          }, {
            "filePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
            "conflictType" : "missing-commit",
            "baseContent" : "",
            "oursContent" : "",
            "theirsContent" : ""
          } ],
          "submoduleUpdates" : [ ],
          "mergeMessage" : "Merge conflicts detected",
          "mergedAt" : "2026-02-25T06:17:22.041275Z"
        },
        "errorMessage" : "Merge conflicts detected",
        "commitMetadata" : [ ]
      },
      "merged" : false
    }
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
  "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q3MN0DM8H4H1P7BZ9ZZ75/01KJ9Q3WA9FQ4K4KQ1JXYDHMXA",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.296503Z",
      "lastCommitHash" : "e4cfaf7a2c3bc3b44e8713bddc94279aeff5b8c9",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
        "baseBranch" : "derived-20CV2RAY",
        "status" : "ACTIVE",
        "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
        "createdAt" : "2026-02-25T06:17:07.295652Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
      "baseBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.295652Z",
      "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
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
    "consolidatedOutput" : "Discovery complete with conflicts",
    "collectorDecision" : {
      "decisionType" : "ADVANCE_PHASE",
      "rationale" : "Advance despite conflicts",
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
  "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q40YNDC64MHKAN7JSSPJV",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.296503Z",
      "lastCommitHash" : "e4cfaf7a2c3bc3b44e8713bddc94279aeff5b8c9",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
        "baseBranch" : "derived-20CV2RAY",
        "status" : "ACTIVE",
        "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
        "createdAt" : "2026-02-25T06:17:07.295652Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
      "baseBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.295652Z",
      "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
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
  "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q40YNDC64MHKAN7JSSPJV/01KJ9Q43DGRJTQAJ98F69K234K/01KJ9Q43DGK2ETPKVHZ8P5VBD3",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/98b8b7fc-7321-4f25-84e3-75717baf4c10/",
      "baseBranch" : "derived-20CV2RAY",
      "derivedBranch" : "planning-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q40YNDC64MHKAN7JSSPJV/01KJ9Q43DGRJTQAJ98F69K234K",
      "createdAt" : "2026-02-25T06:17:31.998848Z",
      "lastCommitHash" : "72a0a3aee104433c20b5c15ea3c759fe93bdae55",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "38b45f53-d184-4707-b625-d2c3014ea833",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/98b8b7fc-7321-4f25-84e3-75717baf4c10/libs/test-sub/",
        "baseBranch" : "planning-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q40YNDC64MHKAN7JSSPJV/01KJ9Q43DGRJTQAJ98F69K234K",
        "createdAt" : "2026-02-25T06:17:32.466395Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "38b45f53-d184-4707-b625-d2c3014ea833",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/98b8b7fc-7321-4f25-84e3-75717baf4c10/libs/test-sub/",
      "baseBranch" : "planning-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q40YNDC64MHKAN7JSSPJV/01KJ9Q43DGRJTQAJ98F69K234K",
      "createdAt" : "2026-02-25T06:17:32.466395Z",
      "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
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
  "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q40YNDC64MHKAN7JSSPJV/01KJ9Q45K7J6V1EHSFCB6JB9HA",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.296503Z",
      "lastCommitHash" : "e4cfaf7a2c3bc3b44e8713bddc94279aeff5b8c9",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
        "baseBranch" : "derived-20CV2RAY",
        "status" : "ACTIVE",
        "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
        "createdAt" : "2026-02-25T06:17:07.295652Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
      "baseBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.295652Z",
      "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "metadata" : { }
    } ]
  },
  "planningAgentResults" : [ {
    "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q40YNDC64MHKAN7JSSPJV/01KJ9Q45K7J6V1EHSFCB6JB9HA/01KJ9Q45K7NW40CJ4ADNEF7X0G",
    "output" : "Plan output",
    "mergeDescriptor" : {
      "mergeDirection" : "TRUNK_TO_CHILD",
      "successful" : true,
      "conflictFiles" : [ ],
      "submoduleMergeResults" : [ ],
      "mainWorktreeMergeResult" : {
        "mergeId" : "1b08145b-af0f-4304-afb4-994e8a32b213",
        "childWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "parentWorktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
        "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/98b8b7fc-7321-4f25-84e3-75717baf4c10",
        "successful" : true,
        "mergeCommitHash" : "3aeef676dad99649f655a59bb047d811072eaca0",
        "conflicts" : [ ],
        "submoduleUpdates" : [ ],
        "mergeMessage" : "Merge successful",
        "mergedAt" : "2026-02-25T06:17:33.066487Z"
      },
      "commitMetadata" : [ ]
    },
    "worktreeContext" : {
      "mainWorktree" : {
        "worktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/98b8b7fc-7321-4f25-84e3-75717baf4c10/",
        "baseBranch" : "derived-20CV2RAY",
        "derivedBranch" : "planning-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q40YNDC64MHKAN7JSSPJV/01KJ9Q43DGRJTQAJ98F69K234K",
        "createdAt" : "2026-02-25T06:17:31.998848Z",
        "lastCommitHash" : "72a0a3aee104433c20b5c15ea3c759fe93bdae55",
        "hasSubmodules" : true,
        "submoduleWorktrees" : [ {
          "worktreeId" : "38b45f53-d184-4707-b625-d2c3014ea833",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/98b8b7fc-7321-4f25-84e3-75717baf4c10/libs/test-sub/",
          "baseBranch" : "planning-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
          "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q40YNDC64MHKAN7JSSPJV/01KJ9Q43DGRJTQAJ98F69K234K",
          "createdAt" : "2026-02-25T06:17:32.466395Z",
          "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
          "metadata" : { }
        } ],
        "metadata" : { }
      },
      "submoduleWorktrees" : [ {
        "worktreeId" : "38b45f53-d184-4707-b625-d2c3014ea833",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/98b8b7fc-7321-4f25-84e3-75717baf4c10/libs/test-sub/",
        "baseBranch" : "planning-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q40YNDC64MHKAN7JSSPJV/01KJ9Q43DGRJTQAJ98F69K234K",
        "createdAt" : "2026-02-25T06:17:32.466395Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
        "metadata" : { }
      } ]
    }
  } ],
  "mergeAggregation" : {
    "merged" : [ ],
    "pending" : [ ],
    "conflicted" : {
      "agentResultId" : "unknown",
      "worktreeContext" : {
        "mainWorktree" : {
          "worktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/98b8b7fc-7321-4f25-84e3-75717baf4c10/",
          "baseBranch" : "derived-20CV2RAY",
          "derivedBranch" : "planning-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
          "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q40YNDC64MHKAN7JSSPJV/01KJ9Q43DGRJTQAJ98F69K234K",
          "createdAt" : "2026-02-25T06:17:31.998848Z",
          "lastCommitHash" : "72a0a3aee104433c20b5c15ea3c759fe93bdae55",
          "hasSubmodules" : true,
          "submoduleWorktrees" : [ {
            "worktreeId" : "38b45f53-d184-4707-b625-d2c3014ea833",
            "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/98b8b7fc-7321-4f25-84e3-75717baf4c10/libs/test-sub/",
            "baseBranch" : "planning-1-ak-01KJ9",
            "status" : "ACTIVE",
            "parentWorktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
            "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q40YNDC64MHKAN7JSSPJV/01KJ9Q43DGRJTQAJ98F69K234K",
            "createdAt" : "2026-02-25T06:17:32.466395Z",
            "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
            "submoduleName" : "libs/test-sub",
            "mainWorktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
            "metadata" : { }
          } ],
          "metadata" : { }
        },
        "submoduleWorktrees" : [ {
          "worktreeId" : "38b45f53-d184-4707-b625-d2c3014ea833",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/98b8b7fc-7321-4f25-84e3-75717baf4c10/libs/test-sub/",
          "baseBranch" : "planning-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
          "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q40YNDC64MHKAN7JSSPJV/01KJ9Q43DGRJTQAJ98F69K234K",
          "createdAt" : "2026-02-25T06:17:32.466395Z",
          "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
          "metadata" : { }
        } ]
      },
      "mergeDescriptor" : {
        "mergeDirection" : "CHILD_TO_TRUNK",
        "successful" : false,
        "conflictFiles" : [ "root", "shared-findings.md", "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00" ],
        "submoduleMergeResults" : [ ],
        "mainWorktreeMergeResult" : {
          "mergeId" : "eb8f9c35-98b1-452e-b84b-942218c7899a",
          "childWorktreeId" : "98b8b7fc-7321-4f25-84e3-75717baf4c10",
          "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
          "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/98b8b7fc-7321-4f25-84e3-75717baf4c10",
          "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
          "successful" : false,
          "conflicts" : [ {
            "filePath" : "root",
            "conflictType" : "merge-error",
            "baseContent" : "",
            "oursContent" : "",
            "theirsContent" : ""
          }, {
            "filePath" : "shared-findings.md",
            "conflictType" : "detected",
            "baseContent" : "",
            "oursContent" : "",
            "theirsContent" : ""
          }, {
            "filePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
            "conflictType" : "missing-commit",
            "baseContent" : "",
            "oursContent" : "",
            "theirsContent" : ""
          } ],
          "submoduleUpdates" : [ ],
          "mergeMessage" : "Merge conflicts detected",
          "mergedAt" : "2026-02-25T06:17:33.535195Z"
        },
        "errorMessage" : "Merge conflicts detected",
        "commitMetadata" : [ ]
      },
      "merged" : false
    }
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
  "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q40YNDC64MHKAN7JSSPJV/01KJ9Q47RVYNBCJJH9CJTXK75Q",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.296503Z",
      "lastCommitHash" : "e4cfaf7a2c3bc3b44e8713bddc94279aeff5b8c9",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
        "baseBranch" : "derived-20CV2RAY",
        "status" : "ACTIVE",
        "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
        "createdAt" : "2026-02-25T06:17:07.295652Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
      "baseBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.295652Z",
      "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
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
  "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q4BRMVNWMCH8B8EFQJCD2",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.296503Z",
      "lastCommitHash" : "e4cfaf7a2c3bc3b44e8713bddc94279aeff5b8c9",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
        "baseBranch" : "derived-20CV2RAY",
        "status" : "ACTIVE",
        "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
        "createdAt" : "2026-02-25T06:17:07.295652Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
      "baseBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.295652Z",
      "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
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
  "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q4BRMVNWMCH8B8EFQJCD2/01KJ9Q4DX3381VGHT6M4E3F9PN/01KJ9Q4DX3PG5SJG4H5WQHHCMK",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/e2c91e8f-2b8e-4035-b2c9-e087256133eb/",
      "baseBranch" : "derived-20CV2RAY",
      "derivedBranch" : "ticket-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q4BRMVNWMCH8B8EFQJCD2/01KJ9Q4DX3381VGHT6M4E3F9PN",
      "createdAt" : "2026-02-25T06:17:42.506730Z",
      "lastCommitHash" : "72a0a3aee104433c20b5c15ea3c759fe93bdae55",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "20e3ed05-3c44-4332-8dea-9f988d58fca6",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/e2c91e8f-2b8e-4035-b2c9-e087256133eb/libs/test-sub/",
        "baseBranch" : "ticket-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q4BRMVNWMCH8B8EFQJCD2/01KJ9Q4DX3381VGHT6M4E3F9PN",
        "createdAt" : "2026-02-25T06:17:42.925104Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "20e3ed05-3c44-4332-8dea-9f988d58fca6",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/e2c91e8f-2b8e-4035-b2c9-e087256133eb/libs/test-sub/",
      "baseBranch" : "ticket-1-ak-01KJ9",
      "status" : "ACTIVE",
      "parentWorktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q4BRMVNWMCH8B8EFQJCD2/01KJ9Q4DX3381VGHT6M4E3F9PN",
      "createdAt" : "2026-02-25T06:17:42.925104Z",
      "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
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
  "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q4BRMVNWMCH8B8EFQJCD2/01KJ9Q4FN8KQXE2HWPMDZFGCTV",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.296503Z",
      "lastCommitHash" : "e4cfaf7a2c3bc3b44e8713bddc94279aeff5b8c9",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
        "baseBranch" : "derived-20CV2RAY",
        "status" : "ACTIVE",
        "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
        "createdAt" : "2026-02-25T06:17:07.295652Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
      "baseBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.295652Z",
      "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "metadata" : { }
    } ]
  },
  "ticketAgentResults" : [ {
    "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q4BRMVNWMCH8B8EFQJCD2/01KJ9Q4FN8KQXE2HWPMDZFGCTV/01KJ9Q4FN83ARXJG7104JJXZ6E",
    "output" : "Ticket output",
    "mergeDescriptor" : {
      "mergeDirection" : "TRUNK_TO_CHILD",
      "successful" : true,
      "conflictFiles" : [ ],
      "submoduleMergeResults" : [ ],
      "mainWorktreeMergeResult" : {
        "mergeId" : "37bb98a2-4ae7-48a9-a250-812ae8f884b0",
        "childWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "parentWorktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
        "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/e2c91e8f-2b8e-4035-b2c9-e087256133eb",
        "successful" : true,
        "mergeCommitHash" : "2dda413e2df0fb7ed773960d081866979ae0ea56",
        "conflicts" : [ ],
        "submoduleUpdates" : [ ],
        "mergeMessage" : "Merge successful",
        "mergedAt" : "2026-02-25T06:17:43.488658Z"
      },
      "commitMetadata" : [ ]
    },
    "worktreeContext" : {
      "mainWorktree" : {
        "worktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/e2c91e8f-2b8e-4035-b2c9-e087256133eb/",
        "baseBranch" : "derived-20CV2RAY",
        "derivedBranch" : "ticket-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q4BRMVNWMCH8B8EFQJCD2/01KJ9Q4DX3381VGHT6M4E3F9PN",
        "createdAt" : "2026-02-25T06:17:42.506730Z",
        "lastCommitHash" : "72a0a3aee104433c20b5c15ea3c759fe93bdae55",
        "hasSubmodules" : true,
        "submoduleWorktrees" : [ {
          "worktreeId" : "20e3ed05-3c44-4332-8dea-9f988d58fca6",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/e2c91e8f-2b8e-4035-b2c9-e087256133eb/libs/test-sub/",
          "baseBranch" : "ticket-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
          "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q4BRMVNWMCH8B8EFQJCD2/01KJ9Q4DX3381VGHT6M4E3F9PN",
          "createdAt" : "2026-02-25T06:17:42.925104Z",
          "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
          "metadata" : { }
        } ],
        "metadata" : { }
      },
      "submoduleWorktrees" : [ {
        "worktreeId" : "20e3ed05-3c44-4332-8dea-9f988d58fca6",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/e2c91e8f-2b8e-4035-b2c9-e087256133eb/libs/test-sub/",
        "baseBranch" : "ticket-1-ak-01KJ9",
        "status" : "ACTIVE",
        "parentWorktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q4BRMVNWMCH8B8EFQJCD2/01KJ9Q4DX3381VGHT6M4E3F9PN",
        "createdAt" : "2026-02-25T06:17:42.925104Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
        "metadata" : { }
      } ]
    }
  } ],
  "mergeAggregation" : {
    "merged" : [ ],
    "pending" : [ ],
    "conflicted" : {
      "agentResultId" : "unknown",
      "worktreeContext" : {
        "mainWorktree" : {
          "worktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/e2c91e8f-2b8e-4035-b2c9-e087256133eb/",
          "baseBranch" : "derived-20CV2RAY",
          "derivedBranch" : "ticket-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
          "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q4BRMVNWMCH8B8EFQJCD2/01KJ9Q4DX3381VGHT6M4E3F9PN",
          "createdAt" : "2026-02-25T06:17:42.506730Z",
          "lastCommitHash" : "72a0a3aee104433c20b5c15ea3c759fe93bdae55",
          "hasSubmodules" : true,
          "submoduleWorktrees" : [ {
            "worktreeId" : "20e3ed05-3c44-4332-8dea-9f988d58fca6",
            "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/e2c91e8f-2b8e-4035-b2c9-e087256133eb/libs/test-sub/",
            "baseBranch" : "ticket-1-ak-01KJ9",
            "status" : "ACTIVE",
            "parentWorktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
            "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q4BRMVNWMCH8B8EFQJCD2/01KJ9Q4DX3381VGHT6M4E3F9PN",
            "createdAt" : "2026-02-25T06:17:42.925104Z",
            "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
            "submoduleName" : "libs/test-sub",
            "mainWorktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
            "metadata" : { }
          } ],
          "metadata" : { }
        },
        "submoduleWorktrees" : [ {
          "worktreeId" : "20e3ed05-3c44-4332-8dea-9f988d58fca6",
          "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/e2c91e8f-2b8e-4035-b2c9-e087256133eb/libs/test-sub/",
          "baseBranch" : "ticket-1-ak-01KJ9",
          "status" : "ACTIVE",
          "parentWorktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
          "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q4BRMVNWMCH8B8EFQJCD2/01KJ9Q4DX3381VGHT6M4E3F9PN",
          "createdAt" : "2026-02-25T06:17:42.925104Z",
          "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
          "submoduleName" : "libs/test-sub",
          "mainWorktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
          "metadata" : { }
        } ]
      },
      "mergeDescriptor" : {
        "mergeDirection" : "CHILD_TO_TRUNK",
        "successful" : false,
        "conflictFiles" : [ "root", "shared-findings.md", "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00" ],
        "submoduleMergeResults" : [ ],
        "mainWorktreeMergeResult" : {
          "mergeId" : "b4fc4ffb-3503-4413-a1c8-47dfa0dc68e1",
          "childWorktreeId" : "e2c91e8f-2b8e-4035-b2c9-e087256133eb",
          "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
          "childWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/e2c91e8f-2b8e-4035-b2c9-e087256133eb",
          "parentWorktreePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
          "successful" : false,
          "conflicts" : [ {
            "filePath" : "root",
            "conflictType" : "merge-error",
            "baseContent" : "",
            "oursContent" : "",
            "theirsContent" : ""
          }, {
            "filePath" : "shared-findings.md",
            "conflictType" : "detected",
            "baseContent" : "",
            "oursContent" : "",
            "theirsContent" : ""
          }, {
            "filePath" : "/var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
            "conflictType" : "missing-commit",
            "baseContent" : "",
            "oursContent" : "",
            "theirsContent" : ""
          } ],
          "submoduleUpdates" : [ ],
          "mergeMessage" : "Merge conflicts detected",
          "mergedAt" : "2026-02-25T06:17:43.841313Z"
        },
        "errorMessage" : "Merge conflicts detected",
        "commitMetadata" : [ ]
      },
      "merged" : false
    }
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
  "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q4BRMVNWMCH8B8EFQJCD2/01KJ9Q4HGKENWZ8G0PNC226P4A",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.296503Z",
      "lastCommitHash" : "e4cfaf7a2c3bc3b44e8713bddc94279aeff5b8c9",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
        "baseBranch" : "derived-20CV2RAY",
        "status" : "ACTIVE",
        "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
        "createdAt" : "2026-02-25T06:17:07.295652Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
      "baseBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.295652Z",
      "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
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

## Call 15: `workflow/worktree_commit_agent`

**Request type**: `CommitAgentRequest`  
**Response type**: `CommitAgentResult`  

### Decorated Request (`CommitAgentRequest`)

```json
{
  "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q4N13H3QXEJ5AJ09EK7JC",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.296503Z",
      "lastCommitHash" : "e4cfaf7a2c3bc3b44e8713bddc94279aeff5b8c9",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
        "baseBranch" : "derived-20CV2RAY",
        "status" : "ACTIVE",
        "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
        "createdAt" : "2026-02-25T06:17:07.295652Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
      "baseBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.295652Z",
      "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "metadata" : { }
    } ]
  },
  "routedFromRequest" : {
    "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
    "goal" : "Discover features",
    "phase" : "DISCOVERY"
  },
  "goal" : "Discover features",
  "sourceAgentType" : "ALL",
  "sourceRequestType" : "OrchestratorRequest",
  "commitInstructions" : "Use your toolset to inspect git status and commit pending changes in this worktree. Split into multiple focused commits when appropriate. Each commit message must include metadata trailers shown below. Before returning, git status must be clean for the target worktree (no staged, unstaged, untracked, or conflicted files).",
  "sourceResultSummary" : "Orchestrator Request Context Id: ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY Worktree Context: \t(none) Goal: Discover features Phase: DISCOVERY Interrupt Feedback Resolutions: (none) Discovery Curation: \t(none) Planning Curation: \t(none) Ticket Curation: \t(none) Previous Context: \t(none)"
}
```

### Response (`CommitAgentResult`)

```json
{
  "successful" : false
}
```

---

## Call 16: `workflow/orchestrator_collector`

**Request type**: `OrchestratorCollectorRequest`  
**Response type**: `OrchestratorCollectorRouting`  

### Decorated Request (`OrchestratorCollectorRequest`)

```json
{
  "contextId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY/01KJ9Q4N0VEBNAPJ2YFTJ5KR76",
  "worktreeContext" : {
    "mainWorktree" : {
      "worktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/",
      "baseBranch" : "main",
      "derivedBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.296503Z",
      "lastCommitHash" : "e4cfaf7a2c3bc3b44e8713bddc94279aeff5b8c9",
      "hasSubmodules" : true,
      "submoduleWorktrees" : [ {
        "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
        "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
        "baseBranch" : "derived-20CV2RAY",
        "status" : "ACTIVE",
        "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
        "createdAt" : "2026-02-25T06:17:07.295652Z",
        "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
        "submoduleName" : "libs/test-sub",
        "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
        "metadata" : { }
      } ],
      "metadata" : { }
    },
    "submoduleWorktrees" : [ {
      "worktreeId" : "870192ea-c4e0-4ee2-9c02-e06198c85887",
      "worktreePath" : "file:///var/folders/b7/rsz3g6wn4hg8zl2bwmdx61q00000gn/T/multi-agent-ide-merge-test-worktrees/70eddfa5-ac36-45a4-8a6c-1d3269f8df00/libs/test-sub/",
      "baseBranch" : "derived-20CV2RAY",
      "status" : "ACTIVE",
      "parentWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "associatedNodeId" : "ak:01KJ9Q3AVXDJCYGJPQ20CV2RAY",
      "createdAt" : "2026-02-25T06:17:07.295652Z",
      "lastCommitHash" : "36455acb32a3b791a16e12dac3c452677c0d38b8",
      "submoduleName" : "libs/test-sub",
      "mainWorktreeId" : "70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
      "metadata" : { }
    } ]
  },
  "goal" : "Discover features",
  "phase" : "DISCOVERY",
  "mergeDescriptor" : {
    "mergeDirection" : "WORKTREE_TO_SOURCE",
    "successful" : false,
    "conflictFiles" : [ ],
    "submoduleMergeResults" : [ ],
    "errorMessage" : "Final merge to source blocked: auto-commit failed: Commit agent returned unsuccessful response for worktree 70eddfa5-ac36-45a4-8a6c-1d3269f8df00",
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

