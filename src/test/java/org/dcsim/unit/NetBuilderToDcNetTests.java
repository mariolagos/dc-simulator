package org.dcsim.unit;

import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.LineData;
import org.dcsim.solver.api.SubstationData;
import org.dcsim.solver.api.TrainData;
import org.dcsim.solver.fixtures.NetFixtures;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Verifies that DcNet produced by NetFixtures has the expected compact indexing,
 * ground selection, and immutable collections. This replaces the old NetBuilderMiniTests.
 */
public class NetBuilderToDcNetTests {

    @Test
    public void threeBusBackbone_basicTopology_ok() {
        final double emf = 800.0;
        final double rint = 0.01;
        final double r12  = 0.10;
        final double r23  = 0.10;
        final boolean allowBackfeed = false;

        DcNet net = NetFixtures.threeBusBackboneBackfeed(emf, rint, r12, r23, allowBackfeed);

        // node and ground basics
        assertEquals(4, net.n());
        assertEquals(0, net.groundIndex());

        // nodeIds and index map must be compact and consistent (id == index in fixtures)
        List<Integer> nodeIds = net.nodeIds();
        Map<Integer, Integer> idx = net.indexById();
        assertEquals(List.of(0, 1, 2, 3), nodeIds);
        assertEquals(Integer.valueOf(0), idx.get(0));
        assertEquals(Integer.valueOf(1), idx.get(1));
        assertEquals(Integer.valueOf(2), idx.get(2));
        assertEquals(Integer.valueOf(3), idx.get(3));

        // lines and substations presence
        assertEquals(2, net.lines().size());
        assertEquals(2, net.substations().size());
        assertEquals(0, net.trains().size());

        // lines use compact indices and expected endpoints
        LineData L12 = net.lines().get(0);
        LineData L23 = net.lines().get(1);
        assertBetweenInclusive(L12.a(), 0, 3);
        assertBetweenInclusive(L12.b(), 0, 3);
        assertBetweenInclusive(L23.a(), 0, 3);
        assertBetweenInclusive(L23.b(), 0, 3);

        // substations are 1->0 and 3->0 with configured EMF and Rint
        SubstationData SS1 = net.substations().get(0);
        SubstationData SS2 = net.substations().get(1);
        assertEquals(1, SS1.a());
        assertEquals(0, SS1.b());
        assertEquals(emf, SS1.emf_V(), 1e-12);
        assertEquals(rint, SS1.rint_ohm(), 1e-12);
        assertEquals(allowBackfeed, SS1.allowBackfeed());

        assertEquals(3, SS2.a());
        assertEquals(0, SS2.b());
        assertEquals(emf, SS2.emf_V(), 1e-12);
        assertEquals(rint, SS2.rint_ohm(), 1e-12);
        assertEquals(allowBackfeed, SS2.allowBackfeed());
    }

    @Test
    public void oneRegenAt2toGround_trainMapping_ok() {
        final double regenW = 100_000.0;
        DcNet net = NetFixtures.oneRegenAt2toGround(regenW);

        assertEquals(4, net.n());
        assertEquals(0, net.groundIndex());

        // two feeders (diodes) and one train
        assertEquals(2, net.substations().size());
        assertEquals(1, net.trains().size());

        TrainData tr = net.trains().get(0);
        assertEquals("Tregen", tr.id());
        assertEquals(2, tr.a());
        assertEquals(0, tr.b());
        assertEquals(-regenW, tr.req_W(), 1e-9);
        assertEquals(NetFixtures.I_MAX_HI, tr.imax_A(), 1e-12);
        assertEquals(NetFixtures.V_MIN, tr.vmin_V(), 1e-12);
        assertEquals(NetFixtures.V_MAX, tr.vmax_V(), 1e-12);
    }

    @Test
    public void immutability_contract_enforced() {
        DcNet net = NetFixtures.threeBusDiodeBackbone();

        // lists returned by DcNet should be unmodifiable
        expectUnsupported(() -> net.nodeIds().add(99));
        expectUnsupported(() -> net.lines().clear());
        expectUnsupported(() -> net.substations().add(
                new SubstationData("X", 1, 0, 800.0, 0.01, false)));
        expectUnsupported(() -> net.trains().add(
                new TrainData("T", 2, 0, -1.0, 1000.0, 500.0, 1000.0)));

        // indexById map should be unmodifiable
        expectUnsupported(() -> net.indexById().put(9, 9));
    }

    @Test
    public void indices_areCompact_and_consistent() {
        DcNet net = NetFixtures.threeBusDiodeBackbone();

        // every endpoint is a compact index in [0..n-1]
        for (LineData L : net.lines()) {
            assertBetweenInclusive(L.a(), 0, net.n() - 1);
            assertBetweenInclusive(L.b(), 0, net.n() - 1);
        }
        for (SubstationData ss : net.substations()) {
            assertBetweenInclusive(ss.a(), 0, net.n() - 1);
            assertBetweenInclusive(ss.b(), 0, net.n() - 1);
        }
    }

    // helpers

    private static void assertBetweenInclusive(int v, int lo, int hi) {
        assertTrue("value " + v + " not in [" + lo + "," + hi + "]", v >= lo && v <= hi);
    }

    private static void expectUnsupported(Runnable r) {
        try {
            r.run();
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ok) {
            // expected
        }
    }
}
