package org.dcsim.sim;

import org.dcsim.electric.AnchorStamp;

/** A tiny dense Y/J builder (production version should use your SparseY). */
final class DenseYJ implements AnchorStamp.AdmittanceMatrix, AnchorStamp.CurrentVector {
    final double[][] Y;
    final double[] J;
    DenseYJ(int n) { Y = new double[n][n]; J = new double[n]; }
    @Override public void add(int r, int c, double v) { Y[r][c] += v; }
    @Override public void add(int r, double v)       { J[r]    += v; }
}