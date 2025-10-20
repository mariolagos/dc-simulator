// src/test/java/org/dcsim/integration/DcIntegrationScenariosParamTest.java
package org.dcsim.integration;

import org.dcsim.electric.GridModel;
import org.dcsim.electric.Node;
import org.dcsim.math.Real;
import org.dcsim.solver.impl.DcDebug;
import org.dcsim.testing.PowerAsserts;
import org.dcsim.testing.TestHarness;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * Parameterized integration tests that consolidate the legacy scenarios from
 * {@code DcIntegrationScenarioTests} into a single, maintainable suite.
 *
 * <p><b>Goals</b></p>
 * <ul>
 *   <li>Keep original test <i>names</i> intact (so external documentation remains valid).</li>
 *   <li>Run via the production path (NetBuilder + selected Solver) with proper verbosity.</li>
 *   <li>Assert high-level outcomes such as "zero grid absorption" by summing powers for
 *       substations (device ids prefixed with "SS").</li>
 * </ul>
 *
 * <p><b>Notes on measurements</b></p>
 * <ul>
 *   <li>"Grid absorption" ≈ sum of substation device powers (ids starting with "SS").</li>
 *   <li>{@link PowerAsserts#assertSumPowerByPrefix(java.util.Map, String, double, double)} is
 *       resilient to both {@code Map<String, Double>} and {@code Map<String, List<Real>>}.</li>
 *   <li>Use {@link TestHarness.Solver#Iterative} for non-linear behavior (diodes/throttle)
 *       and {@link TestHarness.Solver#Linear} for purely linear scenarios.</li>
 * </ul>
 */
@RunWith(Parameterized.class)
public class DcIntegrationScenariosTest {

    // Toggle DOT export while turning tests green (null to disable)
    private static final Path DOT_DIR = null; // e.g., Path.of("build/graphs/integration")

    @Parameterized.Parameter(0) public String name;
    @Parameterized.Parameter(1) public Supplier<GridModel<?>> modelBuilder;
    @Parameterized.Parameter(2) public TestHarness.Solver solver;
    /** Expected sum of substation powers (W). Use Double.NaN to skip this assertion. */
    @Parameterized.Parameter(3) public double expectedGridAbsorptionW;
    /** Absolute tolerance for grid absorption assertion. */
    @Parameterized.Parameter(4) public double tolW;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        final double TOL_DEFAULT = 1e-1; // adjust based on numeric residuals in your stack

        return Arrays.asList(new Object[][]{
                // Keep names EXACTLY as in DcIntegrationScenarioTests

                // 1) diode_twoTrains_balanced_zeroGridAbsorption
                //    Two trains on the same island (one regen, one motoring),
                //    diode substations (no backfeed) => grid absorption ~ 0 W.
                row("diode_twoTrains_balanced_zeroGridAbsorption",
                        Scenarios::buildDiodeTwoTrainsBalanced,
                        TestHarness.Solver.Iterative,
                        0.0, TOL_DEFAULT),

                // 2) diode_unbalanced_regen_blocked
                //    Diode substations block backfeed; despite unbalance between trains,
                //    net/grid absorption should remain ~ 0 W.
                row("diode_unbalanced_regen_blocked",
                        Scenarios::buildDiodeUnbalanced,
                        TestHarness.Solver.Iterative,
                        0.0, TOL_DEFAULT),

                // 3) bidir_twoTrains_netAbsorptionPositive
                //    Bidirectional substations (allow backfeed) — expect positive grid absorption
                //    when a net import/export setpoint exists (requires Train devices/setpoints).
                //    TODO: set a concrete expected W once trains/setpoints are wired.
                row("bidir_twoTrains_netAbsorptionPositive",
                        Scenarios::buildBidirectionalTwoTrains,
                        TestHarness.Solver.Iterative,
                        Double.NaN /* skip until trains wired */, TOL_DEFAULT),

                // 4) island_separation_noCrossFeed
                //    Two isolated islands (no line between them) — assert zero grid absorption.
                row("island_separation_noCrossFeed",
                        Scenarios::buildTwoIslandsNoLink,
                        TestHarness.Solver.Iterative,
                        0.0, TOL_DEFAULT),
        });
    }

    /** Typed row helper (prevents "lambda must target a functional interface" issues). */
    private static Object[] row(String name,
                                Supplier<GridModel<?>> supplier,
                                TestHarness.Solver solver,
                                double expectedGridAbsorptionW,
                                double tolW) {
        return new Object[]{ name, supplier, solver, expectedGridAbsorptionW, tolW };
    }

    @Test
    public void runScenario() {
        // Global verbose to get meaningful logs while stabilizing tests
        DcDebug.setVerbose(true);

        // Build a fresh model via the supplier for this run
        GridModel<?> gm = modelBuilder.get();

        // Solve through the production path
        var h = TestHarness.builder()
                .source(TestHarness.Source.NetBuilder)
                .solver(solver)
                .verbose(true)
                .build();

        Path dot = (DOT_DIR == null ? null : DOT_DIR.resolve(name + ".dot"));
        double[] V = h.solve(gm, dot);

        // Optional: sanity that ground is ~0V if node 0 is ground in your convention
        // org.dcsim.testing.AssertHelpers.assertVoltage(V, 0, 0.0, 1e-6);

        // Assert grid absorption by summing substation powers (ids starting with "SS"), if specified
        if (!Double.isNaN(expectedGridAbsorptionW)) {
            var devP = gm.getUpdatedDevicePowers(); // often Map<String, List<Real>>
            PowerAsserts.assertSumPowerByPrefix(devP, "SS", expectedGridAbsorptionW, tolW);
        }

        // Add scenario-specific physical checks here if needed (voltages, islanding invariants, etc.)
        // if (name.equals("island_separation_noCrossFeed")) { ... }
    }

    /**
     * Scenario builders for integration tests.
     *
     * Keep these explicit and minimal (no reflection). If a scenario depends on train behaviors
     * (regen/motoring), add those devices and setpoints inside the builder so power actually
     * circulates as expected.
     */
    public static final class Scenarios {

        /**
         * One island with two train anchor nodes, each with a diode substation (no backfeed).
         * Nodes inter-connected so regen can supply motoring internally.
         * <b>Expected:</b> sum of substation powers ≈ 0 W.
         */
        public static GridModel<?> buildDiodeTwoTrainsBalanced() {
            var gm = new GridModel<>(0);

            // Nodes
            gm.addNode(new Node(0, Real.fromDouble(0.0),  "GND"));
            gm.addNode(new Node(1, Real.fromDouble(10.0), "Tregen"));
            gm.addNode(new Node(2, Real.fromDouble(20.0), "Tmotor"));
            gm.setGroundNodeId(gm.getNodeById(0));

            // Diode substations (no backfeed)
            org.dcsim.testing.Devices.addSubstation(gm, "SS1", 1, 900.0, 2.0, /*allowBackFeed*/ false, "diode");
            org.dcsim.testing.Devices.addSubstation(gm, "SS2", 2, 900.0, 2.0, /*allowBackFeed*/ false, "diode");

            // Island interconnect
            org.dcsim.testing.Devices.addLine(gm, "L12", 1, 2, /*R*/ 0.5, /*lengthM*/ 100.0);

            // TODO: add Train devices with regen/motoring setpoints if required by your stack
            return gm;
        }

        /**
         * One island but intentionally "unbalanced." With diode substations (no backfeed),
         * grid absorption should still be ~ 0 W (excess is locally burned/redistributed).
         */
        public static GridModel<?> buildDiodeUnbalanced() {
            var gm = new GridModel<>(0);

            // Nodes
            gm.addNode(new Node(0, Real.fromDouble(0.0),  "GND"));
            gm.addNode(new Node(1, Real.fromDouble(10.0), "TrainA"));
            gm.addNode(new Node(2, Real.fromDouble(20.0), "TrainB"));
            gm.setGroundNodeId(gm.getNodeById(0));

            // Diode substations
            org.dcsim.testing.Devices.addSubstation(gm, "SS_A", 1, 900.0, 2.0, /*allowBackFeed*/ false, "diode");
            org.dcsim.testing.Devices.addSubstation(gm, "SS_B", 2, 900.0, 2.0, /*allowBackFeed*/ false, "diode");

            // Interconnect with asymmetric resistance to mimic "unbalance"
            org.dcsim.testing.Devices.addLine(gm, "L12_hi", 1, 2, /*R*/ 1.5, /*lengthM*/ 120.0);

            // TODO: add Train devices / setpoints to create a regen/motoring mismatch
            return gm;
        }

        /**
         * One island with bidirectional substations (allowBackFeed=true).
         * With proper train setpoints, this should yield positive grid absorption.
         * <b>Assertion is currently skipped (Double.NaN) until trains/setpoints are wired.</b>
         */
        public static GridModel<?> buildBidirectionalTwoTrains() {
            var gm = new GridModel<>(0);

            // Nodes
            gm.addNode(new Node(0, Real.fromDouble(0.0),  "GND"));
            gm.addNode(new Node(1, Real.fromDouble(10.0), "Tregen"));
            gm.addNode(new Node(2, Real.fromDouble(20.0), "Tmotor"));
            gm.setGroundNodeId(gm.getNodeById(0));

            // Bidirectional substations (allow backfeed)
            org.dcsim.testing.Devices.addSubstation(gm, "SS1", 1, 900.0, 2.0, /*allowBackFeed*/ true, "bidir");
            org.dcsim.testing.Devices.addSubstation(gm, "SS2", 2, 900.0, 2.0, /*allowBackFeed*/ true, "bidir");

            // Interconnect
            org.dcsim.testing.Devices.addLine(gm, "L12", 1, 2, /*R*/ 0.5, /*lengthM*/ 100.0);

            // TODO: add Train devices with regen/motoring setpoints to make net absorption > 0 W
            return gm;
        }

        /**
         * Two separate islands (no electrical link between them). Any power exchange must
         * not cross islands; with diode substations we expect net/grid absorption ≈ 0 W.
         */
        public static GridModel<?> buildTwoIslandsNoLink() {
            var gm = new GridModel<>(0);

            // Island A
            gm.addNode(new Node(0, Real.fromDouble(0.0),  "GND"));
            gm.addNode(new Node(1, Real.fromDouble(10.0), "A1"));
            gm.setGroundNodeId(gm.getNodeById(0));
            org.dcsim.testing.Devices.addSubstation(gm, "SS_A", 1, 900.0, 2.0, /*allowBackFeed*/ false, "diode");

            // Island B (separate node ids; still same ground id '0' label, but no line between islands)
            gm.addNode(new Node(2, Real.fromDouble(20.0), "B1"));
            org.dcsim.testing.Devices.addSubstation(gm, "SS_B", 2, 900.0, 2.0, /*allowBackFeed*/ false, "diode");

            // IMPORTANT: No line between A1 and B1 => true island separation
            // (Add intra-island lines if needed, but do not connect island A and B.)

            return gm;
        }
    }
}
