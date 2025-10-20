package org.dcsim.power;

import org.dcsim.PowerPoint;
import org.dcsim.math.Real;

import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

/** Interpolates P/x/v from a list of PowerPoint samples. */
public final class PointsPowerProfile implements PowerProfile {

    private final List<PowerPoint> pts;

    public PointsPowerProfile(List<PowerPoint> points) {
        this.pts = points.stream()
                .sorted(Comparator.comparingDouble(PowerPoint::time))
                .toList();
    }

    @Override
    public Real getPowerAtTime(double t) {
        Double w = interp(t, p -> p.power());           // power already in W
        return Real.fromDouble((w != null) ? w : 0.0);
    }

    @Override
    public OptionalDouble getPositionAtTime(double t) {
        return OptionalDouble.of(interp(t, p -> p.hasPositionM() ? p.positionM() : null));
    }

    @Override
    public OptionalDouble getSpeedAtTime(double t) {
        Double vs = interp(t, p -> p.hasSpeedMS() ? p.speedMS() : null);
        if (vs != null) return OptionalDouble.of(vs);
        // derive from x if v was not provided
        int i = rightIndex(t);
        if (i <= 0 || i >= pts.size()) return null;
        PowerPoint a = pts.get(i - 1), b = pts.get(i);
        if (!a.hasPositionM() || !b.hasPositionM()) return null;
        double dt = b.time() - a.time();
        if (dt <= 0) return null;
        return OptionalDouble.of((b.positionM() - a.positionM()) / dt);
    }

    // ---------- helpers ----------
    private interface Extract { Double get(PowerPoint p); }

    private Double interp(double t, Extract ex) {
        int i = rightIndex(t);
        if (i <= 0)             return safe(ex, pts.get(0));
        if (i >= pts.size())    return safe(ex, pts.get(pts.size()-1));

        PowerPoint a = pts.get(i - 1), b = pts.get(i);
        Double va = safe(ex, a), vb = safe(ex, b);
        if (va == null) return vb;
        if (vb == null) return va;

        double ta = a.time(), tb = b.time();
        double alpha = (tb > ta) ? (t - ta) / (tb - ta) : 0.0;
        if (alpha < 0) alpha = 0;
        if (alpha > 1) alpha = 1;
        return va + alpha * (vb - va);
    }

    private static Double safe(Extract ex, PowerPoint p) {
        try { return ex.get(p); } catch (Throwable ignore) { return null; }
    }

    /** First index strictly greater than t (binary search). */
    private int rightIndex(double t) {
        int lo = 0, hi = pts.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (pts.get(mid).time() <= t) lo = mid + 1; else hi = mid;
        }
        return lo;
    }
}
