package com.hayden.multiagentide.artifacts;

import com.hayden.multiagentidelib.artifact.PromptTemplateVersion;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ArtifactNode trie structure.
 * 
 * Tests verify:
 * - Trie insertion with hierarchical key navigation
 * - Hash-based deduplication among siblings
 * - Tree traversal and collection
 */
class ArtifactNodeTest {
    
    private ArtifactKey rootKey;
    private Artifact.ExecutionArtifact rootArtifact;
    private ArtifactNode rootNode;
    
    @BeforeEach
    void setUp() {
        rootKey = ArtifactKey.createRoot();
        rootArtifact = Artifact.ExecutionArtifact.builder()
                .artifactKey(rootKey)
                .workflowRunId("test-run-1")
                .startedAt(Instant.now())
                .status(Artifact.ExecutionStatus.RUNNING)
                .metadata(new java.util.HashMap<>())
                .children(new ArrayList<>())
                .build();
        rootNode = ArtifactNode.createRoot(rootArtifact);
    }
    
    @Nested
    @DisplayName("Basic Node Operations")
    class BasicOperations {
        
        @Test
        @DisplayName("createRoot creates node with correct artifact")
        void createRootCreatesNodeWithCorrectArtifact() {
            assertThat(rootNode.getArtifact()).isEqualTo(rootArtifact);
            assertThat(rootNode.getArtifactKey()).isEqualTo(rootKey);
        }
        
        @Test
        @DisplayName("findNode returns self for root key")
        void findNodeReturnsSelfForRootKey() {
            ArtifactNode found = rootNode.findNode(rootKey);
            assertThat(found).isSameAs(rootNode);
        }
        
        @Test
        @DisplayName("findNode returns null for non-existent key")
        void findNodeReturnsNullForNonExistent() {
            ArtifactKey otherKey = ArtifactKey.createRoot();
            ArtifactNode found = rootNode.findNode(otherKey);
            assertThat(found).isNull();
        }
        
        @Test
        @DisplayName("new node has no children")
        void newNodeHasNoChildren() {
            assertThat(rootNode.getChildren()).isEmpty();
            assertThat(rootNode.size()).isEqualTo(1);
        }
    }
    
    @Nested
    @DisplayName("Child Addition")
    class ChildAddition {
        
        @Test
        @DisplayName("addArtifact adds direct child successfully")
        void addArtifactAddsDirectChild() {
            ArtifactKey childKey = rootKey.createChild();
            Artifact.AgentModelArtifact childArtifact = createAgentModelArtifact(childKey, "InputArtifacts");
            
            ArtifactNode.AddResult result = rootNode.addArtifact(childArtifact);
            
            assertThat(result).isEqualTo(ArtifactNode.AddResult.ADDED);
            assertThat(rootNode.getChildren()).hasSize(1);
            assertThat(rootNode.size()).isEqualTo(2);
        }
        
        @Test
        @DisplayName("addArtifact adds grandchild under correct parent")
        void addArtifactAddsGrandchildUnderCorrectParent() {
            // Add child first
            ArtifactKey childKey = rootKey.createChild();
            Artifact.AgentModelArtifact childArtifact = createAgentModelArtifact(childKey, "InputArtifacts");
            rootNode.addArtifact(childArtifact);
            
            // Add grandchild
            ArtifactKey grandchildKey = childKey.createChild();
            Artifact.RenderedPromptArtifact grandchildArtifact = createRenderedPromptArtifact(
                    grandchildKey, "Hello world", "abc123");
            
            ArtifactNode.AddResult result = rootNode.addArtifact(grandchildArtifact);
            
            assertThat(result).isEqualTo(ArtifactNode.AddResult.ADDED);
            assertThat(rootNode.size()).isEqualTo(3);
            
            // Verify grandchild is under correct parent
            ArtifactNode childNode = rootNode.findNode(childKey);
            assertThat(childNode).isNotNull();
            assertThat(childNode.getChildren()).hasSize(1);
        }
        
