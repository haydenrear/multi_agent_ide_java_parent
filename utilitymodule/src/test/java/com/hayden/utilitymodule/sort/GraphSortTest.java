package com.hayden.utilitymodule.sort;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphSortTest {

    interface TestNode extends GraphSort.GraphSortable {

    }

    // Mock implementation of GraphSortable for testing
    static class TestNodeOne implements TestNode {
        private final List<Class<? extends GraphSort.GraphSortable>> dependencies;

        public TestNodeOne(List<Class<? extends GraphSort.GraphSortable>> dependencies) {
            this.dependencies = dependencies;
        }

        @Override
        public List<Class<? extends GraphSort.GraphSortable>> dependsOn() {
            return dependencies;
        }
    }

    // Mock implementation of GraphSortable for testing
    static class TestNodeTwo implements TestNode {
        private final List<Class<? extends GraphSort.GraphSortable>> dependencies;

        public TestNodeTwo(List<Class<? extends GraphSort.GraphSortable>> dependencies) {
            this.dependencies = dependencies;
        }

        @Override
        public List<Class<? extends GraphSort.GraphSortable>> dependsOn() {
            return dependencies;
        }
    }

    // Mock implementation of GraphSortable for testing
    static class TestNodeThree implements TestNode {
        private final List<Class<? extends GraphSort.GraphSortable>> dependencies;

        public TestNodeThree(List<Class<? extends GraphSort.GraphSortable>> dependencies) {
            this.dependencies = dependencies;
        }

        @Override
        public List<Class<? extends GraphSort.GraphSortable>> dependsOn() {
            return dependencies;
        }
    }

    // Mock implementation of GraphSortable for testing
    static class TestNodeFour implements TestNode {
        private final List<Class<? extends GraphSort.GraphSortable>> dependencies;

        public TestNodeFour(List<Class<? extends GraphSort.GraphSortable>> dependencies) {
            this.dependencies = dependencies;
        }

        @Override
        public List<Class<? extends GraphSort.GraphSortable>> dependsOn() {
            return dependencies;
        }
    }

    @Test
    void testSortWithZeroNodes() {
        List<TestNode> nodes = Collections.emptyList();
        List<TestNode> sorted = GraphSort.sort(nodes);
        assertTrue(sorted.isEmpty(), "Sorting an empty list should return an empty list.");
    }

    @Test
    void testSortWithOneNode() {
        TestNode nodeA = new TestNodeOne(Collections.emptyList());
        List<TestNode> nodes = List.of(nodeA);
        List<TestNode> sorted = GraphSort.sort(nodes);
        assertEquals(List.of(nodeA), sorted, "Sorting a single node should return that node.");
    }

    @Test
    void testSortWithMultipleNodes() {
        TestNode nodeA = new TestNodeOne(Collections.emptyList());
        TestNode nodeB = new TestNodeTwo(List.of(TestNodeOne.class)); // Depends on A
        TestNode nodeC = new TestNodeThree(List.of(TestNodeTwo.class)); // Depends on A
        TestNode nodeD = new TestNodeFour(List.of(TestNodeOne.class, TestNodeTwo.class, TestNodeThree.class)); // Depends on B and C

        List<TestNode> nodes = List.of(nodeA, nodeB, nodeC, nodeD);
        List<TestNode> sorted = GraphSort.sort(nodes);

        // Verify that node A comes first, followed by B and C, and then D
        assertEquals(4, sorted.size(), "There should be 4 sorted nodes.");
        assertTrue(sorted.indexOf(nodeA) < sorted.indexOf(nodeB), "Node A should come before Node B.");
        assertTrue(sorted.indexOf(nodeA) < sorted.indexOf(nodeC), "Node A should come before Node C.");
        assertTrue(sorted.indexOf(nodeB) < sorted.indexOf(nodeD), "Node B should come before Node D.");
        assertTrue(sorted.indexOf(nodeC) < sorted.indexOf(nodeD), "Node C should come before Node D.");
    }

    @Test
    void testSortWithCycle() {
        TestNode nodeA = new TestNodeOne(List.of(TestNodeTwo.class)); // Depends on A itself (cycle)
        TestNode nodeB = new TestNodeTwo(List.of(TestNodeOne.class)); // Depends on A itself (cycle)
        List<TestNode> nodes = List.of(nodeA, nodeB);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            GraphSort.sort(nodes);
        });

        assertEquals("Cycle detected in the graph", exception.getMessage(), "Should detect a cycle.");
    }

    @Test
    void testSortWithCycleMulti() {
        TestNode nodeA = new TestNodeOne(List.of(TestNodeTwo.class)); // Depends on A itself (cycle)
        TestNode nodeB = new TestNodeTwo(List.of(TestNodeThree.class)); // Depends on A itself (cycle)
        TestNode nodeC = new TestNodeThree(List.of(TestNodeOne.class)); // Depends on A itself (cycle)
        List<TestNode> nodes = List.of(nodeA, nodeB, nodeC);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            GraphSort.sort(nodes);
        });

        assertEquals("Cycle detected in the graph", exception.getMessage(), "Should detect a cycle.");
    }
}
