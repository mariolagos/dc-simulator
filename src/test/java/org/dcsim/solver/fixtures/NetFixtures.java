package org.dcsim.solver.fixtures;

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
 * Topology (indices, not original IDs):
 *   nodes: [0,1,2,3] where 0 is ground.
 *   lines: 1-2, 2-3
 *   substations (diodes): 1-0, 2-0, 3-0
 * Trains are connected bus-to-ground as requested (2->0 and 3->0).
 *
 * Notes:
 * - All indices are compact (0..n-1) to match DcNet LineData/SubstationData expectations.
 * - allowBackfeed=false produces a diode-like feeder.
 * - imax is set high by default to avoid becoming the bottleneck in tests.
 */
public final class NetFixtures {

    private NetFixtures() {}

    // Conservative numeric knobs used by some tests
    public static final double V_NOM = 900.0;      // substation EMF [V]
    public static final double V_MAX = 10_000.0;   // train disconnect ceiling [V]
    public static final double V_MIN = 5_000.0;   // train disconnect ceiling [V]
    public static final double I_MAX_HI = 5_000.0; // large current cap for trains [A]
    public static final double RINT = 0.05;        // substation internal R [ohm]
    public static final double R12  = 0.10;        // line 1-2 [ohm]
    public static final double R23  = 0.10;        // line 2-3 [ohm]

    /**
     * Three-bus backbone with diode substations, no trains.
     * nodes: 0..3, groundIndex=0
     * lines: 1-2, 2-3
     * substations: 1-0, 2-0, 3-0 (allowBackfeed=false)
     */
    public static DcNet threeBusDiodeBackbone() {
        return threeBusBackbone(V_NOM, RINT, R12, R23, /*allowBackfeed=*/false);
    }

    /**
     * Same as threeBusDiodeBackbone but parameterized.
     */
    public static DcNet threeBusBackbone(double emfV, double rint, double r12, double r23, boolean allowBackfeed) {
        final int n = 4;                       // nodes 0,1,2,3
        final int groundIndex = 0;             // node[0] is ground
        final List<Integer> nodeIds = Arrays.asList(0,1,2,3);
        final Map<Integer,Integer> idxById = Map.of(0,0, 1,1, 2,2, 3,3); // id==index

        final List<LineData> lines = new ArrayList<>();
        lines.add(new LineData("L_1_2", 1, 2, r12));
        lines.add(new LineData("L_2_3", 2, 3, r23));

        final List<SubstationData> subs = new ArrayList<>();
        subs.add(new SubstationData("SS1", 1, 0, emfV, rint, allowBackfeed));
        subs.add(new SubstationData("SS2", 2, 0, emfV, rint, allowBackfeed));
        subs.add(new SubstationData("SS3", 3, 0, emfV, rint, allowBackfeed));

        final List<TrainData> trains = new ArrayList<>();

        return new DcNet(n, groundIndex, nodeIds, idxById, lines, subs, trains);
    }

    /**
     * Three-bus diode backbone plus a single regenerating train at bus 2 to ground.
     * Useful for "no-receptivity" or "full-receptivity" single-train tests.
     */
    public static DcNet oneRegenAt2toGround(double regenW) {
        DcNet net = threeBusDiodeBackbone();
        List<TrainData> ts = new ArrayList<>(net.trains);
        ts.add(new TrainData("Tregen",
                /*a*/2,
                /*b*/0,
                /*req_W*/ -Math.abs(regenW),
                /*imax_A*/ I_MAX_HI,
                /*vmin_V*/ V_MIN,
                /*vmax_V*/ V_MAX));
        return new DcNet(net.n, net.groundIndex, net.nodeIds, net.indexOfNodeId, net.lines, net.substations, List.copyOf(ts));
    }

    /*
    public record TrainData(
        String id,
        int a,
        int b,
        double req_W,    // +W = motor; -W = regen
        double imax_A,   // max abs-ström
        double vmin_V,    // cutoff för motor/regengating
        double vmax_V    // max spänning för linjeregen
)
     */

    /**
     * Three-bus diode backbone plus two trains:
     *   - regenerating at 2->0
     *   - motoring at 3->0
     * Use equal magnitudes for a balanced grid absorption near zero.
     */
    public static DcNet regenAt2_motorAt3(double regenAbsW) {
        DcNet net = threeBusDiodeBackbone();
        List<TrainData> ts = new ArrayList<>(net.trains);
        ts.add(new TrainData("Tregen", 2, 0, -Math.abs(regenAbsW), I_MAX_HI, V_MIN, V_MAX));
        ts.add(new TrainData("Tmotor", 3, 0, +Math.abs(regenAbsW), I_MAX_HI, V_MIN, V_MAX));
        return new DcNet(net.n, net.groundIndex, net.nodeIds, net.indexOfNodeId, net.lines, net.substations, List.copyOf(ts));
    }

    /**
     * Utility: compute grid absorption from substations at a solved voltage vector.
     * Mirrors the diode behavior used by the solver reporting: when allowBackfeed=false
     * and dV > E+eps, the Norton source current is zero (only leakage handled in G).
     */
    public static double gridAbsorptionW(DcNet net, RealVector V) {
        final double EPS = 1e-6;
        double pSum = 0.0;
        for (SubstationData ss : net.substations) {
            final double Va = V.getEntry(ss.a());
            final double Vb = V.getEntry(ss.b());
            final double dV = Va - Vb;
            final double E  = ss.emf_V();
            final double R  = ss.rint_ohm();
            if (R <= 0.0) continue;
            final double g  = 1.0 / R;

            // zero source current in absorbing quadrant when backfeed is disabled
            final boolean active = ss.allowBackfeed() || (dV <= E + EPS);
            final double iNet = active ? g * (E - dV) : 0.0;
            pSum += iNet * dV;
        }
        return pSum;
    }

    /**
     * Convenience to make a nominal zero seed vector of size n.
     */
    public static RealVector zeroSeed(int n) {
        return new ArrayRealVector(n); // zeros
    }
}