        @Test
        @DisplayName("addArtifact with duplicate key returns DUPLICATE_KEY")
        void addArtifactWithDuplicateKeyReturnsDuplicateKey() {
            ArtifactKey childKey = rootKey.createChild();
            Artifact.AgentModelArtifact firstArtifact = createAgentModelArtifact(childKey, "First");
            Artifact.AgentModelArtifact secondArtifact = createAgentModelArtifact(childKey, "Second");
            
            rootNode.addArtifact(firstArtifact);
            ArtifactNode.AddResult result = rootNode.addArtifact(secondArtifact);
            
            assertThat(result).isEqualTo(ArtifactNode.AddResult.DUPLICATE_KEY);
            assertThat(rootNode.getChildren()).hasSize(1);
        }
        
        @Test
        @DisplayName("addArtifact for root key returns DUPLICATE_KEY")
        void addArtifactForRootKeyReturnsDuplicateKey() {
            Artifact.ExecutionArtifact duplicateRoot = Artifact.ExecutionArtifact.builder()
                    .artifactKey(rootKey)
                    .workflowRunId("different-run")
                    .startedAt(Instant.now())
                    .status(Artifact.ExecutionStatus.RUNNING)
                    .metadata(new java.util.HashMap<>())
                    .children(new ArrayList<>())
                    .build();
            
            ArtifactNode.AddResult result = rootNode.addArtifact(duplicateRoot);
            
            assertThat(result).isEqualTo(ArtifactNode.AddResult.DUPLICATE_KEY);
        }
        
        @Test
        @DisplayName("addArtifact with missing parent returns PARENT_NOT_FOUND")
        void addArtifactWithMissingParentReturnsParentNotFound() {
            // Create a key that's a grandchild of root, but parent doesn't exist
            ArtifactKey missingParentKey = rootKey.createChild();
            ArtifactKey orphanKey = missingParentKey.createChild();
            
            Artifact.AgentModelArtifact orphanArtifact = createAgentModelArtifact(orphanKey, "Orphan");
            
            ArtifactNode.AddResult result = rootNode.addArtifact(orphanArtifact);
            
            assertThat(result).isEqualTo(ArtifactNode.AddResult.PARENT_NOT_FOUND);
        }
    }
    
    @Nested
    @DisplayName("Hash-Based Deduplication")
    class HashDeduplication {
        
        @Test
        @DisplayName("addArtifact with duplicate hash still adds distinct key")
        void addArtifactWithDuplicateHashStillAddsDistinctKey() {
            String sharedHash = "abc123def456";
            
            ArtifactKey firstChildKey = rootKey.createChild();
            Artifact.RenderedPromptArtifact firstChild = createRenderedPromptArtifact(
                    firstChildKey, "Same content", sharedHash);
            
            ArtifactKey secondChildKey = rootKey.createChild();
            Artifact.RenderedPromptArtifact secondChild = createRenderedPromptArtifact(
                    secondChildKey, "Same content", sharedHash);
            
            rootNode.addArtifact(firstChild);
            ArtifactNode.AddResult result = rootNode.addArtifact(secondChild);
            
            assertThat(result).isEqualTo(ArtifactNode.AddResult.ADDED);
            assertThat(rootNode.getChildren()).hasSize(2);
        }
        
        @Test
        @DisplayName("addArtifact with different hash adds successfully")
        void addArtifactWithDifferentHashAddsSuccessfully() {
            ArtifactKey firstChildKey = rootKey.createChild();
            Artifact.RenderedPromptArtifact firstChild = createRenderedPromptArtifact(
                    firstChildKey, "Content A", "hash-a");
            
            ArtifactKey secondChildKey = rootKey.createChild();
            Artifact.RenderedPromptArtifact secondChild = createRenderedPromptArtifact(
                    secondChildKey, "Content B", "hash-b");
            
            rootNode.addArtifact(firstChild);
            ArtifactNode.AddResult result = rootNode.addArtifact(secondChild);
            
            assertThat(result).isEqualTo(ArtifactNode.AddResult.ADDED);
            assertThat(rootNode.getChildren()).hasSize(2);
        }
        
