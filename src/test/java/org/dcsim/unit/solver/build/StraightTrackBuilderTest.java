package org.dcsim.unit.solver.build;

import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.LineData;
import org.dcsim.solver.build.StraightTrackBuilder;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class StraightTrackBuilderTest {

    private static final double EPS = 1e-12;

    @Test
    public void two_nodes_produces_one_line_with_expected_R() {
        int groundIndex = 0;
        int nNodes = 2;

        double length_km = 1.0;   // 1 km total
        double r_per_km = 10.0;   // 10 ohm per km

        // segment_length_km = 1.0 / (2-1) = 1.0
        // R = 10.0 * 1.0 = 10.0
        double expectedR = 10.0;

        DcNet net = StraightTrackBuilder.build(
                groundIndex,
                nNodes,
                length_km,
                r_per_km,
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertEquals(1, net.lines().size());

        LineData l = net.lines().get(0);
        assertEndpointsByIndex(l, 0, 1);
        assertEquals(expectedR, rOhm(l), EPS);
    }

    @Test
    public void three_nodes_produces_two_equal_segments() {
        int groundIndex = 0;
        int nNodes = 3;

        double length_km = 1.0;  // 1 km
        double r_per_km = 10.0;  // 10 ohm/km

        // segment_length_km = 1.0 / (3-1) = 0.5
        // R = 10.0 * 0.5 = 5.0
        double expectedR = 5.0;

        DcNet net = StraightTrackBuilder.build(
                groundIndex,
                nNodes,
                length_km,
                r_per_km,
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertEquals(2, net.lines().size());

        LineData l0 = net.lines().get(0);
        LineData l1 = net.lines().get(1);

        assertEndpointsByIndex(l0, 0, 1);
        assertEquals(expectedR, rOhm(l0), EPS);

        assertEndpointsByIndex(l1, 1, 2);
        assertEquals(expectedR, rOhm(l1), EPS);
    }

    @Test
    public void lines_count_is_nNodes_minus_1_for_various_sizes() {
        double length_km = 2.0;
        double r_per_km = 1.0;

        for (int nNodes : new int[]{2, 3, 5, 10}) {
            DcNet net = StraightTrackBuilder.build(
                    0,
                    nNodes,
                    length_km,
                    r_per_km,
                    Collections.emptyList(),
                    Collections.emptyList()
            );
            assertEquals("nNodes=" + nNodes, Math.max(0, nNodes - 1), net.lines().size());
        }
    }

    // ---- Helpers ----

    private static void assertEndpointsByIndex(LineData l, int i, int j) {
        int a = a(l);
        int b = b(l);
        boolean ok = (a == i && b == j) || (a == j && b == i);
        assertTrue("Unexpected endpoints: (" + a + "," + b + "), expected {" + i + "," + j + "}", ok);
    }

    // Adapt these 3 methods if your LineData getters are named differently.
    private static int a(LineData l) {
        // common names: a(), from(), fromIdx(), i()
        return l.a();
    }

    private static int b(LineData l) {
        // common names: b(), to(), toIdx(), j()
        return l.b();
    }

    private static double rOhm(LineData l) {
        // common names: rOhm(), r_ohm(), r()
        return l.r_ohm();
    }
}
