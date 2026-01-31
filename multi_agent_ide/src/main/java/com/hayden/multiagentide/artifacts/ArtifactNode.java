package com.hayden.multiagentide.artifacts;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.utilitymodule.stream.StreamUtil;
import com.mysema.commons.lang.Assert;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trie node for the artifact tree structure.
 *
 * Each node represents an artifact at a specific position in the hierarchy.
 * Children are keyed by their full ArtifactKey value for O(1) lookup.
 *
 * When children are added, they are also added to the parent artifact's
 * children list, building the tree structure within the Artifact model itself.
 *
 * Invariants:
 * - Messages come in order (no level skipping)
 * - Never remove nodes
 * - Deduplication happens via content hash comparison among siblings
 */
@Slf4j
public class ArtifactNode {

    @Getter
    private final ArtifactKey artifactKey;

    @Getter
    private final Artifact artifact;

    // Children keyed by their full ArtifactKey value
    private final Map<String, ArtifactNode> children = new ConcurrentHashMap<>();

    // Content hashes of children for fast dedup lookup
    private final Set<String> childContentHashes = ConcurrentHashMap.newKeySet();

    public ArtifactNode(ArtifactKey artifactKey, Artifact artifact) {
        this.artifactKey = artifactKey;
        this.artifact = artifact;

        StreamUtil.toStream(artifact)
                .flatMap(a -> StreamUtil.toStream(a.children()))
                .forEach(child -> {
                    String keyValue = child.artifactKey().value();
                    ArtifactNode childNode = new ArtifactNode(child.artifactKey(), child);
                    children.put(keyValue, childNode);
                    child.contentHash().ifPresent(childContentHashes::add);
                });
    }

    /**
     * Creates a root node for an execution tree.
     */
    public static ArtifactNode createRoot(Artifact rootArtifact) {
        return new ArtifactNode(rootArtifact.artifactKey(), rootArtifact);
    }

    /**
     * Adds an artifact to the tree at the correct position.
     * Uses the hierarchical key to navigate to the parent, then checks
     * siblings for hash duplicates before adding.
     *
     * The artifact is also added to the parent artifact's children list,
     * building the tree structure within the Artifact model.
     *
     * @param artifact The artifact to add
     * @return AddResult indicating success, duplicate key, or duplicate hash
     */
    public AddResult addArtifact(Artifact artifact) {
        ArtifactKey key = artifact.artifactKey();

        // If this is for the root node itself, reject as duplicate
        if (key.equals(this.artifactKey)) {
            return AddResult.DUPLICATE_KEY;
        }

        // Find the parent node for this artifact
        Optional<ArtifactKey> parentKeyOpt = key.parent();
        if (parentKeyOpt.isEmpty()) {
            // This is a root-level artifact but we already have a root
            log.warn("Attempted to add root artifact when root already exists: {}", key);
            return AddResult.DUPLICATE_KEY;
        }

        ArtifactKey parentKey = parentKeyOpt.get();
        ArtifactNode parentNode = findNode(parentKey);

        if (parentNode == null) {
            log.warn("Parent node not found for artifact: {} (expected parent: {})", key, parentKey);
            return AddResult.PARENT_NOT_FOUND;
        }

        return parentNode.addChild(artifact);
    }

    /**
     * Adds a child artifact to this node.
     * Checks for duplicate keys and duplicate hashes among siblings.
     * Also adds the artifact to this node's artifact's children list.
     */
    private AddResult addChild(Artifact childArtifact) {
        String keyValue = childArtifact.artifactKey().value();

        // Check for duplicate key
        if (children.containsKey(keyValue)) {
            log.debug("Duplicate key rejected: {}", keyValue);
            return AddResult.DUPLICATE_KEY;
        }

        // Check for duplicate hash among siblings
        Optional<String> contentHash = childArtifact.contentHash();
        if (contentHash.isPresent() && childContentHashes.contains(contentHash.get())) {
            log.debug("Duplicate hash rejected for key {}: {}", keyValue, contentHash.get());
            return AddResult.DUPLICATE_HASH;
        }

        // Add the child node to our trie structure
        ArtifactNode childNode = new ArtifactNode(childArtifact.artifactKey(), childArtifact);
        children.put(keyValue, childNode);

        // Add the child artifact to this artifact's children list
        // This builds the tree structure within the Artifact model itself
        this.artifact.children().add(childArtifact);

        // Register the hash for future dedup
        contentHash.ifPresent(childContentHashes::add);

        log.trace("Added artifact: {} (hash: {})", keyValue, contentHash.orElse("none"));
        return AddResult.ADDED;
    }

    /**
     * Finds a node by its artifact key.
     * Navigates the trie structure using the hierarchical key.
     */
    public ArtifactNode findNode(ArtifactKey targetKey) {
        if (targetKey.equals(this.artifactKey)) {
            return this;
        }

        // Check if target is a descendant of this node
        if (!targetKey.value().startsWith(this.artifactKey.value())) {
            return null;
        }

        // Search children
        for (ArtifactNode child : children.values()) {
            if (targetKey.equals(child.artifactKey)) {
                return child;
            }
            if (targetKey.value().startsWith(child.artifactKey.value() + "/")) {
                ArtifactNode found = child.findNode(targetKey);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * Checks if a sibling with the given hash exists under this node.
     */
    public boolean hasSiblingWithHash(String contentHash) {
        return childContentHashes.contains(contentHash);
    }

    /**
     * Returns all child nodes of this node.
     */
    public Collection<ArtifactNode> getChildren() {
        return Collections.unmodifiableCollection(children.values());
    }

    /**
     * Returns the root artifact with its children tree fully populated.
     * This is the primary way to retrieve the built artifact tree.
     */
    public Artifact buildArtifactTree() {
        // The artifact already has its children populated via addChild
        // Just return the root artifact
        return this.artifact;
    }

    /**
     * Collects all artifacts in this subtree (including this node).
     * This is a flat list, not the tree structure.
     */
    public List<Artifact> collectAll() {
        List<Artifact> result = new ArrayList<>();
        collectAllRecursive(result);
        return result;
    }

    private void collectAllRecursive(List<Artifact> accumulator) {
        accumulator.add(this.artifact);
        for (ArtifactNode child : children.values()) {
            child.collectAllRecursive(accumulator);
        }
    }

    /**
     * Returns the number of artifacts in this subtree (including this node).
     */
    public int size() {
        int count = 1;
        for (ArtifactNode child : children.values()) {
            count += child.size();
        }
        return count;
    }

    /**
     * Result of attempting to add an artifact.
     */
    public enum AddResult {
        /** Artifact was successfully added */
        ADDED,
        /** An artifact with this key already exists */
        DUPLICATE_KEY,
        /** A sibling with the same content hash already exists */
        DUPLICATE_HASH,
        /** The parent node for this artifact was not found */
        PARENT_NOT_FOUND
    }
}
