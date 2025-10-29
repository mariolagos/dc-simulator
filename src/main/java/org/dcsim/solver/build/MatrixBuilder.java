package org.dcsim.solver.build;

import org.apache.commons.math3.linear.*;
import org.dcsim.solver.api.*;

public final class MatrixBuilder {
    private MatrixBuilder() {}

    /** Build base conductance matrix (G) and source vector (J) from lines only. */
    public static DcSystem build(DcNet net, double[] V /*unused, kept for signature compatibility*/) {
        final int n = net.n();
        RealMatrix G = new Array2DRowRealMatrix(n, n);
        RealVector J = new ArrayRealVector(n);

        for (LineData L : net.lines()) {
            int a = L.a(), b = L.b();
            double R = L.r_ohm();
            if (R <= 0.0) continue;
            double g = 1.0 / R;
            G.addToEntry(a, a, +g);
            G.addToEntry(b, b, +g);
            G.addToEntry(a, b, -g);
            G.addToEntry(b, a, -g);
        }
        return new DcSystem(G, J);
    }
}
