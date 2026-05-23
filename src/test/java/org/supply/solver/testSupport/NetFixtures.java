package org.supply.solver.testSupport;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.LineData;
import org.dcsim.solver.api.SubstationData;
import org.dcsim.solver.api.TrainData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Small factory helpers for repeatable DC test networks.
 *
 * Topology (compact indices, not original IDs):
 *   nodes: [0,1,2,3] where 0 is ground
 *   lines: 1-2, 2-3
 *   substations: 1-0, 3-0  (either diode or backfeed-allowed)
 * Trains are connected bus-to-ground as requested (e.g. 2->0 and/or 3->0).
 *
 * NOTE:
 * Earlier variants accidentally tied substations to a non-ground node and/or
 * introduced an extra node. This version always ties substations to true
 * ground (index 0) and keeps the backbone strictly 4 nodes.
 */
public final class NetFixtures {

    private NetFixtures() {}

    // Nominal values used by many tests (900 V-style)
    public static final double V_NOM     = 900.0;    // substation EMF [V]
    public static final double V_MIN     = 600.0;    // train vmin [V]
    public static final double V_MAX     = 1000.0;   // train vmax [V]
    public static final double I_MAX_HI  = 5_000.0;  // generous current limit [A]

    // Backbone impedances (simple and stable for tests)
    public static final double RINT = 1.0;    // substation internal R [ohm]
    public static final double R12  = 1.0;    // line 1-2 [ohm]
    public static final double R23  = 1.0;    // line 2-3 [ohm]

    /* ================================================================== */
    /*  Backbone builders                                                  */
    /* ================================================================== */

    /** Three-bus backbone with diode substations (allowBackfeed=false), no trains. */
    public static DcNet threeBusDiodeBackbone() {
        return threeBusBackbone(V_NOM, RINT, R12, R23, /*allowBackfeed=*/false);
    }

    /**
     * Three-bus backbone with backfeed-allowed substations, no trains (4-arg signature).
     * Kept because several tests expect exactly this method shape.
     */
    public static DcNet threeBusBackboneBackfeed(double emfV, double rint, double r12, double r23) {
        return threeBusBackbone(emfV, rint, r12, r23, /*allowBackfeed=*/true);
    }

    /**
     * Overload with explicit allowBackfeed flag (5-arg). Some tests call this form.
     */
    public static DcNet threeBusBackboneBackfeed(double emfV, double rint, double r12, double r23, boolean allowBackfeed) {
        return threeBusBackbone(emfV, rint, r12, r23, allowBackfeed);
    }

    /**
     * Parameterized three-bus backbone (SS1—bus2—SS2). Ground is node 0.
     * Lines: 1-2, 2-3. Substations: 1-0 and 3-0.
     */
    public static DcNet threeBusBackbone(double emfV,
                                         double rint,
                                         double r12,
                                         double r23,
                                         boolean allowBackfeed) {
        final int n = 4;                       // nodes 0,1,2,3
        final int groundIndex = 0;             // node[0] is ground
        final List<String> nodeIds = Arrays.asList("0","1","2","3");
        final Map<String,Integer> idxById = Map.of("0",0, "1",1, "2",2, "3",3); // id==index

        final List<LineData> lines = new ArrayList<>();
        lines.add(new LineData("L_1_2", 1, 2, r12));
        lines.add(new LineData("L_2_3", 2, 3, r23));

        final List<SubstationData> subs = new ArrayList<>();
        subs.add(new SubstationData("SS1", 1, 0, emfV, rint, allowBackfeed));
        subs.add(new SubstationData("SS2", 3, 0, emfV, rint, allowBackfeed));

        final List<TrainData> trains = new ArrayList<>();

        return new DcNet(n, groundIndex, nodeIds, idxById, lines, subs, trains);
    }

    /* ================================================================== */
    /*  One-bus single-feeder (750 V style)                                */
    /* ================================================================== */