        @Test
        @DisplayName("addArtifact without hash always adds")
        void addArtifactWithoutHashAlwaysAdds() {
            // AgentModelArtifact has no contentHash
            ArtifactKey firstChildKey = rootKey.createChild();
            Artifact.AgentModelArtifact firstChild = createAgentModelArtifact(firstChildKey, "Group1");
            
            ArtifactKey secondChildKey = rootKey.createChild();
            Artifact.AgentModelArtifact secondChild = createAgentModelArtifact(secondChildKey, "Group2");
            
            rootNode.addArtifact(firstChild);
            ArtifactNode.AddResult result = rootNode.addArtifact(secondChild);
            
            assertThat(result).isEqualTo(ArtifactNode.AddResult.ADDED);
            assertThat(rootNode.getChildren()).hasSize(2);
        }
        
        @Test
        @DisplayName("hasSiblingWithHash returns true when hash exists")
        void hasSiblingWithHashReturnsTrueWhenExists() {
            String hash = "existing-hash";
            ArtifactKey childKey = rootKey.createChild();
            Artifact.RenderedPromptArtifact child = createRenderedPromptArtifact(childKey, "Text", hash);
            
            rootNode.addArtifact(child);
            
            assertThat(rootNode.hasSiblingWithHash(hash)).isTrue();
        }
        
        @Test
        @DisplayName("hasSiblingWithHash returns false when hash doesn't exist")
        void hasSiblingWithHashReturnsFalseWhenNotExists() {
            assertThat(rootNode.hasSiblingWithHash("non-existent")).isFalse();
        }
        
        @Test
        @DisplayName("hash deduplication is scoped to siblings only")
        void hashDeduplicationIsScopedToSiblingsOnly() {
            String sharedHash = "shared-hash";
            
            // Add child with hash
            ArtifactKey childKey = rootKey.createChild();
            Artifact.RenderedPromptArtifact child = createRenderedPromptArtifact(
                    childKey, "Text", sharedHash);
            rootNode.addArtifact(child);
            
            // Add grandchild with same hash - should succeed (different parent)
            ArtifactKey grandchildKey = childKey.createChild();
            Artifact.RenderedPromptArtifact grandchild = createRenderedPromptArtifact(
                    grandchildKey, "Text", sharedHash);
            
            ArtifactNode.AddResult result = rootNode.addArtifact(grandchild);
            
            assertThat(result).isEqualTo(ArtifactNode.AddResult.ADDED);
        }
    }
    
    @Nested
    @DisplayName("Tree Collection")
    class TreeCollection {
        
        @Test
        @DisplayName("collectAll returns all artifacts in subtree")
        void collectAllReturnsAllArtifacts() {
            // Build a small tree
            ArtifactKey child1Key = rootKey.createChild();
            ArtifactKey child2Key = rootKey.createChild();
            ArtifactKey grandchildKey = child1Key.createChild();
            
            rootNode.addArtifact(createAgentModelArtifact(child1Key, "Child1"));
            rootNode.addArtifact(createAgentModelArtifact(child2Key, "Child2"));
            rootNode.addArtifact(createAgentModelArtifact(grandchildKey, "Grandchild"));
            
            List<Artifact> allArtifacts = rootNode.collectAll();
            
            assertThat(allArtifacts).hasSize(4);
            assertThat(allArtifacts).contains(rootArtifact);
        }
        
