package org.dcsim.sim;

@FunctionalInterface
public interface LinearSolve {
    /** Solve Y V = J and return node voltages V[0..n-1]. */
    double[] solve(double[][] Y, double[] J);
}
