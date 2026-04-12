package org.dcsim.unit;

import org.dcsim.electric.GridModel;
import org.dcsim.electric.Node;
import org.dcsim.math.Real;
import org.dcsim.solver.impl.DcDebug;
import org.dcsim.testing.AssertHelpers;
import org.dcsim.testing.TestHarness;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


// Migration (#19): NetBuilderMiniTests still uses int node ids. Re-enable after test helpers and fixtures are migrated to String node_id."

///**
// * NetBuilderMiniTests
// *
// * <p>Purpose: a minimal, deterministic smoke test that validates NetBuilder's
// * ability to build a network from a tiny {@link GridModel} consistently.
// *
// * <p>Scope:
// * <ul>
// *   <li>No reflection, no hidden fixtures.</li>
// *   <li>Verifies ID mapping, ground node handling, and that a simple solve completes
// *       yielding the expected divider voltage (indirectly proving stamping order is sane).</li>
// * </ul>
// */
//public class NetBuilderMiniTests {
//
//    private static TestHarness hLinearVerbose() {
//        return TestHarness.builder()
//                .source(TestHarness.Source.NetBuilder)
//                .solver(TestHarness.Solver.Linear)
//                .verbose(true)
//                .build();
//    }
//
//    /**
//     * Smoke: NetBuilder builds a minimal network deterministically, with correct ground handling
//     * and node ID mapping. Also solves the divider to the expected voltage (720 V).
//     */
//    @Test
//    public void smoke_builds_minimal_net_deterministically() {
//        DcDebug.setVerbose(true);
//
//        // Build a tiny, explicit GridModel (ground + 1 node)
//        var gm = new GridModel<>(0);
//        gm.addNode(new Node(0, Real.fromDouble(0.0), "GND"));
//        gm.addNode(new Node(1, Real.fromDouble(10.0), "ND1"));
//        gm.setGroundNodeId(gm.getNodeById(0));
//
//        // Devices: Thevenin substation + line to ground (Rline = 8Ω)
//        org.dcsim.testing.Devices.addSubstation(gm, "SS", 1, 900.0, 2.0, /*allowBackFeed*/ true, "baseline");
//        org.dcsim.testing.Devices.addLine(gm, "L10", 1, 0, 8.0, /*lengthM*/ 1000.0);
//
//        // Determinism on the input model
//        List<String> ids = gm.getNodeIds();
//        assertEquals(List.of(0, 1), ids);
//        assertEquals(0, gm.getGroundNodeId());
//
//        // Substation-id ska finnas bland devices
//        assertTrue(gm.getDeviceIds().contains("SS"));
//
//        // Linjen verifieras via getLines() (vissa stackar exponerar inte linjer i getDeviceIds)
//        assertEquals("Exactly one line expected", 1, gm.getLines().size());
//        // Om din Line har getId(): assertEquals("L10", gm.getLines().get(0).getId());
//        // Run through the production path (NetBuilder + Linear solver)
//        double[] V = hLinearVerbose().solve(gm, null);
//
//        // Divider baseline:
//        // EMF = 900 V, Rint = 2 Ω, Rline = 8 Ω => V(1) = 900 * 8 / (2+8) = 720 V
//        AssertHelpers.assertVoltage(V, 0, 0.0, 1e-6);
//        AssertHelpers.assertVoltage(V, 1, 720.0, 1e-6);
//    }
//}
