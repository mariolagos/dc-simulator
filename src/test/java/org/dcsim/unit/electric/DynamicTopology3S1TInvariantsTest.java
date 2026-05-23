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

public class DynamicTopology3S1TInvariantsTest {

    private static final String GROUND = "GROUND";

    @Ignore("Temporarily disabled during C1 delivery. Covered by new C1-focused tests.")
    @Test
    public void threeSubsOneTrain_builds_consecutive_dynamic_lines_and_includes_anchor() throws Exception {
        GridModel<?> model = load3S1T();

        // Baseline positions from config:
        // 1 @ 0m, 2 @ 1500m, 3 @ 3000m, 99 @ 3100m (all track 1)
        assertExpectedPairs(buildDynLines(model), setOfPairs(
                pair("1", "2"),
                pair("2", "3"),
                pair("3", "99")
        ));

        // Move anchor to be between SS1 and SS2: 2000m
        Node<?> anchor = model.nodeOrThrow("99");
        anchor.setPositionM(2000);

        // Now the dynamic split must be: 1-2, 2-99, 99-3
        assertExpectedPairs(buildDynLines(model), setOfPairs(
                pair("1", "2"),
                pair("2", "99"),
                pair("99", "3")
        ));
    }

    // ---- helpers ----

    private static GridModel<?> load3S1T() throws Exception {
        File f = new File("project/3subs1train/scenario1/application.conf");
        assertTrue("Missing scenario file: " + f.getAbsolutePath(), f.exists());

        Config cfg = ConfigFactory.parseFileAnySyntax(f, ConfigParseOptions.defaults().setAllowMissing(false))
                .resolve();

        GridModelLoader loader = new GridModelLoader();
        GridModel<?> model = loader.load(cfg);

        // Topology invariant: never connect ground in dynamic lines.
        assertNotNull(model.nodeOrThrow(model.getGroundNodeId()));

        // Sanity: anchor exists
        assertNotNull("Anchor node 99 must exist in grid.nodes[]", model.nodeOrThrow("99"));

        return model;
    }

    private static List<Device<Real>> buildDynLines(GridModel<?> model) {
        List<DynamicLineTopologyBuilder.NodePos> nodePos = new ArrayList<>();

        for (Node<?> n : model.getNodes()) {
            if (GROUND.equals(n.getNode_id())) continue;

            nodePos.add(new DynamicLineTopologyBuilder.NodePos(
                    n.getNode_id(),
                    n.getTrackId(),
                    n.getPositionM()
            ));
        }

        // R is irrelevant for topology invariants; just make it deterministic & > 0
        return DynamicLineTopologyBuilder.buildDynamicLines(
                nodePos,
                (trackId, a, b) -> Math.max(1e-9, Math.abs(b - a))
        );
    }

    private static void assertExpectedPairs(List<Device<Real>> lines, Set<String> expectedPairs) {
        Set<String> actual = lines.stream()
                .map(d -> (DcLine) d)
                .peek(l -> {
                    assertNotEquals("Dynamic line must not connect to ground", GROUND, l.getFromNode());
                    assertNotEquals("Dynamic line must not connect to ground", GROUND, l.getToNode());
                })
                .map(l -> pair(l.getFromNode(), l.getToNode()))
                .collect(Collectors.toSet());

        assertEquals("Unexpected dynamic line endpoint set", expectedPairs, actual);
    }

    private static String pair(String a, String b) {
        return (a.compareTo(b) <= 0) ? a + "|" + b : b + "|" + a;
    }

    private static Set<String> setOfPairs(String... ps) {
        return new LinkedHashSet<>(Arrays.asList(ps));
    }
}