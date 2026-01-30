package com.hayden.utilitymodule.iter;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
class LazyDelegatingIteratorTest {

    @Test
    public void testLazyDelegatingIterator() {
        List<Iterator<Integer>> list = IntStream.range(0, 10).boxed().map(i -> IntStream.range(0, i).boxed().toList().iterator())
                .map(DepthFirstLazyDelegatingIterator::new)
                .collect(Collectors.toCollection(ArrayList::new));
        list.add(IntStream.range(0, 10).boxed().toList().iterator());
        DepthFirstLazyDelegatingIterator<Integer> iterator = new DepthFirstLazyDelegatingIterator<>(list);

        int count = 0;
        while(iterator.hasNext()) {
            var next = Assertions.assertDoesNotThrow(iterator::next);
            log.info("{}", next);
            count += 1;
        }

        Assertions.assertEquals(1 + 2 + 3 + 4 + 5 + 6 + 7 + 8 + 9 + 10, count);

        var nIterator = new DepthFirstLazyDelegatingIterator<>(List.of(List.<Integer>of().iterator()));
        count = 0;
        while (nIterator.hasNext()) {
            count += 1;
        }

        Assertions.assertEquals(0, count);
    }

    @Test
    public void testLazyDelegatingIteratorBreadthFirst() {
        List<Iterator<Integer>> list = IntStream.range(0, 10).boxed().map(i -> IntStream.range(0, i).boxed().toList().iterator())
                .map(BreadthFirstLazyDelegatingIterator::new)
                .collect(Collectors.toCollection(ArrayList::new));
        list.add(IntStream.range(0, 10).boxed().toList().iterator());
        BreadthFirstLazyDelegatingIterator<Integer> iterator = new BreadthFirstLazyDelegatingIterator<>(list);

        int count = 0;
        while(iterator.hasNext()) {
            var next = Assertions.assertDoesNotThrow(iterator::next);
            log.info("{}", next);
            count += 1;
        }

        Assertions.assertEquals(1 + 2 + 3 + 4 + 5 + 6 + 7 + 8 + 9 + 10, count);

        var nIterator = new BreadthFirstLazyDelegatingIterator<>(List.of(List.<Integer>of().iterator()));
        count = 0;
        while (nIterator.hasNext()) {
            count += 1;
        }

        Assertions.assertEquals(0, count);
    }

}