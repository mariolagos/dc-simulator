// src/test/java/org/dcsim/unit/StampMiniTests.java
package org.dcsim.unit;

import org.dcsim.electric.GridModel;
import org.dcsim.electric.Node;
import org.dcsim.math.Real;
import org.dcsim.solver.impl.DcDebug;
import org.dcsim.testing.AssertHelpers;
import org.dcsim.testing.TestHarness;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.file.Path;

/**
 * StampMiniTests
 *
 * <p>Purpose: minimal topologies that implicitly validate matrix stamping (G/J) via observable
 * results (voltages/powers). We avoid peeking at internals; if stamping is wrong, these
 * invariants will fail.</p>
 */
public class StampMiniTests {

    static String GROUND = "0";
    static String ND1 = "1";
    static String MID = "1";
    static int ground_internal_id = 0;
    static int nd1_internal_id = 1;
    static int mid_internal_id = 1;

    private static TestHarness hLinearVerbose() {
        return TestHarness.builder()
                .source(TestHarness.Source.NetBuilder)
                .solver(TestHarness.Solver.Linear)
                .verbose(true)
                .build();
    }

    /** Series lines: 1--R1--X--R2--0 should match Req = R1 + R2. */
    @Ignore("Pending #19: legacy node-id assumptions in test helper path. Re-enable after id migration settles.")
    @Test
    public void series_lines_equivalent_resistance_matches_sum() {


        DcDebug.setVerbose(true);

        var gm = new GridModel<>(GROUND);
        gm.addNode(new Node(ground_internal_id, Real.fromDouble(0.0),  GROUND));
        gm.addNode(new Node(nd1_internal_id, Real.fromDouble(10.0), ND1));
        gm.addNode(new Node(mid_internal_id, Real.fromDouble(20.0), MID));
        gm.setGroundNodeId(gm.getNodeById(GROUND));

        org.dcsim.testing.Devices.addSubstation(gm, "SS", 1, 900.0, 2.0, true, "baseline");
        org.dcsim.testing.Devices.addLine(gm, "L1", 1, 2, 3.0, 100.0); // R1=3Ω
        org.dcsim.testing.Devices.addLine(gm, "L2", 2, 0, 5.0, 100.0); // R2=5Ω

        // Req = 3 + 5 = 8Ω => V1 = 900 * (8 / (2 + 8)) = 720 V
        double[] V = hLinearVerbose().solve(gm, (Path) null);
        AssertHelpers.assertVoltage(V, 1, 720.0, 1e-6);
    }

    /** Open circuit: no path to ground => node should float at 0 (due to ground clamp) and no power dissipated. */
    @Ignore("Pending #19: legacy node-id assumptions in test helper path. Re-enable after id migration settles.")
    @Test
    public void open_circuit_has_no_current_flow() {
        DcDebug.setVerbose(true);

        var gm = new GridModel<>(GROUND);
        gm.addNode(new Node(0, Real.fromDouble(0.0),  "GND"));
        gm.addNode(new Node(1, Real.fromDouble(10.0), "ND1"));
        gm.setGroundNodeId(gm.getNodeById(GROUND));

        // Thevenin source to ground: EMF in series with Rint; no load path => I = 0 A.
        org.dcsim.testing.Devices.addSubstation(gm, "SS", 1,
                /*EMF*/ 900.0, /*Rint*/ 2.0, /*allowBackFeed*/ true, "open-circuit");

        double[] V = hLinearVerbose().solve(gm, null);

        // Ground ~ 0 V
        AssertHelpers.assertVoltage(V, 0, 0.0, 1e-6);

        // Open-circuit: no current ⇒ no drop on Rint ⇒ node equals EMF
        AssertHelpers.assertVoltage(V, 1, 900.0, 1e-6);

        // Optional: if device powers are exposed, they should be ≈ 0 W (no current).
        // var devP = gm.getUpdatedDevicePowers();
        // org.dcsim.testing.PowerAsserts.assertSumPowerByPrefix(devP, "SS", 0.0, 1e-6);
    }
}