    /**
     * One-bus network with a single backfeed-allowed feeder at 750 V and a single
     * regenerating train at the same bus-to-ground. Used by integration tests that
     * expect the grid to absorb approximately the requested regen power.
     *
     * Nodes: [0,1] where 0 is ground. No lines.
     * Substation: SS at 1->0 (backfeed allowed).
     * Train: Tregen at 1->0 with requested negative power.
     */
    public static DcNet oneBusSingleFeederBackfeed750(double regenW) {
        final int n = 2;            // nodes 0..1, 0 is ground
        final int groundIndex = 0;

        final List<String> nodeIds = Arrays.asList("0","1");
        final Map<String,Integer> idxById = Map.of("0",0, "1",1);

        final List<LineData> lines = List.of(); // no lines

        final double emfV = 750.0;
        final double rint = 0.5;   // modest internal resistance for a stable Norton
        final boolean allowBackfeed = true;

        final List<SubstationData> subs = List.of(
                new SubstationData("SS", 1, 0, emfV, rint, allowBackfeed)
        );

        // Use a reasonable vmin/vmax window for a 750 V system
        final double vmin = 500.0;
        final double vmax = 900.0;
        final double imax = I_MAX_HI;

        final List<TrainData> trains = List.of(
                new TrainData("Tregen", 1, 0, -Math.abs(regenW), imax, vmin, vmax)
        );

        return new DcNet(n, groundIndex, nodeIds, idxById, lines, subs, trains);
    }

    /* ================================================================== */
    /*  Train composition helpers on the 3-bus backbone                    */
    /* ================================================================== */

    /** Three-bus diode backbone + one regenerating train at 2->0. */
    public static DcNet oneRegenAt2toGround(double regenW) {
        DcNet net = threeBusDiodeBackbone();
        List<TrainData> ts = new ArrayList<>(net.trains());
        ts.add(new TrainData("Tregen", 2, 0, -Math.abs(regenW), I_MAX_HI, V_MIN, V_MAX));
        return new DcNet(net.n(), net.groundIndex(), net.nodeIds(), net.indexById(),
                net.lines(), net.substations(), List.copyOf(ts));
    }

    /**
     * Three-bus diode backbone + two trains:
     *   - regenerating at 2->0
     *   - motoring    at 3->0
     * Use equal magnitudes for a balanced grid absorption near zero.
     */
    public static DcNet regenAt2_motorAt3(double regenAbsW) {
        DcNet net = threeBusDiodeBackbone();
        List<TrainData> ts = new ArrayList<>(net.trains());
        ts.add(new TrainData("Tregen", 2, 0, -Math.abs(regenAbsW), I_MAX_HI, V_MIN, V_MAX));
        ts.add(new TrainData("Tmotor", 3, 0, +Math.abs(regenAbsW), I_MAX_HI, V_MIN, V_MAX));
        return new DcNet(net.n(), net.groundIndex(), net.nodeIds(), net.indexById(),
                net.lines(), net.substations(), List.copyOf(ts));
    }

    /* ================================================================== */
    /*  Utilities used by tests                                            */
    /* ================================================================== */

    /**
     * Compute "grid absorption" (sum of substation P), consistent with
     * diode behavior: when allowBackfeed=false and dV > E+eps, the source is off.
     */
    public static double gridAbsorptionW(DcNet net, RealVector V) {
        final double EPS = 1e-6;
        double pSum = 0.0;
        for (SubstationData ss : net.substations()) {
            final double Va = V.getEntry(ss.a());
            final double Vb = V.getEntry(ss.b());
            final double dV = Va - Vb;
            final double E  = ss.emf_V();
            final double R  = ss.rint_ohm();
            if (R <= 0.0) continue;
            final double g  = 1.0 / R;

            final boolean active = ss.allowBackfeed() || (dV <= E + EPS);
            final double iNet = active ? g * (E - dV) : 0.0;  // Norton current a->b
            pSum += iNet * dV;                                // P = I * (Va - Vb)
        }
        return pSum;
    }

    /** Convenience zero seed vector (size n). */
    public static RealVector zeroSeed(int n) {
        return new ArrayRealVector(n); // zeros
    }
}
