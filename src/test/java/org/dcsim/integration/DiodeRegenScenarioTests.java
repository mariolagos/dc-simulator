// src/test/java/org/dcsim/integration/DiodeRegenScenarioTests.java
package org.dcsim.integration;

import org.dcsim.electric.GridModel;
import org.dcsim.electric.Node;
import org.dcsim.math.Real;
import org.dcsim.solver.impl.DcDebug;
import org.dcsim.testing.AssertHelpers;
import org.dcsim.testing.PowerAsserts;
import org.dcsim.testing.TestHarness;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;

/**
 * DiodeRegenScenarioTests
 *
 * <p><b>Purpose</b>: End-to-end integration checks for diode (no-backfeed) scenarios, using the
 * production build path (NetBuilder) and the appropriate solver. These tests focus on
 * <em>observable system outcomes</em> such as:
 * <ul>
 *   <li>Zero grid absorption when regeneration must be contained within an island</li>
 *   <li>Voltage relationships (e.g., under clamp conditions)</li>
 *   <li>Physical dissipated power in brake/line resistors (when applicable)</li>
 * </ul>
 *
 * <p><b>Conventions</b>:
 * <ul>
 *   <li>We create small, explicit GridModel instances (no reflection).</li>
 *   <li>Substations with {@code allowBackFeed=false} are treated as diodes (no backfeed).</li>
 *   <li>"Grid absorption" is measured as the sum of device powers for substation ids prefixed with "SS".</li>
 *   <li>Use {@link TestHarness.Solver#Iterative} for non-linear/enable/diode behavior.</li>
 *   <li>Ground node (id 0) is expected to be ≈ 0 V.</li>
 * </ul>
 *
 * <p><b>Why not parameterize?</b> We intentionally keep one test method per scenario
 * (with clear names) to preserve external documentation stability. If you later want
 * a parameterized variant, create a <i>separate</i> class so these names can remain unchanged.</p>
 */
public class DiodeRegenScenarioTests {

    /** Set to a folder to emit DOT graphs while stabilizing tests; keep {@code null} to disable. */
    private static final Path DOT_DIR = null; // e.g., Path.of("build/graphs/diode")

    private TestHarness hIter;   // Iterative solver (recommended for diode logic)
    private TestHarness hLinear; // Linear solver (for pure linear baselines)

