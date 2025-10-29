package org.dcsim.solver.profile;

import java.util.Arrays;

public final class TrainProfile {
    public final double[] t_s;   // sorted, monotonic non-decreasing
    public final double[] P_W;   // same length as t_s

    public TrainProfile(double[] t_s, double[] P_W) {
        if (t_s == null || P_W == null || t_s.length != P_W.length || t_s.length == 0)
            throw new IllegalArgumentException("Invalid profile arrays.");
        this.t_s = t_s;
        this.P_W = P_W;
    }

    // Linear interpolation, clamp ends
    public double powerAt(double t) {
        if (t <= t_s[0]) return P_W[0];
        int n = t_s.length;
        if (t >= t_s[n - 1]) return P_W[n - 1];
        int i = Arrays.binarySearch(t_s, t);
        if (i >= 0) return P_W[i];
        int ip = -i - 1;
        int il = ip - 1;
        double t0 = t_s[il], t1 = t_s[ip];
        double p0 = P_W[il], p1 = P_W[ip];
        double u = (t - t0) / Math.max(1e-12, (t1 - t0));
        return p0 + u * (p1 - p0);
    }
}
