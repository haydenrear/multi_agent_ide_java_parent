package com.hayden.utilitymodule.fn;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class ReducerTest {

    @Test
    void chainReducers() {
        BiFunction<Integer, Integer, Integer> first = (a, b) -> a + b;
        BiFunction<Integer, Integer, Integer> second = (a, b) -> a * b;
        BiFunction<Integer, Integer, Integer> third = (a, b) -> a - b;

        var o = Reducer.chainReducers(List.of(first, second, third)).get().apply(1, 2);
        var firstV = 1 + 2;
        var secondV = 1 * firstV;
        var thirdV = 1 - secondV;
        assertEquals(thirdV, o);

        o = Reducer.chainReducers(List.of(first, second, third)).get().apply(10, 20);
        firstV = 10 + 20;
        secondV = 10 * firstV;
        thirdV = 10 - secondV;
        assertEquals(thirdV, o);
    }
}