        @Test
        @DisplayName("size returns correct count")
        void sizeReturnsCorrectCount() {
            ArtifactKey child1Key = rootKey.createChild();
            ArtifactKey child2Key = rootKey.createChild();
            ArtifactKey grandchildKey = child1Key.createChild();
            
            rootNode.addArtifact(createAgentModelArtifact(child1Key, "Child1"));
            rootNode.addArtifact(createAgentModelArtifact(child2Key, "Child2"));
            rootNode.addArtifact(createAgentModelArtifact(grandchildKey, "Grandchild"));
            
            assertThat(rootNode.size()).isEqualTo(4);
        }
    }
    
    @Nested
    @DisplayName("Template Artifacts")
    class TemplateArtifacts {
        
        @Test
        @DisplayName("PromptTemplateVersion artifacts can be added")
        void promptTemplateVersionCanBeAdded() {
            ArtifactKey childKey = rootKey.createChild();
            PromptTemplateVersion template = PromptTemplateVersion.builder()
                    .templateStaticId("tpl.agent.test.prompt")
                    .templateText("Hello {{ name }}")
                    .hash("template-hash-123")
                    .templateArtifactKey(childKey)
                    .lastUpdatedAt(Instant.now())
                    .build();
            
            ArtifactNode.AddResult result = rootNode.addArtifact(template);
            
            assertThat(result).isEqualTo(ArtifactNode.AddResult.ADDED);
            assertThat(rootNode.getChildren()).hasSize(1);
            
            ArtifactNode templateNode = rootNode.findNode(childKey);
            assertThat(templateNode).isNotNull();
            assertThat(templateNode.getArtifact()).isInstanceOf(PromptTemplateVersion.class);
        }
        
        @Test
        @DisplayName("duplicate PromptTemplateVersion hash is accepted for distinct key")
        void duplicateTemplateHashIsAcceptedForDistinctKey() {
            String sharedHash = "template-hash-shared";
            
            ArtifactKey firstKey = rootKey.createChild();
            PromptTemplateVersion first = PromptTemplateVersion.builder()
                    .templateStaticId("tpl.agent.test.first")
                    .templateText("Template text")
                    .hash(sharedHash)
                    .templateArtifactKey(firstKey)
                    .lastUpdatedAt(Instant.now())
                    .build();
            
            ArtifactKey secondKey = rootKey.createChild();
            PromptTemplateVersion second = PromptTemplateVersion.builder()
                    .templateStaticId("tpl.agent.test.second")
                    .templateText("Template text")
                    .hash(sharedHash)
                    .templateArtifactKey(secondKey)
                    .lastUpdatedAt(Instant.now())
                    .build();
            
            rootNode.addArtifact(first);
            ArtifactNode.AddResult result = rootNode.addArtifact(second);
            
            assertThat(result).isEqualTo(ArtifactNode.AddResult.ADDED);
        }
    }
    
    // ========== Helper Methods ==========

    private Artifact.AgentModelArtifact createAgentModelArtifact(ArtifactKey key, String name) {
        return Artifact.AgentModelArtifact.builder()
                .agentModel(new Artifact.AgentModel() {
                    @Override
                    public String computeHash(Artifact.HashContext hashContext) {
                        return UUID.randomUUID().toString();
                    }

                    @Override
                    public List<Artifact.AgentModel> children() {
                        return List.of();
                    }

                    @Override
                    public ArtifactKey key() {
                        return key;
                    }

                    @Override
                    public Artifact.AgentModel withContextId(ArtifactKey key) {
                        return this;
                    }

                    @Override
                    public <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> c) {
                        return null;
                    }
                })
                .metadata(new java.util.HashMap<>())
                .children(new ArrayList<>())
                .build();
    }

    private Artifact.RenderedPromptArtifact createRenderedPromptArtifact(
            ArtifactKey key, String text, String hash) {
        return Artifact.RenderedPromptArtifact.builder()
                .artifactKey(key)
                .renderedText(text)
                .hash(hash)
                .metadata(new java.util.HashMap<>())
                .children(new ArrayList<>())
                .build();
    }
}
