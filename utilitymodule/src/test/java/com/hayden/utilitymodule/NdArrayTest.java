package com.hayden.utilitymodule;


import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.ndarray.types.Shape;
import com.hayden.utilitymodule.NdUtilities;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class NdArrayTest {

    @Test
    void testSerializeDeserialize() {
        try (NDManager manager = NDManager.newBaseManager()) {
            NDArray ndArray = manager.create(new double[]{0.1, 1.0, 2.0});
            var byteBuffer = ndArray.encode();
            NDArray decode = Assertions.assertDoesNotThrow(() -> manager.decode(byteBuffer));
            assertThat(decode.toDoubleArray()).isEqualTo(new double[]{0.1, 1.0, 2.0});
        }
    }

    @Test
    void test3dArr() {
        try (NDManager manager = NDManager.newBaseManager()) {
            NDArray ndArray = manager.create(new double[]{0.1, 1.0, 2.0});
            NDArray ndArray2 = manager.create(new double[]{1.0, 2.0, 3.0});
            NDArray ndArray1 = manager.create(new Shape(1, 2, 3));
            ndArray1.set(new NDIndex(0, 0), ndArray);
            ndArray1.set(new NDIndex(0, 1), ndArray2);
            float[][][] aDouble = NdUtilities.get3dArray(ndArray1);
            double[][][] dArrayDouble = NdUtilities.get3dArrayDouble(ndArray1);
            for (int i=0; i<aDouble.length; ++i) {
                for (int j=0; j<aDouble[0].length; ++j) {
                    for (int q=0; q<aDouble[0][0].length; ++q) {
                        assertThat(aDouble[i][j][q]).isEqualTo((float) dArrayDouble[i][j][q], Offset.<Float>offset(0.01f));
                    }
                }
            }
        }
    }

    @Test
    void testCreate3dArray() {
        try (NDManager manager = NDManager.newBaseManager()) {
            float[][][] startingArray = {{{0.0f, 1.0f, 2.0f}, {1.0f, 3.0f, 4.0f}, {4.0f, 5.0f, 6.0f}}};
            NDArray dArray = NdUtilities.create3dArray(startingArray, manager);
            float[] floatArray = dArray.get(0, 0).toFloatArray();
            assertThat(floatArray).isEqualTo(startingArray[0][0]);
            float[][][] dArray1 = NdUtilities.get3dArray(dArray);
            assertThat(startingArray).isEqualTo(dArray1);
        }
    }

    @Test
    void testCreate4dArray() {
        try (NDManager manager = NDManager.newBaseManager()) {
            float[][][][] startingArray = {{{{0.0f, 1.0f, 2.0f}, {1.0f, 3.0f, 4.0f}, {4.0f, 5.0f, 6.0f}, {4.0f, 5.0f, 7.0f}}}};
            NDArray dArray = NdUtilities.create4DArray(startingArray, manager);
            float[] floatArray = dArray.get(0, 0, 0).toFloatArray();
            assertThat(floatArray).isEqualTo(startingArray[0][0][0]);
            float[][][][] dArray1 = NdUtilities.get4dArray(dArray);
            assertThat(startingArray).isEqualTo(dArray1);
        }
    }

}