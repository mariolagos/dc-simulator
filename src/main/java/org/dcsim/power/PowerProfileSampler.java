package org.dcsim.power;

import org.dcsim.utils.PositionUtils;

import java.util.ArrayList;
import java.util.List;

/** Linear-interpolating sampler for train profiles. */
public final class PowerProfileSampler implements ProfileSampler {

    private static final class Point {
        final double tSec;
        final double posMeters;
        final double motoringKW; // >= 0
        final double brakingKW;  // <= 0 (already signed in file)
        Point(double tSec, double posMeters, double motoringKW, double brakingKW) {
            this.tSec = tSec;
            this.posMeters = posMeters;
            this.motoringKW = motoringKW;
            this.brakingKW = brakingKW;
        }
    }

    private final List<Point> pts = new ArrayList<>();

    /** Add a row from the profile file. bisPosition may be "km.m" or "line km+meters". */
    public void addRow(double tSec, String bisPosition, double motoringKW, double brakingKW) {
        PositionUtils.ParsedPosition p = PositionUtils.parseFlexible(bisPosition);
        pts.add(new Point(tSec, p.meters, motoringKW, brakingKW));
    }

    @Override
    public ProfileSample sampleAt(double tSec, double auxiliaryKW) {
        if (pts.isEmpty()) {
            return new ProfileSample(tSec, 0.0, 0.0, 0.0, auxiliaryKW);
        }
        if (tSec <= pts.get(0).tSec) {
            Point a = pts.get(0);
            return new ProfileSample(tSec, a.posMeters, a.motoringKW, a.brakingKW, auxiliaryKW);
        }
        if (tSec >= pts.get(pts.size() - 1).tSec) {
            Point b = pts.get(pts.size() - 1);
            return new ProfileSample(tSec, b.posMeters, b.motoringKW, b.brakingKW, auxiliaryKW);
        }

        // Linear interpolation between surrounding points
        int hi = upperBound(tSec);
        int lo = hi - 1;
        Point a = pts.get(lo), b = pts.get(hi);
        double w = (tSec - a.tSec) / Math.max(1e-9, (b.tSec - a.tSec));

        double pos = lerp(a.posMeters,  b.posMeters,  w);
        double mot = lerp(a.motoringKW, b.motoringKW, w);
        double brk = lerp(a.brakingKW,  b.brakingKW,  w);

        return new ProfileSample(tSec, pos, mot, brk, auxiliaryKW);
    }

    private static double lerp(double a, double b, double w) { return a + (b - a) * w; }

    private int upperBound(double t) {
        int lo = 0, hi = pts.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (pts.get(mid).tSec < t) lo = mid + 1; else hi = mid;
        }
        return lo;
    }
}
