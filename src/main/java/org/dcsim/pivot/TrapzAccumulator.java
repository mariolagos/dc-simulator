package org.dcsim.pivot;

/** Deterministic trapezoidal accumulator with NaN handling. */
final class TrapzAccumulator {
    private Double tPrev = null;
    private Double yPrev = null;
    private double sumJ = 0d;

    void accept(double t, Double y) {
        if (y == null || y.isNaN()) { return; }
        if (tPrev != null && yPrev != null && !yPrev.isNaN() && t > tPrev) {
            sumJ += 0.5 * (yPrev + y) * (t - tPrev);
        }
        tPrev = t; yPrev = y;
    }

    double getEnergyJ() { return sumJ; }
}
