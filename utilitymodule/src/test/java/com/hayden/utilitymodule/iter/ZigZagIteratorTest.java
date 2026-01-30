package com.hayden.utilitymodule.iter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Iterator;
import java.util.stream.Collectors;

@Slf4j
public class ZigZagIteratorTest {

    record HasChildrenString(String s) implements LazyIterator.HasChildren<HasChildrenString> {
        @Override
        public Iterator<HasChildrenString> childrenIter() {
            return Lists.<HasChildrenString>newArrayList().iterator();
        }
    }

    @Test
    public void testZigZagIterator() {
        List<HasChildrenString> firstList = Lists.newArrayList("A", "B", "C", "D", "E").stream().map(HasChildrenString::new).collect(Collectors.toCollection(ArrayList::new));
        List<HasChildrenString>  secondList = Arrays.asList("F", "G", "H", "I", "J").stream().map(HasChildrenString::new).toList();

        // Initialize ZigZagIterator
        var zigZagIterator = new ZigZagIterator<HasChildrenString>(firstList, secondList);

        // Expected output in zig-zag pattern: A, E, B, D, C
        var expected = Arrays.asList("A", "F", "B", "G", "C", "H", "D", "I", "E", "J")
                .stream().map(HasChildrenString::new).toList();

        // Test the order of the elements
        for (var expectedElement : expected) {
            assertTrue(zigZagIterator.hasNext());
            assertEquals(expectedElement, zigZagIterator.next());
        }

        // Ensure no more elements are left
        assertFalse(zigZagIterator.hasNext());

        firstList.addAll(secondList);

        zigZagIterator = new ZigZagIterator<>(firstList);
        expected = Arrays.asList("E", "F", "D", "G", "C", "H", "B", "I", "A", "J")
                .stream().map(HasChildrenString::new).toList();

        for (var expectedElement : expected) {
            assertTrue(zigZagIterator.hasNext());
            HasChildrenString next = zigZagIterator.next();
            log.info("{}, {}", next, expectedElement);
            assertEquals(expectedElement, next);
        }

    }

    @Test
    public void testEmptyList() {
        // Test an empty list
        var first = Arrays.<HasChildrenString>asList();
        var second = Arrays.<HasChildrenString>asList();
        // Initialize ZigZagIterator
        var zigZagIterator = new ZigZagIterator<>(first, second);

        assertThat(zigZagIterator.hasNext()).isFalse();

    }

    @Test
    public void testZigZagIteratorUnbalanced() {
        // Create a sample list to test the ZigZagIterator
        var first = Arrays.asList("A", "B", "C", "D", "E")
                .stream().map(HasChildrenString::new).toList();
        var second = Arrays.asList("F", "G", "H")
                .stream().map(HasChildrenString::new).toList();

        // Initialize ZigZagIterator
        var zigZagIterator = new ZigZagIterator<>(first, second);

        // Expected output in zig-zag pattern: A, E, B, D, C
        var expected = Arrays.asList("A", "F", "B", "G", "C", "H", "D", "E")
                .stream().map(HasChildrenString::new).toList();

        // Test the order of the elements
        for (var expectedElement : expected) {
            assertTrue(zigZagIterator.hasNext());
            assertEquals(expectedElement, zigZagIterator.next());
        }

        // Ensure no more elements are left
        assertFalse(zigZagIterator.hasNext());
    }
}


