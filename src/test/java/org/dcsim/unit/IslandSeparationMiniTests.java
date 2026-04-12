// src/test/java/org/dcsim/unit/IslandSeparationMiniTests.java
package org.dcsim.unit;

import org.dcsim.electric.GridModel;
import org.dcsim.electric.Node;
import org.dcsim.math.Real;
import org.dcsim.solver.impl.DcDebug;
import org.dcsim.testing.AssertHelpers;
import org.dcsim.testing.PowerAsserts;
import org.dcsim.testing.TestHarness;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.file.Path;

/**
 * IslandSeparationMiniTests
 *
 * <p><b>Purpose</b> — Verify that two unconnected islands behave independently:
 * no cross-feed and no unintended "grid absorption". We exercise the production
 * build path (NetBuilder) and keep the topology tiny and explicit (no reflection).</p>
 *
 * <p><b>Modeling conventions</b></p>
 * <ul>
 *   <li>Node 0 is ground and should settle ~0 V.</li>
 *   <li>We use simple substation + resistor-to-ground per island to get a stable,
 *       solvable DC network without pulling in full train devices.</li>
 *   <li>Substations are created with <code>allowBackFeed=false</code> to emulate diode behavior
 *       (no backfeed to an external grid).</li>
 *   <li>"Grid absorption" is measured as the sum of device powers for ids starting with <code>"SS"</code>.</li>
 * </ul>
 *
 * <p><b>Baseline divider math (per island)</b></p>
 * <pre>
 * EMF = 900 V, Rint = 2 Ω, Rload = 8 Ω
 * V(island node) = 900 * (8 / (2 + 8)) = 720 V
 * P(load) = V^2 / Rload = 720^2 / 8 = 64_800 W
 * </pre>
 *
 * <p><b>Notes</b></p>
 * <ul>
 *   <li>These are physical mini-tests; controller semantics (e.g., Vmax clamp) belong in logic tests.</li>
 *   <li>Switch to Iterative solver when non-linear behaviors are present; Linear suffices here.</li>
 * </ul>
 */
public class IslandSeparationMiniTests {

    /** Helper: build a fresh harness on the production path with verbose logs enabled. */
    private static TestHarness harnessLinearVerbose() {
        return TestHarness.builder()
                .source(TestHarness.Source.NetBuilder)
                .solver(TestHarness.Solver.Linear)
                .verbose(true)
                .build();
    }

    /** Create a minimal two-island network: A and B are not electrically connected. */
    private static GridModel<?> buildTwoDisjointIslands(double emfV, double rInt, double rLoad) {
        var gm = new GridModel<>("GROUND");

        // Ground + two island anchor nodes
        gm.addNode(new Node(0, Real.fromDouble(0.0),  "GND"));
        gm.addNode(new Node(1, Real.fromDouble(10.0), "A1"));
        gm.addNode(new Node(2, Real.fromDouble(20.0), "B1"));
        gm.setGroundNodeId(gm.getNodeById("GROUND"));

        // Per-island diode substation (no backfeed)
        org.dcsim.testing.Devices.addSubstation(gm, "SS_A", 1, emfV, rInt, /*allowBackFeed*/ false, "diode");
        org.dcsim.testing.Devices.addSubstation(gm, "SS_B", 2, emfV, rInt, /*allowBackFeed*/ false, "diode");

        // Per-island local load (resistor to ground) to make the divider
        org.dcsim.testing.Devices.addLine(gm, "RA_to_GND", 1, 0, rLoad, /*lengthM*/ 100.0);
        org.dcsim.testing.Devices.addLine(gm, "RB_to_GND", 2, 0, rLoad, /*lengthM*/ 100.0);

        // IMPORTANT: no line between nodes 1 and 2 ⇒ true island separation
        return gm;
    }

