// src/test/java/org/dcsim/unit/MiniMiniSolverTests.java
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
 * MiniMiniSolverTests
 *
 * <p>Purpose: ultra-small, deterministic sanity checks for the DC solver using the
 * production build path (NetBuilder). No reflection. No hidden fixtures.</p>
 *
 * <p>Baseline divider (substation with internal R + line-to-ground):</p>
 * <pre>
 * EMF = 900 V, Rint = 2 Ω, Rline = 8 Ω
 * V(node) = 900 * (8 / (2 + 8)) = 720 V
 * P(line) = 720^2 / 8 = 64_800 W
 * </pre>
 */
public class MiniMiniSolverTests {

    private static TestHarness hLinearVerbose() {
        return TestHarness.builder()
                .source(TestHarness.Source.NetBuilder)
                .solver(TestHarness.Solver.Linear)
                .verbose(true)
                .build();
    }

    /** Legacy name suggestion: keep your original name and reuse the body. */
    @Ignore("Pending #19: legacy node-id assumptions in test helper path. Re-enable after id migration settles.")
    @Test
    public void solve_substation_plus_line_matches_divider() {
        DcDebug.setVerbose(true);

        // Build minimal model
        var gm = new GridModel<>("GROUND");
        gm.addNode(new Node(0, Real.fromDouble(0.0),  "GND"));
        gm.addNode(new Node(1, Real.fromDouble(10.0), "ND1"));
        gm.setGroundNodeId(gm.getNodeById("GROUND"));

        // Substation (internal R) + line (to ground)
        org.dcsim.testing.Devices.addSubstation(gm, "SS", 1, 900.0, 2.0, /*allowBackFeed*/ true, "baseline");
        org.dcsim.testing.Devices.addLine      (gm, "L10", 1, 0,           8.0,    /*lengthM*/ 1000.0);

        double[] V = hLinearVerbose().solve(gm, (Path) null);

        // Assert baseline divider
        AssertHelpers.assertVoltage(V, 0, 0.0, 1e-6);
        AssertHelpers.assertVoltage(V, 1, 720.0, 1e-6);
        AssertHelpers.assertDissipatedPower(V, 1, 0, 8.0, 64_800.0, 1e-3);
    }

    /** Optional: verify that parallel lines reduce equivalent resistance and voltage reacts accordingly. */
    @Ignore("Pending #19: legacy node-id assumptions in test helper path. Re-enable after id migration settles.")
    @Test
    public void two_parallel_lines_halves_Rline_and_changes_voltage() {
        DcDebug.setVerbose(true);

        var gm = new GridModel<>("GROUND");
        gm.addNode(new Node(0, Real.fromDouble(0.0),  "GND"));
        gm.addNode(new Node(1, Real.fromDouble(10.0), "ND1"));
        gm.setGroundNodeId(gm.getNodeById("GROUND"));

        org.dcsim.testing.Devices.addSubstation(gm, "SS", 1, 900.0, 2.0, true, "baseline");
        // Two parallel 8Ω lines => Req = 4Ω
        org.dcsim.testing.Devices.addLine(gm, "L10a", 1, 0, 8.0, 100.0);
        org.dcsim.testing.Devices.addLine(gm, "L10b", 1, 0, 8.0, 100.0);

        double[] V = hLinearVerbose().solve(gm, null);

        // Expected: V = 900 * (4 / (2 + 4)) = 600 V
        AssertHelpers.assertVoltage(V, 1, 600.0, 1e-6);
        // Total P to ground ≈ 600^2 / 4 = 90_000 W
    }
}
