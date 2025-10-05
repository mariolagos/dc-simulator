package org.dcsim.solver.impl;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

public final class DcStamps {
    private DcStamps() {}

    /** Substation med diodspärr (ingen off-diagonal i spärrläge). */
    public static void stampSubstation(
            RealVector V,
            RealMatrix G,
            RealVector J,
            int aIdx, int bIdx,
            double E, double R, boolean allowBackfeed,
            double EPS, double gLeak) {

        if (R <= 0.0) return;
        final double g = 1.0 / R;

        final double Va = V.getEntry(aIdx);
        final double Vb = V.getEntry(bIdx);
        final double dV = Va - Vb;

        final boolean forward = (dV <= E - EPS);
        final boolean active  = allowBackfeed || forward;

        if (active) {
            // Resistor + Norton-källa (I = g*E från a→b)
            G.addToEntry(aIdx, aIdx, g);
            G.addToEntry(bIdx, bIdx, g);
            G.addToEntry(aIdx, bIdx, -g);
            G.addToEntry(bIdx, aIdx, -g);

            double Isrc = g * E;
            J.addToEntry(aIdx, +Isrc);
            J.addToEntry(bIdx, -Isrc);
        } else {
            // Spärr: öppen krets a↔b; bara liten diagonal läcka om du vill.
            if (gLeak > 0.0) {
                G.addToEntry(aIdx, aIdx, gLeak);
                G.addToEntry(bIdx, bIdx, gLeak);
            }
            // INGA off-diagonaler, INGEN källa.
        }
    }
}