    /**
     * Legacy name preserved from earlier suites.
     *
     * <p><b>islands_regenCannotFeedMotor_whenDisconnected</b></p>
     *
     * <p>Two islands are electrically disjoint. Any regeneration on island A cannot
     * feed a consumer on island B. In this minimal physical model we assert that each
     * island independently settles to the divider baseline and that the sum of substation
     * powers (our "grid absorption" proxy) is ≈ 0 W.</p>
     */
    @Ignore("Pending #19: legacy node-id assumptions in test helper path. Re-enable after id migration settles.")
    @Test
    public void islands_regenCannotFeedMotor_whenDisconnected() {
        DcDebug.setVerbose(true);

        final double E = 900.0;   // EMF per island
        final double Rint = 2.0;  // internal resistance per substation
        final double Rload = 8.0; // local resistor to ground per island

        var gm = buildTwoDisjointIslands(E, Rint, Rload);

        // Optional DOT export while stabilizing tests (set to null to skip file output)
        Path dot = null; // e.g., Path.of("build/graphs/islands_regenCannotFeedMotor_whenDisconnected.dot")

        var h = harnessLinearVerbose();
        double[] V = h.solve(gm, dot);

        // Ground must be ~0 V
        AssertHelpers.assertVoltage(V, 0, 0.0, 1e-6);

        // Each island behaves as its own divider → ~720 V at both island nodes
        AssertHelpers.assertVoltage(V, 1, 720.0, 1e-6);
        AssertHelpers.assertVoltage(V, 2, 720.0, 1e-6);

        // Local dissipation on each island (burned/absorbed in the island load)
        AssertHelpers.assertDissipatedPower(V, 1, 0, Rload, 64_800.0, 1e-3);
        AssertHelpers.assertDissipatedPower(V, 2, 0, Rload, 64_800.0, 1e-3);

        // "Grid absorption" proxy: sum of substation powers should be ~0 W (no external net)
        var devP = gm.getUpdatedDevicePowers(); // Map<String, List<Real>> or Map<String, Double>
        PowerAsserts.assertSumPowerByPrefix(devP, "SS", 0.0, 1e-1);
    }

    /**
     * Optional companion check (keep or remove): if we link the islands with a line,
     * they no longer behave independently; currents can flow between A and B.
     *
     * <p><b>islands_regenCanFeedMotor_whenLinked</b></p>
     *
     * <p>This test documents the contrast with the disconnected case. We still verify
     * ground and basic voltages; additional current/power splits can be asserted once
     * train/behavior elements are in place.</p>
     */
    @Ignore("Pending #19: legacy node-id assumptions in test helper path. Re-enable after id migration settles.")
    @Test
    public void islands_regenCanFeedMotor_whenLinked() {
        DcDebug.setVerbose(true);

        final double E = 900.0;
        final double Rint = 2.0;
        final double Rload = 8.0;

        var gm = buildTwoDisjointIslands(E, Rint, Rload);

        // Now link the two islands: they become one island electrically.
        org.dcsim.testing.Devices.addLine(gm, "LINK_A_B", 1, 2, /*Rlink*/ 0.5, /*lengthM*/ 100.0);

        Path dot = null; // e.g., Path.of("build/graphs/islands_regenCanFeedMotor_whenLinked.dot")

        var h = harnessLinearVerbose();
        double[] V = h.solve(gm, dot);

        // Ground ~ 0 V
        AssertHelpers.assertVoltage(V, 0, 0.0, 1e-6);

        // With symmetric parameters, both nodes still land at the same potential (~720 V),
        // but now A and B can exchange current through LINK_A_B if behaviors/devices require it.
        AssertHelpers.assertVoltage(V, 1, 720.0, 1e-6);
        AssertHelpers.assertVoltage(V, 2, 720.0, 1e-6);

        // Local dissipation still present on both branches
        AssertHelpers.assertDissipatedPower(V, 1, 0, Rload, 64_800.0, 1e-3);
        AssertHelpers.assertDissipatedPower(V, 2, 0, Rload, 64_800.0, 1e-3);

        // Grid absorption remains ~0 W (no external grid in this mini setup)
        var devP = gm.getUpdatedDevicePowers();
        PowerAsserts.assertSumPowerByPrefix(devP, "SS", 0.0, 1e-1);
    }
}
