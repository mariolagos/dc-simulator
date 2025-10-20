package org.dcsim.testing;

import static org.junit.Assert.*;  // JUnit 4

/** Små, återanvändbara asserts för tester (JUnit 4). */
public final class AssertHelpers {
    private AssertHelpers() {}

    /** Kontrollera nodspänning med tolerans. */
    public static void assertVoltage(double[] V, int node, double expected, double tol) {
        assertNotNull("V must not be null", V);
        assertTrue("node index out of bounds", node >= 0 && node < V.length);
        assertEquals("node " + node + " voltage mismatch", expected, V[node], tol);
    }

    /** Summa ska vara ~0 (t.ex. effekt/strömbalanssanity). */
    public static void assertSumApproxZero(double[] arr, double tol) {
        double s = 0.0;
        for (double x : arr) s += x;
        assertEquals("sum not ~ 0", 0.0, s, tol);
    }

    /**
     * Assert dissipated power in a pure resistor between two nodes:
     * P = (Va - Vb)^2 / R.
     * <p>Intended for brake resistor / line-loss checks.</p>
     */
    public static void assertDissipatedPower(double[] V,
                                             int nodeA,
                                             int nodeB,
                                             double resistanceOhm,
                                             double expectedWatts,
                                             double tolAbsWatts) {
        double dv = V[nodeA] - V[nodeB];
        double p  = (dv * dv) / resistanceOhm;
        assertEquals("Dissipated power across R(" + nodeA + "-" + nodeB + ")",
                expectedWatts, p, tolAbsWatts);
    }

    /** Hjälp för ungefär lika. */
    public static void assertApprox(double actual, double expected, double tol, String msg) {
        assertEquals(msg, expected, actual, tol);
    }

    /** Assert V[node] < limit (strict). */
    public static void assertVoltageLessThan(double[] V, int nodeIndex, double limit) {
        assertTrue("Voltage at node " + nodeIndex + " expected < " + limit + " but was " + V[nodeIndex],
                V[nodeIndex] < limit);
    }

    /** Assert V[node] >= limit. */
    public static void assertVoltageAtLeast(double[] V, int nodeIndex, double limit) {
        assertTrue("Voltage at node " + nodeIndex + " expected >= " + limit + " but was " + V[nodeIndex],
                V[nodeIndex] >= limit);
    }
}
