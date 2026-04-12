package org.dcsim.electric;

// AnchorStamp.java
// Minimal, framework-agnostic utilities for migrating anchor split + Norton load.
// Comments in English (as per your preference).

public final class AnchorStamp {

    private AnchorStamp() {}

    // Functional adapters so you can plug in your own types (e.g., SparseY, DenseVec).
    // Example usage:
    //   AdmittanceMatrix Ya = Y::add;   // if your SparseY has add(int,int,double)
    //   CurrentVector    Ja = J::add;   // if your RHS vector has add(int,double)
    @FunctionalInterface
    public interface AdmittanceMatrix {
        void add(int row, int col, double value);
    }

    @FunctionalInterface
    public interface CurrentVector {
        void add(int row, double value);
    }

    /** Classic 2-node resistor stamp: adds a conductance g between nodes p and q. */
    public static void stampResistor(AdmittanceMatrix Y, int p, int q, double g) {
        if (g == 0.0) return;
        Y.add(p, p,  g);
        Y.add(q, q,  g);
        Y.add(p, q, -g);
        Y.add(q, p, -g);
    }

    /**
     * Stamp the migrating split i--a--j for a train at position xM along the edge [0..lengthM].
     * Preserves total line resistance rPerM * lengthM and protects endpoints with Rmin.
     */
    public static void stampAnchorSplit(
            AdmittanceMatrix Y,
            int i, int a, int j,
            double rPerM, double lengthM,
            double xM,
            double Rmin // e.g., 1e-6 Ohm
    ) {
        // Clamp x to [0, L]
        double L  = Math.max(0.0, lengthM);
        double x  = Math.max(0.0, Math.min(xM, L));

        // Segment resistances with endpoint guard
        double rLeft  = Math.max(rPerM * x,        Rmin);
        double rRight = Math.max(rPerM * (L - x),  Rmin);

        double gL = 1.0 / rLeft;
        double gR = 1.0 / rRight;

        stampResistor(Y, i, a, gL);
        stampResistor(Y, a, j, gR);
    }

    /**
     * Stamp a Norton load at node a: current I to ground (negative for a consuming load
     * under the convention J collects injected currents), plus optional shunt conductance Gload.
     *
     * NOTE: If your RHS sign convention differs, flip the sign on I.
     */
    public static void stampNortonAt(
            AdmittanceMatrix Y,
            CurrentVector J,
            int a,
            double I,          // A (positive here means "draw from node to ground" -> J.add(a, -I))
            double Gload       // S (1/Rload). Use 0.0 if not needed.
    ) {
        if (Gload > 0.0) {
            Y.add(a, a, Gload);
        }
        // Sign per "injected currents" convention: load draws current -> negative injection at node.
        if (I != 0.0) {
            J.add(a, -I);
        }
    }

    /**
     * Convenience: constant-P load at node a.
     * Uses I = P / Va. Call this AFTER you know Va from the previous solve (or use a predictor).
     * Optionally add a stabilizing shunt Rload (as Gload). If Va ~ 0, do nothing to avoid blow-up.
     */
    public static void stampConstantPLoad(
            AdmittanceMatrix Y,
            CurrentVector J,
            int a,
            double P,          // W (positive for consuming)
            double Va,         // V at node a (from last iteration/step)
            Double RloadOpt    // nullable. If provided, adds Gload = 1/Rload
    ) {
        if (Va <= 1e-9) return; // guard; caller can also clamp P or Va
        double I = P / Va;       // A
        double Gload = (RloadOpt != null && RloadOpt > 0.0) ? (1.0 / RloadOpt) : 0.0;
        stampNortonAt(Y, J, a, I, Gload);
    }
}
