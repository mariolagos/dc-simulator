package org.dcsim.power;

import org.dcsim.PowerPoint;
import org.dcsim.math.Real;

import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.ToDoubleFunction;


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

    // Position (m)
    @Override
    public OptionalDouble getPositionAtTime(double tSec) {
        // Interpolera position i meter; NaN om saknas
        Double v = interpNumeric(tSec, p -> {
            try {
                double d = p.positionM(); // antas finnas; annars byt till din getter
                return (Double.isNaN(d) || Double.isInfinite(d)) ? Double.NaN : d;
            } catch (Throwable __) {
                return Double.NaN;
            }
        });
        return OptionalDouble.of((v == null || v.isNaN() || v.isInfinite()) ? Double.NaN : v.doubleValue());
    }

    @Override
    public OptionalDouble getSpeedAtTime(double tSec) {
        // Interpolera hastighet i m/s; NaN om saknas
        Double v = interpNumeric(tSec, p -> {
            try {
                double d = p.speedMS(); // antas finnas; annars byt till din getter
                return (Double.isNaN(d) || Double.isInfinite(d)) ? Double.NaN : d;
            } catch (Throwable __) {
                return Double.NaN;
            }
        });
        return OptionalDouble.of((v == null || v.isNaN() || v.isInfinite()) ? Double.NaN : v.doubleValue());
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

    /**
     * Robust interpolation: klampar tid inom profilens omfång, ignorerar NaN/∞,
     * och faller tillbaka till giltig granne om bara ena sidan är definierad.
     * Returnerar null om båda sidor saknar data.
     */
    private Double interpNumeric(double tSec, ToDoubleFunction<PowerPoint> extractor) {
        if (pts == null || pts.isEmpty()) return null;
        if (pts.size() == 1) {
            double y = extractor.applyAsDouble(pts.get(0));
            return (Double.isNaN(y) || Double.isInfinite(y)) ? null : y;
        }

        // Clamp tid
        double t0 = pts.get(0).time();
        double tN = pts.get(pts.size() - 1).time();
        if (tSec <= t0) tSec = t0;
        else if (tSec >= tN) tSec = tN;

        // Segment via binärsök
        int i = findLeftIndex(tSec);
        int j = Math.min(i + 1, pts.size() - 1);

        double y0 = extractor.applyAsDouble(pts.get(i));
        double y1 = extractor.applyAsDouble(pts.get(j));
        boolean ok0 = !(Double.isNaN(y0) || Double.isInfinite(y0));
        boolean ok1 = !(Double.isNaN(y1) || Double.isInfinite(y1));

        if (!ok0 && !ok1) return null;
        if (!ok0) return y1;
        if (!ok1) return y0;

        double ti = pts.get(i).time();
        double tj = pts.get(j).time();
        if (tj == ti) return y0;

        double alpha = (tSec - ti) / (tj - ti);
        return y0 + alpha * (y1 - y0);
    }

    /** Hitta vänstra indexet för tSec (points[lo].time() ≤ tSec ≤ points[lo+1].time()). */
    private int findLeftIndex(double tSec) {
        int n = pts.size();
        if (tSec <= pts.get(0).time()) return 0;
        if (tSec >= pts.get(n - 1).time()) return n - 2; // så att j = lo+1 finns

        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            double tm = pts.get(mid).time();
            if (tm <= tSec) lo = mid;
            else hi = mid;
        }
        return lo;
    }

}