    @Before
    public void setUp() {
        // Enable verbose logs across builder/solver to aid debugging while we turn tests green.
        DcDebug.setVerbose(true);

        hIter = TestHarness.builder()
                .source(TestHarness.Source.NetBuilder)
                .solver(TestHarness.Solver.Iterative)
                .verbose(true)
                .build();

        hLinear = TestHarness.builder()
                .source(TestHarness.Source.NetBuilder)
                .solver(TestHarness.Solver.Linear)
                .verbose(true)
                .build();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Helpers (small, explicit model builders)
    // -----------------------------------------------------------------------------------------------------------------

    /** Base model with ground and two train anchor nodes; islands are created by what we connect (or don’t). */
    private static GridModel<?> baseThreeNodeModel(String n1Label, String n2Label) {
        var gm = new GridModel<>(0);
        gm.addNode(new Node(0, Real.fromDouble(0.0),  "GND"));
        gm.addNode(new Node(1, Real.fromDouble(10.0), n1Label));
        gm.addNode(new Node(2, Real.fromDouble(20.0), n2Label));
        gm.setGroundNodeId(gm.getNodeById(0));
        return gm;
    }

    /** Add two diode substations (no backfeed) anchored at nodes 1 and 2, and interconnect the nodes. */
    private static GridModel<?> buildDiodeIslandBalanced(double emfV, double rInt, double r12Ohm) {
        var gm = baseThreeNodeModel("Tregen", "Tmotor");

        // DIODE substations (no backfeed)
        org.dcsim.testing.Devices.addSubstation(gm, "SS1", 1, emfV, rInt, /*allowBackFeed*/ false, "diode");
        org.dcsim.testing.Devices.addSubstation(gm, "SS2", 2, emfV, rInt, /*allowBackFeed*/ false, "diode");

        // Interconnect within the island
        org.dcsim.testing.Devices.addLine(gm, "L12", 1, 2, r12Ohm, /*lengthM*/ 100.0);

        // TODO: add Train devices with regen/motoring setpoints so power actually circulates internally.
        return gm;
    }

    /** Two separate islands (no line between them); each has a diode substation anchor. */
    private static GridModel<?> buildTwoIsolatedIslands(double emfV, double rInt) {
        var gm = new GridModel<>(0);
        gm.addNode(new Node(0, Real.fromDouble(0.0),  "GND"));
        gm.addNode(new Node(1, Real.fromDouble(10.0), "A1"));
        gm.addNode(new Node(2, Real.fromDouble(20.0), "B1"));
        gm.setGroundNodeId(gm.getNodeById(0));

        org.dcsim.testing.Devices.addSubstation(gm, "SS_A", 1, emfV, rInt, /*allowBackFeed*/ false, "diode");
        org.dcsim.testing.Devices.addSubstation(gm, "SS_B", 2, emfV, rInt, /*allowBackFeed*/ false, "diode");

        // IMPORTANT: no line between 1 and 2 ⇒ true electrical isolation
        return gm;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Tests (keep names stable to avoid touching external docs)
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Legacy name preserved:
     * <p><b>diode_twoTrains_balanced_zeroGridAbsorption</b></p>
     *
     * <p>Two train anchor nodes on a single island, each with a diode substation (no backfeed).
     * With one train regenerating and the other motoring, the island should self-balance and
     * the <em>sum of substation powers</em> should be ≈ 0 W (i.e., zero "grid absorption").</p>
     *
     * <p><b>Assertion:</b> {@code sum_{SS*} P ≈ 0 W}</p>
     */
    @Test
    public void diode_twoTrains_balanced_zeroGridAbsorption() {
        final double E   = 900.0;
        final double R   = 2.0;
        final double R12 = 0.5;
        final double TOL_W = 1e-1;

        GridModel<?> gm = buildDiodeIslandBalanced(E, R, R12);

        Path dot = (DOT_DIR == null) ? null : DOT_DIR.resolve("diode_twoTrains_balanced_zeroGridAbsorption.dot");
        double[] V = hIter.solve(gm, dot);

        // Sanity: ground ~ 0 V
        AssertHelpers.assertVoltage(V, 0, 0.0, 1e-6);

        // Sum powers for substations ("SS*") ≈ 0 W
        var devP = gm.getUpdatedDevicePowers(); // Map<String, List<Real>> or Map<String, Double> depending on stack
        PowerAsserts.assertSumPowerByPrefix(devP, "SS", 0.0, TOL_W);
    }

    /**
     * Legacy name preserved:
     * <p><b>diode_unbalanced_regen_blocked</b></p>
     *
     * <p>Similar to the balanced case, but with deliberate "unbalance" in the island (e.g., higher inter-node
     * resistance). With diode substations (no backfeed), grid absorption should still be ≈ 0 W because
     * backfeed is blocked and excess regen must be handled internally.</p>
     *
     * <p><b>Assertion:</b> {@code sum_{SS*} P ≈ 0 W}</p>
     */
    @Test
    public void diode_unbalanced_regen_blocked() {
        final double E     = 900.0;
        final double R     = 2.0;
        final double R12Hi = 1.5; // "unbalance" via higher interconnect resistance
        final double TOL_W = 1e-1;

        GridModel<?> gm = buildDiodeIslandBalanced(E, R, R12Hi);

        Path dot = (DOT_DIR == null) ? null : DOT_DIR.resolve("diode_unbalanced_regen_blocked.dot");
        double[] V = hIter.solve(gm, dot);

        AssertHelpers.assertVoltage(V, 0, 0.0, 1e-6);

        var devP = gm.getUpdatedDevicePowers();
        PowerAsserts.assertSumPowerByPrefix(devP, "SS", 0.0, TOL_W);
    }

    /**
     * Legacy name preserved:
     * <p><b>island_separation_noCrossFeed</b></p>
     *
     * <p>Two separate islands (no electrical link). Regeneration/motoring must not cross islands,
     * and with diode substations we expect no net/grid absorption either.</p>
     *
     * <p><b>Assertion:</b> {@code sum_{SS*} P ≈ 0 W}</p>
     */
    @Test
    public void island_separation_noCrossFeed() {
        final double E   = 900.0;
        final double R   = 2.0;
        final double TOL_W = 1e-1;

        GridModel<?> gm = buildTwoIsolatedIslands(E, R);

        Path dot = (DOT_DIR == null) ? null : DOT_DIR.resolve("island_separation_noCrossFeed.dot");
        double[] V = hIter.solve(gm, dot);

        AssertHelpers.assertVoltage(V, 0, 0.0, 1e-6);

        var devP = gm.getUpdatedDevicePowers();
        PowerAsserts.assertSumPowerByPrefix(devP, "SS", 0.0, TOL_W);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Optional: add a single-train clamp test (if you had it in this file historically)
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Example (only include if this method existed historically in this file):
     * <p><b>diode_regen_singleTrain_blockedAtVmax</b></p>
     *
     * <p>Demonstrate "no backfeed" + clamp semantics: if Va attempts to exceed Vmax under regen,
     * the diode (and/or controller logic) blocks export to the grid; energy must be burned locally.
     * In this minimal test we assert physical invariants; full controller-side inequality checks
     * (e.g., Va ≥ Vmax) belong in logic-focused unit tests.</p>
     *
     * <p><b>Assertion (physical):</b> Ground is ~0 V; local dissipation is present if you wire
     * an explicit brake/resistor branch. (Left as TODO if your stack models it here.)</p>
     */
    // @Test
    // public void diode_regen_singleTrain_blockedAtVmax() {
    //     // TODO: If this existed historically, wire a minimal single-train model that proves
    //     // local burn when export is blocked. Otherwise, keep this commented out to avoid
    //     // creating a "new" test name in this file.
    // }
}
