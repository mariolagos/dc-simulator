package org.dcsim.power;

import org.dcsim.PowerPoint;
import org.dcsim.math.Real;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Table-backed power profile with linear interpolation (time series).
 *
 * Contract (matches abstract PowerProfile):
 *  - getPowerAtTime(t)    -> Real (W)
 *  - getPositionAtTime(t) -> Real (m)
 *  - getSpeedAtTime(t)    -> Real (m/s)
 *
 * Behavior:
 *  - Power: linear interpolation, clamped to end-points.
 *  - Position: prefers explicit x-series if present; else integrates speed; else 0.
 *  - Speed: prefers explicit v-series; else slope of x; else 0.
 */
public final class TablePowerProfile implements PowerProfile { // if PowerProfile is an interface, change to "implements PowerProfile"

    private final double[] tSec;   // seconds (sorted)
    private final double[] pW;     // W
    private final double[] vMS;    // m/s (NaN if unknown)
    private final double[] xM;     // m  (explicit or integrated)
    private final boolean hasV;
    private final boolean hasX;

    private final double t0;
    private final double tN;

    public TablePowerProfile(List<PowerPoint> points) {
        List<PowerPoint> src = (points == null)
                ? Collections.emptyList()
                : new ArrayList<>(points);
        src.sort(Comparator.comparingDouble(TablePowerProfile::safeTime));

        final int n = Math.max(1, src.size());
        this.tSec = new double[n];
        this.pW   = new double[n];
        this.vMS  = new double[n];

        Double[] xRaw = new Double[n];
        boolean anyV = false, anyX = false;

        if (src.isEmpty()) {
            tSec[0] = 0.0; pW[0] = 0.0; vMS[0] = Double.NaN; xRaw[0] = null;
        } else {
            for (int i = 0; i < n; i++) {
                PowerPoint pt = src.get(i);
                tSec[i] = safeTime(pt);
                pW[i]   = safePower(pt);
                Double vv = tryGetDouble(pt, "speed", "getSpeed", "v", "getV", "velocity", "getVelocity");
                vMS[i]  = (vv != null) ? vv : Double.NaN;
                anyV   |= (vv != null);

                // Position in meters (numeric). Not the label/string “position".
                Double xx = tryGetDouble(pt,
                        "positionMeters", "getPositionMeters", "posM", "getPosM",
                        "x", "getX", "s", "getS");
                xRaw[i] = xx;
                anyX   |= (xx != null);
            }
        }

        this.hasV = anyV;
        this.hasX = anyX;

        // Build x[] either from explicit x or by integrating v (trapezoidal).
        this.xM = new double[n];
        if (hasX) {
            for (int i = 0; i < n; i++) {
                xM[i] = (xRaw[i] != null) ? xRaw[i] : (i > 0 ? xM[i - 1] : 0.0);
            }
        } else if (hasV) {
            xM[0] = 0.0;
            for (int i = 1; i < n; i++) {
                double dt = Math.max(0.0, tSec[i] - tSec[i - 1]);
                double v0 = Double.isNaN(vMS[i - 1]) ? 0.0 : vMS[i - 1];
                double v1 = Double.isNaN(vMS[i])     ? v0   : vMS[i];
                xM[i] = xM[i - 1] + 0.5 * (v0 + v1) * dt;
            }
        } else {
            Arrays.fill(xM, 0.0);
        }

        this.t0 = tSec[0];
        this.tN = tSec[n - 1];
    }

    // ===== Implement PowerProfile (Real return types) =====

    @Override
    public Real getPowerAtTime(double sec) {
        return Real.fromDouble(interpLinearClamped(sec, tSec, pW));
    }

    @Override
    public OptionalDouble getPositionAtTime(double sec) {
        double x = (tSec.length == 1) ? xM[0] : interpX(sec);
        return OptionalDouble.of(x);
    }

    @Override
    public OptionalDouble getSpeedAtTime(double sec) {
        double v;
        if (hasV) {
            v = interpLinearClamped(sec, tSec, withNaNAsPrev(vMS));
        } else if (tSec.length == 1) {
            v = 0.0;
        } else {
            int i = segIndex(sec);
            if (i < 0) i = 0;
            if (i >= tSec.length - 1) i = tSec.length - 2;
            double dt = Math.max(1e-12, tSec[i + 1] - tSec[i]);
            v = (xM[i + 1] - xM[i]) / dt;
        }
        return OptionalDouble.of(v);
    }

