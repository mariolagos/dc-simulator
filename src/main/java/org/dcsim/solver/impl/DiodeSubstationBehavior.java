package org.dcsim.solver.behavior;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

/**
 * Diode-like substation stamping:
 *  - Thevenin(E,R) -> Norton(g,E) when active (forward or backfeed-allowed)
 *  - When blocked (dV > E + eps and backfeed disabled): open circuit,
 *    only a tiny diagonal leak is added for numerical stability.
 */
public  final class DiodeSubstationBehavior {
    private DiodeSubstationBehavior() {}

    public static void stamp(
            double[] V,            // may be null at first iteration
            RealMatrix G,
            RealVector J,
            int a, int b,
            double E,              // EMF [V]
            double Rint,           // internal resistance [ohm]
            boolean allowBackfeed,
            double eps,
            double gLeakDiag
    ) {
        if (Rint <= 0.0) return;

        final double g = 1.0 / Rint;

        // If we don't have a voltage guess yet, treat as active (forward).
        double dV = (V == null) ? 0.0 : (V[a] - V[b]);
        boolean active = allowBackfeed || (dV <= E + eps);

        if (active) {
            // Conductance between a-b
            G.addToEntry(a, a,  g);
            G.addToEntry(b, b,  g);
            G.addToEntry(a, b, -g);
            G.addToEntry(b, a, -g);

            // Norton source current I = g*E from a -> b
            double Isrc = g * E;
            J.addToEntry(a, +Isrc);
            J.addToEntry(b, -Isrc);
        } else {
            // Blocked: open circuit; only tiny diagonal leak to improve conditioning
            if (gLeakDiag > 0.0) {
                G.addToEntry(a, a, gLeakDiag);
                G.addToEntry(b, b, gLeakDiag);
            }
        }
    }
}
