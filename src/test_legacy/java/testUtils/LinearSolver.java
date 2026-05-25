package testUtils;

// testutil/LinearSolve.java
public final class LinearSolver {
    private LinearSolver() {}
    // Solves A x = b in-place on copies; returns x.
    public static double[] solve(double[][] A, double[] b) {
        int n = A.length;
        double[][] M = new double[n][n];
        double[] rhs = new double[n];
        for (int i=0;i<n;i++){ System.arraycopy(A[i],0,M[i],0,n); rhs[i]=b[i]; }

        for (int k=0;k<n;k++){
            // pivot
            int piv = k;
            double best = Math.abs(M[k][k]);
            for (int i=k+1;i<n;i++){
                double v = Math.abs(M[i][k]);
                if (v>best){ best=v; piv=i; }
            }
            if (best < 1e-14) throw new RuntimeException("Singular matrix");
            if (piv!=k){
                double[] tmpRow = M[k]; M[k]=M[piv]; M[piv]=tmpRow;
                double tmp = rhs[k]; rhs[k]=rhs[piv]; rhs[piv]=tmp;
            }
            // eliminate
            double diag = M[k][k];
            for (int j=k;j<n;j++) M[k][j] /= diag;
            rhs[k] /= diag;
            for (int i=0;i<n;i++){
                if (i==k) continue;
                double f = M[i][k];
                if (f==0) continue;
                for (int j=k;j<n;j++) M[i][j] -= f*M[k][j];
                rhs[i] -= f*rhs[k];
            }
        }
        return rhs;
    }
}
