package testUtils;

// testutil/TestMatrix.java
import org.dcsim.electric.AnchorStamp;

import java.util.Arrays;
import java.util.function.BiConsumer;

public final class TestMatrix implements AnchorStamp.AdmittanceMatrix, AnchorStamp.CurrentVector {
    private final int n;
    public final double[][] Y;
    public final double[] J;

    public TestMatrix(int n){
        this.n = n;
        this.Y = new double[n][n];
        this.J = new double[n];
    }
    @Override public void add(int r, int c, double v){ Y[r][c] += v; }
    @Override public void add(int r, double v){ J[r] += v; }

    /** Enforce node 'g' as ground: V_g = 0 (replace row/col with identity row). */
    public void ground(int g){
        Arrays.fill(J, 0.0); // we typically re-stamp sources after grounding; for tests keep simple
        for (int r=0;r<n;r++){ Y[r][g]=0.0; }
        for (int c=0;c<n;c++){ Y[g][c]=0.0; }
        Y[g][g] = 1.0;
    }

    public double[] solve(){ return LinearSolver.solve(Y, J); }
}
