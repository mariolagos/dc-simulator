package org.dcsim.electric;

import java.util.*;
import java.util.function.ToDoubleFunction;

public final class ReportingUtils {
    private ReportingUtils() {}

    /** Boundary-punkt i ett bassegment: nodeId + position i meter från vänster ändnod. */
    public static final class Boundary {
        public final int nodeId;
        public final double sM;
        public Boundary(int nodeId, double sM) { this.nodeId = nodeId; this.sM = sM; }
    }

    /**
     * Summerar ledningsförluster (W) i ett bassegment vid en given tidpunkt,
     * oberoende av hur många delsegment som råkar finnas just då.
     *
     * @param Rtotal   total fysisk resistans (Ohm) för bassegmentet (utan R_min)
     * @param lengthM  bassegmentets längd (m)
     * @param boundaries sorterade eller osorterade boundary-punkter (vänster@0, dynamiska@x, höger@L)
     * @param V        funktion som ger nodspänning (V) för nodeId → double (t.ex. Real::asDouble)
     * @return total P_loss i W
     */
    public static double segmentLossW(
            double Rtotal,
            double lengthM,
            List<Boundary> boundaries,
            ToDoubleFunction<Integer> V
    ) {
        if (lengthM <= 0.0 || Rtotal < 0.0) return 0.0;
        final double L = lengthM;

        // sortera efter sM
        List<Boundary> b = new ArrayList<>(boundaries);
        b.sort(Comparator.comparingDouble(x -> x.sM));

        double loss = 0.0;
        for (int k = 0; k + 1 < b.size(); k++) {
            Boundary a = b.get(k), c = b.get(k + 1);
            double x0 = clamp(a.sM, 0.0, L);
            double x1 = clamp(c.sM, 0.0, L);
            double dx = Math.max(0.0, x1 - x0);
            if (dx <= 0.0) continue;

            double Rk = Rtotal * (dx / L);            // fysisk del-R (utan R_min)
            double dV = V.applyAsDouble(a.nodeId) - V.applyAsDouble(c.nodeId);
            loss += (dV * dV) / Rk;                   // W
        }
        return loss;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(v, hi));
    }
}
