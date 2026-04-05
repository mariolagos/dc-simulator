package org.dcsim.unit.electric;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.dcsim.electric.*;
import org.dcsim.math.Real;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class DynamicTopology3S1TAnchorNearSubstationTest {

    @Ignore("Temporarily disabled during C1 delivery. Covered by new C1-focused tests.")
    @Test
    public void anchor_just_before_SS2_should_attach_on_left_side() throws Exception {
        GridModel<Real> model = load3S1T();

        Node<Real> anchor = model.nodeOrThrow(99);
        anchor.setPositionM(1499);

        assertExpectedPairs(buildDynLines(model), setOfPairs(
                pair(1, 99),
                pair(2, 99),
                pair(2, 3)
        ));
    }

    @Ignore("Temporarily disabled during C1 delivery. Covered by new C1-focused tests.")
    @Test
    public void anchor_just_after_SS2_should_attach_on_right_side() throws Exception {
        GridModel<Real> model = load3S1T();

        Node<Real> anchor = model.nodeOrThrow(99);
        anchor.setPositionM(1501);

        assertExpectedPairs(buildDynLines(model), setOfPairs(
                pair(1, 2),
                pair(2, 99),
                pair(3, 99)
        ));
    }

    /**
     * Optional (but useful): prove we are not producing any zero-length / zero-resistance segment.
     */
    @Ignore("Temporarily disabled during C1 delivery. Covered by new C1-focused tests.")
    @Test
    public void anchor_near_SS2_should_never_create_zero_resistance_segments() throws Exception {
        GridModel<Real> model = load3S1T();

        Node<Real> anchor = model.nodeOrThrow(99);
        anchor.setPositionM(1499);

        List<DcLine> lines = buildDynLines(model);
        assertEquals(3, lines.size());

        for (DcLine l : lines) {
            assertTrue("Line resistance must be strictly positive", l.getResistance().asDouble() > 0.0);
        }
    }

    // ---- helpers (same as previous tests) ----

    private static GridModel<Real> load3S1T() throws Exception {
        File f = new File("project/3subs1train/scenario1/application.conf");
        assertTrue("Missing scenario file: " + f.getAbsolutePath(), f.exists());

        Config cfg = ConfigFactory.parseFileAnySyntax(f, ConfigParseOptions.defaults().setAllowMissing(false))
                .resolve();

        @SuppressWarnings("unchecked")
                GridModelLoader loader = new GridModelLoader();
        GridModel<Real> model = loader.load(cfg);
        assertNotNull(model.nodeOrThrow(99));
        return model;
    }

    private static List<DcLine> buildDynLines(GridModel<Real> model) {
        List<DynamicLineTopologyBuilder.NodePos> nodePos = new ArrayList<>();
        for (Node<Real> n : model.getNodes()) {
            if (n.get_internal_id() == model.getGroundNodeId()) continue;
            nodePos.add(new DynamicLineTopologyBuilder.NodePos(
                    n.get_internal_id(), n.getTrackId(), n.getPositionM()));
        }

        return DynamicLineTopologyBuilder.buildDynamicLines(
                nodePos,
                (trackId, a, b) -> Math.max(1e-9, Math.abs(b - a))
        ).stream().map(d -> (DcLine) d).collect(Collectors.toList());
    }

    private static void assertExpectedPairs(List<DcLine> lines, Set<Long> expectedPairs) {
        Set<Long> actual = lines.stream()
                .peek(l -> {
                    assertNotEquals("Dynamic line must not connect to ground", 0, l.getFromNode());
                    assertNotEquals("Dynamic line must not connect to ground", 0, l.getToNode());
                })
                .map(l -> pair(l.getFromNode(), l.getToNode()))
                .collect(Collectors.toSet());

        assertEquals("Unexpected dynamic line endpoint set", expectedPairs, actual);
    }

    private static long pair(int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return (((long) lo) << 32) ^ (hi & 0xffffffffL);
    }

    private static Set<Long> setOfPairs(long... ps) {
        Set<Long> s = new LinkedHashSet<>();
        for (long p : ps) s.add(p);
        return s;
    }
}
