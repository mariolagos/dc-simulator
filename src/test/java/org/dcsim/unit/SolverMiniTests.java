package org.dcsim.unit;

import org.dcsim.electric.GridModel;
import org.dcsim.electric.Node;
import org.dcsim.math.Real;
import org.dcsim.solver.impl.DcDebug;
import org.dcsim.testing.AssertHelpers;
import org.dcsim.testing.TestHarness;
import org.junit.Test;

/**
 * SolverMiniTests
 *
 * <p>Purpose: tiny, deterministic sanity checks for the DC solver behavior,
 * executed via the production path (NetBuilder + chosen Solver).
 *
 * <p>Design:
 * <ul>
 *   <li>Keep one crisp smoke test that does not duplicate other suites.</li>
 *   <li>Focus on observable physics (voltages/loads) rather than internal matrices.</li>
 * </ul>
 */
public class SolverMiniTests {

    private static TestHarness hLinearVerbose() {
        return TestHarness.builder()
                .source(TestHarness.Source.NetBuilder)
                .solver(TestHarness.Solver.Linear)
                .verbose(true)
                .build();
    }

    /**
     * Smoke: linear divider solves to a precise, closed-form value (720 V).
     * This is the smallest "red light" should the solver regress.
     */
    @Test
    public void smoke_linear_divider_solution_ok() {
        DcDebug.setVerbose(true);

        // Minimal model
        var gm = new GridModel<>(0);
        gm.addNode(new Node(0, Real.fromDouble(0.0),  "GND"));
        gm.addNode(new Node(1, Real.fromDouble(10.0), "ND1"));
        gm.setGroundNodeId(gm.getNodeById(0));

        // Thevenin source + load to ground
        org.dcsim.testing.Devices.addSubstation(gm, "SS", 1, 900.0, 2.0, /*allowBackFeed*/ true, "baseline");
        org.dcsim.testing.Devices.addLine      (gm, "L10", 1, 0, 8.0, /*lengthM*/ 1000.0);

        double[] V = hLinearVerbose().solve(gm, null);

        // Expected divider result
        AssertHelpers.assertVoltage(V, 0, 0.0, 1e-6);
        AssertHelpers.assertVoltage(V, 1, 720.0, 1e-6);

        // And the corresponding dissipated power in the load branch: P = 720^2 / 8 = 64_800 W
        AssertHelpers.assertDissipatedPower(V, 1, 0, 8.0, 64_800.0, 1e-3);
    }
}