    // ===== Internals =====

    private double interpX(double sec) {
        int i = segIndex(sec);
        if (i < 0) return xM[0];
        if (i >= tSec.length - 1) return xM[xM.length - 1];

        double ti  = tSec[i], ti1 = tSec[i + 1];
        double xi  = xM[i],   xi1 = xM[i + 1];

        double tau  = clamp(sec, ti, ti1) - ti;
        double frac = (ti1 > ti) ? tau / (ti1 - ti) : 0.0;

        double out = xi + (xi1 - xi) * frac;

        // If x was derived (no explicit x) but we have speeds, refine inside the segment.
        if (!hasX && hasV) {
            double v0 = Double.isNaN(vMS[i])     ? 0.0 : vMS[i];
            double v1 = Double.isNaN(vMS[i + 1]) ? v0  : vMS[i + 1];
            double dt = Math.max(1e-12, ti1 - ti);
            double tau2 = clamp(sec, ti, ti1) - ti;
            double integral = v0 * tau2 + 0.5 * (v1 - v0) * (tau2 * tau2 / dt);
            out = xM[i] + integral;
        }
        return out;
    }

    private int segIndex(double sec) {
        if (sec <= t0) return -1;                     // before first
        if (sec >= tN) return tSec.length - 1;        // at/after last
        int lo = 0, hi = tSec.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (tSec[mid] <= sec) lo = mid; else hi = mid;
        }
        return lo;
    }

    private static double interpLinearClamped(double xq, double[] x, double[] y) {
        if (x.length == 1) return y[0];
        if (xq <= x[0]) return y[0];
        if (xq >= x[x.length - 1]) return y[y.length - 1];
        int lo = 0, hi = x.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (x[mid] <= xq) lo = mid; else hi = mid;
        }
        double dx = x[lo + 1] - x[lo];
        if (dx <= 0) return y[lo];
        double frac = (xq - x[lo]) / dx;
        return y[lo] + (y[lo + 1] - y[lo]) * frac;
    }

    private static double[] withNaNAsPrev(double[] arr) {
        double[] out = arr.clone();
        double last = 0.0;
        for (int i = 0; i < out.length; i++) {
            if (Double.isNaN(out[i])) out[i] = last;
            else last = out[i];
        }
        return out;
    }

    // ---- PowerPoint extractors (robust to differing field/method names) ----

    private static double safeTime(PowerPoint pt) {
        try { return (double) PowerPoint.class.getMethod("time").invoke(pt); }
        catch (Throwable ignore) {}
        return 0.0;
    }

    private static double safePower(PowerPoint pt) {
        Double d = tryGetDouble(pt, "power", "getPower", "p", "getP", "value", "getValue");
        if (d != null) return d;
        try {
            Method m = pt.getClass().getMethod("power");
            Object v = m.invoke(pt);
            try {
                Method asDouble = v.getClass().getMethod("asDouble");
                Object dv = asDouble.invoke(v);
                if (dv instanceof Number) return ((Number) dv).doubleValue();
            } catch (Throwable ignore) {}
        } catch (Throwable ignore) {}
        return 0.0;
    }

    private static Double tryGetDouble(Object obj, String... names) {
        // Methods
        for (String n : names) {
            try {
                Method m = obj.getClass().getMethod(n);
                Object v = m.invoke(obj);
                if (v instanceof Number) return ((Number) v).doubleValue();
                try {
                    Method asDouble = v.getClass().getMethod("asDouble");
                    Object dv = asDouble.invoke(v);
                    if (dv instanceof Number) return ((Number) dv).doubleValue();
                } catch (Throwable ignoreInner) {}
            } catch (Throwable ignore) {}
        }
        // Fields
        for (String n : names) {
            try {
                Object v = obj.getClass().getField(n).get(obj);
                if (v instanceof Number) return ((Number) v).doubleValue();
            } catch (Throwable ignore) {}
        }
        return null;
    }

    private static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }
}
