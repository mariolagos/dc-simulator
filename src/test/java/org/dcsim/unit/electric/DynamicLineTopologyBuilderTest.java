package org.dcsim.unit.electric;

import org.dcsim.electric.DcLine;
import org.dcsim.electric.Device;
import org.dcsim.electric.DynamicLineTopologyBuilder;
import org.dcsim.math.Real;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class DynamicLineTopologyBuilderTest {

    private static final double EPS = 1e-12;

    @Test
    public void builds_consecutive_lines_sorted_by_position_and_correct_R() {
        double rPerM = 0.01;

        // Same track, intentionally unsorted by position
        List<DynamicLineTopologyBuilder.NodePos> nodes = List.of(
                new DynamicLineTopologyBuilder.NodePos("20", 1, 1000),
                new DynamicLineTopologyBuilder.NodePos("10", 1, 0),
                new DynamicLineTopologyBuilder.NodePos("99", 1, 200)
        );

        List<Device<Real>> lines = DynamicLineTopologyBuilder.buildDynamicLines(
                nodes,
                (trackId, posA, posB) -> rPerM * Math.abs(posB - posA)
        );

        assertEquals(2, lines.size());

        DcLine l0 = (DcLine) lines.get(0);
        DcLine l1 = (DcLine) lines.get(1);

        // After sorting by pos: 10(0) -> 99(200) -> 20(1000)
        assertEndpoints(l0, 10, 99);
        assertEquals(200 * rPerM, l0.getResistance().asDouble(), EPS);

        assertEndpoints(l1, 99, 20);
        assertEquals(800 * rPerM, l1.getResistance().asDouble(), EPS);
    }

    @Test
    public void moving_train_changes_adjacent_segment_resistances() {
        double rPerM = 0.01;

        List<DynamicLineTopologyBuilder.NodePos> near = List.of(
                new DynamicLineTopologyBuilder.NodePos("10", 1, 0),
                new DynamicLineTopologyBuilder.NodePos("99", 1, 200),
                new DynamicLineTopologyBuilder.NodePos("20", 1, 1000)
        );

        List<DynamicLineTopologyBuilder.NodePos> far = List.of(
                new DynamicLineTopologyBuilder.NodePos("10", 1, 0),
                new DynamicLineTopologyBuilder.NodePos("99", 1, 800),
                new DynamicLineTopologyBuilder.NodePos("20", 1, 1000)
        );

        List<Device<Real>> linesNear = DynamicLineTopologyBuilder.buildDynamicLines(
                near,
                (trackId, posA, posB) -> rPerM * Math.abs(posB - posA)
        );

        List<Device<Real>> linesFar = DynamicLineTopologyBuilder.buildDynamicLines(
                far,
                (trackId, posA, posB) -> rPerM * Math.abs(posB - posA)
        );

        assertEquals(2, linesNear.size());
        assertEquals(2, linesFar.size());

        DcLine n0 = (DcLine) linesNear.get(0);
        DcLine n1 = (DcLine) linesNear.get(1);

        DcLine f0 = (DcLine) linesFar.get(0);
        DcLine f1 = (DcLine) linesFar.get(1);

        // near: 0->200 (200m), 200->1000 (800m)
        assertEquals(200 * rPerM, n0.getResistance().asDouble(), EPS);
        assertEquals(800 * rPerM, n1.getResistance().asDouble(), EPS);

        // far: 0->800 (800m), 800->1000 (200m)
        assertEquals(800 * rPerM, f0.getResistance().asDouble(), EPS);
        assertEquals(200 * rPerM, f1.getResistance().asDouble(), EPS);
    }

    @Test
    public void does_not_connect_nodes_across_different_tracks() {
        double rPerM = 0.01;

        // Track 1 has two nodes => 1 line
        // Track 2 has three nodes => 2 lines
        List<DynamicLineTopologyBuilder.NodePos> nodes = List.of(
                new DynamicLineTopologyBuilder.NodePos("10", 1, 0),
                new DynamicLineTopologyBuilder.NodePos("99", 1, 500),

                new DynamicLineTopologyBuilder.NodePos("30", 2, 0),
                new DynamicLineTopologyBuilder.NodePos("31", 2, 100),
                new DynamicLineTopologyBuilder.NodePos("32", 2, 200)
        );

        List<Device<Real>> lines = DynamicLineTopologyBuilder.buildDynamicLines(
                nodes,
                (trackId, posA, posB) -> rPerM * Math.abs(posB - posA)
        );

        assertEquals(3, lines.size());

        // Ensure no line connects track1 nodeIds (10,99) with track2 nodeIds (30,31,32)
        for (Device<Real> d : lines) {
            DcLine l = (DcLine) d;
            String a = l.getFromNode();
            String b = l.getToNode();

//            boolean aTrack2 = (a == 30 || a == 31 || a == 32);
//            boolean bTrack2 = (b == 30 || b == 31 || b == 32);
            boolean aTrack1 = (a.equals("10") || a.equals("99"));
            boolean bTrack1 = (b.equals("10") || b.equals("99"));

            boolean aTrack2 = (a.equals("30") || a.equals("31") || a.equals("32"));
            boolean bTrack2 = (b.equals("30") || b.equals("31") || b.equals("32"));

            assertFalse("Found cross-track line: " + a + "<->" + b,
                    (aTrack1 && bTrack2) || (aTrack2 && bTrack1));
        }
    }

    private static void assertEndpoints(DcLine l, int n1, int n2) {
        String a = l.getFromNode();
        String b = l.getToNode();
//        boolean ok = (a == n1 && b == n2) || (a == n2 && b == n1);
        boolean ok = (a.equals(n1) && b.equals(n2)) || (a.equals(n2) && b.equals((n1)));
        assertTrue("Unexpected endpoints: " + a + "<->" + b + ", expected " + n1 + "<->" + n2, ok);
    }
}
