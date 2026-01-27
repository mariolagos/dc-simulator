package org.dcsim.unit.electric;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.dcsim.electric.*;
import org.dcsim.math.Real;
import org.junit.Test;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class DynamicTopology3S1TAnchorOnSubstationTest {

    @Test
    public void anchor_exactly_on_substation_position_produces_deterministic_topology() throws Exception {
        GridModel<Real> model = load3S1T();

        // SS2 is at 1500 m in the baseline scenario
        Node<Real> anchor = model.nodeOrThrow(99);
        anchor.setPositionM(1500);

        List<DcLine> lines = buildDynLines(model);

        // Expect exactly 3 dynamic lines (N-1)
        assertEquals("Unexpected number of dynamic lines", 3, lines.size());

        Set<Long> pairs = lines.stream()
                .map(l -> pair(l.getFromNode(), l.getToNode()))
                .collect(Collectors.toSet());

        // One deterministic, acceptable outcome:
        // SS1 - SS2 - anchor - SS3
        assertEquals(setOfPairs(
                pair(1, 2),
                pair(2, 99),
                pair(99, 3)
        ), pairs);

        // Hard invariants
        for (DcLine l : lines) {
            assertTrue("Line resistance must be strictly positive", l.getResistance().asDouble() > 0.0);
            assertNotEquals("Dynamic line must not connect to ground", 0, l.getFromNode());
            assertNotEquals("Dynamic line must not connect to ground", 0, l.getToNode());
        }
    }

    // ---- helpers (identical to test #1 on purpose) ----

    private static GridModel<Real> load3S1T() throws Exception {
        File f = new File("project/3subs1train/scenario1/application.conf");
        assertTrue("Missing scenario file: " + f.getAbsolutePath(), f.exists());

        Config cfg = ConfigFactory.parseFileAnySyntax(f, ConfigParseOptions.defaults().setAllowMissing(false))
                .resolve();

        @SuppressWarnings("unchecked")
        GridModel<Real> model = (GridModel<Real>) GridModelLoader.load(cfg);

        assertNotNull(model.nodeOrThrow(99));
        return model;
    }

    private static List<DcLine> buildDynLines(GridModel<Real> model) {
        List<DynamicLineTopologyBuilder.NodePos> nodePos = new ArrayList<>();
        for (Node<Real> n : model.getNodes()) {
            if (n.getId() == model.getGroundNodeId()) continue;
            nodePos.add(new DynamicLineTopologyBuilder.NodePos(
                    n.getId(), n.getTrackId(), n.getPositionM()));
        }

        return DynamicLineTopologyBuilder.buildDynamicLines(
                nodePos,
                (trackId, a, b) -> Math.max(1e-9, Math.abs(b - a))
        ).stream().map(d -> (DcLine) d).collect(Collectors.toList());
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
