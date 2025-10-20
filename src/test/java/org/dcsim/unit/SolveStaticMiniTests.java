package org.dcsim.unit;

import org.dcsim.testing.Devices;
import org.dcsim.testing.ElectricBuilders;
import org.junit.Test;

import java.io.IOException;

/**
 * Static solve without train: substation + line to ground => voltage divider.
 */
public class SolveStaticMiniTests {

    @Test
    public void solve_substation_plus_line_matches_divider() throws IOException {
        org.dcsim.solver.impl.DcDebug.setVerbose(false);

        // Build GridModel with production API (without reflection)
        var gm = new org.dcsim.electric.GridModel<>(0);
        ElectricBuilders.ensureNode(gm, 0, 0.0, "GND");
        ElectricBuilders.ensureNode(gm, 1, 10.0, "ND1");
        ElectricBuilders.ensureGround(gm, 0);

        Devices.addSubstation(gm, "SS", /*anchor*/ 1, 900.0, 2.0, /*allowBackFeed*/ false, "divider");
        Devices.addLine(gm, "L10", 1, 0, 8.0, 1500);

        var h = org.dcsim.testing.TestHarness.builder()
                .source(org.dcsim.testing.TestHarness.Source.NetBuilder)
                .solver(org.dcsim.testing.TestHarness.Solver.Linear)
                .verbose(true)
                .build();

        double[] V = h.solve(gm, null);
        org.dcsim.testing.AssertHelpers.assertVoltage(V, 1, 720.0, 1e-6);
        org.dcsim.testing.AssertHelpers.assertVoltage(V, 0, 0.0, 1e-6);
    }
}
