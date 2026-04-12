package org.dcsim.unit.electric;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.dcsim.electric.*;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class DynamicTopology3S1TAnchorNearSubstationTest {

    private static final String GROUND = "GROUND";

    @Ignore("Temporarily disabled during C1 delivery. Covered by new C1-focused tests.")
    @Test
    public void anchor_just_before_SS2_should_attach_on_left_side() throws Exception {
        GridModel<?> model = load3S1T();

        Node<?> anchor = model.nodeOrThrow("99");
        anchor.setPositionM(1499);

        assertExpectedPairs(buildDynLines(model), setOfPairs(
                pair("1", "99"),
                pair("2", "99"),
                pair("2", "3")
        ));
    }

    @Ignore("Temporarily disabled during C1 delivery. Covered by new C1-focused tests.")
    @Test
    public void anchor_just_after_SS2_should_attach_on_right_side() throws Exception {
        GridModel<?> model = load3S1T();

        Node<?> anchor = model.nodeOrThrow("99");
        anchor.setPositionM(1501);

        assertExpectedPairs(buildDynLines(model), setOfPairs(
                pair("1", "2"),
                pair("2", "99"),
                pair("3", "99")
        ));
    }

    @Ignore("Temporarily disabled during C1 delivery. Covered by new C1-focused tests.")
    @Test
    public void anchor_near_SS2_should_never_create_zero_resistance_segments() throws Exception {
        GridModel<?> model = load3S1T();

        Node<?> anchor = model.nodeOrThrow("99");
        anchor.setPositionM(1499);

        List<DcLine> lines = buildDynLines(model);
        assertEquals(3, lines.size());

        for (DcLine l : lines) {
            assertTrue("Line resistance must be strictly positive", l.getResistance().asDouble() > 0.0);
        }
    }

    // ---- helpers ----

    private static GridModel<?> load3S1T() throws Exception {
        File f = new File("project/3subs1train/scenario1/application.conf");
        assertTrue("Missing scenario file: " + f.getAbsolutePath(), f.exists());

        Config cfg = ConfigFactory.parseFileAnySyntax(f, ConfigParseOptions.defaults().setAllowMissing(false))
                .resolve();

        GridModelLoader loader = new GridModelLoader();
        GridModel<?> model = loader.load(cfg);

        assertNotNull(model.nodeOrThrow("99"));
        return model;
    }

    private static List<DcLine> buildDynLines(GridModel<?> model) {
        List<DynamicLineTopologyBuilder.NodePos> nodePos = new ArrayList<>();

        for (Node<?> n : model.getNodes()) {
            if (GROUND.equals(n.getNode_id())) continue;

            nodePos.add(new DynamicLineTopologyBuilder.NodePos(
                    n.getNode_id(),
                    n.getTrackId(),
                    n.getPositionM()
            ));
        }

        return DynamicLineTopologyBuilder.buildDynamicLines(
                nodePos,
                (trackId, a, b) -> Math.max(1e-9, Math.abs(b - a))
        ).stream().map(d -> (DcLine) d).collect(Collectors.toList());
    }

    private static void assertExpectedPairs(List<DcLine> lines, Set<String> expectedPairs) {
        Set<String> actual = lines.stream()
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