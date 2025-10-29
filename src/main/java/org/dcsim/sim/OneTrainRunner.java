package org.dcsim.sim;

import org.dcsim.electric.AnchorStamp;

import java.util.List;
import java.util.Objects;

public final class OneTrainRunner {

    private OneTrainRunner() {}

    /** Run a simple simulation for one train moving along a path of edges. */
    public static void run(
            int nNodes,
            List<EdgeProps> path,     // ordered edges the train will traverse
            TrainRuntime train,
            int sourceNodeToGround,   // pick a ground/reference node (or use your existing)
            FixedNetworkStamper stampFixed, // stamp substations, feeders, shunts, etc.
            LinearSolve solver,       // hook to your solver (replace dense example)
            double dt, double tEnd,
            double Rmin, double epsFracForMigration // e.g. Rmin=1e-6, epsFrac=1e-3
    ) {
        Objects.requireNonNull(path);
        int iEdge = train.edgeIndex;

        for (double t = 0.0; t <= tEnd; t += dt) {
            EdgeProps e = path.get(iEdge);

            // --- Build Y and J for this step (use your SparseY in production) ---
            DenseYJ sys = new DenseYJ(nNodes);
            AnchorStamp.AdmittanceMatrix Ya = sys::add;
            AnchorStamp.CurrentVector    Ja = sys::add;

            // Stamp fixed network (substations etc.)
            if (stampFixed != null) stampFixed.stamp(Ya, Ja);

            // Stamp split i--a--j at x
            AnchorStamp.stampAnchorSplit(Ya, e.i, train.anchorNode, e.j,
                    e.rPerM, e.lengthM, train.xM, Rmin);

            for (int idx = 0; idx < path.size(); idx++) {
                if (idx == iEdge) continue; // den aktuella kanten hanteras via split
                EdgeProps ei = path.get(idx);
                double Ri = ei.rPerM * ei.lengthM;
                AnchorStamp.stampResistor(Ya, ei.i, train.anchorNode == ei.i || train.anchorNode == ei.j ? train.anchorNode : ei.j, 0.0); // no-op, ignore
                AnchorStamp.stampResistor(Ya, ei.i, ei.j, 1.0 / Ri);
            }
            // Stamp train as constant-P (Norton) at anchor using VaPrev
            // Add a tiny shunt (optional) if you want extra stability, e.g. Rload=1e6 Ohm
            AnchorStamp.stampConstantPLoad(Ya, Ja, train.anchorNode, train.pW, train.VaPrev, null);

            // Ground a node: enforce V_g = 0
            enforceGround(sys.Y, sys.J, sourceNodeToGround);

            // Solve
            double[] V = solver.solve(sys.Y, sys.J);
            double Va = V[train.anchorNode];
            if (Double.isNaN(Va) || Double.isInfinite(Va)) {
                throw new IllegalStateException("Invalid Va at t=" + t + " (check ground, Rmin, sources)");
            }

            // --- Advance position and handle migration ---
            train.VaPrev = Va;                // store for next P/V update
            train.xM += train.vMS * dt;       // simple kinematics

            double eps = Math.max(1e-9, epsFracForMigration * e.lengthM);

            if (train.xM >= e.lengthM - eps) {
                // migrate to next edge if available
                if (iEdge + 1 < path.size()) {
                    iEdge += 1;
                    train.edgeIndex = iEdge;
                    train.xM = Math.max(0.0, train.xM - e.lengthM);
                    // keep same anchorNode id (continuity of node numbering)
                } else {
                    // reached end; clamp and stop moving (or wrap/exit)
                    train.xM = e.lengthM - eps;
                    train.vMS = 0.0;
                }
            }

            // --- (Optional) log or collect metrics ---
            // System.out.printf(Locale.ROOT, "t=%.2f s, edge=%d, x=%.2f m, Va=%.2f V%n", t, iEdge, train.xM, Va);
        }
    }

    /** Replace row/col g with identity to set V_g = 0 (for dense example only). */
    private static void enforceGround(double[][] Y, double[] J, int g) {
        int n = Y.length;
        for (int r = 0; r < n; r++) { Y[r][g] = 0.0; }
        for (int c = 0; c < n; c++) { Y[g][c] = 0.0; }
        Y[g][g] = 1.0;
        J[g] = 0.0;
    }
}