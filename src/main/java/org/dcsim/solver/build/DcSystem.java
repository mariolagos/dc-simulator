package org.dcsim.solver.build;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import java.util.Objects;

/** Immutable container for the linearised DC system: G·V = J. */
public final class DcSystem {
    private final RealMatrix G;
    private final RealVector J;

    public DcSystem(RealMatrix G, RealVector J) {
        this.G = Objects.requireNonNull(G, "G");
        this.J = Objects.requireNonNull(J, "J");
    }

    /** Admittance matrix. */
    public RealMatrix G() { return G; }

    /** Source vector. */
    public RealVector J() { return J; }
}
