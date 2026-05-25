package org.supply.solver.electrical;

import org.junit.Test;
import org.supply.math.Real;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public final class AdmittanceSystemTest {

    @Test
    public void exposesDeterministicNodeIndexMapping() {

        Map<String, Integer> indexById = new LinkedHashMap<>();
        indexById.put("A", 0);
        indexById.put("B", 1);

        AdmittanceSystem system = new AdmittanceSystem(
                indexById,
                List.of("A", "B"),
                zeroMatrix(2),
                zeroVector(2),
                "referenceNode"
        );

        assertEquals(2, system.size());
        assertEquals(0, system.nodeIndex("A"));
        assertEquals(1, system.nodeIndex("B"));

        assertEquals("A", system.nodeIds().get(0));
        assertEquals("B", system.nodeIds().get(1));
    }

    @Test
    public void rejectsUnknownNodeIdLookup() {

        Map<String, Integer> indexById = new LinkedHashMap<>();
        indexById.put("A", 0);

        AdmittanceSystem system = new AdmittanceSystem(
                indexById,
                List.of("A"),
                zeroMatrix(1),
                zeroVector(1),
                "referenceNode"
        );

        try {
            system.nodeIndex("missing");
            fail("Expected IllegalArgumentException");

        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("missing"));
        }
    }

    private static Real[][] zeroMatrix(int size) {
        Real[][] matrix = new Real[size][size];

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                matrix[row][col] = Real.fromDouble(0.0);
            }
        }

        return matrix;
    }

    private static Real[] zeroVector(int size) {
        Real[] vector = new Real[size];

        for (int i = 0; i < size; i++) {
            vector[i] = Real.fromDouble(0.0);
        }

        return vector;
    }
}