package com.hayden.multiagentide.repository;

import com.hayden.utilitymodule.acp.events.Events;
import com.hayden.multiagentidelib.model.nodes.GraphNode;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of GraphRepository using ConcurrentHashMap for thread safety.
 */
@Repository
public class InMemoryGraphRepository implements GraphRepository {

    private final ConcurrentHashMap<String, GraphNode> nodes = new ConcurrentHashMap<>();

    @Override
    public void save(GraphNode node) {
        nodes.put(node.nodeId(), node);
    }

    @Override
    public Optional<GraphNode> findById(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    @Override
    public List<GraphNode> findAll() {
        return new ArrayList<>(nodes.values());
    }

    @Override
    public List<GraphNode> findByParentId(String parentNodeId) {
        return nodes.values().stream()
                .filter(node -> parentNodeId.equals(node.parentNodeId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<GraphNode> findByType(Events.NodeType nodeType) {
        return nodes.values().stream()
                .filter(node -> node.nodeType() == nodeType)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String nodeId) {
        nodes.remove(nodeId);
    }

    @Override
    public boolean exists(String nodeId) {
        return nodes.containsKey(nodeId);
    }

    @Override
    public long count() {
        return nodes.size();
    }

    @Override
    public void clear() {
        nodes.clear();
    }


}
