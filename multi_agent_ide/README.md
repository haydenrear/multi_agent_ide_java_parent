Not implemented yet.

## Artifact Persistence

The artifact system captures execution traces as immutable, reconstructable artifact trees.

### Core Concepts

- **ArtifactKey**: Hierarchical ULID-based identifier (`ak:<ulid>/<ulid>/...`)
- **Execution Tree**: Root artifact with required child groups (ExecutionConfig, InputArtifacts, AgentExecutionArtifacts, OutcomeEvidenceArtifacts)
- **Content Hashing**: SHA-256 on canonical JSON bytes for deduplication and integrity

### Key Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `ArtifactKey` | `multi_agent_ide_lib/.../artifact/` | Hierarchical key generation |
| `Artifact` | `multi_agent_ide_lib/.../artifact/` | Sealed interface for all artifact types |
| `ArtifactEntity` | `.../artifacts/entity/` | JPA persistence entity |
| `ArtifactRepository` | `.../artifacts/repository/` | Spring Data repository |
| `ExecutionScopeService` | `.../artifacts/` | Manages execution scopes and root artifacts |
| `ArtifactEmissionService` | `.../artifacts/` | Emits artifacts during agent execution |
| `SemanticRepresentationService` | `.../artifacts/semantic/` | Post-hoc semantic attachments |

### Artifact Types

- `ExecutionArtifact` - Root of execution tree
- `ExecutionConfigArtifact` - Configuration snapshot
- `RenderedPromptArtifact` - Fully rendered prompt text
- `PromptArgsArtifact` - Template arguments
- `PromptContributionArtifact` - Individual prompt contributor outputs
- `ToolCallArtifact` - Tool invocations with I/O
- `OutcomeEvidenceArtifact` - Objective evidence (tests, builds, etc.)
- `EventArtifact` - Captured GraphEvents
- `MessageStreamArtifact` - Stream deltas and message fragments
- `AgentRequestArtifact` / `AgentResultArtifact` - Agent interactions
- `RefArtifact` - Non-containment references

### Template Versioning

Prompt templates are versioned by content hash and deduplicated:
- `PromptTemplateVersion` - Versioned template model
- `PromptTemplateStore` - Persistence with deduplication by (staticId, contentHash)
- `PromptTemplateLoader` - Loads templates from classpath at startup

### Semantic Layer

Post-hoc semantic representations (embeddings, summaries) can be attached to artifacts without mutating them:
- `SemanticRepresentation` - Model for semantic attachments
- `SemanticRepresentationService` - Attachment and retrieval service

### Configuration

See `application.yml` for artifact persistence settings.
