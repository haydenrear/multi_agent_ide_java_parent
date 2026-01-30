package com.hayden.utilitymodule;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ArrayUtilsTest {

    @Test
    void update() {

    }


    @Test
    void toPrimitive() {
        double[][] doubles = Assertions.assertDoesNotThrow(() -> ArrayUtilUtilities.toPrimitive(new Double[][]{{1.0, 2.0}}));
        assertThat(doubles).isEqualTo(new double[][] {{1.0, 2.0}});
    }

    @Test
    void fromPrimitive() {
        Double[][] doubles = Assertions.assertDoesNotThrow(() -> ArrayUtilUtilities.fromPrimitive(new double[][]{{1.0, 2.0}}));
        assertThat(doubles).isEqualTo(new Double[][] {{1.0, 2.0}});
    }

}