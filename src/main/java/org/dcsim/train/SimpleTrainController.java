package org.dcsim.train;

/**
 * Simple controller with:
 *  - vmin throttle band for traction: linearly scales traction down near vmin
 *  - vmax clamp for regen: blocks any backfeed at/above vmax
 *
 * Parameters:
 *  vminThrottleBandFrac = fraction above vmin that defines the linear ramp.
 *    Example: vmin=600 V, band=0.10 -> throttle region is [600 V, 660 V].
 */
public final class SimpleTrainController implements TrainController {

    private final double vminThrottleBandFrac;

    /**
     * @param vminThrottleBandFrac fraction (e.g., 0.10 for 10%) to form [vmin, vmin*(1+band)]
     */
    public SimpleTrainController(double vminThrottleBandFrac) {
        if (Double.isNaN(vminThrottleBandFrac) || vminThrottleBandFrac < 0.0) {
            throw new IllegalArgumentException("vminThrottleBandFrac must be >= 0");
        }
        this.vminThrottleBandFrac = vminThrottleBandFrac;
    }

    public double getVminThrottleBandFrac() { return vminThrottleBandFrac; }

    @Override
    public double applyLimits(double requestedPowerW, double measuredVoltageV, double vminV, double vmaxV) {
        double p = requestedPowerW;
        double v = measuredVoltageV;

        // 1) vmin throttle for traction
        if (p > 0.0) {
            double lo = vminV;
            double hi = vminV * (1.0 + vminThrottleBandFrac);
            if (v <= lo) {
                p = 0.0; // fully throttled
            } else if (v < hi) {
                // linear scale from 0 at vmin to 1 at vmin*(1+band)
                double alpha = (v - lo) / (hi - lo);
                double scale = clamp01(alpha);
                p = p * scale;
            }
        }

        // 2) vmax clamp for regen
        if (p < 0.0 && v >= vmaxV) {
            p = 0.0; // block backfeed above/at vmax
        }

        return p;
    }

    private static double clamp01(double x) {
        if (x < 0.0) return 0.0;
        if (x > 1.0) return 1.0;
        return x;
    }
}
