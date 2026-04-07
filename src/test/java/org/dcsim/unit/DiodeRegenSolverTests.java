package org.dcsim.unit;

import org.apache.commons.math3.linear.RealVector;
import org.dcsim.solver.api.DcNet;
import org.dcsim.solver.api.LineData;
import org.dcsim.solver.api.SubstationData;
import org.dcsim.solver.api.TrainData;
import org.dcsim.solver.impl.DcIterativeSolver;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * Diode substation + motor/regen trains.
 * Trains are placed on segments (1–2 and 2–3) instead of directly to ground.
 * Substations are 1–0, 2–0, 3–0 (diode rectifiers, no backfeed).
 */
public class DiodeRegenSolverTests {

    private static final double EPS = 1e-6;

    @Test
    public void threeSubs_twoTrains_balanced_diode() {
        DcNet net = makeOneSubTwoTrains(
                /*Rline*/ 0.01, /*Rint*/ 0.01,
                /*Emf*/ 900.0,
                /*motorW*/ +100_000.0,
                /*regenW*/ -100_000.0,
                /*imax*/ 5000.0,
                /*vmin*/ 0.0,   // hard stop for motor below vmin (we keep 0 so it's always on)
                /*vmax*/ 10_000.0 // regen cut above vmax (very large here: no cut)
        );

        RealVector V = DcIterativeSolver.solveVoltages(net);

        double pGrid = sumSubstationPowerWithDiodeGating(net, V);
        // Target ~0 W (line losses are negligible with tiny resistances)
        assertEquals("Grid absorption ≈ 0 in balanced motor/regen with diode",
                0.0, pGrid, 2500.0); // ±2.5 kW
    }

    @Test
    public void threeSubs_oneRegen_unmatched_diode() {
        DcNet net = makeThreeSubsOneRegen(
                /*Rline*/ 0.01, /*Rint*/ 0.01,
                /*Emf*/ 900.0,
                /*regenW*/ -100_000.0,
                /*imax*/ 5000.0,
                /*vmin*/ 0.0,
                /*vmax*/ 10_000.0
        );

        RealVector V = DcIterativeSolver.solveVoltages(net);

        double pGrid = sumSubstationPowerWithDiodeGating(net, V);
        // With no receptivity and diode SS, regen goes to brake; grid should absorb ~0.
        assertEquals("No receptivity: grid absorption ≈ 0 (regen goes to brake)",
                0.0, pGrid, 2500.0); // ±2.5 kW
    }

    // ---------- helpers ----------

    /** Build nodes [0,1,2,3], lines 1-2 and 2-3, diode SS at 1-0, 2-0, 3-0, and trains on 1-2 (regen) and 2-3 (motor). */
    private static DcNet makeOneSubTwoTrains(
            double Rline, double Rint, double Emf,
            double motorW, double regenW,
            double imax, double vmin, double vmax) {

        // nodes: 0=ground, 1,2,3
        final List<String> nodeIds = Arrays.asList("0", "1", "2", "3");
        final Map<String,Integer> idx = new HashMap<>();
        for (int i=0;i<nodeIds.size();i++) idx.put(nodeIds.get(i), i);
        final int g = idx.get(0);

        List<LineData> lines = new ArrayList<>();
        lines.add(new LineData("L12", idx.get(1), idx.get(2), Rline));
        lines.add(new LineData("L23", idx.get(2), idx.get(3), Rline));

        List<SubstationData> subs = new ArrayList<>();
        subs.add(new SubstationData("SS1", idx.get(1), idx.get(0), Emf, Rint, /*allowBackfeed=*/false));

        List<TrainData> trains = new ArrayList<>();
        // regen on segment 1–2
        trains.add(new TrainData("Tregen", idx.get(1), idx.get(2), regenW, imax, vmin, vmax));
        // motor on segment 2–3
        trains.add(new TrainData("Tmotor", idx.get(2), idx.get(3), motorW, imax, vmin, vmax));

        return new DcNet(
                nodeIds.size(),
                g,
                Collections.unmodifiableList(nodeIds),
                Collections.unmodifiableMap(idx),
                Collections.unmodifiableList(lines),
                Collections.unmodifiableList(subs),
                Collections.unmodifiableList(trains)
        );
    }

    /** Like above but only one regen train on segment 1–2. */
    private static DcNet makeThreeSubsOneRegen(
            double Rline, double Rint, double Emf,
            double regenW,
            double imax, double vmin, double vmax) {

        final List<String> nodeIds = Arrays.asList("0", "1", "2", "3");
        final Map<String,Integer> idx = new HashMap<>();
        for (int i=0;i<nodeIds.size();i++) idx.put(nodeIds.get(i), i);
        final int g = idx.get(0);

        List<LineData> lines = new ArrayList<>();
        lines.add(new LineData("L12", idx.get(1), idx.get(2), Rline));
        lines.add(new LineData("L23", idx.get(2), idx.get(3), Rline));

        List<SubstationData> subs = new ArrayList<>();
        subs.add(new SubstationData("SS1", idx.get(1), idx.get(0), Emf, Rint, /*allowBackfeed=*/false));
        subs.add(new SubstationData("SS2", idx.get(2), idx.get(0), Emf, Rint, /*allowBackfeed=*/false));
        subs.add(new SubstationData("SS3", idx.get(3), idx.get(0), Emf, Rint, /*allowBackfeed=*/false));

        List<TrainData> trains = new ArrayList<>();
        trains.add(new TrainData("Tregen", idx.get(1), idx.get(2), regenW, imax, vmin, vmax));

        return new DcNet(
                nodeIds.size(),
                g,
                Collections.unmodifiableList(nodeIds),
                Collections.unmodifiableMap(idx),
                Collections.unmodifiableList(lines),
                Collections.unmodifiableList(subs),
                Collections.unmodifiableList(trains)
        );
    }

    /** Sum substation power using diode gating: i = g*(E - dV) unless (no backfeed && dV > E + EPS) then i=0. P = i*dV. */
    private static double sumSubstationPowerWithDiodeGating(DcNet net, RealVector V) {
        double p = 0.0;
        for (SubstationData ss : net.substations) {
            int a = ss.a(), b = ss.b();
            double Va = V.getEntry(a), Vb = V.getEntry(b);
            double dV = Va - Vb;
            double E  = ss.emf_V();
            double R  = ss.rint_ohm();
            if (R <= 0.0) continue;
            double g  = 1.0 / R;

            boolean active = ss.allowBackfeed() || (dV <= E + EPS);
            double iNet = active ? g * (E - dV) : 0.0;
            p += iNet * dV;
        }
        return p;
        // Note: "absorption" sign follows P = i*dV (power delivered into the network is +).
        // If you want "power drawn from grid" as positive, this is already that convention.
    }
}
